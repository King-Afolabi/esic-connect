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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renouvellement silencieux de session par cookie {@code HttpOnly}
 * (EF-AUTH-014, docs/02 §17.7).
 *
 * <p>Le jeton d'accès ne vit qu'en mémoire côté client (RG-093) : sans ce
 * mécanisme, un simple rechargement de page déconnecterait l'utilisateur.
 * Ces tests vérifient qu'un cookie de renouvellement rotatif rétablit la
 * session, qu'il tourne à chaque usage, qu'un rejeu coupe la famille, et
 * qu'une déconnexion ou une révocation globale le neutralise.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RefreshTokenIntegrationTests {

    private static final String PASSWORD = "MotDePasseAssezLong!2026";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void loginSetsAnHttpOnlyStrictScopedRefreshCookie() {
        UserAccount account = persistUser();

        ResponseEntity<Map<String, Object>> response = login(account.getEmail());

        String rawCookie = rawSetCookie(response);
        assertThat(rawCookie).startsWith("refresh_token=");
        assertThat(rawCookie).contains("HttpOnly");
        assertThat(rawCookie).contains("SameSite=Strict");
        assertThat(rawCookie).contains("Path=/api/v1/auth");
        // Profil test servi en clair : pas d'attribut Secure.
        assertThat(rawCookie).doesNotContain("Secure");
    }

    @Test
    void refreshExchangesTheCookieForAFreshAccessTokenAndRotatesTheCookie() {
        UserAccount account = persistUser();
        ResponseEntity<Map<String, Object>> loginResponse = login(account.getEmail());
        String cookie = cookiePair(loginResponse);

        ResponseEntity<Map<String, Object>> refreshed = refresh(cookie, null);

        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = (String) refreshed.getBody().get("accessToken");
        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(account.getPublicId().toString());

        String rotated = cookiePair(refreshed);
        assertThat(rotated).isNotEqualTo(cookie);
        // Même famille, secret différent.
        assertThat(familyId(rotated)).isEqualTo(familyId(cookie));
    }

    @Test
    void theRefreshedTokenKeepsTheOriginalAuthenticationMethods() {
        UserAccount account = persistUser();
        String cookie = cookiePair(login(account.getEmail()));

        ResponseEntity<Map<String, Object>> refreshed = refresh(cookie, null);

        Jwt jwt = jwtDecoder.decode((String) refreshed.getBody().get("accessToken"));
        assertThat(jwt.getClaimAsStringList("amr")).contains("pwd");
    }

    @Test
    void replayingASupersededCookieIsRejectedAndKillsTheWholeFamily() {
        UserAccount account = persistUser();
        String firstCookie = cookiePair(login(account.getEmail()));

        String secondCookie = cookiePair(refresh(firstCookie, null));

        // Le cookie déjà consommé ne vaut plus rien...
        assertThat(refresh(firstCookie, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // ...et sa présentation a coupé la famille : même le cookie courant tombe.
        assertThat(refresh(secondCookie, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshIsRefusedAfterLogout() {
        UserAccount account = persistUser();
        ResponseEntity<Map<String, Object>> loginResponse = login(account.getEmail());
        String cookie = cookiePair(loginResponse);
        String accessToken = (String) loginResponse.getBody().get("accessToken");

        ResponseEntity<Void> logout = restTemplate.exchange(
                RequestEntity.post(URI.create("/api/v1/auth/logout"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.COOKIE, cookie)
                        .build(),
                Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        // La réponse vide le cookie du navigateur.
        assertThat(rawSetCookie(logout)).contains("refresh_token=").contains("Max-Age=0");

        assertThat(refresh(cookie, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshIsRefusedAfterAGlobalRevocation() {
        UserAccount account = persistUser();
        ResponseEntity<Map<String, Object>> loginResponse = login(account.getEmail());
        String cookie = cookiePair(loginResponse);
        String accessToken = (String) loginResponse.getBody().get("accessToken");

        restTemplate.exchange(
                RequestEntity.post(URI.create("/api/v1/auth/logout-all"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .build(),
                Void.class);

        assertThat(refresh(cookie, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshIsRefusedWhenTheAccountIsNoLongerActive() {
        UserAccount account = persistUser();
        String cookie = cookiePair(login(account.getEmail()));

        // Rechargé : la connexion vient de mettre à jour last_login_at,
        // donc la version de la ligne a changé depuis persistUser().
        UserAccount reloaded = userAccountRepository.findById(account.getId()).orElseThrow();
        reloaded.suspend("compte suspendu pendant la session", null, java.time.Instant.now());
        userAccountRepository.saveAndFlush(reloaded);

        assertThat(refresh(cookie, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshWithoutACookieIsUnauthorized() {
        assertThat(refresh(null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshWithAForgedCookieIsUnauthorized() {
        assertThat(refresh("refresh_token=not-a-real.family-or-secret", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void meReturnsTheAccountIdentityForAValidToken() {
        UserAccount account = persistUser();
        String accessToken = (String) login(account.getEmail()).getBody().get("accessToken");

        ResponseEntity<Map<String, Object>> me = restTemplate.exchange(
                RequestEntity.get(URI.create("/api/v1/auth/me"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .build(),
                new ParameterizedTypeReference<>() {
                });

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("subject")).isEqualTo(account.getPublicId().toString());
        assertThat(me.getBody().get("email")).isEqualTo(account.getEmail());
    }

    @Test
    void meRequiresAToken() {
        ResponseEntity<Void> me = restTemplate.exchange(
                RequestEntity.get(URI.create("/api/v1/auth/me")).build(), Void.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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

    private ResponseEntity<Map<String, Object>> refresh(String cookiePair, String deviceId) {
        RequestEntity.BodyBuilder request = RequestEntity.post(URI.create("/api/v1/auth/refresh"));
        if (cookiePair != null) {
            request.header(HttpHeaders.COOKIE, cookiePair);
        }
        if (deviceId != null) {
            request.header("X-Device-Id", deviceId);
        }
        return restTemplate.exchange(request.build(), new ParameterizedTypeReference<>() {
        });
    }

    /** En-tête {@code Set-Cookie} brut du cookie de renouvellement. */
    private String rawSetCookie(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull();
        return cookies.stream()
                .filter(value -> value.startsWith("refresh_token="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Aucun cookie refresh_token : " + cookies));
    }

    /** Paire {@code refresh_token=<valeur>} réutilisable dans un en-tête {@code Cookie}. */
    private String cookiePair(ResponseEntity<?> response) {
        String raw = rawSetCookie(response);
        int semicolon = raw.indexOf(';');
        return semicolon < 0 ? raw : raw.substring(0, semicolon);
    }

    private String familyId(String cookiePair) {
        String value = cookiePair.substring("refresh_token=".length());
        return value.substring(0, value.indexOf('.'));
    }

    private UserAccount persistUser() {
        UserAccount account = new UserAccount(
                "refresh-" + UUID.randomUUID() + "@esic-connect.test", "Prénom", "Nom",
                AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return userAccountRepository.saveAndFlush(account);
    }
}
