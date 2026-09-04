package com.esic.connect.enrollment;

import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
import com.esic.connect.support.AuthTestSupport;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Suivi à distance individuel (EF-ENR-004 ; docs/02 §15.3).
 *
 * <p>Ce que le cahier demande est net : « sans autorisation, le canal
 * distant est refusé ». Les tests vérifient donc les deux versants — une
 * autorisation valable ouvre le canal, son absence, sa révocation et son
 * expiration le referment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RemoteAttendanceIntegrationTests {

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
    private TestRestTemplate rest;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void useJdkClient() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void uneAutorisationEstCreeeRevocableEtConserveeApresRevocation() {
        String admin = adminToken();
        String classId = classGroup(admin);
        Account student = accountWithRoles(RoleCode.STUDENT);

        Map<String, Object> authorization = created("/api/v1/remote-attendance-authorizations",
                Map.of("studentUserPublicId", student.publicId(),
                        "classGroupPublicId", classId,
                        "reason", "immobilisation médicale de trois semaines",
                        "validFrom", LocalDate.of(2026, 10, 1).toString(),
                        "validUntil", LocalDate.of(2026, 10, 21).toString()), admin);

        assertThat(authorization.get("status")).isEqualTo("ACTIVE");
        assertThat(authorization.get("studentUserPublicId")).isEqualTo(student.publicId());

        Map<String, Object> revoked = post("/api/v1/remote-attendance-authorizations/"
                + authorization.get("publicId") + "/revoke",
                Map.of("reason", "reprise des cours sur site"), admin);
        assertThat(revoked.get("status")).isEqualTo("REVOKED");
        assertThat(revoked.get("revocationReason")).isEqualTo("reprise des cours sur site");

        // L'autorisation n'est jamais supprimée : la décision reste au
        // dossier de l'apprenant.
        List<Map<String, Object>> history = list("/api/v1/remote-attendance-authorizations/students/"
                + student.publicId(), admin);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("status")).isEqualTo("REVOKED");
    }

    @Test
    void uneRevocationNEstPasRejouable() {
        String admin = adminToken();
        String classId = classGroup(admin);
        Account student = accountWithRoles(RoleCode.STUDENT);
        Map<String, Object> authorization = authorize(admin, student, classId,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 21));
        post("/api/v1/remote-attendance-authorizations/" + authorization.get("publicId") + "/revoke",
                Map.of("reason", "première révocation"), admin);

        ResponseEntity<Map<String, Object>> second = exchange(HttpMethod.POST,
                "/api/v1/remote-attendance-authorizations/" + authorization.get("publicId") + "/revoke",
                Map.of("reason", "seconde tentative"), admin);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unePeriodeInverseeEstRefusee() {
        String admin = adminToken();
        String classId = classGroup(admin);
        Account student = accountWithRoles(RoleCode.STUDENT);

        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.POST,
                "/api/v1/remote-attendance-authorizations",
                Map.of("studentUserPublicId", student.publicId(),
                        "classGroupPublicId", classId,
                        "reason", "période absurde",
                        "validFrom", "2026-10-21",
                        "validUntil", "2026-10-01"), admin);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unCompteSansRoleApprenantNEstPasAutorisable() {
        String admin = adminToken();
        String classId = classGroup(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);

        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.POST,
                "/api/v1/remote-attendance-authorizations",
                Map.of("studentUserPublicId", teacher.publicId(),
                        "classGroupPublicId", classId,
                        "reason", "erreur de destinataire",
                        "validFrom", "2026-10-01"), admin);

        // Une autorisation qui ne s'appliquerait jamais est refusée tout de
        // suite, avec un motif compréhensible.
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getBody().get("code")).isEqualTo("ENR_USER_NOT_ELIGIBLE");
    }

    @Test
    void unFormateurNAutorisePasUnSuiviADistance() {
        String admin = adminToken();
        String classId = classGroup(admin);
        Account student = accountWithRoles(RoleCode.STUDENT);
        Account teacher = accountWithRoles(RoleCode.TEACHER);

        // Le formateur signale une exception, il ne l'accorde pas
        // (docs/02 §15.3).
        assertThat(exchange(HttpMethod.POST, "/api/v1/remote-attendance-authorizations",
                Map.of("studentUserPublicId", student.publicId(),
                        "classGroupPublicId", classId,
                        "reason", "tentative",
                        "validFrom", "2026-10-01"), tokenFor(teacher)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unApprenantNeSAutorisePasLuiMeme() {
        String admin = adminToken();
        String classId = classGroup(admin);
        Account student = accountWithRoles(RoleCode.STUDENT);

        assertThat(exchange(HttpMethod.POST, "/api/v1/remote-attendance-authorizations",
                Map.of("studentUserPublicId", student.publicId(),
                        "classGroupPublicId", classId,
                        "reason", "je préfère rester chez moi",
                        "validFrom", "2026-10-01"), tokenFor(student)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unAnonymeNAccedePasAuxAutorisations() {
        Account student = accountWithRoles(RoleCode.STUDENT);

        assertThat(exchange(HttpMethod.GET, "/api/v1/remote-attendance-authorizations/students/"
                + student.publicId(), null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------

    private Map<String, Object> authorize(String token, Account student, String classId,
                                          LocalDate from, LocalDate until) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("studentUserPublicId", student.publicId());
        body.put("classGroupPublicId", classId);
        body.put("reason", "suivi à distance autorisé");
        body.put("validFrom", from.toString());
        if (until != null) {
            body.put("validUntil", until.toString());
        }
        return created("/api/v1/remote-attendance-authorizations", body, token);
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> post(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<Map<String, Object>> list(String path, String token) {
        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                RequestEntity.get(URI.create(path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<Map<String, Object>> exchange(HttpMethod method, String path,
                                                         Map<String, Object> body, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(method, URI.create(path));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        RequestEntity<?> entity = body == null
                ? builder.build()
                : builder.contentType(MediaType.APPLICATION_JSON).body(body);
        return rest.exchange(entity, new ParameterizedTypeReference<>() {
        });
    }

    private String classGroup(String admin) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String site = (String) created("/api/v1/sites", Map.of("code", "SITE-" + suffix,
                "name", "Campus", "timeZoneId", "Europe/Paris"), admin).get("publicId");
        String program = (String) created("/api/v1/programs", Map.of("code", "PRG-" + suffix,
                "name", "BTS SIO", "programType", "BTS"), admin).get("publicId");
        String level = (String) created("/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1-" + suffix, "name", "BTS 1", "sequenceNumber", 1), admin).get("publicId");
        String year = (String) created("/api/v1/academic-years", Map.of("code", "AY-" + suffix,
                "name", "2026-2027", "startDate", "2026-09-01", "endDate", "2027-08-31"), admin)
                .get("publicId");
        String promo = (String) created("/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P-" + suffix, "name", "Promotion"), admin)
                .get("publicId");
        return (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C-" + suffix,
                "name", "Classe"), admin).get("publicId");
    }

    private record Account(String publicId, String email) {
    }

    private Account accountWithRoles(RoleCode... roles) {
        UserAccount account = new UserAccount("rem-" + UUID.randomUUID() + "@esic-connect.test",
                "Distance", "Testeur", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return new Account(account.getPublicId().toString(), account.getEmail());
    }

    private String adminToken() {
        return tokenFor(accountWithRoles(RoleCode.ADMIN));
    }

    private String tokenFor(Account account) {
        return AuthTestSupport.accessToken(rest, account.email(), PASSWORD);
    }
}
