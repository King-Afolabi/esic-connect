package com.esic.connect.notification.internal;

import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.support.AuthTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Audience complète d'une notification (EF-NOTIF-002, EF-NOTIF-003 ;
 * docs/02 §21.3).
 *
 * <p>Ce qui manquait jusqu'ici : seul le formateur était prévenu. Une
 * séance annulée concerne pourtant d'abord les apprenants qui allaient
 * s'y rendre, et le responsable pédagogique qui en répond.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificationAudienceIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";

    @TestConfiguration
    static class NoopMailerConfig {

        @Bean
        @Primary
        InvitationMailer noopInvitationMailer() {
            return (a, b, c, d) -> {
            };
        }

        @Bean
        @Primary
        NotificationMailer noopNotificationMailer() {
            return (a, b, c) -> {
            };
        }
    }

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
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
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    @DisplayName("EF-NOTIF-003 : une annulation prévient le formateur, les apprenants ET le responsable")
    void cancellingASessionNotifiesTeacherStudentsAndManager() {
        String admin = adminToken();
        Fixture fixture = fixture(admin);

        assertThat(post(admin, "/api/v1/sessions/" + fixture.sessionId() + "/cancel",
                Map.of("reason", "Alerte bâtiment"))).isEqualTo(HttpStatus.NO_CONTENT);

        UUID session = UUID.fromString(fixture.sessionId());
        assertThat(isNotified(fixture.teacher(), session)).as("le formateur").isTrue();
        assertThat(isNotified(fixture.student(), session)).as("l'apprenant attendu").isTrue();
        assertThat(isNotified(fixture.manager(), session)).as("le responsable pédagogique").isTrue();

        // Un apprenant d'une autre classe n'est jamais prévenu : l'audience
        // se déduit de la ressource, pas d'une diffusion générale.
        assertThat(isNotified(fixture.outsider(), session)).as("l'apprenant d'une autre classe").isFalse();
    }

    @Test
    @DisplayName("un changement de formateur ne réveille pas toute la classe")
    void aSubstitutionDoesNotNotifyStudents() {
        String admin = adminToken();
        Fixture fixture = fixture(admin);
        Account substitute = account(RoleCode.TEACHER);

        Instant base = Instant.now();
        created(admin, "/api/v1/sessions/" + fixture.sessionId() + "/substitutions", Map.of(
                "substituteTeacherPublicId", substitute.publicId(),
                "reason", "Formateur principal indisponible",
                // La période doit chevaucher la séance, planifiée dans
                // une heure par la fixture.
                "validFrom", base.plusSeconds(3600).toString(),
                "validUntil", base.plusSeconds(7200).toString()));

        UUID session = UUID.fromString(fixture.sessionId());
        assertThat(isNotified(fixture.teacher(), session)).as("le formateur initial").isTrue();
        assertThat(isNotified(substitute, session)).as("le remplaçant").isTrue();
        assertThat(isNotified(fixture.manager(), session)).as("le responsable").isTrue();
        // Prévenir la classe de chaque remplacement transformerait le
        // centre de notifications en bruit de fond.
        assertThat(isNotified(fixture.student(), session)).as("l'apprenant").isFalse();
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    private record Fixture(String sessionId, Account teacher, Account student, Account manager,
                           Account outsider) {
    }

    private Fixture fixture(String admin) {
        String site = (String) created(admin, "/api/v1/sites", Map.of("code", "SITE-" + code(),
                "name", "Campus", "timeZoneId", "Europe/Paris")).get("publicId");
        String program = (String) created(admin, "/api/v1/programs", Map.of("code", "PRG-" + code(),
                "name", "BTS SIO", "programType", "BTS")).get("publicId");
        String level = (String) created(admin, "/api/v1/programs/" + program + "/levels",
                Map.of("code", "N1", "name", "BTS 1", "sequenceNumber", 1)).get("publicId");
        String year = (String) created(admin, "/api/v1/academic-years", Map.of("code", "AY-" + code(),
                "name", "2026-2027", "startDate", "2026-09-01", "endDate", "2027-08-31")).get("publicId");
        String promo = (String) created(admin, "/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P" + code(), "name", "Promotion"))
                .get("publicId");
        String classA = (String) created(admin, "/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C" + code(),
                "name", "Classe A")).get("publicId");
        String classB = (String) created(admin, "/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C" + code(),
                "name", "Classe B")).get("publicId");

        Account manager = account(RoleCode.PEDAGOGICAL_MANAGER);
        created(admin, "/api/v1/pedagogical-assignments", Map.of("programPublicId", program,
                "userPublicId", manager.publicId(), "type", "PRIMARY_MANAGER",
                "validFrom", LocalDate.now().minusDays(1).toString()));

        Account student = enrolled(admin, classA);
        Account outsider = enrolled(admin, classB);
        Account teacher = account(RoleCode.TEACHER);

        Instant now = Instant.now();
        Map<String, Object> body = new HashMap<>();
        body.put("teacherPublicId", teacher.publicId());
        body.put("classPublicIds", List.of(classA));
        body.put("startsAt", now.plusSeconds(3600).toString());
        body.put("endsAt", now.plusSeconds(7200).toString());
        body.put("timeZoneId", "Europe/Paris");
        body.put("reason", "séance exceptionnelle");
        String sessionId = (String) created(admin, "/api/v1/sessions", body).get("publicId");

        return new Fixture(sessionId, teacher, student, manager, outsider);
    }

    private Account enrolled(String admin, String classGroupPublicId) {
        Account student = account(RoleCode.STUDENT);
        String profile = (String) created(admin, "/api/v1/student-profiles",
                Map.of("userPublicId", student.publicId(), "studentNumber", "ESIC-2026-" + code()))
                .get("publicId");
        created(admin, "/api/v1/enrollments", Map.of("studentProfilePublicId", profile,
                "classGroupPublicId", classGroupPublicId, "startDate", "2026-08-01"));
        return student;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean isNotified(Account account, UUID resourcePublicId) {
        Long n = jdbc.queryForObject("""
                select count(*) from notification n
                join user_account u on u.id = n.recipient_user_id
                where u.public_id = UUID_TO_BIN(?) and n.resource_public_id = UUID_TO_BIN(?)
                """, Long.class, account.publicId(), resourcePublicId.toString());
        return n != null && n > 0;
    }

    private Map<String, Object> created(String token, String path, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> r = exchange(HttpMethod.POST, path, body, token);
        assertThat(r.getStatusCode()).as("POST " + path + " -> " + r.getBody())
                .isIn(HttpStatus.CREATED, HttpStatus.OK);
        return r.getBody();
    }

    private HttpStatus post(String token, String path, Map<String, Object> body) {
        return (HttpStatus) exchange(HttpMethod.POST, path, body, token).getStatusCode();
    }

    private ResponseEntity<Map<String, Object>> exchange(HttpMethod method, String path,
                                                         Map<String, Object> body, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(method, URI.create(path));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        RequestEntity<?> entity = body == null ? builder.build()
                : builder.contentType(MediaType.APPLICATION_JSON).body(body);
        return rest.exchange(entity, new ParameterizedTypeReference<>() {
        });
    }

    private record Account(String publicId, String email) {
    }

    private Account account(RoleCode... roles) {
        UserAccount a = new UserAccount("notif-aud-" + UUID.randomUUID() + "@esic-connect.test",
                "Notif", "Audience", AccountStatus.ACTIVE);
        a.setPasswordHash(passwordEncoder.encode(PASSWORD));
        a = userAccountRepository.saveAndFlush(a);
        for (RoleCode rc : roles) {
            Role role = roleRepository.findByCode(rc).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(a, role, Instant.now(), true));
        }
        return new Account(a.getPublicId().toString(), a.getEmail());
    }

    private String adminToken() {
        Account admin = account(RoleCode.ADMIN);
        return AuthTestSupport.accessToken(rest, admin.email(), PASSWORD);
    }

    private static String code() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
