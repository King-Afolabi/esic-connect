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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retour à une version antérieure de planning (EF-PLAN-008 ; critère
 * AC-009 : « un retour à une version antérieure crée une version N+1
 * dont le contenu est celui de la version choisie »).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlanningRollbackIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    private static final String HEADER =
            "slot_key,session_date,start_time,end_time,time_zone_id,title,teacher_public_id,room_code\n";

    /** Salle unique par cas : le conflit de salle est établissement-wide (V21). */
    private String room;

    @BeforeEach
    void freshRoomPerTest() {
        room = "R" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

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
    void leRetourArriereCreeUneVersionTroisAvecLeContenuDeLaVersionUn() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();

        // v1 : deux créneaux.
        publish(admin, classId, HEADER
                + "S1,2026-10-05,09:00,12:00,Europe/Paris,Cours A," + teacher + "," + room + "\n"
                + "S2,2026-10-06,09:00,12:00,Europe/Paris,Cours B," + teacher + "," + room + "\n");
        // v2 : S2 retiré, S1 déplacé.
        publish(admin, classId, HEADER
                + "S1,2026-10-05,10:00,13:00,Europe/Paris,Cours A revu," + teacher + "," + room + "\n");

        List<Map<String, Object>> versions = versions(admin, classId);
        Map<String, Object> versionOne = versions.stream()
                .filter(v -> Integer.valueOf(1).equals(((Number) v.get("versionNumber")).intValue()))
                .findFirst().orElseThrow();

        Map<String, Object> result = post("/api/v1/planning/versions/"
                + versionOne.get("publicId") + "/rollback", admin).getBody();

        // AC-009 : une version N+1, jamais une réactivation de l'ancienne.
        assertThat(((Number) result.get("versionNumber")).intValue()).isEqualTo(3);
        assertThat(((Number) result.get("restoredFromNumber")).intValue()).isEqualTo(1);
        assertThat(((Number) result.get("entryCount")).intValue()).isEqualTo(2);

        Map<String, Object> restored = getMap("/api/v1/planning/versions/"
                + result.get("versionPublicId"), admin);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) restored.get("entries");
        assertThat(entries).extracting(entry -> entry.get("slotKey"))
                .containsExactlyInAnyOrder("S1", "S2");
        assertThat(entries).extracting(entry -> entry.get("title"))
                .contains("Cours A", "Cours B");
    }

    @Test
    void lHistoriqueNEstJamaisEfface() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        publish(admin, classId, HEADER
                + "S1,2026-10-05,09:00,12:00,Europe/Paris,Cours A," + teacher + "," + room + "\n");
        publish(admin, classId, HEADER
                + "S1,2026-10-05,10:00,13:00,Europe/Paris,Cours A revu," + teacher + "," + room + "\n");
        String versionOne = versionPublicId(admin, classId, 1);

        post("/api/v1/planning/versions/" + versionOne + "/rollback", admin);

        // RG-046 : les trois versions coexistent, l'ancienne reste lisible.
        List<Map<String, Object>> versions = versions(admin, classId);
        assertThat(versions).hasSize(3);
        // Le détail imbrique la version : {version: {...}, entries: [...]}.
        @SuppressWarnings("unchecked")
        Map<String, Object> detail =
                (Map<String, Object>) getMap("/api/v1/planning/versions/" + versionOne, admin)
                        .get("version");
        assertThat(detail.get("status")).isEqualTo("SUPERSEDED");
    }

    @Test
    void lesSeancesExistantesSontReutiliseesEtNonRecreees() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        publish(admin, classId, HEADER
                + "S1,2026-10-05,09:00,12:00,Europe/Paris,Cours A," + teacher + "," + room + "\n");
        List<Map<String, Object>> afterV1 = sessions(admin, classId);
        String sessionId = (String) afterV1.get(0).get("publicId");

        publish(admin, classId, HEADER
                + "S1,2026-10-05,10:00,13:00,Europe/Paris,Cours A revu," + teacher + "," + room + "\n");
        post("/api/v1/planning/versions/" + versionPublicId(admin, classId, 1) + "/rollback", admin);

        // RG-047 : l'identité de créneau est stable, la séance est la même —
        // un retour arrière ne fait donc pas perdre ce qui y était rattaché.
        List<Map<String, Object>> afterRollback = sessions(admin, classId);
        assertThat(afterRollback).hasSize(1);
        assertThat(afterRollback.get(0).get("publicId")).isEqualTo(sessionId);
    }

    @Test
    void uneVersionInconnueRepond404() {
        String admin = adminToken();

        assertThat(exchange(HttpMethod.POST,
                "/api/v1/planning/versions/" + UUID.randomUUID() + "/rollback", admin)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void leRetourArriereExigeUnRoleAutorise() {
        String admin = adminToken();
        String teacherToken = tokenFor(RoleCode.TEACHER);
        String classId = classGroup(admin);
        publish(admin, classId, HEADER
                + "S1,2026-10-05,09:00,12:00,Europe/Paris,Cours," + teacherPublicId() + "," + room + "\n");
        String versionOne = versionPublicId(admin, classId, 1);

        assertThat(exchange(HttpMethod.POST,
                "/api/v1/planning/versions/" + versionOne + "/rollback", teacherToken)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------

    private void publish(String token, String classId, String csv) {
        String jobId = (String) upload("planning.csv", csv, token, classId).getBody().get("publicId");
        ResponseEntity<Map<String, Object>> published = exchange(HttpMethod.POST,
                "/api/v1/planning-imports/" + jobId + "/publish", token);
        assertThat(published.getStatusCode()).as("publication -> %s", published.getBody())
                .isEqualTo(HttpStatus.OK);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> versions(String token, String classId) {
        return (List<Map<String, Object>>) getMap(
                "/api/v1/planning/versions?classGroupPublicId=" + classId + "&size=50", token)
                .get("content");
    }

    private String versionPublicId(String token, String classId, int versionNumber) {
        return versions(token, classId).stream()
                .filter(v -> ((Number) v.get("versionNumber")).intValue() == versionNumber)
                .map(v -> (String) v.get("publicId"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("version " + versionNumber + " introuvable"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sessions(String token, String classId) {
        return (List<Map<String, Object>>) getMap(
                "/api/v1/sessions?classGroup=" + classId + "&size=50", token).get("content");
    }

    private ResponseEntity<Map<String, Object>> post(String path, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, token);
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.OK);
        return response;
    }

    private ResponseEntity<Map<String, Object>> exchange(HttpMethod method, String path, String token) {
        return rest.exchange(RequestEntity.method(method, URI.create(path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).body(Map.of()),
                new ParameterizedTypeReference<>() {
                });
    }

    private Map<String, Object> getMap(String path, String token) {
        return rest.exchange(RequestEntity.get(URI.create(path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
    }

    private ResponseEntity<Map<String, Object>> upload(String fileName, String csv, String token,
                                                       String classId) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(new MediaType("text", "csv"));
        parts.add("file", new HttpEntity<>(resource, fileHeaders));
        parts.add("classGroupPublicId", classId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        return rest.exchange(URI.create("/api/v1/planning-imports"), HttpMethod.POST,
                new HttpEntity<>(parts, headers), new ParameterizedTypeReference<>() {
                });
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                RequestEntity.post(URI.create(path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).body(body),
                new ParameterizedTypeReference<>() {
                });
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
        return tokenFor(RoleCode.ADMIN);
    }

    private UserAccount persistUser(RoleCode... roles) {
        UserAccount account = new UserAccount("rb-" + UUID.randomUUID() + "@esic-connect.test",
                "Retour", "Testeur", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return account;
    }

    private String tokenFor(RoleCode... roles) {
        return AuthTestSupport.accessToken(rest, persistUser(roles).getEmail(), PASSWORD);
    }
}
