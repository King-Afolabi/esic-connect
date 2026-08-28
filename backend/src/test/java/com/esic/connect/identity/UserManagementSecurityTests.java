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
 * Contrôles d'autorisation de l'administration des comptes :
 * <ul>
 *   <li>refus anonyme (401) et hors rôle (403) ;</li>
 *   <li>{@code SCHOOL_ADMINISTRATION} peut suspendre mais pas archiver ni
 *       gérer les rôles ;</li>
 *   <li>un {@code ADMIN} ne peut pas toucher un compte ni le rôle
 *       {@code SUPER_ADMIN} ;</li>
 *   <li>auto-action interdite ; {@code public_id} inconnu → 404.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserManagementSecurityTests {

    private static final String PASSWORD = "S3cure-Pass!word";

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
    void listRejectsAnonymousRequestWith401() {
        ResponseEntity<String> response = restTemplate.exchange(
                RequestEntity.get(URI.create("/api/v1/users")).build(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listRejectsStudentRoleWith403() {
        String token = tokenFor(AccountStatus.ACTIVE, RoleCode.STUDENT);
        ResponseEntity<String> response = restTemplate.exchange(
                RequestEntity.get(URI.create("/api/v1/users"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void suspendRejectsTeacherRoleWith403() {
        UserAccount target = persistUser(AccountStatus.ACTIVE, RoleCode.STUDENT);
        String teacher = tokenFor(AccountStatus.ACTIVE, RoleCode.TEACHER);

        ResponseEntity<String> response = restTemplate.exchange(
                RequestEntity.post("/api/v1/users/" + target.getPublicId() + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reason", "x")),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void schoolAdministrationCanSuspendButCannotArchiveOrManageRoles() {
        String schoolAdmin = tokenFor(AccountStatus.ACTIVE, RoleCode.SCHOOL_ADMINISTRATION);
        UserAccount target = persistUser(AccountStatus.ACTIVE, RoleCode.STUDENT);

        ResponseEntity<Void> suspend = restTemplate.exchange(
                RequestEntity.post("/api/v1/users/" + target.getPublicId() + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + schoolAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reason", "Contrôle assiduité")),
                Void.class);
        assertThat(suspend.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        UserAccount other = persistUser(AccountStatus.ACTIVE, RoleCode.STUDENT);
        ResponseEntity<String> archive = restTemplate.exchange(
                RequestEntity.post("/api/v1/users/" + other.getPublicId() + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + schoolAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reason", "x")),
                String.class);
        assertThat(archive.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> assignRole = restTemplate.exchange(
                RequestEntity.post("/api/v1/users/" + other.getPublicId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + schoolAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("role", "TEACHER", "reason", "x")),
                String.class);
        assertThat(assignRole.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCannotArchiveASuperAdminAccount() {
        String admin = tokenFor(AccountStatus.ACTIVE, RoleCode.ADMIN);
        UserAccount superAdmin = persistUser(AccountStatus.ACTIVE, RoleCode.SUPER_ADMIN);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.post("/api/v1/users/" + superAdmin.getPublicId() + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reason", "x")),
                mapType());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("USER_SUPER_ADMIN_PROTECTED");
    }

    @Test
    void adminCannotAssignTheSuperAdminRole() {
        String admin = tokenFor(AccountStatus.ACTIVE, RoleCode.ADMIN);
        UserAccount target = persistUser(AccountStatus.ACTIVE, RoleCode.STUDENT);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.post("/api/v1/users/" + target.getPublicId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("role", "SUPER_ADMIN", "reason", "x")),
                mapType());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("USER_SUPER_ADMIN_PROTECTED");
    }

    @Test
    void adminCannotSuspendItsOwnAccount() {
        UserAccount admin = persistUser(AccountStatus.ACTIVE, RoleCode.ADMIN);
        String token = (String) login(admin.getEmail()).getBody().get("accessToken");

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.post("/api/v1/users/" + admin.getPublicId() + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reason", "x")),
                mapType());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("USER_SELF_ACTION_FORBIDDEN");
    }

    @Test
    void unknownPublicIdReturns404() {
        String admin = tokenFor(AccountStatus.ACTIVE, RoleCode.ADMIN);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.get(URI.create("/api/v1/users/" + UUID.randomUUID()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).build(),
                mapType());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("USER_NOT_FOUND");
    }

    private String tokenFor(AccountStatus status, RoleCode role) {
        UserAccount account = persistUser(status, role);
        return (String) login(account.getEmail()).getBody().get("accessToken");
    }

    private ResponseEntity<Map<String, Object>> login(String email) {
        return restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "password", PASSWORD)),
                mapType());
    }

    private UserAccount persistUser(AccountStatus status, RoleCode roleCode) {
        UserAccount account = new UserAccount("um-sec-" + UUID.randomUUID() + "@esic-connect.test",
                "Prenom", "Nom", status);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        return account;
    }

    private static ParameterizedTypeReference<Map<String, Object>> mapType() {
        return new ParameterizedTypeReference<>() {
        };
    }
}
