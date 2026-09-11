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
 * Matière et salle d'une séance (V35) : sélection à la création,
 * affectation tardive de la salle, et contrôle anti-double-réservation
 * (RG-105) — un formateur ou une salle ne peuvent pas porter deux séances
 * dont les horaires se chevauchent, sauf lorsque plusieurs classes suivent
 * la même séance (une seule séance, jamais deux).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SessionSubjectRoomIntegrationTests {

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
    void uneSeanceEstCreeeAvecUneMatiereEtUneSalle() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String subject = createSubject(admin);
        String room = createRoom(admin, chain.site());

        Map<String, Object> body = createBody(teacher.publicId(), List.of(chain.classA()), "Anglais");
        body.put("subjectPublicId", subject);
        body.put("roomPublicId", room);
        Map<String, Object> created = created("/api/v1/sessions", body, admin);

        Map<?, ?> subjectView = (Map<?, ?>) created.get("subject");
        assertThat(subjectView.get("publicId")).isEqualTo(subject);
        assertThat(created.get("roomCode")).isNotNull();
    }

    @Test
    void laMatiereEtLaSalleSontFacultativesALaCreation() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);

        Map<String, Object> created = created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), "Sans matière ni salle"), admin);

        assertThat(created.get("subject")).isNull();
        assertThat(created.get("roomCode")).isNull();
    }

    @Test
    void laSalleSePreciseSouventAuDernierMomentApresCreation() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String room = createRoom(admin, chain.site());

        String publicId = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), "À préciser plus tard"), admin)
                .get("publicId");

        Map<String, Object> response = post("/api/v1/sessions/" + publicId + "/room",
                Map.of("roomPublicId", room), admin);
        assertThat(response.get("roomCode")).isNotNull();
    }

    @Test
    void unFormateurNePeutPasEtreSurDeuxSeancesQuiSeChevauchent() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        created("/api/v1/sessions", createBody(teacher.publicId(), List.of(chain.classA()), "Première"), admin);

        // Même formateur, même créneau, classe différente : doit être
        // déclaré sur LA MÊME séance (plusieurs classPublicIds), pas sur
        // une seconde séance.
        ResponseEntity<Map<String, Object>> conflict = exchange(HttpMethod.POST, "/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classB()), "Seconde"), admin);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().get("code")).isEqualTo("SESSION_TEACHER_DOUBLE_BOOKING");
    }

    @Test
    void deuxClassesQuiSuiventLaMemeSeanceEnsembleNeSontJamaisUnConflit() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);

        // Une seule séance, deux classes : c'est exactement la façon de
        // déclarer un cours commun — jamais un conflit.
        Map<String, Object> created = created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA(), chain.classB()), "Cours commun"), admin);
        List<?> classes = (List<?>) created.get("classes");
        assertThat(classes).hasSize(2);
    }

    @Test
    void uneSalleNePeutPasEtreOccupeeParDeuxSeancesQuiSeChevauchent() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacherA = accountWithRoles(RoleCode.TEACHER);
        Account teacherB = accountWithRoles(RoleCode.TEACHER);
        String room = createRoom(admin, chain.site());

        Map<String, Object> firstBody = createBody(teacherA.publicId(), List.of(chain.classA()), "Première");
        firstBody.put("roomPublicId", room);
        created("/api/v1/sessions", firstBody, admin);

        Map<String, Object> secondBody = createBody(teacherB.publicId(), List.of(chain.classB()), "Seconde");
        secondBody.put("roomPublicId", room);
        ResponseEntity<Map<String, Object>> conflict = exchange(HttpMethod.POST, "/api/v1/sessions", secondBody, admin);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().get("code")).isEqualTo("SESSION_ROOM_DOUBLE_BOOKING");
    }

    @Test
    void uneMatiereArchiveeNePeutPasEtreRattacheeAUneNouvelleSeance() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String subject = createSubject(admin);
        created("/api/v1/subjects/" + subject + "/archive", Map.of("reason", "test"), admin);

        Map<String, Object> body = createBody(teacher.publicId(), List.of(chain.classA()), "Matière archivée");
        body.put("subjectPublicId", subject);
        ResponseEntity<Map<String, Object>> conflict = exchange(HttpMethod.POST, "/api/v1/sessions", body, admin);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().get("code")).isEqualTo("SESSION_SUBJECT_INACTIVE");
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

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

    private Map<String, Object> post(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.OK);
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
        UserAccount account = new UserAccount("ssr-" + UUID.randomUUID() + "@esic-connect.test",
                "Ssr", "Tester", AccountStatus.ACTIVE);
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
