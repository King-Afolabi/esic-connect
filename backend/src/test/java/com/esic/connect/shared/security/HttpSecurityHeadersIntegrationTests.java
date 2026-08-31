package com.esic.connect.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie les en-têtes de sécurité HTTP (FINAL-008) et la configuration
 * CORS restrictive (FINAL-007) réellement produits par
 * {@link SecurityConfig}.
 *
 * <p>Les en-têtes par défaut de Spring Security ({@code nosniff},
 * {@code X-Frame-Options: DENY}, anti-cache) doivent être présents,
 * complétés par une {@code Content-Security-Policy} et une
 * {@code Referrer-Policy} explicites. HSTS n'est PAS attendu sur une
 * réponse HTTP (émis uniquement sur HTTPS) et n'est donc pas exigé ici.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HttpSecurityHeadersIntegrationTests {

    private static final String ALLOWED_ORIGIN = "http://localhost:4200";
    private static final String DISALLOWED_ORIGIN = "http://evil.example";
    private static final String API_PATH = "/api/v1/sessions";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void securedResponseCarriesTheHardenedSecurityHeaders() {
        // Route métier sans jeton -> 401, mais les writers d'en-têtes
        // s'exécutent avant la décision d'authentification.
        ResponseEntity<String> response = rest.exchange(
                RequestEntity.get(API_PATH).build(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        HttpHeaders headers = response.getHeaders();

        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("Cache-Control")).contains("no-store");
        assertThat(headers.getFirst("Pragma")).isEqualTo("no-cache");
        assertThat(headers.getFirst("Expires")).isEqualTo("0");

        String csp = headers.getFirst("Content-Security-Policy");
        assertThat(csp).isNotNull();
        assertThat(csp)
                .contains("default-src 'self'")
                .contains("script-src 'self'")
                .contains("frame-ancestors 'none'")
                .contains("object-src 'none'")
                .doesNotContain("'unsafe-eval'");
        // script-src ne doit pas autoriser l'inline (style-src le peut, pour Swagger UI).
        assertThat(csp).doesNotContain("script-src 'self' 'unsafe-inline'");

        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("no-referrer");

        // HSTS : jamais sur une réponse HTTP.
        assertThat(headers.get("Strict-Transport-Security")).isNull();
    }

    @Test
    void corsPreflightFromAnAllowedOriginIsAccepted() {
        ResponseEntity<String> response = preflight(ALLOWED_ORIGIN, "GET");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getFirst("Access-Control-Allow-Origin")).isEqualTo(ALLOWED_ORIGIN);
        assertThat(response.getHeaders().getFirst("Access-Control-Allow-Methods")).contains("GET");
        // Aucun cookie -> allowCredentials=false : l'en-tête ne doit pas être "true".
        assertThat(response.getHeaders().getFirst("Access-Control-Allow-Credentials")).isNotEqualTo("true");
    }

    @Test
    void corsPreflightFromADisallowedOriginIsRejectedWithoutAllowHeader() {
        ResponseEntity<String> response = preflight(DISALLOWED_ORIGIN, "GET");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getFirst("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void aSimpleRequestFromAnAllowedOriginGetsTheAllowOriginHeader() {
        RequestEntity<Void> request = RequestEntity.get(URI.create(API_PATH))
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .build();
        ResponseEntity<String> response = rest.exchange(request, String.class);

        // Toujours 401 (pas de jeton) mais l'en-tête CORS est posé.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst("Access-Control-Allow-Origin")).isEqualTo(ALLOWED_ORIGIN);
    }

    private ResponseEntity<String> preflight(String origin, String requestMethod) {
        RequestEntity<Void> request = RequestEntity.method(HttpMethod.OPTIONS, URI.create(API_PATH))
                .header(HttpHeaders.ORIGIN, origin)
                .header("Access-Control-Request-Method", requestMethod)
                .build();
        return rest.exchange(request, String.class);
    }
}
