package com.esic.connect.coursesession;

import com.esic.connect.audit.internal.AuditEvent;
import com.esic.connect.audit.internal.AuditEventRepository;
import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parcours de bout en bout du module {@code coursesession} : création
 * d'une séance exceptionnelle, cycle de vie {@code PLANNED → OPEN →
 * CLOSED}, consultation filtrée par périmètre (formateur, responsable
 * pédagogique), audit écrit, absence d'identifiant SQL, transitions
 * interdites, contrôles de cohérence (motif, période, formateur éligible,
 * classe active).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CourseSessionIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";

    @TestConfiguration
    static class NoopMailerConfig {
        @Bean
        @Primary
        InvitationMailer noopInvitationMailer() {
            return (toEmail, firstName, rawToken, expiresAt) -> {
            };
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void useJdkClient() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void sessionLifecycleIsAuditedAndHidesInternalIds() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);

        Map<String, Object> session = created("/api/v1/sessions", createBody(teacher.publicId(),
                List.of(chain.classA()), "Cours de rattrapage"), admin);
        String id = (String) session.get("publicId");
        assertThat(session.get("status")).isEqualTo("PLANNED");
        assertThat(session.get("exceptionReason")).isEqualTo("séance exceptionnelle");
        assertThat(((Map<?, ?>) session.get("teacher")).get("publicId")).isEqualTo(teacher.publicId());
        assertThat(session.get("checkpointOpen")).isEqualTo(false);
        assertThat(session).doesNotContainKeys("id", "teacherUserId");
        assertThat((List<?>) session.get("classes")).hasSize(1);
        assertThat(auditActions(id)).contains("SESSION_CREATED");

        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        Map<String, Object> opened = getMap("/api/v1/sessions/" + id, admin);
        assertThat(opened.get("status")).isEqualTo("OPEN");
        assertThat(opened.get("checkpointOpen")).isEqualTo(true);
        assertThat(opened.get("openedAt")).isNotNull();

        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/close", null, admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        Map<String, Object> closed = getMap("/api/v1/sessions/" + id, admin);
        assertThat(closed.get("status")).isEqualTo("CLOSED");
        assertThat(closed.get("checkpointOpen")).isEqualTo(false);
        assertThat(auditActions(id)).contains("SESSION_OPENED", "SESSION_CLOSED");
    }

    @Test
    void forbiddenTransitionsAreRejected() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");

        // Fermer une séance PLANNED -> 409
        assertConflict(HttpMethod.POST, "/api/v1/sessions/" + id + "/close", admin, "SESSION_INVALID_STATE");

        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, admin);
        // Ré-ouvrir -> 409
        assertConflict(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", admin, "SESSION_INVALID_STATE");

        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/close", null, admin);
        // Fermer deux fois / rouvrir après CLOSED -> 409
        assertConflict(HttpMethod.POST, "/api/v1/sessions/" + id + "/close", admin, "SESSION_INVALID_STATE");
        assertConflict(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", admin, "SESSION_INVALID_STATE");
    }

    // ------------------------------------------------------------------
    // G1-C — annulation
    // ------------------------------------------------------------------

    @Test
    void cancellingAPlannedSessionMakesItInactiveEverywhereAndIsAudited() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), "À annuler"), admin).get("publicId");

        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/cancel",
                Map.of("reason", "Formateur indisponible"), admin)).isEqualTo(HttpStatus.NO_CONTENT);

        // Inactive pour tout accès métier normal : GET / open / jeton -> 404 ; absente de la liste.
        assertThat(exchange(HttpMethod.GET, "/api/v1/sessions/" + id, null, admin).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, admin).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + id + "/attendance-token", null, admin)
                .getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.CONFLICT);
        List<?> sessionList = (List<?>) getMap(
                "/api/v1/sessions?classGroup=" + chain.classA() + "&size=100", admin).get("content");
        assertThat(sessionList).noneMatch(s -> id.equals(((Map<?, ?>) s).get("publicId")));

        // Double annulation -> 409 (transitions strictes, cohérent avec open/close).
        ResponseEntity<Map<String, Object>> again = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/cancel", Map.of("reason", "encore"), admin);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().get("code")).isEqualTo("SESSION_INVALID_STATE");
        assertThat(auditActions(id)).contains("SESSION_CREATED", "SESSION_CANCELLED");
    }

    @Test
    void cancellingAnOpenSessionIsAllowedAndClosedSessionIsNot() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String open = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");
        status(HttpMethod.POST, "/api/v1/sessions/" + open + "/open", null, admin);
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + open + "/cancel",
                Map.of("reason", "Alerte bâtiment"), admin)).isEqualTo(HttpStatus.NO_CONTENT);

        String closed = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");
        status(HttpMethod.POST, "/api/v1/sessions/" + closed + "/open", null, admin);
        status(HttpMethod.POST, "/api/v1/sessions/" + closed + "/close", null, admin);
        ResponseEntity<Map<String, Object>> onClosed = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + closed + "/cancel", Map.of("reason", "trop tard"), admin);
        assertThat(onClosed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(onClosed.getBody().get("code")).isEqualTo("SESSION_INVALID_STATE");
    }

    @Test
    void cancellationRequiresAReasonAndTheRightRole() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");

        // Motif vide -> 400.
        ResponseEntity<Map<String, Object>> blank = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/cancel", Map.of("reason", "   "), admin);
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // STUDENT -> 403 ; SCHOOL_ADMINISTRATION -> 403 (gestion exclue).
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + id + "/cancel",
                Map.of("reason", "x"), tokenFor(RoleCode.STUDENT)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + id + "/cancel",
                Map.of("reason", "x"), tokenFor(RoleCode.SCHOOL_ADMINISTRATION)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void concurrentOpenAndCancelResolveToOneTerminalStateWithoutServerError() throws Exception {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");

        Callable<HttpStatus> openCall = () -> (HttpStatus) exchange(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/open", null, admin).getStatusCode();
        Callable<HttpStatus> cancelCall = () -> (HttpStatus) exchange(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/cancel", Map.of("reason", "course"), admin).getStatusCode();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<HttpStatus> statuses;
        try {
            List<Future<HttpStatus>> futures = pool.invokeAll(List.of(openCall, cancelCall));
            statuses = List.of(join(futures.get(0)), join(futures.get(1)));
        } finally {
            pool.shutdownNow();
        }
        // Verrou optimiste : un gagnant (204), un perdant (409 contrôlé), jamais 5xx.
        assertThat(statuses).noneMatch(HttpStatus::is5xxServerError);
        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.CONFLICT);
        // État final cohérent : soit OPEN (open a gagné), soit annulée (GET -> 404).
        HttpStatus getStatus = (HttpStatus) exchange(HttpMethod.GET, "/api/v1/sessions/" + id, null, admin)
                .getStatusCode();
        assertThat(getStatus).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test
    void creationRejectsMissingReasonInvalidPeriodAndNonTeacher() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);

        // Motif obligatoire (validation Jakarta @NotBlank -> 400 VALIDATION_ERROR)
        Map<String, Object> noReason = new java.util.HashMap<>(
                createBody(teacher.publicId(), List.of(chain.classA()), null));
        noReason.remove("reason");
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions", noReason, admin).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Période invalide (fin <= début)
        Map<String, Object> badPeriod = new java.util.HashMap<>(
                createBody(teacher.publicId(), List.of(chain.classA()), null));
        badPeriod.put("startsAt", "2026-09-10T12:00:00Z");
        badPeriod.put("endsAt", "2026-09-10T08:00:00Z");
        ResponseEntity<Map<String, Object>> period = exchange(HttpMethod.POST, "/api/v1/sessions", badPeriod, admin);
        assertThat(period.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(period.getBody().get("code")).isEqualTo("SESSION_INVALID_PERIOD");

        // Aucune classe -> 400
        Map<String, Object> noClass = new java.util.HashMap<>(
                createBody(teacher.publicId(), List.of(), null));
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions", noClass, admin).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Compte non formateur -> 409 SESSION_TEACHER_NOT_ELIGIBLE
        Account notTeacher = accountWithRoles(RoleCode.STUDENT);
        ResponseEntity<Map<String, Object>> eligible = exchange(HttpMethod.POST, "/api/v1/sessions",
                createBody(notTeacher.publicId(), List.of(chain.classA()), null), admin);
        assertThat(eligible.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(eligible.getBody().get("code")).isEqualTo("SESSION_TEACHER_NOT_ELIGIBLE");

        // Formateur inconnu -> 400 SESSION_TEACHER_NOT_FOUND
        ResponseEntity<Map<String, Object>> unknown = exchange(HttpMethod.POST, "/api/v1/sessions",
                createBody(UUID.randomUUID().toString(), List.of(chain.classA()), null), admin);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unknown.getBody().get("code")).isEqualTo("SESSION_TEACHER_NOT_FOUND");
    }

    @Test
    void teacherSeesOnlyTheirOwnSessions() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacherA = accountWithRoles(RoleCode.TEACHER);
        Account teacherB = accountWithRoles(RoleCode.TEACHER);
        String sessionA = (String) created("/api/v1/sessions",
                createBody(teacherA.publicId(), List.of(chain.classA()), null), admin).get("publicId");
        created("/api/v1/sessions", createBody(teacherB.publicId(), List.of(chain.classA()), null), admin);

        String tokenA = tokenFor(teacherA);
        Map<String, Object> list = getMap("/api/v1/sessions", tokenA);
        List<?> content = (List<?>) list.get("content");
        assertThat(content).hasSize(1);
        assertThat(((Map<?, ?>) content.get(0)).get("publicId")).isEqualTo(sessionA);

        // La séance d'un autre formateur -> 403
        Map<String, Object> other = getMap("/api/v1/sessions", admin);
        String someoneElse = (String) ((Map<?, ?>) ((List<?>) other.get("content")).stream()
                .map(item -> (Map<?, ?>) item)
                .filter(item -> !sessionA.equals(item.get("publicId")))
                .findFirst().orElseThrow()).get("publicId");
        assertThat(status(HttpMethod.GET, "/api/v1/sessions/" + someoneElse, null, tokenA))
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Un formateur ne peut pas créer de séance -> 403
        assertThat(status(HttpMethod.POST, "/api/v1/sessions",
                createBody(teacherA.publicId(), List.of(chain.classA()), null), tokenA))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void eligibleTeachersEndpointListsActiveTeachersOnly() {
        String admin = adminToken();
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        // Compte formateur suspendu : exclu.
        Account suspended = accountWithRoles(RoleCode.TEACHER);
        UserAccount suspendedAccount = userAccountRepository.findByEmail(suspended.email()).orElseThrow();
        suspendedAccount.suspend("test", null, Instant.now());
        userAccountRepository.saveAndFlush(suspendedAccount);

        List<Map<String, Object>> teachers = restTemplate.exchange(
                RequestEntity.get(URI.create("/api/v1/sessions/teachers"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).build(),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
        List<Object> ids = teachers.stream().map(t -> t.get("publicId")).map(Object.class::cast).toList();
        assertThat(ids).contains(teacher.publicId());
        assertThat(ids).doesNotContain(suspended.publicId());
        assertThat(teachers).allSatisfy(t -> assertThat(t).doesNotContainKeys("email", "id"));
    }

    @Test
    void studentCannotListSessions() {
        assertThat(status(HttpMethod.GET, "/api/v1/sessions", null, tokenFor(RoleCode.STUDENT)))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------
    // Points de contrôle (V10)
    // ------------------------------------------------------------------

    @Test
    void checkpointLifecycleIsAuditedAndListed() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, admin);

        // À l'ouverture, le point de contrôle START existe et est OUVERT.
        List<Map<String, Object>> checkpoints = list("/api/v1/sessions/" + id + "/checkpoints", admin);
        assertThat(checkpoints).hasSize(1);
        assertThat(checkpoints.get(0).get("type")).isEqualTo("START");
        assertThat(checkpoints.get(0).get("status")).isEqualTo("OPEN");
        assertThat(checkpoints.get(0)).doesNotContainKey("id");

        // Ajout d'un point de contrôle CUSTOM.
        Map<String, Object> custom = created("/api/v1/sessions/" + id + "/checkpoints",
                Map.of("label", "Retour de pause", "type", "CUSTOM"), admin);
        String customId = (String) custom.get("publicId");
        assertThat(custom.get("status")).isEqualTo("PLANNED");
        assertThat(((Number) custom.get("displayOrder")).intValue()).isEqualTo(1);

        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/checkpoints/" + customId + "/open", null, admin);
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/checkpoints/" + customId + "/close", null, admin);

        // Un troisième point de contrôle, annulé.
        String cancelledId = (String) created("/api/v1/sessions/" + id + "/checkpoints",
                Map.of("label", "Point en trop", "type", "CUSTOM", "required", false), admin).get("publicId");
        assertThat(status(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/checkpoints/" + cancelledId + "/cancel",
                Map.of("reason", "erreur de saisie"), admin)).isEqualTo(HttpStatus.NO_CONTENT);

        List<Map<String, Object>> after = list("/api/v1/sessions/" + id + "/checkpoints", admin);
        assertThat(after).extracting(cp -> cp.get("status"))
                .containsExactly("OPEN", "CLOSED", "CANCELLED");

        assertThat(auditActions(customId)).contains("CHECKPOINT_CREATED", "CHECKPOINT_OPENED", "CHECKPOINT_CLOSED");
        assertThat(auditActions(cancelledId)).contains("CHECKPOINT_CREATED", "CHECKPOINT_CANCELLED");

        // La fiche séance porte la liste des points de contrôle.
        Map<String, Object> session = getMap("/api/v1/sessions/" + id, admin);
        assertThat((List<?>) session.get("checkpoints")).hasSize(3);

        // Fermeture de la séance -> le point de contrôle START ouvert est fermé.
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/close", null, admin);
        assertThat(list("/api/v1/sessions/" + id + "/checkpoints", admin))
                .extracting(cp -> cp.get("status"))
                .containsExactly("CLOSED", "CLOSED", "CANCELLED");
    }

    @Test
    void checkpointRulesAndForbiddenTransitionsAreRejected() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");

        // Séance PLANNED : on peut créer un point de contrôle, mais pas l'ouvrir.
        String customId = (String) created("/api/v1/sessions/" + id + "/checkpoints",
                Map.of("label", "Fin", "type", "END"), admin).get("publicId");
        assertConflict(HttpMethod.POST, "/api/v1/sessions/" + id + "/checkpoints/" + customId + "/open",
                admin, "ATT_CHECKPOINT_INVALID_STATE");

        // Deux END actifs -> 400 ATT_CHECKPOINT_INVALID_TYPE.
        ResponseEntity<Map<String, Object>> dupEnd = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/checkpoints", Map.of("label", "Fin bis", "type", "END"), admin);
        assertThat(dupEnd.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(dupEnd.getBody().get("code")).isEqualTo("ATT_CHECKPOINT_INVALID_TYPE");

        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, admin);
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/checkpoints/" + customId + "/open", null, admin);
        // Ouvrir deux fois -> 409.
        assertConflict(HttpMethod.POST, "/api/v1/sessions/" + id + "/checkpoints/" + customId + "/open",
                admin, "ATT_CHECKPOINT_INVALID_STATE");
        // Annuler sans motif -> 400 (validation @NotBlank).
        assertThat(exchange(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/checkpoints/" + customId + "/cancel", Map.of("reason", "  "), admin)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Fermer un point de contrôle inconnu -> 404 ATT_CHECKPOINT_NOT_FOUND.
        ResponseEntity<Map<String, Object>> unknown = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/checkpoints/" + UUID.randomUUID() + "/close", null, admin);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody().get("code")).isEqualTo("ATT_CHECKPOINT_NOT_FOUND");

        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/close", null, admin);
        // Séance CLOSED : plus de création de point de contrôle -> 409.
        ResponseEntity<Map<String, Object>> onClosed = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/checkpoints", Map.of("label", "x", "type", "CUSTOM"), admin);
        assertThat(onClosed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(onClosed.getBody().get("code")).isEqualTo("ATT_CHECKPOINT_INVALID_STATE");
    }

    @Test
    void checkpointManagementRolesAreEnforced() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");

        String schoolAdmin = tokenFor(RoleCode.SCHOOL_ADMINISTRATION);
        // Lecture autorisée...
        assertThat(rawStatus(HttpMethod.GET, "/api/v1/sessions/" + id + "/checkpoints", null, schoolAdmin))
                .isEqualTo(HttpStatus.OK);
        // ...mais pas la gestion.
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/checkpoints",
                Map.of("label", "x", "type", "CUSTOM"), schoolAdmin)).isEqualTo(HttpStatus.FORBIDDEN);

        // STUDENT : aucun accès.
        assertThat(rawStatus(HttpMethod.GET, "/api/v1/sessions/" + id + "/checkpoints", null,
                tokenFor(RoleCode.STUDENT))).isEqualTo(HttpStatus.FORBIDDEN);

        // Un formateur non affecté à la séance -> 403.
        Account otherTeacher = accountWithRoles(RoleCode.TEACHER);
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/checkpoints",
                Map.of("label", "x", "type", "CUSTOM"), tokenFor(otherTeacher)))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void concurrentCheckpointOpenAndCloseResolveToAllowedStateWithoutServerError() throws Exception {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, admin);
        // Point de contrôle CUSTOM PLANNED : ouverture et fermeture lancées
        // en parallèle. Une seule transition est valide depuis l'état
        // courant ; l'autre est un conflit contrôlé, jamais un 500.
        String cp = (String) created("/api/v1/sessions/" + id + "/checkpoints",
                Map.of("label", "Retour de pause", "type", "CUSTOM"), admin).get("publicId");

        String base = "/api/v1/sessions/" + id + "/checkpoints/" + cp;
        Callable<HttpStatus> open = () -> (HttpStatus) exchange(HttpMethod.POST, base + "/open", null, admin)
                .getStatusCode();
        Callable<HttpStatus> close = () -> (HttpStatus) exchange(HttpMethod.POST, base + "/close", null, admin)
                .getStatusCode();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<HttpStatus> statuses;
        try {
            List<Future<HttpStatus>> futures = pool.invokeAll(List.of(open, close));
            statuses = List.of(join(futures.get(0)), join(futures.get(1)));
        } finally {
            pool.shutdownNow();
        }
        assertThat(statuses).noneMatch(HttpStatus::is5xxServerError);
        assertThat(statuses).filteredOn(HttpStatus.CONFLICT::equals).hasSize(1);
        assertThat(statuses).anyMatch(HttpStatus.NO_CONTENT::equals);

        List<Map<String, Object>> after = list("/api/v1/sessions/" + id + "/checkpoints", admin);
        Object cpStatus = after.stream().filter(c -> cp.equals(c.get("publicId"))).findFirst()
                .orElseThrow().get("status");
        assertThat(cpStatus).isIn("OPEN", "CLOSED");
    }

    private static HttpStatus join(Future<HttpStatus> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Map<String, Object>> list(String path, String token) {
        return restTemplate.exchange(
                RequestEntity.get(URI.create(path)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
    }

    /** Statut d'une requête sans désérialiser le corps (utile pour les endpoints renvoyant un tableau). */
    private HttpStatus rawStatus(HttpMethod method, String path, Map<String, Object> body, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(method, URI.create(path));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        RequestEntity<?> entity = body == null
                ? builder.build()
                : builder.contentType(MediaType.APPLICATION_JSON).body(body);
        return (HttpStatus) restTemplate.exchange(entity, String.class).getStatusCode();
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private Map<String, Object> createBody(String teacherPublicId, List<String> classPublicIds, String title) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("teacherPublicId", teacherPublicId);
        body.put("classPublicIds", classPublicIds);
        body.put("startsAt", "2026-09-10T08:00:00Z");
        body.put("endsAt", "2026-09-10T12:00:00Z");
        body.put("timeZoneId", "Europe/Paris");
        body.put("reason", "séance exceptionnelle");
        if (title != null) {
            body.put("title", title);
        }
        return body;
    }

    private record Chain(String classA, String classB) {
    }

    private Chain academicChain(String admin) {
        String site = (String) created("/api/v1/sites", Map.of("code", "SITE-" + UUID.randomUUID(),
                "name", "Campus", "timeZoneId", "Europe/Paris"), admin).get("publicId");
        String program = (String) created("/api/v1/programs", Map.of("code", "PRG-" + code(),
                "name", "BTS SIO", "programType", "BTS"), admin).get("publicId");
        String level = (String) created("/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1", "name", "BTS 1", "sequenceNumber", 1), admin).get("publicId");
        String year = (String) created("/api/v1/academic-years", Map.of("code", "AY-" + code(),
                "name", "2026-2027", "startDate", "2026-09-01", "endDate", "2027-08-31"), admin).get("publicId");
        String promo = (String) created("/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P26", "name", "Promotion 2026"), admin).get("publicId");
        String classA = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C1", "name", "Classe 1"), admin)
                .get("publicId");
        String classB = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C2", "name", "Classe 2"), admin)
                .get("publicId");
        return new Chain(classA, classB);
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET, path, null, token);
        assertThat(response.getStatusCode()).as("GET " + path).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private void assertConflict(HttpMethod method, String path, String token, String expectedCode) {
        ResponseEntity<Map<String, Object>> response = exchange(method, path, null, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo(expectedCode);
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
        RequestEntity<?> entity = body == null
                ? builder.build()
                : builder.contentType(MediaType.APPLICATION_JSON).body(body);
        return restTemplate.exchange(entity, new ParameterizedTypeReference<>() {
        });
    }

    private List<String> auditActions(String resourcePublicId) {
        UUID target = UUID.fromString(resourcePublicId);
        return auditEventRepository.findAll().stream()
                .filter(event -> target.equals(event.getResourcePublicId()))
                .map(AuditEvent::getAction)
                .toList();
    }

    private record Account(String publicId, String email) {
    }

    private Account accountWithRoles(RoleCode... roles) {
        UserAccount account = new UserAccount("cs-" + UUID.randomUUID() + "@esic-connect.test",
                "Cs", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return new Account(account.getPublicId().toString(), account.getEmail());
    }

    private String adminToken() {
        return tokenFor(RoleCode.ADMIN);
    }

    private String tokenFor(RoleCode... roles) {
        return tokenFor(accountWithRoles(roles));
    }

    private String tokenFor(Account account) {
        Map<String, Object> body = restTemplate.exchange(
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
