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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QR fixe de salle (EF-ORG-003, EF-ATT-010) sous contrôle de plage réseau
 * (EF-ATT-008 ; docs/02 §16.6 et §16.7).
 *
 * <p>La suite s'exécute depuis {@code 127.0.0.1} : les plages déclarées
 * dans les fixtures l'incluent ou l'excluent délibérément, ce qui permet
 * de vérifier les deux versants du contrôle réseau sans dépendre de
 * l'environnement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // INDÉPENDANCE À L'HEURE DE LA JOURNÉE (défaut corrigé au sprint 10,
        // reproduit à l'identique sur le tag v0.9 — antérieur à ce lot).
        //
        // Cette classe a besoin d'une séance qui commence RÉELLEMENT dans
        // quelques minutes : c'est l'horloge du serveur qui décide si le QR
        // fixe est encore recevable. Elle publie donc un créneau à
        // `Instant.now()`. Avec la fenêtre de travail par défaut
        // (08:00–19:00 UTC), toute exécution en soirée produisait une
        // anomalie bloquante et la publication était refusée — un échec sans
        // aucun rapport avec ce que la classe vérifie, et qui n'apparaissait
        // qu'à certaines heures.
        //
        // La fenêtre et la durée minimale sont donc élargies POUR CETTE
        // CLASSE. Elles restent vérifiées là où c'est leur objet, dans les
        // tests du module `planning`.
        "app.planning.working-day-start=00:00",
        "app.planning.working-day-end=23:59",
        "app.planning.min-duration=PT1M"
})
@ActiveProfiles("test")
class RoomQrAttendanceIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    /** Plage englobant l'origine de la suite de tests. */
    private static final String LOCAL_RANGE = "127.0.0.0/8";
    /** Plage l'excluant délibérément. */
    private static final String FOREIGN_RANGE = "10.0.0.0/8";

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
    // EF-ORG-003 — émission du QR
    // ------------------------------------------------------------------

    @Test
    void leJetonDuQrEstGenereParLeServeurEtNonDevinable() {
        String admin = adminToken();
        Fixture fx = fixture(admin, LOCAL_RANGE);

        Map<String, Object> room = post("/api/v1/rooms/" + fx.roomId + "/static-qr", admin);
        String token = (String) room.get("staticQrReference");

        assertThat(token).isNotBlank();
        // Ni le code de salle, ni son identifiant : une valeur devinable
        // ne serait pas un contrôle.
        assertThat(token).doesNotContain(fx.roomCode).doesNotContain(fx.roomId);
        assertThat(token.length()).isGreaterThanOrEqualTo(32);
        assertThat(room.get("staticQrIssuedAt")).isNotNull();
    }

    @Test
    void renouvelerLeQrInvalideLAfficheePrecedente() {
        String admin = adminToken();
        Fixture fx = fixture(admin, LOCAL_RANGE);
        String first = issueQr(admin, fx);

        String second = issueQr(admin, fx);
        assertThat(second).isNotEqualTo(first);

        openSessionIn(admin, fx, 5);
        // L'ancienne affiche n'ouvre plus rien : c'est le but d'une rotation.
        ResponseEntity<Map<String, Object>> denied = exchange(HttpMethod.POST,
                "/api/v1/attendance/room-qr", Map.of("roomReference", first), tokenFor(fx.student));
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unApprenantNEmetPasDeQrDeSalle() {
        String admin = adminToken();
        Fixture fx = fixture(admin, LOCAL_RANGE);

        assertThat(exchange(HttpMethod.POST, "/api/v1/rooms/" + fx.roomId + "/static-qr",
                null, tokenFor(fx.student)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------
    // EF-ATT-010 — émargement par QR fixe
    // ------------------------------------------------------------------

    @Test
    void leQrFixeEmargeAvantLeDebutDeLaSeance() {
        String admin = adminToken();
        Fixture fx = fixture(admin, LOCAL_RANGE);
        String reference = issueQr(admin, fx);
        openSessionIn(admin, fx, 5);

        Map<String, Object> record = post("/api/v1/attendance/room-qr",
                Map.of("roomReference", reference), tokenFor(fx.student), HttpStatus.OK);

        assertThat(record.get("source")).isEqualTo("ROOM_STATIC_QR");
        assertThat(record.get("status")).isEqualTo("PRESENT");
        // Le corps n'a jamais désigné de séance : le serveur l'a déduite.
        assertThat(record.get("sessionPublicId")).isEqualTo(fx.sessionId);
    }

    @Test
    void leQrFixeEstRefuseApresLeDebutDeLaSeance() {
        String admin = adminToken();
        Fixture fx = fixture(admin, LOCAL_RANGE);
        String reference = issueQr(admin, fx);
        // Séance commencée il y a dix minutes (RG-051).
        openSessionIn(admin, fx, -10);

        ResponseEntity<Map<String, Object>> denied = exchange(HttpMethod.POST,
                "/api/v1/attendance/room-qr", Map.of("roomReference", reference),
                tokenFor(fx.student));

        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(denied.getBody().get("code")).isEqualTo("ATT_ROOM_QR_SESSION_STARTED");
    }

    @Test
    void leQrFixeEstRefuseSansSeanceCorrespondante() {
        String admin = adminToken();
        Fixture fx = fixture(admin, LOCAL_RANGE);
        String reference = issueQr(admin, fx);

        ResponseEntity<Map<String, Object>> denied = exchange(HttpMethod.POST,
                "/api/v1/attendance/room-qr", Map.of("roomReference", reference),
                tokenFor(fx.student));

        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(denied.getBody().get("code")).isEqualTo("ATT_ROOM_QR_NO_SESSION");
    }

    @Test
    void unJetonInconnuEstRefuseSansRienReveler() {
        String admin = adminToken();
        Fixture fx = fixture(admin, LOCAL_RANGE);

        ResponseEntity<Map<String, Object>> denied = exchange(HttpMethod.POST,
                "/api/v1/attendance/room-qr", Map.of("roomReference", "jeton-invente"),
                tokenFor(fx.student));

        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unSecondEmargementSurLeMemePointEstRefuse() {
        String admin = adminToken();
        Fixture fx = fixture(admin, LOCAL_RANGE);
        String reference = issueQr(admin, fx);
        openSessionIn(admin, fx, 5);
        post("/api/v1/attendance/room-qr", Map.of("roomReference", reference),
                tokenFor(fx.student), HttpStatus.OK);

        ResponseEntity<Map<String, Object>> again = exchange(HttpMethod.POST,
                "/api/v1/attendance/room-qr", Map.of("roomReference", reference),
                tokenFor(fx.student));

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().get("code")).isEqualTo("ATT_ALREADY_RECORDED");
    }

    // ------------------------------------------------------------------
    // EF-ATT-008 — contrôle de plage réseau
    // ------------------------------------------------------------------

    @Test
    void leQrFixeEstRefuseHorsPlageReseauAutorisee() {
        String admin = adminToken();
        Fixture fx = fixture(admin, FOREIGN_RANGE);
        String reference = issueQr(admin, fx);
        openSessionIn(admin, fx, 5);

        ResponseEntity<Map<String, Object>> denied = exchange(HttpMethod.POST,
                "/api/v1/attendance/room-qr", Map.of("roomReference", reference),
                tokenFor(fx.student));

        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(denied.getBody().get("code")).isEqualTo("ATT_ROOM_QR_OUT_OF_NETWORK");
    }

    @Test
    void unSiteSansPlageDeclareeRefuseToutEmargementParQrFixe() {
        String admin = adminToken();
        Fixture fx = fixture(admin, null);
        String reference = issueQr(admin, fx);
        openSessionIn(admin, fx, 5);

        ResponseEntity<Map<String, Object>> denied = exchange(HttpMethod.POST,
                "/api/v1/attendance/room-qr", Map.of("roomReference", reference),
                tokenFor(fx.student));

        // Refus par défaut : l'absence de plage n'est pas une autorisation.
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(denied.getBody().get("code")).isEqualTo("ATT_ROOM_QR_OUT_OF_NETWORK");
    }

    @Test
    void uneReponseDeRefusNeContientJamaisLAdresseIp() {
        String admin = adminToken();
        Fixture fx = fixture(admin, FOREIGN_RANGE);
        String reference = issueQr(admin, fx);
        openSessionIn(admin, fx, 5);

        ResponseEntity<Map<String, Object>> denied = exchange(HttpMethod.POST,
                "/api/v1/attendance/room-qr", Map.of("roomReference", reference),
                tokenFor(fx.student));

        // RG-094 : l'adresse sert à décider, jamais à être conservée ni
        // renvoyée.
        assertThat(denied.getBody().toString()).doesNotContain("127.0.0.1");
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static final class Fixture {
        String siteId;
        String roomId;
        String roomCode;
        String classId;
        String sessionId;
        Account teacher;
        Account student;
    }

    private Fixture fixture(String admin, String cidr) {
        Fixture fx = new Fixture();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        fx.siteId = (String) created("/api/v1/sites", Map.of("code", "SITE-" + suffix,
                "name", "Campus", "timeZoneId", "Europe/Paris"), admin).get("publicId");
        if (cidr != null) {
            // Les plages réseau relèvent du SUPER_ADMIN seul (docs/01 §7) :
            // ce sont elles qui décident de ce qui vaut « présence sur
            // site ». Un ADMIN fonctionnel ne les touche pas.
            created("/api/v1/sites/" + fx.siteId + "/network-ranges",
                    Map.of("cidr", cidr, "label", "Réseau de test"), superAdminToken());
        }
        fx.roomCode = "R-" + suffix.toUpperCase(java.util.Locale.ROOT);
        fx.roomId = (String) created("/api/v1/sites/" + fx.siteId + "/rooms",
                Map.of("code", fx.roomCode, "name", "Salle " + suffix, "capacity", 30), admin)
                .get("publicId");

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
                "programLevelPublicId", level, "sitePublicId", fx.siteId, "code", "C-" + suffix,
                "name", "Classe"), admin).get("publicId");

        fx.teacher = accountWithRoles(RoleCode.TEACHER);
        fx.student = accountWithRoles(RoleCode.STUDENT);
        String profile = (String) created("/api/v1/student-profiles", Map.of(
                "userPublicId", fx.student.publicId(),
                "studentNumber", "ESIC-2026-" + suffix), admin).get("publicId");
        created("/api/v1/enrollments", Map.of("studentProfilePublicId", profile,
                "classGroupPublicId", fx.classId,
                "startDate", java.time.LocalDate.now().minusDays(30).toString()), admin);
        return fx;
    }

    private String issueQr(String admin, Fixture fx) {
        return (String) post("/api/v1/rooms/" + fx.roomId + "/static-qr", admin)
                .get("staticQrReference");
    }

    /**
     * Crée et ouvre une séance dans la salle de la fixture, démarrant dans
     * {@code minutesFromNow} minutes (négatif = déjà commencée).
     *
     * <p>La séance est créée par le planning et non manuellement : seule
     * cette voie renseigne {@code room_code}, dont dépend la résolution du
     * QR fixe.
     */
    private void openSessionIn(String admin, Fixture fx, int minutesFromNow) {
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS)
                .plusSeconds(minutesFromNow * 60L);
        // Une ligne de planning porte UNE date, une heure de début et une
        // heure de fin : elle ne sait pas exprimer une séance qui franchit
        // minuit. La fin est donc bornée à la fin du jour UTC du début —
        // sans quoi une exécution en fin de soirée produisait une heure de
        // fin antérieure à l'heure de début, donc une anomalie bloquante.
        java.time.ZonedDateTime startUtc = start.atZone(java.time.ZoneOffset.UTC);
        java.time.ZonedDateTime endOfDay = startUtc.toLocalDate()
                .atTime(23, 59).atZone(java.time.ZoneOffset.UTC);
        Instant end = start.plusSeconds(3 * 3600);
        if (end.isAfter(endOfDay.toInstant())) {
            end = endOfDay.toInstant();
        }
        String csv = "slot_key,session_date,start_time,end_time,time_zone_id,title,"
                + "teacher_public_id,room_code\n"
                + "S1," + startUtc.toLocalDate() + ","
                + timeOf(start) + "," + timeOf(end) + ",UTC,"
                + "Cours," + fx.teacher.publicId() + "," + fx.roomCode + "\n";

        org.springframework.util.MultiValueMap<String, Object> parts =
                new org.springframework.util.LinkedMultiValueMap<>();
        org.springframework.core.io.ByteArrayResource resource =
                new org.springframework.core.io.ByteArrayResource(
                        csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
                    @Override
                    public String getFilename() {
                        return "planning.csv";
                    }
                };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(new MediaType("text", "csv"));
        parts.add("file", new org.springframework.http.HttpEntity<>(resource, fileHeaders));
        parts.add("classGroupPublicId", fx.classId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(admin);
        ResponseEntity<Map<String, Object>> job = rest.exchange(
                URI.create("/api/v1/planning-imports"), HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(parts, headers),
                new ParameterizedTypeReference<>() {
                });
        assertThat(job.getStatusCode()).as("import planning -> %s", job.getBody())
                .isEqualTo(HttpStatus.CREATED);
        post("/api/v1/planning-imports/" + job.getBody().get("publicId") + "/publish",
                null, admin, HttpStatus.OK);

        Map<String, Object> sessions = getMap("/api/v1/sessions?classGroup=" + fx.classId
                + "&size=50", admin);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) sessions.get("content");
        fx.sessionId = (String) content.stream()
                .filter(session -> "Cours".equals(session.get("title")))
                .findFirst().orElseThrow().get("publicId");
        exchange(HttpMethod.POST, "/api/v1/sessions/" + fx.sessionId + "/open", null, admin);
    }

    private static String timeOf(Instant instant) {
        java.time.LocalTime time = instant.atZone(java.time.ZoneOffset.UTC).toLocalTime();
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    // --- HTTP ---

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> post(String path, String token) {
        return post(path, null, token, HttpStatus.OK);
    }

    private Map<String, Object> post(String path, Map<String, Object> body, String token,
                                     HttpStatus expected) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(expected);
        return response.getBody();
    }

    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET, path, null, token);
        assertThat(response.getStatusCode()).as("GET %s", path).isEqualTo(HttpStatus.OK);
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
        UserAccount account = new UserAccount("qr-" + UUID.randomUUID() + "@esic-connect.test",
                "Salle", "Testeur", AccountStatus.ACTIVE);
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

    private String superAdminToken() {
        return tokenFor(accountWithRoles(RoleCode.SUPER_ADMIN));
    }

    private String tokenFor(Account account) {
        return AuthTestSupport.accessToken(rest, account.email(), PASSWORD);
    }
}
