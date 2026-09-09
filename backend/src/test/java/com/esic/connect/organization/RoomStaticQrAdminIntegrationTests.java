package com.esic.connect.organization;

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
 * Gestion administrative du QR fixe de salle (EF-ORG-003 ; docs/02 §7.1,
 * §16.6).
 *
 * <p>Vérifie la <strong>matrice de rôles</strong> exigée : {@code ADMIN}
 * consulte, imprime <em>et</em> renouvelle ; {@code SUPER_ADMIN} et
 * {@code SCHOOL_ADMINISTRATION} consultent et impriment mais ne
 * renouvellent pas ; les autres rôles n'obtiennent jamais la référence.
 * Vérifie aussi que la réimpression est idempotente, que le
 * renouvellement change la référence <em>et</em> sa date d'émission,
 * qu'il est audité sans fuite du jeton, et que la référence complète a
 * disparu du contrat général {@code RoomResponse}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RoomStaticQrAdminIntegrationTests {

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

    private String admin;
    private String roomId;

    @BeforeEach
    void setUp() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        admin = tokenFor(RoleCode.ADMIN);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String siteId = (String) created("/api/v1/sites", Map.of("code", "SITE-" + suffix,
                "name", "Campus " + suffix, "timeZoneId", "Europe/Paris"), admin).get("publicId");
        roomId = (String) created("/api/v1/sites/" + siteId + "/rooms",
                Map.of("code", "R-" + suffix.toUpperCase(java.util.Locale.ROOT),
                        "name", "Salle " + suffix, "capacity", 30, "floorLabel", "2e étage"), admin)
                .get("publicId");
    }

    // ------------------------------------------------------------------
    // Matrice de rôles
    // ------------------------------------------------------------------

    @Test
    void adminPeutConsulterImprimerEtRenouveler() {
        Map<String, Object> before = getMap("/api/v1/rooms/" + roomId + "/static-qr", admin, HttpStatus.OK);
        assertThat(before.get("issued")).isEqualTo(Boolean.FALSE);
        assertThat(before.get("staticQrReference")).isNull();

        Map<String, Object> issued = post("/api/v1/rooms/" + roomId + "/static-qr/rotate", admin, HttpStatus.OK);
        assertThat(issued.get("issued")).isEqualTo(Boolean.TRUE);
        String reference = (String) issued.get("staticQrReference");
        assertThat(reference).isNotBlank();
        assertThat(reference).doesNotContain(roomId);
        assertThat(reference.length()).isGreaterThanOrEqualTo(32);
        assertThat((String) issued.get("checkInPath")).isEqualTo("/attendance?ref=" + reference);
        String masked = (String) issued.get("maskedReference");
        assertThat(masked).contains("…").doesNotContain(reference.substring(4, reference.length() - 4));
        assertThat(issued.get("roomCode")).isNotNull();
        assertThat(issued.get("siteName")).isNotNull();
        assertThat(issued.get("floorLabel")).isEqualTo("2e étage");
    }

    @Test
    void schoolAdministrationConsulteEtImprimeMaisNeRenouvellePas() {
        post("/api/v1/rooms/" + roomId + "/static-qr/rotate", admin, HttpStatus.OK);
        String schoolAdmin = tokenFor(RoleCode.SCHOOL_ADMINISTRATION);

        Map<String, Object> view = getMap("/api/v1/rooms/" + roomId + "/static-qr", schoolAdmin, HttpStatus.OK);
        assertThat(view.get("issued")).isEqualTo(Boolean.TRUE);
        assertThat((String) view.get("staticQrReference")).isNotBlank();

        assertThat(status(HttpMethod.POST, "/api/v1/rooms/" + roomId + "/static-qr/rotate", schoolAdmin))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(HttpMethod.POST, "/api/v1/rooms/" + roomId + "/static-qr", schoolAdmin))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(HttpMethod.DELETE, "/api/v1/rooms/" + roomId + "/static-qr", schoolAdmin))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void superAdminConsulteEtImprimeMaisNeRenouvellePasDansLeParcoursNormal() {
        post("/api/v1/rooms/" + roomId + "/static-qr/rotate", admin, HttpStatus.OK);
        String superAdmin = tokenFor(RoleCode.SUPER_ADMIN);

        assertThat(getMap("/api/v1/rooms/" + roomId + "/static-qr", superAdmin, HttpStatus.OK).get("issued"))
                .isEqualTo(Boolean.TRUE);
        assertThat(status(HttpMethod.POST, "/api/v1/rooms/" + roomId + "/static-qr/rotate", superAdmin))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(HttpMethod.DELETE, "/api/v1/rooms/" + roomId + "/static-qr", superAdmin))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void teacherStudentAndPedagogicalManagerNeRecuperentPasLaReference() {
        post("/api/v1/rooms/" + roomId + "/static-qr/rotate", admin, HttpStatus.OK);
        for (RoleCode role : List.of(RoleCode.TEACHER, RoleCode.STUDENT, RoleCode.PEDAGOGICAL_MANAGER)) {
            assertThat(status(HttpMethod.GET, "/api/v1/rooms/" + roomId + "/static-qr", tokenFor(role)))
                    .as("GET static-qr as %s", role)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void anonymeEstRefuse() {
        assertThat(rest.exchange(RequestEntity.get(URI.create("/api/v1/rooms/" + roomId + "/static-qr")).build(),
                String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------
    // Réimpression vs renouvellement
    // ------------------------------------------------------------------

    @Test
    void reimprimerConserveLaMemeReferenceEtLaMemeDate() {
        Map<String, Object> first = post("/api/v1/rooms/" + roomId + "/static-qr/rotate", admin, HttpStatus.OK);
        Map<String, Object> reprint = getMap("/api/v1/rooms/" + roomId + "/static-qr", admin, HttpStatus.OK);

        assertThat(reprint.get("staticQrReference")).isEqualTo(first.get("staticQrReference"));
        // La réponse de `rotate` porte l'`Instant` en mémoire (précision
        // nanoseconde sur horloge Linux) ; la réponse de `GET` porte la
        // même valeur relue de MySQL, où `DATETIME(6)` la tronque à la
        // microseconde. La réimpression est idempotente : on compare donc
        // à la précision réellement persistée (RG QR fixe, EF-ORG-003).
        Instant firstIssuedAt = Instant.parse((String) first.get("staticQrIssuedAt"));
        Instant reprintIssuedAt = Instant.parse((String) reprint.get("staticQrIssuedAt"));
        assertThat(reprintIssuedAt).isEqualTo(firstIssuedAt.truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void renouvelerChangeLaReferenceEtLaDateDEmission() throws InterruptedException {
        Map<String, Object> first = post("/api/v1/rooms/" + roomId + "/static-qr/rotate", admin, HttpStatus.OK);
        Thread.sleep(1100);
        Map<String, Object> second = post("/api/v1/rooms/" + roomId + "/static-qr/rotate", admin, HttpStatus.OK);

        assertThat(second.get("staticQrReference")).isNotEqualTo(first.get("staticQrReference"));
        assertThat(second.get("staticQrIssuedAt")).isNotNull().isNotEqualTo(first.get("staticQrIssuedAt"));
    }

    @Test
    void revoquerRetourneUneVueSansReference() {
        post("/api/v1/rooms/" + roomId + "/static-qr/rotate", admin, HttpStatus.OK);
        Map<String, Object> revoked = exchangeMap(HttpMethod.DELETE,
                "/api/v1/rooms/" + roomId + "/static-qr", admin, HttpStatus.OK);
        assertThat(revoked.get("issued")).isEqualTo(Boolean.FALSE);
        assertThat(revoked.get("staticQrReference")).isNull();
    }

    // ------------------------------------------------------------------
    // Contrat général & audit
    // ------------------------------------------------------------------

    @Test
    void leContratGeneralDeSalleNExposePlusLaReferenceComplete() {
        post("/api/v1/rooms/" + roomId + "/static-qr/rotate", admin, HttpStatus.OK);
        Map<String, Object> room = getMap("/api/v1/rooms/" + roomId, admin, HttpStatus.OK);
        assertThat(room).doesNotContainKey("staticQrReference");
        assertThat(room).containsKey("staticQrIssuedAt");
        assertThat(room.get("staticQrIssuedAt")).isNotNull();
    }

    @Test
    void leRenouvellementEstAuditeSansFuiteDuJetonNiAdresseIp() {
        String reference = (String) post("/api/v1/rooms/" + roomId + "/static-qr/rotate", admin, HttpStatus.OK)
                .get("staticQrReference");

        boolean found = false;
        for (int attempt = 0; attempt < 20 && !found; attempt++) {
            Map<String, Object> page = getMap("/api/v1/audit-events?resourceType=ROOM&size=100", admin,
                    HttpStatus.OK);
            String asText = page.toString();
            // §23.3 : jamais le jeton complet, jamais d'adresse IP.
            assertThat(asText).doesNotContain(reference).doesNotContain("127.0.0.1");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
            found = content != null && content.stream()
                    .anyMatch(row -> "ROOM".equals(row.get("resourceType"))
                            && "ROOM_UPDATED".equals(row.get("action")));
            if (!found) {
                sleep(500);
            }
        }
        assertThat(found).as("un événement d'audit ROOM/UPDATED doit être produit par le renouvellement")
                .isTrue();
    }

    // ------------------------------------------------------------------
    // Helpers HTTP
    // ------------------------------------------------------------------

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> post(String path, String token, HttpStatus expected) {
        return exchangeMap(HttpMethod.POST, path, token, expected);
    }

    private Map<String, Object> getMap(String path, String token, HttpStatus expected) {
        return exchangeMap(HttpMethod.GET, path, token, expected);
    }

    private Map<String, Object> exchangeMap(HttpMethod method, String path, String token, HttpStatus expected) {
        ResponseEntity<Map<String, Object>> response = exchange(method, path, null, token);
        assertThat(response.getStatusCode()).as("%s %s -> %s", method, path, response.getBody())
                .isEqualTo(expected);
        return response.getBody();
    }

    private HttpStatus status(HttpMethod method, String path, String token) {
        return (HttpStatus) exchange(method, path, null, token).getStatusCode();
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

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private String tokenFor(RoleCode... roles) {
        UserAccount account = new UserAccount("room-qr-" + UUID.randomUUID() + "@esic-connect.test",
                "Salle", "Testeur", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return AuthTestSupport.accessToken(rest, account.getEmail(), PASSWORD);
    }
}
