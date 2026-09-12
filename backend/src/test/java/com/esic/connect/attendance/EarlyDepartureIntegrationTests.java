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
 * Départ anticipé (EF-ATT-013 ; docs/02 §16.13) et journal de
 * transparence (EF-ATT-014 ; docs/02 §5.7).
 *
 * <p>Ce qui est vérifié ici décide de ce qui apparaît, ou non, dans le
 * relevé d'assiduité officiel d'un apprenant : un départ signalé mais non
 * tranché ne doit rien excuser, et un départ accepté ne doit pas se lire
 * comme une absence.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EarlyDepartureIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    /** Journée de référence : le 12 octobre 2026 est un lundi. */
    private static final String DAY = "2026-10-12";
    private static final String MORNING_START = "2026-10-12T07:00:00Z";
    private static final String MORNING_END = "2026-10-12T10:30:00Z";
    private static final String AFTERNOON_START = "2026-10-12T11:30:00Z";
    private static final String AFTERNOON_END = "2026-10-12T15:00:00Z";
    /** Milieu de la matinée : à l'intérieur des bornes de la séance. */
    private static final String DEPARTURE = "2026-10-12T09:00:00Z";

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
    // EF-ATT-013 — dépôt du dossier
    // ------------------------------------------------------------------

    @Test
    void unDepartSignaleResteAConfirmerTantQuePersonneNaTranche() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String student = tokenFor(fx.student);

        Map<String, Object> dossier = declare(student, fx.morningSessionId, "rendez-vous médical");

        assertThat(dossier.get("status")).isEqualTo("REQUESTED");
        // Signaler n'est pas être autorisé : l'effet reste en suspens.
        assertThat(dossier.get("effect")).isEqualTo("TO_CONFIRM");
        assertThat(dossier.get("sessionPublicId")).isEqualTo(fx.morningSessionId);
        assertThat(dossier.get("decidedAt")).isNull();
    }

    @Test
    void unSecondDossierOuvertSurLaMemeSeanceEstRefuse() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String student = tokenFor(fx.student);
        declare(student, fx.morningSessionId, "premier motif");

        ResponseEntity<Map<String, Object>> second = exchange(HttpMethod.POST,
                "/api/v1/attendance/early-departure",
                Map.of("sessionPublicId", fx.morningSessionId,
                        "departureAt", DEPARTURE, "reason", "second motif"), student);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).containsEntry("code", "ATT_EARLY_DEPARTURE_ALREADY_OPEN");
    }

    @Test
    void uneHeureDeDepartHorsDesBornesDeLaSeanceEstRefusee() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String student = tokenFor(fx.student);

        // 03 h 00 : la séance n'a pas commencé. Ce n'est pas un départ
        // anticipé, c'est une saisie fausse.
        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.POST,
                "/api/v1/attendance/early-departure",
                Map.of("sessionPublicId", fx.morningSessionId,
                        "departureAt", "2026-10-12T03:00:00Z", "reason", "trop tôt"), student);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody())
                .containsEntry("code", "ATT_EARLY_DEPARTURE_TIME_OUTSIDE_SESSION");
    }

    @Test
    void unApprenantEtrangerALaSeanceNePeutPasSignalerUnDepart() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        Account outsider = accountWithRoles(RoleCode.STUDENT);

        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.POST,
                "/api/v1/attendance/early-departure",
                Map.of("sessionPublicId", fx.morningSessionId,
                        "departureAt", DEPARTURE, "reason", "je pars"), tokenFor(outsider));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody()).containsEntry("code", "ATT_NOT_ENROLLED");
    }

    // ------------------------------------------------------------------
    // EF-ATT-013 — décision
    // ------------------------------------------------------------------

    @Test
    void unDepartAccepteProduitEXCUSEDPARTIAL() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String dossierId = (String) declare(tokenFor(fx.student), fx.morningSessionId, "motif")
                .get("publicId");

        Map<String, Object> decided = post("/api/v1/attendance/early-departures/"
                + dossierId + "/decision", Map.of("accepted", true, "comment", "accord donné"), admin);

        assertThat(decided.get("status")).isEqualTo("ACCEPTED");
        assertThat(decided.get("effect")).isEqualTo("EXCUSED_PARTIAL");
        assertThat(decided.get("decidedAt")).isNotNull();
        // La fonction est exposée, jamais l'identité civile de l'agent.
        assertThat(decided.get("decidedByRole")).isEqualTo("ADMIN");
    }

    @Test
    void unDepartRefuseLaisseLaJourneeIncompleteEtNonExcusee() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String dossierId = (String) declare(tokenFor(fx.student), fx.morningSessionId, "motif")
                .get("publicId");

        Map<String, Object> decided = post("/api/v1/attendance/early-departures/"
                + dossierId + "/decision", Map.of("accepted", false, "comment", "refus motivé"), admin);

        assertThat(decided.get("status")).isEqualTo("REFUSED");
        // Un refus ne fabrique aucune présence : la journée reste PARTIAL.
        assertThat(decided.get("effect")).isEqualTo("PARTIAL");
    }

    @Test
    void leFormateurQuiTransmetNePeutPlusTrancherLuiMeme() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String dossierId = (String) declare(tokenFor(fx.student), fx.morningSessionId, "motif")
                .get("publicId");
        String teacher = tokenFor(fx.teacher);

        Map<String, Object> forwarded = post("/api/v1/attendance/early-departures/"
                        + dossierId + "/forward",
                Map.of("opinion", "FAVOURABLE", "comment", "avis favorable"), teacher);
        assertThat(forwarded.get("status")).isEqualTo("FORWARDED");
        assertThat(forwarded.get("teacherOpinion")).isEqualTo("FAVOURABLE");
        assertThat(forwarded.get("effect")).isEqualTo("TO_CONFIRM");

        // Transmettre puis décider quand même viderait la transmission de
        // son sens (docs/02 §16.13).
        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.POST,
                "/api/v1/attendance/early-departures/" + dossierId + "/decision",
                Map.of("accepted", true, "comment", "je reprends la main"), teacher);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refused.getBody())
                .containsEntry("code", "ATT_EARLY_DEPARTURE_DECISION_RESERVED");
    }

    @Test
    void leResponsableTrancheUnDossierQuiLuiAEteTransmis() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String dossierId = (String) declare(tokenFor(fx.student), fx.morningSessionId, "motif")
                .get("publicId");
        post("/api/v1/attendance/early-departures/" + dossierId + "/forward",
                Map.of("comment", "je transmets"), tokenFor(fx.teacher));

        Map<String, Object> decided = post("/api/v1/attendance/early-departures/"
                + dossierId + "/decision", Map.of("accepted", true, "comment", "accordé"), admin);

        assertThat(decided.get("status")).isEqualTo("ACCEPTED");
        assertThat(decided.get("effect")).isEqualTo("EXCUSED_PARTIAL");
    }

    @Test
    void unDossierDejaTrancheNestPlusModifiable() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String dossierId = (String) declare(tokenFor(fx.student), fx.morningSessionId, "motif")
                .get("publicId");
        post("/api/v1/attendance/early-departures/" + dossierId + "/decision",
                Map.of("accepted", true, "comment", "accordé"), admin);

        ResponseEntity<Map<String, Object>> again = exchange(HttpMethod.POST,
                "/api/v1/attendance/early-departures/" + dossierId + "/decision",
                Map.of("accepted", false, "comment", "je change d'avis"), admin);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody()).containsEntry("code", "ATT_EARLY_DEPARTURE_INVALID_STATE");
    }

    @Test
    void unApprenantNePeutPasTrancherSonPropreDossier() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String student = tokenFor(fx.student);
        String dossierId = (String) declare(student, fx.morningSessionId, "motif").get("publicId");

        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.POST,
                "/api/v1/attendance/early-departures/" + dossierId + "/decision",
                Map.of("accepted", true, "comment", "je m'autorise"), student);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------
    // EF-ATT-013 — effet sur le résultat journalier
    // ------------------------------------------------------------------

    @Test
    void unDossierOuvertMetLaJourneeAConfirmer() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String student = tokenFor(fx.student);
        // Matin et après-midi attendus, seul le matin validé : PARTIAL.
        validateAll(admin, student, fx,
                List.of("MORNING_ARRIVAL", "MORNING_BREAK_RETURN",
                        "AFTERNOON_ARRIVAL", "AFTERNOON_BREAK_RETURN"),
                List.of("MORNING_ARRIVAL", "MORNING_BREAK_RETURN"));
        assertThat(dailyRow(admin, fx).get("result")).isEqualTo("PARTIAL");

        declare(student, fx.morningSessionId, "je dois partir");

        assertThat(dailyRow(admin, fx).get("result")).isEqualTo("TO_CONFIRM");
    }

    @Test
    void unDepartAccepteRendLaJourneeExcuseePartiellement() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String student = tokenFor(fx.student);
        validateAll(admin, student, fx,
                List.of("MORNING_ARRIVAL", "MORNING_BREAK_RETURN",
                        "AFTERNOON_ARRIVAL", "AFTERNOON_BREAK_RETURN"),
                List.of("MORNING_ARRIVAL", "MORNING_BREAK_RETURN"));
        String dossierId = (String) declare(student, fx.morningSessionId, "motif").get("publicId");

        post("/api/v1/attendance/early-departures/" + dossierId + "/decision",
                Map.of("accepted", true, "comment", "accordé"), admin);

        assertThat(dailyRow(admin, fx).get("result")).isEqualTo("EXCUSED_PARTIAL");
    }

    @Test
    void unDepartRefuseNeChangeRienAuResultatJournalier() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String student = tokenFor(fx.student);
        validateAll(admin, student, fx,
                List.of("MORNING_ARRIVAL", "MORNING_BREAK_RETURN",
                        "AFTERNOON_ARRIVAL", "AFTERNOON_BREAK_RETURN"),
                List.of("MORNING_ARRIVAL", "MORNING_BREAK_RETURN"));
        String dossierId = (String) declare(student, fx.morningSessionId, "motif").get("publicId");

        post("/api/v1/attendance/early-departures/" + dossierId + "/decision",
                Map.of("accepted", false, "comment", "refusé"), admin);

        assertThat(dailyRow(admin, fx).get("result")).isEqualTo("PARTIAL");
    }

    @Test
    void unDepartAccepteNeTransformePasUneAbsenceTotaleEnExcuse() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String student = tokenFor(fx.student);
        // Aucun émargement : la journée est ABSENT.
        validateAll(admin, student, fx,
                List.of("MORNING_ARRIVAL", "MORNING_BREAK_RETURN"), List.of());
        String dossierId = (String) declare(student, fx.morningSessionId, "motif").get("publicId");
        post("/api/v1/attendance/early-departures/" + dossierId + "/decision",
                Map.of("accepted", true, "comment", "accordé"), admin);

        // On ne part pas d'un endroit où l'on n'est jamais venu.
        assertThat(dailyRow(admin, fx).get("result")).isEqualTo("ABSENT");
    }

    // ------------------------------------------------------------------
    // EF-ATT-014 — journal de transparence
    // ------------------------------------------------------------------

    @Test
    void leJournalMontreLEmargementDeLApprenantEtSonCanal() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String student = tokenFor(fx.student);
        validateAll(admin, student, fx, List.of("MORNING_ARRIVAL"), List.of("MORNING_ARRIVAL"));

        List<Map<String, Object>> entries = journal(student);

        assertThat(entries).isNotEmpty();
        Map<String, Object> recorded = entries.stream()
                .filter(entry -> "RECORDED".equals(entry.get("event")))
                .findFirst().orElseThrow();
        assertThat(recorded.get("actorRole")).isEqualTo("SELF");
        assertThat(recorded.get("channel")).isEqualTo("SHORT_CODE");
        assertThat(recorded.get("newStatus")).isEqualTo("PRESENT");
        assertThat(recorded.get("sessionPublicId")).isEqualTo(fx.morningSessionId);
    }

    @Test
    void leJournalMontreUneCorrectionAvecSonAvantSonApresEtSonMotif() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String student = tokenFor(fx.student);
        validateAll(admin, student, fx, List.of("MORNING_ARRIVAL"), List.of("MORNING_ARRIVAL"));
        String attendanceId = firstAttendanceId(admin, fx.morningSessionId);
        post("/api/v1/sessions/" + fx.morningSessionId + "/attendance/" + attendanceId + "/correct",
                Map.of("status", "LATE", "lateMinutes", 20, "reason", "arrivée constatée à 9 h 20"),
                admin);

        Map<String, Object> correction = journal(student).stream()
                .filter(entry -> "STATUS_CORRECTED".equals(entry.get("event")))
                .findFirst().orElseThrow();

        assertThat(correction.get("previousStatus")).isEqualTo("PRESENT");
        assertThat(correction.get("newStatus")).isEqualTo("LATE");
        assertThat(correction.get("newLateMinutes")).isEqualTo(20);
        assertThat(correction.get("reason")).isEqualTo("arrivée constatée à 9 h 20");
        // La fonction de l'auteur, jamais son nom (docs/02 §14).
        assertThat(correction.get("actorRole")).isEqualTo("ADMIN");
        assertThat(correction).doesNotContainKey("actorDisplayName");
    }

    @Test
    void leJournalPorteLeCycleDuDepartAnticipe() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String student = tokenFor(fx.student);
        createCheckpoints(admin, fx, List.of("MORNING_ARRIVAL"));
        String dossierId = (String) declare(student, fx.morningSessionId, "rendez-vous").get("publicId");
        post("/api/v1/attendance/early-departures/" + dossierId + "/forward",
                Map.of("opinion", "FAVOURABLE", "comment", "avis favorable"), tokenFor(fx.teacher));
        post("/api/v1/attendance/early-departures/" + dossierId + "/decision",
                Map.of("accepted", true, "comment", "accordé"), admin);

        List<String> events = journal(student).stream()
                .map(entry -> (String) entry.get("event"))
                .toList();

        assertThat(events).contains("EARLY_DEPARTURE_DECLARED",
                "EARLY_DEPARTURE_FORWARDED", "EARLY_DEPARTURE_DECIDED");
    }

    @Test
    void unApprenantNeVoitQueSonPropreJournal() {
        String admin = adminToken();
        Fixture fx = fixture(admin);
        String student = tokenFor(fx.student);
        validateAll(admin, student, fx, List.of("MORNING_ARRIVAL"), List.of("MORNING_ARRIVAL"));

        // Un autre apprenant, inscrit dans la même classe, n'hérite de rien :
        // le journal est bâti depuis le seul JWT (AC-017).
        Account other = accountWithRoles(RoleCode.STUDENT);
        created("/api/v1/enrollments", Map.of("studentUserPublicId", other.publicId(),
                "classGroupPublicId", fx.classId, "startDate", "2026-09-01"), admin);

        assertThat(journal(tokenFor(other))).isEmpty();
    }

    @Test
    void leJournalEstRefuseAUnFormateur() {
        String admin = adminToken();
        Fixture fx = fixture(admin);

        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.GET,
                "/api/v1/me/attendance/transparency", null, tokenFor(fx.teacher));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void leJournalEstRefuseSansAuthentification() {
        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.GET,
                "/api/v1/me/attendance/transparency", null, null);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static final class Fixture {
        String classId;
        String morningSessionId;
        String afternoonSessionId;
        Account teacher;
        Account student;
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

        fx.teacher = accountWithRoles(RoleCode.TEACHER);
        fx.morningSessionId = openSession(admin, fx.teacher, fx.classId, MORNING_START, MORNING_END);
        fx.afternoonSessionId = openSession(admin, fx.teacher, fx.classId,
                AFTERNOON_START, AFTERNOON_END);

        fx.student = accountWithRoles(RoleCode.STUDENT);
        created("/api/v1/enrollments", Map.of(
                "studentUserPublicId", fx.student.publicId(), "classGroupPublicId", fx.classId,
                "startDate", "2026-09-01"), admin);
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

    private Map<String, Object> declare(String studentToken, String sessionId, String reason) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST,
                "/api/v1/attendance/early-departure",
                Map.of("sessionPublicId", sessionId, "departureAt", DEPARTURE, "reason", reason),
                studentToken);
        assertThat(response.getStatusCode()).as("dépôt du dossier -> %s", response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> dailyRow(String admin, Fixture fx) {
        Map<String, Object> report = getMap("/api/v1/attendance/reports/daily?classGroup="
                + fx.classId + "&date=" + DAY, admin);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) report.get("rows");
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> journal(String studentToken) {
        Map<String, Object> page = getMap("/api/v1/me/attendance/transparency", studentToken);
        return (List<Map<String, Object>>) page.get("content");
    }

    /**
     * Première présence enregistrée de la séance, tous points de contrôle
     * confondus. Le champ {@code records} de la réponse ne reflète que le
     * <em>premier</em> point de contrôle (compat V9), qui est celui créé
     * d'office à l'ouverture de la séance et ne porte aucun émargement.
     */
    @SuppressWarnings("unchecked")
    private String firstAttendanceId(String admin, String sessionId) {
        Map<String, Object> body = getMap("/api/v1/sessions/" + sessionId + "/attendance", admin);
        List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) body.get("checkpoints");
        return checkpoints.stream()
                .flatMap(cp -> ((List<Map<String, Object>>) cp.get("records")).stream())
                .map(record -> (String) record.get("attendancePublicId"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune présence enregistrée sur la séance"));
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
        UserAccount account = new UserAccount("dep-" + UUID.randomUUID() + "@esic-connect.test",
                "Depart", "Testeur", AccountStatus.ACTIVE);
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
