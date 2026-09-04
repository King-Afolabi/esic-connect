package com.esic.connect.coursesession;

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
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 6 — extensions du cycle de vie d'une séance :
 *
 * <ul>
 *   <li>{@code EF-SES-007} report d'une séance annulée : l'originale reste
 *       annulée et consultable, la remplaçante lui est liée (docs/02 §14.4) ;</li>
 *   <li>{@code EF-SES-008} demande d'annulation par le formateur, décidée
 *       par le responsable — « le formateur demande, il ne décide pas »
 *       (RG-024, docs/02 §5.6) ;</li>
 *   <li>{@code EF-SES-009} séance rattachée à plusieurs classes (RG-023).</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SessionLifecycleExtensionIntegrationTests {

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

    // ---------------------------------------------------------------
    // EF-SES-007 — report
    // ---------------------------------------------------------------

    @Test
    void leReportCreeUneSeanceLieeSansRessusciterLoriginale() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        Map<String, Object> session = created("/api/v1/sessions",
                sessionBody(teacher.publicId(), List.of(chain.classA()), "Réseaux"), admin);
        String sessionId = (String) session.get("publicId");

        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + sessionId + "/cancel",
                Map.of("reason", "formateur souffrant"), admin).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        Instant newStart = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(7 * 24 * 3600);
        Map<String, Object> replacement = created("/api/v1/sessions/" + sessionId + "/postpone",
                Map.of("startsAt", newStart.toString(),
                        "endsAt", newStart.plusSeconds(3 * 3600).toString(),
                        "timeZoneId", "Europe/Paris",
                        "reason", "report à la semaine suivante",
                        "classPublicIds", List.of(chain.classA())), admin);

        assertThat(replacement.get("publicId")).isNotEqualTo(sessionId);
        assertThat(replacement.get("status")).isEqualTo("PLANNED");
        assertThat(replacement.get("startsAt")).isEqualTo(newStart.toString());

        // L'originale n'est pas ressuscitée : elle reste CANCELLED et
        // consultable, en portant le lien vers sa remplaçante (docs/02 §14.4).
        Map<String, Object> original = getMap("/api/v1/sessions/" + sessionId, admin);
        assertThat(original.get("status")).isEqualTo("CANCELLED");
        assertThat(original.get("cancellationReason")).isEqualTo("formateur souffrant");
        assertThat(original.get("postponedToPublicId")).isEqualTo(replacement.get("publicId"));
    }

    @Test
    void seuleUneSeanceAnnuleePeutEtreReportee() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String sessionId = (String) created("/api/v1/sessions",
                sessionBody(teacher.publicId(), List.of(chain.classA()), "Bases de données"), admin)
                .get("publicId");

        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + sessionId + "/postpone", postponeBody(chain.classA()), admin);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().get("code")).isEqualTo("SESSION_INVALID_STATE");
    }

    @Test
    void uneSeanceNestReporteeQuUneFois() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String sessionId = (String) created("/api/v1/sessions",
                sessionBody(teacher.publicId(), List.of(chain.classA()), "Algorithmique"), admin)
                .get("publicId");
        exchange(HttpMethod.POST, "/api/v1/sessions/" + sessionId + "/cancel",
                Map.of("reason", "salle indisponible"), admin);

        created("/api/v1/sessions/" + sessionId + "/postpone", postponeBody(chain.classA()), admin);
        ResponseEntity<Map<String, Object>> second = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + sessionId + "/postpone", postponeBody(chain.classA()), admin);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("code")).isEqualTo("SESSION_ALREADY_POSTPONED");
    }

    @Test
    void leFormateurNeReportePasLuiMeme() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String sessionId = (String) created("/api/v1/sessions",
                sessionBody(teacher.publicId(), List.of(chain.classA()), "Anglais"), admin)
                .get("publicId");
        exchange(HttpMethod.POST, "/api/v1/sessions/" + sessionId + "/cancel",
                Map.of("reason", "intempéries"), admin);

        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + sessionId + "/postpone",
                postponeBody(chain.classA()), tokenFor(teacher)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---------------------------------------------------------------
    // EF-SES-008 — demande d'annulation
    // ---------------------------------------------------------------

    @Test
    void leFormateurDemandeEtLeResponsableDecide() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String sessionId = (String) created("/api/v1/sessions",
                sessionBody(teacher.publicId(), List.of(chain.classA()), "Systèmes"), admin)
                .get("publicId");

        Map<String, Object> request = created("/api/v1/sessions/" + sessionId + "/cancellation-requests",
                Map.of("reason", "je suis en arrêt maladie"), tokenFor(teacher));
        assertThat(request.get("status")).isEqualTo("REQUESTED");

        // Le formateur ne décide pas de sa propre demande (RG-024).
        assertThat(exchange(HttpMethod.POST,
                "/api/v1/sessions/cancellation-requests/" + request.get("publicId") + "/decision",
                Map.of("approved", true), tokenFor(teacher)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map<String, Object>> decision = exchange(HttpMethod.POST,
                "/api/v1/sessions/cancellation-requests/" + request.get("publicId") + "/decision",
                Map.of("approved", true, "comment", "remplacement impossible"), admin);
        assertThat(decision.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(decision.getBody().get("status")).isEqualTo("APPROVED");

        // Une acceptation annule effectivement la séance, avec motif.
        Map<String, Object> session = getMap("/api/v1/sessions/" + sessionId, admin);
        assertThat(session.get("status")).isEqualTo("CANCELLED");
        assertThat((String) session.get("cancellationReason")).contains("arrêt maladie");
    }

    @Test
    void unRefusLaisseLaSeancePlanifiee() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String sessionId = (String) created("/api/v1/sessions",
                sessionBody(teacher.publicId(), List.of(chain.classA()), "Économie"), admin)
                .get("publicId");
        Map<String, Object> request = created("/api/v1/sessions/" + sessionId + "/cancellation-requests",
                Map.of("reason", "conflit d'agenda"), tokenFor(teacher));

        ResponseEntity<Map<String, Object>> decision = exchange(HttpMethod.POST,
                "/api/v1/sessions/cancellation-requests/" + request.get("publicId") + "/decision",
                Map.of("approved", false, "comment", "un remplaçant est disponible"), admin);

        assertThat(decision.getBody().get("status")).isEqualTo("REJECTED");
        assertThat(getMap("/api/v1/sessions/" + sessionId, admin).get("status")).isEqualTo("PLANNED");
    }

    @Test
    void uneSeuleDemandeEnAttenteParSeance() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String sessionId = (String) created("/api/v1/sessions",
                sessionBody(teacher.publicId(), List.of(chain.classA()), "Droit"), admin)
                .get("publicId");
        String teacherToken = tokenFor(teacher);
        created("/api/v1/sessions/" + sessionId + "/cancellation-requests",
                Map.of("reason", "première demande"), teacherToken);

        ResponseEntity<Map<String, Object>> second = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + sessionId + "/cancellation-requests",
                Map.of("reason", "deuxième demande"), teacherToken);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("code")).isEqualTo("SESSION_CANCELLATION_ALREADY_REQUESTED");
    }

    @Test
    void leDemandeurRetireSaDemandeEtPeutEnDeposerUneAutre() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String sessionId = (String) created("/api/v1/sessions",
                sessionBody(teacher.publicId(), List.of(chain.classA()), "Marketing"), admin)
                .get("publicId");
        String teacherToken = tokenFor(teacher);
        Map<String, Object> request = created("/api/v1/sessions/" + sessionId + "/cancellation-requests",
                Map.of("reason", "erreur de saisie"), teacherToken);

        assertThat(exchange(HttpMethod.POST,
                "/api/v1/sessions/cancellation-requests/" + request.get("publicId") + "/withdraw",
                null, teacherToken).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        // Historique conservé : la demande retirée reste listée.
        List<Map<String, Object>> requests = list("/api/v1/sessions/" + sessionId
                + "/cancellation-requests", admin);
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).get("status")).isEqualTo("WITHDRAWN");

        assertThat(created("/api/v1/sessions/" + sessionId + "/cancellation-requests",
                Map.of("reason", "vraie demande"), teacherToken).get("status"))
                .isEqualTo("REQUESTED");
    }

    // ---------------------------------------------------------------
    // EF-SES-009 — séance multi-classes
    // ---------------------------------------------------------------

    @Test
    void uneSeanceRattacheDeuxClasses() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);

        Map<String, Object> session = created("/api/v1/sessions",
                sessionBody(teacher.publicId(), List.of(chain.classA(), chain.classB()),
                        "Cours commun"), admin);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> classes = (List<Map<String, Object>>) session.get("classes");
        assertThat(classes).hasSize(2);
        assertThat(classes.stream().map(entry -> entry.get("publicId")))
                .containsExactlyInAnyOrder(chain.classA(), chain.classB());
    }

    // ---------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------

    private Map<String, Object> postponeBody(String classPublicId) {
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(10 * 24 * 3600);
        return Map.of("startsAt", start.toString(),
                "endsAt", start.plusSeconds(3 * 3600).toString(),
                "timeZoneId", "Europe/Paris",
                "reason", "nouvelle date convenue",
                "classPublicIds", List.of(classPublicId));
    }

    private Map<String, Object> sessionBody(String teacherPublicId, List<String> classPublicIds,
                                            String title) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        HashMap<String, Object> body = new HashMap<>();
        body.put("teacherPublicId", teacherPublicId);
        body.put("classPublicIds", classPublicIds);
        body.put("startsAt", now.minusSeconds(3600).toString());
        body.put("endsAt", now.plusSeconds(3 * 3600).toString());
        body.put("timeZoneId", "Europe/Paris");
        body.put("reason", "séance exceptionnelle");
        body.put("title", title);
        return body;
    }

    private record Chain(String classA, String classB) {
    }

    private Chain academicChain(String admin) {
        String site = (String) created("/api/v1/sites", Map.of("code", "SITE-" + UUID.randomUUID(),
                "name", "Campus", "timeZoneId", "Europe/Paris"), admin).get("publicId");
        String program = (String) created("/api/v1/programs", Map.of("code", "PRG-" + code(),
                "name", "BTS SIO", "programType", "BTS"), admin).get("publicId");
        String level = (String) created("/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1", "name", "BTS 1", "sequenceNumber", 1), admin).get("publicId");
        String year = (String) created("/api/v1/academic-years", Map.of("code", "AY-" + code(),
                "name", "2026-2027", "startDate", "2026-09-01", "endDate", "2027-08-31"), admin)
                .get("publicId");
        String promo = (String) created("/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P26", "name", "Promotion 2026"), admin)
                .get("publicId");
        String classA = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C1", "name", "Classe 1"),
                admin).get("publicId");
        String classB = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C2", "name", "Classe 2"),
                admin).get("publicId");
        return new Chain(classA, classB);
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET, path, null, token);
        assertThat(response.getStatusCode()).as("GET " + path).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<Map<String, Object>> list(String path, String token) {
        RequestEntity<?> entity = RequestEntity.get(URI.create(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build();
        ResponseEntity<List<Map<String, Object>>> response =
                restTemplate.exchange(entity, new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).as("GET " + path).isEqualTo(HttpStatus.OK);
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
        return restTemplate.exchange(entity, new ParameterizedTypeReference<>() {
        });
    }

    private record Account(String publicId, String email) {
    }

    private Account accountWithRoles(RoleCode... roles) {
        UserAccount account = new UserAccount("sle-" + UUID.randomUUID() + "@esic-connect.test",
                "Cycle", "Testeur", AccountStatus.ACTIVE);
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
        return AuthTestSupport.accessToken(restTemplate, account.email(), PASSWORD);
    }

    private static String code() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
