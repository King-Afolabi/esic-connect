package com.esic.connect.studentimport.internal;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirmation transactionnelle de l'import (CP6 ; rapport §4.4, §11 ;
 * IMP-STU-02 / TI-005 / TI-006 / TI-012 ; AC-004 / AC-005 / AC-006 ;
 * invariants T2 / T3 / T4 / T6).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StudentImportConfirmationIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    private static final List<String> SENT_EMAILS = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class RecordingMailerConfig {
        @Bean
        @Primary
        InvitationMailer recordingInvitationMailer() {
            return (toEmail, firstName, rawToken, expiresAt) -> SENT_EMAILS.add(toEmail);
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

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        jdbc = new JdbcTemplate(dataSource);
        SENT_EMAILS.clear();
    }

    @Test
    void confirms100RowsIntoBusinessDataWithGeneratedNumbers() {
        Actor admin = admin();
        Chain chain = academicChain(admin.token());

        long users0 = count("user_account");
        long profiles0 = count("student_profile");
        long enrollments0 = count("enrollment");
        long invitations0 = count("account_invitation");

        StringBuilder csv = new StringBuilder(header());
        for (int i = 1; i <= 100; i++) {
            csv.append("Nom").append(i).append(",Prenom").append(i).append(',')
                    .append("gen.").append(UUID.randomUUID()).append("@esic-connect.test").append(',')
                    .append(chain.programCode()).append(',').append(chain.classA()).append(',')
                    .append(chain.yearCode()).append('\n');
        }
        UUID jobId = simulate(admin, "gen.csv", csv.toString());

        var result = confirm(admin, jobId);
        assertThat(result.alreadyApplied()).isFalse();
        assertThat(result.created()).isEqualTo(100);
        assertThat(result.invited()).isEqualTo(100);

        assertThat(count("user_account")).isEqualTo(users0 + 100);
        assertThat(count("student_profile")).isEqualTo(profiles0 + 100);
        assertThat(count("enrollment")).isEqualTo(enrollments0 + 100);
        assertThat(count("account_invitation")).isEqualTo(invitations0 + 100);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_role ur JOIN role r ON r.id = ur.role_id "
                        + "WHERE r.code = 'STUDENT' AND ur.active = 1 AND ur.created_at >= ?", Long.class,
                java.sql.Timestamp.from(Instant.now().minusSeconds(120)))).isGreaterThanOrEqualTo(100L);

        assertThat(jobStatus(jobId)).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT next_value FROM student_number_sequence WHERE start_year = 2026",
                Integer.class)).isGreaterThanOrEqualTo(101);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_profile WHERE student_number LIKE 'ESIC-2026-%'", Long.class))
                .isGreaterThanOrEqualTo(100L);
        assertThat(SENT_EMAILS).hasSize(100);
    }

    @Test
    void reconfirmationIsIdempotent() {
        Actor admin = admin();
        Chain chain = academicChain(admin.token());
        UUID jobId = simulate(admin, "one.csv", header() + row("once@x.test", chain));

        var first = confirm(admin, jobId);
        long users = count("user_account");
        long enrollments = count("enrollment");
        SENT_EMAILS.clear();

        var second = confirm(admin, jobId);
        assertThat(second.alreadyApplied()).isTrue();
        assertThat(second.created()).isEqualTo(first.created());
        assertThat(count("user_account")).isEqualTo(users);
        assertThat(count("enrollment")).isEqualTo(enrollments);
        assertThat(SENT_EMAILS).isEmpty();
    }

    @Test
    void anExpiredSimulationIsRejected() {
        Actor admin = admin();
        Chain chain = academicChain(admin.token());
        UUID jobId = simulate(admin, "exp.csv", header() + row("exp@x.test", chain));
        jdbc.update("UPDATE student_import_job SET expires_at = ? WHERE public_id = UNHEX(REPLACE(?, '-', ''))",
                java.sql.Timestamp.from(Instant.now().minusSeconds(3600)), jobId.toString());

        assertThat(confirmError(admin, jobId)).isEqualTo(StudentImportException.Kind.SIMULATION_EXPIRED);
        assertThat(jobStatus(jobId)).isEqualTo("SIMULATED");
    }

    @Test
    void aCancelledJobIsRejected() {
        Actor admin = admin();
        Chain chain = academicChain(admin.token());
        UUID jobId = simulate(admin, "can.csv", header() + row("can@x.test", chain));
        jdbc.update("UPDATE student_import_job SET status = 'CANCELLED' WHERE public_id = UNHEX(REPLACE(?, '-', ''))",
                jobId.toString());

        assertThat(confirmError(admin, jobId)).isEqualTo(StudentImportException.Kind.JOB_CANCELLED);
    }

    @Test
    void aNonConfirmableJobIsRejected() {
        Actor admin = admin();
        Chain chain = academicChain(admin.token());
        String csv = header() + row("ok@x.test", chain)
                + "Nom,Prenom,not-an-email," + chain.programCode() + "," + chain.classA() + "," + chain.yearCode() + "\n";
        UUID jobId = simulate(admin, "mix.csv", csv);

        assertThat(confirmError(admin, jobId)).isEqualTo(StudentImportException.Kind.NOT_CONFIRMABLE);
        assertThat(jobStatus(jobId)).isEqualTo("SIMULATED");
    }

    @Test
    void archivingTheClassBetweenSimulationAndConfirmationMakesItStale() {
        Actor admin = admin();
        Chain chain = academicChain(admin.token());
        UUID jobId = simulate(admin, "stale.csv", header() + row("stale@x.test", chain));

        long users0 = count("user_account");
        long invitations0 = count("account_invitation");
        archiveClass(admin.token(), chain.classAPublicId());

        assertThat(confirmError(admin, jobId)).isEqualTo(StudentImportException.Kind.STALE_SIMULATION);

        // Chemin « stale » (re-validation invalide la simulation) : la transaction de
        // confirmation a commité SANS rien appliquer, puis StaleRevalidationPersister a
        // écrit les anomalies rafraîchies dans une transaction propre — aucun verrou
        // croisé, aucun deadlock (l'appel a rendu la main sans blocage).
        assertThat(jobStatus(jobId)).isEqualTo("SIMULATED");
        assertThat(count("user_account")).isEqualTo(users0);
        assertThat(count("account_invitation")).isEqualTo(invitations0);
        assertThat(SENT_EMAILS).isEmpty();

        // La ligne est repassée ERROR ET l'anomalie technique rafraîchie est persistée.
        Long errorRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_import_row r JOIN student_import_job j ON j.id = r.student_import_job_id "
                        + "WHERE j.public_id = UNHEX(REPLACE(?, '-', '')) AND r.row_status = 'ERROR'",
                Long.class, jobId.toString());
        assertThat(errorRows).isEqualTo(1L);
        Long refreshedIssues = jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_import_row_issue i "
                        + "JOIN student_import_row r ON r.id = i.student_import_row_id "
                        + "JOIN student_import_job j ON j.id = r.student_import_job_id "
                        + "WHERE j.public_id = UNHEX(REPLACE(?, '-', '')) AND i.error_code = 'IMP_CHAIN_ARCHIVED'",
                Long.class, jobId.toString());
        assertThat(refreshedIssues).isEqualTo(1L);
        assertThat(Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT confirmable FROM student_import_job WHERE public_id = UNHEX(REPLACE(?, '-', ''))",
                Boolean.class, jobId.toString()))).isFalse();
    }

    @Test
    void aTransferKeepsHistoryAndDoesNotDuplicateTheAccount() {
        Actor admin = admin();
        Chain chain = academicChain(admin.token());

        // Apprenant déjà inscrit en classe B.
        String studentEmail = "mover." + UUID.randomUUID() + "@esic-connect.test";
        Actor mover = actorWith(studentEmail, RoleCode.STUDENT);
        String studentNumber = "ESIC-TR-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        String profileId = (String) created("/api/v1/student-profiles", Map.of(
                "userPublicId", mover.publicId(), "studentNumber", studentNumber), admin.token()).get("publicId");
        created("/api/v1/enrollments", Map.of(
                "studentProfilePublicId", profileId, "classGroupPublicId", chain.classBPublicId()), admin.token());

        long accountsForEmail0 = countAccounts(studentEmail);
        UUID jobId = simulate(admin, "transfer.csv",
                "last_name,first_name,email,formation_code,class_code,academic_year\n"
                        + "Mover,Max," + studentEmail + "," + chain.programCode() + "," + chain.classA() + ","
                        + chain.yearCode() + "\n");
        var result = confirm(admin, jobId);
        assertThat(result.transferred()).isEqualTo(1);

        assertThat(countAccounts(studentEmail)).isEqualTo(accountsForEmail0); // aucun doublon de compte
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM enrollment WHERE student_profile_id = "
                        + "(SELECT id FROM student_profile WHERE public_id = UNHEX(REPLACE(?, '-', ''))) "
                        + "AND status = 'TRANSFERRED'", Long.class, profileId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM enrollment WHERE student_profile_id = "
                        + "(SELECT id FROM student_profile WHERE public_id = UNHEX(REPLACE(?, '-', ''))) "
                        + "AND status = 'ACTIVE'", Long.class, profileId)).isEqualTo(1L);
    }

    // ------------------------------------------------------------------

    private static String header() {
        return "last_name,first_name,email,formation_code,class_code,academic_year\n";
    }

    private static String row(String email, Chain chain) {
        return "Nom,Prenom," + email + "," + chain.programCode() + "," + chain.classA() + "," + chain.yearCode()
                + "\n";
    }

    private UUID simulate(Actor actor, String fileName, String csv) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(actor.publicId(), null, "ROLE_ADMIN"));
        try {
            return simulationService.simulate(new StudentImportSimulationService.SimulationCommand(
                    fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8), actor.internalId(), null, null))
                    .getPublicId();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private StudentImportConfirmationService.ConfirmationResult confirm(Actor actor, UUID jobId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(actor.publicId(), null, "ROLE_ADMIN"));
        try {
            return confirmationService.confirm(jobId.toString(), actor.publicId());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private StudentImportException.Kind confirmError(Actor actor, UUID jobId) {
        try {
            confirm(actor, jobId);
        } catch (StudentImportException ex) {
            return ex.kind();
        }
        throw new AssertionError("StudentImportException attendue");
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private long countAccounts(String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE email = ?", Long.class, email);
    }

    private String jobStatus(UUID jobId) {
        return jdbc.queryForObject(
                "SELECT status FROM student_import_job WHERE public_id = UNHEX(REPLACE(?, '-', ''))",
                String.class, jobId.toString());
    }

    private void archiveClass(String adminToken, String classPublicId) {
        RequestEntity<?> entity = RequestEntity.post(URI.create("/api/v1/class-groups/" + classPublicId + "/archive"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("reason", "test import stale"));
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(entity,
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).as("archive -> " + response.getBody()).isTrue();
    }

    private record Chain(String programCode, String yearCode, String classA, String classB,
                         String classAPublicId, String classBPublicId) {
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
        String year = (String) created("/api/v1/academic-years", Map.of("code", yearCode, "name", "2026-2027",
                "startDate", "2026-09-01", "endDate", "2027-08-31"), admin).get("publicId");
        String promo = (String) created("/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P-" + suffix, "name", "Promotion"), admin).get("publicId");
        Map<String, Object> a = created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "CA-" + suffix, "name", "Classe A"),
                admin);
        Map<String, Object> b = created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "CB-" + suffix, "name", "Classe B"),
                admin);
        return new Chain(programCode, yearCode, "CA-" + suffix, "CB-" + suffix,
                (String) a.get("publicId"), (String) b.get("publicId"));
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        RequestEntity<?> entity = RequestEntity.post(URI.create(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(body);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(entity,
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private record Actor(String publicId, long internalId, String email, String token) {
    }

    private Actor admin() {
        return actorWith("imp-" + UUID.randomUUID() + "@esic-connect.test", RoleCode.ADMIN);
    }

    private Actor actorWith(String email, RoleCode... roles) {
        UserAccount account = new UserAccount(email, "Imp", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        Map<String, Object> body = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return new Actor(account.getPublicId().toString(), account.getId(), email, (String) body.get("accessToken"));
    }
}
