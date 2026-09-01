package com.esic.connect.notification.internal;

import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.planning.PlanningPublishedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bloc G1-D — centre de notifications persistantes. Vérifie : livraison
 * <strong>après commit</strong> des événements {@code coursesession} /
 * {@code planning} vers les formateurs concernés ; API « mes
 * notifications » (liste paginée, compteur non lus, marquage lu / tout
 * lu) ; isolation stricte par destinataire ; sécurité ; idempotence par
 * {@code dedup_key} ; exclusion d'un compte archivé ; absence de PII / de
 * jeton / d'identifiant SQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificationIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";

    @TestConfiguration
    static class NoopMailerConfig {
        @Bean
        @Primary
        InvitationMailer noopMailer() {
            return (a, b, c, d) -> {
            };
        }
    }

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private NotificationWriter notificationWriter;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void jdkClient() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    // ------------------------------------------------------------------
    // Livraison après commit — cycle de vie des séances
    // ------------------------------------------------------------------

    @Test
    void cancellingASessionNotifiesThePrincipalTeacherAndActiveSubstitute() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account principal = account(RoleCode.TEACHER);
        Account substitute = account(RoleCode.TEACHER);
        String sessionId = createSession(admin, principal, chain.classA());
        Instant start = Instant.parse((String) session(admin, sessionId).get("startsAt"));
        Instant end = Instant.parse((String) session(admin, sessionId).get("endsAt"));

        // Remplacement ACTIVE couvrant le créneau.
        created(admin, "/api/v1/sessions/" + sessionId + "/substitutions", Map.of(
                "substituteTeacherPublicId", substitute.publicId(),
                "reason", "Congé",
                "validFrom", start.minusSeconds(600).toString(),
                "validUntil", end.plusSeconds(600).toString()));

        post(admin, "/api/v1/sessions/" + sessionId + "/cancel", Map.of("reason", "Alerte bâtiment"));

        // Le principal ET le remplaçant reçoivent la notification d'annulation.
        List<Map<String, Object>> principalNotifs = notifications(tokenFor(principal));
        assertThat(principalNotifs).anySatisfy(n -> {
            assertThat(n.get("type")).isEqualTo("SESSION_CANCELLED");
            assertThat(n.get("status")).isEqualTo("UNREAD");
            assertThat(n.get("resourceType")).isEqualTo("COURSE_SESSION");
            assertThat(n.get("resourcePublicId")).isEqualTo(sessionId);
            assertThat((String) n.get("body")).doesNotContain("Alerte bâtiment"); // motif nominatif exclu
            assertThat(n).doesNotContainKeys("id", "recipientUserId", "dedupKey");
        });
        assertThat(notifications(tokenFor(substitute)))
                .anyMatch(n -> "SESSION_CANCELLED".equals(n.get("type")));
        // Le remplaçant « ADDED » a aussi reçu une notification de type ADDED.
        assertThat(notifications(tokenFor(principal)))
                .anyMatch(n -> "SESSION_SUBSTITUTION_ADDED".equals(n.get("type")));
    }

    @Test
    void aSessionOpenOrCloseDoesNotProduceANotification() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account principal = account(RoleCode.TEACHER);
        String sessionId = createSession(admin, principal, chain.classA());
        post(admin, "/api/v1/sessions/" + sessionId + "/open", null);
        post(admin, "/api/v1/sessions/" + sessionId + "/close", null);
        assertThat(notifications(tokenFor(principal))).isEmpty();
    }

    // ------------------------------------------------------------------
    // G1-D.1 — fin d'un remplacement : le remplaçant qui vient de
    // terminer (n'est plus ACTIVE) doit être notifié via l'événement
    // ------------------------------------------------------------------

    @Test
    void endingASubstitutionNotifiesThePrincipalAndTheJustEndedSubstitute() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account principal = account(RoleCode.TEACHER);
        Account substitute = account(RoleCode.TEACHER);
        Account bystander = account(RoleCode.TEACHER);
        String sessionId = createSession(admin, principal, chain.classA());
        Instant start = Instant.parse((String) session(admin, sessionId).get("startsAt"));
        Instant end = Instant.parse((String) session(admin, sessionId).get("endsAt"));

        String substitutionId = (String) created(admin,
                "/api/v1/sessions/" + sessionId + "/substitutions", Map.of(
                        "substituteTeacherPublicId", substitute.publicId(),
                        "reason", "Congé",
                        "validFrom", start.minusSeconds(600).toString(),
                        "validUntil", end.plusSeconds(600).toString())).get("publicId");

        assertThat(post(admin,
                "/api/v1/sessions/" + sessionId + "/substitutions/" + substitutionId + "/end", null))
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(endedCount(tokenFor(substitute), sessionId))
                .as("le remplaçant tout juste terminé reçoit exactement une notification ENDED")
                .isEqualTo(1L);
        assertThat(endedCount(tokenFor(principal), sessionId))
                .as("le formateur principal reçoit exactement une notification ENDED")
                .isEqualTo(1L);
        assertThat(notifications(tokenFor(bystander)))
                .as("aucun autre formateur n'est notifié")
                .noneMatch(n -> "SESSION_SUBSTITUTION_ENDED".equals(n.get("type")));
        // La notification ENDED ne contient aucun identifiant SQL.
        notifications(tokenFor(substitute)).stream()
                .filter(n -> "SESSION_SUBSTITUTION_ENDED".equals(n.get("type")))
                .forEach(n -> assertThat(n).doesNotContainKeys("id", "recipientUserId", "dedupKey"));
    }

    @Test
    void acrossSuccessiveSubstitutionsOnlyTheConcernedSubstituteIsNotifiedOfItsOwnEnd() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account principal = account(RoleCode.TEACHER);
        Account subA = account(RoleCode.TEACHER);
        Account subB = account(RoleCode.TEACHER);
        String sessionId = createSession(admin, principal, chain.classA());
        Instant start = Instant.parse((String) session(admin, sessionId).get("startsAt"));
        Instant end = Instant.parse((String) session(admin, sessionId).get("endsAt"));
        Instant mid = start.plusSeconds((end.getEpochSecond() - start.getEpochSecond()) / 2);

        String a = (String) created(admin, "/api/v1/sessions/" + sessionId + "/substitutions", Map.of(
                "substituteTeacherPublicId", subA.publicId(), "reason", "A",
                "validFrom", start.minusSeconds(600).toString(),
                "validUntil", mid.toString())).get("publicId");
        post(admin, "/api/v1/sessions/" + sessionId + "/substitutions/" + a + "/end", null);

        String b = (String) created(admin, "/api/v1/sessions/" + sessionId + "/substitutions", Map.of(
                "substituteTeacherPublicId", subB.publicId(), "reason", "B",
                "validFrom", mid.toString(),
                "validUntil", end.plusSeconds(600).toString())).get("publicId");
        post(admin, "/api/v1/sessions/" + sessionId + "/substitutions/" + b + "/end", null);

        assertThat(endedCount(tokenFor(subA), sessionId)).isEqualTo(1L); // seulement sa propre fin
        assertThat(endedCount(tokenFor(subB), sessionId)).isEqualTo(1L); // seulement sa propre fin
        assertThat(endedCount(tokenFor(principal), sessionId)).isEqualTo(2L); // les deux fins
    }

    @Test
    void concurrentEndOfTheSameSubstitutionProducesNoDuplicateNotificationAndNo5xx() throws Exception {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account principal = account(RoleCode.TEACHER);
        Account substitute = account(RoleCode.TEACHER);
        String sessionId = createSession(admin, principal, chain.classA());
        Instant start = Instant.parse((String) session(admin, sessionId).get("startsAt"));
        Instant end = Instant.parse((String) session(admin, sessionId).get("endsAt"));
        String subId = (String) created(admin, "/api/v1/sessions/" + sessionId + "/substitutions", Map.of(
                "substituteTeacherPublicId", substitute.publicId(), "reason", "x",
                "validFrom", start.minusSeconds(600).toString(),
                "validUntil", end.plusSeconds(600).toString())).get("publicId");

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.Callable<Integer> call = () -> {
            barrier.await();
            return exchange(HttpMethod.POST,
                    "/api/v1/sessions/" + sessionId + "/substitutions/" + subId + "/end", null, admin)
                    .getStatusCode().value();
        };
        java.util.concurrent.Future<Integer> f1 = pool.submit(call);
        java.util.concurrent.Future<Integer> f2 = pool.submit(call);
        int s1 = f1.get();
        int s2 = f2.get();
        pool.shutdownNow();

        assertThat(List.of(s1, s2)).allSatisfy(s -> assertThat(s).isLessThan(500));
        assertThat(List.of(s1, s2)).contains(HttpStatus.NO_CONTENT.value());
        assertThat(endedCount(tokenFor(substitute), sessionId))
                .as("une seule mutation réussie ⇒ une seule notification ENDED")
                .isEqualTo(1L);
    }

    private long endedCount(String token, String sessionId) {
        return notifications(token).stream()
                .filter(n -> "SESSION_SUBSTITUTION_ENDED".equals(n.get("type")))
                .filter(n -> sessionId.equals(n.get("resourcePublicId")))
                .count();
    }

    // ------------------------------------------------------------------
    // Livraison après commit — publication de planning (événement direct)
    // ------------------------------------------------------------------

    @Test
    void aCommittedPlanningPublishedEventNotifiesTheAffectedTeachers() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = account(RoleCode.TEACHER);
        Account otherTeacher = account(RoleCode.TEACHER);
        String affectedSession = createSession(admin, teacher, chain.classA());
        String unaffectedSession = createSession(admin, otherTeacher, chain.classA());

        UUID versionPublicId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(tx -> eventPublisher.publishEvent(new PlanningPublishedEvent(
                UUID.randomUUID(), versionPublicId, 1, UUID.fromString(chain.classA()),
                UUID.randomUUID(), true,
                List.of(UUID.fromString(affectedSession)), List.of(), List.of(),
                Instant.now(), null)));

        assertThat(notifications(tokenFor(teacher))).anySatisfy(n -> {
            assertThat(n.get("type")).isEqualTo("PLANNING_PUBLISHED");
            assertThat(n.get("resourceType")).isEqualTo("PLANNING_VERSION");
            assertThat(n.get("resourcePublicId")).isEqualTo(versionPublicId.toString());
        });
        // Le formateur d'une séance NON concernée ne reçoit rien.
        assertThat(notifications(tokenFor(otherTeacher)))
                .noneMatch(n -> versionPublicId.toString().equals(n.get("resourcePublicId")));
        assertThat(unaffectedSession).isNotBlank(); // (garde la variable expressive)
    }

    @Test
    void aRolledBackPlanningPublishedEventProducesNoNotification() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = account(RoleCode.TEACHER);
        String affectedSession = createSession(admin, teacher, chain.classA());
        UUID versionPublicId = UUID.randomUUID();

        // Transaction volontairement rollbackée : la phase AFTER_COMMIT
        // n'est jamais atteinte -> aucune notification.
        transactionTemplate.execute(tx -> {
            eventPublisher.publishEvent(new PlanningPublishedEvent(
                    UUID.randomUUID(), versionPublicId, 1, UUID.fromString(chain.classA()),
                    UUID.randomUUID(), true,
                    List.of(UUID.fromString(affectedSession)), List.of(), List.of(),
                    Instant.now(), null));
            tx.setRollbackOnly();
            return null;
        });

        assertThat(notifications(tokenFor(teacher)))
                .noneMatch(n -> versionPublicId.toString().equals(n.get("resourcePublicId")));
    }

    // ------------------------------------------------------------------
    // Idempotence & compte archivé (NotificationWriter direct)
    // ------------------------------------------------------------------

    @Test
    void writingTheSameEventTwiceCreatesExactlyOneNotification() {
        Account teacher = account(RoleCode.TEACHER);
        UUID resource = UUID.randomUUID();
        UUID eventKey = UUID.randomUUID();
        notificationWriter.write(NotificationType.SESSION_CANCELLED, "COURSE_SESSION", resource, eventKey,
                Set.of(UUID.fromString(teacher.publicId())), "Séance annulée", "corps neutre");
        notificationWriter.write(NotificationType.SESSION_CANCELLED, "COURSE_SESSION", resource, eventKey,
                Set.of(UUID.fromString(teacher.publicId())), "Séance annulée", "corps neutre");
        long count = notifications(tokenFor(teacher)).stream()
                .filter(n -> resource.toString().equals(n.get("resourcePublicId"))).count();
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void anArchivedRecipientIsNeverNotified() {
        Account teacher = account(RoleCode.TEACHER);
        UserAccount toArchive = userAccountRepository.findByEmail(teacher.email()).orElseThrow();
        toArchive.archive(null, Instant.now());
        userAccountRepository.saveAndFlush(toArchive);

        notificationWriter.write(NotificationType.SESSION_CANCELLED, "COURSE_SESSION", UUID.randomUUID(),
                UUID.randomUUID(), Set.of(UUID.fromString(teacher.publicId())), "t", "b");
        Long rows = jdbc.queryForObject(
                "select count(*) from notification where recipient_user_id = "
                        + "(select id from user_account where email = ?)", Long.class, teacher.email());
        assertThat(rows).isZero();
    }

    // ------------------------------------------------------------------
    // API « mes notifications » : liste / compteur / lu / tout lu / isolation
    // ------------------------------------------------------------------

    @Test
    void listUnreadCountReadAndReadAllAreScopedToTheCaller() {
        Account alice = account(RoleCode.TEACHER);
        Account bob = account(RoleCode.TEACHER);
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        notificationWriter.write(NotificationType.SESSION_CANCELLED, "COURSE_SESSION", r1, UUID.randomUUID(),
                Set.of(UUID.fromString(alice.publicId())), "A1", "b");
        notificationWriter.write(NotificationType.SESSION_SUBSTITUTION_ADDED, "COURSE_SESSION", r2, UUID.randomUUID(),
                Set.of(UUID.fromString(alice.publicId())), "A2", "b");
        notificationWriter.write(NotificationType.SESSION_CANCELLED, "COURSE_SESSION", UUID.randomUUID(),
                UUID.randomUUID(), Set.of(UUID.fromString(bob.publicId())), "B1", "b");

        String aliceToken = tokenFor(alice);
        assertThat(unreadCount(aliceToken)).isEqualTo(2L);
        assertThat(unreadCount(tokenFor(bob))).isEqualTo(1L);

        // Filtre statut + tri newest-first.
        List<Map<String, Object>> unread = notificationsFiltered(aliceToken, "UNREAD");
        assertThat(unread).hasSize(2);
        assertThat(unread.get(0).get("createdAt").toString())
                .isGreaterThanOrEqualTo(unread.get(1).get("createdAt").toString());

        // Marquer une notif comme lue (idempotent).
        String firstId = (String) unread.get(0).get("publicId");
        assertThat(post(aliceToken, "/api/v1/me/notifications/" + firstId + "/read", null))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post(aliceToken, "/api/v1/me/notifications/" + firstId + "/read", null))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(unreadCount(aliceToken)).isEqualTo(1L);

        // Bob ne peut pas marquer une notif d'Alice -> 404 (ne révèle pas l'existence).
        assertThat(post(tokenFor(bob), "/api/v1/me/notifications/" + firstId + "/read", null))
                .isEqualTo(HttpStatus.NOT_FOUND);

        // Tout marquer comme lu, borné à l'appelant.
        assertThat(post(aliceToken, "/api/v1/me/notifications/read-all", null))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(unreadCount(aliceToken)).isZero();
        assertThat(unreadCount(tokenFor(bob))).isEqualTo(1L); // Bob intact
    }

    @Test
    void securityIsEnforcedOnTheNotificationEndpoints() {
        // 401 sans jeton.
        assertThat(status(HttpMethod.GET, "/api/v1/me/notifications", null, null))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(status(HttpMethod.GET, "/api/v1/me/notifications/unread-count", null, null))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        // Filtre de statut invalide -> 400.
        assertThat(status(HttpMethod.GET, "/api/v1/me/notifications?status=BOGUS", null,
                tokenFor(account(RoleCode.STUDENT)))).isEqualTo(HttpStatus.BAD_REQUEST);
        // Identifiant inconnu -> 404 NOTIF_NOT_FOUND.
        ResponseEntity<Map<String, Object>> unknown = exchange(HttpMethod.POST,
                "/api/v1/me/notifications/" + UUID.randomUUID() + "/read", null,
                tokenFor(account(RoleCode.STUDENT)));
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody().get("code")).isEqualTo("NOTIF_NOT_FOUND");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private record Chain(String classA) {
    }

    private Chain academicChain(String admin) {
        String site = (String) created(admin, "/api/v1/sites", Map.of("code", "SITE-" + UUID.randomUUID(),
                "name", "Campus", "timeZoneId", "Europe/Paris")).get("publicId");
        String program = (String) created(admin, "/api/v1/programs", Map.of("code", "PRG-" + code(),
                "name", "BTS SIO", "programType", "BTS")).get("publicId");
        String level = (String) created(admin, "/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1", "name", "BTS 1", "sequenceNumber", 1)).get("publicId");
        String year = (String) created(admin, "/api/v1/academic-years", Map.of("code", "AY-" + code(),
                "name", "2026-2027", "startDate", "2026-09-01", "endDate", "2027-08-31")).get("publicId");
        String promo = (String) created(admin, "/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P26", "name", "Promotion 2026")).get("publicId");
        String classA = (String) created(admin, "/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C1", "name", "Classe 1"))
                .get("publicId");
        return new Chain(classA);
    }

    private String createSession(String admin, Account teacher, String classPublicId) {
        Instant now = Instant.now();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("teacherPublicId", teacher.publicId());
        body.put("classPublicIds", List.of(classPublicId));
        body.put("startsAt", now.minusSeconds(3 * 3600).toString());
        body.put("endsAt", now.plusSeconds(6 * 3600).toString());
        body.put("timeZoneId", "Europe/Paris");
        body.put("reason", "séance exceptionnelle");
        return (String) created(admin, "/api/v1/sessions", body).get("publicId");
    }

    private Map<String, Object> session(String token, String id) {
        return exchange(HttpMethod.GET, "/api/v1/sessions/" + id, null, token).getBody();
    }

    private List<Map<String, Object>> notifications(String token) {
        return list("/api/v1/me/notifications?size=100", token);
    }

    private List<Map<String, Object>> notificationsFiltered(String token, String status) {
        return list("/api/v1/me/notifications?size=100&status=" + status, token);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(String path, String token) {
        Map<String, Object> page = exchange(HttpMethod.GET, path, null, token).getBody();
        return (List<Map<String, Object>>) page.get("content");
    }

    private long unreadCount(String token) {
        Object v = exchange(HttpMethod.GET, "/api/v1/me/notifications/unread-count", null, token)
                .getBody().get("unread");
        return ((Number) v).longValue();
    }

    private Map<String, Object> created(String token, String path, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> r = exchange(HttpMethod.POST, path, body, token);
        assertThat(r.getStatusCode()).as("POST " + path + " -> " + r.getBody()).isEqualTo(HttpStatus.CREATED);
        return r.getBody();
    }

    private HttpStatus post(String token, String path, Map<String, Object> body) {
        return (HttpStatus) exchange(HttpMethod.POST, path, body, token).getStatusCode();
    }

    private HttpStatus status(HttpMethod method, String path, Map<String, Object> body, String token) {
        return (HttpStatus) exchange(method, path, body, token).getStatusCode();
    }

    private ResponseEntity<Map<String, Object>> exchange(HttpMethod method, String path,
                                                         Map<String, Object> body, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(method, URI.create(path));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        RequestEntity<?> entity = body == null ? builder.build()
                : builder.contentType(MediaType.APPLICATION_JSON).body(body);
        return rest.exchange(entity, new ParameterizedTypeReference<>() {
        });
    }

    private record Account(String publicId, String email) {
    }

    private Account account(RoleCode... roles) {
        UserAccount a = new UserAccount("notif-" + UUID.randomUUID() + "@esic-connect.test",
                "Notif", "Tester", AccountStatus.ACTIVE);
        a.setPasswordHash(passwordEncoder.encode(PASSWORD));
        a = userAccountRepository.saveAndFlush(a);
        for (RoleCode rc : roles) {
            Role role = roleRepository.findByCode(rc).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(a, role, Instant.now(), true));
        }
        return new Account(a.getPublicId().toString(), a.getEmail());
    }

    private String adminToken() {
        return tokenFor(account(RoleCode.ADMIN));
    }

    private String tokenFor(Account account) {
        Map<String, Object> body = rest.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", account.email(), "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) body.get("accessToken");
    }

    private static String code() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
