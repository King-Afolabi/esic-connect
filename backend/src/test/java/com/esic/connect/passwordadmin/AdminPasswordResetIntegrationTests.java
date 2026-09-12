package com.esic.connect.passwordadmin;

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
import org.junit.jupiter.api.BeforeEach;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Réinitialisation du mot de passe d'un tiers (module {@code passwordadmin}) :
 * hiérarchie de rôles (RG-009) et périmètre pédagogique du
 * {@code PEDAGOGICAL_MANAGER}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminPasswordResetIntegrationTests {

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
    void unSuperAdminReinitialiseNimporteQui() {
        String superAdmin = tokenFor(RoleCode.SUPER_ADMIN);
        Account admin = accountWithRoles(RoleCode.ADMIN);
        Account otherSuperAdmin = accountWithRoles(RoleCode.SUPER_ADMIN);
        Account student = accountWithRoles(RoleCode.STUDENT);

        assertThat(triggerReset(admin.publicId(), superAdmin)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(triggerReset(otherSuperAdmin.publicId(), superAdmin)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(triggerReset(student.publicId(), superAdmin)).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void unAdminNePeutPasViserUnAutreAdminNiUnSuperAdmin() {
        String admin = tokenFor(RoleCode.ADMIN);
        Account otherAdmin = accountWithRoles(RoleCode.ADMIN);
        Account superAdmin = accountWithRoles(RoleCode.SUPER_ADMIN);
        Account manager = accountWithRoles(RoleCode.PEDAGOGICAL_MANAGER);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        Account student = accountWithRoles(RoleCode.STUDENT);

        assertThat(triggerReset(otherAdmin.publicId(), admin)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(triggerReset(superAdmin.publicId(), admin)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(triggerReset(manager.publicId(), admin)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(triggerReset(teacher.publicId(), admin)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(triggerReset(student.publicId(), admin)).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void schoolAdministrationAMemePerimetreQueAdmin() {
        String schoolAdmin = tokenFor(RoleCode.SCHOOL_ADMINISTRATION);
        Account admin = accountWithRoles(RoleCode.ADMIN);
        Account teacher = accountWithRoles(RoleCode.TEACHER);

        assertThat(triggerReset(admin.publicId(), schoolAdmin)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(triggerReset(teacher.publicId(), schoolAdmin)).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void unResponsablePedagogiqueEstLimiteAuFormateurEtLApprenantDeSonPerimetre() {
        String admin = adminToken();
        String managerProgram = createProgram(admin, "PRG-A");
        String otherProgram = createProgram(admin, "PRG-B");
        String site = createSite(admin);
        String classA = createClass(admin, managerProgram, site, "C1");
        String classB = createClass(admin, otherProgram, site, "C2");

        Account manager = accountWithRoles(RoleCode.PEDAGOGICAL_MANAGER);
        created("/api/v1/pedagogical-assignments", Map.of(
                "programPublicId", managerProgram,
                "userPublicId", manager.publicId(),
                "type", "PRIMARY_MANAGER",
                "validFrom", "2020-01-01"), admin);
        String managerToken = tokenFor(manager);

        Account ownTeacher = accountWithRoles(RoleCode.TEACHER);
        created("/api/v1/sessions", sessionBody(ownTeacher.publicId(), classA), admin);
        Account ownStudent = accountWithRoles(RoleCode.STUDENT);
        enroll(admin, ownStudent, classA);

        Account otherTeacher = accountWithRoles(RoleCode.TEACHER);
        created("/api/v1/sessions", sessionBody(otherTeacher.publicId(), classB), admin);
        Account otherStudent = accountWithRoles(RoleCode.STUDENT);
        enroll(admin, otherStudent, classB);

        // Périmètre propre : autorisé.
        assertThat(triggerReset(ownTeacher.publicId(), managerToken)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(triggerReset(ownStudent.publicId(), managerToken)).isEqualTo(HttpStatus.NO_CONTENT);

        // Autre formation : refusé, jamais un contournement silencieux.
        assertThat(triggerReset(otherTeacher.publicId(), managerToken)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(triggerReset(otherStudent.publicId(), managerToken)).isEqualTo(HttpStatus.FORBIDDEN);

        // Un responsable pédagogique ne peut jamais viser un rôle d'administration.
        Account someAdmin = accountWithRoles(RoleCode.ADMIN);
        assertThat(triggerReset(someAdmin.publicId(), managerToken)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void compteInconnuEtAppelAnonyme() {
        String admin = adminToken();
        assertThat(triggerReset(UUID.randomUUID().toString(), admin)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange(HttpMethod.POST, "/api/v1/users/" + UUID.randomUUID() + "/password-reset", null, null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Un formateur ou un apprenant n'a pas ce droit.
        assertThat(triggerReset(accountWithRoles(RoleCode.STUDENT).publicId(), tokenFor(RoleCode.TEACHER)))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private String createSite(String admin) {
        return (String) created("/api/v1/sites", Map.of("code", "SITE-" + code(),
                "name", "Campus", "timeZoneId", "Europe/Paris"), admin).get("publicId");
    }

    private String createProgram(String admin, String label) {
        String program = (String) created("/api/v1/programs", Map.of("code", label + "-" + code(),
                "name", "Formation " + label, "programType", "BTS"), admin).get("publicId");
        String level = (String) created("/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1", "name", "Niveau 1", "sequenceNumber", 1), admin).get("publicId");
        String year = (String) created("/api/v1/academic-years", Map.of("code", "AY-" + code(),
                "name", "2026-2027", "startDate", "2026-09-01", "endDate", "2027-08-31"), admin).get("publicId");
        String promo = (String) created("/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P-" + code(), "name", "Promotion"), admin).get("publicId");
        // Encode le niveau dans le programme retourné via un identifiant composite maison
        // n'est pas possible ici : on renvoie le programme, et createClass recrée le niveau
        // au besoin. Pour simplifier, on stocke niveau/promo dans une carte statique locale.
        levelByProgram.put(program, level);
        promotionByProgram.put(program, promo);
        return program;
    }

    private final Map<String, String> levelByProgram = new java.util.HashMap<>();
    private final Map<String, String> promotionByProgram = new java.util.HashMap<>();

    private String createClass(String admin, String program, String site, String code) {
        return (String) created("/api/v1/class-groups", Map.of(
                "promotionPublicId", promotionByProgram.get(program),
                "programLevelPublicId", levelByProgram.get(program),
                "sitePublicId", site, "code", code + "-" + code(), "name", "Classe " + code), admin)
                .get("publicId");
    }

    private Map<String, Object> sessionBody(String teacherPublicId, String classPublicId) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("teacherPublicId", teacherPublicId);
        body.put("classPublicIds", List.of(classPublicId));
        body.put("startsAt", "2026-09-15T08:00:00Z");
        body.put("endsAt", "2026-09-15T10:00:00Z");
        body.put("timeZoneId", "Europe/Paris");
        body.put("reason", "séance de test");
        body.put("title", "Cours");
        return body;
    }

    private void enroll(String admin, Account student, String classPublicId) {
        created("/api/v1/enrollments", Map.of("studentUserPublicId", student.publicId(),
                "classGroupPublicId", classPublicId, "startDate", "2026-09-01"), admin);
    }

    private HttpStatus triggerReset(String targetPublicId, String token) {
        return (HttpStatus) exchange(HttpMethod.POST, "/api/v1/users/" + targetPublicId + "/password-reset",
                null, token).getStatusCode();
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.CREATED);
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

    private record Account(String publicId, String email) {
    }

    private Account accountWithRoles(RoleCode... roles) {
        UserAccount account = new UserAccount("pwdadmin-" + UUID.randomUUID() + "@esic-connect.test",
                "Pwd", "Tester", AccountStatus.ACTIVE);
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
