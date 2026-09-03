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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Limitation de débit de la connexion, de bout en bout (EF-AUTH-012,
 * RG-092, AC-022).
 *
 * <p>Les seuils sont redéfinis ici, très bas : le profil de test les
 * relève volontairement pour ne pas faire échouer le reste de la suite,
 * qui se connecte des centaines de fois depuis la même origine.
 *
 * <p>Le seau d'<em>origine</em> reste volontairement large même ici :
 * l'ensemble des tests de cette classe partagent 127.0.0.1, et c'est le
 * seau d'<em>identité</em> qui est mis à l'épreuve.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.login.identity-limit=3",
                "app.security.login.origin-limit=1000",
                "app.security.login.window=PT5M",
                "app.security.password-reset.request-limit=2",
                "app.security.password-reset.request-window=PT5M"
        })
@ActiveProfiles("test")
class AuthRateLimitIntegrationTests {

    private static final String PASSWORD = "AncienMotDePasse!2026";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void repeatedFailedLoginsOnTheSameAccountEndUpRateLimited() {
        UserAccount account = persistUser();

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(login(account.getEmail(), "mauvais-mot-de-passe").getStatusCode())
                    .as("tentative %d", attempt)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<Map<String, Object>> limited = login(account.getEmail(), "mauvais-mot-de-passe");
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getBody()).containsEntry("code", "RATE_LIMITED");
        assertThat(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
    }

    @Test
    void theRateLimitResponseRevealsNothingAboutTheAccount() {
        // Une adresse qui n'existe pas doit être limitée exactement comme
        // une adresse connue : sinon le 429 devient un oracle d'existence.
        String unknown = "inconnu-" + UUID.randomUUID() + "@esic-connect.test";

        for (int attempt = 1; attempt <= 3; attempt++) {
            login(unknown, "mauvais-mot-de-passe");
        }

        ResponseEntity<Map<String, Object>> limited = login(unknown, "mauvais-mot-de-passe");
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getBody()).containsEntry("code", "RATE_LIMITED");
        assertThat(limited.getBody().get("message").toString())
                .doesNotContain(unknown)
                .doesNotContain("compte");
    }

    @Test
    void aSuccessfulLoginClearsTheIdentityCounter() {
        UserAccount account = persistUser();

        login(account.getEmail(), "mauvais-mot-de-passe");
        login(account.getEmail(), "mauvais-mot-de-passe");
        assertThat(login(account.getEmail(), PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Le compteur est reparti de zéro : trois nouveaux échecs doivent
        // être acceptés avant que la limite ne s'applique de nouveau.
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(login(account.getEmail(), "mauvais-mot-de-passe").getStatusCode())
                    .as("tentative %d après remise à zéro", attempt)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(login(account.getEmail(), "mauvais-mot-de-passe").getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void forgotPasswordIsRateLimitedToo() {
        String email = "forgot-" + UUID.randomUUID() + "@esic-connect.test";

        for (int attempt = 1; attempt <= 2; attempt++) {
            assertThat(forgotPassword(email).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }

        ResponseEntity<Map<String, Object>> limited = forgotPassword(email);
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private ResponseEntity<Map<String, Object>> login(String email, String password) {
        return restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "password", password)),
                new ParameterizedTypeReference<>() {
                });
    }

    private ResponseEntity<Map<String, Object>> forgotPassword(String email) {
        return restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email)),
                new ParameterizedTypeReference<>() {
                });
    }

    private UserAccount persistUser() {
        UserAccount account = new UserAccount(
                "ratelimit-" + UUID.randomUUID() + "@esic-connect.test", "Prénom", "Nom", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return userAccountRepository.saveAndFlush(account);
    }
}
