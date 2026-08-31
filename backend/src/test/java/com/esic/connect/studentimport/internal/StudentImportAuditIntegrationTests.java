package com.esic.connect.studentimport.internal;

import com.esic.connect.identity.internal.AccountStatus;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Audit de l'import (CP7 ; rapport §4.4, §10, §14.3, §14.4) :
 * {@code StudentImportAuditListener} (AFTER_COMMIT + REQUIRES_NEW).
 *
 * <ul>
 *   <li>simulation → exactement une ligne {@code STUDENT_IMPORT_SIMULATED} ;</li>
 *   <li>confirmation committée → exactement une ligne
 *       {@code STUDENT_IMPORT_CONFIRMED}, sans PII, écrite après le retour
 *       de l'appel (transaction dédiée) ;</li>
 *   <li>confirmation en rollback → <strong>aucune</strong> ligne
 *       {@code STUDENT_IMPORT_CONFIRMED} (phase AFTER_COMMIT jamais
 *       atteinte).</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StudentImportAuditIntegrationTests {

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
    private StudentImportSimulationService simulationService;
    @Autowired
    private StudentImportConfirmationService confirmationService;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private long adminInternalId;
    private String adminPublicId;
    private String adminToken;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        jdbc = new JdbcTemplate(dataSource);
        String email = "aud-" + UUID.randomUUID() + "@esic-connect.test";
        UserAccount account = new UserAccount(email, "Aud", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        userRoleRepository.saveAndFlush(new UserRole(account,
                roleRepository.findByCode(RoleCode.ADMIN).orElseThrow(), Instant.now(), true));
        adminInternalId = account.getId();
        adminPublicId = account.getPublicId().toString();
        adminToken = login(email);
    }

    @Test
    void simulationWritesExactlyOneSimulatedAuditRowWithoutPii() {
        Chain chain = academicChain();
        String email = "aud." + UUID.randomUUID() + "@esic-connect.test";
        UUID jobId = simulate("s.csv", header() + row(email, chain));

        assertThat(auditCount(jobId, "STUDENT_IMPORT_SIMULATED")).isEqualTo(1);
        String reason = auditReason(jobId, "STUDENT_IMPORT_SIMULATED");
        assertThat(reason).contains("job=" + jobId).contains("rows=1").contains("confirmable=true");
        assertNoPii(reason, email);
    }

    @Test
    void aCommittedConfirmationWritesExactlyOneConfirmedAuditRowInADedicatedTransaction() {
        Chain chain = academicChain();
        String email = "aud." + UUID.randomUUID() + "@esic-connect.test";
        UUID jobId = simulate("c.csv", header() + row(email, chain));

        long confirmedRowsBefore = auditCount(jobId, "STUDENT_IMPORT_CONFIRMED");

        confirm(jobId);

        // Exactement une ligne d'audit CONFIRMED, visible depuis une requête tierce APRÈS le
        // retour de l'appel (le listener AFTER_COMMIT + REQUIRES_NEW l'a écrite dans sa propre
        // transaction, une fois la confirmation committée).
        assertThat(auditCount(jobId, "STUDENT_IMPORT_CONFIRMED")).isEqualTo(confirmedRowsBefore + 1);
        String reason = auditReason(jobId, "STUDENT_IMPORT_CONFIRMED");
        assertThat(reason).contains("created=1").contains("invited=1").contains("moved=0");
        assertNoPii(reason, email);
    }

    @Test
    void aRolledBackConfirmationWritesNoConfirmedAuditRow() {
        Chain chain = academicChain();
        String clashing = "ESIC-AUD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String csv = "last_name,first_name,email,formation_code,class_code,academic_year,student_number\n"
                + "Un,A,aud1." + UUID.randomUUID() + "@esic-connect.test," + chain.programCode() + ","
                + chain.classCode() + "," + chain.yearCode() + ",\n"
                + "Deux,B,aud2." + UUID.randomUUID() + "@esic-connect.test," + chain.programCode() + ","
                + chain.classCode() + "," + chain.yearCode() + "," + clashing + "\n";
        UUID jobId = simulate("rb.csv", csv);

        long confirmedBefore = auditCount(jobId, "STUDENT_IMPORT_CONFIRMED");
        insertForeignProfileWithNumber(clashing);

        try {
            confirm(jobId);
        } catch (StudentImportException expected) {
            assertThat(expected.kind()).isEqualTo(StudentImportException.Kind.STALE_SIMULATION);
        }

        assertThat(auditCount(jobId, "STUDENT_IMPORT_CONFIRMED")).isEqualTo(confirmedBefore);
    }

    // ------------------------------------------------------------------

    private void assertNoPii(String reason, String email) {
        assertThat(reason).doesNotContain(email).doesNotContain("@").doesNotContainIgnoringCase("nom")
                .doesNotContain("ESIC-");
    }

    private long auditCount(UUID jobId, String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE category = 'STUDENT_IMPORT' "
                + "AND action = ? AND resource_public_id = UNHEX(REPLACE(?, '-', ''))", Long.class,
                action, jobId.toString());
    }

    private String auditReason(UUID jobId, String action) {
        return jdbc.queryForObject("SELECT reason FROM audit_event WHERE category = 'STUDENT_IMPORT' "
                + "AND action = ? AND resource_public_id = UNHEX(REPLACE(?, '-', '')) ORDER BY id DESC LIMIT 1",
                String.class, action, jobId.toString());
    }

    private void insertForeignProfileWithNumber(String studentNumber) {
        String email = "audforeign." + UUID.randomUUID() + "@esic-connect.test";
        jdbc.update("INSERT INTO user_account (public_id, email, first_name, last_name, status, created_at, "
                + "updated_at, version) VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, 'For', 'Eign', 'ACTIVE', "
                + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)", email);
        Long userId = jdbc.queryForObject("SELECT id FROM user_account WHERE email = ?", Long.class, email);
        jdbc.update("INSERT INTO student_profile (public_id, user_id, student_number, work_study, status, "
                + "created_at, updated_at, version) VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, ?, 0, 'ACTIVE', "
                + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)", userId, studentNumber);
    }

    private static String header() {
        return "last_name,first_name,email,formation_code,class_code,academic_year\n";
    }

    private static String row(String email, Chain chain) {
        return "Nom,Prenom," + email + "," + chain.programCode() + "," + chain.classCode() + ","
                + chain.yearCode() + "\n";
    }

    private UUID simulate(String fileName, String csv) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(adminPublicId, null, "ROLE_ADMIN"));
        try {
            return simulationService.simulate(new StudentImportSimulationService.SimulationCommand(
                    fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8), adminInternalId, null, null))
                    .getPublicId();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void confirm(UUID jobId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(adminPublicId, null, "ROLE_ADMIN"));
        try {
            confirmationService.confirm(jobId.toString(), adminPublicId);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private record Chain(String programCode, String classCode, String yearCode) {
    }

    private Chain academicChain() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String site = (String) created("/api/v1/sites", Map.of("code", "SITE-" + suffix,
                "name", "Campus", "timeZoneId", "Europe/Paris")).get("publicId");
        String programCode = "PRG-" + suffix;
        String program = (String) created("/api/v1/programs", Map.of("code", programCode,
                "name", "BTS SIO", "programType", "BTS")).get("publicId");
        String level = (String) created("/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1-" + suffix, "name", "BTS 1", "sequenceNumber", 1)).get("publicId");
        String yearCode = "AY-" + suffix;
        String year = (String) created("/api/v1/academic-years", Map.of("code", yearCode, "name", "2026-2027",
                "startDate", "2026-09-01", "endDate", "2027-08-31")).get("publicId");
        String promo = (String) created("/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P-" + suffix, "name", "Promotion")).get("publicId");
        String classCode = "C-" + suffix;
        created("/api/v1/class-groups", Map.of("promotionPublicId", promo, "programLevelPublicId", level,
                "sitePublicId", site, "code", classCode, "name", "Classe"));
        return new Chain(programCode, classCode, yearCode);
    }

    private Map<String, Object> created(String path, Map<String, Object> body) {
        RequestEntity<?> entity = RequestEntity.post(URI.create(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).body(body);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(entity,
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private String login(String email) {
        Map<String, Object> body = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) body.get("accessToken");
    }
}
