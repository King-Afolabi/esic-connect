package com.esic.connect.alternation;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrôles d'accès du module {@code alternation} :
 * <ul>
 *   <li>401 anonyme sur chaque groupe de routes ;</li>
 *   <li>écriture des modèles réservée à
 *       {@code ADMIN}/{@code SUPER_ADMIN}/{@code SCHOOL_ADMINISTRATION} ;
 *       {@code PEDAGOGICAL_MANAGER} lit mais n'écrit pas ;</li>
 *   <li>{@code TEACHER} et {@code STUDENT} toujours refusés ;</li>
 *   <li>{@code PEDAGOGICAL_MANAGER} limité à son périmètre pour les
 *       affectations et les exceptions — aucun élargissement par un
 *       paramètre client.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AlternationSecurityTests {

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

    @BeforeEach
    void useJdkClient() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void anonymousIsRejectedOnEveryRouteGroup() {
        assertThat(status(HttpMethod.GET, "/api/v1/alternation/patterns", null, null))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(status(HttpMethod.POST, "/api/v1/alternation/patterns", Map.of(), null))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(status(HttpMethod.GET, "/api/v1/alternation/class-assignments", null, null))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(status(HttpMethod.POST, "/api/v1/alternation/student-exceptions", Map.of(), null))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void teacherAndStudentAreForbiddenEverywhere() {
        Map<String, Object> wellFormedException = Map.of("enrollmentPublicId", UUID.randomUUID().toString(),
                "type", "REMOTE_ALLOWED", "startAt", "2026-09-07T08:00:00Z", "endAt", "2026-09-07T18:00:00Z",
                "timeZoneId", "Europe/Paris", "reason", "x");
        Map<String, Object> wellFormedPattern = threeTwoBody("RYT-" + code());
        for (RoleCode role : List.of(RoleCode.TEACHER, RoleCode.STUDENT)) {
            String token = tokenFor(role);
            assertThat(status(HttpMethod.GET, "/api/v1/alternation/patterns", null, token))
                    .as("GET patterns as " + role).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(status(HttpMethod.GET, "/api/v1/alternation/class-assignments", null, token))
                    .as("GET class-assignments as " + role).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(status(HttpMethod.POST, "/api/v1/alternation/patterns", wellFormedPattern, token))
                    .as("POST patterns as " + role).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(status(HttpMethod.POST, "/api/v1/alternation/student-exceptions", wellFormedException,
                    token)).as("POST exceptions as " + role).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void pedagogicalManagerReadsPatternsButCannotWriteThem() {
        String pm = tokenFor(RoleCode.PEDAGOGICAL_MANAGER);
        assertThat(status(HttpMethod.GET, "/api/v1/alternation/patterns", null, pm))
                .isEqualTo(HttpStatus.OK);
        assertThat(status(HttpMethod.POST, "/api/v1/alternation/patterns", threeTwoBody("RYT-" + code()), pm))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void schoolAdministrationCanCreateAndAssignPatterns() {
        String schoolAdmin = tokenFor(RoleCode.SCHOOL_ADMINISTRATION);
        assertThat(status(HttpMethod.POST, "/api/v1/alternation/patterns", threeTwoBody("RYT-" + code()),
                schoolAdmin)).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void pedagogicalManagerIsLimitedToItsPerimeterForClassAssignments() {
        String admin = adminToken();
        Perimeter inScope = academicChain(admin);
        Perimeter outOfScope = academicChain(admin);
        Account managerAccount = accountWithRoles(RoleCode.PEDAGOGICAL_MANAGER);
        // Affectation du responsable pédagogique à la formation "inScope"
        created("/api/v1/pedagogical-assignments", Map.of("programPublicId", inScope.program(),
                "userPublicId", managerAccount.publicId(), "type", "PRIMARY_MANAGER",
                "validFrom", LocalDate.now().minusDays(1).toString()), admin);
        String pm = tokenFor(managerAccount);

        String pattern = (String) created("/api/v1/alternation/patterns", threeTwoBody("RYT-" + code()), admin)
                .get("publicId");

        // Dans le périmètre -> 201
        assertThat(status(HttpMethod.POST, "/api/v1/alternation/class-assignments", Map.of(
                "classGroupPublicId", inScope.classGroup(), "workStudyPatternPublicId", pattern,
                "cycleStartDate", "2026-09-01", "validFrom", "2026-09-01"), pm))
                .isEqualTo(HttpStatus.CREATED);

        // Hors périmètre -> 403, même en fournissant explicitement la classe
        ResponseEntity<Map<String, Object>> denied = exchange(HttpMethod.POST,
                "/api/v1/alternation/class-assignments", Map.of("classGroupPublicId", outOfScope.classGroup(),
                        "workStudyPatternPublicId", pattern, "cycleStartDate", "2026-09-01",
                        "validFrom", "2026-09-01"), pm);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(denied.getBody().get("code")).isEqualTo("ALT_FORBIDDEN");

        // La liste par classe hors périmètre est également refusée
        assertThat(status(HttpMethod.GET,
                "/api/v1/alternation/classes/" + outOfScope.classGroup() + "/assignments", null, pm))
                .isEqualTo(HttpStatus.FORBIDDEN);
        // La liste par classe dans le périmètre est autorisée
        assertThat(status(HttpMethod.GET,
                "/api/v1/alternation/classes/" + inScope.classGroup() + "/assignments", null, pm))
                .isEqualTo(HttpStatus.OK);

        // La liste plate ne retourne que les affectations du périmètre du
        // responsable — et aucun paramètre client ne l'élargit.
        Map<String, Object> flat = getMap("/api/v1/alternation/class-assignments", pm);
        List<?> content = (List<?>) flat.get("content");
        assertThat(content).allSatisfy(item ->
                assertThat(((Map<?, ?>) item).get("classGroupPublicId")).isEqualTo(inScope.classGroup()));
        assertThat(((Number) flat.get("totalElements")).intValue()).isEqualTo(1);
        // Filtrer explicitement sur une classe hors périmètre -> 403 (jamais un élargissement)
        assertThat(status(HttpMethod.GET,
                "/api/v1/alternation/class-assignments?class=" + outOfScope.classGroup(), null, pm))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void pedagogicalManagerIsLimitedToItsPerimeterForStudentExceptionsAndContext() {
        String admin = adminToken();
        Perimeter inScope = academicChain(admin);
        Perimeter outOfScope = academicChain(admin);
        Account managerAccount = accountWithRoles(RoleCode.PEDAGOGICAL_MANAGER);
        created("/api/v1/pedagogical-assignments", Map.of("programPublicId", inScope.program(),
                "userPublicId", managerAccount.publicId(), "type", "PRIMARY_MANAGER",
                "validFrom", LocalDate.now().minusDays(1).toString()), admin);
        String pm = tokenFor(managerAccount);

        String inScopeEnrollment = enrollmentInClass(admin, inScope.classGroup());
        String outOfScopeEnrollment = enrollmentInClass(admin, outOfScope.classGroup());

        // Exception individuelle dans le périmètre -> autorisée (201)
        Map<String, Object> body = Map.of("type", "REMOTE_ALLOWED", "startAt", "2026-09-07T08:00:00Z",
                "endAt", "2026-09-07T18:00:00Z", "timeZoneId", "Europe/Paris", "reason", "santé");
        Map<String, Object> inBody = new java.util.HashMap<>(body);
        inBody.put("enrollmentPublicId", inScopeEnrollment);
        assertThat(status(HttpMethod.POST, "/api/v1/alternation/student-exceptions", inBody, pm))
                .isEqualTo(HttpStatus.CREATED);

        // Exception individuelle hors périmètre -> 403
        Map<String, Object> outBody = new java.util.HashMap<>(body);
        outBody.put("enrollmentPublicId", outOfScopeEnrollment);
        ResponseEntity<Map<String, Object>> deniedException = exchange(HttpMethod.POST,
                "/api/v1/alternation/student-exceptions", outBody, pm);
        assertThat(deniedException.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(deniedException.getBody().get("code")).isEqualTo("ALT_FORBIDDEN");

        // Contexte d'une inscription dans le périmètre -> autorisé (200)
        assertThat(status(HttpMethod.GET, "/api/v1/alternation/enrollments/" + inScopeEnrollment
                + "/context?date=2026-09-07", null, pm)).isEqualTo(HttpStatus.OK);
        // Contexte d'une inscription hors périmètre -> 403
        assertThat(status(HttpMethod.GET, "/api/v1/alternation/enrollments/" + outOfScopeEnrollment
                + "/context?date=2026-09-07", null, pm)).isEqualTo(HttpStatus.FORBIDDEN);
        // Liste des exceptions d'une inscription hors périmètre -> 403
        assertThat(status(HttpMethod.GET, "/api/v1/alternation/enrollments/" + outOfScopeEnrollment
                + "/exceptions", null, pm)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String enrollmentInClass(String admin, String classPublicId) {
        String studentUser = accountWithRoles(RoleCode.STUDENT).publicId();
        String profile = (String) created("/api/v1/student-profiles", Map.of("userPublicId", studentUser,
                "studentNumber", "ESIC-2026-" + code()), admin).get("publicId");
        return (String) created("/api/v1/enrollments", Map.of("studentProfilePublicId", profile,
                "classGroupPublicId", classPublicId), admin).get("publicId");
    }

    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET, path, null, token);
        assertThat(response.getStatusCode()).as("GET " + path).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    // ------------------------------------------------------------------

    private static Map<String, Object> threeTwoBody(String code) {
        return Map.of("code", code, "name", "Rythme 3/2", "type", "THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY",
                "configuration", Map.of("schoolDays", List.of("MONDAY", "TUESDAY", "WEDNESDAY"),
                        "companyDays", List.of("THURSDAY", "FRIDAY")));
    }

    private record Perimeter(String program, String classGroup) {
    }

    private Perimeter academicChain(String admin) {
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
        String classGroup = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C1", "name", "Classe 1"), admin)
                .get("publicId");
        return new Perimeter(program, classGroup);
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.CREATED);
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

    private record Account(String publicId, String email) {
    }

    private Account accountWithRoles(RoleCode... roles) {
        UserAccount account = new UserAccount("alt-sec-" + UUID.randomUUID() + "@esic-connect.test",
                "Alt", "Sec", AccountStatus.ACTIVE);
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
        return tokenFor(accountWithRoles(roles));
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
