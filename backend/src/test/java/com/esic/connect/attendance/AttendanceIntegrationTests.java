package com.esic.connect.attendance;

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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parcours d'émargement de bout en bout : création + ouverture d'une
 * séance, émission d'un jeton + code court (Redis), validation d'une
 * présence par un apprenant inscrit (code court puis jeton opaque),
 * anti-double présence (contrainte SQL), refus d'un non-inscrit, refus
 * après fermeture, rotation du jeton, concurrence, et consultation des
 * présences côté formateur.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AttendanceIntegrationTests {

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
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void useJdkClient() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void shortCodeWorkflowFromIssueToCloseIsAuditedAndAntiDouble() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 2);

        Map<String, Object> issued = post("/api/v1/sessions/" + fx.sessionId() + "/attendance-token",
                null, admin, HttpStatus.OK);
        String shortCode = (String) issued.get("shortCode");
        assertThat(issued.get("token")).isNotNull();
        assertThat(issued.get("expiresAt")).isNotNull();
        assertThat(((Number) issued.get("ttlSeconds")).longValue()).isPositive();

        // L'apprenant 1 émarge avec le code court -> 200
        String student1 = tokenFor(fx.students().get(0));
        Map<String, Object> record = post("/api/v1/attendance/validate",
                Map.of("shortCode", shortCode), student1, HttpStatus.OK);
        assertThat(record.get("sessionPublicId")).isEqualTo(fx.sessionId());
        assertThat(record.get("source")).isEqualTo("SHORT_CODE");
        assertThat(record.get("recordedAt")).isNotNull();
        assertThat(record).doesNotContainKeys("id", "token", "shortCode");
        assertThat(auditActions((String) record.get("attendancePublicId"))).contains("ATTENDANCE_RECORDED");

        // Deuxième émargement du même apprenant -> 409
        ResponseEntity<Map<String, Object>> again = exchange(HttpMethod.POST, "/api/v1/attendance/validate",
                Map.of("shortCode", shortCode), student1);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().get("code")).isEqualTo("ATT_ALREADY_RECORDED");

        // Consultation des présences (formateur / admin)
        Map<String, Object> roster = getMap("/api/v1/sessions/" + fx.sessionId() + "/attendance", admin);
        assertThat(((Number) roster.get("presentCount")).intValue()).isEqualTo(1);
        assertThat(((Number) roster.get("expectedCount")).longValue()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) roster.get("records");
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("studentNumber")).isNotNull();
        assertThat(row.get("source")).isEqualTo("SHORT_CODE");
        assertThat(row).doesNotContainKeys("email", "id");

        // Fermeture -> tout émargement ultérieur refusé
        post("/api/v1/sessions/" + fx.sessionId() + "/close", null, admin, HttpStatus.NO_CONTENT);
        String student2 = tokenFor(fx.students().get(1));
        ResponseEntity<Map<String, Object>> afterClose = exchange(HttpMethod.POST, "/api/v1/attendance/validate",
                Map.of("shortCode", shortCode), student2);
        assertThat(afterClose.getStatusCode()).isIn(HttpStatus.CONFLICT);
        assertThat(afterClose.getBody().get("code")).isIn("ATT_SESSION_CLOSED", "ATT_TOKEN_INVALID");
    }

    @Test
    void opaqueTokenAlsoValidatesAndIsMarkedAsDynamicQr() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        Map<String, Object> issued = post("/api/v1/sessions/" + fx.sessionId() + "/attendance-token",
                null, admin, HttpStatus.OK);
        String token = (String) issued.get("token");

        Map<String, Object> record = post("/api/v1/attendance/validate",
                Map.of("token", token), tokenFor(fx.students().get(0)), HttpStatus.OK);
        assertThat(record.get("source")).isEqualTo("DYNAMIC_QR");
    }

    @Test
    void nonEnrolledStudentIsRejected() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        Map<String, Object> issued = post("/api/v1/sessions/" + fx.sessionId() + "/attendance-token",
                null, admin, HttpStatus.OK);

        // Apprenant avec un profil mais aucune inscription dans une classe de la séance.
        Account outsider = accountWithRoles(RoleCode.STUDENT);
        createProfile(admin, outsider.publicId());
        ResponseEntity<Map<String, Object>> denied = exchange(HttpMethod.POST, "/api/v1/attendance/validate",
                Map.of("shortCode", issued.get("shortCode")), tokenFor(outsider));
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(denied.getBody().get("code")).isEqualTo("ATT_NOT_ENROLLED");
    }

    @Test
    void malformedAndInvalidSubmissionsAreRejected() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        post("/api/v1/sessions/" + fx.sessionId() + "/attendance-token", null, admin, HttpStatus.OK);
        String student = tokenFor(fx.students().get(0));

        // Ni jeton ni code court -> 400
        ResponseEntity<Map<String, Object>> neither = exchange(HttpMethod.POST, "/api/v1/attendance/validate",
                Map.of(), student);
        assertThat(neither.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(neither.getBody().get("code")).isEqualTo("ATT_INVALID_SUBMISSION");

        // Les deux à la fois -> 400
        ResponseEntity<Map<String, Object>> both = exchange(HttpMethod.POST, "/api/v1/attendance/validate",
                Map.of("token", "abc", "shortCode", "ABCD2345"), student);
        assertThat(both.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(both.getBody().get("code")).isEqualTo("ATT_INVALID_SUBMISSION");

        // Code court inconnu -> 409 ATT_TOKEN_INVALID (la valeur soumise n'est jamais renvoyée)
        ResponseEntity<Map<String, Object>> unknown = exchange(HttpMethod.POST, "/api/v1/attendance/validate",
                Map.of("shortCode", "ZZZZ9999"), student);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(unknown.getBody().get("code")).isEqualTo("ATT_TOKEN_INVALID");
        assertThat(String.valueOf(unknown.getBody().get("message"))).doesNotContain("ZZZZ9999");
    }

    @Test
    void tokenRotationInvalidatesThePreviousCode() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 2);
        Map<String, Object> first = post("/api/v1/sessions/" + fx.sessionId() + "/attendance-token",
                null, admin, HttpStatus.OK);
        Map<String, Object> second = post("/api/v1/sessions/" + fx.sessionId() + "/attendance-token",
                null, admin, HttpStatus.OK);
        assertThat(first.get("shortCode")).isNotEqualTo(second.get("shortCode"));

        // L'ancien code court n'est plus accepté.
        ResponseEntity<Map<String, Object>> old = exchange(HttpMethod.POST, "/api/v1/attendance/validate",
                Map.of("shortCode", first.get("shortCode")), tokenFor(fx.students().get(0)));
        assertThat(old.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(old.getBody().get("code")).isEqualTo("ATT_TOKEN_INVALID");

        // Le nouveau est valide.
        post("/api/v1/attendance/validate", Map.of("shortCode", second.get("shortCode")),
                tokenFor(fx.students().get(1)), HttpStatus.OK);
    }

    @Test
    void plannedSessionCannotIssueAToken() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String sessionId = (String) created("/api/v1/sessions", sessionBody(teacher.publicId(),
                List.of(chain.classA())), admin).get("publicId");

        ResponseEntity<Map<String, Object>> denied = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + sessionId + "/attendance-token", null, admin);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(denied.getBody().get("code")).isEqualTo("ATT_SESSION_CLOSED");
    }

    @Test
    void twoConcurrentValidationsYieldExactlyOne200AndOne409() throws Exception {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        Map<String, Object> issued = post("/api/v1/sessions/" + fx.sessionId() + "/attendance-token",
                null, admin, HttpStatus.OK);
        String student = tokenFor(fx.students().get(0));
        Map<String, Object> body = Map.of("shortCode", issued.get("shortCode"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<HttpStatus> call = () -> (HttpStatus) exchange(HttpMethod.POST,
                    "/api/v1/attendance/validate", body, student).getStatusCode();
            List<Future<HttpStatus>> results = pool.invokeAll(List.of(call, call));
            List<HttpStatus> statuses = List.of(get(results.get(0)), get(results.get(1)));
            assertThat(statuses).filteredOn(HttpStatus.OK::equals).hasSize(1);
            assertThat(statuses).filteredOn(HttpStatus.CONFLICT::equals).hasSize(1);
            assertThat(statuses).noneMatch(HttpStatus::is5xxServerError);
        } finally {
            pool.shutdownNow();
        }
        assertThat(((Number) getMap("/api/v1/sessions/" + fx.sessionId() + "/attendance", admin)
                .get("presentCount")).intValue()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void secondCheckpointHasItsOwnTokenAndAttendanceBreakdown() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 2);

        // Point de contrôle CUSTOM ouvert en plus du START.
        Map<String, Object> custom = post("/api/v1/sessions/" + fx.sessionId() + "/checkpoints",
                Map.of("label", "Retour de pause", "type", "CUSTOM"), admin, HttpStatus.CREATED);
        String customId = (String) custom.get("publicId");
        post("/api/v1/sessions/" + fx.sessionId() + "/checkpoints/" + customId + "/open", null, admin,
                HttpStatus.NO_CONTENT);

        // Jeton émis explicitement pour ce point de contrôle.
        Map<String, Object> issued = post(
                "/api/v1/sessions/" + fx.sessionId() + "/checkpoints/" + customId + "/attendance-token",
                null, admin, HttpStatus.OK);
        assertThat(issued.get("checkpointPublicId")).isEqualTo(customId);

        Map<String, Object> record = post("/api/v1/attendance/validate",
                Map.of("shortCode", issued.get("shortCode")), tokenFor(fx.students().get(0)), HttpStatus.OK);
        assertThat(record.get("checkpointPublicId")).isEqualTo(customId);
        assertThat(record.get("status")).isEqualTo("PRESENT");

        Map<String, Object> roster = getMap("/api/v1/sessions/" + fx.sessionId() + "/attendance", admin);
        List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) roster.get("checkpoints");
        assertThat(checkpoints).hasSize(2);
        Map<String, Object> customBlock = checkpoints.stream()
                .filter(cp -> customId.equals(cp.get("checkpointPublicId"))).findFirst().orElseThrow();
        assertThat(((Number) customBlock.get("presentCount")).intValue()).isEqualTo(1);
        assertThat(((Number) customBlock.get("derivedAbsentCount")).intValue()).isEqualTo(1);
    }

    @Test
    void lateArrivalIsClassifiedAsLate() {
        String admin = adminToken();
        // Séance dont l'heure de début est largement dépassée : émargement
        // au-delà du seuil app.attendance.late-threshold -> LATE.
        Fixture fx = openSessionWithEnrolledStudents(admin, 1,
                "2026-08-01T08:00:00Z", "2026-08-01T12:00:00Z");
        Map<String, Object> issued = post("/api/v1/sessions/" + fx.sessionId() + "/attendance-token",
                null, admin, HttpStatus.OK);
        Map<String, Object> record = post("/api/v1/attendance/validate",
                Map.of("shortCode", issued.get("shortCode")), tokenFor(fx.students().get(0)), HttpStatus.OK);
        assertThat(record.get("status")).isEqualTo("LATE");
        assertThat(((Number) record.get("lateMinutes")).intValue()).isPositive();
    }

    // ------------------------------------------------------------------
    // Présence manuelle / correction / annulation / historique (V10)
    // ------------------------------------------------------------------

    @Test
    void manualRecordCorrectionAndCancellationAreAuditedWithAppendOnlyHistory() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);

        Map<String, Object> manual = post("/api/v1/sessions/" + fx.sessionId() + "/attendance/manual",
                Map.of("enrollmentPublicId", fx.enrollments().get(0),
                        "checkpointPublicId", firstCheckpoint(admin, fx.sessionId()),
                        "status", "ABSENT", "comment", "absent constaté en début de cours"),
                admin, HttpStatus.CREATED);
        String attendanceId = (String) manual.get("attendancePublicId");
        assertThat(manual.get("status")).isEqualTo("ABSENT");
        assertThat(manual.get("source")).isEqualTo("MANUAL");
        assertThat(auditActions(attendanceId)).contains("ATTENDANCE_MANUAL_RECORDED");

        Map<String, Object> corrected = post(
                "/api/v1/sessions/" + fx.sessionId() + "/attendance/" + attendanceId + "/correct",
                Map.of("status", "PRESENT", "reason", "arrivé en fait, pointage oublié"), admin, HttpStatus.OK);
        assertThat(corrected.get("status")).isEqualTo("PRESENT");

        post("/api/v1/sessions/" + fx.sessionId() + "/attendance/" + attendanceId + "/cancel",
                Map.of("reason", "doublon"), admin, HttpStatus.OK);

        List<Map<String, Object>> history = listRaw(
                "/api/v1/sessions/" + fx.sessionId() + "/attendance/" + attendanceId + "/history", admin);
        assertThat(history).extracting(h -> h.get("action"))
                .containsExactly("CREATED_MANUALLY", "STATUS_CORRECTED", "CANCELLED");
        assertThat(auditActions(attendanceId)).contains("ATTENDANCE_CORRECTED", "ATTENDANCE_CANCELLED");

        // Re-corriger une présence annulée -> 409.
        ResponseEntity<Map<String, Object>> onCancelled = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + fx.sessionId() + "/attendance/" + attendanceId + "/correct",
                Map.of("status", "PRESENT", "reason", "x"), admin);
        assertThat(onCancelled.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(onCancelled.getBody().get("code")).isEqualTo("ATT_RECORD_INVALID_STATE");
    }

    @Test
    void manualRecordRejectsMissingCommentAndForeignEnrollment() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        String cp = firstCheckpoint(admin, fx.sessionId());

        // Commentaire manquant -> 400 (validation @NotBlank).
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + fx.sessionId() + "/attendance/manual",
                Map.of("enrollmentPublicId", fx.enrollments().get(0), "checkpointPublicId", cp,
                        "status", "ABSENT"), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Statut EXCUSED_ABSENCE non saisissable directement -> 400.
        ResponseEntity<Map<String, Object>> excused = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + fx.sessionId() + "/attendance/manual",
                Map.of("enrollmentPublicId", fx.enrollments().get(0), "checkpointPublicId", cp,
                        "status", "EXCUSED_ABSENCE", "comment", "x"), admin);
        assertThat(excused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Inscription d'une autre séance / classe -> 409 ATT_NOT_ENROLLED.
        Fixture other = openSessionWithEnrolledStudents(admin, 1);
        ResponseEntity<Map<String, Object>> foreign = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + fx.sessionId() + "/attendance/manual",
                Map.of("enrollmentPublicId", other.enrollments().get(0), "checkpointPublicId", cp,
                        "status", "ABSENT", "comment", "x"), admin);
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(foreign.getBody().get("code")).isEqualTo("ATT_NOT_ENROLLED");
    }

    // ------------------------------------------------------------------
    // Candidats à la présence manuelle (correctif PR #22 §2)
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void manualAttendanceCandidatesListEnrolledStudentsOfTheSessionOnly() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 2);
        // Une autre séance / classe : ses apprenants ne doivent jamais apparaître.
        Fixture other = openSessionWithEnrolledStudents(admin, 1);

        List<Map<String, Object>> candidates = listRaw(
                "/api/v1/sessions/" + fx.sessionId() + "/attendance/candidates", admin);
        assertThat(candidates).hasSize(2);
        assertThat(candidates).allSatisfy(c -> {
            assertThat(c).doesNotContainKeys("email", "id", "userId", "studentUserId");
            assertThat(c.get("enrollmentPublicId")).isNotNull();
            assertThat(c.get("classCode")).isNotNull();
        });
        List<Object> enrollmentIds = candidates.stream().map(c -> c.get("enrollmentPublicId")).toList();
        assertThat(enrollmentIds).containsExactlyInAnyOrderElementsOf(fx.enrollments());
        assertThat(enrollmentIds).doesNotContainAnyElementsOf(other.enrollments());

        // L'identifiant renvoyé est directement utilisable pour une saisie manuelle.
        String cp = firstCheckpoint(admin, fx.sessionId());
        post("/api/v1/sessions/" + fx.sessionId() + "/attendance/manual",
                Map.of("enrollmentPublicId", enrollmentIds.get(0), "checkpointPublicId", cp,
                        "status", "ABSENT", "comment", "absent au pointage"),
                admin, HttpStatus.CREATED);

        // STUDENT et anonyme : refusés.
        assertThat(exchange(HttpMethod.GET,
                "/api/v1/sessions/" + fx.sessionId() + "/attendance/candidates", null,
                tokenFor(fx.students().get(0))).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(HttpMethod.GET,
                "/api/v1/sessions/" + fx.sessionId() + "/attendance/candidates", null, null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void manualAttendanceCandidatesRespectTeacherSessionScope() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        String path = "/api/v1/sessions/" + fx.sessionId() + "/attendance/candidates";
        // Le formateur affecté à la séance y accède ; un autre formateur non.
        assertThat(rawStatus(path, tokenFor(fx.teacher()))).isEqualTo(HttpStatus.OK);
        assertThat(rawStatus(path, roleToken(RoleCode.TEACHER))).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private HttpStatus rawStatus(String path, String token) {
        RequestEntity.HeadersBuilder<?> builder = RequestEntity.get(URI.create(path));
        if (token != null) {
            builder = builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return (HttpStatus) restTemplate.exchange(builder.build(), String.class).getStatusCode();
    }

    /**
     * §2 — l'éligibilité des candidats (et la validation d'une saisie
     * manuelle) dépend de la <strong>date de la séance</strong>, pas du
     * seul état actif courant : une inscription qui débute après la séance
     * ou qui s'est terminée avant est exclue de la liste et refusée à la
     * saisie ; un apprenant d'une classe extérieure n'apparaît jamais.
     */
    @Test
    @SuppressWarnings("unchecked")
    void candidateEligibilityDependsOnTheSessionDateNotJustCurrentActiveState() {
        String admin = adminToken();
        // Séance au 2026-09-10 (fixture par défaut). L'apprenant de base a
        // une inscription ouverte débutant le 2026-08-01 -> valable ce jour.
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        String validEnrollment = fx.enrollments().get(0);
        String cp = firstCheckpoint(admin, fx.sessionId());

        // Inscription débutant APRÈS la séance : active, mais pas encore en vigueur le 10/09.
        String futureEnrollment = enrollExtraStudentInFixtureClass(admin, fx, "2026-10-01");
        // Inscription TERMINÉE avant la séance : forcée ACTIVE + end_date passée
        // (impossible via l'API, qui clôt aussi le statut ; on isole ainsi la
        // branche "date" de la branche "statut").
        String endedEnrollment = enrollExtraStudentInFixtureClass(admin, fx, "2026-08-15");
        assertThat(jdbcTemplate.update(
                "UPDATE enrollment SET end_date = ? WHERE public_id = UUID_TO_BIN(?)",
                "2026-09-01", endedEnrollment)).isEqualTo(1);

        // Un apprenant actif d'une classe extérieure (autre séance) : jamais retourné.
        Fixture outside = openSessionWithEnrolledStudents(admin, 1);

        List<Map<String, Object>> candidates = listRaw(
                "/api/v1/sessions/" + fx.sessionId() + "/attendance/candidates", admin);
        List<Object> ids = candidates.stream().map(c -> c.get("enrollmentPublicId")).toList();
        assertThat(ids).containsExactly(validEnrollment);
        assertThat(ids).doesNotContain(futureEnrollment, endedEnrollment);
        assertThat(ids).doesNotContainAnyElementsOf(outside.enrollments());

        // Saisie manuelle : acceptée pour l'inscription valable ce jour-là.
        post("/api/v1/sessions/" + fx.sessionId() + "/attendance/manual",
                Map.of("enrollmentPublicId", validEnrollment, "checkpointPublicId", cp,
                        "status", "ABSENT", "comment", "absent au pointage"),
                admin, HttpStatus.CREATED);

        // Refusée (409 ATT_NOT_ENROLLED) pour l'inscription débutant après la séance.
        ResponseEntity<Map<String, Object>> future = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + fx.sessionId() + "/attendance/manual",
                Map.of("enrollmentPublicId", futureEnrollment, "checkpointPublicId", cp,
                        "status", "ABSENT", "comment", "x"), admin);
        assertThat(future.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(future.getBody().get("code")).isEqualTo("ATT_NOT_ENROLLED");

        // Refusée pour l'inscription terminée avant la séance.
        ResponseEntity<Map<String, Object>> ended = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + fx.sessionId() + "/attendance/manual",
                Map.of("enrollmentPublicId", endedEnrollment, "checkpointPublicId", cp,
                        "status", "ABSENT", "comment", "x"), admin);
        assertThat(ended.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ended.getBody().get("code")).isEqualTo("ATT_NOT_ENROLLED");
    }

    /**
     * §3 — matrice de contrôle d'accès du endpoint final des candidats,
     * exercée avec des fixtures représentatives (pas seulement le
     * {@code @PreAuthorize}) : ADMIN et SCHOOL_ADMINISTRATION 200 ; un
     * PEDAGOGICAL_MANAGER dans son périmètre 200, hors périmètre 403 ; le
     * formateur affecté 200, un formateur non affecté 403 ; STUDENT 403 ;
     * anonyme 401.
     */
    @Test
    void candidatesEndpointEnforcesFineGrainedScopePerRole() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        String path = "/api/v1/sessions/" + fx.sessionId() + "/attendance/candidates";

        assertThat(rawStatus(path, admin)).as("ADMIN").isEqualTo(HttpStatus.OK);
        assertThat(rawStatus(path, roleToken(RoleCode.SCHOOL_ADMINISTRATION)))
                .as("SCHOOL_ADMINISTRATION").isEqualTo(HttpStatus.OK);
        assertThat(rawStatus(path, tokenFor(fx.teacher()))).as("TEACHER affecté").isEqualTo(HttpStatus.OK);
        assertThat(rawStatus(path, roleToken(RoleCode.TEACHER)))
                .as("TEACHER non affecté").isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rawStatus(path, tokenFor(fx.students().get(0)))).as("STUDENT").isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rawStatus(path, null)).as("anonyme").isEqualTo(HttpStatus.UNAUTHORIZED);

        // PEDAGOGICAL_MANAGER hors périmètre (aucune affectation) -> 403.
        Account outOfScope = accountWithRoles(RoleCode.PEDAGOGICAL_MANAGER);
        assertThat(rawStatus(path, tokenFor(outOfScope)))
                .as("PEDAGOGICAL_MANAGER hors périmètre").isEqualTo(HttpStatus.FORBIDDEN);

        // PEDAGOGICAL_MANAGER responsable principal de la formation -> 200.
        Account inScope = accountWithRoles(RoleCode.PEDAGOGICAL_MANAGER);
        created("/api/v1/pedagogical-assignments", Map.of(
                "programPublicId", fx.program(), "userPublicId", inScope.publicId(),
                "type", "PRIMARY_MANAGER", "reason", "responsable de la formation"), admin);
        assertThat(rawStatus(path, tokenFor(inScope)))
                .as("PEDAGOGICAL_MANAGER dans son périmètre").isEqualTo(HttpStatus.OK);

        // SCHOOL_ADMINISTRATION peut aussi enregistrer une présence manuelle
        // (rôle « global » du modèle documenté — exclu seulement des points
        // de contrôle) ; un formateur non affecté reste refusé.
        String cp = firstCheckpoint(admin, fx.sessionId());
        String manualBody = "{\"enrollmentPublicId\":\"" + fx.enrollments().get(0)
                + "\",\"checkpointPublicId\":\"" + cp + "\",\"status\":\"ABSENT\",\"comment\":\"absent\"}";
        assertThat(rawPost("/api/v1/sessions/" + fx.sessionId() + "/attendance/manual",
                manualBody, roleToken(RoleCode.SCHOOL_ADMINISTRATION)))
                .as("SCHOOL_ADMINISTRATION saisie manuelle").isEqualTo(HttpStatus.CREATED);
        assertThat(rawPost("/api/v1/sessions/" + fx.sessionId() + "/attendance/manual",
                manualBody, roleToken(RoleCode.TEACHER)))
                .as("TEACHER non affecté saisie manuelle").isEqualTo(HttpStatus.FORBIDDEN);
    }

    private HttpStatus rawPost(String path, String jsonBody, String token) {
        return (HttpStatus) restTemplate.exchange(
                RequestEntity.post(URI.create(path)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).body(jsonBody),
                String.class).getStatusCode();
    }

    // ------------------------------------------------------------------
    // Concurrence déterministe (correctif PR #22 §3)
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void qrValidationAndManualRecordRaceKeepExactlyOneRecord() throws Exception {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        String cp = firstCheckpoint(admin, fx.sessionId());
        Map<String, Object> issued = post("/api/v1/sessions/" + fx.sessionId() + "/attendance-token",
                null, admin, HttpStatus.OK);
        String student = tokenFor(fx.students().get(0));

        Callable<ResponseEntity<Map<String, Object>>> validate = () -> exchange(HttpMethod.POST,
                "/api/v1/attendance/validate", Map.of("shortCode", issued.get("shortCode")), student);
        Callable<ResponseEntity<Map<String, Object>>> manual = () -> exchange(HttpMethod.POST,
                "/api/v1/sessions/" + fx.sessionId() + "/attendance/manual",
                Map.of("enrollmentPublicId", fx.enrollments().get(0), "checkpointPublicId", cp,
                        "status", "ABSENT", "comment", "conflit de course"), admin);

        List<HttpStatus> statuses = bothConcurrently(validate, manual).stream()
                .map(r -> (HttpStatus) r.getStatusCode()).toList();
        assertThat(statuses).filteredOn(HttpStatus::is2xxSuccessful).hasSize(1);
        assertThat(statuses).filteredOn(HttpStatus.CONFLICT::equals).hasSize(1);
        assertThat(statuses).noneMatch(HttpStatus::is5xxServerError);

        Map<String, Object> roster = getMap("/api/v1/sessions/" + fx.sessionId() + "/attendance", admin);
        List<Map<String, Object>> records = (List<Map<String, Object>>) roster.get("records");
        assertThat(records).hasSize(1);
    }

    @Test
    void twoConcurrentManualRecordsKeepExactlyOneRow() throws Exception {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        String cp = firstCheckpoint(admin, fx.sessionId());
        Map<String, Object> body = Map.of("enrollmentPublicId", fx.enrollments().get(0),
                "checkpointPublicId", cp, "status", "ABSENT", "comment", "double saisie simultanée");

        Callable<ResponseEntity<Map<String, Object>>> call = () -> exchange(HttpMethod.POST,
                "/api/v1/sessions/" + fx.sessionId() + "/attendance/manual", body, admin);
        List<ResponseEntity<Map<String, Object>>> results = bothConcurrently(call, call);
        List<HttpStatus> statuses = results.stream().map(r -> (HttpStatus) r.getStatusCode()).toList();
        assertThat(statuses).filteredOn(HttpStatus.CREATED::equals).hasSize(1);
        assertThat(statuses).filteredOn(HttpStatus.CONFLICT::equals).hasSize(1);
        assertThat(statuses).noneMatch(HttpStatus::is5xxServerError);
        assertThat(results.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).findFirst()
                .orElseThrow().getBody().get("code")).isEqualTo("ATT_ALREADY_RECORDED");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) getMap(
                "/api/v1/sessions/" + fx.sessionId() + "/attendance", admin).get("records");
        assertThat(records).hasSize(1);
    }

    @Test
    void twoConcurrentCorrectionsYieldOneWinnerAndOneControlledConflict() throws Exception {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        String cp = firstCheckpoint(admin, fx.sessionId());
        String attId = (String) post("/api/v1/sessions/" + fx.sessionId() + "/attendance/manual",
                Map.of("enrollmentPublicId", fx.enrollments().get(0), "checkpointPublicId", cp,
                        "status", "PRESENT", "comment", "présent"),
                admin, HttpStatus.CREATED).get("attendancePublicId");

        String url = "/api/v1/sessions/" + fx.sessionId() + "/attendance/" + attId + "/correct";
        Callable<ResponseEntity<Map<String, Object>>> toAbsent = () -> exchange(HttpMethod.POST, url,
                Map.of("status", "ABSENT", "reason", "corrigé en absent"), admin);
        Callable<ResponseEntity<Map<String, Object>>> toLate = () -> exchange(HttpMethod.POST, url,
                Map.of("status", "LATE", "lateMinutes", 12, "reason", "corrigé en retard"), admin);

        List<ResponseEntity<Map<String, Object>>> results = bothConcurrently(toAbsent, toLate);
        List<HttpStatus> statuses = results.stream().map(r -> (HttpStatus) r.getStatusCode()).toList();
        assertThat(statuses).filteredOn(HttpStatus.OK::equals).hasSize(1);
        assertThat(statuses).filteredOn(HttpStatus.CONFLICT::equals).hasSize(1);
        assertThat(statuses).noneMatch(HttpStatus::is5xxServerError);
        assertThat(results.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).findFirst()
                .orElseThrow().getBody().get("code")).isEqualTo("ATT_RECORD_INVALID_STATE");

        // Historique cohérent : une seule correction de statut appliquée.
        List<Map<String, Object>> history = listRaw(
                "/api/v1/sessions/" + fx.sessionId() + "/attendance/" + attId + "/history", admin);
        assertThat(history).extracting(h -> h.get("action"))
                .containsExactly("CREATED_MANUALLY", "STATUS_CORRECTED");
        Map<String, Object> finalRecord = getMap(
                "/api/v1/sessions/" + fx.sessionId() + "/attendance", admin);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) finalRecord.get("records");
        assertThat(rows.get(0).get("status")).isIn("ABSENT", "LATE");
    }

    @Test
    void twoConcurrentJustificationReviewsYieldOneDecisionAndOneControlledConflict() throws Exception {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        String cp = firstCheckpoint(admin, fx.sessionId());
        post("/api/v1/sessions/" + fx.sessionId() + "/close", null, admin, HttpStatus.NO_CONTENT);

        String student = tokenFor(fx.students().get(0));
        String justifId = (String) post("/api/v1/me/attendance/justifications",
                Map.of("checkpointPublicId", cp, "category", "MEDICAL", "comment", "certificat"),
                student, HttpStatus.CREATED).get("publicId");

        String reviewer = roleToken(RoleCode.SCHOOL_ADMINISTRATION);
        String url = "/api/v1/attendance/justifications/" + justifId + "/review";
        Callable<ResponseEntity<Map<String, Object>>> accept = () -> exchange(HttpMethod.POST, url,
                Map.of("decision", "ACCEPTED"), reviewer);
        Callable<ResponseEntity<Map<String, Object>>> reject = () -> exchange(HttpMethod.POST, url,
                Map.of("decision", "REJECTED", "decisionReason", "pièce illisible"), reviewer);

        List<HttpStatus> statuses = bothConcurrently(accept, reject).stream()
                .map(r -> (HttpStatus) r.getStatusCode()).toList();
        assertThat(statuses).filteredOn(HttpStatus.OK::equals).hasSize(1);
        assertThat(statuses).filteredOn(HttpStatus.CONFLICT::equals).hasSize(1);
        assertThat(statuses).noneMatch(HttpStatus::is5xxServerError);

        Map<String, Object> settled = getMap(
                "/api/v1/attendance/justifications/" + justifId, reviewer);
        assertThat(settled.get("status")).isIn("ACCEPTED", "REJECTED");
    }

    // ------------------------------------------------------------------
    // Justificatifs + espace apprenant (V10)
    // ------------------------------------------------------------------

    @Test
    void studentJustificationLifecycleAcceptedThenRejected() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        String cp = firstCheckpoint(admin, fx.sessionId());
        // Un second point de contrôle pour tester le parcours de refus.
        String cp2 = (String) post("/api/v1/sessions/" + fx.sessionId() + "/checkpoints",
                Map.of("label", "Fin", "type", "END"), admin, HttpStatus.CREATED).get("publicId");
        post("/api/v1/sessions/" + fx.sessionId() + "/checkpoints/" + cp2 + "/open", null, admin,
                HttpStatus.NO_CONTENT);
        // Séance fermée -> les points de contrôle passent CLOSED, les absences sont dérivées.
        post("/api/v1/sessions/" + fx.sessionId() + "/close", null, admin, HttpStatus.NO_CONTENT);

        String student = tokenFor(fx.students().get(0));
        Map<String, Object> justif = post("/api/v1/me/attendance/justifications",
                Map.of("checkpointPublicId", cp, "category", "MEDICAL",
                        "comment", "certificat médical du 10/09"), student, HttpStatus.CREATED);
        String justifId = (String) justif.get("publicId");
        assertThat(justif.get("status")).isEqualTo("PENDING");
        assertThat(justif.get("attendanceStatus")).isEqualTo("ABSENT");
        // Vue apprenant : les champs d'identité nominative restent nuls.
        assertThat(justif.get("studentNumber")).isNull();
        assertThat(justif.get("firstName")).isNull();

        // Un second dépôt actif -> 409.
        ResponseEntity<Map<String, Object>> again = exchange(HttpMethod.POST,
                "/api/v1/me/attendance/justifications",
                Map.of("checkpointPublicId", cp, "category", "TRANSPORT", "comment", "grève"), student);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().get("code")).isEqualTo("ATT_JUSTIFICATION_INVALID_STATE");

        // Un formateur ne peut pas examiner.
        ResponseEntity<Map<String, Object>> byTeacher = exchange(HttpMethod.POST,
                "/api/v1/attendance/justifications/" + justifId + "/review",
                Map.of("decision", "ACCEPTED"), tokenFor(fx.teacher()));
        assertThat(byTeacher.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // L'administration accepte -> la présence passe EXCUSED_ABSENCE.
        Map<String, Object> reviewed = post("/api/v1/attendance/justifications/" + justifId + "/review",
                Map.of("decision", "ACCEPTED"), roleToken(RoleCode.SCHOOL_ADMINISTRATION), HttpStatus.OK);
        assertThat(reviewed.get("status")).isEqualTo("ACCEPTED");
        assertThat(reviewed.get("attendanceStatus")).isEqualTo("EXCUSED_ABSENCE");
        assertThat(auditActions(justifId)).contains("ATTENDANCE_JUSTIFICATION_SUBMITTED",
                "ATTENDANCE_JUSTIFICATION_REVIEWED");

        // Ré-examen d'un justificatif déjà traité -> 409.
        ResponseEntity<Map<String, Object>> reReview = exchange(HttpMethod.POST,
                "/api/v1/attendance/justifications/" + justifId + "/review",
                Map.of("decision", "REJECTED", "decisionReason", "x"),
                roleToken(RoleCode.SCHOOL_ADMINISTRATION));
        assertThat(reReview.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Sur le second point de contrôle : refus sans motif -> 400 ;
        // refus motivé -> REJECTED, la présence reste ABSENT ; nouveau
        // dépôt alors possible.
        String j2 = (String) post("/api/v1/me/attendance/justifications",
                Map.of("checkpointPublicId", cp2, "category", "TRANSPORT", "comment", "retard de train"),
                student, HttpStatus.CREATED).get("publicId");
        assertThat(exchange(HttpMethod.POST, "/api/v1/attendance/justifications/" + j2 + "/review",
                Map.of("decision", "REJECTED"), roleToken(RoleCode.SCHOOL_ADMINISTRATION)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> rejected = post("/api/v1/attendance/justifications/" + j2 + "/review",
                Map.of("decision", "REJECTED", "decisionReason", "pièce illisible"),
                roleToken(RoleCode.SCHOOL_ADMINISTRATION), HttpStatus.OK);
        assertThat(rejected.get("status")).isEqualTo("REJECTED");
        assertThat(rejected.get("attendanceStatus")).isEqualTo("ABSENT");

        Map<String, Object> resubmit = post("/api/v1/me/attendance/justifications",
                Map.of("checkpointPublicId", cp2, "category", "OTHER", "comment", "nouvelle pièce"),
                student, HttpStatus.CREATED);
        assertThat(resubmit.get("status")).isEqualTo("PENDING");
    }

    @Test
    @SuppressWarnings("unchecked")
    void studentSeesOnlyTheirOwnAttendance() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 2);
        post("/api/v1/sessions/" + fx.sessionId() + "/close", null, admin, HttpStatus.NO_CONTENT);

        Map<String, Object> mine = getMap("/api/v1/me/attendance", tokenFor(fx.students().get(0)));
        List<Map<String, Object>> rows = (List<Map<String, Object>>) mine.get("content");
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(r -> assertThat(r).doesNotContainKeys("email", "id", "studentNumber"));
        // Absence dérivée d'un point de contrôle fermé, justifiable.
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.get("status")).isEqualTo("ABSENT");
            assertThat(r.get("canJustify")).isEqualTo(true);
        });

        // Un non-STUDENT ne peut pas appeler /me/attendance.
        assertThat(exchange(HttpMethod.GET, "/api/v1/me/attendance", null, admin).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------
    // Rapports + export CSV (V10)
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void reportsAggregateHalfDaysFromRealAttendance() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 2);
        // Un apprenant émarge (PRESENT), l'autre pas.
        Map<String, Object> issued = post("/api/v1/sessions/" + fx.sessionId() + "/attendance-token",
                null, admin, HttpStatus.OK);
        post("/api/v1/attendance/validate", Map.of("shortCode", issued.get("shortCode")),
                tokenFor(fx.students().get(0)), HttpStatus.OK);
        post("/api/v1/sessions/" + fx.sessionId() + "/close", null, admin, HttpStatus.NO_CONTENT);

        // La base de test est partagée : on borne le rapport à la classe
        // fraîche de cette fixture.
        Map<String, Object> summary = getMap("/api/v1/attendance/reports/summary"
                + "?from=2026-09-01T00:00:00Z&to=2026-09-30T00:00:00Z&classGroup=" + fx.classA(), admin);
        Map<String, Object> totals = (Map<String, Object>) summary.get("totals");
        // Aucun rythme d'alternance affecté : contexte UNKNOWN. La
        // demi-journée présente est comptée (expected + present) ; la
        // demi-journée non émargée est signalée en "unknown", jamais en
        // "absent" (design §4.C).
        assertThat(((Number) totals.get("presentHalfDays")).longValue()).isEqualTo(1);
        assertThat(((Number) totals.get("expectedHalfDays")).longValue()).isEqualTo(1);
        assertThat(((Number) totals.get("unknownHalfDays")).longValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) totals.get("absentHalfDays")).longValue()).isEqualTo(0);

        Map<String, Object> byStudent = getMap("/api/v1/attendance/reports/students"
                + "?from=2026-09-01T00:00:00Z&to=2026-09-30T00:00:00Z&classGroup=" + fx.classA(), admin);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) byStudent.get("content");
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(r -> assertThat(r).doesNotContainKeys("email"));

        Map<String, Object> byClass = getMap("/api/v1/attendance/reports/classes"
                + "?from=2026-09-01T00:00:00Z&to=2026-09-30T00:00:00Z&classGroup=" + fx.classA(), admin);
        List<Map<String, Object>> classRows = (List<Map<String, Object>>) byClass.get("content");
        assertThat(classRows).isNotEmpty();
        // Correctif §7 : le code lisible de la classe, jamais l'UUID public.
        assertThat(classRows).allSatisfy(r -> {
            assertThat(r.get("classCode")).isEqualTo("C1");
            assertThat(String.valueOf(r.get("classCode"))).isNotEqualTo(fx.classA());
        });

        // Tri serveur borné : valide accepté, invalide -> 400 ATT_REPORT_INVALID_SORT.
        assertThat(getMap("/api/v1/attendance/reports/students?from=2026-09-01T00:00:00Z"
                + "&to=2026-09-30T00:00:00Z&classGroup=" + fx.classA() + "&sort=studentNumber,desc", admin))
                .isNotNull();
        ResponseEntity<Map<String, Object>> badSort = exchange(HttpMethod.GET,
                "/api/v1/attendance/reports/students?from=2026-09-01T00:00:00Z&to=2026-09-30T00:00:00Z"
                        + "&classGroup=" + fx.classA() + "&sort=email,asc", null, admin);
        assertThat(badSort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badSort.getBody().get("code")).isEqualTo("ATT_REPORT_INVALID_SORT");
        ResponseEntity<Map<String, Object>> badDir = exchange(HttpMethod.GET,
                "/api/v1/attendance/reports/sessions?from=2026-09-01T00:00:00Z&to=2026-09-30T00:00:00Z"
                        + "&sort=startsAt,sideways", null, admin);
        assertThat(badDir.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badDir.getBody().get("code")).isEqualTo("ATT_REPORT_INVALID_SORT");
    }

    @Test
    void reportRejectsCorruptPersistedTimeZoneInsteadOfFabricatingTotals() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        post("/api/v1/sessions/" + fx.sessionId() + "/close", null, admin, HttpStatus.NO_CONTENT);
        // Corruption d'un fuseau persisté (impossible via l'API : validé à
        // l'écriture). Le rapport ne doit pas produire un résultat chiffré
        // trompeur (correctif §1) : erreur interne contrôlée, jamais un 200.
        int updated = jdbcTemplate.update(
                "UPDATE course_session SET time_zone_id = ? WHERE public_id = UUID_TO_BIN(?)",
                "Invalid/Zone", fx.sessionId());
        assertThat(updated).isEqualTo(1);
        try {
            ResponseEntity<Map<String, Object>> report = exchange(HttpMethod.GET,
                    "/api/v1/attendance/reports/summary?from=2026-09-01T00:00:00Z&to=2026-09-30T00:00:00Z"
                            + "&classGroup=" + fx.classA(), null, admin);
            assertThat(report.getStatusCode().is2xxSuccessful()).isFalse();
            assertThat(report.getStatusCode().is5xxServerError()).isTrue();
        } finally {
            // La base de test est partagée : restaurer un fuseau valide
            // pour ne pas casser les rapports non bornés des autres tests.
            jdbcTemplate.update(
                    "UPDATE course_session SET time_zone_id = ? WHERE public_id = UUID_TO_BIN(?)",
                    "Europe/Paris", fx.sessionId());
        }
    }

    @Test
    void csvExportIsUtf8WithBomAndNeutralizesFormulaInjection() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        Map<String, Object> body = new java.util.HashMap<>(
                sessionBody(teacher.publicId(), List.of(chain.classA())));
        body.put("title", "=SUM(A1:A9)+cmd|'/c calc'");
        created("/api/v1/sessions", body, admin);

        String csv = getCsv("/api/v1/attendance/reports/sessions/export"
                + "?from=2026-09-01T00:00:00Z&to=2026-09-30T00:00:00Z", admin);
        assertThat(csv).startsWith("﻿"); // BOM UTF-8
        assertThat(csv).contains("session_id;titre;debut"); // en-tête, séparateur ;
        // La cellule commençant par '=' est neutralisée par une apostrophe en tête.
        assertThat(csv).contains("'=SUM(A1:A9)+cmd");
        assertThat(csv).doesNotContain(";=SUM(A1:A9)");
    }

    /**
     * §6 — l'export CSV des présences <em>d'une séance</em> ne contient
     * plus la colonne libre « commentaire » (minimisation) ; le texte des
     * commentaires n'apparaît nulle part ; la neutralisation d'injection
     * de formule reste appliquée à toutes les cellules issues des
     * utilisateurs (ici le libellé d'un point de contrôle).
     */
    @Test
    void sessionAttendanceCsvExportExcludesFreeTextCommentAndNeutralizesInjection() {
        String admin = adminToken();
        Fixture fx = openSessionWithEnrolledStudents(admin, 1);
        String cp = (String) post("/api/v1/sessions/" + fx.sessionId() + "/checkpoints",
                Map.of("label", "=SUM(A1)+cmd|'/c calc'", "type", "CUSTOM"), admin, HttpStatus.CREATED)
                .get("publicId");
        post("/api/v1/sessions/" + fx.sessionId() + "/attendance/manual",
                Map.of("enrollmentPublicId", fx.enrollments().get(0), "checkpointPublicId", cp,
                        "status", "ABSENT", "comment", "=DANGER()-formule@ligne libre"),
                admin, HttpStatus.CREATED);

        String csv = getCsv("/api/v1/sessions/" + fx.sessionId() + "/attendance/export", admin);
        String header = csv.split("\r\n", 2)[0];
        assertThat(header).doesNotContain("commentaire");
        assertThat(header).contains("point_de_controle;numero_etudiant;prenom;nom;statut;retard_minutes;"
                + "enregistre_le;canal");
        // Le contenu du commentaire libre est absent de l'export.
        assertThat(csv).doesNotContain("DANGER");
        // Neutralisation d'injection conservée : le libellé du point de
        // contrôle commençant par '=' est préfixé d'une apostrophe.
        assertThat(csv).contains("'=SUM(A1)+cmd");
        assertThat(csv).doesNotContain(";=SUM(A1)");
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private String getCsv(String path, String token) {
        return restTemplate.exchange(
                RequestEntity.get(URI.create(path)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                String.class).getBody();
    }

    private String firstCheckpoint(String token, String sessionId) {
        Map<String, Object> session = getMap("/api/v1/sessions/" + sessionId, token);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) session.get("checkpoints");
        return (String) checkpoints.get(0).get("publicId");
    }

    private String roleToken(RoleCode... roles) {
        return tokenFor(accountWithRoles(roles));
    }

    private List<Map<String, Object>> listRaw(String path, String token) {
        return restTemplate.exchange(
                RequestEntity.get(URI.create(path)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
    }

    private record Fixture(String sessionId, List<Account> students, List<String> enrollments,
                           String classA, String program, Account teacher) {
    }

    /**
     * Inscrit un apprenant supplémentaire dans la classe de la fixture,
     * avec une {@code startDate} explicite (pour tester la couverture de
     * l'inscription à la date de la séance — §2). Renvoie l'identifiant
     * public de l'inscription.
     */
    private String enrollExtraStudentInFixtureClass(String admin, Fixture fx, String startDate) {
        Account student = accountWithRoles(RoleCode.STUDENT);
        String profile = createProfile(admin, student.publicId());
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("studentProfilePublicId", profile);
        body.put("classGroupPublicId", fx.classA());
        if (startDate != null) {
            body.put("startDate", startDate);
        }
        return (String) created("/api/v1/enrollments", body, admin).get("publicId");
    }

    private Fixture openSessionWithEnrolledStudents(String admin, int studentCount) {
        return openSessionWithEnrolledStudents(admin, studentCount,
                "2026-09-10T08:00:00Z", "2026-09-10T12:00:00Z");
    }

    private Fixture openSessionWithEnrolledStudents(String admin, int studentCount,
                                                   String startsAt, String endsAt) {
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        Map<String, Object> body = new java.util.HashMap<>(
                sessionBody(teacher.publicId(), List.of(chain.classA())));
        body.put("startsAt", startsAt);
        body.put("endsAt", endsAt);
        String sessionId = (String) created("/api/v1/sessions", body, admin).get("publicId");
        post("/api/v1/sessions/" + sessionId + "/open", null, admin, HttpStatus.NO_CONTENT);

        java.util.ArrayList<Account> students = new java.util.ArrayList<>();
        java.util.ArrayList<String> enrollments = new java.util.ArrayList<>();
        for (int i = 0; i < studentCount; i++) {
            Account student = accountWithRoles(RoleCode.STUDENT);
            String profile = createProfile(admin, student.publicId());
            // startDate explicite antérieure à toutes les dates de séance des
            // fixtures (la plus ancienne = 2026-08-01) : la couverture de
            // l'inscription à la date de la séance ne doit jamais dépendre de
            // la date d'exécution des tests (défaut = LocalDate.now).
            String enrollment = (String) created("/api/v1/enrollments", Map.of("studentProfilePublicId", profile,
                    "classGroupPublicId", chain.classA(), "startDate", "2026-08-01"), admin).get("publicId");
            students.add(student);
            enrollments.add(enrollment);
        }
        return new Fixture(sessionId, students, enrollments, chain.classA(), chain.program(), teacher);
    }

    private String createProfile(String admin, String userPublicId) {
        return (String) created("/api/v1/student-profiles", Map.of("userPublicId", userPublicId,
                "studentNumber", "ESIC-2026-" + code()), admin).get("publicId");
    }

    private Map<String, Object> sessionBody(String teacherPublicId, List<String> classPublicIds) {
        return Map.of("teacherPublicId", teacherPublicId, "classPublicIds", classPublicIds,
                "startsAt", "2026-09-10T08:00:00Z", "endsAt", "2026-09-10T12:00:00Z",
                "timeZoneId", "Europe/Paris", "reason", "séance exceptionnelle");
    }

    private record Chain(String classA, String classB, String program) {
    }

    private Chain academicChain(String admin) {
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
        String classA = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C1", "name", "Classe 1"), admin)
                .get("publicId");
        String classB = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C2", "name", "Classe 2"), admin)
                .get("publicId");
        return new Chain(classA, classB, program);
    }

    private static HttpStatus get(Future<HttpStatus> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Exécute {@code a} et {@code b} <strong>réellement</strong> en
     * parallèle (deux threads) et renvoie leurs résultats dans l'ordre
     * de soumission — support des tests de concurrence §3.
     */
    private static <T> List<T> bothConcurrently(Callable<T> a, Callable<T> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<T>> futures = pool.invokeAll(List.of(a, b));
            return List.of(joinFuture(futures.get(0)), joinFuture(futures.get(1)));
        } finally {
            pool.shutdownNow();
        }
    }

    private static <T> T joinFuture(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------
    // HTTP utilitaires
    // ------------------------------------------------------------------

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> post(String path, Map<String, Object> body, String token, HttpStatus expected) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody()).isEqualTo(expected);
        return response.getBody();
    }

    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET, path, null, token);
        assertThat(response.getStatusCode()).as("GET " + path).isEqualTo(HttpStatus.OK);
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

    private List<String> auditActions(String resourcePublicId) {
        UUID target = UUID.fromString(resourcePublicId);
        return auditEventRepository.findAll().stream()
                .filter(event -> target.equals(event.getResourcePublicId()))
                .map(AuditEvent::getAction)
                .toList();
    }

    private record Account(String publicId, String email) {
    }

    private Account accountWithRoles(RoleCode... roles) {
        UserAccount account = new UserAccount("att-" + UUID.randomUUID() + "@esic-connect.test",
                "Att", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return new Account(account.getPublicId().toString(), account.getEmail());
    }

    private String adminToken() {
        return tokenFor(accountWithRoles(RoleCode.ADMIN));
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
