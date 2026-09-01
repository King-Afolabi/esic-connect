package com.esic.connect.recette;

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
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Recette G1-G — parcours produit prioritaire rejoué de bout en bout
 * (CDC §47.2) + extensions du grand lot G1, par appels HTTP réels :
 *
 * <pre>
 *   import apprenants → import planning → simulation (AC-007) →
 *   publication → séances créées → ouverture par le formateur →
 *   émargement → rapport + export CSV
 *   puis : annulation → notification · remplacement · justificatif +
 *   pièce jointe → acceptation → notification propriétaire · dashboards.
 * </pre>
 *
 * <p><strong>Repli e2e (DEC-G1-011).</strong> Playwright n'est pas ajouté
 * (dépendance et téléchargement de navigateur disproportionnés dans cet
 * environnement) : cette classe est le <em>repli API automatisé</em>. Le
 * e2e <em>navigateur</em> reste `PARTIAL` — non livré.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PriorityPathRecetteIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    private static final byte[] PDF = "%PDF-1.4\njustificatif fictif ESIC\n%%EOF".getBytes(StandardCharsets.UTF_8);

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
    private JdbcTemplate jdbc;

    @BeforeEach
    void jdkClient() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void theEndToEndPriorityPathAndG1ExtensionsReplaySuccessfully() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String admin = tokenFor(account(RoleCode.ADMIN));
        Account teacher = account(RoleCode.TEACHER);
        Account substitute = account(RoleCode.TEACHER);

        // --- 1. Référentiel académique ---------------------------------
        String site = created(admin, "/api/v1/sites", Map.of("code", "SITE-" + suffix,
                "name", "Campus", "timeZoneId", "Europe/Paris")).get("publicId").toString();
        String programCode = "PRG-" + suffix;
        String program = created(admin, "/api/v1/programs", Map.of("code", programCode,
                "name", "BTS SIO", "programType", "BTS")).get("publicId").toString();
        String level = created(admin, "/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1-" + suffix, "name", "BTS 1", "sequenceNumber", 1)).get("publicId").toString();
        String yearCode = "AY-" + suffix;
        String year = created(admin, "/api/v1/academic-years", Map.of("code", yearCode, "name", "2026-2027",
                "startDate", "2026-09-01", "endDate", "2027-08-31")).get("publicId").toString();
        String promo = created(admin, "/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P-" + suffix, "name", "Promotion")).get("publicId").toString();
        String classCode = "C-" + suffix;
        String classPublicId = created(admin, "/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", classCode, "name", "Classe 1"))
                .get("publicId").toString();

        // --- 2. Import des apprenants (CSV → simulation → confirmation) -
        String studentCsv = "last_name,first_name,email,formation_code,class_code,academic_year\n"
                + "Bernard,Camille,camille." + suffix + "@example.test," + programCode + "," + classCode + "," + yearCode + "\n"
                + "Nguyen,Alex,alex." + suffix + "@example.test," + programCode + "," + classCode + "," + yearCode + "\n"
                + "Diop,Mathis,mathis." + suffix + "@example.test," + programCode + "," + classCode + "," + yearCode + "\n";
        Map<String, Object> importJob = multipartCsv(admin, "/api/v1/student-imports",
                "apprenants-" + suffix + ".csv", studentCsv).getBody();
        String importId = importJob.get("publicId").toString();
        assertThat(importJob.get("status")).isEqualTo("SIMULATED");
        assertThat(importJob.get("confirmable")).isEqualTo(Boolean.TRUE);
        // Aucun compte avant confirmation.
        assertThat(accountCount("%" + suffix + "@example.test")).isZero();
        Map<String, Object> applied = post(admin, "/api/v1/student-imports/" + importId + "/confirm").getBody();
        assertThat(((Number) applied.get("created")).intValue()).isEqualTo(3);
        assertThat(applied.get("alreadyApplied")).isEqualTo(Boolean.FALSE);
        assertThat(accountCount("%" + suffix + "@example.test")).isEqualTo(3);

        // --- 3. Import du planning : simulation (AC-007) puis publication
        String planningCsv = "slot_key,session_date,start_time,end_time,time_zone_id,title,teacher_public_id,room_code\n"
                + "S-" + suffix + "-1,2026-09-14,09:00,12:00,Europe/Paris,Algorithmique," + teacher.publicId() + ",A101\n"
                + "S-" + suffix + "-2,2026-09-14,13:30,17:00,Europe/Paris,Bases de données," + teacher.publicId() + ",A101\n";
        Map<String, Object> planJob = multipartCsvWithClass(admin, "apprenants-plan-" + suffix + ".csv",
                planningCsv, classPublicId).getBody();
        String planId = planJob.get("publicId").toString();
        // AC-007 : la simulation ne crée AUCUNE séance.
        assertThat(planningSessionCount(classPublicId)).isZero();
        Map<String, Object> publication = post(admin, "/api/v1/planning-imports/" + planId + "/publish").getBody();
        assertThat(((Number) publication.get("versionNumber")).intValue()).isEqualTo(1);
        assertThat(planningSessionCount(classPublicId)).isEqualTo(2);

        // --- 4. Le formateur consulte, ouvre, émet un jeton -----------
        String teacherToken = tokenFor(teacher);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> teacherSessions =
                (List<Map<String, Object>>) getMap(teacherToken, "/api/v1/sessions").get("content");
        assertThat(teacherSessions).extracting(s -> s.get("title"))
                .contains("Algorithmique", "Bases de données");
        String sessionId = teacherSessions.stream()
                .filter(s -> "Algorithmique".equals(s.get("title")))
                .map(s -> s.get("publicId").toString()).findFirst().orElseThrow();
        String sessionId2 = teacherSessions.stream()
                .filter(s -> "Bases de données".equals(s.get("title")))
                .map(s -> s.get("publicId").toString()).findFirst().orElseThrow();

        assertThat(post(teacherToken, "/api/v1/sessions/" + sessionId + "/open").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        Map<String, Object> token = post(teacherToken, "/api/v1/sessions/" + sessionId + "/attendance-token").getBody();
        String shortCode = token.get("shortCode").toString();

        // --- 5. Un apprenant actif inscrit émarge --------------------
        Account activeStudent = account(RoleCode.STUDENT);
        String profile = created(admin, "/api/v1/student-profiles", Map.of("userPublicId", activeStudent.publicId(),
                "studentNumber", "ESIC-2026-" + suffix)).get("publicId").toString();
        created(admin, "/api/v1/enrollments", Map.of("studentProfilePublicId", profile,
                "classGroupPublicId", classPublicId, "startDate", "2026-08-01"));
        String studentToken = tokenFor(activeStudent);
        Map<String, Object> record = exchange(HttpMethod.POST, "/api/v1/attendance/validate",
                Map.of("shortCode", shortCode), studentToken).getBody();
        assertThat(record.get("status")).isIn("PRESENT", "LATE");

        // --- 6. Rapport + export CSV --------------------------------
        Map<String, Object> roster = getMap(admin, "/api/v1/sessions/" + sessionId + "/attendance");
        assertThat(((Number) roster.get("presentCount")).intValue()).isGreaterThanOrEqualTo(1);
        Map<String, Object> classesReport = getMap(admin,
                "/api/v1/attendance/reports/classes?classGroup=" + classPublicId);
        assertThat(((Number) classesReport.get("totalElements")).intValue()).isGreaterThanOrEqualTo(1);
        ResponseEntity<byte[]> export = rest.exchange(RequestEntity.get(URI.create(
                        "/api/v1/attendance/reports/sessions/export?from=2026-09-01T00:00:00Z&to=2026-09-30T00:00:00Z"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).build(), byte[].class);
        assertThat(export.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(export.getHeaders().getContentType().toString()).startsWith("text/csv");

        // --- 7a. Annulation d'une séance → notification du formateur --
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + sessionId2 + "/cancel",
                Map.of("reason", "Salle indisponible"), admin).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(notificationCount(teacher.email(), "SESSION_CANCELLED")).isEqualTo(1L);

        // --- 7b. Remplacement de formateur --------------------------
        // Séance « Algorithmique » : 2026-09-14 09:00–12:00 Europe/Paris = 07:00–10:00 UTC.
        // La période du remplacement doit chevaucher la séance (marge ± 60 min).
        Instant from = Instant.parse("2026-09-14T07:00:00Z");
        Instant until = Instant.parse("2026-09-14T10:30:00Z");
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + sessionId + "/substitutions",
                Map.of("substituteTeacherPublicId", substitute.publicId(), "validFrom", from.toString(),
                        "validUntil", until.toString(), "reason", "Formateur souffrant"), admin)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // --- 7c. Justificatif + pièce jointe → acceptation → notif ---
        // Séance exceptionnelle dédiée (l'apprenant y est absent).
        Map<String, Object> absentSession = created(admin, "/api/v1/sessions", Map.of(
                "teacherPublicId", teacher.publicId(),
                "classPublicIds", List.of(classPublicId),
                "startsAt", "2026-09-15T08:00:00Z",
                "endsAt", "2026-09-15T12:00:00Z",
                "timeZoneId", "Europe/Paris",
                "reason", "séance exceptionnelle recette"));
        String absentSessionId = absentSession.get("publicId").toString();
        post(admin, "/api/v1/sessions/" + absentSessionId + "/open");
        String checkpointId = created(admin, "/api/v1/sessions/" + absentSessionId + "/checkpoints",
                Map.of("label", "Matin", "type", "CUSTOM")).get("publicId").toString();
        post(admin, "/api/v1/sessions/" + absentSessionId + "/checkpoints/" + checkpointId + "/open");
        ResponseEntity<Map<String, Object>> submitResp = exchange(HttpMethod.POST,
                "/api/v1/me/attendance/justifications",
                Map.of("checkpointPublicId", checkpointId, "category", "MEDICAL", "comment", "certificat"),
                studentToken);
        assertThat(submitResp.getStatusCode()).as("dépôt justificatif -> " + submitResp.getBody())
                .isEqualTo(HttpStatus.CREATED);
        String justifId = submitResp.getBody().get("publicId").toString();
        assertThat(multipartFile(studentToken,
                "/api/v1/me/attendance/justifications/" + justifId + "/attachment", "c.pdf", PDF)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(exchange(HttpMethod.POST, "/api/v1/attendance/justifications/" + justifId + "/review",
                Map.of("decision", "ACCEPTED"), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(notificationCount(activeStudent.email(), "JUSTIFICATION_ACCEPTED")).isEqualTo(1L);
        // L'examinateur télécharge la pièce.
        ResponseEntity<byte[]> dl = rest.exchange(RequestEntity.get(URI.create(
                        "/api/v1/attendance/justifications/" + justifId + "/attachment/download"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).build(), byte[].class);
        assertThat(dl.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dl.getBody()).isEqualTo(PDF);
        assertThat(dl.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;");

        // --- 8. Tableaux de bord par rôle --------------------------
        Map<String, Object> adminDash = getMap(admin, "/api/v1/me/dashboard");
        assertThat(adminDash.get("role")).isEqualTo("ADMINISTRATION");
        @SuppressWarnings("unchecked")
        Map<String, Object> adminCard = (Map<String, Object>) adminDash.get("administration");
        assertThat(((Number) adminCard.get("activeAccounts")).longValue()).isPositive();

        Map<String, Object> teacherDash = getMap(teacherToken, "/api/v1/me/dashboard");
        assertThat(teacherDash.get("role")).isEqualTo("TEACHER");

        Map<String, Object> studentDash = getMap(studentToken, "/api/v1/me/dashboard");
        assertThat(studentDash.get("role")).isEqualTo("STUDENT");
        assertThat(studentDash.get("student")).isNotNull();
    }

    // ================================================================
    // Helpers
    // ================================================================

    private long accountCount(String emailLike) {
        Long n = jdbc.queryForObject("select count(*) from user_account where email like ?", Long.class, emailLike);
        return n == null ? 0 : n;
    }

    private long planningSessionCount(String classPublicId) {
        Long n = jdbc.queryForObject(
                "select count(*) from course_session cs "
                        + "join session_class sc on sc.course_session_id = cs.id "
                        + "join class_group cg on cg.id = sc.class_group_id "
                        + "where cg.public_id = UUID_TO_BIN(?) and cs.planning_slot_public_id is not null "
                        + "and cs.superseded_by_scheduling = false and cs.status <> 'CANCELLED'",
                Long.class, classPublicId);
        return n == null ? 0 : n;
    }

    private long notificationCount(String recipientEmail, String type) {
        Long n = jdbc.queryForObject(
                "select count(*) from notification n join user_account u on u.id = n.recipient_user_id "
                        + "where u.email = ? and n.type = ?",
                Long.class, recipientEmail, type);
        return n == null ? 0 : n;
    }

    private ResponseEntity<Map<String, Object>> multipartCsv(String token, String path, String fileName, String csv) {
        MultiValueMap<String, Object> parts = csvPart(fileName, csv);
        return sendMultipart(token, URI.create(path), parts);
    }

    private ResponseEntity<Map<String, Object>> multipartCsvWithClass(String token, String fileName, String csv,
                                                                     String classPublicId) {
        MultiValueMap<String, Object> parts = csvPart(fileName, csv);
        return sendMultipart(token, URI.create("/api/v1/planning-imports?classGroupPublicId=" + classPublicId), parts);
    }

    private ResponseEntity<Map<String, Object>> multipartFile(String token, String path, String fileName,
                                                              byte[] bytes) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        HttpHeaders ph = new HttpHeaders();
        ph.setContentType(MediaType.APPLICATION_PDF);
        parts.add("file", new HttpEntity<>(named(bytes, fileName), ph));
        return sendMultipart(token, URI.create(path), parts);
    }

    private MultiValueMap<String, Object> csvPart(String fileName, String csv) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        HttpHeaders ph = new HttpHeaders();
        ph.setContentType(new MediaType("text", "csv"));
        parts.add("file", new HttpEntity<>(named(csv.getBytes(StandardCharsets.UTF_8), fileName), ph));
        return parts;
    }

    private ResponseEntity<Map<String, Object>> sendMultipart(String token, URI uri,
                                                              MultiValueMap<String, Object> parts) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        ResponseEntity<Map<String, Object>> r = rest.exchange(uri, HttpMethod.POST,
                new HttpEntity<>(parts, headers), new ParameterizedTypeReference<>() {
                });
        assertThat(r.getStatusCode().is2xxSuccessful()).as("multipart " + uri + " -> " + r.getBody()).isTrue();
        return r;
    }

    private static ByteArrayResource named(byte[] bytes, String fileName) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    private Map<String, Object> created(String token, String path, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> r = exchange(HttpMethod.POST, path, body, token);
        assertThat(r.getStatusCode()).as("POST " + path + " -> " + r.getBody()).isEqualTo(HttpStatus.CREATED);
        return r.getBody();
    }

    private ResponseEntity<Map<String, Object>> post(String token, String path) {
        return exchange(HttpMethod.POST, path, null, token);
    }

    private Map<String, Object> getMap(String token, String path) {
        ResponseEntity<Map<String, Object>> r = rest.exchange(RequestEntity.get(URI.create(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(r.getStatusCode()).as("GET " + path + " -> " + r.getBody()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private ResponseEntity<Map<String, Object>> exchange(HttpMethod method, String path, Object body, String token) {
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
        UserAccount a = new UserAccount("rec-" + UUID.randomUUID() + "@esic-connect.test",
                "Rec", "Tester", AccountStatus.ACTIVE);
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
}
