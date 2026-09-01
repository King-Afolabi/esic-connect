package com.esic.connect.notification.internal;

import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bloc G1-D.1 — résilience de la livraison des notifications.
 *
 * <p>Un {@link NotificationRowWriter} <strong>volontairement défaillant</strong>
 * ({@code @Primary}, échoue sur les {@code failFirstN} premiers appels)
 * prouve :
 * <ul>
 *   <li>§6 — l'échec d'écriture d'<em>un</em> destinataire n'empêche pas
 *       les autres destinataires du même événement d'être notifiés ;</li>
 *   <li>§7 — un échec <em>complet</em> du writer après le commit métier
 *       ne renvoie <strong>pas</strong> d'erreur HTTP, ne rollbacke pas la
 *       mutation métier et ne laisse aucun état partiel (livraison « au
 *       mieux », DEC-G1-007 / G1-D-OUTBOX).</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificationDeliveryResilienceIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";

    @TestConfiguration
    static class FlakyRowWriterConfig {

        static final AtomicInteger calls = new AtomicInteger();
        /** Nombre d'appels initiaux du writer qui lèvent une exception. */
        static volatile int failFirstN = 0;

        static void reset() {
            calls.set(0);
            failFirstN = 0;
        }

        @Bean
        @Primary
        NotificationRowWriter flakyRowWriter(NotificationRepository repository) {
            return new NotificationRowWriter(repository) {
                @Override
                void write(long recipientUserId, NotificationType type, String title, String body,
                          String resourceType, UUID resourcePublicId, String dedupKey, Instant createdAt) {
                    if (calls.incrementAndGet() <= failFirstN) {
                        throw new RuntimeException("panne simulee d'ecriture de notification");
                    }
                    repository.saveAndFlush(new Notification(recipientUserId, type, title, body,
                            resourceType, resourcePublicId, dedupKey, createdAt));
                }
            };
        }

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
    private NotificationWriter notificationWriter;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        FlakyRowWriterConfig.reset();
    }

    @AfterEach
    void tearDown() {
        FlakyRowWriterConfig.reset();
    }

    // ------------------------------------------------------------------
    // §6 — échec d'un destinataire n'empêche pas les autres
    // ------------------------------------------------------------------

    @Test
    void whenOneRecipientWriteFailsTheOtherRecipientsAreStillNotified() {
        Account a = account(RoleCode.TEACHER);
        Account b = account(RoleCode.TEACHER);
        Account c = account(RoleCode.TEACHER);
        UUID resource = UUID.randomUUID();
        UUID eventKey = UUID.randomUUID();

        FlakyRowWriterConfig.failFirstN = 1; // le tout premier destinataire échoue

        // 3 destinataires ; 1 échoue, 2 réussissent — aucune exception ne remonte.
        notificationWriter.write(NotificationType.SESSION_CANCELLED, "COURSE_SESSION", resource, eventKey,
                new java.util.LinkedHashSet<>(List.of(
                        UUID.fromString(a.publicId()),
                        UUID.fromString(b.publicId()),
                        UUID.fromString(c.publicId()))),
                "Séance annulée", "corps neutre");

        assertThat(notificationRows(resource))
                .as("2 des 3 destinataires notifiés malgré l'échec du premier").isEqualTo(2L);
    }

    @Test
    void aDuplicateOnOneRecipientDoesNotBlockAFreshRecipient() {
        Account a = account(RoleCode.TEACHER);
        Account b = account(RoleCode.TEACHER);
        UUID resource = UUID.randomUUID();
        UUID eventKey = UUID.randomUUID();
        Set<UUID> recipients = new java.util.LinkedHashSet<>(List.of(
                UUID.fromString(a.publicId()), UUID.fromString(b.publicId())));

        // Première livraison : a et b notifiés.
        notificationWriter.write(NotificationType.SESSION_CANCELLED, "COURSE_SESSION", resource, eventKey,
                recipients, "t", "b");
        // Deuxième livraison (rejeu) : a et b déjà notifiés — toujours exactement une ligne chacun.
        notificationWriter.write(NotificationType.SESSION_CANCELLED, "COURSE_SESSION", resource, eventKey,
                recipients, "t", "b");

        assertThat(notificationRows(resource)).isEqualTo(2L);
    }

    // ------------------------------------------------------------------
    // §7 — échec complet du writer après commit métier
    // ------------------------------------------------------------------

    @Test
    void aTotalWriterFailureAfterCommitDoesNotBreakTheBusinessOperation() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account principal = account(RoleCode.TEACHER);
        String sessionId = createSession(admin, principal, chain.classA());

        FlakyRowWriterConfig.failFirstN = 999; // toute écriture de notification échoue

        // L'annulation (mutation métier) réussit malgré l'échec des notifications.
        assertThat(post(admin, "/api/v1/sessions/" + sessionId + "/cancel",
                Map.of("reason", "Alerte bâtiment"))).isEqualTo(HttpStatus.NO_CONTENT);

        // Mutation métier bien persistée.
        Map<String, Object> reloaded = exchange(HttpMethod.GET, "/api/v1/sessions/" + sessionId, null, admin)
                .getBody();
        assertThat(reloaded.get("status")).isEqualTo("CANCELLED");

        // Aucun état de notification partiel.
        assertThat(notificationRows(UUID.fromString(sessionId))).isZero();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private long notificationRows(UUID resourcePublicId) {
        Long n = jdbc.queryForObject(
                "select count(*) from notification where resource_public_id = UUID_TO_BIN(?)",
                Long.class, resourcePublicId.toString());
        return n == null ? 0L : n;
    }

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
        return new Chain((String) created(admin, "/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C1", "name", "Classe 1"))
                .get("publicId"));
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

    private Map<String, Object> created(String token, String path, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> r = exchange(HttpMethod.POST, path, body, token);
        assertThat(r.getStatusCode()).as("POST " + path + " -> " + r.getBody()).isEqualTo(HttpStatus.CREATED);
        return r.getBody();
    }

    private HttpStatus post(String token, String path, Map<String, Object> body) {
        return (HttpStatus) exchange(HttpMethod.POST, path, body, token).getStatusCode();
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
        UserAccount a = new UserAccount("notif-res-" + UUID.randomUUID() + "@esic-connect.test",
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
