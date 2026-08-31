package com.esic.connect.studentimport;

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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recette de l'import CSV des apprenants (CP10 ; rapport §14) — comble les
 * cas non couverts par les checkpoints précédents :
 *
 * <ul>
 *   <li>TI-010 : fichier au-delà de la limite de taille → 4xx ;</li>
 *   <li>TI-007 (partie ligne) : {@code PEDAGOGICAL_MANAGER} hors
 *       périmètre → lignes {@code ERROR IMP_CLASS_OUT_OF_SCOPE}, job non
 *       confirmable ;</li>
 *   <li>invariant T5 (effet observable) : une confirmation n'écrit
 *       <strong>aucune</strong> ligne d'audit
 *       {@code ACCOUNT_INVITATION_ISSUED} / {@code INVITATION_ISSUED} (le
 *       port {@code identity} ne publie jamais {@code AccountLifecycleEvent}),
 *       alors que les e-mails d'invitation partent bien
 *       ({@code AccountInvitationIssuedEvent} publié) ;</li>
 *   <li>§14.6 : deux imports aux e-mails recoupés confirmés l'un après
 *       l'autre → aucun doublon de compte ;</li>
 *   <li>§14.5 : {@code 401} anonyme sur les six endpoints.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StudentImportRecetteTests {

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
    void aFileLargerThanTheLimitIsRejected() {
        String admin = tokenFor(RoleCode.ADMIN);
        // > 2 MiB : intercepté par le conteneur multipart puis retraduit.
        byte[] tooBig = new byte[2 * 1024 * 1024 + 64];
        for (int i = 0; i < tooBig.length; i++) {
            tooBig[i] = (byte) 'a';
        }
        ResponseEntity<Map<String, Object>> response = uploadBytes("apprenants.csv", tooBig, admin);
        assertThat(response.getStatusCode())
                .isIn(HttpStatus.PAYLOAD_TOO_LARGE, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("IMP_FILE_TOO_LARGE");
    }

    @Test
    void aPedagogicalManagerOutsideItsScopeGetsClassOutOfScopeErrorsAndCannotConfirm() {
        String admin = tokenFor(RoleCode.ADMIN);
        String manager = tokenFor(RoleCode.PEDAGOGICAL_MANAGER); // aucune affectation pédagogique
        Chain chain = academicChain(admin);

        String jobId = (String) upload("scope.csv",
                header() + row("outofscope." + UUID.randomUUID() + "@x.test", chain), manager)
                .getBody().get("publicId");

        Map<String, Object> job = getMap("/api/v1/student-imports/" + jobId, manager);
        assertThat(job.get("confirmable")).isEqualTo(Boolean.FALSE);
        assertThat(((Number) ((Map<?, ?>) job.get("summary")).get("error")).intValue()).isEqualTo(1);

        Map<String, Object> rows = getMap("/api/v1/student-imports/" + jobId + "/rows", manager);
        List<?> content = (List<?>) rows.get("content");
        List<?> issues = (List<?>) ((Map<?, ?>) content.get(0)).get("issues");
        assertThat(issues).anySatisfy(issue ->
                assertThat(((Map<?, ?>) issue).get("code")).isEqualTo("IMP_CLASS_OUT_OF_SCOPE"));

        assertThat(errorCode(post("/api/v1/student-imports/" + jobId + "/confirm", manager)))
                .isEqualTo("IMP_NOT_CONFIRMABLE");
    }

    @Test
    void confirmationNeverWritesAnInvitationLifecycleAuditRowButStillSendsTheEmail() {
        String admin = tokenFor(RoleCode.ADMIN);
        Chain chain = academicChain(admin);
        String email = "t5." + UUID.randomUUID() + "@esic-connect.test";
        String jobId = (String) upload("t5.csv", header() + row(email, chain), admin).getBody().get("publicId");

        long invitationAuditBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE action IN "
                        + "('ACCOUNT_INVITATION_ISSUED','INVITATION_ISSUED')", Long.class);

        post("/api/v1/student-imports/" + jobId + "/confirm", admin);

        // Aucun AccountLifecycleEvent (donc aucune ligne d'audit d'invitation).
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE action IN "
                        + "('ACCOUNT_INVITATION_ISSUED','INVITATION_ISSUED')", Long.class))
                .isEqualTo(invitationAuditBefore);
        // Mais l'e-mail d'invitation est bien parti (AccountInvitationIssuedEvent, AFTER_COMMIT).
        assertThat(SENT_EMAILS).contains(email.toLowerCase());
        // Et exactement une ligne d'audit d'import CONFIRMED.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE category = 'STUDENT_IMPORT' "
                        + "AND action = 'STUDENT_IMPORT_CONFIRMED' "
                        + "AND resource_public_id = UNHEX(REPLACE(?, '-', ''))", Long.class, jobId)).isEqualTo(1L);
    }

    @Test
    void twoImportsSharingAnEmailConfirmedInSequenceCreateNoDuplicateAccount() {
        String admin = tokenFor(RoleCode.ADMIN);
        Chain chain = academicChain(admin);
        String shared = "shared." + UUID.randomUUID() + "@esic-connect.test";

        String jobA = (String) upload("a.csv", header() + row(shared, chain), admin).getBody().get("publicId");
        String jobB = (String) upload("b.csv", header() + row(shared, chain), admin).getBody().get("publicId");

        post("/api/v1/student-imports/" + jobA + "/confirm", admin);
        // Le 2e import : la re-validation reclasse la ligne (le compte existe maintenant) ;
        // jamais de doublon (autorité = unicité SQL de l'e-mail).
        post("/api/v1/student-imports/" + jobB + "/confirm", admin);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE email = ?", Long.class, shared))
                .isEqualTo(1L);
    }

    @Test
    void anonymousIsRejectedOnAllSixEndpoints() {
        String someId = UUID.randomUUID().toString();
        assertThat(uploadBytes("f.csv", "x".getBytes(StandardCharsets.UTF_8), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        for (String path : List.of(
                "/api/v1/student-imports",
                "/api/v1/student-imports/" + someId,
                "/api/v1/student-imports/" + someId + "/rows")) {
            assertThat(status(HttpMethod.GET, path, null)).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(status(HttpMethod.POST, "/api/v1/student-imports/" + someId + "/confirm", null))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(status(HttpMethod.POST, "/api/v1/student-imports/" + someId + "/cancel", null))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------

    private static String header() {
        return "last_name,first_name,email,formation_code,class_code,academic_year\n";
    }

    private static String row(String email, Chain chain) {
        return "Nom,Prenom," + email + "," + chain.programCode() + "," + chain.classCode() + ","
                + chain.yearCode() + "\n";
    }

    private ResponseEntity<Map<String, Object>> upload(String fileName, String csv, String token) {
        return uploadBytes(fileName, csv.getBytes(StandardCharsets.UTF_8), token);
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
        UserAccount account = new UserAccount("rec-" + UUID.randomUUID() + "@esic-connect.test",
                "Rec", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            userRoleRepository.saveAndFlush(new UserRole(account,
                    roleRepository.findByCode(roleCode).orElseThrow(), Instant.now(), true));
        }
        Map<String, Object> body = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", account.getEmail(), "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) body.get("accessToken");
    }
}
