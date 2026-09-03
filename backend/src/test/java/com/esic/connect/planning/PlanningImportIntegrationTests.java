package com.esic.connect.planning;

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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parcours de bout en bout de la <strong>simulation</strong> d'un import
 * CSV de planning (EF-PLAN-001/002 ; AC-007 : « des séances uniquement
 * après confirmation et publication » — ici : simulation ⇒ 0 séance).
 * Couvre : téléversement multipart, réponse sans identifiant SQL,
 * anomalies de valeur, formateur non éligible, doublon de {@code slot_key},
 * conflit de chevauchement, colonnes manquantes, annulation idempotente,
 * sécurité par rôle et périmètre.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlanningImportIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    private static final String HEADER =
            "slot_key,session_date,start_time,end_time,time_zone_id,title,teacher_public_id,room_code\n";

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
    void validFileSimulatesAsAddedRowsWithoutLeakingIds() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();

        String csv = HEADER
                + "S1,2026-09-07,09:00,12:00,Europe/Paris,Algorithmique," + teacher + ",A101\n"
                + "S2,2026-09-07,13:30,17:00,Europe/Paris,Bases de données," + teacher + ",A101\n";
        ResponseEntity<Map<String, Object>> response = upload("planning.csv", csv, admin, classId);
        assertThat(response.getStatusCode()).as("%s", response.getBody()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> job = response.getBody();

        assertThat(job.get("status")).isEqualTo("SIMULATED");
        assertThat(((Number) job.get("totalRows")).intValue()).isEqualTo(2);
        assertThat(((Number) job.get("addedRows")).intValue()).isEqualTo(2);
        assertThat(((Number) job.get("errorRows")).intValue()).isZero();
        assertThat(job.get("confirmable")).isEqualTo(true);
        assertThat(job.get("classGroupPublicId")).isEqualTo(classId);
        assertThat(job).doesNotContainKeys("id", "classGroupId", "requestedById", "fileSha256");

        String jobId = (String) job.get("publicId");
        Map<String, Object> rows = getMap("/api/v1/planning-imports/" + jobId + "/rows", admin);
        List<?> content = (List<?>) rows.get("content");
        assertThat(content).hasSize(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> firstRow = (Map<String, Object>) content.get(0);
        assertThat(firstRow.get("rowStatus")).isEqualTo("VALID");
        assertThat(firstRow.get("plannedAction")).isEqualTo("ADDED");
        assertThat(firstRow.get("slotKey")).isEqualTo("S1");
        assertThat(firstRow).doesNotContainKey("id");
    }

    @Test
    void teacherNotEligibleAndDuplicateSlotKeyProduceBlockingRows() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();

        String csv = HEADER
                + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours," + UUID.randomUUID() + ",A1\n"
                + "S1,2026-09-08,09:00,12:00,Europe/Paris,Cours," + teacher + ",A1\n";
        Map<String, Object> job = upload("planning.csv", csv, admin, classId).getBody();
        assertThat(((Number) job.get("errorRows")).intValue()).isEqualTo(2);
        assertThat(job.get("confirmable")).isEqualTo(false);

        String jobId = (String) job.get("publicId");
        List<?> content = (List<?>) getMap("/api/v1/planning-imports/" + jobId + "/rows", admin).get("content");
        String allCodes = content.toString();
        assertThat(allCodes).contains("PLAN_TEACHER_NOT_ELIGIBLE");
        assertThat(allCodes).contains("PLAN_SLOT_KEY_DUPLICATED");
    }

    @Test
    void overlappingRowsAreFlaggedAsAClassConflict() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();

        String csv = HEADER
                + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours A," + teacher + ",A1\n"
                + "S2,2026-09-07,10:00,13:00,Europe/Paris,Cours B," + teacher + ",A1\n";
        Map<String, Object> job = upload("planning.csv", csv, admin, classId).getBody();
        assertThat(((Number) job.get("errorRows")).intValue()).isEqualTo(2);

        String jobId = (String) job.get("publicId");
        String rows = getMap("/api/v1/planning-imports/" + jobId + "/rows", admin).get("content").toString();
        assertThat(rows).contains("PLAN_CONFLICT_CLASS");
        assertThat(rows).contains("PLAN_CONFLICT_TEACHER");
        assertThat(rows).contains("PLAN_CONFLICT_ROOM");
        assertThat(rows).contains("CONFLICT");
    }

    @Test
    void missingMandatoryColumnIsRejectedBeforeAnyJobIsPersisted() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String csv = "slot_key,session_date,start_time\nS1,2026-09-07,09:00\n";
        ResponseEntity<Map<String, Object>> response = upload("planning.csv", csv, admin, classId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("PLAN_MISSING_COLUMNS");
    }

    @Test
    void nonCsvUploadIsRejected() {
        String admin = adminToken();
        String classId = classGroup(admin);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource("PKzip".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "planning.csv";
            }
        };
        parts.add("file", new HttpEntity<>(resource, csvPartHeaders()));
        parts.add("classGroupPublicId", classId);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                URI.create("/api/v1/planning-imports"), HttpMethod.POST,
                new HttpEntity<>(parts, multipartHeaders(admin)), new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().get("code")).isEqualTo("PLAN_UNSUPPORTED_FILE");
    }

    @Test
    void cancelIsIdempotentAndFlipsTheJobStatus() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        String csv = HEADER + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours," + teacher + ",A1\n";
        String jobId = (String) upload("planning.csv", csv, admin, classId).getBody().get("publicId");

        assertThat(status(HttpMethod.POST, "/api/v1/planning-imports/" + jobId + "/cancel", admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getMap("/api/v1/planning-imports/" + jobId, admin).get("status")).isEqualTo("CANCELLED");
        // Idempotent.
        assertThat(status(HttpMethod.POST, "/api/v1/planning-imports/" + jobId + "/cancel", admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void securityRolesAndScopeAreEnforced() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        String csv = HEADER + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours," + teacher + ",A1\n";

        // Pas de jeton -> 401.
        assertThat(upload("planning.csv", csv, null, classId).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        // STUDENT -> 403. TEACHER -> 403.
        assertThat(upload("planning.csv", csv, tokenFor(RoleCode.STUDENT), classId).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(upload("planning.csv", csv, tokenFor(RoleCode.TEACHER), classId).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // PEDAGOGICAL_MANAGER sans affectation : hors périmètre -> 403 PLAN_SCOPE_FORBIDDEN.
        ResponseEntity<Map<String, Object>> pm = upload("planning.csv", csv,
                tokenFor(RoleCode.PEDAGOGICAL_MANAGER), classId);
        assertThat(pm.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(pm.getBody().get("code")).isEqualTo("PLAN_SCOPE_FORBIDDEN");
    }

    /**
     * Régression F-SEC-1 : un paramètre obligatoire absent doit produire un
     * {@code 400 VALIDATION_ERROR}, jamais un {@code 500 INTERNAL_ERROR}
     * (audit-report.md §3, finding F-SEC-1).
     */
    @Test
    void aMissingRequiredQueryParameterIsA400NotA500() {
        String admin = adminToken();
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.get(URI.create("/api/v1/planning/versions"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).build(),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().get("details").toString()).contains("classGroupPublicId");
    }

    // ------------------------------------------------------------------

    private ResponseEntity<Map<String, Object>> upload(String fileName, String csv, String token, String classId) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        parts.add("file", new HttpEntity<>(resource, csvPartHeaders()));
        parts.add("classGroupPublicId", classId);
        return restTemplate.exchange(URI.create("/api/v1/planning-imports"), HttpMethod.POST,
                new HttpEntity<>(parts, multipartHeaders(token)), new ParameterizedTypeReference<>() {
                });
    }

    private static HttpHeaders csvPartHeaders() {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(new MediaType("text", "csv"));
        return fileHeaders;
    }

    private static HttpHeaders multipartHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.get(URI.create(path)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).as("GET " + path).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private HttpStatus status(HttpMethod method, String path, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(method, URI.create(path));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return (HttpStatus) restTemplate.exchange(builder.build(),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getStatusCode();
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.post(URI.create(path)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).body(body),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private String classGroup(String admin) {
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
        return (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C1", "name", "Classe 1"), admin)
                .get("publicId");
    }

    private String teacherPublicId() {
        return account(RoleCode.TEACHER).publicId();
    }

    private record Account(String publicId, String email) {
    }

    private Account account(RoleCode... roles) {
        UserAccount user = new UserAccount("plan-" + UUID.randomUUID() + "@esic-connect.test",
                "Plan", "Tester", AccountStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user = userAccountRepository.saveAndFlush(user);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(user, role, Instant.now(), true));
        }
        return new Account(user.getPublicId().toString(), user.getEmail());
    }

    private String adminToken() {
        return tokenFor(RoleCode.ADMIN);
    }

    private String tokenFor(RoleCode... roles) {
        Account a = account(roles);
        Map<String, Object> body = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", a.email(), "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) body.get("accessToken");
    }

    private static String code() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
