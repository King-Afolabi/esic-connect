package com.esic.connect.reporting;

import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exports de rapports en CSV, Excel et PDF (EF-REP-003, EF-REP-004,
 * EF-REP-005 ; AC-032).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReportExportFormatIntegrationTests {

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

    private S11TestFixture fx;
    private String admin;
    private String classCode;

    @BeforeEach
    void setUp() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        fx = new S11TestFixture(rest, userAccountRepository, userRoleRepository,
                roleRepository, passwordEncoder);
        admin = fx.tokenFor(fx.account(RoleCode.ADMIN));
        S11TestFixture.Chain chain = fx.academicChain(admin);
        classCode = chain.classACode();
        S11TestFixture.Account teacher = fx.account(RoleCode.TEACHER);
        S11TestFixture.Student student = fx.enrolledStudent(admin, chain.classA());
        String sessionId = fx.openSession(admin, teacher, chain.classA(), Instant.now().minusSeconds(1800));
        String checkpoint = fx.openCheckpoint(admin, sessionId, "MORNING_ARRIVAL");
        fx.validateAttendance(admin, fx.tokenFor(student.account()), sessionId, checkpoint);
    }

    @Test
    void csvRemainsTheDefaultAndKeepsItsBom() {
        ResponseEntity<byte[]> csv = fx.bytes(HttpMethod.GET,
                "/api/v1/attendance/reports/classes/export", admin);
        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(csv.getHeaders().getContentType().toString()).startsWith("text/csv");
        String content = new String(csv.getBody(), StandardCharsets.UTF_8);
        assertThat(content).startsWith("﻿");
        assertThat(content).contains(classCode);
    }

    @Test
    void excelExportIsARealXlsxContainingTheReport() throws Exception {
        ResponseEntity<byte[]> xlsx = fx.bytes(HttpMethod.GET,
                "/api/v1/attendance/reports/classes/export?format=xlsx", admin);
        assertThat(xlsx.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(xlsx.getHeaders().getContentType().toString())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(xlsx.getHeaders().getFirst("Content-Disposition")).contains(".xlsx");
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx.getBody()))) {
            StringBuilder flat = new StringBuilder();
            workbook.getSheetAt(0).forEach(row -> row.forEach(cell -> flat.append(cell.getStringCellValue())
                    .append('|')));
            assertThat(flat.toString()).contains(classCode);
            assertThat(flat.toString()).contains("Demi-journées attendues");
        }
    }

    @Test
    void pdfExportIsARealPdfCarryingTheVisualIdentity() throws Exception {
        ResponseEntity<byte[]> pdf = fx.bytes(HttpMethod.GET,
                "/api/v1/attendance/reports/students/export?format=pdf", admin);
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pdf.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        try (PDDocument document = Loader.loadPDF(pdf.getBody())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("ESIC");
            assertThat(text).contains("Rapport d'assiduité par apprenant");
            assertThat(text).contains("Document électronique");
        }
    }

    @Test
    void theThreeFormatsCarryTheSameColumns() throws Exception {
        String csv = new String(fx.bytes(HttpMethod.GET,
                "/api/v1/attendance/reports/classes/export?format=csv", admin).getBody(),
                StandardCharsets.UTF_8);
        byte[] xlsx = fx.bytes(HttpMethod.GET,
                "/api/v1/attendance/reports/classes/export?format=xlsx", admin).getBody();
        byte[] pdf = fx.bytes(HttpMethod.GET,
                "/api/v1/attendance/reports/classes/export?format=pdf", admin).getBody();

        // Une colonne présente dans un format et absente d'un autre se
        // lirait comme une différence de chiffres.
        for (String column : java.util.List.of("Classe", "Effectif", "Retards", "Taux de présence (%)")) {
            assertThat(csv).as("CSV contient « " + column + " »").contains(column);
        }
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            StringBuilder flat = new StringBuilder();
            workbook.getSheetAt(0).forEach(row -> row.forEach(cell -> flat.append(cell.getStringCellValue())
                    .append('|')));
            assertThat(flat.toString()).contains("Effectif").contains("Retards");
        }
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Effectif").contains("Retards");
        }
    }

    @Test
    void anUnknownFormatIsRefusedRatherThanSilentlyDowngraded() {
        ResponseEntity<Map<String, Object>> response = fx.exchange(HttpMethod.GET,
                "/api/v1/attendance/reports/classes/export?format=docx", null, admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("ATT_REPORT_INVALID_FILTER");
    }

    @Test
    void exportsAreRefusedToTeachersStudentsAndAnonymousCallers() {
        S11TestFixture.Account teacher = fx.account(RoleCode.TEACHER);
        S11TestFixture.Account student = fx.account(RoleCode.STUDENT);
        for (String format : java.util.List.of("csv", "xlsx", "pdf")) {
            String path = "/api/v1/attendance/reports/classes/export?format=" + format;
            assertThat(fx.exchange(HttpMethod.GET, path, null, fx.tokenFor(teacher)).getStatusCode())
                    .as(format + " refusé au formateur").isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(fx.exchange(HttpMethod.GET, path, null, fx.tokenFor(student)).getStatusCode())
                    .as(format + " refusé à l'apprenant").isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(fx.exchange(HttpMethod.GET, path, null, null).getStatusCode())
                    .as(format + " refusé sans jeton").isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
