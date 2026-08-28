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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie le parcours de connexion réussi de bout en bout : jeton
 * signé décodable, `last_login_at` mis à jour, `audit_event` réellement
 * committé (visible en relecture indépendante après la requête HTTP,
 * preuve que la transaction dédiée de l'écouteur d'audit s'est bien
 * validée).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthenticationIntegrationTests {

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
    private JwtDecoder jwtDecoder;

    @Test
    void successfulLoginReturnsSignedTokenUpdatesLastLoginAndRecordsAudit() {
        UserAccount account = persistUser(uniqueEmail(), AccountStatus.ACTIVE);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", account.getEmail(), "password", RAW_PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("tokenType")).isEqualTo("Bearer");
        assertThat(((Number) body.get("expiresInSeconds")).longValue()).isPositive();

        String token = (String) body.get("accessToken");
        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(account.getPublicId().toString());
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
        assertThat(jwt.getClaimAsStringList("roles")).isNotNull();
        assertThat(jwt.getClaims()).doesNotContainKeys("email", "password", "firstName", "lastName");
        assertThat(token).doesNotContain(RAW_PASSWORD);

        UserAccount reloaded = userAccountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getLastLoginAt()).isNotNull();

        AuditEvent newest = latestAuditEvent();
        assertThat(newest.getActorUserId()).isEqualTo(account.getId());
        assertThat(newest.getAction()).isEqualTo("LOGIN_SUCCESS");
        assertThat(newest.getResult()).isEqualTo("SUCCESS");
    }

    private AuditEvent latestAuditEvent() {
        return auditEventRepository.findAll().stream()
                .max(Comparator.comparing(AuditEvent::getId))
                .orElseThrow();
    }

    private UserAccount persistUser(String email, AccountStatus status) {
        UserAccount account = new UserAccount(email, "Prénom", "Nom", status);
        account.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        return userAccountRepository.saveAndFlush(account);
    }

    private static String uniqueEmail() {
        return "auth-" + UUID.randomUUID() + "@esic-connect.test";
    }
}
