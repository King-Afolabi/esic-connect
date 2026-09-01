package com.esic.connect.planning.internal;

import com.esic.connect.coursesession.PlanningSessionWriter;
import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
import com.esic.connect.planning.PlanningPublishedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Publication d'un planning — <strong>chemin d'échec</strong> (audit
 * G1-B.1 ; DEC-G1-003).
 *
 * <p>Un {@link PlanningSessionWriter} défaillant est injecté via
 * {@code @Primary} : il lève <em>après</em> le début de la publication.
 * On vérifie précisément :
 * <ul>
 *   <li>transaction métier rollbackée : 0 {@code planning_version},
 *       0 {@code planning_entry}, 0 {@code course_session} d'origine
 *       planning, {@code planning_schedule.current_version_number}
 *       inchangé, aucun {@link PlanningPublishedEvent} consommé ;</li>
 *   <li>transaction séparée ({@code REQUIRES_NEW}) : job {@code FAILED},
 *       {@code failure_reason} non sensible, exactement une
 *       {@code planning_import_job_issue} {@code PLAN_PUBLICATION_FAILED}
 *       sans cellule CSV ni donnée personnelle ;</li>
 *   <li>API : {@code 409} {@code PLAN_PUBLICATION_FAILED}, sans
 *       stacktrace, sans nom SQL, sans chemin interne ;</li>
 *   <li>un conflit métier <em>attendu</em> (ligne {@code ERROR}) ne
 *       transforme <strong>jamais</strong> le job en {@code FAILED}.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlanningPublicationFailureIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    private static final String HEADER =
            "slot_key,session_date,start_time,end_time,time_zone_id,title,teacher_public_id,room_code\n";

    /** Marqueur non sensible cherché dans les traces de fuite. */
    static final String FAULT_MARKER = "FAULT_INJECTED_BY_TEST";

    @TestConfiguration
    static class FailingWriterConfig {

        static final List<PlanningPublishedEvent> PUBLISHED_EVENTS = new CopyOnWriteArrayList<>();

        @Bean
        @Primary
        InvitationMailer noopInvitationMailer() {
            return (toEmail, firstName, rawToken, expiresAt) -> {
            };
        }

        @Bean
        @Primary
        PlanningSessionWriter failingPlanningSessionWriter() {
            return command -> {
                throw new IllegalStateException(FAULT_MARKER);
            };
        }

        @Bean
        PublishedEventRecorder publishedEventRecorder() {
            return new PublishedEventRecorder();
        }

        static class PublishedEventRecorder {
            @EventListener
            void on(PlanningPublishedEvent event) {
                PUBLISHED_EVENTS.add(event);
            }
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
    @Autowired
    private PlanningImportJobRepository jobRepository;
    @Autowired
    private PlanningImportJobIssueRepository jobIssueRepository;
    @Autowired
    private PlanningVersionRepository versionRepository;
    @Autowired
    private PlanningEntryRepository entryRepository;
    @Autowired
    private PlanningScheduleRepository scheduleRepository;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        FailingWriterConfig.PUBLISHED_EVENTS.clear();
    }

    @Test
    void unexpectedWriterFailureRollsBackEverythingAndMarksJobFailed() {
        String admin = adminToken();
        String classId = classGroup(admin);
        String teacher = teacherPublicId();
        String csv = HEADER + "S1,2026-09-07,09:00,12:00,Europe/Paris,Algorithmique," + teacher + ",A101\n";
        String jobId = (String) upload(csv, admin, classId).getBody().get("publicId");

        long versionsBefore = versionRepository.count();
        long entriesBefore = entryRepository.count();

        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST,
                "/api/v1/planning-imports/" + jobId + "/publish", admin);

        // --- API : 409 contrôlé, sans fuite ---
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo("PLAN_PUBLICATION_FAILED");
        String rendered = body.toString();
        assertThat(rendered).doesNotContain(FAULT_MARKER);
        assertThat(rendered).doesNotContain("IllegalStateException");
        assertThat(rendered).doesNotContain("course_session");
        assertThat(rendered).doesNotContain("planning_version");
        assertThat(rendered).doesNotContain("com.esic.connect");
        assertThat(rendered).doesNotContain("Exception");

        // --- Transaction métier rollbackée ---
        assertThat(versionRepository.count()).as("aucune planning_version créée").isEqualTo(versionsBefore);
        assertThat(entryRepository.count()).as("aucune planning_entry créée").isEqualTo(entriesBefore);
        assertThat(planningSessionsForClass(admin, classId)).as("aucune séance d'origine planning").isEmpty();
        assertThat(scheduleRepository.findByClassGroupIdAndAcademicYearId(
                internalClassId(jobId), internalYearId(jobId)))
                .satisfiesAnyOf(
                        opt -> assertThat(opt).isEmpty(),
                        opt -> assertThat(opt.get().getCurrentVersionNumber()).isZero());
        assertThat(FailingWriterConfig.PUBLISHED_EVENTS).as("aucun PlanningPublishedEvent consommé").isEmpty();

        // --- Transaction séparée : FAILED + 1 issue non sensible ---
        PlanningImportJob job = jobRepository.findByPublicId(UUID.fromString(jobId)).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(PlanningImportJobStatus.FAILED);
        assertThat(job.getFailureReason()).isNotBlank();
        assertThat(job.getFailureReason()).doesNotContain(FAULT_MARKER).doesNotContain("Algorithmique");
        List<PlanningImportJobIssue> issues = jobIssueRepository.findByJob_IdOrderByIdAsc(job.getId());
        assertThat(issues).extracting(PlanningImportJobIssue::getErrorCode)
                .containsExactly("PLAN_PUBLICATION_FAILED");
        PlanningImportJobIssue issue = issues.get(0);
        assertThat(issue.getMessage()).doesNotContain(FAULT_MARKER).doesNotContain("Algorithmique")
                .doesNotContain("A101").doesNotContain(teacher);

        // --- FAILED n'est pas republiable ---
        assertThat(exchange(HttpMethod.POST, "/api/v1/planning-imports/" + jobId + "/publish", admin)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void expectedBusinessConflictNeverMarksJobFailed() {
        String admin = adminToken();
        String classId = classGroup(admin);
        // teacher_public_id inconnu → ligne ERROR (RG-034), publication bloquée.
        String csv = HEADER + "S1,2026-09-07,09:00,12:00,Europe/Paris,Cours," + UUID.randomUUID() + ",A1\n";
        String jobId = (String) upload(csv, admin, classId).getBody().get("publicId");

        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST,
                "/api/v1/planning-imports/" + jobId + "/publish", admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("PLAN_BLOCKING_ISSUES");

        PlanningImportJob job = jobRepository.findByPublicId(UUID.fromString(jobId)).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(PlanningImportJobStatus.SIMULATED);
        assertThat(jobIssueRepository.findByJob_IdOrderByIdAsc(job.getId()))
                .extracting(PlanningImportJobIssue::getErrorCode)
                .doesNotContain("PLAN_PUBLICATION_FAILED");
    }

    // ------------------------------------------------------------------

    private Long internalClassId(String jobId) {
        return jobRepository.findByPublicId(UUID.fromString(jobId)).orElseThrow().getClassGroupId();
    }

    private Long internalYearId(String jobId) {
        return jobRepository.findByPublicId(UUID.fromString(jobId)).orElseThrow().getAcademicYearId();
    }

    private List<Map<String, Object>> planningSessionsForClass(String token, String classId) {
        Map<String, Object> page = getMap("/api/v1/sessions?classGroup=" + classId + "&size=100", token);
        return ((List<?>) page.get("content")).stream().map(this::castMap).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
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

    private ResponseEntity<Map<String, Object>> exchange(HttpMethod method, String path, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(method, URI.create(path));
        builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
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
