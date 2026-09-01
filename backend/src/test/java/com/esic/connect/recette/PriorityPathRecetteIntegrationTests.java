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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recette G1-G — parcours produit prioritaire rejoué de bout en bout
 * (CDC §47.2) + extensions du grand lot G1, par appels HTTP réels sur les
 * <strong>API publiques</strong> :
 *
 * <pre>
 *   import apprenants CSV → confirmation → activation d'un apprenant
 *   RÉELLEMENT importé (jeton d'invitation → /account-invitations/activate)
 *   → import planning → simulation (AC-007) → publication → séances créées
 *   → ouverture par le formateur → CE MÊME apprenant émarge → rapport +
 *   export CSV → annulation → notification · remplacement de formateur ·
 *   justificatif + pièce jointe déposés par CE MÊME apprenant → acceptation
 *   → notification propriétaire · tableaux de bord par rôle.
 * </pre>
 *
 * <p>La chaîne est <strong>continue</strong> : l'apprenant qui émarge et
 * dépose le justificatif est celui créé et inscrit par l'import (aucun
 * compte apprenant parallèle). Le SQL direct ne sert qu'à observer des
 * invariants ({@code accountCount}, {@code notificationCount}…).
 *
 * <p>Les dates sont construites <strong>relativement à l'horloge</strong>
 * (aucune ne périme). Toutes les actions métier passent par l'API.
 *
 * <p><strong>Nature : recette d'intégration API Spring</strong> — ce
 * n'est <em>pas</em> un e2e navigateur. Playwright n'est pas ajouté
 * (DEC-G1-011 : dépendance et téléchargement de navigateur
 * disproportionnés ici) ; le e2e navigateur reste {@code NOT_IMPLEMENTED}.
 * Aucune démonstration manuelle n'est consignée
 * ({@code IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PriorityPathRecetteIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    private static final byte[] PDF = "%PDF-1.4\njustificatif fictif ESIC\n%%EOF".getBytes(StandardCharsets.UTF_8);

    /**
     * Enregistre les jetons d'activation émis (aucun SMTP réel), pour
     * activer un apprenant <strong>réellement issu de l'import</strong> via
     * l'API publique {@code POST /account-invitations/activate}.
     */
    @TestConfiguration
    static class RecordingMailerConfig {
        static final ConcurrentHashMap<String, String> TOKENS = new ConcurrentHashMap<>();

        @Bean
        @Primary
        InvitationMailer recordingMailer() {
            return (toEmail, firstName, rawToken, expiresAt) ->
                    TOKENS.put(toEmail.toLowerCase(java.util.Locale.ROOT), rawToken);
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
        RecordingMailerConfig.TOKENS.clear();
    }

    @Test
    void theEndToEndPriorityPathAndG1ExtensionsReplaySuccessfully() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String admin = tokenFor(account(RoleCode.ADMIN));
        Account teacher = account(RoleCode.TEACHER);
        Account substitute = account(RoleCode.TEACHER);

        // Dates construites relativement à l'horloge : rien ne devient
        // invalide avec le temps.
        LocalDate ayStart = LocalDate.now().withDayOfMonth(1).minusMonths(1);
        LocalDate ayEnd = ayStart.plusYears(1).minusDays(1);
        LocalDate sessionDate = LocalDate.now().plusDays(10);

        // --- 1. Référentiel académique ---------------------------------
        String site = created(admin, "/api/v1/sites", Map.of("code", "SITE-" + suffix,
                "name", "Campus", "timeZoneId", "Europe/Paris")).get("publicId").toString();
        String programCode = "PRG-" + suffix;
        String program = created(admin, "/api/v1/programs", Map.of("code", programCode,
                "name", "BTS SIO", "programType", "BTS")).get("publicId").toString();
        String level = created(admin, "/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1-" + suffix, "name", "BTS 1", "sequenceNumber", 1)).get("publicId").toString();
        String yearCode = "AY-" + suffix;
        String year = created(admin, "/api/v1/academic-years", Map.of("code", yearCode, "name", "Année " + suffix,
                "startDate", ayStart.toString(), "endDate", ayEnd.toString())).get("publicId").toString();
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

        // --- 2b. Activation d'un apprenant RÉELLEMENT issu de l'import ----
        // (parcours métier : jeton d'invitation -> POST /account-invitations/activate).
        // L'inscription en classe a déjà été créée par l'import : la suite du
        // scénario porte donc sur ce même apprenant, sans compte parallèle.
        String importedStudentEmail = "camille." + suffix + "@example.test";
        String activationToken = awaitActivationToken(importedStudentEmail);
        assertThat(exchange(HttpMethod.POST, "/api/v1/account-invitations/activate",
                Map.of("token", activationToken, "password", PASSWORD), null).getStatusCode())
                .as("activation de l'apprenant importé").isEqualTo(HttpStatus.NO_CONTENT);
        String studentToken = tokenFor(new Account(null, importedStudentEmail));

        // --- 3. Import du planning : simulation (AC-007) puis publication
        String planningCsv = "slot_key,session_date,start_time,end_time,time_zone_id,title,teacher_public_id,room_code\n"
                + "S-" + suffix + "-1," + sessionDate + ",09:00,12:00,Europe/Paris,Algorithmique," + teacher.publicId() + ",A101\n"
                + "S-" + suffix + "-2," + sessionDate + ",13:30,17:00,Europe/Paris,Bases de données," + teacher.publicId() + ",A101\n";
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

        Map<String, Object> algorithmiqueSession = teacherSessions.stream()
                .filter(s -> "Algorithmique".equals(s.get("title"))).findFirst().orElseThrow();
        Instant sessionStartsAt = Instant.parse(algorithmiqueSession.get("startsAt").toString());
        Instant sessionEndsAt = Instant.parse(algorithmiqueSession.get("endsAt").toString());

        assertThat(post(teacherToken, "/api/v1/sessions/" + sessionId + "/open").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        Map<String, Object> token = post(teacherToken, "/api/v1/sessions/" + sessionId + "/attendance-token").getBody();
        String shortCode = token.get("shortCode").toString();

        // --- 5. L'apprenant importé (activé, inscrit par l'import) émarge ---
        Map<String, Object> record = exchange(HttpMethod.POST, "/api/v1/attendance/validate",
                Map.of("shortCode", shortCode), studentToken).getBody();
        assertThat(record.get("status")).isIn("PRESENT", "LATE");

        // --- 6. Rapport + export CSV --------------------------------
        Map<String, Object> roster = getMap(admin, "/api/v1/sessions/" + sessionId + "/attendance");
        assertThat(((Number) roster.get("presentCount")).intValue()).isGreaterThanOrEqualTo(1);
        Map<String, Object> classesReport = getMap(admin,
                "/api/v1/attendance/reports/classes?classGroup=" + classPublicId);
        assertThat(((Number) classesReport.get("totalElements")).intValue()).isGreaterThanOrEqualTo(1);
        String exportFrom = Instant.now().minus(Duration.ofDays(1)).toString();
        String exportTo = Instant.now().plus(Duration.ofDays(60)).toString();
        ResponseEntity<byte[]> export = rest.exchange(RequestEntity.get(URI.create(
                        "/api/v1/attendance/reports/sessions/export?from=" + exportFrom + "&to=" + exportTo))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).build(), byte[].class);
        assertThat(export.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(export.getHeaders().getContentType().toString()).startsWith("text/csv");

        // --- 7a. Annulation d'une séance → notification du formateur --
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + sessionId2 + "/cancel",
                Map.of("reason", "Salle indisponible"), admin).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(notificationCount(teacher.email(), "SESSION_CANCELLED")).isEqualTo(1L);

        // --- 7b. Remplacement de formateur --------------------------
        // Période dérivée des instants réels de la séance (chevauchement +
        // marge ≤ 60 min — G1-C.3), sans arithmétique de fuseau / DST.
        Instant from = sessionStartsAt.minus(Duration.ofMinutes(30));
        Instant until = sessionEndsAt.plus(Duration.ofMinutes(30));
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + sessionId + "/substitutions",
                Map.of("substituteTeacherPublicId", substitute.publicId(), "validFrom", from.toString(),
                        "validUntil", until.toString(), "reason", "Formateur souffrant"), admin)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // --- 7c. Justificatif + pièce jointe → acceptation → notif ---
        // Séance exceptionnelle dédiée (l'apprenant y est absent).
        Instant absentStart = Instant.now().plus(Duration.ofDays(11));
        Map<String, Object> absentSession = created(admin, "/api/v1/sessions", Map.of(
                "teacherPublicId", teacher.publicId(),
                "classPublicIds", List.of(classPublicId),
                "startsAt", absentStart.toString(),
                "endsAt", absentStart.plus(Duration.ofHours(4)).toString(),
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
        assertThat(notificationCount(importedStudentEmail, "JUSTIFICATION_ACCEPTED")).isEqualTo(1L);
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

    /**
     * Jeton d'activation capté par le mailer de test (émis
     * {@code AFTER_COMMIT} de la confirmation d'import). Court poll
     * défensif : le listener est synchrone, mais on tolère un léger délai.
     */
    private String awaitActivationToken(String email) {
        String key = email.toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < 40; i++) {
            String token = RecordingMailerConfig.TOKENS.get(key);
            if (token != null) {
                return token;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Aucun jeton d'activation capté pour " + email);
    }

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
