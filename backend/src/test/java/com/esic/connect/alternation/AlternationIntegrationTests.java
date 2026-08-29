package com.esic.connect.alternation;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parcours de bout en bout du module {@code alternation} (T-J1-033 /
 * US-060 à US-063) : cycle de vie d'un modèle de rythme, d'une
 * affectation de classe et d'une exception individuelle ; résolution du
 * contexte par classe et par inscription ; pagination plafonnée, tri hors
 * liste blanche, format {@code ApiError}, absence d'identifiant SQL, audit
 * écrit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AlternationIntegrationTests {

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

    // ------------------------------------------------------------------
    // Modèles de rythme
    // ------------------------------------------------------------------

    @Test
    void patternLifecycleIsAuditedAndHidesInternalIds() {
        String admin = adminToken();
        Map<String, Object> pattern = created("/api/v1/alternation/patterns", threeTwoBody("RYT-" + code()), admin);
        String id = (String) pattern.get("publicId");

        assertThat(pattern.get("type")).isEqualTo("THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY");
        assertThat(pattern.get("cycleLengthWeeks")).isEqualTo(1);
        assertThat(pattern).doesNotContainKeys("id");
        assertThat(((Map<?, ?>) pattern.get("configuration")).get("schoolDays")).isNotNull();
        assertThat(auditActions(id)).contains("WORK_STUDY_PATTERN_CREATED");

        Map<String, Object> updated = patch("/api/v1/alternation/patterns/" + id, Map.of(
                "name", "Rythme 3-2 révisé", "cycleLengthWeeks", 1,
                "configuration", Map.of("schoolDays", List.of("MONDAY", "TUESDAY"),
                        "companyDays", List.of("WEDNESDAY", "THURSDAY", "FRIDAY"))), admin);
        assertThat(updated.get("name")).isEqualTo("Rythme 3-2 révisé");
        assertThat(auditActions(id)).contains("WORK_STUDY_PATTERN_UPDATED");

        assertThat(status(HttpMethod.POST, "/api/v1/alternation/patterns/" + id + "/archive",
                Map.of("reason", "obsolète"), admin)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getMap("/api/v1/alternation/patterns/" + id, admin).get("status")).isEqualTo("ARCHIVED");
        assertThat(status(HttpMethod.POST, "/api/v1/alternation/patterns/" + id + "/restore", null, admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getMap("/api/v1/alternation/patterns/" + id, admin).get("status")).isEqualTo("ACTIVE");
        assertThat(auditActions(id)).contains("WORK_STUDY_PATTERN_ARCHIVED", "WORK_STUDY_PATTERN_RESTORED");
    }

    @Test
    void invalidConfigurationIsRejectedWithApiErrorDetail() {
        String admin = adminToken();
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, "/api/v1/alternation/patterns",
                Map.of("code", "RYT-" + code(), "name", "Bad", "type", "THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY",
                        "configuration", Map.of("schoolDays", List.of("MONDAY"), "unknownKey", 1)), admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("ALT_INVALID_CONFIGURATION");
        assertThat(response.getBody().get("correlationId")).isNotNull();
        assertThat((List<?>) response.getBody().get("details")).isNotEmpty();
    }

    @Test
    void patternListRejectsSortOutsideWhitelist() {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET,
                "/api/v1/alternation/patterns?sort=configurationJson,asc", null, adminToken());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("ALT_INVALID_SORT");
    }

    @Test
    void patternListSizeIsCappedAt100() {
        Map<String, Object> page = getMap("/api/v1/alternation/patterns?size=500", adminToken());
        assertThat(((Number) page.get("size")).intValue()).isEqualTo(100);
    }

    @Test
    void unknownPatternReturns404WithApiError() {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET,
                "/api/v1/alternation/patterns/" + UUID.randomUUID(), null, adminToken());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("ALT_PATTERN_NOT_FOUND");
    }

    // ------------------------------------------------------------------
    // Affectations de classe
    // ------------------------------------------------------------------

    @Test
    void classAssignmentLifecycleAndOverlapRules() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        String pattern = (String) created("/api/v1/alternation/patterns", threeTwoBody("RYT-" + code()), admin)
                .get("publicId");

        Map<String, Object> assignment = created("/api/v1/alternation/class-assignments", Map.of(
                "classGroupPublicId", chain.classA(), "workStudyPatternPublicId", pattern,
                "cycleStartDate", "2026-09-01", "validFrom", "2026-09-01", "validUntil", "2026-12-31"), admin);
        String assignmentId = (String) assignment.get("publicId");
        assertThat(assignment.get("status")).isEqualTo("ACTIVE");
        assertThat(assignment.get("classGroupPublicId")).isEqualTo(chain.classA());
        assertThat(assignment).doesNotContainKeys("id", "classGroupId", "workStudyPatternId");
        assertThat(auditActions(assignmentId)).contains("CLASS_WORK_STUDY_PATTERN_ASSIGNED");

        // Chevauchement (partage le 31/12) -> 409
        ResponseEntity<Map<String, Object>> overlap = exchange(HttpMethod.POST,
                "/api/v1/alternation/class-assignments", Map.of("classGroupPublicId", chain.classA(),
                        "workStudyPatternPublicId", pattern, "cycleStartDate", "2026-12-31",
                        "validFrom", "2026-12-31", "validUntil", "2027-06-30"), admin);
        assertThat(overlap.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(overlap.getBody().get("code")).isEqualTo("ALT_ASSIGNMENT_OVERLAP");

        // Période strictement adjacente (débute le 01/01) -> acceptée
        Map<String, Object> adjacent = created("/api/v1/alternation/class-assignments", Map.of(
                "classGroupPublicId", chain.classA(), "workStudyPatternPublicId", pattern,
                "cycleStartDate", "2027-01-01", "validFrom", "2027-01-01"), admin);
        assertThat(adjacent.get("validUntil")).isNull();

        // Deuxième affectation ouverte pour la même classe -> 409
        ResponseEntity<Map<String, Object>> secondOpen = exchange(HttpMethod.POST,
                "/api/v1/alternation/class-assignments", Map.of("classGroupPublicId", chain.classA(),
                        "workStudyPatternPublicId", pattern, "cycleStartDate", "2028-01-01",
                        "validFrom", "2028-01-01"), admin);
        assertThat(secondOpen.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondOpen.getBody().get("code"))
                .isIn("ALT_ASSIGNMENT_OVERLAP", "ALT_OPEN_ASSIGNMENT_EXISTS");

        // Clôture de l'affectation adjacente ouverte
        String adjacentId = (String) adjacent.get("publicId");
        assertThat(status(HttpMethod.POST, "/api/v1/alternation/class-assignments/" + adjacentId + "/close",
                Map.of("reason", "réorganisation", "effectiveDate", "2027-03-31"), admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getMap("/api/v1/alternation/class-assignments/" + adjacentId, admin).get("status"))
                .isEqualTo("CLOSED");
        assertThat(auditActions(adjacentId)).contains("CLASS_WORK_STUDY_PATTERN_CLOSED");

        // Historique conservé : la classe garde ses deux affectations
        assertThat(count("/api/v1/alternation/classes/" + chain.classA() + "/assignments", admin)).isEqualTo(2);
    }

    @Test
    void archivedPatternCannotBeAssigned() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        String pattern = (String) created("/api/v1/alternation/patterns", threeTwoBody("RYT-" + code()), admin)
                .get("publicId");
        status(HttpMethod.POST, "/api/v1/alternation/patterns/" + pattern + "/archive",
                Map.of("reason", "stop"), admin);

        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST,
                "/api/v1/alternation/class-assignments", Map.of("classGroupPublicId", chain.classA(),
                        "workStudyPatternPublicId", pattern, "cycleStartDate", "2026-09-01",
                        "validFrom", "2026-09-01"), admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("ALT_PATTERN_ARCHIVED");
    }

    @Test
    void classContextResolvesFromAssignedPattern() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        String pattern = (String) created("/api/v1/alternation/patterns", threeTwoBody("RYT-" + code()), admin)
                .get("publicId");
        created("/api/v1/alternation/class-assignments", Map.of(
                "classGroupPublicId", chain.classA(), "workStudyPatternPublicId", pattern,
                "cycleStartDate", "2026-09-01", "validFrom", "2026-09-01"), admin);

        // 2026-09-10 = jeudi -> jour entreprise du rythme 3/2
        Map<String, Object> thursday = getMap("/api/v1/alternation/classes/" + chain.classA()
                + "/context?date=2026-09-10", admin);
        assertThat(thursday.get("context")).isEqualTo("COMPANY");
        assertThat(thursday.get("source")).isEqualTo("PATTERN");
        assertThat(thursday.get("dayOfWeek")).isEqualTo("THURSDAY");

        // 2026-09-07 = lundi -> école
        assertThat(getMap("/api/v1/alternation/classes/" + chain.classA() + "/context?date=2026-09-07", admin)
                .get("context")).isEqualTo("SCHOOL");

        // 2026-09-05 = samedi -> UNKNOWN
        assertThat(getMap("/api/v1/alternation/classes/" + chain.classA() + "/context?date=2026-09-05", admin)
                .get("context")).isEqualTo("UNKNOWN");

        // Date sans affectation couvrante -> UNKNOWN / NONE
        Map<String, Object> before = getMap("/api/v1/alternation/classes/" + chain.classB()
                + "/context?date=2026-09-10", admin);
        assertThat(before.get("context")).isEqualTo("UNKNOWN");
        assertThat(before.get("source")).isEqualTo("NONE");
    }

    // ------------------------------------------------------------------
    // Exceptions individuelles
    // ------------------------------------------------------------------

    @Test
    void studentExceptionLifecycleAndEffectiveContext() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        String pattern = (String) created("/api/v1/alternation/patterns", threeTwoBody("RYT-" + code()), admin)
                .get("publicId");
        created("/api/v1/alternation/class-assignments", Map.of(
                "classGroupPublicId", chain.classA(), "workStudyPatternPublicId", pattern,
                "cycleStartDate", "2026-09-01", "validFrom", "2026-09-01"), admin);
        String enrollmentId = enrollmentInClass(admin, chain.classA());

        Map<String, Object> exception = created("/api/v1/alternation/student-exceptions", Map.of(
                "enrollmentPublicId", enrollmentId, "type", "COMPANY_PERIOD",
                "startAt", "2026-09-07T00:00:00Z", "endAt", "2026-09-08T23:59:00Z",
                "timeZoneId", "Europe/Paris", "reason", "immersion entreprise"), admin);
        String exceptionId = (String) exception.get("publicId");
        assertThat(exception.get("type")).isEqualTo("COMPANY_PERIOD");
        assertThat(exception.get("enrollmentPublicId")).isEqualTo(enrollmentId);
        assertThat(exception).doesNotContainKeys("id", "enrollmentId");
        assertThat(auditActions(exceptionId)).contains("STUDENT_SCHEDULE_EXCEPTION_CREATED");

        // Chevauchement de même type -> 409
        ResponseEntity<Map<String, Object>> overlap = exchange(HttpMethod.POST,
                "/api/v1/alternation/student-exceptions", Map.of("enrollmentPublicId", enrollmentId,
                        "type", "COMPANY_PERIOD", "startAt", "2026-09-08T08:00:00Z",
                        "endAt", "2026-09-08T18:00:00Z", "timeZoneId", "Europe/Paris", "reason", "suite"), admin);
        assertThat(overlap.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(overlap.getBody().get("code")).isEqualTo("ALT_EXCEPTION_OVERLAP");

        assertThat(count("/api/v1/alternation/enrollments/" + enrollmentId + "/exceptions", admin)).isEqualTo(1);

        // Contexte effectif : lundi 2026-09-07 -> le rythme dit SCHOOL,
        // l'exception COMPANY_PERIOD prime -> COMPANY / INDIVIDUAL_EXCEPTION
        Map<String, Object> context = getMap("/api/v1/alternation/enrollments/" + enrollmentId
                + "/context?date=2026-09-07", admin);
        assertThat(context.get("patternContext")).isEqualTo("SCHOOL");
        assertThat(context.get("effectiveContext")).isEqualTo("COMPANY");
        assertThat(context.get("source")).isEqualTo("INDIVIDUAL_EXCEPTION");
        assertThat(context.get("coveringExceptionTypes")).isEqualTo(List.of("COMPANY_PERIOD"));

        // Annulation -> historique conservé, contexte revient au rythme
        assertThat(status(HttpMethod.POST, "/api/v1/alternation/student-exceptions/" + exceptionId + "/cancel",
                Map.of("reason", "erreur"), admin)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getMap("/api/v1/alternation/student-exceptions/" + exceptionId, admin).get("status"))
                .isEqualTo("CANCELLED");
        assertThat(auditActions(exceptionId)).contains("STUDENT_SCHEDULE_EXCEPTION_CANCELLED");
        assertThat(getMap("/api/v1/alternation/enrollments/" + enrollmentId + "/context?date=2026-09-07", admin)
                .get("effectiveContext")).isEqualTo("SCHOOL");
    }

    @Test
    void exceptionOnInactiveEnrollmentIsRejected() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        String enrollmentId = enrollmentInClass(admin, chain.classA());
        // Clôture de l'inscription -> statut COMPLETED
        assertThat(status(HttpMethod.POST, "/api/v1/enrollments/" + enrollmentId + "/close",
                Map.of("status", "COMPLETED", "reason", "diplômé"), admin)).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST,
                "/api/v1/alternation/student-exceptions", Map.of("enrollmentPublicId", enrollmentId,
                        "type", "REMOTE_ALLOWED", "startAt", "2026-09-07T08:00:00Z",
                        "endAt", "2026-09-07T18:00:00Z", "timeZoneId", "Europe/Paris", "reason", "x"), admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("ALT_ENROLLMENT_NOT_USABLE");
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private static Map<String, Object> threeTwoBody(String code) {
        return Map.of("code", code, "name", "Rythme 3 jours / 2 jours",
                "type", "THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY",
                "configuration", Map.of(
                        "schoolDays", List.of("MONDAY", "TUESDAY", "WEDNESDAY"),
                        "companyDays", List.of("THURSDAY", "FRIDAY")));
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

    private String enrollmentInClass(String admin, String classPublicId) {
        String studentUser = accountWithRoles(RoleCode.STUDENT).publicId();
        String profile = (String) created("/api/v1/student-profiles", Map.of("userPublicId", studentUser,
                "studentNumber", "ESIC-2026-" + code()), admin).get("publicId");
        return (String) created("/api/v1/enrollments", Map.of("studentProfilePublicId", profile,
                "classGroupPublicId", classPublicId), admin).get("publicId");
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

    private Map<String, Object> patch(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.PATCH, path, body, token);
        assertThat(response.getStatusCode()).as("PATCH " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.OK);
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
        UserAccount account = new UserAccount("alt-" + UUID.randomUUID() + "@esic-connect.test",
                "Alt", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return new Account(account.getPublicId().toString(), account.getEmail());
    }

    private String adminToken() {
        Account account = accountWithRoles(RoleCode.ADMIN);
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
