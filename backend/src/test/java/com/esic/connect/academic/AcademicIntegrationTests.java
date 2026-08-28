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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parcours de bout en bout du référentiel académique : hiérarchie année →
 * formation → niveau → promotion → classe, rattachement d'une classe à un
 * site (port {@code SiteDirectory}), archivage / restauration en cascade
 * contrôlée, validations (période, période de promotion dans l'année,
 * niveau étranger à la formation), pagination et écriture de l'audit.
 * Les DTO ne doivent jamais exposer d'identifiant SQL interne.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AcademicIntegrationTests {

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
    // Hiérarchie complète + audit
    // ------------------------------------------------------------------

    @Test
    void academicHierarchyLifecycleIsAuditedAndHidesInternalIds() {
        String admin = adminToken();
        String sitePublicId = createSite(admin);

        Map<String, Object> year = created("/api/v1/academic-years", Map.of(
                "code", code("AY"), "name", "2026-2027",
                "startDate", "2026-09-01", "endDate", "2027-08-31"), admin);
        String yearId = (String) year.get("publicId");
        assertThat(year).containsKey("publicId").doesNotContainKey("id");
        assertThat(year.get("status")).isEqualTo("ACTIVE");

        String programCode = code("PRG");
        Map<String, Object> program = created("/api/v1/programs", Map.of(
                "code", programCode, "name", "BTS SIO", "programType", "bts"), admin);
        String programId = (String) program.get("publicId");
        assertThat(program.get("programType")).isEqualTo("BTS");

        Map<String, Object> level = created("/api/v1/programs/" + programId + "/levels", Map.of(
                "code", "N1", "name", "BTS 1", "sequenceNumber", 1), admin);
        String levelId = (String) level.get("publicId");
        assertThat(level.get("programPublicId")).isEqualTo(programId);
        assertThat(level).doesNotContainKeys("id", "programId");

        Map<String, Object> promotion = created("/api/v1/promotions", Map.of(
                "programPublicId", programId, "academicYearPublicId", yearId,
                "code", "P26", "name", "Promotion 2026"), admin);
        String promotionId = (String) promotion.get("publicId");
        assertThat(promotion.get("programPublicId")).isEqualTo(programId);
        assertThat(promotion.get("academicYearPublicId")).isEqualTo(yearId);
        assertThat(promotion).doesNotContainKeys("id", "programId", "academicYearId");

        Map<String, Object> classGroup = created("/api/v1/class-groups", Map.of(
                "promotionPublicId", promotionId, "programLevelPublicId", levelId,
                "sitePublicId", sitePublicId, "code", "C1", "name", "Classe 1", "capacity", 24), admin);
        String classGroupId = (String) classGroup.get("publicId");
        assertThat(classGroup.get("promotionPublicId")).isEqualTo(promotionId);
        assertThat(classGroup.get("programLevelPublicId")).isEqualTo(levelId);
        assertThat(classGroup.get("sitePublicId")).isEqualTo(sitePublicId);
        assertThat(classGroup.get("capacity")).isEqualTo(24);
        assertThat(classGroup).doesNotContainKeys("id", "siteId", "promotionId", "programLevelId");

        // Consultations
        assertThat(getMap("/api/v1/class-groups/" + classGroupId, admin).get("code")).isEqualTo("C1");
        assertThat(getMap("/api/v1/programs?q=" + programCode, admin).get("totalElements")).isEqualTo(1);
        assertThat(getMap("/api/v1/class-groups?promotion=" + promotionId, admin).get("totalElements")).isEqualTo(1);

        // Modification
        ResponseEntity<Map<String, Object>> renamed = exchange(HttpMethod.PATCH,
                "/api/v1/class-groups/" + classGroupId, Map.of("name", "Classe 1 A", "capacity", 30), admin);
        assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(renamed.getBody().get("name")).isEqualTo("Classe 1 A");

        // Archivage en cascade contrôlée (enfant -> parent)
        assertThat(action("/api/v1/class-groups/" + classGroupId + "/archive", "réorg", admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(action("/api/v1/promotions/" + promotionId + "/archive", "clôture", admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(action("/api/v1/program-levels/" + levelId + "/archive", "révision", admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(action("/api/v1/programs/" + programId + "/archive", "fin de formation", admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(action("/api/v1/academic-years/" + yearId + "/archive", "année close", admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getMap("/api/v1/programs/" + programId, admin).get("status")).isEqualTo("ARCHIVED");

        // Restauration : chaque parent doit être actif avant son enfant.
        assertThat(restore("/api/v1/academic-years/" + yearId + "/restore", admin)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restore("/api/v1/programs/" + programId + "/restore", admin)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getMap("/api/v1/programs/" + programId, admin).get("status")).isEqualTo("ACTIVE");
        assertThat(restore("/api/v1/program-levels/" + levelId + "/restore", admin)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restore("/api/v1/promotions/" + promotionId + "/restore", admin)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restore("/api/v1/class-groups/" + classGroupId + "/restore", admin)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getMap("/api/v1/class-groups/" + classGroupId, admin).get("status")).isEqualTo("ACTIVE");

        assertThat(auditActions(programId)).contains("PROGRAM_CREATED", "PROGRAM_ARCHIVED", "PROGRAM_RESTORED");
        assertThat(auditActions(promotionId)).contains("PROMOTION_CREATED", "PROMOTION_ARCHIVED",
                "PROMOTION_RESTORED");
        assertThat(auditActions(classGroupId)).contains("CLASS_GROUP_CREATED", "CLASS_GROUP_UPDATED",
                "CLASS_GROUP_ARCHIVED", "CLASS_GROUP_RESTORED");
        assertThat(auditActions(yearId)).contains("ACADEMIC_YEAR_CREATED", "ACADEMIC_YEAR_ARCHIVED",
                "ACADEMIC_YEAR_RESTORED");
    }

    @Test
    void restoreClassGroupRefusedWhileAcademicYearArchived() {
        String admin = adminToken();
        String site = createSite(admin);
        String program = (String) created("/api/v1/programs",
                Map.of("code", code("PRG"), "name", "P", "programType", "BTS"), admin).get("publicId");
        String level = (String) created("/api/v1/programs/" + program + "/levels",
                Map.of("code", "N1", "name", "N1", "sequenceNumber", 1), admin).get("publicId");
        String year = (String) created("/api/v1/academic-years",
                Map.of("code", code("AY"), "name", "Y", "startDate", "2026-09-01", "endDate", "2027-08-31"), admin)
                .get("publicId");
        String promotion = (String) created("/api/v1/promotions",
                Map.of("programPublicId", program, "academicYearPublicId", year, "code", "P1", "name", "P1"), admin)
                .get("publicId");
        String classGroup = (String) created("/api/v1/class-groups",
                Map.of("promotionPublicId", promotion, "programLevelPublicId", level,
                        "sitePublicId", site, "code", "C1", "name", "C1"), admin).get("publicId");

        // Archive la classe, puis la promotion, puis l'année.
        action("/api/v1/class-groups/" + classGroup + "/archive", "x", admin);
        action("/api/v1/promotions/" + promotion + "/archive", "x", admin);
        action("/api/v1/academic-years/" + year + "/archive", "x", admin);

        // Restaurer la classe alors que l'année (grand-parent) est archivée est refusé.
        ResponseEntity<Map<String, Object>> restore = exchange(HttpMethod.POST,
                "/api/v1/class-groups/" + classGroup + "/restore", Map.of(), admin);
        assertThat(restore.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(restore.getBody().get("code")).isEqualTo("ACAD_ARCHIVED_PARENT");
    }

    @Test
    void academicYearPeriodUpdateRefusedWhenItExcludesExistingPromotion() {
        String admin = adminToken();
        String program = (String) created("/api/v1/programs",
                Map.of("code", code("PRG"), "name", "P", "programType", "BTS"), admin).get("publicId");
        String year = (String) created("/api/v1/academic-years",
                Map.of("code", code("AY"), "name", "Y", "startDate", "2026-09-01", "endDate", "2027-08-31"), admin)
                .get("publicId");
        created("/api/v1/promotions", Map.of("programPublicId", program, "academicYearPublicId", year,
                "code", "P1", "name", "P1", "startDate", "2026-10-01", "endDate", "2027-06-30"), admin);

        // Nouvelle période qui exclut le début de la promotion existante.
        ResponseEntity<Map<String, Object>> update = exchange(HttpMethod.PATCH, "/api/v1/academic-years/" + year,
                Map.of("name", "Y", "startDate", "2026-11-01", "endDate", "2027-08-31"), admin);
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(update.getBody().get("code")).isEqualTo("ACAD_ACADEMIC_YEAR_PERIOD_CONFLICT");

        // Une période qui englobe toujours la promotion est acceptée.
        ResponseEntity<Map<String, Object>> ok = exchange(HttpMethod.PATCH, "/api/v1/academic-years/" + year,
                Map.of("name", "Y2", "startDate", "2026-09-15", "endDate", "2027-07-31"), admin);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------------
    // Règles de hiérarchie et validations
    // ------------------------------------------------------------------

    @Test
    void archiveProgramRefusedWhileActiveChildrenRemain() {
        String admin = adminToken();
        String programId = (String) created("/api/v1/programs",
                Map.of("code", code("PRG"), "name", "P", "programType", "OTHER"), admin).get("publicId");
        created("/api/v1/programs/" + programId + "/levels",
                Map.of("code", "N1", "name", "N1", "sequenceNumber", 1), admin);

        ResponseEntity<Map<String, Object>> archive = exchange(HttpMethod.POST,
                "/api/v1/programs/" + programId + "/archive", Map.of("reason", "x"), admin);
        assertThat(archive.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(archive.getBody().get("code")).isEqualTo("ACAD_HAS_ACTIVE_CHILDREN");
    }

    @Test
    void classGroupRejectsLevelFromAnotherProgram() {
        String admin = adminToken();
        String site = createSite(admin);
        String programA = (String) created("/api/v1/programs",
                Map.of("code", code("PRG"), "name", "A", "programType", "BTS"), admin).get("publicId");
        String programB = (String) created("/api/v1/programs",
                Map.of("code", code("PRG"), "name", "B", "programType", "BTS"), admin).get("publicId");
        String levelA = (String) created("/api/v1/programs/" + programA + "/levels",
                Map.of("code", "N1", "name", "N1", "sequenceNumber", 1), admin).get("publicId");
        String year = (String) created("/api/v1/academic-years",
                Map.of("code", code("AY"), "name", "Y", "startDate", "2026-09-01", "endDate", "2027-08-31"), admin)
                .get("publicId");
        String promotionB = (String) created("/api/v1/promotions",
                Map.of("programPublicId", programB, "academicYearPublicId", year, "code", "P1", "name", "P1"), admin)
                .get("publicId");

        ResponseEntity<Map<String, Object>> create = exchange(HttpMethod.POST, "/api/v1/class-groups",
                Map.of("promotionPublicId", promotionB, "programLevelPublicId", levelA,
                        "sitePublicId", site, "code", "C1", "name", "C1"), admin);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(create.getBody().get("code")).isEqualTo("ACAD_PROGRAM_LEVEL_MISMATCH");
    }

    @Test
    void promotionPeriodMustFallWithinAcademicYear() {
        String admin = adminToken();
        String program = (String) created("/api/v1/programs",
                Map.of("code", code("PRG"), "name", "P", "programType", "BTS"), admin).get("publicId");
        String year = (String) created("/api/v1/academic-years",
                Map.of("code", code("AY"), "name", "Y", "startDate", "2026-09-01", "endDate", "2027-08-31"), admin)
                .get("publicId");

        ResponseEntity<Map<String, Object>> create = exchange(HttpMethod.POST, "/api/v1/promotions",
                Map.of("programPublicId", program, "academicYearPublicId", year, "code", "P1", "name", "P1",
                        "startDate", "2026-08-01", "endDate", "2027-06-30"), admin);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(create.getBody().get("code")).isEqualTo("ACAD_PROMOTION_PERIOD_OUT_OF_YEAR");
    }

    @Test
    void academicYearRejectsInvertedPeriodAndDuplicateProgramCodeRejected() {
        String admin = adminToken();
        ResponseEntity<Map<String, Object>> badPeriod = exchange(HttpMethod.POST, "/api/v1/academic-years",
                Map.of("code", code("AY"), "name", "Y", "startDate", "2027-09-01", "endDate", "2026-08-31"), admin);
        assertThat(badPeriod.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badPeriod.getBody().get("code")).isEqualTo("ACAD_INVALID_PERIOD");

        String programCode = code("PRG");
        created("/api/v1/programs", Map.of("code", programCode, "name", "P", "programType", "BTS"), admin);
        ResponseEntity<Map<String, Object>> duplicate = exchange(HttpMethod.POST, "/api/v1/programs",
                Map.of("code", programCode, "name", "P2", "programType", "BTS"), admin);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("code")).isEqualTo("ACAD_DUPLICATE_CODE");
    }

    @Test
    void classGroupCannotBeCreatedUnderArchivedPromotion() {
        String admin = adminToken();
        String site = createSite(admin);
        String program = (String) created("/api/v1/programs",
                Map.of("code", code("PRG"), "name", "P", "programType", "BTS"), admin).get("publicId");
        String level = (String) created("/api/v1/programs/" + program + "/levels",
                Map.of("code", "N1", "name", "N1", "sequenceNumber", 1), admin).get("publicId");
        String year = (String) created("/api/v1/academic-years",
                Map.of("code", code("AY"), "name", "Y", "startDate", "2026-09-01", "endDate", "2027-08-31"), admin)
                .get("publicId");
        String promotion = (String) created("/api/v1/promotions",
                Map.of("programPublicId", program, "academicYearPublicId", year, "code", "P1", "name", "P1"), admin)
                .get("publicId");
        assertThat(action("/api/v1/promotions/" + promotion + "/archive", "gel", admin))
                .isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map<String, Object>> create = exchange(HttpMethod.POST, "/api/v1/class-groups",
                Map.of("promotionPublicId", promotion, "programLevelPublicId", level,
                        "sitePublicId", site, "code", "C1", "name", "C1"), admin);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(create.getBody().get("code")).isEqualTo("ACAD_ARCHIVED_PARENT");
    }

    @Test
    void listClampsPageSizeAndRejectsUnknownSort() {
        String admin = adminToken();
        assertThat(getMap("/api/v1/programs?size=9999", admin).get("size")).isEqualTo(100);

        ResponseEntity<Map<String, Object>> badSort = exchange(HttpMethod.GET,
                "/api/v1/programs?sort=createdById,asc", null, admin);
        assertThat(badSort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badSort.getBody().get("code")).isEqualTo("ACAD_INVALID_SORT");
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private String createSite(String token) {
        Map<String, Object> body = created("/api/v1/sites",
                Map.of("code", "SITE-" + UUID.randomUUID(), "name", "Campus", "timeZoneId", "Europe/Paris"), token);
        return (String) body.get("publicId");
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

    private HttpStatus action(String path, String reason, String token) {
        return (HttpStatus) exchange(HttpMethod.POST, path, Map.of("reason", reason), token).getStatusCode();
    }

    private HttpStatus restore(String path, String token) {
        return (HttpStatus) exchange(HttpMethod.POST, path, Map.of(), token).getStatusCode();
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
                .filter(e -> target.equals(e.getResourcePublicId()))
                .map(AuditEvent::getAction)
                .toList();
    }

    private String adminToken() {
        return tokenFor(RoleCode.ADMIN);
    }

    private String tokenFor(RoleCode... roles) {
        UserAccount account = new UserAccount("acad-" + UUID.randomUUID() + "@esic-connect.test",
                "Acad", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        Map<String, Object> login = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", account.getEmail(), "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) login.get("accessToken");
    }

    private static String code(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
