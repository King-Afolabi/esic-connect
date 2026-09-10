package com.esic.connect.myplanning;

import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
import com.esic.connect.support.AuthTestSupport;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * « Mon planning » (CDC §5.6/§5.7) : un formateur voit ses séances, un
 * apprenant celles des classes où il a une inscription active, chacun
 * strictement sur son propre périmètre — jamais celui d'un autre compte
 * ni un paramètre client.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MyPlanningIntegrationTests {

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
    void unFormateurEtUnApprenantNeVoientQueLeursPropresSeances() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        Account otherTeacher = accountWithRoles(RoleCode.TEACHER);
        Account student = accountWithRoles(RoleCode.STUDENT);

        String profile = (String) created("/api/v1/student-profiles", Map.of(
                "userPublicId", student.publicId(), "studentNumber", "ESIC-" + code()), admin)
                .get("publicId");
        created("/api/v1/enrollments", Map.of("studentProfilePublicId", profile,
                "classGroupPublicId", chain.classA(), "startDate", "2026-09-01"), admin);

        String ownSession = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), "Cours de la classe A"), admin)
                .get("publicId");
        // Séance d'une autre classe et d'un autre formateur : hors périmètre des deux.
        created("/api/v1/sessions",
                createBody(otherTeacher.publicId(), List.of(chain.classB()), "Cours de la classe B"), admin);

        Map<String, Object> teacherPlanning = getMap("/api/v1/me/planning", tokenFor(teacher));
        assertThat(teacherPlanning.get("role")).isEqualTo("TEACHER");
        List<?> teacherSessions = (List<?>) teacherPlanning.get("sessions");
        assertThat(teacherSessions).hasSize(1);
        assertThat(((Map<?, ?>) teacherSessions.get(0)).get("sessionPublicId")).isEqualTo(ownSession);
        assertThat(((Map<?, ?>) teacherSessions.get(0)).get("title")).isEqualTo("Cours de la classe A");

        Map<String, Object> studentPlanning = getMap("/api/v1/me/planning", tokenFor(student));
        assertThat(studentPlanning.get("role")).isEqualTo("STUDENT");
        List<?> studentSessions = (List<?>) studentPlanning.get("sessions");
        assertThat(studentSessions).hasSize(1);
        assertThat(((Map<?, ?>) studentSessions.get(0)).get("sessionPublicId")).isEqualTo(ownSession);
        Map<?, ?> teacherView = (Map<?, ?>) ((Map<?, ?>) studentSessions.get(0)).get("teacher");
        assertThat(teacherView.get("publicId")).isEqualTo(teacher.publicId());

        // Formateur sans séance / apprenant sans inscription : liste vide, jamais une erreur.
        Account idleTeacher = accountWithRoles(RoleCode.TEACHER);
        assertThat((List<?>) getMap("/api/v1/me/planning", tokenFor(idleTeacher)).get("sessions")).isEmpty();
        Account idleStudent = accountWithRoles(RoleCode.STUDENT);
        assertThat((List<?>) getMap("/api/v1/me/planning", tokenFor(idleStudent)).get("sessions")).isEmpty();
    }

    @Test
    void lAdministrationNaPasDeMonPlanningEtLAppelAnonymeEstRefuse() {
        String admin = adminToken();

        // ADMIN / SCHOOL_ADMINISTRATION ont déjà GET /api/v1/sessions (leur besoin
        // n'est pas « mon » planning mais la liste générale filtrable).
        assertThat(status("/api/v1/me/planning", admin)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status("/api/v1/me/planning", tokenFor(RoleCode.SCHOOL_ADMINISTRATION)))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status("/api/v1/me/planning", tokenFor(RoleCode.PEDAGOGICAL_MANAGER)))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange("/api/v1/me/planning", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------
    // Fixtures — mêmes helpers que CourseSessionIntegrationTests.
    // ------------------------------------------------------------------

    private record Chain(String classA, String classB) {
    }

    private Chain academicChain(String admin) {
        String site = (String) created("/api/v1/sites", Map.of("code", "SITE-" + code(),
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

    private Map<String, Object> createBody(String teacherPublicId, List<String> classPublicIds, String title) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("teacherPublicId", teacherPublicId);
        body.put("classPublicIds", classPublicIds);
        body.put("startsAt", "2026-09-15T08:00:00Z");
        body.put("endsAt", "2026-09-15T10:00:00Z");
        body.put("timeZoneId", "Europe/Paris");
        body.put("reason", "séance de test");
        if (title != null) {
            body.put("title", title);
        }
        return body;
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

    private HttpStatus status(String path, String token) {
        return (HttpStatus) exchange(HttpMethod.GET, path, null, token).getStatusCode();
    }

    private ResponseEntity<Map<String, Object>> exchange(String path, String token) {
        return exchange(HttpMethod.GET, path, null, token);
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
        UserAccount account = new UserAccount("mp-" + UUID.randomUUID() + "@esic-connect.test",
                "Mp", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, java.time.Instant.now(), true));
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
        return AuthTestSupport.accessToken(restTemplate, account.email(), PASSWORD);
    }

    private static String code() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
