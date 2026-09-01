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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Publication d'un planning (EF-PLAN-004/005/007 ; EF-SES-001 ;
 * AC-007/008 ; RG-030..RG-034 ; DEC-G1-001/003/004) : transaction
 * atomique, séances créées via le port {@code coursesession}, invariant
 * « simulation ⇒ 0 séance », versionnement N/N+1, ancienne version
 * {@code SUPERSEDED}, idempotence, ligne bloquante, concurrence sans
 * {@code 500}, sécurité par rôle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlanningPublicationIntegrationTests {

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
    void publishesAVersionAndCreatesPlanningSessionsOnlyAfterConfirmation() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        String csv = HEADER
                + "S1,2026-09-07,09:00,12:00,Europe/Paris,Algorithmique," + teacher + ",A101\n"
                + "S2,2026-09-07,13:30,17:00,Europe/Paris,Bases de données," + teacher + ",A101\n";

        String jobId = (String) upload("planning.csv", csv, admin, classId).getBody().get("publicId");
        // AC-007 : simulation ⇒ aucune séance.
        assertThat(planningSessionsForClass(admin, classId)).isEmpty();

        Map<String, Object> published = post("/api/v1/planning-imports/" + jobId + "/publish", admin);
        assertThat(((Number) published.get("versionNumber")).intValue()).isEqualTo(1);
        assertThat(published.get("alreadyPublished")).isEqualTo(false);

        // Deux séances d'origine planning, PLANNED, sans motif d'exception.
        List<Map<String, Object>> sessions = planningSessionsForClass(admin, classId);
        assertThat(sessions).hasSize(2);
        assertThat(sessions).allSatisfy(s -> {
            assertThat(s.get("status")).isEqualTo("PLANNED");
            assertThat(s.get("exceptionReason")).isNull();
        });

        // La version est consultable et PUBLISHED, avec ses 2 entrées.
        Map<String, Object> versions = getMap(
                "/api/v1/planning/versions?classGroupPublicId=" + classId, admin);
        List<?> content = (List<?>) versions.get("content");
        assertThat(content).hasSize(1);
        assertThat(((Map<?, ?>) content.get(0)).get("status")).isEqualTo("PUBLISHED");
        String versionId = (String) ((Map<?, ?>) content.get(0)).get("publicId");
        Map<String, Object> detail = getMap("/api/v1/planning/versions/" + versionId, admin);
        assertThat((List<?>) detail.get("entries")).hasSize(2);
        assertThat(((List<?>) detail.get("entries")).stream()
                .allMatch(e -> ((Map<?, ?>) e).get("sessionPublicId") != null)).isTrue();
    }

    @Test
    void republishingIsIdempotent() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        String csv = HEADER + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours," + teacher + ",A1\n";
        String jobId = (String) upload("planning.csv", csv, admin, classId).getBody().get("publicId");

        Map<String, Object> first = post("/api/v1/planning-imports/" + jobId + "/publish", admin);
        Map<String, Object> second = post("/api/v1/planning-imports/" + jobId + "/publish", admin);
        assertThat(second.get("alreadyPublished")).isEqualTo(true);
        assertThat(second.get("versionPublicId")).isEqualTo(first.get("versionPublicId"));
        assertThat(planningSessionsForClass(admin, classId)).hasSize(1);
    }

    @Test
    void republicationOfAModifiedImportCreatesVersionTwoAndSupersedesVersionOne() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();

        String v1 = HEADER
                + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours A," + teacher + ",A1\n"
                + "S2,2026-09-08,09:00,12:00,Europe/Paris,Cours B," + teacher + ",A1\n";
        String job1 = (String) upload("planning.csv", v1, admin, classId).getBody().get("publicId");
        post("/api/v1/planning-imports/" + job1 + "/publish", admin);

        // S1 modifié (nouvel horaire), S2 retiré, S3 ajouté.
        String v2 = HEADER
                + "S1,2026-09-07,10:00,13:00,Europe/Paris,Cours A," + teacher + ",A1\n"
                + "S3,2026-09-09,09:00,12:00,Europe/Paris,Cours C," + teacher + ",A1\n";
        String job2 = (String) upload("planning.csv", v2, admin, classId).getBody().get("publicId");
        Map<String, Object> published2 = post("/api/v1/planning-imports/" + job2 + "/publish", admin);
        assertThat(((Number) published2.get("versionNumber")).intValue()).isEqualTo(2);

        Map<String, Object> versions = getMap(
                "/api/v1/planning/versions?classGroupPublicId=" + classId + "&sort=versionNumber,asc", admin);
        List<?> content = (List<?>) versions.get("content");
        assertThat(content).hasSize(2);
        assertThat(((Map<?, ?>) content.get(0)).get("status")).isEqualTo("SUPERSEDED");
        assertThat(((Map<?, ?>) content.get(0)).get("replacedByVersionPublicId")).isNotNull();
        assertThat(((Map<?, ?>) content.get(1)).get("status")).isEqualTo("PUBLISHED");

        // La séance S1 est réutilisée (toujours 1 séance active pour S1),
        // S2 supersédée (filtrée de l'affichage), S3 créée.
        List<Map<String, Object>> active = planningSessionsForClass(admin, classId);
        assertThat(active).hasSize(2);
        assertThat(active).extracting(s -> s.get("title")).containsExactlyInAnyOrder("Cours A", "Cours C");
    }

    @Test
    void publishingAJobWithBlockingRowsIsRejectedAndLeavesItSimulated() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String csv = HEADER
                + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours," + UUID.randomUUID() + ",A1\n";
        String jobId = (String) upload("planning.csv", csv, admin, classId).getBody().get("publicId");

        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST,
                "/api/v1/planning-imports/" + jobId + "/publish", admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("PLAN_BLOCKING_ISSUES");
        assertThat(getMap("/api/v1/planning-imports/" + jobId, admin).get("status")).isEqualTo("SIMULATED");
    }

    /**
     * Deux publications concurrentes du <strong>même</strong> job : issue
     * strictement idempotente (audit G1-B.1). État final EXACT :
     * <ul>
     *   <li>les deux réponses sont {@code 200} (jamais {@code 409}, jamais
     *       {@code 5xx}) ;</li>
     *   <li>exactement une réponse porte {@code alreadyPublished=false}
     *       (la gagnante) et l'autre {@code alreadyPublished=true} ;</li>
     *   <li>les deux pointent la même {@code versionPublicId} ;</li>
     *   <li>le job est {@code PUBLISHED}, {@code publishedVersionPublicId}
     *       non nul, {@code failureReason} nul — <strong>jamais</strong>
     *       {@code FAILED} ni {@code PLAN_PUBLICATION_FAILED} ;</li>
     *   <li>exactement <strong>une</strong> version et <strong>une</strong>
     *       séance ; aucune séance supersédée par erreur.</li>
     * </ul>
     */
    @Test
    void concurrentPublishOfSameJobIsStrictlyIdempotent() throws Exception {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        String csv = HEADER + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours," + teacher + ",A1\n";
        String jobId = (String) upload("planning.csv", csv, admin, classId).getBody().get("publicId");

        String path = "/api/v1/planning-imports/" + jobId + "/publish";
        Callable<ResponseEntity<Map<String, Object>>> call = () -> exchange(HttpMethod.POST, path, admin);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        ResponseEntity<Map<String, Object>> r1;
        ResponseEntity<Map<String, Object>> r2;
        try {
            List<Future<ResponseEntity<Map<String, Object>>>> futures = pool.invokeAll(List.of(call, call));
            r1 = futures.get(0).get();
            r2 = futures.get(1).get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Boolean> alreadyFlags = List.of(
                (Boolean) r1.getBody().get("alreadyPublished"),
                (Boolean) r2.getBody().get("alreadyPublished"));
        assertThat(alreadyFlags).as("exactement une gagnante, une idempotente")
                .containsExactlyInAnyOrder(false, true);
        assertThat(r1.getBody().get("versionPublicId"))
                .isEqualTo(r2.getBody().get("versionPublicId"));

        Map<String, Object> job = getMap("/api/v1/planning-imports/" + jobId, admin);
        assertThat(job.get("status")).isEqualTo("PUBLISHED");
        assertThat(job.get("publishedVersionPublicId")).isNotNull();
        assertThat(job.get("failureReason")).isNull();

        Map<String, Object> versions = getMap(
                "/api/v1/planning/versions?classGroupPublicId=" + classId, admin);
        assertThat((List<?>) versions.get("content")).hasSize(1);
        assertThat(planningSessionsForClass(admin, classId)).hasSize(1);
    }

    /**
     * Une séance retirée par une republication (DEC-G1-004 règle 4) est
     * <strong>inactive</strong> pour tout accès métier : invisible en
     * liste, non résolvable par identifiant, non ouvrable, sans jeton.
     * Seul l'historique des versions de planning continue de la montrer,
     * avec son {@code sessionPublicId} (audit G1-B.1).
     */
    @Test
    void supersededSessionIsInactiveButRemainsInPlanningVersionHistory() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();

        String v1 = HEADER
                + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours A," + teacher + ",A1\n"
                + "S2,2026-09-08,09:00,12:00,Europe/Paris,Cours B," + teacher + ",A1\n";
        String job1 = (String) upload("planning.csv", v1, admin, classId).getBody().get("publicId");
        post("/api/v1/planning-imports/" + job1 + "/publish", admin);
        String versionOneId = (String) ((Map<?, ?>) ((List<?>) getMap(
                "/api/v1/planning/versions?classGroupPublicId=" + classId, admin).get("content")).get(0))
                .get("publicId");

        // Republication sans S2 → la séance de S2 est supersédée.
        String v2 = HEADER + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours A," + teacher + ",A1\n";
        String job2 = (String) upload("planning.csv", v2, admin, classId).getBody().get("publicId");
        post("/api/v1/planning-imports/" + job2 + "/publish", admin);

        // Identifiant de la séance supersédée : lu dans l'historique de la version 1.
        Map<String, Object> v1Detail = getMap("/api/v1/planning/versions/" + versionOneId, admin);
        List<?> v1Entries = (List<?>) v1Detail.get("entries");
        String supersededSessionId = (String) v1Entries.stream()
                .map(PlanningPublicationIntegrationTests::castMap)
                .filter(e -> "S2".equals(e.get("slotKey")))
                .findFirst().orElseThrow()
                .get("sessionPublicId");
        assertThat(supersededSessionId).as("l'historique de la version 1 garde le sessionPublicId").isNotNull();

        // Inactive pour tout accès métier.
        assertThat(exchange(HttpMethod.GET, "/api/v1/sessions/" + supersededSessionId, admin)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + supersededSessionId + "/open", admin)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange(HttpMethod.POST,
                "/api/v1/sessions/" + supersededSessionId + "/attendance-token", admin)
                .getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.CONFLICT);
        assertThat(planningSessionsForClass(admin, classId))
                .noneMatch(s -> supersededSessionId.equals(s.get("publicId")));
    }

    /**
     * La simulation détecte un conflit formateur / classe avec une séance
     * <strong>déjà publiée</strong> (RG-034 ; audit G1-B.1) via le port
     * {@code CourseSessionDirectory}. Le <strong>même créneau republié</strong>
     * (même {@code slot_key}) n'est jamais signalé contre lui-même.
     */
    @Test
    void simulationDetectsConflictWithAlreadyPublishedSessionButNotWithTheSameSlot() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();

        String v1 = HEADER
                + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours lundi," + teacher + ",A1\n"
                + "S2,2026-09-08,09:00,12:00,Europe/Paris,Cours mardi," + teacher + ",A1\n";
        String job1 = (String) upload("planning.csv", v1, admin, classId).getBody().get("publicId");
        post("/api/v1/planning-imports/" + job1 + "/publish", admin);

        // v2 : S1 inchangé (même créneau → contre lui-même : pas de conflit) ;
        // S9 (nouveau slot) le MARDI 10:00-11:00 chevauche la séance
        // publiée S2 (même formateur, même classe) mais PAS S1 (lundi) →
        // seul S9 doit être en conflit "déjà publié".
        String v2 = HEADER
                + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours lundi," + teacher + ",A1\n"
                + "S9,2026-09-08,10:00,11:00,Europe/Paris,Atelier mardi," + teacher + ",A1\n";
        String job2 = (String) upload("planning.csv", v2, admin, classId).getBody().get("publicId");

        Map<String, Object> job2Body = getMap("/api/v1/planning-imports/" + job2, admin);
        assertThat(job2Body.get("confirmable")).isEqualTo(false);

        List<?> rows = (List<?>) getMap("/api/v1/planning-imports/" + job2 + "/rows", admin).get("content");
        Map<String, Object> s9 = rows.stream().map(PlanningPublicationIntegrationTests::castMap)
                .filter(r -> "S9".equals(r.get("slotKey"))).findFirst().orElseThrow();
        Map<String, Object> s1 = rows.stream().map(PlanningPublicationIntegrationTests::castMap)
                .filter(r -> "S1".equals(r.get("slotKey"))).findFirst().orElseThrow();

        List<String> s9Codes = ((List<?>) s9.get("issues")).stream()
                .map(i -> (String) castMap(i).get("errorCode")).toList();
        assertThat(s9.get("rowStatus")).isEqualTo("ERROR");
        assertThat(s9Codes).contains("PLAN_CONFLICT_TEACHER", "PLAN_CONFLICT_CLASS");

        // S1 = même créneau republié → PAS de conflit contre lui-même.
        List<String> s1Codes = ((List<?>) s1.get("issues")).stream()
                .map(i -> (String) castMap(i).get("errorCode")).toList();
        assertThat(s1Codes).doesNotContain("PLAN_CONFLICT_TEACHER", "PLAN_CONFLICT_CLASS");
        assertThat(s1.get("rowStatus")).isNotEqualTo("ERROR");
    }

    @Test
    void publishIsForbiddenForTeacherAndStudent() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        String csv = HEADER + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours," + teacher + ",A1\n";
        String jobId = (String) upload("planning.csv", csv, admin, classId).getBody().get("publicId");

        assertThat(exchange(HttpMethod.POST, "/api/v1/planning-imports/" + jobId + "/publish",
                tokenFor(RoleCode.TEACHER)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(HttpMethod.POST, "/api/v1/planning-imports/" + jobId + "/publish",
                tokenFor(RoleCode.STUDENT)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------

    private List<Map<String, Object>> planningSessionsForClass(String token, String classId) {
        // Filtre serveur par classe : robuste quel que soit l'état global
        // de la base de test partagée (les séances supersédées sont déjà
        // exclues côté serveur — DEC-G1-004 règle 4).
        Map<String, Object> page = getMap("/api/v1/sessions?classGroup=" + classId + "&size=100", token);
        return ((List<?>) page.get("content")).stream().map(o -> castMap(o)).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }

    private ResponseEntity<Map<String, Object>> upload(String fileName, String csv, String token, String classId) {
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
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(URI.create("/api/v1/planning-imports"), HttpMethod.POST,
                new HttpEntity<>(parts, headers), new ParameterizedTypeReference<>() {
                });
    }

    private Map<String, Object> post(String path, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, token);
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<Map<String, Object>> exchange(HttpMethod method, String path, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(method, URI.create(path));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return restTemplate.exchange(builder.build(), new ParameterizedTypeReference<>() {
        });
    }

    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.get(URI.create(path)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).as("GET " + path).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                RequestEntity.post(URI.create(path)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).body(body),
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
        return tokenFor(RoleCode.ADMIN);
    }

    private String tokenFor(RoleCode... roles) {
        Account a = account(roles);
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
