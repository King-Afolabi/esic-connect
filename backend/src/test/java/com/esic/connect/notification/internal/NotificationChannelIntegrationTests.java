package com.esic.connect.notification.internal;

import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.support.AuthTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Canaux de notification : courriel, poussée et préférences
 * (EF-NOTIF-004, EF-NOTIF-005, EF-NOTIF-006 ; docs/02 §21.1, §21.6,
 * §29.3).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificationChannelIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";

    @TestConfiguration
    static class MailerConfig {

        static final List<String> sent = new CopyOnWriteArrayList<>();

        @Bean
        @Primary
        NotificationMailer recordingNotificationMailer() {
            return (toEmail, title, body) -> sent.add(toEmail + "|" + title);
        }

        @Bean
        @Primary
        InvitationMailer noopInvitationMailer() {
            return (a, b, c, d) -> {
            };
        }
    }

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private NotificationOutboxHandler notificationHandler;
    @Autowired
    private WebPushSender webPushSender;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        MailerConfig.sent.clear();
    }

    // ------------------------------------------------------------------
    // EF-NOTIF-004 — courriel
    // ------------------------------------------------------------------

    @Test
    @DisplayName("une notification met en file son courriel, envoyé après commit")
    void aNotificationAlsoReachesTheRecipientByEmail() {
        Account teacher = account(RoleCode.TEACHER);
        UUID resource = UUID.randomUUID();

        deliver(resource, UUID.randomUUID(), teacher);

        assertThat(MailerConfig.sent).anyMatch(entry -> entry.startsWith(teacher.email() + "|"));
        // « Remis au serveur de messagerie » — jamais « délivré » : le
        // statut fournisseur reste UNKNOWN tant qu'il n'a rien constaté.
        assertThat(deliveryStatuses()).contains("SENT_TO_PROVIDER|UNKNOWN");
    }

    @Test
    @DisplayName("l'adresse n'est jamais écrite en clair dans le journal de délivrabilité")
    void theDeliveryLogNeverStoresTheAddressInClear() {
        Account teacher = account(RoleCode.TEACHER);
        deliver(UUID.randomUUID(), UUID.randomUUID(), teacher);

        Long inClear = jdbc.queryForObject(
                "select count(*) from email_delivery where recipient_masked = ?", Long.class,
                teacher.email());
        assertThat(inClear).isZero();
    }

    // ------------------------------------------------------------------
    // EF-NOTIF-006 — préférences
    // ------------------------------------------------------------------

    @Test
    @DisplayName("refuser le courriel d'une catégorie l'empêche, sans toucher au centre de notifications")
    void disablingTheEmailChannelStopsTheEmailButNotTheInAppNotification() {
        Account teacher = account(RoleCode.TEACHER);
        String token = tokenFor(teacher);

        assertThat(put(token, "/api/v1/me/notification-preferences",
                Map.of("category", "SESSION", "channel", "EMAIL", "enabled", false)))
                .isEqualTo(HttpStatus.OK);

        UUID resource = UUID.randomUUID();
        deliver(resource, UUID.randomUUID(), teacher);

        assertThat(MailerConfig.sent).as("aucun courriel pour cette catégorie").isEmpty();
        assertThat(notificationRows(resource)).as("le centre reste alimenté").isEqualTo(1L);
    }

    @Test
    @DisplayName("le réglage est par catégorie : couper SESSION ne coupe pas JUSTIFICATION")
    void aPreferenceOnlyAffectsItsOwnCategory() {
        Account teacher = account(RoleCode.TEACHER);
        put(tokenFor(teacher), "/api/v1/me/notification-preferences",
                Map.of("category", "SESSION", "channel", "EMAIL", "enabled", false));

        deliver(NotificationType.JUSTIFICATION_ACCEPTED, UUID.randomUUID(), UUID.randomUUID(), teacher);

        assertThat(MailerConfig.sent).anyMatch(entry -> entry.startsWith(teacher.email() + "|"));
    }

    @Test
    @DisplayName("le centre de notifications et les alertes de sécurité ne se désactivent pas")
    void mandatoryChannelsCannotBeDisabled() {
        String token = tokenFor(account(RoleCode.TEACHER));

        ResponseEntity<Map<String, Object>> inApp = exchange(HttpMethod.PUT,
                "/api/v1/me/notification-preferences",
                Map.of("category", "SESSION", "channel", "IN_APP", "enabled", false), token);
        assertThat(inApp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(inApp.getBody().get("code")).isEqualTo("NOTIF_PREFERENCE_LOCKED");

        ResponseEntity<Map<String, Object>> security = exchange(HttpMethod.PUT,
                "/api/v1/me/notification-preferences",
                Map.of("category", "SECURITY", "channel", "EMAIL", "enabled", false), token);
        assertThat(security.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("les réglages verrouillés sont annoncés comme tels, plutôt que refusés au clic")
    void lockedPreferencesAreAdvertisedAsLocked() {
        String token = tokenFor(account(RoleCode.TEACHER));

        ResponseEntity<Map<String, Object>> response =
                exchange(HttpMethod.GET, "/api/v1/me/notification-preferences", null, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> preferences =
                (List<Map<String, Object>>) response.getBody().get("preferences");
        assertThat(preferences).anySatisfy(p -> {
            if ("IN_APP".equals(p.get("channel"))) {
                assertThat(p.get("locked")).isEqualTo(true);
                assertThat(p.get("enabled")).isEqualTo(true);
            }
        });
        assertThat(preferences).allSatisfy(p -> {
            if ("SECURITY".equals(p.get("category"))) {
                assertThat(p.get("locked")).isEqualTo(true);
            }
        });
    }

    @Test
    @DisplayName("une catégorie ou un canal inconnu est refusé en 400")
    void unknownPreferencesAreRejected() {
        String token = tokenFor(account(RoleCode.TEACHER));
        assertThat(put(token, "/api/v1/me/notification-preferences",
                Map.of("category", "INCONNUE", "channel", "EMAIL", "enabled", false)))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(put(token, "/api/v1/me/notification-preferences",
                Map.of("category", "SESSION", "channel", "PIGEON", "enabled", false)))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("les préférences sont strictement personnelles")
    void preferencesAreScopedToTheCaller() {
        Account alice = account(RoleCode.TEACHER);
        Account bob = account(RoleCode.TEACHER);
        put(tokenFor(alice), "/api/v1/me/notification-preferences",
                Map.of("category", "SESSION", "channel", "EMAIL", "enabled", false));

        deliver(UUID.randomUUID(), UUID.randomUUID(), bob);
        assertThat(MailerConfig.sent).as("le réglage d'Alice ne prive pas Bob")
                .anyMatch(entry -> entry.startsWith(bob.email() + "|"));
    }

    // ------------------------------------------------------------------
    // EF-NOTIF-005 — poussée : état honnête et cycle de vie
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sans clé VAPID, l'API DÉCLARE que la poussée est inactive")
    void thePushProviderDeclaresItselfInactiveWhenUnconfigured() {
        String token = tokenFor(account(RoleCode.STUDENT));

        ResponseEntity<Map<String, Object>> status =
                exchange(HttpMethod.GET, "/api/v1/me/push/status", null, token);

        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Le produit ne simule pas un service qu'il n'a pas : il le dit.
        assertThat(status.getBody().get("providerActive")).isEqualTo(false);
        assertThat(webPushSender.isActive()).isFalse();
        assertThat(webPushSender.send("https://push.example/x", "k", "a", "{}"))
                .isEqualTo(WebPushSender.Outcome.INACTIVE);
    }

    @Test
    @DisplayName("un appareil s'abonne, réapparaît dans son état, puis se révoque")
    void aDeviceCanSubscribeAndRevoke() {
        String token = tokenFor(account(RoleCode.STUDENT));
        String endpoint = "https://push.example.test/" + UUID.randomUUID();

        ResponseEntity<Map<String, Object>> created = exchange(HttpMethod.POST,
                "/api/v1/me/push/subscriptions",
                Map.of("endpoint", endpoint, "p256dh", "BOAKI...", "auth", "c2VjcmV0"), token);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String publicId = (String) created.getBody().get("publicId");
        assertThat(created.getBody().get("active")).isEqualTo(true);
        // Ni la terminaison, ni les clés ne reviennent au client.
        assertThat(created.getBody()).doesNotContainKeys("endpoint", "p256dh", "auth");

        assertThat(exchange(HttpMethod.DELETE, "/api/v1/me/push/subscriptions/" + publicId, null, token)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map<String, Object>> status =
                exchange(HttpMethod.GET, "/api/v1/me/push/status", null, token);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subscriptions =
                (List<Map<String, Object>>) status.getBody().get("subscriptions");
        assertThat(subscriptions).anySatisfy(s -> {
            assertThat(s.get("publicId")).isEqualTo(publicId);
            // Révoqué, et non supprimé : sans quoi une page restée
            // ouverte le recréerait aussitôt.
            assertThat(s.get("active")).isEqualTo(false);
            assertThat(s.get("revokedAt")).isNotNull();
        });
    }

    @Test
    @DisplayName("le même appareil qui se réabonne réutilise sa ligne au lieu de la dupliquer")
    void resubscribingTheSameDeviceReusesTheRow() {
        String token = tokenFor(account(RoleCode.STUDENT));
        String endpoint = "https://push.example.test/" + UUID.randomUUID();
        Map<String, Object> body = Map.of("endpoint", endpoint, "p256dh", "K1", "auth", "A1");

        String first = (String) exchange(HttpMethod.POST, "/api/v1/me/push/subscriptions", body, token)
                .getBody().get("publicId");
        String second = (String) exchange(HttpMethod.POST, "/api/v1/me/push/subscriptions",
                Map.of("endpoint", endpoint, "p256dh", "K2", "auth", "A2"), token)
                .getBody().get("publicId");

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("une terminaison non HTTPS ou vide est refusée")
    void amalformedSubscriptionIsRejected() {
        String token = tokenFor(account(RoleCode.STUDENT));
        assertThat(exchange(HttpMethod.POST, "/api/v1/me/push/subscriptions",
                Map.of("endpoint", "http://push.example.test/x", "p256dh", "K", "auth", "A"), token)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange(HttpMethod.POST, "/api/v1/me/push/subscriptions",
                Map.of("endpoint", "", "p256dh", "K", "auth", "A"), token)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("l'abonnement d'autrui répond 404, jamais 403")
    void anotherUsersSubscriptionIsNotFound() {
        String alice = tokenFor(account(RoleCode.STUDENT));
        String bob = tokenFor(account(RoleCode.STUDENT));
        String publicId = (String) exchange(HttpMethod.POST, "/api/v1/me/push/subscriptions",
                Map.of("endpoint", "https://push.example.test/" + UUID.randomUUID(),
                        "p256dh", "K", "auth", "A"), alice)
                .getBody().get("publicId");

        // 404 et non 403 : un 403 confirmerait l'existence de l'appareil.
        assertThat(exchange(HttpMethod.DELETE, "/api/v1/me/push/subscriptions/" + publicId, null, bob)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("aucune poussée n'est mise en file pour un compte sans appareil abonné")
    void noPushIsQueuedWithoutASubscribedDevice() {
        Account teacher = account(RoleCode.TEACHER);
        deliver(UUID.randomUUID(), UUID.randomUUID(), teacher);

        Long queued = jdbc.queryForObject(
                "select count(*) from outbox_message where message_type = 'NOTIFICATION_PUSH'",
                Long.class);
        assertThat(queued).isZero();
    }

    @Test
    @DisplayName("les routes de préférences et d'abonnement exigent une authentification")
    void channelRoutesRequireAuthentication() {
        assertThat(exchange(HttpMethod.GET, "/api/v1/me/notification-preferences", null, null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange(HttpMethod.GET, "/api/v1/me/push/status", null, null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void deliver(UUID resource, UUID eventKey, Account recipient) {
        deliver(NotificationType.SESSION_CANCELLED, resource, eventKey, recipient);
    }

    private void deliver(NotificationType type, UUID resource, UUID eventKey, Account recipient) {
        notificationHandler.handle("cle-de-test-" + eventKey, NotificationRequest
                .of(type, "COURSE_SESSION", resource, eventKey)
                .label("Séance annulée", "corps neutre")
                .recipients(Set.of(UUID.fromString(recipient.publicId())))
                .build()
                .toPayload());
    }

    private long notificationRows(UUID resourcePublicId) {
        Long n = jdbc.queryForObject(
                "select count(*) from notification where resource_public_id = UUID_TO_BIN(?)",
                Long.class, resourcePublicId.toString());
        return n == null ? 0L : n;
    }

    private List<String> deliveryStatuses() {
        return jdbc.queryForList(
                "select concat(internal_status, '|', provider_status) from email_delivery "
                        + "where message_type = 'NOTIFICATION'", String.class);
    }

    private HttpStatus put(String token, String path, Map<String, Object> body) {
        return (HttpStatus) exchange(HttpMethod.PUT, path, body, token).getStatusCode();
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
        UserAccount a = new UserAccount("notif-chan-" + UUID.randomUUID() + "@esic-connect.test",
                "Notif", "Canal", AccountStatus.ACTIVE);
        a.setPasswordHash(passwordEncoder.encode(PASSWORD));
        a = userAccountRepository.saveAndFlush(a);
        for (RoleCode rc : roles) {
            Role role = roleRepository.findByCode(rc).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(a, role, Instant.now(), true));
        }
        return new Account(a.getPublicId().toString(), a.getEmail());
    }

    private String tokenFor(Account account) {
        return AuthTestSupport.accessToken(rest, account.email(), PASSWORD);
    }
}
