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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API de simulation et de consultation des imports (CP5 ; rapport §8, §9 ;
 * IMP-STU-01 / IMP-STU-03 / TI-002 / TI-011) : téléversement multipart,
 * réponses sans identifiant SQL, pagination et filtres, sécurité par rôle
 * et périmètre. Aucun endpoint de confirmation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StudentImportApiIntegrationTests {

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

    @BeforeEach
    void useJdkClient() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void uploadSimulatesAndReturnsAJobWithoutInternalIds() {
        String admin = tokenFor(RoleCode.ADMIN);
        Chain chain = academicChain(admin);
        String csv = header() + row("a@x.test", chain) + row("b@x.test", chain);

        ResponseEntity<Map<String, Object>> response = upload("apprenants.csv", csv, admin, null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> job = response.getBody();
        assertThat(job).doesNotContainKeys("id", "requestedById", "requestedBy");
        assertThat(job.get("status")).isEqualTo("SIMULATED");
        assertThat(job.get("fileName")).isEqualTo("apprenants.csv");
        assertThat(job.get("confirmable")).isEqualTo(Boolean.TRUE);
        Map<?, ?> summary = (Map<?, ?>) job.get("summary");
        assertThat(((Number) summary.get("total")).intValue()).isEqualTo(2);
        assertThat(((Number) summary.get("plannedCreate")).intValue()).isEqualTo(2);
    }

    @Test
    void aMissingMandatoryColumnGivesA400WithDetails() {
        String admin = tokenFor(RoleCode.ADMIN);
        ResponseEntity<Map<String, Object>> response =
                upload("bad.csv", "first_name,phone\nJane,0102030405\n", admin, null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("IMP_MISSING_COLUMN");
        assertThat(String.valueOf(response.getBody().get("details"))).contains("email");
    }

    @Test
    void anXlsxDisguisedAsCsvIsRejected() {
        String admin = tokenFor(RoleCode.ADMIN);
        byte[] zip = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00};
        ResponseEntity<Map<String, Object>> response = uploadBytes("apprenants.csv", zip, admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().get("code")).isEqualTo("IMP_UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void rowsEndpointSupportsPaginationAndStatusFilter() {
        String admin = tokenFor(RoleCode.ADMIN);
        Chain chain = academicChain(admin);

        String csv = header()
                + row("valid@x.test", chain)
                + "Nom,Prenom,not-an-email," + chain.programCode() + "," + chain.classCode() + "," + chain.yearCode() + "\n"
                + "Nom,Prenom,unknown@x.test,NOPE," + chain.classCode() + "," + chain.yearCode() + "\n";
        String jobId = (String) upload("mix.csv", csv, admin, null, null).getBody().get("publicId");

        assertThat(((Number) getMap("/api/v1/student-imports/" + jobId + "/rows", admin).get("totalElements"))
                .longValue()).isEqualTo(3);
        assertThat(((Number) getMap("/api/v1/student-imports/" + jobId + "/rows?rowStatus=ERROR", admin)
                .get("totalElements")).longValue()).isEqualTo(2);

        Map<String, Object> validRows =
                getMap("/api/v1/student-imports/" + jobId + "/rows?rowStatus=VALID&sort=rowNumber,asc", admin);
        assertThat(((Number) validRows.get("totalElements")).longValue()).isEqualTo(1);
        Map<?, ?> firstRow = (Map<?, ?>) ((List<?>) validRows.get("content")).get(0);
        assertThat(firstRow.containsKey("id")).isFalse();
        assertThat(firstRow.containsKey("jobId")).isFalse();
        assertThat(firstRow.get("plannedAction")).isEqualTo("CREATE_ACCOUNT_AND_ENROLL");
    }

    @Test
    void aManagerOnlySeesItsOwnJobsAndGlobalStaffSeesAll() {
        String admin = tokenFor(RoleCode.ADMIN);
        String managerA = tokenFor(RoleCode.PEDAGOGICAL_MANAGER);
        String managerB = tokenFor(RoleCode.PEDAGOGICAL_MANAGER);
        Chain chain = academicChain(admin);

        String jobId = (String) upload("own.csv", header() + row("mine@x.test", chain), managerA, null, null)
                .getBody().get("publicId");

        assertThat(((Number) getMap("/api/v1/student-imports", managerA).get("totalElements")).longValue())
                .isGreaterThanOrEqualTo(1);
        assertThat(status(HttpMethod.GET, "/api/v1/student-imports/" + jobId, managerB))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getError("/api/v1/student-imports/" + jobId, managerB))
                .containsEntry("code", "IMP_JOB_FORBIDDEN");
        assertThat(status(HttpMethod.GET, "/api/v1/student-imports/" + jobId, admin)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void unknownJobIs404AndInvalidSortOrFilterIs400() {
        String admin = tokenFor(RoleCode.ADMIN);
        Chain chain = academicChain(admin);
        String jobId = (String) upload("f.csv", header() + row("z@x.test", chain), admin, null, null)
                .getBody().get("publicId");

        assertThat(status(HttpMethod.GET, "/api/v1/student-imports/" + UUID.randomUUID(), admin))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getError("/api/v1/student-imports/" + UUID.randomUUID(), admin))
                .containsEntry("code", "IMP_JOB_NOT_FOUND");
        assertThat(getError("/api/v1/student-imports?sort=bogus", admin)).containsEntry("code", "IMP_INVALID_SORT");
        assertThat(getError("/api/v1/student-imports?status=BOGUS", admin)).containsEntry("code", "IMP_INVALID_FILTER");
        assertThat(getError("/api/v1/student-imports/" + jobId + "/rows?rowStatus=BOGUS", admin))
                .containsEntry("code", "IMP_INVALID_FILTER");
    }

    @Test
    void securityMatrix() {
        String csv = header();
        assertThat(uploadBytes("f.csv", csv.getBytes(StandardCharsets.UTF_8), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(uploadBytes("f.csv", csv.getBytes(StandardCharsets.UTF_8), tokenFor(RoleCode.STUDENT))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(uploadBytes("f.csv", csv.getBytes(StandardCharsets.UTF_8), tokenFor(RoleCode.TEACHER))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(HttpMethod.GET, "/api/v1/student-imports", tokenFor(RoleCode.STUDENT)))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------

    private static String header() {
        return "last_name,first_name,email,formation_code,class_code,academic_year\n";
    }

    private static String row(String email, Chain chain) {
        return "Nom,Prenom," + email + "," + chain.programCode() + "," + chain.classCode() + ","
                + chain.yearCode() + "\n";
    }

    private ResponseEntity<Map<String, Object>> upload(String fileName, String csv, String token,
                                                       String programCode, String classCode) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(new MediaType("text", "csv"));
        parts.add("file", new HttpEntity<>(resource, fileHeaders));
        if (programCode != null) {
            parts.add("programCode", programCode);
        }
        if (classCode != null) {
            parts.add("classCode", classCode);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(URI.create("/api/v1/student-imports"), HttpMethod.POST,
                new HttpEntity<>(parts, headers), new ParameterizedTypeReference<>() {
                });
    }

    private ResponseEntity<Map<String, Object>> uploadBytes(String fileName, byte[] content, String token) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(new MediaType("text", "csv"));
        parts.add("file", new HttpEntity<>(resource, fileHeaders));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(URI.create("/api/v1/student-imports"), HttpMethod.POST,
                new HttpEntity<>(parts, headers), new ParameterizedTypeReference<>() {
                });
    }

    private Map<String, Object> getMap(String path, String token) {
        return restTemplate.exchange(
                RequestEntity.get(URI.create(path)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
    }

    /** Renvoie le corps quel que soit le statut (pour inspecter un code d'erreur). */
    private Map<String, Object> getError(String path, String token) {
        return restTemplate.exchange(
                RequestEntity.get(URI.create(path)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
    }

    private HttpStatus status(HttpMethod method, String path, String token) {
        return (HttpStatus) restTemplate.exchange(
                RequestEntity.method(method, URI.create(path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
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
        UserAccount account = new UserAccount("imp-" + UUID.randomUUID() + "@esic-connect.test",
                "Imp", "Tester", AccountStatus.ACTIVE);
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
