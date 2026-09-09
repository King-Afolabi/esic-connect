package com.esic.connect.identity;

import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
import com.esic.connect.support.AuthTestSupport;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comparaison contrôlée de deux doublons (ANO-USER-001 ; docs/02 §9.5).
 *
 * <p>Le point vérifié en priorité : <strong>strictement rien n'est
 * écrit</strong>. La comparaison est une simulation en lecture seule —
 * aucune fusion, aucune mutation, aucun message d'outbox, aucune
 * notification, aucune trace d'audit, {@code updated_at} intact.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DuplicateComparisonIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    private static final String URL = "/api/v1/users/duplicates/compare";

    /** Décomptes / attributs simulés, poussés par un autre module. Réinitialisés par test. */
    static final Map<Long, Map<String, Long>> COUNTS = new ConcurrentHashMap<>();
    static final Map<Long, Map<String, String>> ATTRS = new ConcurrentHashMap<>();

    @TestConfiguration
    static class TestContributorConfig {
        @Bean
        @Primary
        InvitationMailer noopInvitationMailer() {
            return (toEmail, firstName, rawToken, expiresAt) -> {
            };
        }

        /** Contributeur factice : joue le rôle d'enrollment / attendance / claim… */
        @Bean
        DuplicateDependencyContributor testDuplicateDependencyContributor() {
            return new DuplicateDependencyContributor() {
                @Override
                public Map<String, Long> countsFor(long userInternalId) {
                    return COUNTS.getOrDefault(userInternalId, Map.of());
                }

                @Override
                public Map<String, String> attributesFor(long userInternalId) {
                    return ATTRS.getOrDefault(userInternalId, Map.of());
                }
            };
        }
    }

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    // ------------------------------------------------------------------
    // Autorisations
    // ------------------------------------------------------------------

    @Test
    void unAdministrateurCompareDeuxDoublons() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount a = persistNamed("Awa", "Diallo");
        UserAccount b = persistNamed("awa", "diallo");

        ResponseEntity<Map<String, Object>> response = compare(admin, a.getPublicId(), b.getPublicId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).containsKeys("first", "second", "matchingFields", "differentFields",
                "conflicts", "warnings", "consequences", "dependencySummary", "assessment", "reasons");
        assertThat(body.get("matchingFields").toString()).contains("normalizedName");
    }

    @Test
    void unRoleNonAutoriseEstRefuse() {
        String teacher = tokenFor(RoleCode.TEACHER);
        UserAccount a = persistNamed("Awa", "Diallo");
        UserAccount b = persistNamed("Awa", "Diallo");

        assertThat(compare(teacher, a.getPublicId(), b.getPublicId()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void laComparaisonEstRefuseeSansAuthentification() {
        UserAccount a = persistNamed("Awa", "Diallo");
        UserAccount b = persistNamed("Awa", "Diallo");
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                RequestEntity.post(URI.create(URL)).contentType(MediaType.APPLICATION_JSON)
                        .body(bodyOf(a.getPublicId(), b.getPublicId())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------
    // Validation d'entrée
    // ------------------------------------------------------------------

    @Test
    void deuxIdentifiantsIdentiquesSontRefuses() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount a = persistNamed("Awa", "Diallo");

        ResponseEntity<Map<String, Object>> response = compare(admin, a.getPublicId(), a.getPublicId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("USER_COMPARE_SAME");
    }

    @Test
    void unIdentifiantInconnuEstUn404() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount a = persistNamed("Awa", "Diallo");

        ResponseEntity<Map<String, Object>> response =
                compare(admin, a.getPublicId(), UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // Calcul de la comparaison
    // ------------------------------------------------------------------

    @Test
    void lesCorrespondancesSontCalculees() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount a = persistWithPhone("Awa", "Diallo", "06 12 34 56 78");
        UserAccount b = persistWithPhone("Awa", "Diallo", "0612345678");

        Map<String, Object> body = compare(admin, a.getPublicId(), b.getPublicId()).getBody();

        assertThat(body.get("matchingFields").toString()).contains("normalizedName").contains("phone");
    }

    @Test
    void lesDifferencesSontCalculees() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount a = persistNamed("Awa", "Diallo");
        UserAccount b = persistNamed("Awa", "Traore");

        Map<String, Object> body = compare(admin, a.getPublicId(), b.getPublicId()).getBody();

        assertThat(body.get("differentFields").toString()).contains("name").contains("email");
    }

    @Test
    void unConflitDIdentiteRendLaFusionImpossible() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount a = persistNamed("Awa", "Diallo");
        UserAccount b = persistNamed("Boris", "Petit");

        Map<String, Object> body = compare(admin, a.getPublicId(), b.getPublicId()).getBody();

        assertThat(body.get("assessment")).isEqualTo("NOT_MERGEABLE");
        assertThat(body.get("conflicts").toString()).contains("DIFFERENT_IDENTITY");
    }

    @Test
    void deuxNumerosEtudiantEtDeuxInscriptionsActivesSontUnConflitBloquant() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount a = persistNamed("Awa", "Diallo");
        UserAccount b = persistNamed("Awa", "Diallo");
        // Le module enrollment « remonte » : profil, inscription active, numéro.
        COUNTS.put(a.getId(), Map.of("studentProfile", 1L, "activeEnrollments", 1L, "enrollments", 3L));
        COUNTS.put(b.getId(), Map.of("studentProfile", 1L, "activeEnrollments", 1L, "enrollments", 2L));
        ATTRS.put(a.getId(), Map.of("studentNumber", "ESIC-2026-00001"));
        ATTRS.put(b.getId(), Map.of("studentNumber", "ESIC-2026-09999"));

        Map<String, Object> body = compare(admin, a.getPublicId(), b.getPublicId()).getBody();

        assertThat(body.get("assessment")).isEqualTo("NOT_MERGEABLE");
        assertThat(body.get("conflicts").toString())
                .contains("DIFFERENT_STUDENT_NUMBERS")
                .contains("BOTH_HAVE_ACTIVE_ENROLLMENT");
    }

    @Test
    void leResumeDesDependancesAdditionneLesDeuxComptes() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount a = persistNamed("Awa", "Diallo");
        UserAccount b = persistNamed("Awa", "Diallo");
        COUNTS.put(a.getId(), Map.of("attendanceRecords", 12L, "claims", 1L));
        COUNTS.put(b.getId(), Map.of("attendanceRecords", 4L, "notifications", 7L));

        Map<String, Object> body = compare(admin, a.getPublicId(), b.getPublicId()).getBody();

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) body.get("dependencySummary");
        assertThat(((Number) summary.get("attendanceRecords")).longValue()).isEqualTo(16L);
        assertThat(((Number) summary.get("claims")).longValue()).isEqualTo(1L);
        assertThat(((Number) summary.get("notifications")).longValue()).isEqualTo(7L);
        // identity contribue toujours ses propres décomptes.
        assertThat(summary).containsKeys("invitations", "passkeys", "trustedDevices", "activeRoles");
    }

    @Test
    void unHistoriquePorteParUnSeulCompteAvecIdentiteConcordanteEstPotentiellementSur() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount full = persistNamed("Awa", "Diallo");
        // Cas canonique : une réinvitation a créé un compte fantôme —
        // même nom, en attente d'activation, sans mot de passe ni rôle ni
        // historique. Seule l'adresse diffère (attendu).
        UserAccount empty = persistPending("Awa", "Diallo");
        COUNTS.put(full.getId(), Map.of("attendanceRecords", 30L, "enrollments", 2L));

        Map<String, Object> body = compare(admin, full.getPublicId(), empty.getPublicId()).getBody();

        assertThat(body.get("assessment")).isEqualTo("POTENTIALLY_SAFE");
    }

    // ------------------------------------------------------------------
    // Lecture seule — aucune écriture, aucun effet de bord
    // ------------------------------------------------------------------

    @Test
    void aucuneEntiteNEstModifiee() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount a = persistNamed("Awa", "Diallo");
        UserAccount b = persistNamed("Awa", "Diallo");
        Instant updatedA = reload(a).getUpdatedAt();
        Instant updatedB = reload(b).getUpdatedAt();

        compare(admin, a.getPublicId(), b.getPublicId());

        assertThat(reload(a).getUpdatedAt()).isEqualTo(updatedA);
        assertThat(reload(b).getUpdatedAt()).isEqualTo(updatedB);
        assertThat(reload(a).getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(reload(b).getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void aucunEffetDeBordNiTraceNiNotification() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount a = persistNamed("Awa", "Diallo");
        UserAccount b = persistNamed("Awa", "Diallo");

        long outboxBefore = count("outbox_message");
        long auditBefore = count("audit_event");
        long notifBefore = count("notification");

        compare(admin, a.getPublicId(), b.getPublicId());

        assertThat(count("outbox_message")).isEqualTo(outboxBefore);
        assertThat(count("audit_event")).isEqualTo(auditBefore);
        assertThat(count("notification")).isEqualTo(notifBefore);
    }

    @Test
    void aucunSecretDansLaReponse() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount a = persistNamed("Awa", "Diallo");
        UserAccount b = persistNamed("Awa", "Diallo");

        ResponseEntity<String> raw = rest.exchange(RequestEntity.post(URI.create(URL))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(bodyOf(a.getPublicId(), b.getPublicId())),
                String.class);

        assertThat(raw.getStatusCode()).isEqualTo(HttpStatus.OK);
        String json = raw.getBody().toLowerCase(java.util.Locale.ROOT);
        assertThat(json).doesNotContain("passwordhash").doesNotContain("password_hash")
                .doesNotContain("tokenhash").doesNotContain("secret")
                .doesNotContain("recoverycode");
    }

    @Test
    void leCoutSqlEstBorneEtStableAvecUnHistoriqueVolumineux() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount a = persistNamed("Awa", "Diallo");
        UserAccount b = persistNamed("Awa", "Diallo");

        COUNTS.put(a.getId(), Map.of("attendanceRecords", 3L));
        COUNTS.put(b.getId(), Map.of("attendanceRecords", 3L));
        long light = queriesFor(() -> compare(admin, a.getPublicId(), b.getPublicId()));

        COUNTS.put(a.getId(), Map.of("attendanceRecords", 500_000L, "enrollments", 40L));
        COUNTS.put(b.getId(), Map.of("attendanceRecords", 900_000L, "enrollments", 80L));
        long heavy = queriesFor(() -> compare(admin, a.getPublicId(), b.getPublicId()));

        // Les décomptes sont fournis par le contributeur : le volume
        // d'historique ne change pas le nombre de requêtes émises.
        assertThat(heavy).isEqualTo(light);
        assertThat(light).isLessThan(60);
    }

    // ------------------------------------------------------------------

    private long queriesFor(Runnable action) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        action.run();
        return statistics.getPrepareStatementCount();
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return value == null ? 0L : value;
    }

    private ResponseEntity<Map<String, Object>> compare(String token, UUID first, UUID second) {
        return rest.exchange(RequestEntity.post(URI.create(URL))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).body(bodyOf(first, second)),
                new ParameterizedTypeReference<>() {
                });
    }

    private static Map<String, Object> bodyOf(UUID first, UUID second) {
        Map<String, Object> body = new HashMap<>();
        body.put("firstUserId", first == null ? null : first.toString());
        body.put("secondUserId", second == null ? null : second.toString());
        return body;
    }

    private UserAccount reload(UserAccount account) {
        return userAccountRepository.findById(account.getId()).orElseThrow();
    }

    private UserAccount persistNamed(String firstName, String lastName) {
        return persistWithPhone(firstName, lastName, null);
    }

    private UserAccount persistWithPhone(String firstName, String lastName, String phone) {
        UserAccount account = new UserAccount("cmp-" + UUID.randomUUID() + "@esic-connect.test",
                firstName, lastName, AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        if (phone != null) {
            account.updatePhone(phone, null);
        }
        return userAccountRepository.saveAndFlush(account);
    }

    private UserAccount persistPending(String firstName, String lastName) {
        UserAccount account = new UserAccount("cmp-" + UUID.randomUUID() + "@esic-connect.test",
                firstName, lastName, AccountStatus.PENDING_ACTIVATION);
        return userAccountRepository.saveAndFlush(account);
    }

    private UserAccount persistUser(RoleCode roleCode) {
        UserAccount account = new UserAccount("cmp-" + UUID.randomUUID() + "@esic-connect.test",
                "Lot", "Testeur", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        return account;
    }

    private String tokenFor(RoleCode roleCode) {
        UserAccount account = persistUser(roleCode);
        return AuthTestSupport.accessToken(rest, account.getEmail(), PASSWORD);
    }

    @org.junit.jupiter.api.AfterEach
    void clearContributedData() {
        COUNTS.clear();
        ATTRS.clear();
    }
}
