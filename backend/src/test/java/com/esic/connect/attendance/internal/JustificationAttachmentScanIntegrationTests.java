package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttachmentMalwareScanner;
import com.esic.connect.attendance.JustificationFileStorage;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Analyse antivirus des pièces jointes et balayage des orphelins
 * (EF-JUS-002 ; docs/02 §19.2 et §19.5).
 *
 * <p>Le contexte est démarré avec {@code antivirus.required=true} : la
 * quarantaine du cahier n'a de sens que sous cette exigence, et c'est le
 * comportement d'un déploiement réel.
 *
 * <p>L'analyseur est un double dont le verdict est piloté par le test :
 * aucun ClamAV n'est démarré, et aucune signature réelle n'est utilisée —
 * cette classe vérifie le <em>contrat du port</em> et ce que le produit en
 * fait, pas la qualité de détection d'un produit tiers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.attendance.antivirus.required=true",
                "app.attendance.orphan-sweep-enabled=true"})
@ActiveProfiles("test")
class JustificationAttachmentScanIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    private static final byte[] PDF =
            "%PDF-1.4\nfaux justificatif fictif ESIC\n%%EOF".getBytes(StandardCharsets.UTF_8);

    @TestConfiguration
    static class ScannerConfig {

        static volatile AttachmentMalwareScanner.Result verdict =
                AttachmentMalwareScanner.Result.clean();
        static volatile boolean active = true;

        @Bean
        @Primary
        AttachmentMalwareScanner stubScanner() {
            return new AttachmentMalwareScanner() {
                @Override
                public boolean isActive() {
                    return active;
                }

                @Override
                public Result scan(InputStream content) {
                    try {
                        // Le flux DOIT être consommé : un adaptateur réel le
                        // consomme, et un test qui ne le fait pas laisserait
                        // passer une régression de gestion de flux.
                        content.readAllBytes();
                    } catch (java.io.IOException io) {
                        throw new IllegalStateException(io);
                    }
                    return verdict;
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
    private JustificationOrphanSweepService orphanSweep;

    @BeforeEach
    void setUp() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        ScannerConfig.active = true;
        ScannerConfig.verdict = AttachmentMalwareScanner.Result.clean();
    }

    // ------------------------------------------------------------------
    // Analyse
    // ------------------------------------------------------------------

    @Test
    void unePieceAnalyseeEtSainePorteLeVerdictCleanEtEstTelechargeable() {
        Ctx c = pendingJustification();

        Map<String, Object> meta = uploadOk(c.studentToken, c.justificationId, "certificat.pdf",
                "application/pdf", PDF);

        assertThat(meta.get("scanStatus")).isEqualTo("CLEAN");
        assertThat(meta.get("scannedAt")).isNotNull();
        ResponseEntity<byte[]> served = download(c.studentToken, c.justificationId);
        assertThat(served.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(served.getBody()).isEqualTo(PDF);
    }

    @Test
    void unePieceInfecteeEstRefuseeEtRienNestEcrit() {
        Ctx c = pendingJustification();
        ScannerConfig.verdict = AttachmentMalwareScanner.Result.infected("Eicar-Test-Signature");

        ResponseEntity<Map<String, Object>> refused = multipart(c.studentToken,
                "/api/v1/me/attendance/justifications/" + c.justificationId + "/attachment",
                "verole.pdf", "application/pdf", PDF);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getBody()).containsEntry("code", "ATT_ATTACHMENT_INFECTED");
        // Ni ligne, ni fichier : le contenu n'a jamais atteint le disque.
        assertThat(attachmentRowCount(c.justificationId)).isZero();
        // Le message ne divulgue pas le nom de la signature — inutile à
        // l'apprenant, et c'est une information sur l'outillage.
        assertThat(String.valueOf(refused.getBody().get("message")))
                .doesNotContain("Eicar-Test-Signature");
    }

    @Test
    void unePieceRefuseeLaisseLaPlaceAUnRedepotSain() {
        Ctx c = pendingJustification();
        ScannerConfig.verdict = AttachmentMalwareScanner.Result.infected("Eicar-Test-Signature");
        multipart(c.studentToken, "/api/v1/me/attendance/justifications/" + c.justificationId
                + "/attachment", "verole.pdf", "application/pdf", PDF);

        ScannerConfig.verdict = AttachmentMalwareScanner.Result.clean();
        Map<String, Object> meta = uploadOk(c.studentToken, c.justificationId, "propre.pdf",
                "application/pdf", PDF);

        assertThat(meta.get("scanStatus")).isEqualTo("CLEAN");
    }

    @Test
    void unAnalyseurMuetMetLaPieceEnQuarantaine() {
        Ctx c = pendingJustification();
        ScannerConfig.verdict = AttachmentMalwareScanner.Result.unavailable();

        // Le dépôt réussit — refuser perdrait le justificatif de
        // l'apprenant pour une panne qui ne le concerne pas.
        Map<String, Object> meta = uploadOk(c.studentToken, c.justificationId, "certificat.pdf",
                "application/pdf", PDF);
        assertThat(meta.get("scanStatus")).isEqualTo("UNAVAILABLE");

        // ... mais le contenu n'est pas remis tant que rien n'a conclu.
        ResponseEntity<Map<String, Object>> refused = downloadError(c.studentToken, c.justificationId);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody()).containsEntry("code", "ATT_ATTACHMENT_QUARANTINED");
    }

    @Test
    void sansAnalyseurLeProduitDeclareNotScannedEtNePretendPasQueLaPieceEstSaine() {
        Ctx c = pendingJustification();
        ScannerConfig.active = false;

        Map<String, Object> meta = uploadOk(c.studentToken, c.justificationId, "certificat.pdf",
                "application/pdf", PDF);

        assertThat(meta.get("scanStatus")).isEqualTo("NOT_SCANNED");
        assertThat(meta.get("scannedAt")).isNull();
        // `required=true` : sans verdict, pas de remise du contenu.
        assertThat(downloadError(c.studentToken, c.justificationId).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------------
    // Balayage des orphelins (docs/02 §19.5)
    // ------------------------------------------------------------------

    @Test
    void leBalayageSupprimeUnFichierQueAucuneLigneNeReference() {
        String orphan = storage.newStorageKey();
        storage.store(orphan, new JustificationFileStorage.PendingUpload(
                new ByteArrayInputStream(PDF), 5_242_880L));
        assertThat(fileExists(orphan)).isTrue();

        orphanSweep.sweepOnce();

        assertThat(fileExists(orphan)).isFalse();
    }

    @Test
    void leBalayageNeToucheJamaisUnFichierEncoreReference() {
        Ctx c = pendingJustification();
        Map<String, Object> meta = uploadOk(c.studentToken, c.justificationId, "certificat.pdf",
                "application/pdf", PDF);
        String key = storageKeyOf((String) meta.get("publicId"));

        orphanSweep.sweepOnce();

        assertThat(fileExists(key)).isTrue();
        assertThat(download(c.studentToken, c.justificationId).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void leBalayageSupprimeLeFichierDUneLigneRetiree() {
        Ctx c = pendingJustification();
        Map<String, Object> meta = uploadOk(c.studentToken, c.justificationId, "certificat.pdf",
                "application/pdf", PDF);
        String key = storageKeyOf((String) meta.get("publicId"));
        // Ligne DELETED sans suppression du fichier : exactement le résidu
        // que le balayage doit rattraper.
        jdbc.update("update justification_attachment set status='DELETED', deleted_at=now(6) "
                + "where public_id = UUID_TO_BIN(?)", meta.get("publicId"));
        assertThat(fileExists(key)).isTrue();

        orphanSweep.sweepOnce();

        assertThat(fileExists(key)).isFalse();
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private record Ctx(String adminToken, String studentToken, String justificationId) {
    }

    private Ctx pendingJustification() {
        String admin = tokenFor(account(RoleCode.ADMIN));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String site = created(admin, "/api/v1/sites", Map.of("code", "SITE-" + suffix,
                "name", "Campus", "timeZoneId", "Europe/Paris")).get("publicId").toString();
        String program = created(admin, "/api/v1/programs", Map.of("code", "PRG-" + suffix,
                "name", "BTS SIO", "programType", "BTS")).get("publicId").toString();
        String level = created(admin, "/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1-" + suffix, "name", "BTS 1", "sequenceNumber", 1)).get("publicId").toString();
        String year = created(admin, "/api/v1/academic-years", Map.of("code", "AY-" + suffix,
                "name", "2026-2027", "startDate", "2026-09-01", "endDate", "2027-08-31"))
                .get("publicId").toString();
        String promo = created(admin, "/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P-" + suffix, "name", "Promotion"))
                .get("publicId").toString();
        String classA = created(admin, "/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C-" + suffix,
                "name", "Classe")).get("publicId").toString();

        Account teacher = account(RoleCode.TEACHER);
        Map<String, Object> sessionBody = new java.util.HashMap<>();
        sessionBody.put("teacherPublicId", teacher.publicId);
        sessionBody.put("classPublicIds", List.of(classA));
        sessionBody.put("startsAt", "2026-09-10T08:00:00Z");
        sessionBody.put("endsAt", "2026-09-10T12:00:00Z");
        sessionBody.put("timeZoneId", "Europe/Paris");
        sessionBody.put("reason", "séance exceptionnelle");
        String sessionId = created(admin, "/api/v1/sessions", sessionBody).get("publicId").toString();
        post(admin, "/api/v1/sessions/" + sessionId + "/open", HttpStatus.NO_CONTENT);
        String checkpoint = created(admin, "/api/v1/sessions/" + sessionId + "/checkpoints",
                Map.of("label", "Matin", "type", "CUSTOM")).get("publicId").toString();
        post(admin, "/api/v1/sessions/" + sessionId + "/checkpoints/" + checkpoint + "/open",
                HttpStatus.NO_CONTENT);

        Account student = account(RoleCode.STUDENT);
        String profile = created(admin, "/api/v1/student-profiles", Map.of(
                "userPublicId", student.publicId,
                "studentNumber", "ESIC-2026-" + suffix)).get("publicId").toString();
        created(admin, "/api/v1/enrollments", Map.of("studentUserPublicId", student.publicId,
                "classGroupPublicId", classA, "startDate", "2026-08-01"));

        String studentToken = tokenFor(student);
        String justificationId = created(studentToken, "/api/v1/me/attendance/justifications",
                Map.of("checkpointPublicId", checkpoint, "category", "MEDICAL", "comment", "certificat"))
                .get("publicId").toString();
        return new Ctx(admin, studentToken, justificationId);
    }

    // --- sondes ---

    private long attachmentRowCount(String justificationId) {
        Long n = jdbc.queryForObject("select count(*) from justification_attachment a "
                + "join attendance_justification j on j.id = a.justification_id "
                + "where j.public_id = UUID_TO_BIN(?)", Long.class, justificationId);
        return n == null ? 0 : n;
    }

    private String storageKeyOf(String attachmentPublicId) {
        return jdbc.queryForObject(
                "select storage_key from justification_attachment where public_id = UUID_TO_BIN(?)",
                String.class, attachmentPublicId);
    }

    private boolean fileExists(String storageKey) {
        try (InputStream in = storage.open(storageKey)) {
            in.readAllBytes();
            return true;
        } catch (com.esic.connect.attendance.JustificationFileStorageException notFound) {
            if (notFound.kind()
                    == com.esic.connect.attendance.JustificationFileStorageException.Kind.NOT_FOUND) {
                return false;
            }
            throw notFound;
        } catch (java.io.IOException io) {
            throw new IllegalStateException(io);
        }
    }

    // --- HTTP ---

    private Map<String, Object> uploadOk(String token, String justificationId, String filename,
                                         String contentType, byte[] bytes) {
        ResponseEntity<Map<String, Object>> r = multipart(token,
                "/api/v1/me/attendance/justifications/" + justificationId + "/attachment",
                filename, contentType, bytes);
        assertThat(r.getStatusCode()).as("upload -> " + r.getBody()).isEqualTo(HttpStatus.CREATED);
        return r.getBody();
    }

    /**
     * Téléchargement typé {@code byte[]} : un succès renvoie le contenu du
     * fichier, pas du JSON — le lire en {@code Map} ferait échouer le cas
     * nominal pour une raison qui n'a rien à voir avec ce qui est vérifié.
     */
    private ResponseEntity<byte[]> download(String token, String justificationId) {
        return rest.exchange(RequestEntity.get(URI.create(
                        "/api/v1/me/attendance/justifications/" + justificationId
                                + "/attachment/download"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(), byte[].class);
    }

    /** Même route, lue comme une erreur JSON — pour les cas de refus. */
    private ResponseEntity<Map<String, Object>> downloadError(String token, String justificationId) {
        return rest.exchange(RequestEntity.get(URI.create(
                        "/api/v1/me/attendance/justifications/" + justificationId
                                + "/attachment/download"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<>() {
                });
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

    private Map<String, Object> created(String token, String path, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> r = rest.exchange(
                RequestEntity.post(URI.create(path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).body(body),
                new ParameterizedTypeReference<>() {
                });
        assertThat(r.getStatusCode()).as("POST %s -> %s", path, r.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return r.getBody();
    }

    private void post(String token, String path, HttpStatus expected) {
        ResponseEntity<Void> r = rest.exchange(RequestEntity.post(URI.create(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(), Void.class);
        assertThat(r.getStatusCode()).as("POST %s", path).isEqualTo(expected);
    }

    private record Account(String publicId, String email) {
    }

    private Account account(RoleCode... roles) {
        UserAccount account = new UserAccount("scan-" + UUID.randomUUID() + "@esic-connect.test",
                "Analyse", "Testeur", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return new Account(account.getPublicId().toString(), account.getEmail());
    }

    private String tokenFor(Account account) {
        return AuthTestSupport.accessToken(rest, account.email(), PASSWORD);
    }
}
