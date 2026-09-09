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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Changement volontaire de mot de passe par un utilisateur connecté
 * ({@code POST /api/v1/auth/change-password}, docs/02 §17.1, §34.2).
 *
 * <p>Vérifie : succès et bascule effective du mot de passe, refus si le
 * mot de passe actuel est faux (sans le changer), refus si le nouveau
 * est identique à l'actuel, refus si le nouveau viole la politique,
 * indépendance du rôle, et surtout qu'un utilisateur ne change que
 * <strong>son propre</strong> mot de passe (le compte visé est le sujet
 * du jeton, jamais un paramètre).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PasswordChangeIntegrationTests {

    private static final String OLD_PASSWORD = "AncienMotDePasse!2026";
    private static final String NEW_PASSWORD = "cheval batterie agrafe correct";

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
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void useJdkClient() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void changesThePasswordAndInvalidatesTheOldOne() {
        String email = account(RoleCode.STUDENT);
        String token = AuthTestSupport.accessToken(rest, email, OLD_PASSWORD);

        assertThat(changePassword(token, OLD_PASSWORD, NEW_PASSWORD)).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(AuthTestSupport.login(rest, email, OLD_PASSWORD).getStatusCode())
                .as("l'ancien mot de passe ne fonctionne plus")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(AuthTestSupport.login(rest, email, NEW_PASSWORD).getStatusCode())
                .as("le nouveau mot de passe fonctionne")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void rejectsAWrongCurrentPasswordWithoutChangingAnything() {
        String email = account(RoleCode.STUDENT);
        String token = AuthTestSupport.accessToken(rest, email, OLD_PASSWORD);

        ResponseEntity<Map<String, Object>> response = changePasswordBody(token,
                "mauvais-mot-de-passe-actuel", NEW_PASSWORD);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("AUTH_CURRENT_PASSWORD_INVALID");

        assertThat(AuthTestSupport.login(rest, email, OLD_PASSWORD).getStatusCode())
                .as("le mot de passe est inchangé après un échec")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void rejectsANewPasswordIdenticalToTheCurrentOne() {
        String email = account(RoleCode.STUDENT);
        String token = AuthTestSupport.accessToken(rest, email, OLD_PASSWORD);

        ResponseEntity<Map<String, Object>> response = changePasswordBody(token, OLD_PASSWORD, OLD_PASSWORD);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("AUTH_PASSWORD_UNCHANGED");
    }

    @Test
    void rejectsANewPasswordThatViolatesThePolicy() {
        String email = account(RoleCode.STUDENT);
        String token = AuthTestSupport.accessToken(rest, email, OLD_PASSWORD);

        ResponseEntity<Map<String, Object>> response = changePasswordBody(token, OLD_PASSWORD, "court");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("AUTH_PASSWORD_TOO_WEAK");
    }

    @Test
    void isAvailableToAnyRoleIncludingPedagogicalManager() {
        String email = account(RoleCode.PEDAGOGICAL_MANAGER);
        String token = AuthTestSupport.accessToken(rest, email, OLD_PASSWORD);

        assertThat(changePassword(token, OLD_PASSWORD, NEW_PASSWORD)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(AuthTestSupport.login(rest, email, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void requiresAuthentication() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                RequestEntity.post(URI.create("/api/v1/auth/change-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("currentPassword", OLD_PASSWORD, "newPassword", NEW_PASSWORD)),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void onlyChangesTheCallersOwnPassword() {
        String alice = account(RoleCode.STUDENT);
        String bob = account(RoleCode.STUDENT);
        String aliceToken = AuthTestSupport.accessToken(rest, alice, OLD_PASSWORD);

        assertThat(changePassword(aliceToken, OLD_PASSWORD, NEW_PASSWORD)).isEqualTo(HttpStatus.NO_CONTENT);

        // Le compte de Bob est intact : le changement ne porte que sur le
        // sujet du jeton, il n'existe aucun moyen de viser un autre compte.
        assertThat(AuthTestSupport.login(rest, bob, OLD_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------------

    private HttpStatus changePassword(String token, String current, String next) {
        return (HttpStatus) changePasswordBody(token, current, next).getStatusCode();
    }

    private ResponseEntity<Map<String, Object>> changePasswordBody(String token, String current,
                                                                   String next) {
        return rest.exchange(RequestEntity.post(URI.create("/api/v1/auth/change-password"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("currentPassword", current, "newPassword", next)),
                new ParameterizedTypeReference<>() {
                });
    }

    private String account(RoleCode... roles) {
        UserAccount account = new UserAccount("pwd-change-" + UUID.randomUUID() + "@esic-connect.test",
                "Pwd", "Change", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(OLD_PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return account.getEmail();
    }
}
