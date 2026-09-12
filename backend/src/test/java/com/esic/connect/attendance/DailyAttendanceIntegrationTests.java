package com.esic.connect.attendance;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Points de contrôle journaliers nommés (EF-ATT-003) et résultat
 * journalier (EF-ATT-004 ; docs/02 §16.2 et §16.3).
 *
 * <p>La table de vérité du cahier est vérifiée cas par cas — c'est elle
 * qui décide si un apprenant est compté présent, et une erreur ici
 * produirait une absence injustifiée dans un rapport officiel.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DailyAttendanceIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    /** Journée de référence : le 5 octobre 2026 est un lundi. */
    private static final String DAY = "2026-10-05";
    private static final String MORNING_START = "2026-10-05T07:00:00Z";
    private static final String MORNING_END = "2026-10-05T10:30:00Z";
    private static final String AFTERNOON_START = "2026-10-05T11:30:00Z";
    private static final String AFTERNOON_END = "2026-10-05T15:00:00Z";

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

    // ------------------------------------------------------------------
    // EF-ATT-003 — les quatre points nommés
    // ------------------------------------------------------------------

    @Test
    void lesQuatrePointsNommesSontCreablesEtUniquesParSeance() {
        String admin = adminToken();
        Fixture fx = fixture(admin);

        for (String type : List.of("MORNING_ARRIVAL", "MORNING_BREAK_RETURN")) {
            Map<String, Object> checkpoint = created(
                    "/api/v1/sessions/" + fx.morningSessionId + "/checkpoints",
                    Map.of("label", type, "type", type), admin);
            assertThat(checkpoint.get("type")).isEqualTo(type);
        }

        // Deux MORNING_ARRIVAL sur la même séance rendraient le résultat
        // journalier indéterminé.
        ResponseEntity<Map<String, Object>> duplicate = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + fx.morningSessionId + "/checkpoints",
                Map.of("label", "doublon", "type", "MORNING_ARRIVAL"), admin);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unTypeInconnuEstRefuseParLaValidation() {
        String admin = adminToken();
        Fixture fx = fixture(admin);

        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + fx.morningSessionId + "/checkpoints",
                Map.of("label", "libre", "type", "EVENING_ARRIVAL"), admin);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------------
    // EF-ATT-004 — table de vérité de docs/02 §16.3
    // ------------------------------------------------------------------

    @Test
    void quatreValidationsCoherentesFontUneJourneeComplete() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String student = tokenFor(fx.student);
        validateAll(admin, student, fx, List.of("MORNING_ARRIVAL", "MORNING_BREAK_RETURN",
                "AFTERNOON_ARRIVAL", "AFTERNOON_BREAK_RETURN"));

        Map<String, Object> row = dailyRow(admin, fx);

        assertThat(row.get("result")).isEqualTo("FULL_DAY");
        assertThat(row.get("morningValidated")).isEqualTo(true);
        assertThat(row.get("afternoonValidated")).isEqualTo(true);
    }

    @Test
    void deuxValidationsDuMatinSurUneJourneeCompleteRestentPartielles() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String student = tokenFor(fx.student);
        validateAll(admin, student, fx, List.of("MORNING_ARRIVAL", "MORNING_BREAK_RETURN",
                "AFTERNOON_ARRIVAL", "AFTERNOON_BREAK_RETURN"),
                List.of("MORNING_ARRIVAL", "MORNING_BREAK_RETURN"));

        Map<String, Object> row = dailyRow(admin, fx);

        // L'après-midi était attendu : le cahier parle alors de
        // validations incomplètes, pas de « demi-journée ».
        assertThat(row.get("result")).isEqualTo("PARTIAL");
        assertThat(row.get("morningValidated")).isEqualTo(true);
        assertThat(row.get("afternoonValidated")).isEqualTo(false);
    }

    @Test
    void unRetourDePauseSansArriveeEstAConfirmer() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String student = tokenFor(fx.student);
        validateAll(admin, student, fx, List.of("MORNING_ARRIVAL", "MORNING_BREAK_RETURN"),
                List.of("MORNING_BREAK_RETURN"));

        Map<String, Object> row = dailyRow(admin, fx);

        // Une séquence impossible appelle un humain, elle ne se tranche
        // pas toute seule.
        assertThat(row.get("result")).isEqualTo("TO_CONFIRM");
    }

    @Test
    void aucuneValidationProduitUneAbsence() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        createCheckpoints(admin, fx, List.of("MORNING_ARRIVAL", "MORNING_BREAK_RETURN"));

        Map<String, Object> row = dailyRow(admin, fx);

        assertThat(row.get("result")).isEqualTo("ABSENT");
    }

    @Test
    void uneJourneeSansPointNommeNEstPasUneAbsence() {
        String admin = adminToken();
        Fixture fx = fixture(admin);

        Map<String, Object> row = dailyRow(admin, fx);

        // Aucun point journalier attendu : compter une absence serait une
        // invention.
        assertThat(row.get("result")).isEqualTo("NOT_EXPECTED");
    }

    @Test
    void lesDeuxPointsDuMatinSeulsAttendusFontUneDemiJournee() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String student = tokenFor(fx.student);
        validateAll(admin, student, fx, List.of("MORNING_ARRIVAL", "MORNING_BREAK_RETURN"));

        Map<String, Object> row = dailyRow(admin, fx);

        assertThat(row.get("result")).isEqualTo("MORNING");
    }

    @Test
    void leRapportJournalierEstRefuseHorsPerimetre() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String student = tokenFor(fx.student);

        assertThat(exchange(HttpMethod.GET, "/api/v1/attendance/reports/daily?classGroup="
                + fx.classId + "&date=" + DAY, null, student).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unFuseauInconnuEstRefuseSansPlanter() {
        String admin = adminToken();
        Fixture fx = fixture(admin);

        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.GET,
                "/api/v1/attendance/reports/daily?classGroup=" + fx.classId + "&date=" + DAY
                        + "&zone=Mars/Olympus", null, admin);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("code")).isEqualTo("ATT_INVALID_SUBMISSION");
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static final class Fixture {
        String classId;
        String morningSessionId;
        String afternoonSessionId;
        Account student;
        String enrollmentId;
        final Map<String, String> checkpointsByType = new HashMap<>();
        final Map<String, String> sessionByType = new HashMap<>();
    }

    private Fixture fixture(String admin) {
        Fixture fx = new Fixture();
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
        fx.classId = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C-" + suffix,
                "name", "Classe"), admin).get("publicId");

        Account teacher = accountWithRoles(RoleCode.TEACHER);
        fx.morningSessionId = openSession(admin, teacher, fx.classId, MORNING_START, MORNING_END);
        fx.afternoonSessionId = openSession(admin, teacher, fx.classId, AFTERNOON_START, AFTERNOON_END);

        fx.student = accountWithRoles(RoleCode.STUDENT);
        fx.enrollmentId = (String) created("/api/v1/enrollments", Map.of(
                "studentUserPublicId", fx.student.publicId(), "classGroupPublicId", fx.classId,
                "startDate", "2026-09-01"), admin).get("publicId");
        return fx;
    }

    private String openSession(String admin, Account teacher, String classId,
                               String startsAt, String endsAt) {
        Map<String, Object> body = new HashMap<>();
        body.put("teacherPublicId", teacher.publicId());
        body.put("classPublicIds", List.of(classId));
        body.put("startsAt", startsAt);
        body.put("endsAt", endsAt);
        body.put("timeZoneId", "Europe/Paris");
        body.put("reason", "séance de test");
        String id = (String) created("/api/v1/sessions", body, admin).get("publicId");
        exchange(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, admin);
        return id;
    }

    private void createCheckpoints(String admin, Fixture fx, List<String> types) {
        for (String type : types) {
            String sessionId = type.startsWith("MORNING") ? fx.morningSessionId : fx.afternoonSessionId;
            Map<String, Object> checkpoint = created("/api/v1/sessions/" + sessionId + "/checkpoints",
                    Map.of("label", type, "type", type), admin);
            fx.checkpointsByType.put(type, (String) checkpoint.get("publicId"));
            fx.sessionByType.put(type, sessionId);
        }
    }

    private void validateAll(String admin, String studentToken, Fixture fx, List<String> expected) {
        validateAll(admin, studentToken, fx, expected, expected);
    }

    /**
     * Crée les points {@code expected} puis fait émarger l'apprenant sur
     * {@code validated} seulement — c'est ce décalage qui produit les cas
     * PARTIAL et TO_CONFIRM de la table du cahier.
     */
    private void validateAll(String admin, String studentToken, Fixture fx,
                             List<String> expected, List<String> validated) {
        createCheckpoints(admin, fx, expected);
        for (String type : validated) {
            String sessionId = fx.sessionByType.get(type);
            String checkpointId = fx.checkpointsByType.get(type);
            exchange(HttpMethod.POST, "/api/v1/sessions/" + sessionId + "/checkpoints/"
                    + checkpointId + "/open", null, admin);
            Map<String, Object> issued = post("/api/v1/sessions/" + sessionId + "/checkpoints/"
                    + checkpointId + "/attendance-token", admin);
            post("/api/v1/attendance/validate",
                    Map.of("shortCode", issued.get("shortCode")), studentToken);
        }
    }

    private Map<String, Object> dailyRow(String admin, Fixture fx) {
        Map<String, Object> report = getMap("/api/v1/attendance/reports/daily?classGroup="
                + fx.classId + "&date=" + DAY, admin);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) report.get("rows");
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    // --- HTTP ---

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> post(String path, String token) {
        return post(path, null, token);
    }

    private Map<String, Object> post(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET, path, null, token);
        assertThat(response.getStatusCode()).as("GET %s -> %s", path, response.getBody())
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
        return rest.exchange(entity, new ParameterizedTypeReference<>() {
        });
    }

    private record Account(String publicId, String email) {
    }

    private Account accountWithRoles(RoleCode... roles) {
        UserAccount account = new UserAccount("day-" + UUID.randomUUID() + "@esic-connect.test",
                "Journee", "Testeur", AccountStatus.ACTIVE);
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
