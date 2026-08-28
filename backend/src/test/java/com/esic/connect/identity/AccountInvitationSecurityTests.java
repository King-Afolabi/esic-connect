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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrôles de sécurité du parcours d'invitation :
 * <ul>
 *   <li>émission refusée sans jeton (401) et avec un rôle non habilité (403) ;</li>
 *   <li>validation publique : jamais de donnée personnelle, réponse
 *       identique pour tout jeton invalide ;</li>
 *   <li>activation avec un jeton inconnu : erreur générique, sans fuite.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AccountInvitationSecurityTests {

    private static final String PASSWORD = "S3curePass!word";

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
    private PasswordEncoder passwordEncoder;

    @Test
    void issueRejectsAnonymousRequestWith401() {
        ResponseEntity<String> response = restTemplate.exchange(
                RequestEntity.post("/api/v1/account-invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", "someone@esic-connect.test", "role", "STUDENT")),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void issueRejectsNonPrivilegedRoleWith403() {
        UserAccount student = persistUser(AccountStatus.ACTIVE, RoleCode.STUDENT);
        String token = bearerToken(student.getEmail());

        ResponseEntity<String> response = restTemplate.exchange(
                RequestEntity.post("/api/v1/account-invitations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", "someone@esic-connect.test", "role", "STUDENT")),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void publicValidationNeverExposesPersonalDataAndIsUniformForInvalidTokens() {
        UserAccount pending = persistUser(AccountStatus.PENDING_ACTIVATION, null);
        // Aucune invitation émise : le compte existe mais aucun jeton valide.
        ResponseEntity<Map<String, Object>> unknown = restTemplate.exchange(
                RequestEntity.get(validateUri("totally-unknown")).build(), mapType());
        ResponseEntity<Map<String, Object>> alsoUnknown = restTemplate.exchange(
                RequestEntity.get(validateUri("another-unknown-" + UUID.randomUUID())).build(), mapType());

        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unknown.getBody()).isEqualTo(Map.of("valid", false));
        assertThat(alsoUnknown.getBody()).isEqualTo(unknown.getBody());
        // Aucune clé email / nom / rôle / statut.
        assertThat(unknown.getBody().keySet()).containsExactly("valid");
        assertThat(pending.getEmail()).isNotNull(); // le compte n'a jamais été divulgué par l'API
    }

    @Test
    void activateWithUnknownTokenReturnsGenericInvalidError() {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.post("/api/v1/account-invitations/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("token", "unknown-" + UUID.randomUUID(), "password", PASSWORD)),
                mapType());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("INVITATION_INVALID");
        assertThat(response.getBody().get("message")).isEqualTo("Lien d'activation invalide ou expire.");
        // Le corps ne contient que le format d'erreur générique : aucune
        // donnée personnelle ni indication du motif exact.
        assertThat(response.getBody()).doesNotContainKeys("email", "firstName", "lastName", "role");
    }

    private String bearerToken(String email) {
        ResponseEntity<Map<String, Object>> login = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "password", PASSWORD)),
                mapType());
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) login.getBody().get("accessToken");
    }

    private UserAccount persistUser(AccountStatus status, RoleCode roleCode) {
        UserAccount account = new UserAccount("sec-inv-" + UUID.randomUUID() + "@esic-connect.test",
                "Prenom", "Nom", status);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        if (roleCode != null) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return account;
    }

    private URI validateUri(String token) {
        return URI.create("/api/v1/account-invitations/validate?token=" + token);
    }

    private static ParameterizedTypeReference<Map<String, Object>> mapType() {
        return new ParameterizedTypeReference<>() {
        };
    }
}
