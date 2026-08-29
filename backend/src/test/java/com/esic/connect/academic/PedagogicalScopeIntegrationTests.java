package com.esic.connect.academic;

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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrôle de périmètre pédagogique sur l'ensemble du référentiel
 * (formation, niveau, promotion, classe), via {@code AcademicScopeGuard} :
 * <ul>
 *   <li>un {@code PEDAGOGICAL_MANAGER} seul ne lit et n'écrit que les
 *       formations de son périmètre (403 {@code ACAD_FORBIDDEN} ailleurs),
 *       et le filtrage descend aux niveaux/promotions/classes ;</li>
 *   <li>{@code PEDAGOGICAL_MANAGER + TEACHER} reste limité ;</li>
 *   <li>{@code PEDAGOGICAL_MANAGER + ADMIN} est global ;</li>
 *   <li>{@code SCHOOL_ADMINISTRATION} a une lecture globale mais ne gère
 *       pas les affectations.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PedagogicalScopeIntegrationTests {

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
    void scopeAppliesToProgramLevelPromotionAndClassGroup() {
        String admin = adminToken();
        String site = createSite(admin);
        String year = (String) created("/api/v1/academic-years", Map.of(
                "code", code("AY"), "name", "Y", "startDate", "2026-09-01", "endDate", "2027-08-31"), admin)
                .get("publicId");

        Branch in = branch(admin, site, year);
        Branch out = branch(admin, site, year);

        String pm = pedagogicalManagerAssignedTo(admin, in.program());

        // Lecture : listes filtrées + détail hors périmètre refusé
        assertThat(getMap("/api/v1/programs", pm).get("totalElements")).isEqualTo(1);
        assertThat(getMap("/api/v1/promotions", pm).get("totalElements")).isEqualTo(1);
        assertThat(getMap("/api/v1/class-groups", pm).get("totalElements")).isEqualTo(1);

        assertThat(status(HttpMethod.GET, "/api/v1/programs/" + in.program(), null, pm)).isEqualTo(HttpStatus.OK);
        assertForbidden(HttpMethod.GET, "/api/v1/programs/" + out.program(), null, pm);
        assertThat(status(HttpMethod.GET, "/api/v1/programs/" + in.program() + "/levels", null, pm))
                .isEqualTo(HttpStatus.OK);
        assertForbidden(HttpMethod.GET, "/api/v1/programs/" + out.program() + "/levels", null, pm);
        assertThat(status(HttpMethod.GET, "/api/v1/program-levels/" + in.level(), null, pm))
                .isEqualTo(HttpStatus.OK);
        assertForbidden(HttpMethod.GET, "/api/v1/program-levels/" + out.level(), null, pm);
        assertForbidden(HttpMethod.GET, "/api/v1/promotions/" + out.promotion(), null, pm);
        assertForbidden(HttpMethod.GET, "/api/v1/class-groups/" + out.classGroup(), null, pm);

        // Écriture : autorisée dans le périmètre, refusée dehors
        assertThat(status(HttpMethod.PATCH, "/api/v1/program-levels/" + in.level(),
                Map.of("name", "N1 bis", "sequenceNumber", 1), pm)).isEqualTo(HttpStatus.OK);
        assertForbidden(HttpMethod.PATCH, "/api/v1/program-levels/" + out.level(),
                Map.of("name", "x", "sequenceNumber", 1), pm);
        assertThat(status(HttpMethod.PATCH, "/api/v1/promotions/" + in.promotion(),
                Map.of("name", "P bis"), pm)).isEqualTo(HttpStatus.OK);
        assertForbidden(HttpMethod.PATCH, "/api/v1/promotions/" + out.promotion(), Map.of("name", "x"), pm);
        assertThat(status(HttpMethod.PATCH, "/api/v1/class-groups/" + in.classGroup(),
                Map.of("name", "C bis", "capacity", 20), pm)).isEqualTo(HttpStatus.OK);
        assertForbidden(HttpMethod.PATCH, "/api/v1/class-groups/" + out.classGroup(),
                Map.of("name", "x", "capacity", 20), pm);

        // Création d'un enfant : dans le périmètre OK, dehors 403
        assertThat(status(HttpMethod.POST, "/api/v1/programs/" + in.program() + "/levels",
                Map.of("code", "N2", "name", "N2", "sequenceNumber", 2), pm)).isEqualTo(HttpStatus.CREATED);
        assertForbidden(HttpMethod.POST, "/api/v1/programs/" + out.program() + "/levels",
                Map.of("code", "N2", "name", "N2", "sequenceNumber", 2), pm);

        // Cycle de vie d'un enfant dans le périmètre
        assertThat(status(HttpMethod.POST, "/api/v1/class-groups/" + in.classGroup() + "/archive",
                Map.of("reason", "réorg"), pm)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(status(HttpMethod.POST, "/api/v1/class-groups/" + in.classGroup() + "/restore",
                Map.of(), pm)).isEqualTo(HttpStatus.NO_CONTENT);
        assertForbidden(HttpMethod.POST, "/api/v1/class-groups/" + out.classGroup() + "/archive",
                Map.of("reason", "x"), pm);
    }

    @Test
    void managerPlusTeacherRemainsScoped() {
        String pm = tokenFor(RoleCode.PEDAGOGICAL_MANAGER, RoleCode.TEACHER);
        assertThat(getMap("/api/v1/programs", pm).get("totalElements")).isEqualTo(0);
        String someProgram = (String) created("/api/v1/programs",
                Map.of("code", code("PRG"), "name", "P", "programType", "BTS"), adminToken()).get("publicId");
        assertForbidden(HttpMethod.GET, "/api/v1/programs/" + someProgram, null, pm);
    }

    @Test
    void managerPlusAdministratorIsGlobal() {
        String admin = adminToken();
        String program = (String) created("/api/v1/programs",
                Map.of("code", code("PRG"), "name", "P", "programType", "BTS"), admin).get("publicId");
        String both = tokenFor(RoleCode.PEDAGOGICAL_MANAGER, RoleCode.ADMIN);

        assertThat(((Number) getMap("/api/v1/programs", both).get("totalElements")).intValue())
                .isGreaterThanOrEqualTo(1);
        assertThat(status(HttpMethod.GET, "/api/v1/programs/" + program, null, both)).isEqualTo(HttpStatus.OK);
        assertThat(status(HttpMethod.PATCH, "/api/v1/programs/" + program,
                Map.of("name", "P2", "programType", "OTHER"), both)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void schoolAdministrationHasGlobalReadButNoAssignmentWrite() {
        String admin = adminToken();
        String program = (String) created("/api/v1/programs",
                Map.of("code", code("PRG"), "name", "P", "programType", "BTS"), admin).get("publicId");
        String school = tokenFor(RoleCode.SCHOOL_ADMINISTRATION);

        assertThat(((Number) getMap("/api/v1/programs", school).get("totalElements")).intValue())
                .isGreaterThanOrEqualTo(1);
        assertThat(status(HttpMethod.GET, "/api/v1/programs/" + program, null, school)).isEqualTo(HttpStatus.OK);
        assertThat(status(HttpMethod.GET, "/api/v1/pedagogical-assignments", null, school))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(HttpMethod.POST, "/api/v1/pedagogical-assignments",
                Map.of("programPublicId", program, "userPublicId", UUID.randomUUID().toString(),
                        "type", "DELEGATE"), school)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private record Branch(String program, String level, String promotion, String classGroup) {
    }

    private Branch branch(String admin, String site, String year) {
        String program = (String) created("/api/v1/programs",
                Map.of("code", code("PRG"), "name", "P", "programType", "BTS"), admin).get("publicId");
        String level = (String) created("/api/v1/programs/" + program + "/levels",
                Map.of("code", "N1", "name", "N1", "sequenceNumber", 1), admin).get("publicId");
        String promotion = (String) created("/api/v1/promotions",
                Map.of("programPublicId", program, "academicYearPublicId", year, "code", "P1", "name", "P1"), admin)
                .get("publicId");
        String classGroup = (String) created("/api/v1/class-groups",
                Map.of("promotionPublicId", promotion, "programLevelPublicId", level,
                        "sitePublicId", site, "code", "C1", "name", "C1"), admin).get("publicId");
        return new Branch(program, level, promotion, classGroup);
    }

    private String pedagogicalManagerAssignedTo(String admin, String programPublicId) {
        Account pm = accountWithRoles(RoleCode.PEDAGOGICAL_MANAGER);
        ResponseEntity<Map<String, Object>> assignment = exchange(HttpMethod.POST, "/api/v1/pedagogical-assignments",
                Map.of("programPublicId", programPublicId, "userPublicId", pm.publicId(),
                        "type", "PRIMARY_MANAGER"), admin);
        assertThat(assignment.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return login(pm.email());
    }

    private String createSite(String token) {
        return (String) created("/api/v1/sites",
                Map.of("code", "SITE-" + UUID.randomUUID(), "name", "Campus", "timeZoneId", "Europe/Paris"), token)
                .get("publicId");
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

    private void assertForbidden(HttpMethod method, String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(method, path, body, token);
        assertThat(response.getStatusCode()).as(method + " " + path).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("ACAD_FORBIDDEN");
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
        UserAccount account = new UserAccount("scope-" + UUID.randomUUID() + "@esic-connect.test",
                "Scope", "Tester", AccountStatus.ACTIVE);
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
        return login(accountWithRoles(roles).email());
    }

    private String login(String email) {
        Map<String, Object> body = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) body.get("accessToken");
    }

    private static String code(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
