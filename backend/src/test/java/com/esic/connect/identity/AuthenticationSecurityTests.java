package com.esic.connect.identity;

import com.esic.connect.audit.internal.AuditEvent;
import com.esic.connect.audit.internal.AuditEventRepository;
import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de sécurité : réponse publique strictement uniforme en cas
 * d'échec (docs/02 §27.2), rejet des routes protégées sans jeton,
 * routes techniques toujours publiques, absence de fuite de l'adresse
 * brute d'un compte inconnu dans l'audit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthenticationSecurityTests {

    private static final String RAW_PASSWORD = "Str0ngPassw0rd!ForTests";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void unknownEmailWrongPasswordAndInactiveAccountProduceTheSameResponse() {
        UserAccount active = persistUser(uniqueEmail(), AccountStatus.ACTIVE);
        UserAccount inactive = persistUser(uniqueEmail(), AccountStatus.PENDING_ACTIVATION);

        ResponseEntity<Map<String, Object>> unknownEmail = attemptLogin(uniqueEmail(), RAW_PASSWORD);
        ResponseEntity<Map<String, Object>> wrongPassword = attemptLogin(active.getEmail(), "not-the-password");
        ResponseEntity<Map<String, Object>> inactiveAccount = attemptLogin(inactive.getEmail(), RAW_PASSWORD);

        assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(inactiveAccount.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(unknownEmail.getBody().get("code")).isEqualTo("AUTH_INVALID_CREDENTIALS");
        assertThat(unknownEmail.getBody().get("code")).isEqualTo(wrongPassword.getBody().get("code"));
        assertThat(wrongPassword.getBody().get("code")).isEqualTo(inactiveAccount.getBody().get("code"));
        assertThat(unknownEmail.getBody().get("message")).isEqualTo(wrongPassword.getBody().get("message"));
        assertThat(wrongPassword.getBody().get("message")).isEqualTo(inactiveAccount.getBody().get("message"));
    }

    @Test
    void failedAttemptWithUnknownEmailNeverStoresTheRawEmailInAudit() {
        String attemptedEmail = uniqueEmail();

        attemptLogin(attemptedEmail, "whatever");

        AuditEvent newest = auditEventRepository.findAll().stream()
                .max(Comparator.comparing(AuditEvent::getId))
                .orElseThrow();
        assertThat(newest.getActorUserId()).isNull();
        assertThat(newest.getActorDisplaySnapshot()).isNull();
        assertThat(newest.getReason()).doesNotContain(attemptedEmail);
    }

    @Test
    void protectedRouteRejectsRequestWithoutToken() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/some-protected-resource", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedRouteAcceptsRequestWithValidToken() {
        UserAccount account = persistUser(uniqueEmail(), AccountStatus.ACTIVE);
        String token = (String) attemptLogin(account.getEmail(), RAW_PASSWORD).getBody().get("accessToken");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/some-protected-resource", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        // Le jeton est accepté par la chaîne de sécurité (pas de 401/403) ;
        // 404 est attendu puisqu'aucune route métier n'existe encore.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void tokenWithCorrectSignatureButWrongIssuerIsRejected() {
        // Signé avec la vraie clé de l'application (via le même
        // JwtEncoder), non expiré, mais émis avec un `iss` différent de
        // `app.security.jwt.issuer` : doit être refusé par le validateur
        // d'émetteur explicite du JwtDecoder.
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("un-autre-emetteur")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .subject(UUID.randomUUID().toString())
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of("STUDENT"))
                .build();
        String forgedToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(forgedToken);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/some-protected-resource", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Aucun détail technique de validation du JWT exposé au client :
        // ni corps, ni en-tête WWW-Authenticate détaillé (point de
        // fuite réel constaté avec l'AuthenticationEntryPoint par défaut
        // de Resource Server : "the iss claim is not valid").
        assertThat(response.getBody()).isNullOrEmpty();
        String wwwAuthenticate = response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE);
        if (wwwAuthenticate != null) {
            assertThat(wwwAuthenticate.toLowerCase())
                    .doesNotContain("issuer", "iss claim", "exception", "nimbus", "invalid_token", "error_description");
        }
    }

    @Test
    void healthAndSwaggerRemainPublic() {
        assertThat(restTemplate.getForEntity("/actuator/health", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.getForEntity("/v3/api-docs", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map<String, Object>> attemptLogin(String email, String password) {
        return restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "password", password)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                });
    }

    private UserAccount persistUser(String email, AccountStatus status) {
        UserAccount account = new UserAccount(email, "Prénom", "Nom", status);
        account.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        return userAccountRepository.saveAndFlush(account);
    }

    private static String uniqueEmail() {
        return "sec-" + UUID.randomUUID() + "@esic-connect.test";
    }
}
