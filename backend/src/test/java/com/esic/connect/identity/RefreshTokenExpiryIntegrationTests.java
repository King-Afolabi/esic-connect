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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bornes de temps du jeton de renouvellement (docs/02 §17.7 :
 * « 30 minutes d'inactivité ; durée absolue configurable »).
 *
 * <p>Contexte dédié avec des durées très courtes ; le reste de la suite
 * garde les valeurs par défaut. Deux règles distinctes sont vérifiées :
 * l'inactivité glisse à chaque usage, mais le plafond absolu, lui, ne
 * bouge jamais.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.jwt.refresh-token-idle-ttl=PT5S",
                "app.security.jwt.refresh-token-absolute-ttl=PT5S"
        })
@ActiveProfiles("test")
class RefreshTokenExpiryIntegrationTests {

    private static final String PASSWORD = "MotDePasseAssezLong!2026";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void anIdleSessionCanNoLongerBeRefreshedOnceTheInactivityWindowHasElapsed() throws Exception {
        String cookie = cookiePair(login(persistUser().getEmail()));

        Thread.sleep(6_000);

        assertThat(refresh(cookie).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void regularActivityDoesNotPushBackTheAbsoluteCap() throws Exception {
        String cookie = cookiePair(login(persistUser().getEmail()));

        // Renouvellement à ~2 s : l'inactivité repart pour 5 s, la clé
        // Redis vivra donc jusqu'à ~7 s.
        Thread.sleep(2_000);
        ResponseEntity<Map<String, Object>> rotated = refresh(cookie);
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        String rotatedCookie = cookiePair(rotated);

        // À ~6 s : la clé est encore là (inactivité), mais le plafond
        // absolu — 5 s après l'ouverture — est franchi.
        Thread.sleep(4_000);
        assertThat(refresh(rotatedCookie).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------

    private ResponseEntity<Map<String, Object>> login(String email) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.post(URI.create("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "password", PASSWORD)),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response;
    }

    private ResponseEntity<Map<String, Object>> refresh(String cookiePair) {
        return restTemplate.exchange(
                RequestEntity.post(URI.create("/api/v1/auth/refresh"))
                        .header(HttpHeaders.COOKIE, cookiePair)
                        .build(),
                new ParameterizedTypeReference<>() {
                });
    }

    private String cookiePair(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull();
        String raw = cookies.stream()
                .filter(value -> value.startsWith("refresh_token="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Aucun cookie refresh_token : " + cookies));
        int semicolon = raw.indexOf(';');
        return semicolon < 0 ? raw : raw.substring(0, semicolon);
    }

    private UserAccount persistUser() {
        UserAccount account = new UserAccount(
                "refresh-ttl-" + UUID.randomUUID() + "@esic-connect.test", "Prénom", "Nom",
                AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return userAccountRepository.saveAndFlush(account);
    }
}
