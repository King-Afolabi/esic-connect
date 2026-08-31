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
import org.junit.jupiter.api.Tag;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mesure de performance (FINAL-009) — <strong>non exécutée par
 * {@code mvn test}</strong> (tag {@code perf}, exclu par le
 * {@code maven-surefire-plugin}). Lancer via {@code ./mvnw test -Pperf}.
 *
 * <p>Chronomètre la <em>simulation</em> d'un import CSV de 100 apprenants
 * valides ({@link StudentImportSimulationService#simulate}, phase 1 : pas
 * d'écriture métier — invariant T1). Aucune assertion de latence stricte :
 * seul un garde-fou très large détecte une régression catastrophique. Les
 * chiffres p50 / p95 sont écrits sur la sortie standard et repris dans
 * {@code docs/reports/PERF_NOTES.md} avec le contexte machine.
 */
@Tag("perf")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StudentImportSimulationPerfTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    private static final int WARMUP = 3;
    private static final int ITERATIONS = 15;

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

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void measuresSimulationOfOneHundredValidRows() {
        Account admin = accountWithRole(RoleCode.ADMIN);
        String token = login(admin);
        Chain chain = academicChain(token);

        long[] samplesNanos = new long[ITERATIONS];
        for (int i = 0; i < WARMUP + ITERATIONS; i++) {
            byte[] csv = buildCsv(chain, 100);
            long start = System.nanoTime();
            StudentImportJob job = simulateAsAdmin(new StudentImportSimulationService.SimulationCommand(
                    "apprenants.csv", "text/csv", csv, admin.internalId(), null, null));
            long elapsed = System.nanoTime() - start;
            assertThat(job.getTotalRows()).isEqualTo(100);
            assertThat(job.getStatus()).isEqualTo(StudentImportJobStatus.SIMULATED);
            if (i >= WARMUP) {
                samplesNanos[i - WARMUP] = elapsed;
            }
        }

        java.util.Arrays.sort(samplesNanos);
        double minMs = samplesNanos[0] / 1_000_000d;
        double p50Ms = samplesNanos[samplesNanos.length / 2] / 1_000_000d;
        double p95Ms = samplesNanos[(int) Math.ceil(samplesNanos.length * 0.95) - 1] / 1_000_000d;
        double maxMs = samplesNanos[samplesNanos.length - 1] / 1_000_000d;

        System.out.printf(
                "%n[PERF] student-import simulate(100 rows) — iterations=%d warmup=%d%n"
                        + "[PERF]   min=%.1f ms  p50=%.1f ms  p95=%.1f ms  max=%.1f ms%n",
                ITERATIONS, WARMUP, minMs, p50Ms, p95Ms, maxMs);

        // Garde-fou très large : détecte une régression catastrophique,
        // ce n'est PAS une garantie contractuelle de latence.
        assertThat(p50Ms).as("simulation médiane d'un import de 100 lignes").isLessThan(10_000d);
    }

    // ------------------------------------------------------------------

    private static byte[] buildCsv(Chain chain, int rows) {
        StringBuilder csv = new StringBuilder(
                "last_name,first_name,email,formation_code,class_code,academic_year\n");
        for (int i = 1; i <= rows; i++) {
            csv.append("Nom").append(i).append(",Prenom").append(i).append(',')
                    .append("perf.").append(UUID.randomUUID()).append("@esic-connect.test").append(',')
                    .append(chain.programCode()).append(',')
                    .append(chain.classCode()).append(',')
                    .append(chain.yearCode()).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private StudentImportJob simulateAsAdmin(StudentImportSimulationService.SimulationCommand command) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("perf-tester", null, "ROLE_ADMIN"));
        try {
            return simulationService.simulate(command);
        } finally {
            SecurityContextHolder.clearContext();
        }
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
        RequestEntity<?> entity = RequestEntity.method(HttpMethod.POST, URI.create(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(body);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(entity,
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).as("POST " + path).isEqualTo(HttpStatus.CREATED);
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

    private Account accountWithRole(RoleCode roleCode) {
        UserAccount account = new UserAccount("perf-" + UUID.randomUUID() + "@esic-connect.test",
                "Perf", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        return new Account(account.getId(), account.getEmail());
    }
}
