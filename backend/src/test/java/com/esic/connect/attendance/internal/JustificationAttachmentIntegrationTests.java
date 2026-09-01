package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.JustificationFileStorage;
import com.esic.connect.attendance.JustificationFileStorageException;
import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
import org.junit.jupiter.api.AfterEach;
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

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Bloc G1-E — parcours complet des pièces jointes de justificatif :
 * dépôt multipart par le propriétaire, métadonnées, téléchargement
 * sécurisé (en-têtes), cloisonnement propriétaire / examinateur,
 * validation ({@code 413} / {@code 415}), unicité d'une pièce active
 * ({@code 409}), retrait + redépôt, dépôt refusé sur un justificatif
 * examiné, compensation d'un échec de stockage, réconciliation des
 * lignes {@code PENDING_STORAGE}, et notification du propriétaire à
 * l'examen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.attendance.justification-reconciliation-batch=2")
@ActiveProfiles("test")
class JustificationAttachmentIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    private static final byte[] PDF = "%PDF-1.4\nfaux justificatif fictif ESIC\n%%EOF".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PNG = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4, 5, 6, 7, 8};

    @TestConfiguration
    static class FlakyStorageConfig {

        static volatile boolean failStore = false;
        /**
         * Simule la défaillance de la trace d'audit <em>après</em> que la
         * pièce est durablement stockée : un {@link org.springframework.context.event.EventListener}
         * synchrone sur {@code AttendanceChangeEvent} — exactement comme le
         * listener d'audit de production — qui lève quand il est armé. Aucun
         * bean de production n'est modifié.
         */
        static volatile boolean failAuditAfterStore = false;
        /**
         * Simule l'échec silencieux (best effort) de la suppression du
         * fichier lors d'un retrait : la ligne passe {@code DELETED} mais le
         * fichier reste sur le disque (« orphelin à métadonnée périmée »).
         */
        static volatile boolean skipDelete = false;

        static void reset() {
            failStore = false;
            failAuditAfterStore = false;
            skipDelete = false;
        }

        @Bean
        AttachmentAuditFaultListener attachmentAuditFaultListener() {
            return new AttachmentAuditFaultListener();
        }

        static class AttachmentAuditFaultListener {
            // Priorité maximale : s'exécute AVANT le listener d'audit de
            // production, qui ne tourne donc pas quand la faute est armée —
            // l'absence de trace est déterministe, pas dépendante de l'ordre.
            @org.springframework.core.annotation.Order(
                    org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
            @org.springframework.context.event.EventListener
            void onChange(com.esic.connect.attendance.AttendanceChangeEvent event) {
                if (failAuditAfterStore
                        && event.action() == com.esic.connect.attendance.AttendanceChangeAction
                                .JUSTIFICATION_ATTACHMENT_STORED) {
                    throw new org.springframework.dao.DataAccessResourceFailureException(
                            "panne simulée d'écriture d'audit après stockage");
                }
            }
        }

        @Bean
        @Primary
        JustificationFileStorage flakyStorage() throws Exception {
            Path base = Files.createTempDirectory("esic-g1e-it-");
            LocalFilesystemJustificationFileStorage real = new LocalFilesystemJustificationFileStorage(base.toString());
            return new JustificationFileStorage() {
                @Override
                public String newStorageKey() {
                    return real.newStorageKey();
                }

                @Override
                public StoredRef store(String storageKey, PendingUpload upload) {
                    if (failStore) {
                        throw new JustificationFileStorageException(JustificationFileStorageException.Kind.IO_ERROR,
                                "panne simulée de stockage");
                    }
                    return real.store(storageKey, upload);
                }

                @Override
                public InputStream open(String storageKey) {
                    return real.open(storageKey);
                }

                @Override
                public void delete(String storageKey) {
                    if (skipDelete) {
                        return;
                    }
                    real.delete(storageKey);
                }
            };
        }

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
    @Autowired
    private JustificationFileStorage storage;
    @Autowired
    private JustificationAttachmentReconciliationService reconciliation;

    @BeforeEach
    void setUp() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        FlakyStorageConfig.reset();
    }

    @AfterEach
    void tearDown() {
        FlakyStorageConfig.reset();
    }

    // ------------------------------------------------------------------
    // Parcours nominal + téléchargement sécurisé
    // ------------------------------------------------------------------

    @Test
    void ownerUploadsAPdfThenDownloadsItWithSecureHeaders() {
        Ctx c = pendingJustification();

        Map<String, Object> meta = uploadOk(c.studentToken, c.justificationId, "certificat médical.pdf",
                MediaType.APPLICATION_PDF_VALUE, PDF);
        assertThat(meta.get("contentType")).isEqualTo("application/pdf");
        assertThat(((Number) meta.get("sizeBytes")).longValue()).isEqualTo(PDF.length);
        assertThat(meta).doesNotContainKeys("storageKey", "id", "justificationId");

        Map<String, Object> fetched = getMap(c.studentToken,
                "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment");
        assertThat(fetched.get("publicId")).isEqualTo(meta.get("publicId"));

        ResponseEntity<byte[]> dl = download(c.studentToken,
                "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment/download");
        assertThat(dl.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dl.getBody()).isEqualTo(PDF);
        assertThat(dl.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;");
        assertThat(dl.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(dl.getHeaders().getCacheControl()).contains("no-store");
        assertThat(dl.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(dl.getHeaders().getContentLength()).isEqualTo(PDF.length);

        assertThat(auditActionsFor(c.justificationId)).contains("ATTENDANCE_JUSTIFICATION_ATTACHMENT_STORED");
    }

    @Test
    void aStaffReviewerInScopeCanDownloadTheAttachment() {
        Ctx c = pendingJustification();
        uploadOk(c.studentToken, c.justificationId, "c.pdf", MediaType.APPLICATION_PDF_VALUE, PDF);

        ResponseEntity<byte[]> dl = download(c.adminToken,
                "/api/v1/attendance/justifications/" + c.justificationId + "/attachment/download");
        assertThat(dl.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dl.getBody()).isEqualTo(PDF);
    }

    // ------------------------------------------------------------------
    // Cloisonnement
    // ------------------------------------------------------------------

    @Test
    void anotherStudentGets404NotForbiddenOnEveryAttachmentRoute() {
        Ctx c = pendingJustification();
        uploadOk(c.studentToken, c.justificationId, "c.pdf", MediaType.APPLICATION_PDF_VALUE, PDF);
        String intruder = tokenFor(account(RoleCode.STUDENT));

        assertThat(multipart(intruder, "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment",
                "x.pdf", MediaType.APPLICATION_PDF_VALUE, PDF).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.exchange(RequestEntity.get(URI.create(
                        "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder).build(),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(download(intruder, "/api/v1/me/attendance/justifications/" + c.justificationId
                + "/attachment/download").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aTeacherWithoutScopeAndAManagerOutOfScopeGet404() {
        Ctx c = pendingJustification();
        uploadOk(c.studentToken, c.justificationId, "c.pdf", MediaType.APPLICATION_PDF_VALUE, PDF);

        String teacher = tokenFor(account(RoleCode.TEACHER));
        String manager = tokenFor(account(RoleCode.PEDAGOGICAL_MANAGER));

        for (String token : List.of(teacher, manager)) {
            assertThat(download(token, "/api/v1/attendance/justifications/" + c.justificationId
                    + "/attachment/download").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    void rejectsAnOversizedFileWith413AndPersistsNothing() {
        Ctx c = pendingJustification();
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        big[0] = '%';
        big[1] = 'P';
        big[2] = 'D';
        big[3] = 'F';
        big[4] = '-';

        ResponseEntity<Map<String, Object>> r = multipart(c.studentToken,
                "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment",
                "big.pdf", MediaType.APPLICATION_PDF_VALUE, big);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(attachmentRowCount(c.justificationId)).isZero();
    }

    @Test
    void rejectsAWrongTypeWith415AndPersistsNothing() {
        Ctx c = pendingJustification();
        byte[] zip = new byte[] {0x50, 0x4B, 0x03, 0x04, 9, 9, 9, 9};

        ResponseEntity<Map<String, Object>> r = multipart(c.studentToken,
                "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment",
                "sournois.pdf", MediaType.APPLICATION_PDF_VALUE, zip);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(attachmentRowCount(c.justificationId)).isZero();
    }

    // ------------------------------------------------------------------
    // Unicité, retrait, état du justificatif
    // ------------------------------------------------------------------

    @Test
    void aSecondUploadWhileOneActiveIsRejectedWith409() {
        Ctx c = pendingJustification();
        uploadOk(c.studentToken, c.justificationId, "1.pdf", MediaType.APPLICATION_PDF_VALUE, PDF);

        ResponseEntity<Map<String, Object>> second = multipart(c.studentToken,
                "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment",
                "2.png", MediaType.IMAGE_PNG_VALUE, PNG);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("code")).isEqualTo("ATT_ATTACHMENT_ALREADY_EXISTS");
    }

    @Test
    void twoConcurrentUploadsYieldExactlyOneActiveAttachment() throws Exception {
        Ctx c = pendingJustification();
        String url = "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment";
        List<ResponseEntity<Map<String, Object>>> results = bothConcurrently(
                () -> multipart(c.studentToken, url, "a.pdf", MediaType.APPLICATION_PDF_VALUE, PDF),
                () -> multipart(c.studentToken, url, "b.pdf", MediaType.APPLICATION_PDF_VALUE, PDF));
        List<HttpStatus> codes = results.stream().map(r -> (HttpStatus) r.getStatusCode()).sorted().toList();
        assertThat(codes).containsExactly(HttpStatus.CREATED, HttpStatus.CONFLICT);
        assertThat(activeRowCount(c.justificationId)).isEqualTo(1);
        assertThat(results.stream().anyMatch(r -> r.getStatusCode().is5xxServerError())).isFalse();
    }

    @Test
    void removingOwnAttachmentThenReUploadingSucceeds() {
        Ctx c = pendingJustification();
        uploadOk(c.studentToken, c.justificationId, "1.pdf", MediaType.APPLICATION_PDF_VALUE, PDF);

        ResponseEntity<Void> del = rest.exchange(RequestEntity.method(HttpMethod.DELETE, URI.create(
                        "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + c.studentToken).build(), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(multipart(c.studentToken,
                "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment",
                "2.pdf", MediaType.APPLICATION_PDF_VALUE, PDF).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void uploadingToAReviewedJustificationIsRejected() {
        Ctx c = pendingJustification();
        assertThat(rest.exchange(RequestEntity.post(URI.create(
                        "/api/v1/attendance/justifications/" + c.justificationId + "/review"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + c.adminToken)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("decision", "ACCEPTED")),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map<String, Object>> r = multipart(c.studentToken,
                "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment",
                "tard.pdf", MediaType.APPLICATION_PDF_VALUE, PDF);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------------
    // Compensation & réconciliation
    // ------------------------------------------------------------------

    @Test
    void aStorageFailureAfterThePendingRowCompensatesAndAllowsARetry() {
        Ctx c = pendingJustification();
        FlakyStorageConfig.failStore = true;

        ResponseEntity<Map<String, Object>> failed = multipart(c.studentToken,
                "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment",
                "c.pdf", MediaType.APPLICATION_PDF_VALUE, PDF);
        assertThat(failed.getStatusCode().is5xxServerError()).isTrue();
        // La ligne PENDING committée a été compensée (DELETED) : aucune pièce active.
        assertThat(activeRowCount(c.justificationId)).isZero();

        FlakyStorageConfig.failStore = false;
        assertThat(multipart(c.studentToken,
                "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment",
                "c.pdf", MediaType.APPLICATION_PDF_VALUE, PDF).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void anAuditFailureAfterTheAttachmentIsStoredStillReturns201AndKeepsThePiece() {
        Ctx c = pendingJustification();
        FlakyStorageConfig.failAuditAfterStore = true;

        // 1. Le dépôt réussit malgré l'échec de la trace d'audit : la pièce
        //    est déjà durablement stockée (fichier + ligne STORED committés),
        //    un rollback n'a plus de sens, l'API ne doit pas annoncer d'échec.
        ResponseEntity<Map<String, Object>> r = multipart(c.studentToken,
                "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment",
                "c.pdf", MediaType.APPLICATION_PDF_VALUE, PDF);
        assertThat(r.getStatusCode()).as("dépôt malgré audit KO -> " + r.getBody())
                .isEqualTo(HttpStatus.CREATED);

        // 2. La ligne est bien STORED et unique.
        assertThat(statusOf(r.getBody().get("publicId").toString())).isEqualTo("STORED");
        assertThat(activeRowCount(c.justificationId)).isEqualTo(1);

        // 3. La pièce est réellement téléchargeable.
        ResponseEntity<byte[]> dl = download(c.studentToken,
                "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment/download");
        assertThat(dl.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dl.getBody()).isEqualTo(PDF);

        // 4. Dette d'audit assumée : la trace ATTACHMENT_STORED est absente
        //    (échec isolé, non rejoué) — mais ce n'est pas un faux négatif d'API.
        assertThat(auditActionsFor(c.justificationId))
                .doesNotContain("ATTENDANCE_JUSTIFICATION_ATTACHMENT_STORED");

        // 5. Audit rétabli : un dépôt ultérieur (après retrait) est de nouveau tracé.
        FlakyStorageConfig.failAuditAfterStore = false;
        rest.exchange(RequestEntity.method(HttpMethod.DELETE, URI.create(
                        "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + c.studentToken).build(), Void.class);
        uploadOk(c.studentToken, c.justificationId, "c2.pdf", MediaType.APPLICATION_PDF_VALUE, PDF);
        assertThat(auditActionsFor(c.justificationId))
                .contains("ATTENDANCE_JUSTIFICATION_ATTACHMENT_STORED");
    }

    @Test
    void reconciliationPromotesAnAgedPendingRowWhoseFileIsValid() {
        Ctx c = pendingJustification();
        Map<String, Object> meta = uploadOk(c.studentToken, c.justificationId, "c.pdf",
                MediaType.APPLICATION_PDF_VALUE, PDF);
        // On force la ligne à l'état PENDING_STORAGE, vieillie au-delà du seuil,
        // en gardant le fichier réel sur disque (déjà écrit par le dépôt).
        backdateToPending(meta.get("publicId").toString());

        reconciliation.reconcile();

        assertThat(statusOf(meta.get("publicId").toString())).isEqualTo("STORED");
    }

    @Test
    void reconciliationDropsAnAgedPendingRowWhoseFileIsMissingAndFreesTheSlot() {
        Ctx c = pendingJustification();
        Map<String, Object> meta = uploadOk(c.studentToken, c.justificationId, "c.pdf",
                MediaType.APPLICATION_PDF_VALUE, PDF);
        backdateToPending(meta.get("publicId").toString());
        storage.delete(storageKeyOf(meta.get("publicId").toString()));

        reconciliation.reconcile();

        assertThat(statusOf(meta.get("publicId").toString())).isEqualTo("DELETED");
        // créneau d'unicité libéré : redépôt possible
        assertThat(multipart(c.studentToken,
                "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment",
                "c2.pdf", MediaType.APPLICATION_PDF_VALUE, PDF).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void reconciliationDoesNotSweepAFileOrphanedByADeletedRow() {
        // Portée assumée (dette G1-E) : la réconciliation ne traite QUE les
        // lignes PENDING_STORAGE. Un fichier laissé derrière par un retrait
        // dont la suppression best effort a échoué (ligne DELETED, fichier
        // présent) n'est PAS balayé — pas de scan actif du répertoire.
        Ctx c = pendingJustification();
        Map<String, Object> meta = uploadOk(c.studentToken, c.justificationId, "c.pdf",
                MediaType.APPLICATION_PDF_VALUE, PDF);
        String key = storageKeyOf(meta.get("publicId").toString());
        assertThat(fileExists(key)).isTrue();

        FlakyStorageConfig.skipDelete = true;
        ResponseEntity<Void> del = rest.exchange(RequestEntity.method(HttpMethod.DELETE, URI.create(
                        "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + c.studentToken).build(), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(statusOf(meta.get("publicId").toString())).isEqualTo("DELETED");
        assertThat(fileExists(key)).as("le fichier orphelin subsiste après un retrait sans suppression").isTrue();

        reconciliation.reconcile();

        // La réconciliation ne balaie pas cet orphelin : ligne DELETED, fichier toujours là.
        assertThat(statusOf(meta.get("publicId").toString())).isEqualTo("DELETED");
        assertThat(fileExists(key)).as("la réconciliation ne balaie pas un orphelin à ligne DELETED").isTrue();
    }

    @Test
    void reconciliationLeavesAFreshPendingRowUntouched() {
        Ctx c = pendingJustification();
        Map<String, Object> meta = uploadOk(c.studentToken, c.justificationId, "c.pdf",
                MediaType.APPLICATION_PDF_VALUE, PDF);
        // PENDING mais récent (created_at = maintenant) : hors fenêtre de réconciliation.
        jdbc.update("update justification_attachment set status='PENDING_STORAGE', stored_at=null "
                + "where public_id = UUID_TO_BIN(?)", meta.get("publicId").toString());

        reconciliation.reconcile();

        assertThat(statusOf(meta.get("publicId").toString())).isEqualTo("PENDING_STORAGE");
    }

    // ------------------------------------------------------------------
    // Notification du propriétaire à l'examen
    // ------------------------------------------------------------------

    @Test
    void acceptingAJustificationNotifiesTheOwnerExactlyOnceAndRejectingToo() {
        Ctx accepted = pendingJustification();
        review(accepted, "ACCEPTED", null);
        assertThat(notificationCount(accepted.studentInternalId, "JUSTIFICATION_ACCEPTED")).isEqualTo(1L);

        Ctx rejected = pendingJustification();
        review(rejected, "REJECTED", "pièce illisible");
        assertThat(notificationCount(rejected.studentInternalId, "JUSTIFICATION_REJECTED")).isEqualTo(1L);
        // corps neutre : jamais le motif de refus
        String body = jdbc.queryForObject("select body from notification where recipient_user_id = ? "
                + "and type = 'JUSTIFICATION_REJECTED'", String.class, rejected.studentInternalId);
        assertThat(body).doesNotContain("illisible");

        // l'apprenant de l'autre justificatif n'est pas notifié du refus
        assertThat(notificationCount(accepted.studentInternalId, "JUSTIFICATION_REJECTED")).isZero();
    }

    // ==================================================================
    // Fixtures
    // ==================================================================

    private record Ctx(String adminToken, String studentToken, long studentInternalId, String justificationId) {
    }

    /** Crée une classe, une séance ouverte + point de contrôle, inscrit un
     *  apprenant et dépose un justificatif {@code PENDING} (présence
     *  {@code ABSENT} dérivée). */
    private Ctx pendingJustification() {
        String admin = tokenFor(account(RoleCode.ADMIN));
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

        Account teacher = account(RoleCode.TEACHER);
        Map<String, Object> sessionBody = new java.util.HashMap<>();
        sessionBody.put("teacherPublicId", teacher.publicId);
        sessionBody.put("classPublicIds", List.of(classA));
        sessionBody.put("startsAt", "2026-09-10T08:00:00Z");
        sessionBody.put("endsAt", "2026-09-10T12:00:00Z");
        sessionBody.put("timeZoneId", "Europe/Paris");
        sessionBody.put("reason", "séance exceptionnelle");
        String sessionId = created(admin, "/api/v1/sessions", sessionBody).get("publicId").toString();
        post(admin, "/api/v1/sessions/" + sessionId + "/open", null, HttpStatus.NO_CONTENT);
        String checkpoint = created(admin, "/api/v1/sessions/" + sessionId + "/checkpoints",
                Map.of("label", "Matin", "type", "CUSTOM")).get("publicId").toString();
        post(admin, "/api/v1/sessions/" + sessionId + "/checkpoints/" + checkpoint + "/open", null,
                HttpStatus.NO_CONTENT);

        Account student = account(RoleCode.STUDENT);
        String profile = created(admin, "/api/v1/student-profiles", Map.of("userPublicId", student.publicId,
                "studentNumber", "ESIC-2026-" + code())).get("publicId").toString();
        created(admin, "/api/v1/enrollments", Map.of("studentProfilePublicId", profile,
                "classGroupPublicId", classA, "startDate", "2026-08-01"));

        String studentToken = tokenFor(student);
        String justificationId = created(studentToken, "/api/v1/me/attendance/justifications",
                Map.of("checkpointPublicId", checkpoint, "category", "MEDICAL", "comment", "certificat"))
                .get("publicId").toString();

        long studentInternalId = userAccountRepository.findByEmail(student.email).orElseThrow().getId();
        return new Ctx(admin, studentToken, studentInternalId, justificationId);
    }

    private void review(Ctx c, String decision, String reason) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("decision", decision);
        if (reason != null) {
            body.put("decisionReason", reason);
        }
        assertThat(rest.exchange(RequestEntity.post(URI.create(
                        "/api/v1/attendance/justifications/" + c.justificationId + "/review"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + c.adminToken)
                .contentType(MediaType.APPLICATION_JSON).body(body),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // --- jdbc probes ---

    private long attachmentRowCount(String justificationId) {
        Long n = jdbc.queryForObject("select count(*) from justification_attachment a "
                + "join attendance_justification j on j.id = a.justification_id "
                + "where j.public_id = UUID_TO_BIN(?)", Long.class, justificationId);
        return n == null ? 0 : n;
    }

    private long activeRowCount(String justificationId) {
        Long n = jdbc.queryForObject("select count(*) from justification_attachment a "
                + "join attendance_justification j on j.id = a.justification_id "
                + "where j.public_id = UUID_TO_BIN(?) and a.status <> 'DELETED'", Long.class, justificationId);
        return n == null ? 0 : n;
    }

    private String statusOf(String attachmentPublicId) {
        return jdbc.queryForObject("select status from justification_attachment where public_id = UUID_TO_BIN(?)",
                String.class, attachmentPublicId);
    }

    private String storageKeyOf(String attachmentPublicId) {
        return jdbc.queryForObject("select storage_key from justification_attachment where public_id = UUID_TO_BIN(?)",
                String.class, attachmentPublicId);
    }

    private boolean fileExists(String storageKey) {
        try (InputStream in = storage.open(storageKey)) {
            in.readAllBytes();
            return true;
        } catch (JustificationFileStorageException notFound) {
            if (notFound.kind() == JustificationFileStorageException.Kind.NOT_FOUND) {
                return false;
            }
            throw notFound;
        } catch (java.io.IOException io) {
            throw new IllegalStateException(io);
        }
    }

    private void backdateToPending(String attachmentPublicId) {
        jdbc.update("update justification_attachment set status='PENDING_STORAGE', stored_at=null, "
                + "created_at = (now(6) - interval 1 hour) where public_id = UUID_TO_BIN(?)", attachmentPublicId);
    }

    private long notificationCount(long recipientInternalId, String type) {
        Long n = jdbc.queryForObject("select count(*) from notification where recipient_user_id = ? and type = ?",
                Long.class, recipientInternalId, type);
        return n == null ? 0 : n;
    }

    private List<String> auditActionsFor(String justificationId) {
        return jdbc.queryForList("select action from audit_event where resource_public_id = UUID_TO_BIN(?)",
                String.class, justificationId);
    }

    // --- HTTP ---

    private Map<String, Object> uploadOk(String token, String justificationId, String filename,
                                         String contentType, byte[] bytes) {
        ResponseEntity<Map<String, Object>> r = multipart(token,
                "/api/v1/me/attendance/justifications/" + justificationId + "/attachment", filename, contentType, bytes);
        assertThat(r.getStatusCode()).as("upload -> " + r.getBody()).isEqualTo(HttpStatus.CREATED);
        return r.getBody();
    }

    private ResponseEntity<Map<String, Object>> multipart(String token, String path, String filename,
                                                          String contentType, byte[] bytes) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        form.add("file", new HttpEntity<>(resource, partHeaders));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        return rest.exchange(URI.create(path), HttpMethod.POST, new HttpEntity<>(form, headers),
                new ParameterizedTypeReference<>() {
                });
    }

    private ResponseEntity<byte[]> download(String token, String path) {
        return rest.exchange(RequestEntity.get(URI.create(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(), byte[].class);
    }

    private Map<String, Object> getMap(String token, String path) {
        ResponseEntity<Map<String, Object>> r = rest.exchange(RequestEntity.get(URI.create(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(r.getStatusCode()).as("GET " + path).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private Map<String, Object> created(String token, String path, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> r = rest.exchange(RequestEntity.post(URI.create(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(body),
                new ParameterizedTypeReference<>() {
                });
        assertThat(r.getStatusCode()).as("POST " + path + " -> " + r.getBody()).isEqualTo(HttpStatus.CREATED);
        return r.getBody();
    }

    private void post(String token, String path, Map<String, Object> body, HttpStatus expected) {
        RequestEntity.BodyBuilder b = RequestEntity.post(URI.create(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        RequestEntity<?> entity = body == null ? b.build() : b.contentType(MediaType.APPLICATION_JSON).body(body);
        assertThat(rest.exchange(entity, Void.class).getStatusCode()).as("POST " + path).isEqualTo(expected);
    }

    private record Account(String publicId, String email) {
    }

    private Account account(RoleCode... roles) {
        UserAccount a = new UserAccount("g1e-" + UUID.randomUUID() + "@esic-connect.test",
                "G1E", "Tester", AccountStatus.ACTIVE);
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
                        .body(Map.of("email", account.email, "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) body.get("accessToken");
    }

    private static String code() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static <T> List<T> bothConcurrently(Callable<T> a, Callable<T> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<T>> futures = pool.invokeAll(List.of(a, b));
            return List.of(futures.get(0).get(), futures.get(1).get());
        } finally {
            pool.shutdownNow();
        }
    }
}
