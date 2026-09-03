package com.esic.connect.identity;

import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Révocation des jetons d'accès (EF-AUTH-014, RG-010).
 *
 * <p>L'API est sans état : un JWT reste cryptographiquement valide
 * jusqu'à son expiration. Ces tests vérifient que la déconnexion et la
 * révocation globale le rendent malgré tout inutilisable — sans quoi
 * « se déconnecter » ne voudrait rien dire.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SessionRevocationIntegrationTests {

    private static final String PASSWORD = "AncienMotDePasse!2026";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void aTokenWorksUntilItIsRevokedByLogout() {
        UserAccount account = persistUser();
        String token = login(account.getEmail());

        assertThat(callProtectedRoute(token)).isNotEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(logout(token)).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(callProtectedRoute(token)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutOnlyRevokesTheSessionThatAskedForIt() {
        UserAccount account = persistUser();
        String firstDevice = login(account.getEmail());
        String secondDevice = login(account.getEmail());
        assertThat(firstDevice).isNotEqualTo(secondDevice);

        logout(firstDevice);

        assertThat(callProtectedRoute(firstDevice)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(callProtectedRoute(secondDevice)).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutAllRevokesEverySessionOfTheAccount() {
        UserAccount account = persistUser();
        String firstDevice = login(account.getEmail());
        String secondDevice = login(account.getEmail());

        ResponseEntity<Void> response = restTemplate.exchange(
                RequestEntity.post(URI.create("/api/v1/auth/logout-all"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstDevice)
                        .build(),
                Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(callProtectedRoute(firstDevice)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(callProtectedRoute(secondDevice)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aTokenIssuedAfterAGlobalRevocationRemainsValid() {
        UserAccount account = persistUser();
        String oldToken = login(account.getEmail());

        restTemplate.exchange(
                RequestEntity.post(URI.create("/api/v1/auth/logout-all"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken)
                        .build(),
                Void.class);

        // Se reconnecter doit fonctionner immédiatement : la révocation
        // porte sur les jetons déjà émis, pas sur le compte.
        String freshToken = login(account.getEmail());
        assertThat(callProtectedRoute(freshToken)).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRequiresAValidToken() {
        ResponseEntity<Void> response = restTemplate.exchange(
                RequestEntity.post(URI.create("/api/v1/auth/logout")).build(), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private HttpStatus callProtectedRoute(String token) {
        ResponseEntity<String> response = restTemplate.exchange(
                RequestEntity.get(URI.create("/api/v1/me/dashboard"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build(),
                String.class);
        return HttpStatus.valueOf(response.getStatusCode().value());
    }

    private HttpStatus logout(String token) {
        ResponseEntity<Void> response = restTemplate.exchange(
                RequestEntity.post(URI.create("/api/v1/auth/logout"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build(),
                Void.class);
        return HttpStatus.valueOf(response.getStatusCode().value());
    }

    private String login(String email) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.post(URI.create("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "password", PASSWORD)),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }

    private UserAccount persistUser() {
        UserAccount account = new UserAccount(
                "revoke-" + UUID.randomUUID() + "@esic-connect.test", "Prénom", "Nom", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return userAccountRepository.saveAndFlush(account);
    }
}
