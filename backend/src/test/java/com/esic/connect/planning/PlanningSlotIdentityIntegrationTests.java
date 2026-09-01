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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Identité d'un créneau de planning (DEC-G1-002 ; audit G1-B.1).
 *
 * <ul>
 *   <li>même planning + même {@code slot_key} entre la version 1 et la
 *       version 2 ⇒ même {@code slotPublicId}, et la même séance
 *       {@code course_session} réutilisée ;</li>
 *   <li>les deux {@code planning_entry} (v1 / v2) du même créneau ont des
 *       {@code publicId} <strong>différents</strong> (identifiant de ligne
 *       de version, aléatoire) ;</li>
 *   <li>deux plannings différents (deux classes) avec le même
 *       {@code slot_key} ⇒ {@code slotPublicId} différents ;</li>
 *   <li>aucun champ ambigu nommé {@code entryPublicId} dans les DTO.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlanningSlotIdentityIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    private static final String HEADER =
            "slot_key,session_date,start_time,end_time,time_zone_id,title,teacher_public_id,room_code\n";

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
    void slotIdentityIsStableAcrossVersionsWhilePlanningEntryIdIsPerVersion() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();

        String v1 = HEADER
                + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours A," + teacher + ",A1\n"
                + "S2,2026-09-08,09:00,12:00,Europe/Paris,Cours B," + teacher + ",A1\n";
        String job1 = (String) upload(v1, admin, classId).getBody().get("publicId");
        post("/api/v1/planning-imports/" + job1 + "/publish", admin);
        String versionOneId = versionIds(admin, classId).get(0);

        String v2 = HEADER
                + "S1,2026-09-07,10:00,13:00,Europe/Paris,Cours A," + teacher + ",A1\n"
                + "S3,2026-09-09,09:00,12:00,Europe/Paris,Cours C," + teacher + ",A1\n";
        String job2 = (String) upload(v2, admin, classId).getBody().get("publicId");
        post("/api/v1/planning-imports/" + job2 + "/publish", admin);
        String versionTwoId = versionIds(admin, classId).stream()
                .filter(id -> !id.equals(versionOneId)).findFirst().orElseThrow();

        Map<String, Object> d1 = versionDetail(admin, versionOneId);
        Map<String, Object> d2 = versionDetail(admin, versionTwoId);
        Map<String, Object> s1v1 = entry(d1, "S1");
        Map<String, Object> s1v2 = entry(d2, "S1");

        // publicId de ligne : différent d'une version à l'autre.
        assertThat(s1v1.get("publicId")).isNotEqualTo(s1v2.get("publicId"));
        // slotPublicId : identité stable, identique.
        assertThat(s1v1.get("slotPublicId"))
                .isNotNull()
                .isEqualTo(s1v2.get("slotPublicId"));
        // la séance est réutilisée (même sessionPublicId).
        assertThat(s1v1.get("sessionPublicId"))
                .isNotNull()
                .isEqualTo(s1v2.get("sessionPublicId"));

        // Aucun champ ambigu "entryPublicId" dans le DTO.
        assertThat(d1.toString()).doesNotContain("entryPublicId");
        assertThat(d2.toString()).doesNotContain("entryPublicId");
    }

    @Test
    void sameSlotKeyInTwoDifferentPlanningsYieldsDifferentSlotIdentities() {
        String admin = adminToken();
        String classA = classGroup(admin);
        String classB = classGroup(admin);
        // Formateurs et créneaux distincts : aucun conflit inter-séances,
        // le test ne porte que sur l'identité de créneau par planning.
        String csvA = HEADER + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours,"
                + teacherPublicId() + ",A1\n";
        String csvB = HEADER + "S1,2026-09-14,13:00,16:00,Europe/Paris,Cours,"
                + teacherPublicId() + ",B2\n";
        String jobA = (String) upload(csvA, admin, classA).getBody().get("publicId");
        post("/api/v1/planning-imports/" + jobA + "/publish", admin);
        String jobB = (String) upload(csvB, admin, classB).getBody().get("publicId");
        post("/api/v1/planning-imports/" + jobB + "/publish", admin);

        Map<String, Object> detailA = versionDetail(admin, versionIds(admin, classA).get(0));
        Map<String, Object> detailB = versionDetail(admin, versionIds(admin, classB).get(0));

        assertThat(entry(detailA, "S1").get("slotPublicId"))
                .isNotEqualTo(entry(detailB, "S1").get("slotPublicId"));
    }

    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> entry(Map<String, Object> versionDetail, String slotKey) {
        return ((List<Object>) versionDetail.get("entries")).stream()
                .map(o -> (Map<String, Object>) o)
                .filter(e -> slotKey.equals(e.get("slotKey")))
                .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private List<String> versionIds(String token, String classId) {
        Map<String, Object> page = getMap(
                "/api/v1/planning/versions?classGroupPublicId=" + classId + "&sort=versionNumber,asc", token);
        return ((List<Object>) page.get("content")).stream()
                .map(o -> (String) ((Map<String, Object>) o).get("publicId"))
                .toList();
    }

    private Map<String, Object> versionDetail(String token, String versionId) {
        return getMap("/api/v1/planning/versions/" + versionId, token);
    }

    private ResponseEntity<Map<String, Object>> upload(String csv, String token, String classId) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "planning.csv";
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(new MediaType("text", "csv"));
        parts.add("file", new HttpEntity<>(resource, fileHeaders));
        parts.add("classGroupPublicId", classId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        return restTemplate.exchange(URI.create("/api/v1/planning-imports"), HttpMethod.POST,
                new HttpEntity<>(parts, headers), new ParameterizedTypeReference<>() {
                });
    }

    private Map<String, Object> post(String path, String token) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.method(HttpMethod.POST, URI.create(path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<Map<String, Object>>() {
                });
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.get(URI.create(path)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).as("GET " + path).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<String, Object> created(String path, Map<String, Object> payload, String token) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.post(URI.create(path)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).body(payload),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private String classGroup(String admin) {
        String site = (String) created("/api/v1/sites", Map.of("code", "SITE-" + code(),
                "name", "Campus", "timeZoneId", "Europe/Paris"), admin).get("publicId");
        String program = (String) created("/api/v1/programs", Map.of("code", "PRG-" + code(),
                "name", "BTS SIO", "programType", "BTS"), admin).get("publicId");
        String level = (String) created("/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1", "name", "BTS 1", "sequenceNumber", 1), admin).get("publicId");
        String year = (String) created("/api/v1/academic-years", Map.of("code", "AY-" + code(),
                "name", "2026-2027", "startDate", "2026-09-01", "endDate", "2027-08-31"), admin).get("publicId");
        String promo = (String) created("/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P26", "name", "Promotion 2026"), admin).get("publicId");
        return (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C1", "name", "Classe 1"), admin)
                .get("publicId");
    }

    private String teacherPublicId() {
        return account(RoleCode.TEACHER).publicId();
    }

    private record Account(String publicId, String email) {
    }

    private Account account(RoleCode... roles) {
        UserAccount user = new UserAccount("pub-" + UUID.randomUUID() + "@esic-connect.test",
                "Pub", "Tester", AccountStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user = userAccountRepository.saveAndFlush(user);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(user, role, Instant.now(), true));
        }
        return new Account(user.getPublicId().toString(), user.getEmail());
    }

    private String adminToken() {
        Account a = account(RoleCode.ADMIN);
        Map<String, Object> body = restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", a.email(), "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) body.get("accessToken");
    }

    private static String code() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
