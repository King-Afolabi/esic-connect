package com.esic.connect.attendance.internal;

import com.esic.connect.notification.internal.InvitationMailer;
import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.attendance.JustificationFileStorage;
import com.esic.connect.support.AuthTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Parcours COMPLET de dépôt d'une pièce jointe contre un
 * <strong>{@code clamd} réel</strong> (dettes T-04 et T-11 ;
 * EF-JUS-002 ; docs/02 §19.2).
 *
 * <p>Différence avec {@code JustificationAttachmentScanIntegrationTests} :
 * celui-ci pilote un double du port et vérifie ce que le produit fait
 * d'un verdict. Ici, aucun double — l'adaptateur ClamAV parle à un vrai
 * démon, qui reconnaît réellement la signature. C'est ce qui manquait
 * pour affirmer que l'analyse antivirus fonctionne.
 *
 * <p><strong>Conditionnel, et un test ignoré n'est pas un test réussi.</strong>
 * Sans démon joignable, la classe est ignorée et l'analyse reste
 * <em>non démontrée</em> — {@code docs/STATUS.md} doit alors le
 * dire.
 *
 * <pre>
 * docker compose --profile antivirus up -d clamav
 * docker exec esic-connect-clamav clamdscan --ping 1   # attendre « PONG »
 * cd backend &amp;&amp; ESIC_CLAMAV_REAL=1 ./mvnw test \
 *     -Dtest=JustificationAttachmentRealAntivirusIntegrationTests
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.attendance.antivirus.enabled=true",
                "app.attendance.antivirus.host=${ANTIVIRUS_HOST:127.0.0.1}",
                "app.attendance.antivirus.port=${ANTIVIRUS_PORT:3310}",
                "app.attendance.antivirus.timeout-millis=30000",
                // Quarantaine active : une pièce sans verdict exploitable
                // n'est pas téléchargeable. C'est le réglage qu'un
                // déploiement doté d'un analyseur doit retenir.
                "app.attendance.antivirus.required=true",
                "app.attendance.orphan-sweep-enabled=false"
        })
