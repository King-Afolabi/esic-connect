package com.esic.connect.identity;

import com.esic.connect.audit.internal.AuditEvent;
import com.esic.connect.audit.internal.AuditEventRepository;
import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parcours complet : émission protégée → email capturé → validation
 * publique générique → activation → connexion avec le nouveau mot de
 * passe. Un mailer enregistreur remplace l'envoi SMTP réel.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AccountInvitationIntegrationTests {

    private static final String ADMIN_PASSWORD = "Adm1nStr0ngPass!";
    private static final String NEW_PASSWORD = "Student-Str0ng-Pass!";

    @TestConfiguration
    static class RecordingMailerConfig {
        static final AtomicReference<String> LAST_TOKEN = new AtomicReference<>();
        static final AtomicReference<String> LAST_RECIPIENT = new AtomicReference<>();

        @Bean
        @Primary
        InvitationMailer recordingInvitationMailer() {
            return (toEmail, firstName, rawToken, expiresAt) -> {
                LAST_RECIPIENT.set(toEmail);
                LAST_TOKEN.set(rawToken);
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
    private AuditEventRepository auditEventRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetRecorder() {
        RecordingMailerConfig.LAST_TOKEN.set(null);
        RecordingMailerConfig.LAST_RECIPIENT.set(null);
    }

    @Test
    void issueThenValidateThenActivateThenLogin() {
        UserAccount target = persistUser(uniqueEmail(), AccountStatus.PENDING_ACTIVATION, null);
        String adminToken = adminBearerToken();

        // --- Émission (protégée) ---
        ResponseEntity<Map<String, Object>> issued = restTemplate.exchange(
                RequestEntity.post("/api/v1/account-invitations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", target.getEmail(), "role", "STUDENT")),
                mapType());

        assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(issued.getBody()).containsKey("invitationId").containsKey("expiresAt");
        assertThat(issued.getBody()).doesNotContainKey("token").doesNotContainKey("rawToken");

        String rawToken = RecordingMailerConfig.LAST_TOKEN.get();
        assertThat(rawToken).isNotBlank();
        assertThat(RecordingMailerConfig.LAST_RECIPIENT.get()).isEqualTo(target.getEmail());

        // --- Validation publique : uniquement un booléen ---
        ResponseEntity<Map<String, Object>> validValidation = restTemplate.exchange(
                RequestEntity.get(validateUri(rawToken)).build(), mapType());
        assertThat(validValidation.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(validValidation.getBody()).isEqualTo(Map.of("valid", true));

        ResponseEntity<Map<String, Object>> bogusValidation = restTemplate.exchange(
                RequestEntity.get(validateUri("not-a-real-token")).build(), mapType());
        assertThat(bogusValidation.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bogusValidation.getBody()).isEqualTo(Map.of("valid", false));

        // --- Activation ---
        ResponseEntity<Void> activation = restTemplate.exchange(
                RequestEntity.post("/api/v1/account-invitations/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("token", rawToken, "password", NEW_PASSWORD)),
                Void.class);
        assertThat(activation.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        UserAccount activated = userAccountRepository.findById(target.getId()).orElseThrow();
        assertThat(activated.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(activated.getEmailVerifiedAt()).isNotNull();
        assertThat(activated.getPasswordHash()).isNotBlank();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, activated.getPasswordHash())).isTrue();

        // Lecture dans une transaction : UserRole.role est chargé en LAZY.
        Boolean hasStudentRole = new TransactionTemplate(transactionManager).execute(status ->
                userRoleRepository.findByUserId(target.getId()).stream()
                        .anyMatch(userRole -> userRole.isActive()
                                && userRole.getRole().getCode() == RoleCode.STUDENT));
        assertThat(hasStudentRole).isTrue();

        // --- Connexion avec le nouveau mot de passe ---
        ResponseEntity<Map<String, Object>> login = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", target.getEmail(), "password", NEW_PASSWORD)),
                mapType());
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) login.getBody().get("accessToken")).isNotBlank();

        // --- Jeton à usage unique ---
        ResponseEntity<Map<String, Object>> reuse = restTemplate.exchange(
                RequestEntity.post("/api/v1/account-invitations/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("token", rawToken, "password", NEW_PASSWORD)),
                mapType());
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reuse.getBody().get("code")).isEqualTo("INVITATION_INVALID");

        // --- Audit ---
        List<String> actions = auditEventRepository.findAll().stream().map(AuditEvent::getAction).toList();
        assertThat(actions).contains("ACCOUNT_INVITATION_ISSUED", "ACCOUNT_ACTIVATED");
    }

    @Test
    void reissuingRevokesThePreviousToken() {
        UserAccount target = persistUser(uniqueEmail(), AccountStatus.PENDING_ACTIVATION, null);
        String adminToken = adminBearerToken();

        issueFor(target.getEmail(), adminToken);
        String firstToken = RecordingMailerConfig.LAST_TOKEN.get();

        issueFor(target.getEmail(), adminToken);
        String secondToken = RecordingMailerConfig.LAST_TOKEN.get();

        assertThat(firstToken).isNotEqualTo(secondToken);
        assertThat(validateBoolean(firstToken)).isFalse();
        assertThat(validateBoolean(secondToken)).isTrue();
    }

    private void issueFor(String email, String adminToken) {
        ResponseEntity<Map<String, Object>> issued = restTemplate.exchange(
                RequestEntity.post("/api/v1/account-invitations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "role", "STUDENT")),
                mapType());
        assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    private boolean validateBoolean(String token) {
        return Boolean.TRUE.equals(restTemplate.exchange(
                RequestEntity.get(validateUri(token)).build(), mapType()).getBody().get("valid"));
    }

    private String adminBearerToken() {
        UserAccount admin = persistUser(uniqueEmail(), AccountStatus.ACTIVE, RoleCode.ADMIN);
        ResponseEntity<Map<String, Object>> login = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", admin.getEmail(), "password", ADMIN_PASSWORD)),
                mapType());
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) login.getBody().get("accessToken");
    }

    private UserAccount persistUser(String email, AccountStatus status, RoleCode roleCode) {
        UserAccount account = new UserAccount(email, "Prenom", "Nom", status);
        account.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        if (roleCode != null) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return account;
    }

    private URI validateUri(String token) {
        // Les jetons Base64 URL (et le libellé de test) sont sûrs en query string.
        return URI.create("/api/v1/account-invitations/validate?token=" + token);
    }

    private static ParameterizedTypeReference<Map<String, Object>> mapType() {
        return new ParameterizedTypeReference<>() {
        };
    }

    private static String uniqueEmail() {
        return "inv-" + UUID.randomUUID() + "@esic-connect.test";
    }
}
