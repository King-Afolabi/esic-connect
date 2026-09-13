package com.esic.connect.coursesession;

import com.esic.connect.support.AuthTestSupport;
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
 * Édition structurelle complète d'une séance {@code PLANNED} non démarrée
 * (Lot 12 ; docs/02 §14.6) : formateur, classes, matière, salle,
 * horaires, motif, libellé, modalité et lien distant modifiables en une
 * fois, atomiquement, tant qu'aucun émargement n'a pu commencer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SessionStructuralEditIntegrationTests {

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
    void tousLesChampsModifiablesSontAppliquesEnUneFois() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account originalTeacher = accountWithRoles(RoleCode.TEACHER);
        Account newTeacher = accountWithRoles(RoleCode.TEACHER);
        String originalSubject = createSubject(admin);
        String newSubject = createSubject(admin);
        String newRoom = createRoom(admin, chain.site());

        Map<String, Object> createBody = createBody(originalTeacher.publicId(), List.of(chain.classA()), "Original");
        createBody.put("subjectPublicId", originalSubject);
        String publicId = (String) created("/api/v1/sessions", createBody, admin).get("publicId");

        Map<String, Object> update = new HashMap<>();
        update.put("teacherPublicId", newTeacher.publicId());
        update.put("classPublicIds", List.of(chain.classA(), chain.classB()));
        update.put("subjectPublicId", newSubject);
        update.put("roomPublicId", newRoom);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        update.put("startsAt", now.plusSeconds(7200).toString());
        update.put("endsAt", now.plusSeconds(3 * 3600).toString());
        update.put("reason", "motif corrigé");
        update.put("title", "Titre corrigé");
        update.put("attendanceMode", "HYBRID");
        update.put("remoteLink", "https://meet.example.org/edite");

        Map<String, Object> edited = patched("/api/v1/sessions/" + publicId, update, admin);

        assertThat(edited.get("title")).isEqualTo("Titre corrigé");
        assertThat(edited.get("exceptionReason")).isEqualTo("motif corrigé");
        assertThat(((Map<?, ?>) edited.get("teacher")).get("publicId")).isEqualTo(newTeacher.publicId());
        assertThat(((Map<?, ?>) edited.get("subject")).get("publicId")).isEqualTo(newSubject);
        assertThat(edited.get("roomCode")).isNotNull();
        assertThat((List<?>) edited.get("classes")).hasSize(2);
        assertThat(edited.get("attendanceMode")).isEqualTo("HYBRID");
        assertThat(edited.get("remoteLink")).isEqualTo("https://meet.example.org/edite");
    }

    @Test
    void editionRefuseeSiLaSeanceEstDejaOuverte() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String publicId = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), "À ouvrir"), admin).get("publicId");
        created("/api/v1/sessions/" + publicId + "/open", null, admin);

        ResponseEntity<Map<String, Object>> rejected = exchange(HttpMethod.PATCH,
                "/api/v1/sessions/" + publicId, minimalUpdate(teacher.publicId(), chain.classA()), admin);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rejected.getBody().get("code")).isEqualTo("SESSION_INVALID_STATE");
    }

    @Test
    void editionRefuseeSiLaSeanceEstAnnulee() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String publicId = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), "À annuler"), admin).get("publicId");
        created("/api/v1/sessions/" + publicId + "/cancel", Map.of("reason", "test"), admin);

        ResponseEntity<Map<String, Object>> rejected = exchange(HttpMethod.PATCH,
                "/api/v1/sessions/" + publicId, minimalUpdate(teacher.publicId(), chain.classA()), admin);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void editionAtomiqueRienNestModifieSiUneClasseEstInvalide() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String publicId = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), "Original intact"), admin).get("publicId");

        Map<String, Object> update = minimalUpdate(teacher.publicId(), UUID.randomUUID().toString());
        update.put("title", "Ne doit jamais apparaître");

        ResponseEntity<Map<String, Object>> rejected = exchange(HttpMethod.PATCH,
                "/api/v1/sessions/" + publicId, update, admin);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody().get("code")).isEqualTo("SESSION_CLASS_NOT_FOUND");

        Map<String, Object> reloaded = get("/api/v1/sessions/" + publicId, admin);
        assertThat(reloaded.get("title")).isEqualTo("Original intact");
    }

    @Test
    void laCorrectionDuFormateurTitulaireNeCreePasDeRemplacement() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account originalTeacher = accountWithRoles(RoleCode.TEACHER);
        Account correctedTeacher = accountWithRoles(RoleCode.TEACHER);
        String publicId = (String) created("/api/v1/sessions",
                createBody(originalTeacher.publicId(), List.of(chain.classA()), "Titulaire à corriger"), admin)
                .get("publicId");

        patched("/api/v1/sessions/" + publicId, minimalUpdate(correctedTeacher.publicId(), chain.classA()), admin);

        List<?> substitutions = getList("/api/v1/sessions/" + publicId + "/substitutions", admin);
        assertThat(substitutions).isEmpty();
    }

    @Test
    void unFormateurNePeutPasEditerLaStructureDuneSeance() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String publicId = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), "Réservé à la gestion"), admin)
                .get("publicId");
        String teacherToken = tokenFor(teacher);

        ResponseEntity<Map<String, Object>> rejected = exchange(HttpMethod.PATCH,
                "/api/v1/sessions/" + publicId, minimalUpdate(teacher.publicId(), chain.classA()), teacherToken);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void uneEditionVersRemoteSansLienEstRefusee() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String publicId = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), "Présentiel"), admin).get("publicId");

        Map<String, Object> update = minimalUpdate(teacher.publicId(), chain.classA());
        update.put("attendanceMode", "REMOTE");

        ResponseEntity<Map<String, Object>> rejected = exchange(HttpMethod.PATCH,
                "/api/v1/sessions/" + publicId, update, admin);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody().get("code")).isEqualTo("SESSION_REMOTE_LINK_REQUIRED");
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private Map<String, Object> minimalUpdate(String teacherPublicId, String classPublicId) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        HashMap<String, Object> body = new HashMap<>();
        body.put("teacherPublicId", teacherPublicId);
        body.put("classPublicIds", List.of(classPublicId));
        body.put("startsAt", now.plusSeconds(3600).toString());
        body.put("endsAt", now.plusSeconds(3 * 3600).toString());
        body.put("reason", "motif d'édition");
        return body;
    }

    private record Chain(String site, String classA, String classB) {
    }

    private Chain academicChain(String admin) {
        String site = (String) created("/api/v1/sites", Map.of("code", "SITE-" + code(),
                "name", "Campus", "timeZoneId", "Europe/Paris"), admin).get("publicId");
        String program = (String) created("/api/v1/programs", Map.of("code", "PRG-" + code(),
                "name", "BTS SIO", "programType", "BTS"), admin).get("publicId");
        String level = (String) created("/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1", "name", "BTS 1", "sequenceNumber", 1), admin).get("publicId");
        String year = (String) created("/api/v1/academic-years", Map.of("code", "AY-" + code(),
                "name", "2026-2027", "startDate", "2026-09-01", "endDate", "2027-08-31"), admin).get("publicId");
        String promo = (String) created("/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P-" + code(), "name", "Promotion 2026"), admin).get("publicId");
        String classA = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C1-" + code(), "name", "Classe 1"), admin)
                .get("publicId");
        String classB = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C2-" + code(), "name", "Classe 2"), admin)
                .get("publicId");
        return new Chain(site, classA, classB);
    }

    private String createSubject(String admin) {
        return created("/api/v1/subjects", Map.of("code", "MAT-" + code(),
                "name", "Anglais"), admin).get("id").toString();
    }

    private String createRoom(String admin, String sitePublicId) {
        return (String) created("/api/v1/sites/" + sitePublicId + "/rooms", Map.of(
                "code", "A" + code(), "name", "Salle A"), admin).get("publicId");
    }

    private Map<String, Object> createBody(String teacherPublicId, List<String> classPublicIds, String title) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        HashMap<String, Object> body = new HashMap<>();
        body.put("teacherPublicId", teacherPublicId);
        body.put("classPublicIds", classPublicIds);
        body.put("startsAt", now.plusSeconds(3600).toString());
        body.put("endsAt", now.plusSeconds(3 * 3600).toString());
        body.put("timeZoneId", "Europe/Paris");
        body.put("reason", "séance de test");
        if (title != null) {
            body.put("title", title);
        }
        return body;
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody())
                .isIn(HttpStatus.CREATED, HttpStatus.OK, HttpStatus.NO_CONTENT);
        return response.getBody() != null ? response.getBody() : Map.of();
    }

    private Map<String, Object> patched(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.PATCH, path, body, token);
        assertThat(response.getStatusCode()).as("PATCH " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<String, Object> get(String path, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET, path, null, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<Object> getList(String path, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(HttpMethod.GET, URI.create(path));
        builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        ResponseEntity<List<Object>> response = restTemplate.exchange(builder.build(),
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
        return restTemplate.exchange(entity, new ParameterizedTypeReference<>() {
        });
    }

    private record Account(String publicId, String email) {
    }

    private Account accountWithRoles(RoleCode... roles) {
        UserAccount account = new UserAccount("sse-" + UUID.randomUUID() + "@esic-connect.test",
                "Sse", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, java.time.Instant.now(), true));
        }
        return new Account(account.getPublicId().toString(), account.getEmail());
    }

    private String adminToken() {
        return tokenFor(RoleCode.ADMIN);
    }

    private String tokenFor(RoleCode... roles) {
        return tokenFor(accountWithRoles(roles));
    }

    private String tokenFor(Account account) {
        return AuthTestSupport.accessToken(restTemplate, account.email(), PASSWORD);
    }

    private static String code() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
