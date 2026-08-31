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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Purge planifiée des imports (CP7 ; rapport §12.C, §7.6 ; §14.3
 * « Purge {@code @Scheduled} ») : un job {@code SIMULATED} expiré est
 * supprimé en cascade ; un job {@code APPLIED} ancien conserve son
 * en-tête et ses agrégats mais perd ses lignes filles ; un job récent est
 * préservé. Les données métier ne sont jamais touchées.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StudentImportPurgeTests {

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
    private StudentImportPurgeService purgeService;
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
        String email = "pur-" + UUID.randomUUID() + "@esic-connect.test";
        UserAccount account = new UserAccount(email, "Pur", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        userRoleRepository.saveAndFlush(new UserRole(account,
                roleRepository.findByCode(RoleCode.ADMIN).orElseThrow(), Instant.now(), true));
        adminInternalId = account.getId();
        adminPublicId = account.getPublicId().toString();
        adminToken = login(email);
    }

    @Test
    void purgeRemovesAnExpiredSimulationInCascadeAndKeepsARecentOne() {
        Chain chain = academicChain();
        UUID expiredJob = simulate(header() + row("exp." + UUID.randomUUID() + "@x.test", chain));
        UUID freshJob = simulate(header() + row("fresh." + UUID.randomUUID() + "@x.test", chain));

        jdbc.update("UPDATE student_import_job SET expires_at = ? WHERE public_id = UNHEX(REPLACE(?, '-', ''))",
                Timestamp.from(Instant.now().minusSeconds(3600)), expiredJob.toString());
        long expiredJobRowId = jobRowId(expiredJob);

        StudentImportPurgeService.PurgeReport report = purgeService.purge();

        assertThat(report.jobsDeleted()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_import_job WHERE public_id = UNHEX(REPLACE(?, '-', ''))",
                Long.class, expiredJob.toString())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_import_row WHERE student_import_job_id = ?", Long.class,
                expiredJobRowId)).isZero();
        // Le job récent n'est pas touché.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_import_job WHERE public_id = UNHEX(REPLACE(?, '-', ''))",
                Long.class, freshJob.toString())).isEqualTo(1L);
    }

    @Test
    void purgeTrimsChildRowsOfAnOldAppliedJobButKeepsItsHeaderAndAggregates() {
        Chain chain = academicChain();
        UUID jobId = simulate(header() + row("applied." + UUID.randomUUID() + "@x.test", chain));
        confirm(jobId);
        long jobRowId = jobRowId(jobId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM student_import_row WHERE student_import_job_id = ?",
                Long.class, jobRowId)).isEqualTo(1L);

        jdbc.update("UPDATE student_import_job SET confirmed_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(java.time.Duration.ofDays(40))), jobRowId);

        purgeService.purge();

        Map<String, Object> job = jdbc.queryForMap(
                "SELECT status, applied_created FROM student_import_job WHERE id = ?", jobRowId);
        assertThat(job.get("status")).isEqualTo("APPLIED");
        assertThat(((Number) job.get("applied_created")).intValue()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM student_import_row WHERE student_import_job_id = ?",
                Long.class, jobRowId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_import_job_issue WHERE student_import_job_id = ?", Long.class,
                jobRowId)).isZero();
    }

    // ------------------------------------------------------------------

    private long jobRowId(UUID publicId) {
        return jdbc.queryForObject(
                "SELECT id FROM student_import_job WHERE public_id = UNHEX(REPLACE(?, '-', ''))",
                Long.class, publicId.toString());
    }

    private static String header() {
        return "last_name,first_name,email,formation_code,class_code,academic_year\n";
    }

    private static String row(String email, Chain chain) {
        return "Nom,Prenom," + email + "," + chain.programCode() + "," + chain.classCode() + ","
                + chain.yearCode() + "\n";
    }

    private UUID simulate(String csv) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(adminPublicId, null, "ROLE_ADMIN"));
        try {
            return simulationService.simulate(new StudentImportSimulationService.SimulationCommand(
                    "p.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8), adminInternalId, null, null))
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
