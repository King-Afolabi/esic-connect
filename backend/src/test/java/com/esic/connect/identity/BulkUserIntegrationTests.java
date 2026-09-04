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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opérations de masse et détection de doublons (EF-USER-004,
 * EF-USER-005 ; docs/02 §9.4 et §9.5 ; RG-034).
 *
 * <p>Le point vérifié en priorité : <strong>sans confirmation
 * explicite, rien n'est écrit</strong>. Une opération de masse est
 * précisément le geste où une erreur coûte le plus cher.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BulkUserIntegrationTests {

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
    private TestRestTemplate rest;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------------
    // EF-USER-004 — prévisualisation obligatoire
    // ------------------------------------------------------------------

    @Test
    void sansConfirmationRienNEstEcrit() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount first = persistUser(RoleCode.STUDENT, AccountStatus.ACTIVE);
        UserAccount second = persistUser(RoleCode.STUDENT, AccountStatus.ACTIVE);

        Map<String, Object> preview = bulk(admin, "SUSPEND",
                List.of(first.getPublicId().toString(), second.getPublicId().toString()),
                "Départ de l'établissement", null).getBody();

        assertThat(preview.get("applied")).isEqualTo(false);
        assertThat(preview.get("eligible")).isEqualTo(2);
        // RG-034 : la prévisualisation chiffre, elle n'écrit pas.
        assertThat(reload(first).getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(reload(second).getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void laConfirmationAppliqueLAction() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount target = persistUser(RoleCode.STUDENT, AccountStatus.ACTIVE);

        Map<String, Object> result = bulk(admin, "SUSPEND",
                List.of(target.getPublicId().toString()), "Départ", true).getBody();

        assertThat(result.get("applied")).isEqualTo(true);
        assertThat(result.get("eligible")).isEqualTo(1);
        assertThat(reload(target).getStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    void unCompteDejaDansLEtatVisEstIgnoreSansEtreUneErreur() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount alreadySuspended = persistUser(RoleCode.STUDENT, AccountStatus.SUSPENDED);
        UserAccount active = persistUser(RoleCode.STUDENT, AccountStatus.ACTIVE);

        Map<String, Object> preview = bulk(admin, "SUSPEND",
                List.of(alreadySuspended.getPublicId().toString(), active.getPublicId().toString()),
                "Nettoyage", null).getBody();

        assertThat(preview.get("eligible")).isEqualTo(1);
        assertThat(preview.get("ignored")).isEqualTo(1);
        assertThat(preview.get("rejected")).isEqualTo(0);
    }

    @Test
    void unIdentifiantInconnuEstRefuseSansFaireEchouerLeLot() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount valid = persistUser(RoleCode.STUDENT, AccountStatus.ACTIVE);

        Map<String, Object> result = bulk(admin, "SUSPEND",
                List.of(valid.getPublicId().toString(), UUID.randomUUID().toString()),
                "Départ", true).getBody();

        assertThat(result.get("eligible")).isEqualTo(1);
        assertThat(result.get("rejected")).isEqualTo(1);
        // Le compte valide a bien basculé : un identifiant fautif ne doit
        // pas priver les autres de l'opération.
        assertThat(reload(valid).getStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    void unSuperAdministrateurEstProtegeDansUnLot() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount superAdmin = persistUser(RoleCode.SUPER_ADMIN, AccountStatus.ACTIVE);

        Map<String, Object> result = bulk(admin, "SUSPEND",
                List.of(superAdmin.getPublicId().toString()), "Tentative", true).getBody();

        assertThat(result.get("rejected")).isEqualTo(1);
        assertThat(reload(superAdmin).getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.toString()).contains("super administrateur");
    }

    @Test
    void lActionGroupeeEstFermeeAuxRolesNonAutorises() {
        String teacher = tokenFor(RoleCode.TEACHER);
        UserAccount target = persistUser(RoleCode.STUDENT, AccountStatus.ACTIVE);

        assertThat(bulk(teacher, "SUSPEND", List.of(target.getPublicId().toString()), "x", true)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void uneActionInconnueEstRefusee() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount target = persistUser(RoleCode.STUDENT, AccountStatus.ACTIVE);

        assertThat(bulk(admin, "SUPPRIMER", List.of(target.getPublicId().toString()), "x", true)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void laReemissionGroupeeNeViseQueLesComptesEnAttente() {
        String admin = tokenFor(RoleCode.ADMIN);
        UserAccount pending = persistUser(RoleCode.STUDENT, AccountStatus.PENDING_ACTIVATION);
        UserAccount active = persistUser(RoleCode.STUDENT, AccountStatus.ACTIVE);

        Map<String, Object> preview = bulk(admin, "RESEND_INVITATION",
                List.of(pending.getPublicId().toString(), active.getPublicId().toString()),
                "Relance", null).getBody();

        assertThat(preview.get("eligible")).isEqualTo(1);
        assertThat(preview.get("ignored")).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // EF-USER-005 — doublons
    // ------------------------------------------------------------------

    @Test
    void deuxComptesDeMemeNomSontSignalesSansEtreFusionnes() {
        String admin = tokenFor(RoleCode.ADMIN);
        String marker = UUID.randomUUID().toString().substring(0, 8);
        UserAccount first = persistNamed("Camille", "Bernard" + marker);
        UserAccount second = persistNamed("camille", "BERNARD" + marker);

        List<Map<String, Object>> groups = duplicates(admin);

        Map<String, Object> group = groups.stream()
                .filter(candidate -> candidate.get("signature").toString()
                        .contains(("bernard" + marker).toLowerCase(java.util.Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("groupe de doublons attendu : " + groups));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> accounts = (List<Map<String, Object>>) group.get("accounts");
        assertThat(accounts).hasSize(2);
        // Signalés, pas fusionnés : les deux comptes existent toujours
        // (docs/02 §9.5 — la suppression reste une action humaine).
        assertThat(reload(first)).isNotNull();
        assertThat(reload(second)).isNotNull();
    }

    @Test
    void desNomsDifferentsNeSontPasSignalesCommeDoublons() {
        String admin = tokenFor(RoleCode.ADMIN);
        String marker = UUID.randomUUID().toString().substring(0, 8);
        persistNamed("Alice", "Martin" + marker);
        persistNamed("Bob", "Durand" + marker);

        List<Map<String, Object>> groups = duplicates(admin);

        assertThat(groups).noneSatisfy(group ->
                assertThat(group.get("signature").toString()).contains("alice"));
    }

    @Test
    void laDetectionDeDoublonsEstReserveeAuxAdministrateurs() {
        String schoolAdmin = tokenFor(RoleCode.SCHOOL_ADMINISTRATION);

        assertThat(rest.exchange(RequestEntity.get(URI.create("/api/v1/users/duplicates"))
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + schoolAdmin).build(),
                        new ParameterizedTypeReference<Map<String, Object>>() {
                        })
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------

    private ResponseEntity<Map<String, Object>> bulk(String token, String action, List<String> ids,
                                                     String reason, Boolean confirm) {
        Map<String, Object> body = new HashMap<>();
        body.put("action", action);
        body.put("userIds", ids);
        body.put("reason", reason);
        if (confirm != null) {
            body.put("confirm", confirm);
        }
        return rest.exchange(RequestEntity.post(URI.create("/api/v1/users/bulk"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).body(body),
                new ParameterizedTypeReference<>() {
                });
    }

    private List<Map<String, Object>> duplicates(String token) {
        return rest.exchange(RequestEntity.get(URI.create("/api/v1/users/duplicates"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
    }

    private UserAccount reload(UserAccount account) {
        return userAccountRepository.findById(account.getId()).orElseThrow();
    }

    private UserAccount persistNamed(String firstName, String lastName) {
        UserAccount account = new UserAccount("dup-" + UUID.randomUUID() + "@esic-connect.test",
                firstName, lastName, AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return userAccountRepository.saveAndFlush(account);
    }

    private UserAccount persistUser(RoleCode roleCode, AccountStatus status) {
        UserAccount account = new UserAccount("bulk-" + UUID.randomUUID() + "@esic-connect.test",
                "Lot", "Testeur", status);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        return account;
    }

    private String tokenFor(RoleCode roleCode) {
        UserAccount account = persistUser(roleCode, AccountStatus.ACTIVE);
        return AuthTestSupport.accessToken(rest, account.getEmail(), PASSWORD);
    }
}
