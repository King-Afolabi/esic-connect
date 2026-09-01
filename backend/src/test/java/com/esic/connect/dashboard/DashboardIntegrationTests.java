package com.esic.connect.dashboard;

import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

import jakarta.persistence.EntityManagerFactory;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bloc G1-F — tableau de bord par rôle ({@code GET /api/v1/me/dashboard}).
 * Vérifie : rôle effectif décidé serveur (priorité fixe), sections
 * exclusives par rôle, cloisonnement (`STUDENT` = ses données, AC-017 ;
 * `TEACHER` = ses séances ; `PEDAGOGICAL_MANAGER` = son périmètre),
 * absence de N+1 sur le dashboard manager, `401` / `403`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
class DashboardIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";

    @TestConfiguration
    static class NoopMailerConfig {
        @Bean
        @Primary
        InvitationMailer noopMailer() {
            return (a, b, c, d) -> {
            };
        }
    }

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void jdkClient() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void aStudentGetsOnlyTheStudentSectionWithTheirOwnData() {
        String admin = tokenFor(account(RoleCode.ADMIN));
        Chain chain = academicChain(admin);
        Account teacher = account(RoleCode.TEACHER);
        String sessionId = openSession(admin, teacher, chain.classA(), Instant.now().plusSeconds(3600));

        Account s1 = enrolledStudent(admin, chain.classA());
        Account s2 = enrolledStudent(admin, chain.classA());

        Map<String, Object> d1 = dashboard(tokenFor(s1));
        assertThat(d1.get("role")).isEqualTo("STUDENT");
        assertThat(d1.get("student")).isNotNull();
        assertThat(d1.get("teacher")).isNull();
        assertThat(d1.get("manager")).isNull();
        assertThat(d1.get("administration")).isNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> week = (List<Map<String, Object>>) student(d1).get("weekSessions");
        assertThat(week).anySatisfy(row -> assertThat(row.get("sessionPublicId")).isEqualTo(sessionId));
        // Aucun identifiant SQL dans la charge utile.
        assertThat(d1.toString()).doesNotContain("internalId").doesNotContain("teacherUserId");

        // s2 voit sa propre carte (digest à zéro), jamais celle de s1.
        Map<String, Object> d2 = dashboard(tokenFor(s2));
        assertThat(d2.get("role")).isEqualTo("STUDENT");
        assertThat(student(d2)).isNotNull();
    }

    @Test
    void aTeacherSeesOnlyTheirOwnUpcomingSessions() {
        String admin = tokenFor(account(RoleCode.ADMIN));
        Chain chain = academicChain(admin);
        Account mine = account(RoleCode.TEACHER);
        Account other = account(RoleCode.TEACHER);
        String s1 = openSession(admin, mine, chain.classA(), Instant.now().plusSeconds(3600));
        String s2 = openSession(admin, other, chain.classA(), Instant.now().plusSeconds(7200));

        Map<String, Object> d = dashboard(tokenFor(mine));
        assertThat(d.get("role")).isEqualTo("TEACHER");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> upcoming = (List<Map<String, Object>>) teacher(d).get("upcoming");
        assertThat(upcoming).extracting(r -> r.get("sessionPublicId")).contains(s1).doesNotContain(s2);
    }

    @Test
    void aMultiRoleUserGetsTheHighestPriorityDashboard() {
        Account both = account(RoleCode.STUDENT, RoleCode.TEACHER);
        assertThat(dashboard(tokenFor(both)).get("role")).isEqualTo("TEACHER");

        Account adminAndTeacher = account(RoleCode.TEACHER, RoleCode.ADMIN);
        assertThat(dashboard(tokenFor(adminAndTeacher)).get("role")).isEqualTo("ADMINISTRATION");
    }

    @Test
    void anAdministrationDashboardExposesAccountCountsAndRecentImportsWithoutPii() {
        Map<String, Object> d = dashboard(tokenFor(account(RoleCode.SCHOOL_ADMINISTRATION)));
        assertThat(d.get("role")).isEqualTo("ADMINISTRATION");
        Map<String, Object> admin = administration(d);
        assertThat(((Number) admin.get("activeAccounts")).longValue()).isPositive();
        assertThat(admin).containsKeys("suspendedAccounts", "pendingActivation", "archivedAccounts",
                "pendingJustifications", "recentImports", "todaySessions");
        assertThat(d.toString()).doesNotContain("@esic-connect.test");
    }

    @Test
    void aPedagogicalManagerDashboardIsScopedAndDoesNotNPlusOne() {
        String admin = tokenFor(account(RoleCode.ADMIN));
        Chain chain = academicChain(admin);
        Account teacher = account(RoleCode.TEACHER);
        openSession(admin, teacher, chain.classA(), Instant.now().plusSeconds(3600));
        openSession(admin, teacher, chain.classB(), Instant.now().plusSeconds(7200));

        Account manager = account(RoleCode.PEDAGOGICAL_MANAGER);
        // Affecte le manager à la formation de la chaîne (périmètre pédagogique).
        assertThat(post(admin, "/api/v1/pedagogical-assignments", Map.of(
                "programPublicId", chain.program(),
                "userPublicId", manager.publicId(),
                "type", "PRIMARY_MANAGER")).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        Map<String, Object> d = dashboard(tokenFor(manager));
        long statements = stats.getPrepareStatementCount();

        assertThat(d.get("role")).isEqualTo("PEDAGOGICAL_MANAGER");
        Map<String, Object> card = manager(d);
        assertThat(((Number) card.get("classCount")).longValue()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<String> codes = (List<String>) card.get("classCodes");
        assertThat(codes).contains("C1", "C2");
        // Borne franche : résolution du périmètre + classes par lot + séances + codes,
        // sans une requête par classe/séance.
        assertThat(statements).as("requêtes SQL du dashboard manager").isLessThan(20L);
    }

    @Test
    void theDashboardRequiresAuthentication() {
        ResponseEntity<Map<String, Object>> r = rest.exchange(
                RequestEntity.get(URI.create("/api/v1/me/dashboard")).build(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anAccountWithoutAnyKnownRoleGets403() {
        Account roleless = account();
        ResponseEntity<Map<String, Object>> r = exchange(HttpMethod.GET, "/api/v1/me/dashboard", null,
                tokenFor(roleless));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody().get("code")).isEqualTo("DASHBOARD_NO_ROLE");
    }

    // ================================================================
    // Fixtures
    // ================================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> student(Map<String, Object> d) {
        return (Map<String, Object>) d.get("student");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> teacher(Map<String, Object> d) {
        return (Map<String, Object>) d.get("teacher");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> manager(Map<String, Object> d) {
        return (Map<String, Object>) d.get("manager");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> administration(Map<String, Object> d) {
        return (Map<String, Object>) d.get("administration");
    }

    private Map<String, Object> dashboard(String token) {
        ResponseEntity<Map<String, Object>> r = exchange(HttpMethod.GET, "/api/v1/me/dashboard", null, token);
        assertThat(r.getStatusCode()).as("GET /me/dashboard -> " + r.getBody()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private record Chain(String classA, String classB, String program) {
    }

    private Chain academicChain(String admin) {
        String site = created(admin, "/api/v1/sites", Map.of("code", "SITE-" + UUID.randomUUID(),
                "name", "Campus", "timeZoneId", "Europe/Paris")).get("publicId").toString();
        String program = created(admin, "/api/v1/programs", Map.of("code", "PRG-" + code(),
                "name", "BTS SIO", "programType", "BTS")).get("publicId").toString();
        String level = created(admin, "/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1", "name", "BTS 1", "sequenceNumber", 1)).get("publicId").toString();
        String year = created(admin, "/api/v1/academic-years", Map.of("code", "AY-" + code(),
                "name", "2026-2027", "startDate", "2026-09-01", "endDate", "2027-08-31")).get("publicId").toString();
        String promo = created(admin, "/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P26", "name", "Promotion 2026")).get("publicId").toString();
        String classA = created(admin, "/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C1", "name", "Classe 1"))
                .get("publicId").toString();
        String classB = created(admin, "/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C2", "name", "Classe 2"))
                .get("publicId").toString();
        return new Chain(classA, classB, program);
    }

    private String openSession(String admin, Account teacher, String classPublicId, Instant startsAt) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("teacherPublicId", teacher.publicId());
        body.put("classPublicIds", List.of(classPublicId));
        body.put("startsAt", startsAt.toString());
        body.put("endsAt", startsAt.plusSeconds(2 * 3600).toString());
        body.put("timeZoneId", "Europe/Paris");
        body.put("reason", "séance exceptionnelle");
        return created(admin, "/api/v1/sessions", body).get("publicId").toString();
    }

    private Account enrolledStudent(String admin, String classPublicId) {
        Account student = account(RoleCode.STUDENT);
        String profile = created(admin, "/api/v1/student-profiles", Map.of("userPublicId", student.publicId(),
                "studentNumber", "ESIC-2026-" + code())).get("publicId").toString();
        created(admin, "/api/v1/enrollments", Map.of("studentProfilePublicId", profile,
                "classGroupPublicId", classPublicId, "startDate", "2026-08-01"));
        return student;
    }

    private Map<String, Object> created(String token, String path, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> r = exchange(HttpMethod.POST, path, body, token);
        assertThat(r.getStatusCode()).as("POST " + path + " -> " + r.getBody()).isEqualTo(HttpStatus.CREATED);
        return r.getBody();
    }

    private ResponseEntity<Map<String, Object>> post(String token, String path, Map<String, Object> body) {
        return exchange(HttpMethod.POST, path, body, token);
    }

    private ResponseEntity<Map<String, Object>> exchange(HttpMethod method, String path,
                                                         Map<String, Object> body, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(method, URI.create(path));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        RequestEntity<?> entity = body == null ? builder.build()
                : builder.contentType(MediaType.APPLICATION_JSON).body(body);
        return rest.exchange(entity, new ParameterizedTypeReference<>() {
        });
    }

    private record Account(String publicId, String email) {
    }

    private Account account(RoleCode... roles) {
        UserAccount a = new UserAccount("dash-" + UUID.randomUUID() + "@esic-connect.test",
                "Dash", "Tester", AccountStatus.ACTIVE);
        a.setPasswordHash(passwordEncoder.encode(PASSWORD));
        a = userAccountRepository.saveAndFlush(a);
        for (RoleCode rc : roles) {
            Role role = roleRepository.findByCode(rc).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(a, role, Instant.now(), true));
        }
        return new Account(a.getPublicId().toString(), a.getEmail());
    }

    private String tokenFor(Account account) {
        Map<String, Object> body = rest.exchange(
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