@ActiveProfiles("test")
class JustificationAttachmentRealAntivirusIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    private static final byte[] PDF =
            "%PDF-1.4\nfaux justificatif fictif ESIC\n%%EOF".getBytes(StandardCharsets.UTF_8);

    /**
     * Chaîne d'essai standard EICAR, <strong>inoffensive</strong> : une
     * suite de caractères imprimables que tout antivirus conforme
     * reconnaît, faite pour vérifier une chaîne de détection sans
     * manipuler de code malveillant. Scindée pour ne pas alarmer
     * l'antivirus du poste qui lit ce dépôt.
     */
    private static final String EICAR = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR"
            + "-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    private static final String HOST = System.getenv().getOrDefault("ANTIVIRUS_HOST", "127.0.0.1");
    private static final int PORT =
            Integer.parseInt(System.getenv().getOrDefault("ANTIVIRUS_PORT", "3310"));

    @BeforeAll
    static void requireRealDaemon() {
        assumeTrue("1".equals(System.getenv("ESIC_CLAMAV_REAL")),
                "clamd reel non demande (exporter ESIC_CLAMAV_REAL=1) — analyse NON demontree");
        assumeTrue(reachable(), "clamd injoignable sur " + HOST + ':' + PORT);
    }

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
    @Autowired
    private JustificationFileStorage storage;
    @Autowired
    private com.esic.connect.attendance.AttachmentMalwareScanner scanner;

    @BeforeEach
    void setUp() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    @DisplayName("l'adaptateur réellement câblé est ClamAV, et il se déclare actif")
    void theWiredScannerIsTheRealClamAvAdapter() {
        // Sans ce contrôle, les deux tests suivants pourraient passer avec
        // un analyseur inactif renvoyant NOT_SCANNED, et l'on croirait
        // avoir démontré ce qu'on n'a pas démontré.
        assertThat(scanner).isInstanceOf(ClamAvMalwareScanner.class);
        assertThat(scanner.isActive()).isTrue();
    }

    @Test
    @DisplayName("T-04 : une pièce saine est acceptée, marquée CLEAN et téléchargeable")
    void aCleanAttachmentIsAcceptedAndDownloadable() {
        Ctx c = pendingJustification();

        Map<String, Object> meta = uploadOk(c.studentToken(), c.justificationId(),
                "certificat.pdf", "application/pdf", PDF);

        assertThat(meta.get("scanStatus")).isEqualTo("CLEAN");
        assertThat(meta.get("scannedAt")).isNotNull();
        ResponseEntity<byte[]> served = download(c.studentToken(), c.justificationId());
        assertThat(served.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(served.getBody()).isEqualTo(PDF);
    }

    @Test
    @DisplayName("T-11 : le clamd réel détecte EICAR — mais seulement en fichier entier")
    void theRealDaemonDetectsEicarOnlyAsAWholeFile() {
        byte[] pure = EICAR.getBytes(StandardCharsets.US_ASCII);
        byte[] wrappedInPdf = ("%PDF-1.4\n" + EICAR + "\n%%EOF").getBytes(StandardCharsets.US_ASCII);

        var onPure = scanner.scan(new java.io.ByteArrayInputStream(pure));
        var onWrapped = scanner.scan(new java.io.ByteArrayInputStream(wrappedInPdf));

        // Le démon réel reconnaît bien la signature : la chaîne de
        // détection fonctionne de bout en bout.
        assertThat(onPure.verdict())
                .isEqualTo(com.esic.connect.attendance.AttachmentMalwareScanner.Verdict.INFECTED);
        assertThat(onPure.signature()).containsIgnoringCase("eicar");

        // MAIS la signature EICAR est ancrée au fichier ENTIER, par
        // construction : elle existe pour vérifier une chaîne d'analyse,
        // pas pour être dissimulée dans un conteneur. Enveloppée dans un
        // PDF, elle n'est plus reconnue.
        //
        // Ce constat n'est pas une lacune du produit, c'est une propriété
        // de la chaîne d'essai — et il rappelle pourquoi l'antivirus ne
        // remplace jamais les contrôles structurels : « un antivirus
        // reconnaît ce qu'il connaît ; il ne rend pas un format dangereux
        // inoffensif » (ClamAvMalwareScanner).
        assertThat(onWrapped.verdict())
                .isEqualTo(com.esic.connect.attendance.AttachmentMalwareScanner.Verdict.CLEAN);
    }

    @Test
    @DisplayName("un fichier EICAR déposé tel quel est refusé AVANT l'antivirus, par le contrôle de contenu")
    void aRawEicarUploadIsStoppedByTheStructuralControlFirst() {
        Ctx c = pendingJustification();

        // Nom et type déclarés d'un PDF, contenu réel : la chaîne EICAR.
        // Le type est re-dérivé du CONTENU (magic bytes) : le fichier est
        // écarté avant même d'atteindre l'analyseur. L'ordre compte —
        // c'est ce contrôle, et non l'antivirus, qui protège des formats
        // qu'aucune signature ne couvre.
        ResponseEntity<Map<String, Object>> refused = multipart(c.studentToken(),
                "/api/v1/me/attendance/justifications/" + c.justificationId() + "/attachment",
                "certificat.pdf", "application/pdf", EICAR.getBytes(StandardCharsets.US_ASCII));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(refused.getBody().get("code")).isEqualTo("ATT_ATTACHMENT_UNSUPPORTED");
        assertThat(attachmentRowCount(c.justificationId())).isZero();
    }

    @Test
    @DisplayName("l'API annonce que l'analyse antivirus est active")
    void theApiAdvertisesAnActiveScanner() {
        String token = tokenFor(account(RoleCode.STUDENT));
        ResponseEntity<Map<String, Object>> status = rest.exchange(
                RequestEntity.get(URI.create("/api/v1/attendance/antivirus/status"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<>() {
                });

        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody().get("active")).isEqualTo(true);
        assertThat(status.getBody().get("quarantineRequired")).isEqualTo(true);
    }

    private static boolean reachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 2_000);
            return true;
        } catch (IOException unreachable) {
            return false;
        }
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
        UserAccount account = new UserAccount("scan-reel-" + UUID.randomUUID() + "@esic-connect.test",
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
