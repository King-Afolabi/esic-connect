package com.esic.connect.planning;

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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Construction directe d'un planning dans le calendrier (EF-PLAN-006 ;
 * docs/02 §13.7).
 *
 * <p>Ce qui est vérifié ici n'est pas seulement « on peut ajouter un
 * créneau » : c'est que le calendrier emprunte <strong>le même</strong>
 * chemin que l'import — mêmes conflits, même brouillon, même publication
 * atomique versionnée. Un calendrier qui publierait par un raccourci
 * serait une seconde porte d'entrée non contrôlée sur les séances.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlanningCalendarIntegrationTests {

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

    @Test
    void unCreneauAjouteApparaitDansLeBrouillonPuisDevientUneSeanceApresPublication() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();

        Map<String, Object> job = addSlot(admin, classId,
                slot("2026-11-09", "09:00", "12:30", "Réseaux", teacher, room()));
        assertThat(job.get("status")).isEqualTo("SIMULATED");
        assertThat(job.get("totalRows")).isEqualTo(1);
        assertThat(job.get("errorRows")).isEqualTo(0);

        // Le brouillon est visible dans le calendrier, distinct du publié.
        Map<String, Object> calendar = calendar(admin, classId, "2026-11-01", "2026-11-30");
        assertThat(listOf(calendar, "published")).isEmpty();
        assertThat(listOf(calendar, "draft")).hasSize(1);
        assertThat(calendar.get("draftJobPublicId")).isEqualTo(job.get("publicId"));

        // Publication par la voie normale : aucune route propre au calendrier.
        Map<String, Object> publication = post("/api/v1/planning-imports/"
                + job.get("publicId") + "/publish", admin);
        assertThat(publication.get("versionNumber")).isEqualTo(1);

        Map<String, Object> after = calendar(admin, classId, "2026-11-01", "2026-11-30");
        assertThat(listOf(after, "published")).hasSize(1);
        assertThat(listOf(after, "draft")).isEmpty();
        assertThat(after.get("publishedVersionNumber")).isEqualTo(1);
    }

    @Test
    void deplacerUnCreneauLeveLeConflitQuIlCreait() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        String salle = room();

        addSlot(admin, classId, slot("2026-11-10", "09:00", "12:30", "Réseaux", teacher, salle));
        Map<String, Object> job = addSlot(admin, classId,
                slot("2026-11-10", "09:00", "12:30", "Systèmes", teacher, salle));

        // Deux cours au même moment pour le même formateur : conflit.
        assertThat(job.get("errorRows")).isEqualTo(2);
        assertThat(job.get("confirmable")).isEqualTo(false);

        String rowId = (String) rows((String) job.get("publicId"), admin).get(1).get("publicId");
        Map<String, Object> moved = patch("/api/v1/planning/drafts/" + job.get("publicId")
                        + "/slots/" + rowId,
                slot("2026-11-10", "13:30", "17:00", "Systèmes", teacher, salle), admin);

        // Déplacer relance l'analyse du lot entier : le conflit tombe des
        // DEUX côtés, pas seulement sur la ligne touchée.
        assertThat(moved.get("errorRows")).isEqualTo(0);
        assertThat(moved.get("confirmable")).isEqualTo(true);
    }

    @Test
    void supprimerUnCreneauDuBrouillonLeRetireDuLot() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();

        addSlot(admin, classId, slot("2026-11-11", "09:00", "12:30", "Réseaux", teacher, room()));
        Map<String, Object> job = addSlot(admin, classId,
                slot("2026-11-12", "09:00", "12:30", "Systèmes", teacher, room()));
        String rowId = (String) rows((String) job.get("publicId"), admin).get(0).get("publicId");

        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.DELETE,
                "/api/v1/planning/drafts/" + job.get("publicId") + "/slots/" + rowId, null, admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("totalRows")).isEqualTo(1);
    }

    @Test
    void dupliquerUneSemaineRecopieSesCreneauxSurLaSuivante() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();

        // Lundi 9 et mardi 10 novembre 2026.
        addSlot(admin, classId, slot("2026-11-09", "09:00", "12:30", "Réseaux", teacher, room()));
        Map<String, Object> job = addSlot(admin, classId,
                slot("2026-11-10", "09:00", "12:30", "Systèmes", teacher, room()));

        Map<String, Object> duplicated = postBody("/api/v1/planning/drafts/"
                        + job.get("publicId") + "/duplicate-week",
                Map.of("sourceWeekStart", "2026-11-09", "targetWeekStart", "2026-11-16"), admin);

        assertThat(duplicated.get("totalRows")).isEqualTo(4);
        assertThat(duplicated.get("errorRows")).isEqualTo(0);
        List<String> dates = rows((String) job.get("publicId"), admin).stream()
                .map(row -> (String) row.get("sessionDate")).toList();
        // Le lundi reste un lundi, le mardi un mardi.
        assertThat(dates).contains("2026-11-16", "2026-11-17");
    }

    @Test
    void dupliquerUneSemaineVideEstRefuse() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        Map<String, Object> job = addSlot(admin, classId,
                slot("2026-11-09", "09:00", "12:30", "Réseaux", teacher, room()));

        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.POST,
                "/api/v1/planning/drafts/" + job.get("publicId") + "/duplicate-week",
                Map.of("sourceWeekStart", "2026-12-07", "targetWeekStart", "2026-12-14"), admin);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().get("code")).isEqualTo("PLAN_CALENDAR_NOTHING_TO_COPY");
    }

    @Test
    void repeterUnCreneauCreeLesOccurrencesDemandees() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        Map<String, Object> job = addSlot(admin, classId,
                slot("2026-11-09", "09:00", "12:30", "Réseaux", teacher, room()));
        String rowId = (String) rows((String) job.get("publicId"), admin).get(0).get("publicId");

        Map<String, Object> repeated = postBody("/api/v1/planning/drafts/" + job.get("publicId")
                + "/slots/" + rowId + "/repeat", Map.of("occurrences", 3, "everyDays", 7), admin);

        assertThat(repeated.get("totalRows")).isEqualTo(4);
        List<String> dates = rows((String) job.get("publicId"), admin).stream()
                .map(row -> (String) row.get("sessionDate")).toList();
        assertThat(dates).contains("2026-11-16", "2026-11-23", "2026-11-30");
    }

    @Test
    void unCreneauChevauchantUneSeanceDejaPublieeEstSignale() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();

        Map<String, Object> first = addSlot(admin, classId,
                slot("2026-11-18", "09:00", "12:30", "Réseaux", teacher, room()));
        post("/api/v1/planning-imports/" + first.get("publicId") + "/publish", admin);

        Map<String, Object> second = addSlot(admin, classId,
                slot("2026-11-18", "09:00", "12:30", "Systèmes", teacher, room()));

        // Les contrôles de conflit s'appliquent aussi au calendrier
        // (docs/02 §13.7), y compris contre le déjà publié.
        assertThat(second.get("errorRows")).isEqualTo(1);
        assertThat(second.get("confirmable")).isEqualTo(false);
    }

    @Test
    void unApprenantNAccedePasAuCalendrierDeConstruction() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String student = AuthTestSupport.accessToken(rest,
                persistUser(RoleCode.STUDENT).getEmail(), PASSWORD);

        assertThat(exchange(HttpMethod.GET, "/api/v1/planning/calendar?classGroupPublicId="
                + classId + "&from=2026-11-01&to=2026-11-30", null, student).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(HttpMethod.POST, "/api/v1/planning/slots?classGroupPublicId=" + classId,
                slot("2026-11-09", "09:00", "12:30", "Réseaux", teacherPublicId(), room()), student)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unePeriodeInverseeEstRefusee() {
        String admin = adminToken();
        String classId = classGroup(admin);

        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.GET,
                "/api/v1/planning/calendar?classGroupPublicId=" + classId
                        + "&from=2026-11-30&to=2026-11-01", null, admin);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("code")).isEqualTo("PLAN_CALENDAR_INVALID_RANGE");
    }

    // ------------------------------------------------------------------

    private static Map<String, Object> slot(String date, String start, String end, String title,
                                            String teacher, String room) {
        Map<String, Object> body = new HashMap<>();
        body.put("sessionDate", date);
        body.put("startTime", start);
        body.put("endTime", end);
        body.put("timeZoneId", "Europe/Paris");
        body.put("title", title);
        body.put("teacherPublicId", teacher);
        body.put("roomCode", room);
        return body;
    }

    private static String room() {
        return "R" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> addSlot(String token, String classId, Map<String, Object> slot) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST,
                "/api/v1/planning/slots?classGroupPublicId=" + classId, slot, token);
        assertThat(response.getStatusCode()).as("POST /slots -> %s", response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> patch(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.PATCH, path, body, token);
        assertThat(response.getStatusCode()).as("PATCH %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<String, Object> calendar(String token, String classId, String from, String to) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET,
                "/api/v1/planning/calendar?classGroupPublicId=" + classId
                        + "&from=" + from + "&to=" + to, null, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Map<String, Object> body, String field) {
        return (List<Map<String, Object>>) body.get(field);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(String jobId, String token) {
        return (List<Map<String, Object>>) exchange(HttpMethod.GET,
                "/api/v1/planning-imports/" + jobId + "/rows?size=100", null, token)
                .getBody().get("content");
    }

    private Map<String, Object> post(String path, String token) {
        return postBody(path, Map.of(), token);
    }

    private Map<String, Object> postBody(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<Map<String, Object>> exchange(HttpMethod method, String path,
                                                         Map<String, Object> body, String token) {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
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

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
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

    private String teacherPublicId() {
        return persistUser(RoleCode.TEACHER).getPublicId().toString();
    }

    private String adminToken() {
        return AuthTestSupport.accessToken(rest, persistUser(RoleCode.ADMIN).getEmail(), PASSWORD);
    }

    private UserAccount persistUser(RoleCode... roles) {
        UserAccount account = new UserAccount("cal-" + UUID.randomUUID() + "@esic-connect.test",
                "Calendrier", "Testeur", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return account;
    }
}
