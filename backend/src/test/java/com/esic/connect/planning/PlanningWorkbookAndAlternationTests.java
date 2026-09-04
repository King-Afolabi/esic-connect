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
import com.esic.connect.support.AuthTestSupport;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 6 — import de planning au format Excel ({@code EF-PLAN-011}) et
 * avertissement d'alternance ({@code EF-PLAN-010}).
 *
 * <p>Deux exigences distinctes réunies ici parce qu'elles s'observent au
 * même endroit : la simulation d'import, avant toute écriture de séance.
 *
 * <p>L'avertissement d'alternance est <strong>non bloquant</strong>
 * (docs/02 §13.5) : un créneau tombant sur une période résolue en
 * entreprise doit être signalé, jamais refusé — l'établissement décide
 * parfois de convoquer une classe un jour d'entreprise.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlanningWorkbookAndAlternationTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    private static final List<String> HEADER = List.of(
            "slot_key", "session_date", "start_time", "end_time", "time_zone_id",
            "title", "teacher_public_id", "room_code");

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
    // EF-PLAN-011 — import Excel
    // ------------------------------------------------------------------

    @Test
    void unClasseurXlsxEstSimuleCommeUnCsv() throws IOException {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        byte[] workbook = workbook(List.of(
                List.of("S1", "2026-10-12", "09:00", "12:30", "Europe/Paris",
                        "Réseaux", teacher, room()),
                List.of("S2", "2026-10-12", "13:30", "17:00", "Europe/Paris",
                        "Systèmes", teacher, room())));

        Map<String, Object> job = uploadWorkbook("planning.xlsx", workbook, admin, classId).getBody();

        assertThat(job.get("status")).isEqualTo("SIMULATED");
        assertThat(job.get("totalRows")).isEqualTo(2);
        assertThat(job.get("errorRows")).isEqualTo(0);
        // Aucune séance avant publication (RG-030, EF-PLAN-002).
        assertThat(job.get("confirmable")).isEqualTo(true);
    }

    @Test
    void uneDateEtUneHeureSaisiesCommeCellulesExcelSontNormalisees() throws IOException {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();

        byte[] workbook = typedCellWorkbook(teacher, room());
        String jobId = (String) uploadWorkbook("typé.xlsx", workbook, admin, classId)
                .getBody().get("publicId");

        // Une date Excel est un nombre : sans conversion prévisible, la
        // ligne serait rejetée pour « 46 012 » au lieu du 12 octobre 2026.
        List<Map<String, Object>> rows = rows(jobId, admin);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("sessionDate")).isEqualTo("2026-10-12");
        assertThat(rows.get(0).get("startTime")).isEqualTo("09:00");
    }

    @Test
    void unFichierRenommeEnXlsxEstRefuse() {
        String admin = adminToken();
        String classId = classGroup(admin);

        ResponseEntity<Map<String, Object>> response = uploadWorkbook("faux.xlsx",
                "slot_key,session_date\nS1,2026-10-12\n".getBytes(StandardCharsets.UTF_8),
                admin, classId);

        // Type RÉEL dérivé du contenu, jamais du nom (docs/02 §13.4).
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    // ------------------------------------------------------------------
    // EF-PLAN-010 — avertissement de période en entreprise
    // ------------------------------------------------------------------

    @Test
    void unCreneauTombantUnJourEntrepriseEstSignaleSansBloquer() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        // Rythme 3 jours école / 2 jours entreprise : jeudi et vendredi en
        // entreprise. Le 15 octobre 2026 est un jeudi.
        assignThreeTwoPattern(admin, classId);

        String csv = header()
                + "S1,2026-10-15,09:00,12:30,Europe/Paris,Réseaux," + teacher + "," + room() + "\n";
        Map<String, Object> job = upload("planning.csv", csv, admin, classId).getBody();

        assertThat(issueCodes((String) job.get("publicId"), admin))
                .contains("PLAN_ALTERNATION_COMPANY_PERIOD");
        // Avertissement, pas erreur : la publication reste possible.
        assertThat(job.get("errorRows")).isEqualTo(0);
        assertThat(job.get("warningRows")).isEqualTo(1);
        assertThat(job.get("confirmable")).isEqualTo(true);
    }

    @Test
    void unCreneauUnJourEcoleNeProduitAucunAvertissement() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        assignThreeTwoPattern(admin, classId);

        // Le 12 octobre 2026 est un lundi — jour école du rythme.
        String csv = header()
                + "S1,2026-10-12,09:00,12:30,Europe/Paris,Réseaux," + teacher + "," + room() + "\n";
        Map<String, Object> job = upload("planning.csv", csv, admin, classId).getBody();

        assertThat(issueCodes((String) job.get("publicId"), admin))
                .doesNotContain("PLAN_ALTERNATION_COMPANY_PERIOD");
    }

    @Test
    void sansRythmeAffecteAucunAvertissementNEstProduit() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();

        String csv = header()
                + "S1,2026-10-15,09:00,12:30,Europe/Paris,Réseaux," + teacher + "," + room() + "\n";
        Map<String, Object> job = upload("planning.csv", csv, admin, classId).getBody();

        // Axe UNKNOWN : le produit ne présume pas d'une absence, il se tait.
        assertThat(issueCodes((String) job.get("publicId"), admin))
                .doesNotContain("PLAN_ALTERNATION_COMPANY_PERIOD");
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private void assignThreeTwoPattern(String admin, String classId) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String pattern = (String) created("/api/v1/alternation/patterns", Map.of(
                "code", "RY-" + suffix,
                "name", "3 jours école / 2 jours entreprise",
                "type", "THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY",
                "configuration", Map.of(
                        "schoolDays", List.of("MONDAY", "TUESDAY", "WEDNESDAY"),
                        "companyDays", List.of("THURSDAY", "FRIDAY"))), admin).get("publicId");
        created("/api/v1/alternation/class-assignments", Map.of(
                "classGroupPublicId", classId,
                "workStudyPatternPublicId", pattern,
                "cycleStartDate", LocalDate.of(2026, 9, 1).toString(),
                "validFrom", LocalDate.of(2026, 9, 1).toString()), admin);
    }

    private static String header() {
        return String.join(",", HEADER) + "\n";
    }

    private static String room() {
        return "R" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static byte[] workbook(List<List<String>> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Planning");
            writeRow(sheet, 0, HEADER);
            for (int index = 0; index < rows.size(); index++) {
                writeRow(sheet, index + 1, rows.get(index));
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** Date et heure saisies comme <em>valeurs</em> Excel, pas comme texte. */
    private static byte[] typedCellWorkbook(String teacher, String room) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Planning");
            writeRow(sheet, 0, HEADER);
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("S1");

            org.apache.poi.ss.usermodel.CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            org.apache.poi.ss.usermodel.Cell date = row.createCell(1);
            date.setCellValue(java.sql.Date.valueOf(LocalDate.of(2026, 10, 12)));
            date.setCellStyle(dateStyle);

            org.apache.poi.ss.usermodel.CellStyle timeStyle = workbook.createCellStyle();
            timeStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("hh:mm"));
            org.apache.poi.ss.usermodel.Cell start = row.createCell(2);
            start.setCellValue(9.0 / 24.0);
            start.setCellStyle(timeStyle);

            row.createCell(3).setCellValue("12:30");
            row.createCell(4).setCellValue("Europe/Paris");
            row.createCell(5).setCellValue("Réseaux");
            row.createCell(6).setCellValue(teacher);
            row.createCell(7).setCellValue(room);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static void writeRow(Sheet sheet, int index, List<String> values) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(index);
        for (int column = 0; column < values.size(); column++) {
            row.createCell(column).setCellValue(values.get(column));
        }
    }

    private List<String> issueCodes(String jobId, String token) {
        return rows(jobId, token).stream()
                .flatMap(row -> ((List<?>) row.get("issues")).stream())
                .map(issue -> (String) castMap(issue).get("errorCode"))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(String jobId, String token) {
        return (List<Map<String, Object>>) getMap(
                "/api/v1/planning-imports/" + jobId + "/rows?size=100", token).get("content");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private ResponseEntity<Map<String, Object>> uploadWorkbook(String fileName, byte[] bytes,
                                                               String token, String classId) {
        return post(fileName, bytes, token, classId,
                new MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    private ResponseEntity<Map<String, Object>> upload(String fileName, String csv, String token,
                                                       String classId) {
        return post(fileName, csv.getBytes(StandardCharsets.UTF_8), token, classId,
                new MediaType("text", "csv"));
    }

    private ResponseEntity<Map<String, Object>> post(String fileName, byte[] bytes, String token,
                                                     String classId, MediaType partType) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(partType);
        parts.add("file", new HttpEntity<>(resource, fileHeaders));
        parts.add("classGroupPublicId", classId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        return rest.exchange(URI.create("/api/v1/planning-imports"), HttpMethod.POST,
                new HttpEntity<>(parts, headers), new ParameterizedTypeReference<>() {
                });
    }

    private Map<String, Object> getMap(String path, String token) {
        return rest.exchange(RequestEntity.get(URI.create(path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
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

    private String classGroup(String admin) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String site = (String) created("/api/v1/sites", Map.of("code", "SITE-" + suffix,
                "name", "Campus", "timeZoneId", "Europe/Paris"), admin).get("publicId");
        String program = (String) created("/api/v1/programs", Map.of("code", "PRG-" + suffix,
                "name", "BTS SIO", "programType", "BTS"), admin).get("publicId");
        String level = (String) created("/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1-" + suffix, "name", "BTS 1", "sequenceNumber", 1), admin).get("publicId");
        String year = (String) created("/api/v1/academic-years", Map.of("code", "AY-" + suffix,
                "name", "2026-2027", "startDate", "2026-09-01", "endDate", "2027-08-31"), admin)
                .get("publicId");
        String promo = (String) created("/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P-" + suffix, "name", "Promotion"), admin)
                .get("publicId");
        return (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C-" + suffix,
                "name", "Classe"), admin).get("publicId");
    }

    private String teacherPublicId() {
        return persistUser(RoleCode.TEACHER).getPublicId().toString();
    }

    private String adminToken() {
        UserAccount account = persistUser(RoleCode.ADMIN);
        return AuthTestSupport.accessToken(rest, account.getEmail(), PASSWORD);
    }

    private UserAccount persistUser(RoleCode... roles) {
        UserAccount account = new UserAccount("pwa-" + UUID.randomUUID() + "@esic-connect.test",
                "Planning", "Testeur", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return account;
    }
}
