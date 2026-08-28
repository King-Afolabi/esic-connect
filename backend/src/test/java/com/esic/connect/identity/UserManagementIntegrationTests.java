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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parcours de bout en bout de l'administration des comptes : liste
 * paginée / filtrée, détail par {@code public_id}, suspension →
 * réactivation (connexion bloquée puis rétablie), archivage (rôles
 * clôturés, irréversible), attribution / retrait de rôle avec
 * conservation de l'historique, et écriture de l'audit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserManagementIntegrationTests {

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
    private AuditEventRepository auditEventRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    @Test
    void listIsPaginatedFilteredAndNeverExposesSecrets() {
        String tag = "Tag" + UUID.randomUUID().toString().replace("-", "");
        persistUser(uniqueEmail(), "Alice", tag, AccountStatus.ACTIVE, RoleCode.STUDENT);
        persistUser(uniqueEmail(), "Bob", tag, AccountStatus.SUSPENDED, RoleCode.STUDENT);
        persistUser(uniqueEmail(), "Carla", tag, AccountStatus.ACTIVE, RoleCode.TEACHER);
        String admin = adminToken();

        Map<String, Object> firstPage = getMap("/api/v1/users?q=" + tag + "&size=2", admin);
        assertThat(firstPage).containsKeys("content", "page", "size", "totalElements", "totalPages");
        assertThat(firstPage.get("size")).isEqualTo(2);
        assertThat(firstPage.get("totalElements")).isEqualTo(3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) firstPage.get("content");
        assertThat(content).hasSize(2);
        assertThat(content.get(0)).containsKey("publicId")
                .doesNotContainKeys("id", "passwordHash", "password_hash", "suspendedById");

        Map<String, Object> suspendedOnly = getMap("/api/v1/users?q=" + tag + "&status=SUSPENDED", admin);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suspended = (List<Map<String, Object>>) suspendedOnly.get("content");
        assertThat(suspended).hasSize(1);
        assertThat(suspended.get(0).get("status")).isEqualTo("SUSPENDED");
        assertThat(suspended.get(0).get("firstName")).isEqualTo("Bob");

        Map<String, Object> teacherOnly = getMap("/api/v1/users?q=" + tag + "&role=TEACHER", admin);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> teachers = (List<Map<String, Object>>) teacherOnly.get("content");
        assertThat(teachers).hasSize(1);
        assertThat(teachers.get(0).get("firstName")).isEqualTo("Carla");

        Map<String, Object> clamped = getMap("/api/v1/users?q=" + tag + "&size=9999", admin);
        assertThat(clamped.get("size")).isEqualTo(100);

        ResponseEntity<Map<String, Object>> badSort = restTemplate.exchange(
                RequestEntity.get(URI.create("/api/v1/users?sort=passwordHash,asc"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).build(),
                mapType());
        assertThat(badSort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badSort.getBody().get("code")).isEqualTo("USER_INVALID_SORT");
    }

    @Test
    void getByPublicIdReturnsDetailWithoutSecretAnd404ForUnknown() {
        UserAccount user = persistUser(uniqueEmail(), "Dan", "Detail", AccountStatus.ACTIVE, RoleCode.TEACHER);
        String admin = adminToken();

        Map<String, Object> detail = getMap("/api/v1/users/" + user.getPublicId(), admin);
        assertThat(detail.get("publicId")).isEqualTo(user.getPublicId().toString());
        assertThat(detail).doesNotContainKeys("id", "passwordHash", "password_hash");
        assertThat(detail).containsKey("roleAssignments");

        ResponseEntity<Map<String, Object>> unknown = restTemplate.exchange(
                RequestEntity.get(URI.create("/api/v1/users/" + UUID.randomUUID()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).build(),
                mapType());
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody().get("code")).isEqualTo("USER_NOT_FOUND");

        ResponseEntity<Map<String, Object>> malformed = restTemplate.exchange(
                RequestEntity.get(URI.create("/api/v1/users/not-a-uuid"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).build(),
                mapType());
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // Cycle de vie
    // ------------------------------------------------------------------

    @Test
    void suspendBlocksLoginThenRestoreRestoresIt() {
        UserAccount user = persistUser(uniqueEmail(), "Eve", "Lifecycle", AccountStatus.ACTIVE, RoleCode.STUDENT);
        String admin = adminToken();
        assertThat(login(user.getEmail(), PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(action("/api/v1/users/" + user.getPublicId() + "/suspend", "Absence", admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(login(user.getEmail(), PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Map<String, Object> suspended = getMap("/api/v1/users/" + user.getPublicId(), admin);
        assertThat(suspended.get("status")).isEqualTo("SUSPENDED");
        assertThat(suspended.get("suspensionReason")).isEqualTo("Absence");

        assertThat(action("/api/v1/users/" + user.getPublicId() + "/restore", "Retour", admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(login(user.getEmail(), PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> actions = auditActionsFor(user.getPublicId());
        assertThat(actions).contains("ACCOUNT_SUSPENDED", "ACCOUNT_REACTIVATED");
    }

    @Test
    void archiveClosesRolesBlocksLoginAndIsIrreversible() {
        UserAccount user = persistUser(uniqueEmail(), "Fred", "Archive", AccountStatus.ACTIVE,
                RoleCode.TEACHER, RoleCode.STUDENT);
        String admin = adminToken();
        assertThat(login(user.getEmail(), PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(action("/api/v1/users/" + user.getPublicId() + "/archive", "Fin de scolarité", admin))
                .isEqualTo(HttpStatus.NO_CONTENT);

        Map<String, Object> archived = getMap("/api/v1/users/" + user.getPublicId(), admin);
        assertThat(archived.get("status")).isEqualTo("ARCHIVED");
        assertThat(archived.get("archivedAt")).isNotNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) archived.get("roleAssignments");
        assertThat(assignments).isNotEmpty();
        assertThat(assignments).allSatisfy(a -> {
            assertThat(a.get("active")).isEqualTo(false);
            assertThat(a.get("validUntil")).isNotNull();
        });

        assertThat(login(user.getEmail(), PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map<String, Object>> restore = restTemplate.exchange(
                RequestEntity.post("/api/v1/users/" + user.getPublicId() + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reason", "tentative")),
                mapType());
        assertThat(restore.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(restore.getBody().get("code")).isEqualTo("USER_INVALID_STATE");

        assertThat(auditActionsFor(user.getPublicId())).contains("ACCOUNT_ARCHIVED");
    }

    // ------------------------------------------------------------------
    // Rôles
    // ------------------------------------------------------------------

    @Test
    void assignThenRevokeRoleKeepsHistoryAndProtectsLastRole() {
        UserAccount user = persistUser(uniqueEmail(), "Gina", "Roles", AccountStatus.ACTIVE, RoleCode.STUDENT);
        String admin = adminToken();

        ResponseEntity<Void> assign = restTemplate.exchange(
                RequestEntity.post("/api/v1/users/" + user.getPublicId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("role", "TEACHER", "reason", "Intervention ponctuelle")),
                Void.class);
        assertThat(assign.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Map<String, Object> afterAssign = getMap("/api/v1/users/" + user.getPublicId(), admin);
        assertThat(activeRoles(afterAssign)).containsExactlyInAnyOrder("STUDENT", "TEACHER");

        assertThat(action("/api/v1/users/" + user.getPublicId() + "/roles/TEACHER/revoke", "Fin", admin))
                .isEqualTo(HttpStatus.NO_CONTENT);

        Map<String, Object> afterRevoke = getMap("/api/v1/users/" + user.getPublicId(), admin);
        assertThat(activeRoles(afterRevoke)).containsExactly("STUDENT");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) afterRevoke.get("roleAssignments");
        assertThat(assignments).anySatisfy(a -> {
            assertThat(a.get("role")).isEqualTo("TEACHER");
            assertThat(a.get("active")).isEqualTo(false);
            assertThat(a.get("validUntil")).isNotNull();
        });

        ResponseEntity<Map<String, Object>> revokeLast = restTemplate.exchange(
                RequestEntity.post("/api/v1/users/" + user.getPublicId() + "/roles/STUDENT/revoke")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reason", "test")),
                mapType());
        assertThat(revokeLast.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(revokeLast.getBody().get("code")).isEqualTo("USER_LAST_ACTIVE_ROLE");

        assertThat(auditActionsFor(user.getPublicId())).contains("ROLE_ASSIGNED", "ROLE_REVOKED");
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private HttpStatus action(String path, String reason, String bearer) {
        return (HttpStatus) restTemplate.exchange(
                RequestEntity.post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("reason", reason)),
                Void.class).getStatusCode();
    }

    private Map<String, Object> getMap(String path, String bearer) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.get(URI.create(path)).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer).build(),
                mapType());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<Map<String, Object>> login(String email, String password) {
        return restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "password", password)),
                mapType());
    }

    private String adminToken() {
        UserAccount admin = persistUser(uniqueEmail(), "Admin", "Ops", AccountStatus.ACTIVE, RoleCode.ADMIN);
        return (String) login(admin.getEmail(), PASSWORD).getBody().get("accessToken");
    }

    @SuppressWarnings("unchecked")
    private static List<String> activeRoles(Map<String, Object> detail) {
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) detail.get("roleAssignments");
        return assignments.stream()
                .filter(a -> Boolean.TRUE.equals(a.get("active")))
                .map(a -> (String) a.get("role"))
                .toList();
    }

    private List<String> auditActionsFor(UUID resourcePublicId) {
        return auditEventRepository.findAll().stream()
                .filter(e -> resourcePublicId.equals(e.getResourcePublicId()))
                .map(AuditEvent::getAction)
                .toList();
    }

    private UserAccount persistUser(String email, String firstName, String lastName,
                                    AccountStatus status, RoleCode... roles) {
        UserAccount account = new UserAccount(email, firstName, lastName, status);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return account;
    }

    private static String uniqueEmail() {
        return "um-" + UUID.randomUUID() + "@esic-connect.test";
    }

    private static ParameterizedTypeReference<Map<String, Object>> mapType() {
        return new ParameterizedTypeReference<>() {
        };
    }
}
