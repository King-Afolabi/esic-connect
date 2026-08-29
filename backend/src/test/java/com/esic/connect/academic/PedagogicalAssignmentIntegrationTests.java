package com.esic.connect.academic;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parcours des affectations de responsable pédagogique : création,
 * consultation filtrée ({@code program}/{@code user}/{@code type}/
 * {@code status}/{@code activeOn} en dates inclusives), clôture (date
 * effective par défaut aujourd'hui, refus avant {@code validFrom}),
 * unicité du responsable principal (dont une course concurrente traduite
 * en 409, pas 500), éligibilité de la cible, audit et matrice
 * d'autorisation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PedagogicalAssignmentIntegrationTests {

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

    @BeforeEach
    void useJdkClient() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void createListCloseLifecycleIsAuditedAndHidesInternalIds() {
        String admin = adminToken();
        String program = createProgram(admin);
        String manager = pedagogicalManagerPublicId();

        Map<String, Object> created = created("/api/v1/pedagogical-assignments", Map.of(
                "programPublicId", program, "userPublicId", manager, "type", "PRIMARY_MANAGER",
                "validFrom", "2026-09-01", "reason", "nomination"), admin);
        String id = (String) created.get("publicId");
        assertThat(created.get("programPublicId")).isEqualTo(program);
        assertThat(created.get("userPublicId")).isEqualTo(manager);
        assertThat(created.get("type")).isEqualTo("PRIMARY_MANAGER");
        assertThat(created.get("status")).isEqualTo("ACTIVE");
        assertThat(created.get("validFrom")).isEqualTo("2026-09-01");
        assertThat(created.get("reason")).isEqualTo("nomination");
        assertThat(created).doesNotContainKeys("id", "programId", "managerUserId", "delegatedById");
        assertThat(auditActions(id)).contains("PEDAGOGICAL_ASSIGNMENT_CREATED");

        assertThat(getMap("/api/v1/pedagogical-assignments/" + id, admin).get("reason")).isEqualTo("nomination");
        assertThat(getMap("/api/v1/pedagogical-assignments?program=" + program, admin).get("totalElements"))
                .isEqualTo(1);
        assertThat(getMap("/api/v1/pedagogical-assignments?user=" + manager, admin).get("totalElements"))
                .isEqualTo(1);
        assertThat(getMap("/api/v1/pedagogical-assignments?program=" + program + "&status=ACTIVE", admin)
                .get("totalElements")).isEqualTo(1);

        // Clôture avec date effective explicite ; libère le créneau.
        assertThat(status(HttpMethod.POST, "/api/v1/pedagogical-assignments/" + id + "/close",
                Map.of("reason", "fin de mission", "effectiveDate", "2026-12-31"), admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(auditActions(id)).contains("PEDAGOGICAL_ASSIGNMENT_CLOSED");
        Map<String, Object> closed = getMap("/api/v1/pedagogical-assignments/" + id, admin);
        assertThat(closed.get("status")).isEqualTo("CLOSED");
        assertThat(closed.get("validUntil")).isEqualTo("2026-12-31");
        assertThat(closed.get("closeReason")).isEqualTo("fin de mission");

        // Créneau réattribuable après clôture.
        assertThat(status(HttpMethod.POST, "/api/v1/pedagogical-assignments", Map.of(
                "programPublicId", program, "userPublicId", manager, "type", "PRIMARY_MANAGER"), admin))
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void activeOnFilterUsesInclusiveDates() {
        String admin = adminToken();
        String program = createProgram(admin);
        String manager = pedagogicalManagerPublicId();
        created("/api/v1/pedagogical-assignments", Map.of("programPublicId", program, "userPublicId", manager,
                "type", "DELEGATE", "validFrom", "2026-09-01", "validUntil", "2026-09-30"), admin);

        assertThat(count("/api/v1/pedagogical-assignments?program=" + program + "&activeOn=2026-09-01", admin))
                .isEqualTo(1);
        assertThat(count("/api/v1/pedagogical-assignments?program=" + program + "&activeOn=2026-09-30", admin))
                .isEqualTo(1);
        assertThat(count("/api/v1/pedagogical-assignments?program=" + program + "&activeOn=2026-08-31", admin))
                .isEqualTo(0);
        assertThat(count("/api/v1/pedagogical-assignments?program=" + program + "&activeOn=2026-10-01", admin))
                .isEqualTo(0);
    }

    @Test
    void typeFilterDistinguishesPrimaryAndDelegate() {
        String admin = adminToken();
        String program = createProgram(admin);
        created("/api/v1/pedagogical-assignments", Map.of("programPublicId", program,
                "userPublicId", pedagogicalManagerPublicId(), "type", "PRIMARY_MANAGER"), admin);
        created("/api/v1/pedagogical-assignments", Map.of("programPublicId", program,
                "userPublicId", pedagogicalManagerPublicId(), "type", "DELEGATE"), admin);

        assertThat(count("/api/v1/pedagogical-assignments?program=" + program + "&type=DELEGATE", admin))
                .isEqualTo(1);
        assertThat(count("/api/v1/pedagogical-assignments?program=" + program + "&type=PRIMARY_MANAGER", admin))
                .isEqualTo(1);
    }

    @Test
    void closeRejectsEffectiveDateBeforeValidFrom() {
        String admin = adminToken();
        String program = createProgram(admin);
        String future = LocalDate.now().plusDays(30).toString();
        String id = (String) created("/api/v1/pedagogical-assignments", Map.of("programPublicId", program,
                "userPublicId", pedagogicalManagerPublicId(), "type", "DELEGATE", "validFrom", future), admin)
                .get("publicId");

        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST,
                "/api/v1/pedagogical-assignments/" + id + "/close",
                Map.of("reason", "trop tôt", "effectiveDate", LocalDate.now().toString()), admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("ACAD_ASSIGNMENT_DATE_INVALID");
    }

    @Test
    void closeDefaultsEffectiveDateToToday() {
        String admin = adminToken();
        String program = createProgram(admin);
        String id = (String) created("/api/v1/pedagogical-assignments", Map.of("programPublicId", program,
                "userPublicId", pedagogicalManagerPublicId(), "type", "DELEGATE"), admin).get("publicId");

        assertThat(status(HttpMethod.POST, "/api/v1/pedagogical-assignments/" + id + "/close",
                Map.of("reason", "fin"), admin)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getMap("/api/v1/pedagogical-assignments/" + id, admin).get("validUntil"))
                .isEqualTo(LocalDate.now().toString());
    }

    @Test
    void targetMustExistAndCarryPedagogicalManagerRole() {
        String admin = adminToken();
        String program = createProgram(admin);

        ResponseEntity<Map<String, Object>> unknown = exchange(HttpMethod.POST, "/api/v1/pedagogical-assignments",
                Map.of("programPublicId", program, "userPublicId", UUID.randomUUID().toString(),
                        "type", "DELEGATE"), admin);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(unknown.getBody().get("code")).isEqualTo("ACAD_TARGET_NOT_ELIGIBLE");

        String student = accountWithRoles(RoleCode.STUDENT).publicId();
        ResponseEntity<Map<String, Object>> notManager = exchange(HttpMethod.POST, "/api/v1/pedagogical-assignments",
                Map.of("programPublicId", program, "userPublicId", student, "type", "DELEGATE"), admin);
        assertThat(notManager.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(notManager.getBody().get("code")).isEqualTo("ACAD_TARGET_NOT_ELIGIBLE");
    }

    @Test
    void duplicatePrimaryManagerIsRejectedWith409() {
        String admin = adminToken();
        String program = createProgram(admin);
        created("/api/v1/pedagogical-assignments", Map.of("programPublicId", program,
                "userPublicId", pedagogicalManagerPublicId(), "type", "PRIMARY_MANAGER"), admin);

        ResponseEntity<Map<String, Object>> second = exchange(HttpMethod.POST, "/api/v1/pedagogical-assignments",
                Map.of("programPublicId", program, "userPublicId", pedagogicalManagerPublicId(),
                        "type", "PRIMARY_MANAGER"), admin);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("code")).isEqualTo("ACAD_PRIMARY_MANAGER_EXISTS");
    }

    @Test
    void concurrentPrimaryManagerCreationsYieldExactlyOneSuccessAndOne409() throws Exception {
        String admin = adminToken();
        String program = createProgram(admin);
        String manager = pedagogicalManagerPublicId();
        Map<String, Object> body = Map.of("programPublicId", program, "userPublicId", manager,
                "type", "PRIMARY_MANAGER");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<HttpStatus> call = () -> (HttpStatus) exchange(HttpMethod.POST,
                    "/api/v1/pedagogical-assignments", body, admin).getStatusCode();
            List<Future<HttpStatus>> results = pool.invokeAll(List.of(call, call));
            long created = results.stream().map(PedagogicalAssignmentIntegrationTests::get)
                    .filter(HttpStatus.CREATED::equals).count();
            long conflicts = results.stream().map(PedagogicalAssignmentIntegrationTests::get)
                    .filter(HttpStatus.CONFLICT::equals).count();
            assertThat(created).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void invalidTypeIsRejectedByValidation() {
        String admin = adminToken();
        String program = createProgram(admin);
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, "/api/v1/pedagogical-assignments",
                Map.of("programPublicId", program, "userPublicId", pedagogicalManagerPublicId(),
                        "type", "OWNER"), admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listRejectsSortOutsideWhitelist() {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET,
                "/api/v1/pedagogical-assignments?sort=managerUserId,asc", null, adminToken());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("ACAD_INVALID_SORT");
    }

    @Test
    void authorizationMatrix() {
        assertThat(restTemplate.exchange(RequestEntity.get(URI.create("/api/v1/pedagogical-assignments")).build(),
                String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        for (RoleCode role : List.of(RoleCode.STUDENT, RoleCode.TEACHER, RoleCode.SCHOOL_ADMINISTRATION,
                RoleCode.PEDAGOGICAL_MANAGER)) {
            String token = tokenFor(role);
            assertThat(status(HttpMethod.GET, "/api/v1/pedagogical-assignments", null, token))
                    .as("GET as " + role).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(status(HttpMethod.POST, "/api/v1/pedagogical-assignments",
                    Map.of("programPublicId", UUID.randomUUID().toString(),
                            "userPublicId", UUID.randomUUID().toString(), "type", "DELEGATE"), token))
                    .as("POST as " + role).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(status(HttpMethod.GET, "/api/v1/pedagogical-assignments", null, adminToken()))
                .isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private static HttpStatus get(Future<HttpStatus> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String createProgram(String token) {
        return (String) created("/api/v1/programs",
                Map.of("code", "PRG-" + UUID.randomUUID().toString().substring(0, 8), "name", "P",
                        "programType", "BTS"), token).get("publicId");
    }

    private String pedagogicalManagerPublicId() {
        return accountWithRoles(RoleCode.PEDAGOGICAL_MANAGER).publicId();
    }

    private int count(String path, String token) {
        return ((Number) getMap(path, token).get("totalElements")).intValue();
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET, path, null, token);
        assertThat(response.getStatusCode()).as("GET " + path).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private HttpStatus status(HttpMethod method, String path, Map<String, Object> body, String token) {
        return (HttpStatus) exchange(method, path, body, token).getStatusCode();
    }

    private ResponseEntity<Map<String, Object>> exchange(HttpMethod method, String path,
                                                         Map<String, Object> body, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(method, URI.create(path));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        RequestEntity<?> entity = body == null
                ? builder.build()
                : builder.contentType(MediaType.APPLICATION_JSON).body(body);
        return restTemplate.exchange(entity, new ParameterizedTypeReference<>() {
        });
    }

    private List<String> auditActions(String resourcePublicId) {
        UUID target = UUID.fromString(resourcePublicId);
        return auditEventRepository.findAll().stream()
                .filter(event -> target.equals(event.getResourcePublicId()))
                .map(AuditEvent::getAction)
                .toList();
    }

    private record Account(String publicId, String email) {
    }

    private Account accountWithRoles(RoleCode... roles) {
        UserAccount account = new UserAccount("assign-" + UUID.randomUUID() + "@esic-connect.test",
                "Assign", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return new Account(account.getPublicId().toString(), account.getEmail());
    }

    private String adminToken() {
        return tokenFor(RoleCode.ADMIN);
    }

    private String tokenFor(RoleCode... roles) {
        Account account = accountWithRoles(roles);
        Map<String, Object> body = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", account.email(), "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) body.get("accessToken");
    }
}
