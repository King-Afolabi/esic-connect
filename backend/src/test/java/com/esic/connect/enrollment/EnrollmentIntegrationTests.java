package com.esic.connect.enrollment;

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
 * Parcours de bout en bout des inscriptions historiques (T-J1-032 /
 * US-053 ; AC-006) : création d'un profil apprenant, inscription,
 * changement de classe conservant l'ancienne inscription consultable
 * ({@code TRANSFERRED}) et créant la nouvelle liée
 * ({@code previous_enrollment_id}), clôture, audit, unicité d'une
 * inscription active par année (dont une course concurrente traduite en
 * 409, pas 500), refus sous une classe archivée. Les DTO ne doivent
 * jamais exposer d'identifiant SQL interne.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EnrollmentIntegrationTests {

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
    void profileEnrollTransferCloseLifecycleIsAuditedAndHidesInternalIds() {
        String admin = adminToken();
        String studentUser = studentAccountPublicId();

        Map<String, Object> profile = created("/api/v1/student-profiles", Map.of(
                "userPublicId", studentUser, "studentNumber", "ESIC-2026-" + shortCode(),
                "workStudy", true, "companyName", "ACME"), admin);
        String profileId = (String) profile.get("publicId");
        assertThat(profile.get("userPublicId")).isEqualTo(studentUser);
        assertThat(profile.get("status")).isEqualTo("ACTIVE");
        assertThat(profile).doesNotContainKeys("id", "userId");
        assertThat(auditActions(profileId)).contains("STUDENT_PROFILE_CREATED");

        Chain chain = academicChain(admin);

        Map<String, Object> first = created("/api/v1/enrollments", Map.of(
                "studentProfilePublicId", profileId, "classGroupPublicId", chain.classA()), admin);
        String firstId = (String) first.get("publicId");
        assertThat(first.get("status")).isEqualTo("ACTIVE");
        assertThat(first.get("enrollmentSource")).isEqualTo("MANUAL");
        assertThat(first.get("startDate")).isNotNull();
        assertThat(first.get("classGroupPublicId")).isEqualTo(chain.classA());
        assertThat(first.get("academicYearCode")).isEqualTo(chain.yearCode());
        assertThat(first.get("previousEnrollmentPublicId")).isNull();
        assertThat(first).doesNotContainKeys("id", "studentProfileId", "classGroupId", "academicYearId");
        assertThat(auditActions(firstId)).contains("ENROLLMENT_CREATED");

        assertThat(count("/api/v1/enrollments?student=" + profileId, admin)).isEqualTo(1);

        Map<String, Object> second = created("/api/v1/enrollments/" + firstId + "/transfer", Map.of(
                "classGroupPublicId", chain.classB(), "reason", "réorientation"), admin);
        String secondId = (String) second.get("publicId");
        assertThat(second.get("enrollmentSource")).isEqualTo("CLASS_TRANSFER");
        assertThat(second.get("previousEnrollmentPublicId")).isEqualTo(firstId);
        assertThat(second.get("status")).isEqualTo("ACTIVE");

        Map<String, Object> closedFirst = getMap("/api/v1/enrollments/" + firstId, admin);
        assertThat(closedFirst.get("status")).isEqualTo("TRANSFERRED");
        assertThat(closedFirst.get("endDate")).isNotNull();
        // Bornes inclusives : la nouvelle inscription débute le lendemain
        // de la fin de l'ancienne — les périodes ne se chevauchent pas.
        LocalDate previousEnd = LocalDate.parse((String) closedFirst.get("endDate"));
        LocalDate newStart = LocalDate.parse((String) second.get("startDate"));
        assertThat(newStart).isEqualTo(previousEnd.plusDays(1));
        assertThat(newStart).isAfter(previousEnd);
        assertThat(count("/api/v1/enrollments?student=" + profileId, admin)).isEqualTo(2);
        assertThat(auditActions(firstId)).contains("ENROLLMENT_TRANSFERRED");
        assertThat(auditActions(secondId)).contains("ENROLLMENT_CREATED");

        // La nouvelle inscription débute le lendemain du transfert : la
        // clôture doit prendre effet au plus tôt à cette date.
        assertThat(status(HttpMethod.POST, "/api/v1/enrollments/" + secondId + "/close",
                Map.of("status", "COMPLETED", "reason", "diplômé", "effectiveDate", newStart.toString()), admin))
                .isEqualTo(HttpStatus.OK);
        Map<String, Object> closedSecond = getMap("/api/v1/enrollments/" + secondId, admin);
        assertThat(closedSecond.get("status")).isEqualTo("COMPLETED");
        assertThat(closedSecond.get("endDate")).isNotNull();
        assertThat(auditActions(secondId)).contains("ENROLLMENT_CLOSED");
    }

    @Test
    void duplicateActiveEnrollmentForTheYearIsRejectedWith409() {
        String admin = adminToken();
        String profileId = profile(admin);
        Chain chain = academicChain(admin);
        created("/api/v1/enrollments", Map.of("studentProfilePublicId", profileId,
                "classGroupPublicId", chain.classA()), admin);

        ResponseEntity<Map<String, Object>> second = exchange(HttpMethod.POST, "/api/v1/enrollments",
                Map.of("studentProfilePublicId", profileId, "classGroupPublicId", chain.classB()), admin);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("code")).isEqualTo("ENR_ACTIVE_ENROLLMENT_EXISTS");
    }

    @Test
    void concurrentEnrollmentsYieldExactlyOneSuccessAndOne409() throws Exception {
        String admin = adminToken();
        String profileId = profile(admin);
        Chain chain = academicChain(admin);
        Map<String, Object> body = Map.of("studentProfilePublicId", profileId,
                "classGroupPublicId", chain.classA());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<HttpStatus> call = () -> (HttpStatus) exchange(HttpMethod.POST, "/api/v1/enrollments",
                    body, admin).getStatusCode();
            List<Future<HttpStatus>> results = pool.invokeAll(List.of(call, call));
            long created = results.stream().map(EnrollmentIntegrationTests::get)
                    .filter(HttpStatus.CREATED::equals).count();
            long conflicts = results.stream().map(EnrollmentIntegrationTests::get)
                    .filter(HttpStatus.CONFLICT::equals).count();
            assertThat(created).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void transferToTheSameClassIsRejected() {
        String admin = adminToken();
        String profileId = profile(admin);
        Chain chain = academicChain(admin);
        String id = (String) created("/api/v1/enrollments", Map.of("studentProfilePublicId", profileId,
                "classGroupPublicId", chain.classA()), admin).get("publicId");

        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST,
                "/api/v1/enrollments/" + id + "/transfer",
                Map.of("classGroupPublicId", chain.classA(), "reason", "erreur"), admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("ENR_SAME_CLASS");
    }

    @Test
    void studentProfileForNonStudentAccountIsRejectedWith422() {
        String admin = adminToken();
        String teacherUser = accountWithRoles(RoleCode.TEACHER).publicId();
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, "/api/v1/student-profiles",
                Map.of("userPublicId", teacherUser, "studentNumber", "ESIC-2026-" + shortCode()), admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("code")).isEqualTo("ENR_USER_NOT_ELIGIBLE");
    }

    @Test
    void duplicateStudentNumberIsRejectedWith409() {
        String admin = adminToken();
        String number = "ESIC-2026-" + shortCode();
        created("/api/v1/student-profiles", Map.of("userPublicId", studentAccountPublicId(),
                "studentNumber", number), admin);

        ResponseEntity<Map<String, Object>> second = exchange(HttpMethod.POST, "/api/v1/student-profiles",
                Map.of("userPublicId", studentAccountPublicId(), "studentNumber", number), admin);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("code")).isEqualTo("ENR_DUPLICATE_STUDENT_NUMBER");
    }

    @Test
    void enrollmentUnderArchivedClassIsRejected() {
        String admin = adminToken();
        String profileId = profile(admin);
        Chain chain = academicChain(admin);
        assertThat(status(HttpMethod.POST, "/api/v1/class-groups/" + chain.classA() + "/archive",
                Map.of("reason", "fermeture"), admin)).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, "/api/v1/enrollments",
                Map.of("studentProfilePublicId", profileId, "classGroupPublicId", chain.classA()), admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("ENR_ARCHIVED_PARENT");
    }

    @Test
    void enrollRejectsUnknownStudentProfileWith404() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, "/api/v1/enrollments",
                Map.of("studentProfilePublicId", UUID.randomUUID().toString(),
                        "classGroupPublicId", chain.classA()), admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("ENR_STUDENT_PROFILE_NOT_FOUND");
    }

    @Test
    void listRejectsSortOutsideWhitelist() {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET,
                "/api/v1/enrollments?sort=classGroupId,asc", null, adminToken());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("ENR_INVALID_SORT");
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private record Chain(String classA, String classB, String yearCode) {
    }

    private Chain academicChain(String admin) {
        String site = (String) created("/api/v1/sites", Map.of("code", "SITE-" + UUID.randomUUID(),
                "name", "Campus", "timeZoneId", "Europe/Paris"), admin).get("publicId");
        String program = (String) created("/api/v1/programs", Map.of("code", "PRG-" + shortCode(),
                "name", "BTS SIO", "programType", "BTS"), admin).get("publicId");
        String level = (String) created("/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1", "name", "BTS 1", "sequenceNumber", 1), admin).get("publicId");
        String yearCode = "AY-" + shortCode();
        String year = (String) created("/api/v1/academic-years", Map.of("code", yearCode, "name", "2026-2027",
                "startDate", "2026-09-01", "endDate", "2027-08-31"), admin).get("publicId");
        String promo = (String) created("/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P26", "name", "Promotion 2026"), admin).get("publicId");
        String classA = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C1", "name", "Classe 1"), admin)
                .get("publicId");
        String classB = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C2", "name", "Classe 2"), admin)
                .get("publicId");
        return new Chain(classA, classB, yearCode);
    }

    private String profile(String admin) {
        return (String) created("/api/v1/student-profiles", Map.of("userPublicId", studentAccountPublicId(),
                "studentNumber", "ESIC-2026-" + shortCode()), admin).get("publicId");
    }

    private String studentAccountPublicId() {
        return accountWithRoles(RoleCode.STUDENT).publicId();
    }

    private static HttpStatus get(Future<HttpStatus> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        UserAccount account = new UserAccount("enr-" + UUID.randomUUID() + "@esic-connect.test",
                "Enr", "Tester", AccountStatus.ACTIVE);
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

    private static String shortCode() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
