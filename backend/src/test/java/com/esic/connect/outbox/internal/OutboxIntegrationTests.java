package com.esic.connect.outbox.internal;

import com.esic.connect.outbox.OutboxHandler;
import com.esic.connect.outbox.OutboxPublisher;
import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.organization.OrganizationChangeAction;
import com.esic.connect.organization.OrganizationChangeEvent;
import com.esic.connect.organization.OrganizationResourceType;
import com.esic.connect.support.AuthTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox transactionnelle (EF-AUD-003, EF-OPS-005 ; RG-096, RG-097 ;
 * AC-027, AC-028).
 *
 * <p>Deux tentatives seulement, pour que le passage en <strong>file
 * d'échec</strong> soit observable sans attendre. Les reprises sont
 * déclenchées explicitement par le test, jamais laissées au hasard de
 * l'horloge.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.outbox.max-attempts=2",
        // Attente de reprise LONGUE, et c'est voulu : une attente quasi
        // nulle rendrait la ligne immédiatement re-réclamable à
        // l'intérieur du même passage du diffuseur, et le test compterait
        // deux tentatives là où il en attend une. Les reprises sont donc
        // déclenchées explicitement par le test.
        "app.outbox.retry-initial=PT30S",
        "app.outbox.retry-max=PT30S"
})
@ActiveProfiles("test")
class OutboxIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    /** Type routé vers le gestionnaire pilotable ci-dessous. */
    private static final String PROBE_TYPE = "TEST_PROBE";

    @TestConfiguration
    static class ProbeConfig {

        /**
         * Compteur d'appels <strong>par clé de sonde</strong>. Un compteur
         * global serait faussé par les messages laissés en file par les
         * tests précédents : le diffuseur traite toute la file à chaque
         * passage, et ces messages sont du même type.
         */
        static final ConcurrentHashMap<String, Integer> callsByProbe = new ConcurrentHashMap<>();
        /** Sondes dont le gestionnaire doit échouer. */
        static final Set<String> failing = new CopyOnWriteArraySet<>();

        static void reset() {
            callsByProbe.clear();
            failing.clear();
        }

        static int calls(String probe) {
            return callsByProbe.getOrDefault(probe, 0);
        }

        /**
         * Gestionnaire pilotable : il permet d'observer le comportement du
         * diffuseur — reprise, file d'échec, rejeu — sans provoquer de
         * panne artificielle dans un module métier.
         */
        @Bean
        OutboxHandler probeHandler() {
            return new OutboxHandler() {
                @Override
                public String messageType() {
                    return PROBE_TYPE;
                }

                @Override
                public void handle(String messageKey, Map<String, Object> payload) {
                    String probe = String.valueOf(payload.get("probe"));
                    callsByProbe.merge(probe, 1, Integer::sum);
                    if (failing.contains(probe)) {
                        throw new IllegalStateException("panne simulee de gestionnaire");
                    }
                }
            };
        }

        @Bean
        @Primary
        com.esic.connect.notification.internal.InvitationMailer noopMailer() {
            return (a, b, c, d) -> {
            };
        }
    }

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private OutboxPublisher outboxPublisher;
    @Autowired
    private OutboxDispatcher dispatcher;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
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
        ProbeConfig.reset();
    }

    // ------------------------------------------------------------------
    // AC-027 — une transaction annulée ne produit aucun effet de bord
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC-027 : une transaction annulée ne laisse ni ligne d'outbox, ni trace d'audit")
    void aRolledBackTransactionProducesNoSideEffectAtAll() {
        UUID resource = UUID.randomUUID();

        transactionTemplate.execute(tx -> {
            eventPublisher.publishEvent(new OrganizationChangeEvent(
                    OrganizationResourceType.SITE, resource, null,
                    OrganizationChangeAction.CREATED, "code=ROLLBACK"));
            tx.setRollbackOnly();
            return null;
        });

        assertThat(auditRowsFor(resource)).as("aucune trace d'audit").isZero();
        // La ligne d'outbox elle-même a disparu avec la transaction :
        // c'est là toute la différence avec l'écriture en REQUIRES_NEW,
        // qui laissait une trace de succès derrière une action annulée.
        assertThat(outboxRowsMentioning(resource)).as("aucune ligne d'outbox").isZero();
    }

    @Test
    @DisplayName("une transaction committée écrit sa trace via l'outbox, message marqué SENT")
    void aCommittedTransactionWritesItsAuditThroughTheOutbox() {
        UUID resource = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(tx -> eventPublisher.publishEvent(
                new OrganizationChangeEvent(OrganizationResourceType.SITE, resource, null,
                        OrganizationChangeAction.CREATED, "code=COMMIT")));

        assertThat(auditRowsFor(resource)).isEqualTo(1L);
        assertThat(auditOutboxKeyFor(resource)).as("la trace porte la clé du message").isNotNull();
        assertThat(sentAuditMessages()).isPositive();
    }

    @Test
    @DisplayName("rejouer le gestionnaire d'audit n'écrit pas la trace une seconde fois")
    void replayingTheAuditHandlerIsIdempotent() {
        UUID resource = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(tx -> eventPublisher.publishEvent(
                new OrganizationChangeEvent(OrganizationResourceType.SITE, resource, null,
                        OrganizationChangeAction.CREATED, "code=IDEM")));
        assertThat(auditRowsFor(resource)).isEqualTo(1L);

        // Remise en file du message déjà traité, puis nouveau passage :
        // la contrainte `uq_audit_event_outbox_key` empêche le doublon.
        String key = auditOutboxKeyFor(resource);
        jdbc.update("update outbox_message set status='PENDING', processed_at=null, "
                + "next_attempt_at=now(6) where dedup_key = ?", key);
        drain();

        assertThat(auditRowsFor(resource)).as("toujours une seule trace").isEqualTo(1L);
    }

    // ------------------------------------------------------------------
    // AC-028 — reprise, file d'échec, rejeu manuel (EF-OPS-005)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC-028 : un effet de bord en échec est repris, puis placé en file d'échec")
    void aFailingSideEffectIsRetriedThenParkedInTheDeadLetterQueue() {
        String key = "sonde-" + UUID.randomUUID();
        ProbeConfig.failing.add(key);
        outboxPublisher.enqueue(PROBE_TYPE, key, Map.of("probe", key));

        // Première tentative : déclenchée par le drain immédiat.
        assertThat(ProbeConfig.calls(key)).isEqualTo(1);
        assertThat(statusOf(PROBE_TYPE, key)).isEqualTo("FAILED");

        // Seconde tentative (max-attempts=2) : passage en file d'échec.
        drain();
        assertThat(ProbeConfig.calls(key)).isEqualTo(2);
        assertThat(statusOf(PROBE_TYPE, key)).isEqualTo("DEAD");

        // Un message en file d'échec n'est plus repris tout seul : il
        // attend une décision humaine, sans quoi la file n'aurait aucun
        // sens.
        drain();
        assertThat(ProbeConfig.calls(key)).isEqualTo(2);
    }

    @Test
    @DisplayName("EF-OPS-005 : un effet de bord en file d'échec est rejouable et aboutit")
    void aDeadMessageCanBeReplayedByAnAdministrator() {
        String key = "rejeu-" + UUID.randomUUID();
        ProbeConfig.failing.add(key);
        outboxPublisher.enqueue(PROBE_TYPE, key, Map.of("probe", key));
        drain();
        assertThat(statusOf(PROBE_TYPE, key)).isEqualTo("DEAD");

        ProbeConfig.failing.remove(key);
        String publicId = publicIdOf(PROBE_TYPE, key);
        assertThat(post(adminToken(), "/api/v1/outbox/messages/" + publicId + "/replay"))
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(statusOf(PROBE_TYPE, key)).as("le rejeu aboutit sans attendre").isEqualTo("SENT");
        assertThat(ProbeConfig.calls(key)).as("deux échecs, puis le rejeu").isEqualTo(3);
    }

    @Test
    @DisplayName("le rejeu est refusé sur un message qui n'est pas en file d'échec")
    void replayingANonDeadMessageIsRefused() {
        String key = "deja-traite-" + UUID.randomUUID();
        outboxPublisher.enqueue(PROBE_TYPE, key, Map.of("probe", key));
        assertThat(statusOf(PROBE_TYPE, key)).isEqualTo("SENT");

        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST,
                "/api/v1/outbox/messages/" + publicIdOf(PROBE_TYPE, key) + "/replay", null, adminToken());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("OUTBOX_NOT_REPLAYABLE");
    }

    @Test
    @DisplayName("un type sans gestionnaire finit en file d'échec, jamais ignoré en silence")
    void aMessageWithoutHandlerEndsInTheDeadLetterQueue() {
        String key = "sans-gestionnaire-" + UUID.randomUUID();
        outboxPublisher.enqueue("TYPE_INEXISTANT", key, Map.of());
        drain();

        assertThat(statusOf("TYPE_INEXISTANT", key)).isEqualTo("DEAD");
        assertThat(lastErrorOf("TYPE_INEXISTANT", key)).startsWith("NO_HANDLER:");
    }

    @Test
    @DisplayName("deux enregistrements de la même occurrence ne créent qu'une ligne")
    void enqueuingTheSameOccurrenceTwiceCreatesOneRow() {
        String key = "idempotent-" + UUID.randomUUID();
        assertThat(outboxPublisher.enqueue(PROBE_TYPE, key, Map.of("probe", key))).isTrue();
        assertThat(outboxPublisher.enqueue(PROBE_TYPE, key, Map.of("probe", key))).isFalse();
        assertThat(rowsFor(PROBE_TYPE, key)).isEqualTo(1L);
    }

    // ------------------------------------------------------------------
    // Autorisations
    // ------------------------------------------------------------------

    @Test
    @DisplayName("la file d'échec n'est ni lisible ni rejouable hors administration")
    void theDeadLetterQueueIsRestrictedToAdministrators() {
        assertThat(exchange(HttpMethod.GET, "/api/v1/outbox/messages", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        String teacher = tokenFor(account(RoleCode.TEACHER));
        assertThat(exchange(HttpMethod.GET, "/api/v1/outbox/messages", null, teacher).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(HttpMethod.POST,
                "/api/v1/outbox/messages/" + UUID.randomUUID() + "/replay", null, teacher).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        String admin = adminToken();
        assertThat(exchange(HttpMethod.GET, "/api/v1/outbox/messages?status=DEAD", null, admin)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange(HttpMethod.GET, "/api/v1/outbox/messages/summary", null, admin)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("un identifiant inconnu ou mal formé répond 404, sans distinction")
    void anUnknownMessageIsNotFound() {
        String admin = adminToken();
        assertThat(exchange(HttpMethod.POST,
                "/api/v1/outbox/messages/" + UUID.randomUUID() + "/replay", null, admin).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange(HttpMethod.POST, "/api/v1/outbox/messages/pas-un-uuid/replay", null, admin)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange(HttpMethod.GET, "/api/v1/outbox/messages?status=INCONNU", null, admin)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("l'écran d'exploitation ne renvoie jamais le contenu du message")
    void theOperationsViewNeverExposesThePayload() {
        String key = "confidentiel-" + UUID.randomUUID();
        outboxPublisher.enqueue(PROBE_TYPE, key,
                Map.of("probe", key, "valeurMetier", "SECRET-A-NE-PAS-EXPOSER"));

        ResponseEntity<Map<String, Object>> page = exchange(HttpMethod.GET,
                "/api/v1/outbox/messages?size=100", null, adminToken());
        assertThat(page.getBody().toString()).doesNotContain("SECRET-A-NE-PAS-EXPOSER");
        assertThat(page.getBody().toString()).doesNotContain("dedupKey");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Force un passage du diffuseur, en rendant d'abord visibles les
     * lignes dont la reprise était différée. Appel direct plutôt que par
     * un message supplémentaire : ce dernier serait lui-même traité et
     * fausserait les comptages.
     */
    private void drain() {
        jdbc.update("update outbox_message set next_attempt_at = now(6) "
                + "where status in ('PENDING','FAILED')");
        dispatcher.drain();
    }

    private String hashed(String messageType, String rawKey) {
        // Même dérivation que l'implémentation : SHA-256 de « type|clé ».
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest((messageType + '|' + rawKey)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(out);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private String statusOf(String messageType, String rawKey) {
        return jdbc.queryForObject("select status from outbox_message where dedup_key = ?",
                String.class, hashed(messageType, rawKey));
    }

    private String lastErrorOf(String messageType, String rawKey) {
        return jdbc.queryForObject("select last_error from outbox_message where dedup_key = ?",
                String.class, hashed(messageType, rawKey));
    }

    private String publicIdOf(String messageType, String rawKey) {
        return jdbc.queryForObject("select bin_to_uuid(public_id) from outbox_message where dedup_key = ?",
                String.class, hashed(messageType, rawKey));
    }

    private long rowsFor(String messageType, String rawKey) {
        Long n = jdbc.queryForObject("select count(*) from outbox_message where dedup_key = ?",
                Long.class, hashed(messageType, rawKey));
        return n == null ? 0L : n;
    }

    private long auditRowsFor(UUID resourcePublicId) {
        Long n = jdbc.queryForObject(
                "select count(*) from audit_event where resource_public_id = UUID_TO_BIN(?)",
                Long.class, resourcePublicId.toString());
        return n == null ? 0L : n;
    }

    private String auditOutboxKeyFor(UUID resourcePublicId) {
        return jdbc.queryForObject(
                "select outbox_key from audit_event where resource_public_id = UUID_TO_BIN(?)",
                String.class, resourcePublicId.toString());
    }

    private long outboxRowsMentioning(UUID resourcePublicId) {
        Long n = jdbc.queryForObject(
                "select count(*) from outbox_message where json_extract(payload, '$.resourcePublicId') = ?",
                Long.class, resourcePublicId.toString());
        return n == null ? 0L : n;
    }

    private long sentAuditMessages() {
        Long n = jdbc.queryForObject(
                "select count(*) from outbox_message where message_type='AUDIT' and status='SENT'",
                Long.class);
        return n == null ? 0L : n;
    }

    private HttpStatus post(String token, String path) {
        return (HttpStatus) exchange(HttpMethod.POST, path, null, token).getStatusCode();
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
        UserAccount a = new UserAccount("outbox-" + UUID.randomUUID() + "@esic-connect.test",
                "Outbox", "Tester", AccountStatus.ACTIVE);
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
        return AuthTestSupport.accessToken(rest, account.email(), PASSWORD);
    }
}
