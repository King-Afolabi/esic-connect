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
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rollback précis et concurrence de la confirmation (CP6 ; rapport §14.4,
 * §14.6 ; invariants T3 / T4 / T6). Un échec sur la <strong>dernière
 * ligne</strong> annule <strong>toutes</strong> les écritures des lignes
 * précédentes ; deux confirmations concurrentes ne créent qu'un seul jeu
 * de comptes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StudentImportConfirmationRollbackTests {

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
    void aFailureOnTheLastRowRollsBackEveryEarlierRow() {
        Actor admin = admin();
        Chain chain = academicChain(admin.token());

        // Numéro qui sera libre à la simulation, puis pris juste avant la confirmation.
        String clashingNumber = "ESIC-CLASH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String csv = "last_name,first_name,email,formation_code,class_code,academic_year,student_number\n"
                + "Un,A,rb1." + UUID.randomUUID() + "@esic-connect.test," + chain.programCode() + ","
                + chain.classCode() + "," + chain.yearCode() + ",\n"
                + "Deux,B,rb2." + UUID.randomUUID() + "@esic-connect.test," + chain.programCode() + ","
                + chain.classCode() + "," + chain.yearCode() + "," + clashingNumber + "\n";
        UUID jobId = simulate(admin, "rollback.csv", csv);

        long users0 = count("user_account");
        long profiles0 = count("student_profile");
        long enrollments0 = count("enrollment");
        long invitations0 = count("account_invitation");
        Integer sequence0 = jdbc.queryForObject(
                "SELECT next_value FROM student_number_sequence WHERE start_year = 2026", Integer.class);

        // La 2ᵉ ligne devient invalide : son numéro est désormais attribué à un autre profil.
        insertForeignProfileWithNumber(clashingNumber);

        assertThat(confirmError(admin, jobId)).isEqualTo(StudentImportException.Kind.STALE_SIMULATION);

        // + 1 = compte / profil « étrangers » insérés en SQL natif ; la ligne 1 de l'import,
        // elle, n'a créé AUCUN compte / profil / inscription / invitation (rollback total).
        assertThat(count("user_account")).isEqualTo(users0 + 1);
        assertThat(count("student_profile")).isEqualTo(profiles0 + 1);
        assertThat(count("enrollment")).isEqualTo(enrollments0);
        assertThat(count("account_invitation")).isEqualTo(invitations0);
        assertThat(jdbc.queryForObject(
                "SELECT next_value FROM student_number_sequence WHERE start_year = 2026", Integer.class))
                .isEqualTo(sequence0);
        assertThat(SENT_EMAILS).isEmpty();
        assertThat(jobStatus(jobId)).isEqualTo("SIMULATED");
    }

    @Test
    void twoConcurrentConfirmationsCreateExactlyOneSetOfAccounts() throws Exception {
        Actor admin = admin();
        Chain chain = academicChain(admin.token());
        StringBuilder csv = new StringBuilder(
                "last_name,first_name,email,formation_code,class_code,academic_year\n");
        for (int i = 0; i < 20; i++) {
            csv.append("N").append(i).append(",P").append(i).append(',')
                    .append("cc.").append(UUID.randomUUID()).append("@esic-connect.test").append(',')
                    .append(chain.programCode()).append(',').append(chain.classCode()).append(',')
                    .append(chain.yearCode()).append('\n');
        }
        UUID jobId = simulate(admin, "concurrent.csv", csv.toString());

        long users0 = count("user_account");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> task = () -> {
                try {
                    return confirm(admin, jobId);
                } catch (RuntimeException ex) {
                    return ex;
                }
            };
            Future<Object> f1 = pool.submit(task);
            Future<Object> f2 = pool.submit(task);
            Object r1 = f1.get();
            Object r2 = f2.get();

            long applied = List.of(r1, r2).stream()
                    .filter(r -> r instanceof StudentImportConfirmationService.ConfirmationResult res
                            && !res.alreadyApplied() && res.created() == 20)
                    .count();
            assertThat(applied).isEqualTo(1);
            for (Object r : List.of(r1, r2)) {
                assertThat(r).isNotInstanceOf(NullPointerException.class);
                if (r instanceof RuntimeException ex) {
                    assertThat(ex).isInstanceOf(StudentImportException.class);
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(count("user_account")).isEqualTo(users0 + 20); // jamais 40
        assertThat(jobStatus(jobId)).isEqualTo("APPLIED");
    }

    // ------------------------------------------------------------------

    private void insertForeignProfileWithNumber(String studentNumber) {
        String email = "foreign." + UUID.randomUUID() + "@esic-connect.test";
        jdbc.update("INSERT INTO user_account (public_id, email, first_name, last_name, status, created_at, "
                + "updated_at, version) VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, 'For', 'Eign', 'ACTIVE', "
                + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)", email);
        Long userId = jdbc.queryForObject("SELECT id FROM user_account WHERE email = ?", Long.class, email);
        jdbc.update("INSERT INTO student_profile (public_id, user_id, student_number, work_study, status, "
                + "created_at, updated_at, version) VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, ?, 0, 'ACTIVE', "
                + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)", userId, studentNumber);
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

    private String jobStatus(UUID jobId) {
        return jdbc.queryForObject(
                "SELECT status FROM student_import_job WHERE public_id = UNHEX(REPLACE(?, '-', ''))",
                String.class, jobId.toString());
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
        String year = (String) created("/api/v1/academic-years", Map.of("code", yearCode, "name", "2026-2027",
                "startDate", "2026-09-01", "endDate", "2027-08-31"), admin).get("publicId");
        String promo = (String) created("/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P-" + suffix, "name", "Promotion"), admin).get("publicId");
        String classCode = "C-" + suffix;
        created("/api/v1/class-groups", Map.of("promotionPublicId", promo, "programLevelPublicId", level,
                "sitePublicId", site, "code", classCode, "name", "Classe"), admin);
        return new Chain(programCode, classCode, yearCode);
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

    private record Actor(String publicId, long internalId, String token) {
    }

    private Actor admin() {
        String email = "imp-" + UUID.randomUUID() + "@esic-connect.test";
        UserAccount account = new UserAccount(email, "Imp", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        Role role = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        Map<String, Object> body = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return new Actor(account.getPublicId().toString(), account.getId(), (String) body.get("accessToken"));
    }
}
