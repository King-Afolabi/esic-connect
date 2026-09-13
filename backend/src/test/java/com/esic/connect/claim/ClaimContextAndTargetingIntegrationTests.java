package com.esic.connect.claim;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contexte lisible d'une réclamation (Lot 18) et ciblage facultatif d'un
 * formateur sur le guichet TEACHER (Lot 19) : les champs {@code
 * authorName}, {@code sessionLabel}, {@code classLabel} et {@code
 * targetTeacherName} résolvent un affichage lisible sans exposer
 * {@code GET /users/{id}} à un rôle supplémentaire ; le ciblage vérifie
 * toujours le compte visé (existant, actif, rôle {@code TEACHER} actif)
 * avant de le mémoriser.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ClaimContextAndTargetingIntegrationTests {

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

    @BeforeEach
    void useJdkClient() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void laReclamationExposeLeNomDeLAuteurEtLaClasse() {
        ClassContext context = classContext();
        String claimId = claimId(openClaim(tokenFor(context.student()), "PEDAGOGICAL_MANAGER"));

        Map<String, Object> thread = getMap("/api/v1/claims/" + claimId, tokenFor(context.manager()));
        Map<String, Object> claim = castMap(thread.get("claim"));

        assertThat(claim.get("authorName")).asString().contains("Reclamation");
        assertThat(claim.get("classLabel")).asString().contains(context.classCode());
    }

    @Test
    void laReclamationAvecUneSeanceExposeUnLibelleDeSeance() {
        ClassContext context = classContext();
        String sessionId = createSession(context);

        Map<String, Object> claim = created("/api/v1/claims", Map.of(
                "category", "ATTENDANCE", "subject", "À propos de cette séance",
                "description", "Un souci sur cette séance précise.",
                "audience", "PEDAGOGICAL_MANAGER", "sessionPublicId", sessionId),
                tokenFor(context.student()));

        assertThat(claim.get("sessionLabel")).asString().contains("Cours de test");
    }

    @Test
    void unFormateurCibleExplicitementEstExposeEtPrioritairePourLeGuichetTeacher() {
        ClassContext context = classContext();
        Account teacher = accountWithRoles(RoleCode.TEACHER);

        Map<String, Object> claim = created("/api/v1/claims", Map.of(
                "category", "ATTENDANCE", "subject", "Question pour mon formateur",
                "description", "J'aimerais une précision.",
                "audience", "TEACHER", "targetTeacherPublicId", teacher.publicId()),
                tokenFor(context.student()));

        assertThat(claim.get("targetTeacherPublicId")).isEqualTo(teacher.publicId());
        assertThat(claim.get("targetTeacherName")).asString().contains("Reclamation");
    }

    @Test
    void unFormateurCibleNonEligibleEstRefuse() {
        ClassContext context = classContext();
        Account notATeacher = accountWithRoles(RoleCode.STUDENT);

        ResponseEntity<Map<String, Object>> rejected = exchange(HttpMethod.POST, "/api/v1/claims", Map.of(
                "category", "ATTENDANCE", "subject", "Question", "description", "Un souci.",
                "audience", "TEACHER", "targetTeacherPublicId", notATeacher.publicId()),
                tokenFor(context.student()));

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody().get("code")).isEqualTo("CLAIM_TARGET_TEACHER_NOT_ELIGIBLE");
    }

    @Test
    void laRechercheDeSeancesEstVideSansTermeDeRecherche() {
        ClassContext context = classContext();
        List<Object> results = getList("/api/v1/claims/sessions/search", tokenFor(context.student()));
        assertThat(results).isEmpty();
    }

    @Test
    void laRechercheDeSeancesTrouveUneSeanceDeLaClasseDeLAuteur() {
        ClassContext context = classContext();
        createSession(context);

        List<Object> results = getList("/api/v1/claims/sessions/search?q=Cours", tokenFor(context.student()));
        assertThat(results).isNotEmpty();
    }

    @Test
    void laRechercheDeFormateursIgnoreUneRequeteVideEtTrouveUnFormateurEligible() {
        ClassContext context = classContext();
        accountWithRoles(RoleCode.TEACHER, "Zzrareprefix");

        List<Object> empty = getList("/api/v1/claims/teachers/search", tokenFor(context.student()));
        assertThat(empty).isEmpty();

        List<Object> found = getList("/api/v1/claims/teachers/search?q=Zzrareprefix", tokenFor(context.student()));
        assertThat(found).isNotEmpty();
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private record ClassContext(Account student, Account manager, String adminToken, String classId,
                                String classCode) {
    }

    private ClassContext classContext() {
        String admin = tokenFor(accountWithRoles(RoleCode.ADMIN));
        Account student = accountWithRoles(RoleCode.STUDENT);
        String suffix = code();
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
        String classCode = "C-" + suffix;
        String classId = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", classCode,
                "name", "Classe"), admin).get("publicId");
        created("/api/v1/enrollments", Map.of("studentUserPublicId", student.publicId(),
                "classGroupPublicId", classId,
                "startDate", java.time.LocalDate.now().minusDays(30).toString()), admin);

        Account manager = accountWithRoles(RoleCode.PEDAGOGICAL_MANAGER);
        created("/api/v1/pedagogical-assignments", Map.of(
                "programPublicId", program, "userPublicId", manager.publicId(),
                "type", "PRIMARY_MANAGER", "reason", "responsable de la formation"), admin);
        return new ClassContext(student, manager, admin, classId, classCode);
    }

    /** Séance exceptionnelle rattachée à la classe du contexte, pour un dépôt lié à une séance. */
    private String createSession(ClassContext context) {
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        HashMap<String, Object> body = new HashMap<>();
        body.put("teacherPublicId", teacher.publicId());
        body.put("classPublicIds", List.of(context.classId()));
        body.put("startsAt", now.plusSeconds(3600).toString());
        body.put("endsAt", now.plusSeconds(3 * 3600).toString());
        body.put("timeZoneId", "Europe/Paris");
        body.put("reason", "séance de test");
        body.put("title", "Cours de test");
        return (String) created("/api/v1/sessions", body, context.adminToken()).get("publicId");
    }

    private Map<String, Object> openClaim(String token, String audience) {
        return created("/api/v1/claims", Map.of(
                "category", "ATTENDANCE", "subject", "Absence contestée",
                "description", "Je conteste cette absence.", "audience", audience), token);
    }

    private static String claimId(Map<String, Object> claim) {
        return (String) claim.get("publicId");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isIn(HttpStatus.CREATED, HttpStatus.OK, HttpStatus.NO_CONTENT);
        return response.getBody() != null ? response.getBody() : Map.of();
    }

    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET, path, null, token);
        assertThat(response.getStatusCode()).as("GET %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<Object> getList(String path, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(HttpMethod.GET, URI.create(path));
        builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        ResponseEntity<List<Object>> response = rest.exchange(builder.build(), new ParameterizedTypeReference<>() {
        });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
        return accountWithRoles(roles, "Reclamation");
    }

    private Account accountWithRoles(RoleCode role, String firstName) {
        return accountWithRoles(new RoleCode[] {role}, firstName);
    }

    private Account accountWithRoles(RoleCode[] roles, String firstName) {
        UserAccount account = new UserAccount("claim-ctx-" + UUID.randomUUID() + "@esic-connect.test",
                firstName, "Testeur", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return new Account(account.getPublicId().toString(), account.getEmail());
    }

    private String tokenFor(Account account) {
        return AuthTestSupport.accessToken(rest, account.email(), PASSWORD);
    }

    private static String code() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
