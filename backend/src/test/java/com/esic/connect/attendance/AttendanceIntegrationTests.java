package com.esic.connect.attendance;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parcours d'émargement de bout en bout : création + ouverture d'une
 * séance, émission d'un jeton + code court (Redis), validation d'une
 * présence par un apprenant inscrit (code court puis jeton opaque),
 * anti-double présence (contrainte SQL), refus d'un non-inscrit, refus
 * après fermeture, rotation du jeton, concurrence, et consultation des
 * présences côté formateur.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AttendanceIntegrationTests {

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
    void shortCodeWorkflowFromIssueToCloseIsAuditedAndAntiDouble() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 2);

        Map<String, Object> issued = post("/api/v1/sessions/" + fx.sessionId() + "/attendance-token",
                null, admin, HttpStatus.OK);
        String shortCode = (String) issued.get("shortCode");
        assertThat(issued.get("token")).isNotNull();
        assertThat(issued.get("expiresAt")).isNotNull();
        assertThat(((Number) issued.get("ttlSeconds")).longValue()).isPositive();

        // L'apprenant 1 émarge avec le code court -> 200
        String student1 = tokenFor(fx.students().get(0));
        Map<String, Object> record = post("/api/v1/attendance/validate",
                Map.of("shortCode", shortCode), student1, HttpStatus.OK);
        assertThat(record.get("sessionPublicId")).isEqualTo(fx.sessionId());
        assertThat(record.get("source")).isEqualTo("SHORT_CODE");
        assertThat(record.get("recordedAt")).isNotNull();
        assertThat(record).doesNotContainKeys("id", "token", "shortCode");
        assertThat(auditActions((String) record.get("attendancePublicId"))).contains("ATTENDANCE_RECORDED");

        // Deuxième émargement du même apprenant -> 409
        ResponseEntity<Map<String, Object>> again = exchange(HttpMethod.POST, "/api/v1/attendance/validate",
                Map.of("shortCode", shortCode), student1);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().get("code")).isEqualTo("ATT_ALREADY_RECORDED");

        // Consultation des présences (formateur / admin)
        Map<String, Object> roster = getMap("/api/v1/sessions/" + fx.sessionId() + "/attendance", admin);
        assertThat(((Number) roster.get("presentCount")).intValue()).isEqualTo(1);
        assertThat(((Number) roster.get("expectedCount")).longValue()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) roster.get("records");
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("studentNumber")).isNotNull();
        assertThat(row.get("source")).isEqualTo("SHORT_CODE");
        assertThat(row).doesNotContainKeys("email", "id");

        // Fermeture -> tout émargement ultérieur refusé
        post("/api/v1/sessions/" + fx.sessionId() + "/close", null, admin, HttpStatus.NO_CONTENT);
        String student2 = tokenFor(fx.students().get(1));
        ResponseEntity<Map<String, Object>> afterClose = exchange(HttpMethod.POST, "/api/v1/attendance/validate",
                Map.of("shortCode", shortCode), student2);
        assertThat(afterClose.getStatusCode()).isIn(HttpStatus.CONFLICT);
        assertThat(afterClose.getBody().get("code")).isIn("ATT_SESSION_CLOSED", "ATT_TOKEN_INVALID");
    }

    @Test
    void opaqueTokenAlsoValidatesAndIsMarkedAsDynamicQr() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        Map<String, Object> issued = post("/api/v1/sessions/" + fx.sessionId() + "/attendance-token",
                null, admin, HttpStatus.OK);
        String token = (String) issued.get("token");

        Map<String, Object> record = post("/api/v1/attendance/validate",
                Map.of("token", token), tokenFor(fx.students().get(0)), HttpStatus.OK);
        assertThat(record.get("source")).isEqualTo("DYNAMIC_QR");
    }

    @Test
    void nonEnrolledStudentIsRejected() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        Map<String, Object> issued = post("/api/v1/sessions/" + fx.sessionId() + "/attendance-token",
                null, admin, HttpStatus.OK);

        // Apprenant avec un profil mais aucune inscription dans une classe de la séance.
        Account outsider = accountWithRoles(RoleCode.STUDENT);
        createProfile(admin, outsider.publicId());
        ResponseEntity<Map<String, Object>> denied = exchange(HttpMethod.POST, "/api/v1/attendance/validate",
                Map.of("shortCode", issued.get("shortCode")), tokenFor(outsider));
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(denied.getBody().get("code")).isEqualTo("ATT_NOT_ENROLLED");
    }

    @Test
    void malformedAndInvalidSubmissionsAreRejected() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        post("/api/v1/sessions/" + fx.sessionId() + "/attendance-token", null, admin, HttpStatus.OK);
        String student = tokenFor(fx.students().get(0));

        // Ni jeton ni code court -> 400
        ResponseEntity<Map<String, Object>> neither = exchange(HttpMethod.POST, "/api/v1/attendance/validate",
                Map.of(), student);
        assertThat(neither.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(neither.getBody().get("code")).isEqualTo("ATT_INVALID_SUBMISSION");

        // Les deux à la fois -> 400
        ResponseEntity<Map<String, Object>> both = exchange(HttpMethod.POST, "/api/v1/attendance/validate",
                Map.of("token", "abc", "shortCode", "ABCD2345"), student);
        assertThat(both.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(both.getBody().get("code")).isEqualTo("ATT_INVALID_SUBMISSION");

        // Code court inconnu -> 409 ATT_TOKEN_INVALID (la valeur soumise n'est jamais renvoyée)
        ResponseEntity<Map<String, Object>> unknown = exchange(HttpMethod.POST, "/api/v1/attendance/validate",
                Map.of("shortCode", "ZZZZ9999"), student);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(unknown.getBody().get("code")).isEqualTo("ATT_TOKEN_INVALID");
        assertThat(String.valueOf(unknown.getBody().get("message"))).doesNotContain("ZZZZ9999");
    }

    @Test
    void tokenRotationInvalidatesThePreviousCode() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 2);
        Map<String, Object> first = post("/api/v1/sessions/" + fx.sessionId() + "/attendance-token",
                null, admin, HttpStatus.OK);
        Map<String, Object> second = post("/api/v1/sessions/" + fx.sessionId() + "/attendance-token",
                null, admin, HttpStatus.OK);
        assertThat(first.get("shortCode")).isNotEqualTo(second.get("shortCode"));

        // L'ancien code court n'est plus accepté.
        ResponseEntity<Map<String, Object>> old = exchange(HttpMethod.POST, "/api/v1/attendance/validate",
                Map.of("shortCode", first.get("shortCode")), tokenFor(fx.students().get(0)));
        assertThat(old.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(old.getBody().get("code")).isEqualTo("ATT_TOKEN_INVALID");

        // Le nouveau est valide.
        post("/api/v1/attendance/validate", Map.of("shortCode", second.get("shortCode")),
                tokenFor(fx.students().get(1)), HttpStatus.OK);
    }

    @Test
    void plannedSessionCannotIssueAToken() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String sessionId = (String) created("/api/v1/sessions", sessionBody(teacher.publicId(),
                List.of(chain.classA())), admin).get("publicId");

        ResponseEntity<Map<String, Object>> denied = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + sessionId + "/attendance-token", null, admin);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(denied.getBody().get("code")).isEqualTo("ATT_SESSION_CLOSED");
    }

    @Test
    void twoConcurrentValidationsYieldExactlyOne200AndOne409() throws Exception {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        Map<String, Object> issued = post("/api/v1/sessions/" + fx.sessionId() + "/attendance-token",
                null, admin, HttpStatus.OK);
        String student = tokenFor(fx.students().get(0));
        Map<String, Object> body = Map.of("shortCode", issued.get("shortCode"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<HttpStatus> call = () -> (HttpStatus) exchange(HttpMethod.POST,
                    "/api/v1/attendance/validate", body, student).getStatusCode();
            List<Future<HttpStatus>> results = pool.invokeAll(List.of(call, call));
            List<HttpStatus> statuses = List.of(get(results.get(0)), get(results.get(1)));
            assertThat(statuses).filteredOn(HttpStatus.OK::equals).hasSize(1);
            assertThat(statuses).filteredOn(HttpStatus.CONFLICT::equals).hasSize(1);
            assertThat(statuses).noneMatch(HttpStatus::is5xxServerError);
        } finally {
            pool.shutdownNow();
        }
        assertThat(((Number) getMap("/api/v1/sessions/" + fx.sessionId() + "/attendance", admin)
                .get("presentCount")).intValue()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void secondCheckpointHasItsOwnTokenAndAttendanceBreakdown() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 2);

        // Point de contrôle CUSTOM ouvert en plus du START.
        Map<String, Object> custom = post("/api/v1/sessions/" + fx.sessionId() + "/checkpoints",
                Map.of("label", "Retour de pause", "type", "CUSTOM"), admin, HttpStatus.CREATED);
        String customId = (String) custom.get("publicId");
        post("/api/v1/sessions/" + fx.sessionId() + "/checkpoints/" + customId + "/open", null, admin,
                HttpStatus.NO_CONTENT);

        // Jeton émis explicitement pour ce point de contrôle.
        Map<String, Object> issued = post(
                "/api/v1/sessions/" + fx.sessionId() + "/checkpoints/" + customId + "/attendance-token",
                null, admin, HttpStatus.OK);
        assertThat(issued.get("checkpointPublicId")).isEqualTo(customId);

        Map<String, Object> record = post("/api/v1/attendance/validate",
                Map.of("shortCode", issued.get("shortCode")), tokenFor(fx.students().get(0)), HttpStatus.OK);
        assertThat(record.get("checkpointPublicId")).isEqualTo(customId);
        assertThat(record.get("status")).isEqualTo("PRESENT");

        Map<String, Object> roster = getMap("/api/v1/sessions/" + fx.sessionId() + "/attendance", admin);
        List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) roster.get("checkpoints");
        assertThat(checkpoints).hasSize(2);
        Map<String, Object> customBlock = checkpoints.stream()
                .filter(cp -> customId.equals(cp.get("checkpointPublicId"))).findFirst().orElseThrow();
        assertThat(((Number) customBlock.get("presentCount")).intValue()).isEqualTo(1);
        assertThat(((Number) customBlock.get("derivedAbsentCount")).intValue()).isEqualTo(1);
    }

    @Test
    void lateArrivalIsClassifiedAsLate() {
        String admin = adminToken();
        // Séance dont l'heure de début est largement dépassée : émargement
        // au-delà du seuil app.attendance.late-threshold -> LATE.
        Fixture fx = openSessionWithEnrolledStudents(admin, 1,
                "2026-08-01T08:00:00Z", "2026-08-01T12:00:00Z");
        Map<String, Object> issued = post("/api/v1/sessions/" + fx.sessionId() + "/attendance-token",
                null, admin, HttpStatus.OK);
        Map<String, Object> record = post("/api/v1/attendance/validate",
                Map.of("shortCode", issued.get("shortCode")), tokenFor(fx.students().get(0)), HttpStatus.OK);
        assertThat(record.get("status")).isEqualTo("LATE");
        assertThat(((Number) record.get("lateMinutes")).intValue()).isPositive();
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private record Fixture(String sessionId, List<Account> students) {
    }

    private Fixture openSessionWithEnrolledStudents(String admin, int studentCount) {
        return openSessionWithEnrolledStudents(admin, studentCount,
                "2026-09-10T08:00:00Z", "2026-09-10T12:00:00Z");
    }

    private Fixture openSessionWithEnrolledStudents(String admin, int studentCount,
                                                   String startsAt, String endsAt) {
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        Map<String, Object> body = new java.util.HashMap<>(
                sessionBody(teacher.publicId(), List.of(chain.classA())));
        body.put("startsAt", startsAt);
        body.put("endsAt", endsAt);
        String sessionId = (String) created("/api/v1/sessions", body, admin).get("publicId");
        post("/api/v1/sessions/" + sessionId + "/open", null, admin, HttpStatus.NO_CONTENT);

        java.util.ArrayList<Account> students = new java.util.ArrayList<>();
        for (int i = 0; i < studentCount; i++) {
            Account student = accountWithRoles(RoleCode.STUDENT);
            String profile = createProfile(admin, student.publicId());
            created("/api/v1/enrollments", Map.of("studentProfilePublicId", profile,
                    "classGroupPublicId", chain.classA()), admin);
            students.add(student);
        }
        return new Fixture(sessionId, students);
    }

    private String createProfile(String admin, String userPublicId) {
        return (String) created("/api/v1/student-profiles", Map.of("userPublicId", userPublicId,
                "studentNumber", "ESIC-2026-" + code()), admin).get("publicId");
    }

    private Map<String, Object> sessionBody(String teacherPublicId, List<String> classPublicIds) {
        return Map.of("teacherPublicId", teacherPublicId, "classPublicIds", classPublicIds,
                "startsAt", "2026-09-10T08:00:00Z", "endsAt", "2026-09-10T12:00:00Z",
                "timeZoneId", "Europe/Paris", "reason", "séance exceptionnelle");
    }

    private record Chain(String classA, String classB) {
    }

    private Chain academicChain(String admin) {
        String site = (String) created("/api/v1/sites", Map.of("code", "SITE-" + UUID.randomUUID(),
                "name", "Campus", "timeZoneId", "Europe/Paris"), admin).get("publicId");
        String program = (String) created("/api/v1/programs", Map.of("code", "PRG-" + code(),
                "name", "BTS SIO", "programType", "BTS"), admin).get("publicId");
        String level = (String) created("/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1", "name", "BTS 1", "sequenceNumber", 1), admin).get("publicId");
        String year = (String) created("/api/v1/academic-years", Map.of("code", "AY-" + code(),
                "name", "2026-2027", "startDate", "2026-09-01", "endDate", "2027-08-31"), admin).get("publicId");
        String promo = (String) created("/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P26", "name", "Promotion 2026"), admin).get("publicId");
        String classA = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C1", "name", "Classe 1"), admin)
                .get("publicId");
        String classB = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C2", "name", "Classe 2"), admin)
                .get("publicId");
        return new Chain(classA, classB);
    }

    private static HttpStatus get(Future<HttpStatus> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------
    // HTTP utilitaires
    // ------------------------------------------------------------------

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> post(String path, Map<String, Object> body, String token, HttpStatus expected) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody()).isEqualTo(expected);
        return response.getBody();
    }

    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET, path, null, token);
        assertThat(response.getStatusCode()).as("GET " + path).isEqualTo(HttpStatus.OK);
        return response.getBody();
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
        UserAccount account = new UserAccount("att-" + UUID.randomUUID() + "@esic-connect.test",
                "Att", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return new Account(account.getPublicId().toString(), account.getEmail());
    }

    private String adminToken() {
        return tokenFor(accountWithRoles(RoleCode.ADMIN));
    }

    private String tokenFor(Account account) {
        Map<String, Object> body = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", account.email(), "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) body.get("accessToken");
    }

    private static String code() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
