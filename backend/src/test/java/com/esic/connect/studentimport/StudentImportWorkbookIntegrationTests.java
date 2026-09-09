package com.esic.connect.studentimport;

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
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Import de classeur Excel, classeur multifeuille et correction de ligne
 * (EF-IMP-003, EF-IMP-004, EF-IMP-006 ; critère IMP-STU-05).
 *
 * <p>Les classeurs sont <strong>construits par le test</strong>, jamais
 * lus depuis un fichier d'exemple : ce qui est vérifié est le
 * comportement du produit face à une structure donnée, pas la capacité à
 * relire un fichier figé dans le dépôt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StudentImportWorkbookIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    private static final List<String> HEADER = List.of(
            "last_name", "first_name", "email", "formation_code", "class_code", "academic_year");

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
    private TestRestTemplate rest;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------------
    // EF-IMP-003 — classeur `.xlsx`
    // ------------------------------------------------------------------

    @Test
    void unClasseurSimpleEstAnalyseCommeUnCsv() throws IOException {
        String admin = tokenFor(RoleCode.ADMIN);
        Chain chain = academicChain(admin);
        byte[] workbook = workbook(Map.of("Classe", List.of(
                row("alice@esic-connect.test", chain),
                row("bruno@esic-connect.test", chain))));

        Map<String, Object> job = uploadWorkbook("apprenants.xlsx", workbook, admin).getBody();

        assertThat(summary(job).get("total")).isEqualTo(2);
        assertThat(summary(job).get("error")).isEqualTo(0);
        assertThat(job.get("confirmable")).isEqualTo(true);
        // Aucune écriture métier avant confirmation (RG-030).
        assertThat(job.get("status")).isEqualTo("SIMULATED");
    }

    @Test
    void unNumeroEtudiantSaisiCommeNombreNeDevientPasUnDecimal() throws IOException {
        String admin = tokenFor(RoleCode.ADMIN);
        Chain chain = academicChain(admin);
        byte[] workbook = numericStudentNumberWorkbook(chain);

        String jobId = (String) uploadWorkbook("numeros.xlsx", workbook, admin).getBody().get("publicId");
        List<Map<String, Object>> rows = rows(jobId, admin);

        // Sans conversion prévisible, Excel rendrait « 20260001.0 », que la
        // validation refuserait pour une raison incompréhensible.
        assertThat(rows.get(0).get("studentNumber")).isEqualTo("20260001");
    }

    @Test
    void unFichierRenommeEnXlsxEstRefuse() {
        String admin = tokenFor(RoleCode.ADMIN);

        ResponseEntity<Map<String, Object>> response = uploadWorkbook("faux.xlsx",
                "last_name,first_name\nNom,Prenom\n".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                admin);

        // Le type RÉEL est dérivé du contenu, jamais du nom.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    // ------------------------------------------------------------------
    // EF-IMP-004 — classeur multifeuille (critère IMP-STU-05)
    // ------------------------------------------------------------------

    @Test
    void unClasseurDeTroisFeuillesRattacheChaqueApprenantASaClasse() throws IOException {
        String admin = tokenFor(RoleCode.ADMIN);
        Chain first = academicChain(admin);
        Chain second = academicChain(admin);
        Chain third = academicChain(admin);
        byte[] workbook = workbook(new java.util.LinkedHashMap<>(Map.of(
                "BTS1", List.of(row("bts1@esic-connect.test", first)),
                "BTS2", List.of(row("bts2@esic-connect.test", second)),
                "BACH3", List.of(row("bach3@esic-connect.test", third)))));

        ResponseEntity<Map<String, Object>> upload =
                uploadWorkbook("trois-classes.xlsx", workbook, admin);
        assertThat(upload.getStatusCode()).as("téléversement -> %s", upload.getBody())
                .isEqualTo(HttpStatus.CREATED);
        List<Map<String, Object>> rows = rows((String) upload.getBody().get("publicId"), admin);

        assertThat(rows).hasSize(3);
        // IMP-STU-05 : les trois classes sont correctement rattachées. Les
        // codes sont normalisés en majuscules par l'import, comme pour un CSV.
        assertThat(rows).extracting(row -> String.valueOf(row.get("classCode")))
                .containsExactlyInAnyOrder(
                        first.classCode().toUpperCase(java.util.Locale.ROOT),
                        second.classCode().toUpperCase(java.util.Locale.ROOT),
                        third.classCode().toUpperCase(java.util.Locale.ROOT));
        // Chaque ligne sait de quelle feuille elle vient (docs/02 §10.7).
        assertThat(rows).extracting(row -> row.get("sheetName"))
                .containsExactlyInAnyOrder("BTS1", "BTS2", "BACH3");
    }

    @Test
    void uneFeuilleAuxEnTetesDifferentsEstSignaleeEtNonLueDeTravers() throws IOException {
        String admin = tokenFor(RoleCode.ADMIN);
        Chain chain = academicChain(admin);
        byte[] workbook = mismatchedWorkbook(chain);

        Map<String, Object> job = uploadWorkbook("melange.xlsx", workbook, admin).getBody();

        // La feuille divergente est écartée, pas devinée : la lire avec le
        // mauvais mapping produirait des données fausses et plausibles.
        assertThat(summary(job).get("total")).isEqualTo(1);
        assertThat(job.toString()).contains("WORKBOOK_SHEET_IGNORED");
    }

    // ------------------------------------------------------------------
    // EF-IMP-006 — correction avant confirmation
    // ------------------------------------------------------------------

    @Test
    void corrigerLaDerniereLigneFautiveRendLeTravailConfirmable() throws IOException {
        String admin = tokenFor(RoleCode.ADMIN);
        Chain chain = academicChain(admin);
        byte[] workbook = workbook(Map.of("Classe", List.of(
                List.of("Nom", "Prenom", "correct@esic-connect.test", chain.programCode(),
                        chain.classCode(), chain.yearCode()),
                List.of("Nom", "Prenom", "adresse-invalide", chain.programCode(),
                        chain.classCode(), chain.yearCode()))));

        Map<String, Object> job = uploadWorkbook("a-corriger.xlsx", workbook, admin).getBody();
        String jobId = (String) job.get("publicId");
        assertThat(summary(job).get("error")).isEqualTo(1);
        assertThat(job.get("confirmable")).isEqualTo(false);

        Map<String, Object> faulty = rows(jobId, admin).stream()
                .filter(row -> "ERROR".equals(row.get("rowStatus")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ligne en erreur attendue"));

        ResponseEntity<Map<String, Object>> corrected = correct(jobId, (String) faulty.get("publicId"),
                Map.of("email", "corrige@esic-connect.test"), admin);

        assertThat(corrected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(corrected.getBody().get("rowStatus")).isNotEqualTo("ERROR");
        assertThat(corrected.getBody().get("email")).isEqualTo("corrige@esic-connect.test");

        // Le travail redevient confirmable sans réimport (docs/02 §13.6).
        Map<String, Object> reloaded = getMap("/api/v1/student-imports/" + jobId, admin);
        assertThat(summary(reloaded).get("error")).isEqualTo(0);
        assertThat(reloaded.get("confirmable")).isEqualTo(true);
    }

    @Test
    void unChampNonCorrigeableEstRefuse() throws IOException {
        String admin = tokenFor(RoleCode.ADMIN);
        Chain chain = academicChain(admin);
        String jobId = (String) uploadWorkbook("x.xlsx",
                workbook(Map.of("Classe", List.of(row("a@esic-connect.test", chain)))), admin)
                .getBody().get("publicId");
        String rowId = (String) rows(jobId, admin).get(0).get("publicId");

        ResponseEntity<Map<String, Object>> refused = correct(jobId, rowId,
                Map.of("row_status", "VALID"), admin);

        // La liste des champs corrigeables est fermée : on ne devine pas.
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("code")).isEqualTo("IMP_CORRECTION_UNKNOWN_FIELD");
    }

    @Test
    void uneDateCorrigeeIllisibleEstRefusee() throws IOException {
        String admin = tokenFor(RoleCode.ADMIN);
        Chain chain = academicChain(admin);
        String jobId = (String) uploadWorkbook("x.xlsx",
                workbook(Map.of("Classe", List.of(row("b@esic-connect.test", chain)))), admin)
                .getBody().get("publicId");
        String rowId = (String) rows(jobId, admin).get(0).get("publicId");

        ResponseEntity<Map<String, Object>> refused = correct(jobId, rowId,
                Map.of("birth_date", "32/13/2000"), admin);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("code")).isEqualTo("IMP_CORRECTION_INVALID_VALUE");
    }

    @Test
    void uneLigneDUnAutreTravailNEstPasCorrigeable() throws IOException {
        String admin = tokenFor(RoleCode.ADMIN);
        Chain chain = academicChain(admin);
        String firstJob = (String) uploadWorkbook("un.xlsx",
                workbook(Map.of("Classe", List.of(row("un@esic-connect.test", chain)))), admin)
                .getBody().get("publicId");
        String secondJob = (String) uploadWorkbook("deux.xlsx",
                workbook(Map.of("Classe", List.of(row("deux@esic-connect.test", chain)))), admin)
                .getBody().get("publicId");
        String foreignRow = (String) rows(secondJob, admin).get(0).get("publicId");

        assertThat(correct(firstJob, foreignRow, Map.of("first_name", "Autre"), admin)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void laCorrectionExigeUnRoleAutorise() throws IOException {
        String admin = tokenFor(RoleCode.ADMIN);
        String teacher = tokenFor(RoleCode.TEACHER);
        Chain chain = academicChain(admin);
        String jobId = (String) uploadWorkbook("x.xlsx",
                workbook(Map.of("Classe", List.of(row("c@esic-connect.test", chain)))), admin)
                .getBody().get("publicId");
        String rowId = (String) rows(jobId, admin).get(0).get("publicId");

        assertThat(correct(jobId, rowId, Map.of("first_name", "X"), teacher).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------
    // Outillage : construction de classeurs
    // ------------------------------------------------------------------

    private static List<String> row(String email, Chain chain) {
        return List.of("Nom", "Prenom", email, chain.programCode(), chain.classCode(),
                chain.yearCode());
    }

    /** Classeur avec un en-tête identique sur toutes les feuilles. */
    private static byte[] workbook(Map<String, List<List<String>>> sheets) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            sheets.forEach((name, rows) -> {
                Sheet sheet = workbook.createSheet(name);
                writeRow(sheet, 0, HEADER);
                for (int index = 0; index < rows.size(); index++) {
                    writeRow(sheet, index + 1, rows.get(index));
                }
            });
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** Deuxième feuille aux en-têtes différents — doit être écartée. */
    private static byte[] mismatchedWorkbook(Chain chain) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet valid = workbook.createSheet("Conforme");
            writeRow(valid, 0, HEADER);
            writeRow(valid, 1, row("conforme@esic-connect.test", chain));

            Sheet other = workbook.createSheet("Divergente");
            writeRow(other, 0, List.of("nom_complet", "adresse"));
            writeRow(other, 1, List.of("Nom Prenom", "autre@esic-connect.test"));

            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** Numéro étudiant saisi comme NOMBRE, cas courant dans un vrai fichier. */
    private static byte[] numericStudentNumberWorkbook(Chain chain) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Classe");
            List<String> header = new java.util.ArrayList<>(HEADER);
            header.add("student_number");
            writeRow(sheet, 0, header);

            Row row = sheet.createRow(1);
            List<String> values = row("numero@esic-connect.test", chain);
            for (int index = 0; index < values.size(); index++) {
                row.createCell(index).setCellValue(values.get(index));
            }
            row.createCell(values.size()).setCellValue(20260001d);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static void writeRow(Sheet sheet, int rowIndex, List<String> values) {
        Row row = sheet.createRow(rowIndex);
        for (int index = 0; index < values.size(); index++) {
            row.createCell(index).setCellValue(values.get(index));
        }
    }

    // ------------------------------------------------------------------
    // Outillage HTTP
    // ------------------------------------------------------------------

    private ResponseEntity<Map<String, Object>> uploadWorkbook(String fileName, byte[] content,
                                                               String token) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        parts.add("file", new HttpEntity<>(resource, fileHeaders));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        return rest.exchange(URI.create("/api/v1/student-imports"), HttpMethod.POST,
                new HttpEntity<>(parts, headers), new ParameterizedTypeReference<>() {
                });
    }

    private ResponseEntity<Map<String, Object>> correct(String jobId, String rowId,
                                                        Map<String, String> corrections, String token) {
        return rest.exchange(RequestEntity
                        .post(URI.create("/api/v1/student-imports/" + jobId + "/rows/" + rowId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(corrections),
                new ParameterizedTypeReference<>() {
                });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(String jobId, String token) {
        Map<String, Object> page = getMap("/api/v1/student-imports/" + jobId + "/rows?size=100", token);
        return (List<Map<String, Object>>) page.get("content");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> summary(Map<String, Object> job) {
        return (Map<String, Object>) job.get("summary");
    }

    private Map<String, Object> getMap(String path, String token) {
        return rest.exchange(RequestEntity.get(URI.create(path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
    }

    private record Chain(String programCode, String classCode, String yearCode) {
    }

    private Chain academicChain(String admin) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String site = (String) created("/api/v1/sites", Map.of("code", "SITE-" + suffix,
                "name", "Campus", "timeZoneId", "Europe/Paris"), admin).get("publicId");
        String programCode = "PRG-" + suffix;
        String program = (String) created("/api/v1/programs", Map.of("code", programCode,
                "name", "BTS SIO", "programType", "BTS"), admin).get("publicId");
        String level = (String) created("/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1-" + suffix, "name", "BTS 1", "sequenceNumber", 1), admin).get("publicId");
        String yearCode = "AY-" + suffix;
        String year = (String) created("/api/v1/academic-years", Map.of("code", yearCode,
                "name", "2026-2027", "startDate", "2026-09-01", "endDate", "2027-08-31"), admin)
                .get("publicId");
        String promo = (String) created("/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P-" + suffix, "name", "Promotion"), admin)
                .get("publicId");
        String classCode = "C-" + suffix;
        created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", classCode,
                "name", "Classe"), admin);
        return new Chain(programCode, classCode, yearCode);
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                RequestEntity.post(URI.create(path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).body(body),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private String tokenFor(RoleCode... roles) {
        UserAccount account = new UserAccount("wb-" + UUID.randomUUID() + "@esic-connect.test",
                "Classeur", "Testeur", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return AuthTestSupport.accessToken(rest, account.getEmail(), PASSWORD);
    }
}
