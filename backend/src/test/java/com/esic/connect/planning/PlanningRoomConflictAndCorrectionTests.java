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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conflit de salle contre les séances déjà publiées (EF-PLAN-009,
 * EF-ORG-004) et correction de ligne avant publication (EF-PLAN-003).
 *
 * <p>Le conflit de salle ne s'exerçait jusqu'ici qu'à l'intérieur d'un
 * même fichier : la séance créée ne conservait pas son code de salle.
 * Deux imports successifs pouvaient donc placer deux classes dans la même
 * salle à la même heure sans que rien ne le signale.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlanningRoomConflictAndCorrectionTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    private static final String HEADER =
            "slot_key,session_date,start_time,end_time,time_zone_id,title,teacher_public_id,room_code\n";

    /**
     * Code de salle unique par cas de test.
     *
     * <p>Une salle est partagée par tout l'établissement : le conflit
     * s'exerce donc à l'échelle de la base, pas d'une classe. Réutiliser
     * « A1 » ferait entrer ces tests en collision avec les données
     * laissées par d'autres — un faux échec sans rapport avec ce qui est
     * vérifié ici.
     */
    private static String room() {
        return "R" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
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

    // ------------------------------------------------------------------
    // EF-PLAN-009 / EF-ORG-004 — conflit de salle contre le publié
    // ------------------------------------------------------------------

    @Test
    void uneSalleOccupeeParUneSeancePublieeEstSignaleeDansUnAutreImport() {
        String admin = adminToken();
        String firstClass = classGroup(admin);
        String secondClass = classGroup(admin);
        String firstTeacher = teacherPublicId();
        String secondTeacher = teacherPublicId();

        String sharedRoom = room();
        // Première classe publiée dans cette salle, mardi 09:00–12:00.
        String published = HEADER + "S1,2026-09-08,09:00,12:00,Europe/Paris,Cours A,"
                + firstTeacher + "," + sharedRoom + "\n";
        String firstJob = (String) upload("classe-a.csv", published, admin, firstClass)
                .getBody().get("publicId");
        post("/api/v1/planning-imports/" + firstJob + "/publish", admin);

        // Autre classe, autre formateur, MÊME salle, créneau chevauchant.
        String conflicting = HEADER + "S1,2026-09-08,10:00,11:00,Europe/Paris,Cours B,"
                + secondTeacher + "," + sharedRoom + "\n";
        String secondJob = (String) upload("classe-b.csv", conflicting, admin, secondClass)
                .getBody().get("publicId");

        Map<String, Object> job = getMap("/api/v1/planning-imports/" + secondJob, admin);
        assertThat(job.get("confirmable")).isEqualTo(false);
        assertThat(issueCodes(secondJob, admin)).contains("PLAN_CONFLICT_ROOM");
    }

    @Test
    void uneSalleDifferenteNeProduitAucunConflit() {
        String admin = adminToken();
        String firstClass = classGroup(admin);
        String secondClass = classGroup(admin);

        post("/api/v1/planning-imports/" + (String) upload("a.csv",
                HEADER + "S1,2026-09-08,09:00,12:00,Europe/Paris,Cours A," + teacherPublicId()
                        + "," + room() + "\n",
                admin, firstClass).getBody().get("publicId") + "/publish", admin);

        String secondJob = (String) upload("b.csv",
                HEADER + "S1,2026-09-08,10:00,11:00,Europe/Paris,Cours B," + teacherPublicId()
                        + "," + room() + "\n",
                admin, secondClass).getBody().get("publicId");

        assertThat(getMap("/api/v1/planning-imports/" + secondJob, admin).get("confirmable"))
                .isEqualTo(true);
        assertThat(issueCodes(secondJob, admin)).doesNotContain("PLAN_CONFLICT_ROOM");
    }

    @Test
    void deuxCreneauxSansSalleNeSontPasEnConflit() {
        String admin = adminToken();
        String firstClass = classGroup(admin);
        String secondClass = classGroup(admin);

        post("/api/v1/planning-imports/" + (String) upload("a.csv",
                HEADER + "S1,2026-09-08,09:00,12:00,Europe/Paris,Cours A," + teacherPublicId() + ",\n",
                admin, firstClass).getBody().get("publicId") + "/publish", admin);

        String secondJob = (String) upload("b.csv",
                HEADER + "S1,2026-09-08,10:00,11:00,Europe/Paris,Cours B," + teacherPublicId() + ",\n",
                admin, secondClass).getBody().get("publicId");

        // Deux créneaux sans salle n'occupent rien : ce n'est pas un conflit.
        assertThat(issueCodes(secondJob, admin)).doesNotContain("PLAN_CONFLICT_ROOM");
    }

    @Test
    void leMemeCreneauRepublieNEstPasEnConflitDeSalleContreLuiMeme() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        String csv = HEADER + "S1,2026-09-08,09:00,12:00,Europe/Paris,Cours A," + teacher
                + "," + room() + "\n";

        String firstJob = (String) upload("v1.csv", csv, admin, classId).getBody().get("publicId");
        post("/api/v1/planning-imports/" + firstJob + "/publish", admin);

        String secondJob = (String) upload("v2.csv", csv, admin, classId).getBody().get("publicId");

        assertThat(issueCodes(secondJob, admin)).doesNotContain("PLAN_CONFLICT_ROOM");
        assertThat(getMap("/api/v1/planning-imports/" + secondJob, admin).get("confirmable"))
                .isEqualTo(true);
    }

    // ------------------------------------------------------------------
    // EF-PLAN-003 — correction de ligne avant publication
    // ------------------------------------------------------------------

    @Test
    void corrigerUneLigneFautiveRendLeTravailPubliable() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        String csv = HEADER
                + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours lundi," + teacher + "," + room() + "\n"
                + "S2,pas-une-date,09:00,12:00,Europe/Paris,Cours mardi," + teacher + "," + room() + "\n";

        String jobId = (String) upload("planning.csv", csv, admin, classId).getBody().get("publicId");
        assertThat(getMap("/api/v1/planning-imports/" + jobId, admin).get("confirmable"))
                .isEqualTo(false);

        Map<String, Object> faulty = rows(jobId, admin).stream()
                .filter(row -> "ERROR".equals(row.get("rowStatus")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ligne en erreur attendue"));

        ResponseEntity<Map<String, Object>> corrected = correct(jobId,
                (String) faulty.get("publicId"), Map.of("session_date", "2026-09-08"), admin);

        assertThat(corrected.getStatusCode()).isEqualTo(HttpStatus.OK);
        // La réponse est le travail réanalysé : corriger la dernière ligne
        // fautive le rend publiable, sans réimport (docs/02 §13.6).
        assertThat(corrected.getBody().get("confirmable")).isEqualTo(true);
        assertThat(rows(jobId, admin)).noneMatch(row -> "ERROR".equals(row.get("rowStatus")));
    }

    @Test
    void unePublicationResteRefuseeSiLaCorrectionCreeUnAutreConflit() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        String csv = HEADER
                + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours lundi," + teacher + "," + room() + "\n"
                + "S2,2026-09-09,09:00,12:00,Europe/Paris,Cours mercredi," + teacher + "," + room() + "\n";
        String jobId = (String) upload("planning.csv", csv, admin, classId).getBody().get("publicId");
        assertThat(getMap("/api/v1/planning-imports/" + jobId, admin).get("confirmable"))
                .isEqualTo(true);

        Map<String, Object> second = rows(jobId, admin).stream()
                .filter(row -> "S2".equals(row.get("slotKey")))
                .findFirst()
                .orElseThrow();

        // On déplace S2 sur le créneau de S1 : le conflit apparaît, et il
        // est détecté parce que TOUT le lot est réanalysé, pas seulement
        // la ligne touchée.
        ResponseEntity<Map<String, Object>> corrected = correct(jobId,
                (String) second.get("publicId"), Map.of("session_date", "2026-09-07"), admin);

        assertThat(corrected.getBody().get("confirmable")).isEqualTo(false);
        assertThat(issueCodes(jobId, admin)).contains("PLAN_CONFLICT_CLASS");
    }

    @Test
    void unChampNonCorrigeableEstRefuse() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String jobId = (String) upload("p.csv",
                HEADER + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours," + teacherPublicId()
                        + "," + room() + "\n",
                admin, classId).getBody().get("publicId");
        String rowId = (String) rows(jobId, admin).get(0).get("publicId");

        ResponseEntity<Map<String, Object>> refused = correct(jobId, rowId,
                Map.of("row_status", "VALID"), admin);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("code")).isEqualTo("PLAN_CORRECTION_UNKNOWN_FIELD");
    }

    @Test
    void unTravailDejaPublieNEstPlusCorrigeable() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String jobId = (String) upload("p.csv",
                HEADER + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours," + teacherPublicId()
                        + "," + room() + "\n",
                admin, classId).getBody().get("publicId");
        String rowId = (String) rows(jobId, admin).get(0).get("publicId");
        post("/api/v1/planning-imports/" + jobId + "/publish", admin);

        ResponseEntity<Map<String, Object>> refused = correct(jobId, rowId,
                Map.of("title", "Autre"), admin);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().get("code")).isEqualTo("PLAN_JOB_NOT_SIMULATED");
    }

    @Test
    void uneLigneDUnAutreTravailNEstPasCorrigeable() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        String firstJob = (String) upload("un.csv",
                HEADER + "S1,2026-09-07,09:00,12:00,Europe/Paris,Un," + teacher + "," + room() + "\n",
                admin, classId).getBody().get("publicId");
        String secondJob = (String) upload("deux.csv",
                HEADER + "S2,2026-09-14,09:00,12:00,Europe/Paris,Deux," + teacher + "," + room() + "\n",
                admin, classId).getBody().get("publicId");
        String foreignRow = (String) rows(secondJob, admin).get(0).get("publicId");

        assertThat(correct(firstJob, foreignRow, Map.of("title", "X"), admin).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void laCorrectionExigeUnRoleAutorise() {
        String admin = adminToken();
        String teacherToken = tokenFor(RoleCode.TEACHER);
        String classId = classGroup(admin);
        String jobId = (String) upload("p.csv",
                HEADER + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours," + teacherPublicId()
                        + "," + room() + "\n",
                admin, classId).getBody().get("publicId");
        String rowId = (String) rows(jobId, admin).get(0).get("publicId");

        assertThat(correct(jobId, rowId, Map.of("title", "X"), teacherToken).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------

    private List<String> issueCodes(String jobId, String token) {
        return rows(jobId, token).stream()
                .flatMap(row -> ((List<?>) row.get("issues")).stream())
                .map(issue -> (String) castMap(issue).get("errorCode"))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(String jobId, String token) {
        return (List<Map<String, Object>>) getMap(
                "/api/v1/planning-imports/" + jobId + "/rows?size=100", token).get("content");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private ResponseEntity<Map<String, Object>> correct(String jobId, String rowId,
                                                        Map<String, String> corrections, String token) {
        return rest.exchange(RequestEntity
                        .post(URI.create("/api/v1/planning-imports/" + jobId + "/rows/" + rowId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).body(corrections),
                new ParameterizedTypeReference<>() {
                });
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

    private Map<String, Object> post(String path, String token) {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                RequestEntity.post(URI.create(path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).body(Map.of()),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<String, Object> getMap(String path, String token) {
        return rest.exchange(RequestEntity.get(URI.create(path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
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
        UserAccount account = new UserAccount("plan-" + UUID.randomUUID() + "@esic-connect.test",
                "Planning", "Testeur", AccountStatus.ACTIVE);
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
