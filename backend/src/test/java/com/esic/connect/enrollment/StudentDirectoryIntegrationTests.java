package com.esic.connect.enrollment;

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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refonte du modèle Student / User / Enrollment (2026-09) : le rôle
 * {@code STUDENT} (module {@code identity}) est l'<strong>unique</strong>
 * source de vérité du statut apprenant sur l'écran « Apprenants »
 * ({@code GET /api/v1/students}). Ni {@code student_profile} ni
 * {@code enrollment} ne conditionnent la présence d'un compte dans cette
 * liste — ce sont des données facultatives et indépendantes.
 *
 * <p>Couvre les cinq scénarios exigés par le cahier des charges de la
 * refonte :
 * <ol>
 *   <li>{@code STUDENT} sans {@code student_profile} → visible ;</li>
 *   <li>{@code STUDENT} avec {@code student_profile} → visible ;</li>
 *   <li>{@code STUDENT} sans {@code enrollment} → visible ;</li>
 *   <li>non-{@code STUDENT} → non visible ;</li>
 *   <li>{@code STUDENT} avec plusieurs {@code enrollment} → une seule
 *       ligne (pas de doublon).</li>
 * </ol>
 * ainsi que le scénario bout en bout de vérification finale : créer un
 * compte {@code STUDENT} sans rien d'autre → visible ; lui ajouter un
 * profil → reste visible ; lui ajouter une inscription → reste visible,
 * une seule fois.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StudentDirectoryIntegrationTests {

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

    private String admin;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        admin = adminToken();
    }

    // ------------------------------------------------------------------
    // 1. STUDENT sans student_profile → visible
    // ------------------------------------------------------------------

    @Test
    void studentWithoutProfileIsVisibleInStudentsDirectory() {
        Account student = accountWithRoles(RoleCode.STUDENT);

        Map<String, Object> row = findRow(student.publicId());
        assertThat(row).as("un compte STUDENT sans profil doit apparaître dans /api/v1/students").isNotNull();
        assertThat(row.get("userPublicId")).isEqualTo(student.publicId());
        assertThat(row.get("studentProfilePublicId")).isNull();
        assertThat(row.get("studentNumber")).isNull();
        assertThat(row.get("currentEnrollmentPublicId")).isNull();

        // La fiche individuelle aussi.
        assertThat(status(HttpMethod.GET, "/api/v1/students/" + student.publicId())).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------------
    // 2. STUDENT avec student_profile → visible
    // ------------------------------------------------------------------

    @Test
    void studentWithProfileIsVisibleAndDecoratedWithProfileData() {
        Account student = accountWithRoles(RoleCode.STUDENT);
        String number = "ESIC-2026-" + shortCode();
        String profileId = (String) created("/api/v1/student-profiles", Map.of(
                "userPublicId", student.publicId(), "studentNumber", number,
                "workStudy", true, "companyName", "ACME"), admin).get("publicId");

        Map<String, Object> row = findRow(student.publicId());
        assertThat(row).isNotNull();
        assertThat(row.get("studentProfilePublicId")).isEqualTo(profileId);
        assertThat(row.get("studentNumber")).isEqualTo(number);
        assertThat(row.get("workStudy")).isEqualTo(true);
        assertThat(row.get("companyName")).isEqualTo("ACME");

        Map<String, Object> detail = getMap("/api/v1/students/" + student.publicId());
        assertThat(detail.get("studentProfilePublicId")).isEqualTo(profileId);
    }

    // ------------------------------------------------------------------
    // 3. STUDENT sans enrollment → visible
    // ------------------------------------------------------------------

    @Test
    void studentWithoutEnrollmentIsVisible() {
        Account student = accountWithRoles(RoleCode.STUDENT);
        created("/api/v1/student-profiles", Map.of(
                "userPublicId", student.publicId(), "studentNumber", "ESIC-2026-" + shortCode()), admin);

        Map<String, Object> row = findRow(student.publicId());
        assertThat(row).isNotNull();
        assertThat(row.get("currentEnrollmentPublicId")).isNull();
        assertThat(row.get("classGroupPublicId")).isNull();
        assertThat(row.get("enrollmentStatus")).isNull();
    }

    // ------------------------------------------------------------------
    // 4. non-STUDENT → non visible
    // ------------------------------------------------------------------

    @Test
    void nonStudentAccountIsNotVisibleInStudentsDirectory() {
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        Account manager = accountWithRoles(RoleCode.PEDAGOGICAL_MANAGER);
        Account schoolAdmin = accountWithRoles(RoleCode.SCHOOL_ADMINISTRATION);

        assertThat(findRow(teacher.publicId())).as("un TEACHER n'est pas un apprenant").isNull();
        assertThat(findRow(manager.publicId())).as("un PEDAGOGICAL_MANAGER n'est pas un apprenant").isNull();
        assertThat(findRow(schoolAdmin.publicId())).as("un SCHOOL_ADMINISTRATION n'est pas un apprenant").isNull();

        assertThat(status(HttpMethod.GET, "/api/v1/students/" + teacher.publicId()))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // 5. STUDENT avec plusieurs enrollments → une seule ligne
    // ------------------------------------------------------------------

    @Test
    void studentWithMultipleEnrollmentsAppearsExactlyOnce() {
        Account student = accountWithRoles(RoleCode.STUDENT);
        Chain chainA = academicChain();
        Chain chainB = academicChain();

        // Deux inscriptions actives, sur deux années scolaires distinctes
        // (une seule inscription ACTIVE par année — RG-012) : le même
        // compte cumule donc deux enrollments sans jamais dupliquer sa
        // ligne dans la liste des apprenants.
        String firstEnrollment = (String) created("/api/v1/enrollments", Map.of(
                "studentUserPublicId", student.publicId(), "classGroupPublicId", chainA.classA()), admin)
                .get("publicId");
        created("/api/v1/enrollments", Map.of(
                "studentUserPublicId", student.publicId(), "classGroupPublicId", chainB.classA()), admin);

        Map<String, Object> page = getMap("/api/v1/students?q=" + student.email().substring(0, 12));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
        long occurrences = content.stream()
                .filter(row -> student.publicId().equals(row.get("userPublicId")))
                .count();
        assertThat(occurrences).as("un même compte ne doit jamais apparaître deux fois").isEqualTo(1);

        // Historique complet néanmoins consultable via /enrollments.
        assertThat(count("/api/v1/enrollments?student=" + student.publicId())).isEqualTo(2);
        assertThat(firstEnrollment).isNotNull();
    }

    // ------------------------------------------------------------------
    // Scénario de vérification finale bout en bout
    // ------------------------------------------------------------------

    @Test
    void endToEndScenario_createBareStudent_addProfile_addEnrollment_remainsVisibleOnce() {
        // 1) Créer un compte STUDENT sans rien d'autre → apparaît dans Apprenants.
        Map<String, Object> createdUser = created("/api/v1/users", Map.of(
                "email", "e2e-" + UUID.randomUUID() + "@esic-connect.test",
                "firstName", "Bout", "lastName", "EnBout", "role", "STUDENT"), admin);
        String userPublicId = (String) createdUser.get("publicId");
        assertThat(status(HttpMethod.GET, "/api/v1/students/" + userPublicId)).isEqualTo(HttpStatus.OK);
        assertThat(findRow(userPublicId)).isNotNull();

        // 2) Ajouter un profil → reste visible.
        created("/api/v1/student-profiles", Map.of(
                "userPublicId", userPublicId, "studentNumber", "ESIC-2026-" + shortCode()), admin);
        Map<String, Object> afterProfile = findRow(userPublicId);
        assertThat(afterProfile).isNotNull();
        assertThat(afterProfile.get("studentProfilePublicId")).isNotNull();

        // 3) Ajouter une inscription → reste visible, une seule fois.
        Chain chain = academicChain();
        created("/api/v1/enrollments", Map.of(
                "studentUserPublicId", userPublicId, "classGroupPublicId", chain.classA()), admin);
        Map<String, Object> page = getMap("/api/v1/students?size=200");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
        long occurrences = content.stream().filter(row -> userPublicId.equals(row.get("userPublicId"))).count();
        assertThat(occurrences).isEqualTo(1);
        Map<String, Object> afterEnrollment = findRow(userPublicId);
        assertThat(afterEnrollment.get("currentEnrollmentPublicId")).isNotNull();
        assertThat(afterEnrollment.get("classGroupPublicId")).isEqualTo(chain.classA());
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private record Chain(String classA) {
    }

    private Chain academicChain() {
        String suffix = shortCode();
        String site = (String) created("/api/v1/sites", Map.of("code", "SITE-" + suffix,
                "name", "Campus", "timeZoneId", "Europe/Paris"), admin).get("publicId");
        String program = (String) created("/api/v1/programs", Map.of("code", "PRG-" + suffix,
                "name", "BTS SIO", "programType", "BTS"), admin).get("publicId");
        String level = (String) created("/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1", "name", "BTS 1", "sequenceNumber", 1), admin).get("publicId");
        String year = (String) created("/api/v1/academic-years", Map.of("code", "AY-" + suffix, "name", "2026-2027",
                "startDate", "2026-09-01", "endDate", "2027-08-31"), admin).get("publicId");
        String promo = (String) created("/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P-" + suffix, "name", "Promotion"), admin).get("publicId");
        String classA = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C-" + suffix, "name", "Classe"), admin)
                .get("publicId");
        return new Chain(classA);
    }

    /** Cherche la ligne d'un compte dans {@code GET /api/v1/students} (page large), ou {@code null}. */
    private Map<String, Object> findRow(String userPublicId) {
        Map<String, Object> page = getMap("/api/v1/students?size=200");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
        return content.stream().filter(row -> userPublicId.equals(row.get("userPublicId"))).findFirst().orElse(null);
    }

    private int count(String path) {
        return ((Number) getMap(path).get("totalElements")).intValue();
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> getMap(String path) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET, path, null, admin);
        assertThat(response.getStatusCode()).as("GET " + path).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private HttpStatus status(HttpMethod method, String path) {
        return (HttpStatus) exchange(method, path, null, admin).getStatusCode();
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
        UserAccount account = new UserAccount("sdir-" + UUID.randomUUID() + "@esic-connect.test",
                "Sdir", "Tester", AccountStatus.ACTIVE);
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
        return AuthTestSupport.accessToken(restTemplate, account.email(), PASSWORD);
    }

    private static String shortCode() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
