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
import org.springframework.http.HttpMethod;
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
 * Simulation d'import de bout en bout (CP4 ; rapport §6, §11 invariant
 * T1 ; IMP-STU-01 / TI-001 / TI-008) : un fichier de 100 apprenants
 * valides produit un job {@code SIMULATED} confirmable dont le bilan
 * annonce 100 créations, <strong>sans aucune écriture métier</strong>
 * ({@code user_account} / {@code student_profile} / {@code enrollment} /
 * {@code account_invitation} inchangés). Colonne obligatoire absente →
 * rejet avant toute création de job. Filtre de périmètre pour un appelant
 * non global → refusé.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StudentImportSimulationIntegrationTests {

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
    }

    @Test
    void aHundredValidRowsSimulateWithoutAnyBusinessWrite() {
        Account adminAccount = accountWithRoles(RoleCode.ADMIN);
        String admin = login(adminAccount);
        Chain chain = academicChain(admin);

        long users0 = count("user_account");
        long profiles0 = count("student_profile");
        long enrollments0 = count("enrollment");
        long invitations0 = count("account_invitation");
        long jobs0 = count("student_import_job");

        StringBuilder csv = new StringBuilder(
                "last_name,first_name,email,formation_code,class_code,academic_year\n");
        for (int i = 1; i <= 100; i++) {
            csv.append("Nom").append(i).append(",Prenom").append(i).append(',')
                    .append("import.").append(UUID.randomUUID()).append("@esic-connect.test").append(',')
                    .append(chain.programCode()).append(',')
                    .append(chain.classCode()).append(',')
                    .append(chain.yearCode()).append('\n');
        }

        StudentImportJob job = simulateAs("ROLE_ADMIN",
                new StudentImportSimulationService.SimulationCommand("apprenants.csv", "text/csv",
                        csv.toString().getBytes(StandardCharsets.UTF_8), adminAccount.internalId(), null, null));

        assertThat(job.getStatus()).isEqualTo(StudentImportJobStatus.SIMULATED);
        assertThat(job.getTotalRows()).isEqualTo(100);
        assertThat(job.getPlannedCreateRows()).isEqualTo(100);
        assertThat(job.getErrorRows()).isZero();
        assertThat(job.isConfirmable()).isTrue();

        assertThat(count("student_import_job")).isEqualTo(jobs0 + 1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_import_row WHERE student_import_job_id = ?", Long.class, job.getId()))
                .isEqualTo(100L);

        // Invariant T1 : aucune écriture métier pendant la simulation.
        assertThat(count("user_account")).isEqualTo(users0);
        assertThat(count("student_profile")).isEqualTo(profiles0);
        assertThat(count("enrollment")).isEqualTo(enrollments0);
        assertThat(count("account_invitation")).isEqualTo(invitations0);
    }

    @Test
    void aMissingMandatoryColumnIsRejectedBeforeAnyJobIsCreated() {
        long jobs0 = count("student_import_job");
        StudentImportException ex = simulateExpectingFailure("ROLE_ADMIN",
                "first_name,phone\nJane,0102030405\n");
        assertThat(ex.kind()).isEqualTo(StudentImportException.Kind.MISSING_COLUMN);
        assertThat(count("student_import_job")).isEqualTo(jobs0);
    }

    @Test
    void aScopeFilterFromANonGlobalCallerIsForbidden() {
        StudentImportException ex = simulateExpectingFailure("ROLE_PEDAGOGICAL_MANAGER",
                "last_name,first_name,email,formation_code,class_code,academic_year\n"
                        + "D,J,j@x.test,P,C,2026-2027\n", "PRG-X", null);
        assertThat(ex.kind()).isEqualTo(StudentImportException.Kind.SCOPE_FORBIDDEN);
    }

    // ------------------------------------------------------------------

    private StudentImportJob simulateAs(String authority, StudentImportSimulationService.SimulationCommand command) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("import-tester", null, authority));
        try {
            return simulationService.simulate(command);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private StudentImportException simulateExpectingFailure(String authority, String csv) {
        return simulateExpectingFailure(authority, csv, null, null);
    }

    private StudentImportException simulateExpectingFailure(String authority, String csv,
                                                           String programCode, String classCode) {
        try {
            simulateAs(authority, new StudentImportSimulationService.SimulationCommand("f.csv", "text/csv",
                    csv.getBytes(StandardCharsets.UTF_8), null, programCode, classCode));
        } catch (StudentImportException ex) {
            return ex;
        }
        throw new AssertionError("StudentImportException attendue");
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private record Chain(String programCode, String classCode, String yearCode) {
    }

    private Chain academicChain(String admin) {
        String suffix = shortCode();
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
        RequestEntity<?> entity = RequestEntity.method(HttpMethod.POST, URI.create(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(body);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(entity,
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private String login(Account account) {
        Map<String, Object> body = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", account.email(), "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) body.get("accessToken");
    }

    private record Account(long internalId, String email) {
    }

    private Account accountWithRoles(RoleCode... roles) {
        UserAccount account = new UserAccount("imp-" + UUID.randomUUID() + "@esic-connect.test",
                "Imp", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return new Account(account.getId(), account.getEmail());
    }

    private static String shortCode() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
