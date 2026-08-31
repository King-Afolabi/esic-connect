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
import org.junit.jupiter.api.BeforeEach;
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

import javax.sql.DataSource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API de confirmation et de cycle de vie des imports (CP8 ; rapport §8,
 * §9 ; TI-012 ; invariant T6). Statuts HTTP et codes d'erreur exacts,
 * idempotence, périmètre, concurrence. Endpoints exercés uniquement par
 * HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StudentImportLifecycleApiIntegrationTests {

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
    void confirmAppliesThenReconfirmIsIdempotent() {
        String admin = tokenFor(RoleCode.ADMIN);
        Chain chain = academicChain(admin);
        String jobId = simulate(admin, header() + row("life1." + UUID.randomUUID() + "@x.test", chain) + row("life2." + UUID.randomUUID() + "@x.test", chain));

        ResponseEntity<Map<String, Object>> first = post("/api/v1/student-imports/" + jobId + "/confirm", admin);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().get("alreadyApplied")).isEqualTo(Boolean.FALSE);
        assertThat(((Number) first.getBody().get("created")).intValue()).isEqualTo(2);
        assertThat(getMap("/api/v1/student-imports/" + jobId, admin).get("status")).isEqualTo("APPLIED");

        ResponseEntity<Map<String, Object>> second = post("/api/v1/student-imports/" + jobId + "/confirm", admin);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().get("alreadyApplied")).isEqualTo(Boolean.TRUE);
        assertThat(((Number) second.getBody().get("created")).intValue()).isEqualTo(2);
    }

    @Test
    void confirmRejectsNonConfirmableExpiredAndCancelledJobs() {
        String admin = tokenFor(RoleCode.ADMIN);
        Chain chain = academicChain(admin);

        // Non confirmable (ligne e-mail invalide).
        String badJob = simulate(admin, header() + row("ok." + UUID.randomUUID() + "@x.test", chain)
                + "Nom,Prenom,not-an-email," + chain.programCode() + "," + chain.classCode() + ","
                + chain.yearCode() + "\n");
        assertThat(errorCode(post("/api/v1/student-imports/" + badJob + "/confirm", admin)))
                .isEqualTo("IMP_NOT_CONFIRMABLE");

        // Expirée.
        String expiredJob = simulate(admin, header() + row("exp." + UUID.randomUUID() + "@x.test", chain));
        jdbc.update("UPDATE student_import_job SET expires_at = ? WHERE public_id = UNHEX(REPLACE(?, '-', ''))",
                Timestamp.from(Instant.now().minusSeconds(3600)), expiredJob);
        assertThat(errorCode(post("/api/v1/student-imports/" + expiredJob + "/confirm", admin)))
                .isEqualTo("IMP_SIMULATION_EXPIRED");

        // Annulée puis reconfirmée.
        String cancelledJob = simulate(admin, header() + row("can." + UUID.randomUUID() + "@x.test", chain));
        assertThat(post("/api/v1/student-imports/" + cancelledJob + "/cancel", admin).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getMap("/api/v1/student-imports/" + cancelledJob, admin).get("status")).isEqualTo("CANCELLED");
        assertThat(errorCode(post("/api/v1/student-imports/" + cancelledJob + "/confirm", admin)))
                .isEqualTo("IMP_JOB_CANCELLED");
    }

    @Test
    void cancelOnlyAppliesToASimulatedJob() {
        String admin = tokenFor(RoleCode.ADMIN);
        Chain chain = academicChain(admin);
        String jobId = simulate(admin, header() + row("cn." + UUID.randomUUID() + "@x.test", chain));

        post("/api/v1/student-imports/" + jobId + "/confirm", admin);
        assertThat(errorCode(post("/api/v1/student-imports/" + jobId + "/cancel", admin)))
                .isEqualTo("IMP_JOB_NOT_CANCELLABLE");
        assertThat(status(HttpMethod.POST, "/api/v1/student-imports/" + UUID.randomUUID() + "/cancel", admin))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void securityAndScopeAreEnforced() {
        String admin = tokenFor(RoleCode.ADMIN);
        String managerA = tokenFor(RoleCode.PEDAGOGICAL_MANAGER);
        String managerB = tokenFor(RoleCode.PEDAGOGICAL_MANAGER);
        Chain chain = academicChain(admin);
        String jobId = simulate(managerA, header() + row("sc." + UUID.randomUUID() + "@x.test", chain));

        assertThat(status(HttpMethod.POST, "/api/v1/student-imports/" + jobId + "/confirm", null))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(status(HttpMethod.POST, "/api/v1/student-imports/" + jobId + "/confirm",
                tokenFor(RoleCode.STUDENT))).isEqualTo(HttpStatus.FORBIDDEN);
        // Un autre RP ne peut ni confirmer ni annuler le job d'un tiers.
        assertThat(status(HttpMethod.POST, "/api/v1/student-imports/" + jobId + "/confirm", managerB))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(HttpMethod.POST, "/api/v1/student-imports/" + jobId + "/cancel", managerB))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void twoConcurrentConfirmHttpCallsNeverDoubleApply() throws Exception {
        String admin = tokenFor(RoleCode.ADMIN);
        Chain chain = academicChain(admin);
        StringBuilder csv = new StringBuilder(header());
        for (int i = 0; i < 10; i++) {
            csv.append(row("conc." + UUID.randomUUID() + "@x.test", chain));
        }
        String jobId = simulate(admin, csv.toString());
        long users0 = jdbc.queryForObject("SELECT COUNT(*) FROM user_account", Long.class);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<ResponseEntity<Map<String, Object>>> task =
                    () -> post("/api/v1/student-imports/" + jobId + "/confirm", admin);
            Future<ResponseEntity<Map<String, Object>>> f1 = pool.submit(task);
            Future<ResponseEntity<Map<String, Object>>> f2 = pool.submit(task);
            List<ResponseEntity<Map<String, Object>>> results = List.of(f1.get(), f2.get());
            for (ResponseEntity<Map<String, Object>> r : results) {
                assertThat(r.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CONFLICT);
            }
            long nonIdempotentOk = results.stream()
                    .filter(r -> r.getStatusCode() == HttpStatus.OK
                            && Boolean.FALSE.equals(r.getBody().get("alreadyApplied")))
                    .count();
            assertThat(nonIdempotentOk).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_account", Long.class))
                .isEqualTo(users0 + 10); // jamais + 20
    }

    // ------------------------------------------------------------------

    private static String header() {
        return "last_name,first_name,email,formation_code,class_code,academic_year\n";
    }

    private static String row(String email, Chain chain) {
        return "Nom,Prenom," + email + "," + chain.programCode() + "," + chain.classCode() + ","
                + chain.yearCode() + "\n";
    }

    private String simulate(String token, String csv) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "life.csv";
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(new MediaType("text", "csv"));
        parts.add("file", new HttpEntity<>(resource, fileHeaders));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                URI.create("/api/v1/student-imports"), HttpMethod.POST, new HttpEntity<>(parts, headers),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).as("upload -> " + response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("publicId");
    }

    private ResponseEntity<Map<String, Object>> post(String path, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(HttpMethod.POST, URI.create(path));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return restTemplate.exchange(builder.contentType(MediaType.APPLICATION_JSON).body("{}"),
                new ParameterizedTypeReference<>() {
                });
    }

    private String errorCode(ResponseEntity<Map<String, Object>> response) {
        return String.valueOf(response.getBody().get("code"));
    }

    private Map<String, Object> getMap(String path, String token) {
        return restTemplate.exchange(
                RequestEntity.get(URI.create(path)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
    }

    private HttpStatus status(HttpMethod method, String path, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(method, URI.create(path));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return (HttpStatus) restTemplate.exchange(builder.contentType(MediaType.APPLICATION_JSON).body("{}"),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getStatusCode();
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

    private String tokenFor(RoleCode... roles) {
        UserAccount account = new UserAccount("life-" + UUID.randomUUID() + "@esic-connect.test",
                "Life", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        Map<String, Object> body = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", account.getEmail(), "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) body.get("accessToken");
    }
}
