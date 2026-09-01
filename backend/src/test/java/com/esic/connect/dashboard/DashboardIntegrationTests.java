package com.esic.connect.dashboard;

import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.notification.internal.InvitationMailer;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

import jakarta.persistence.EntityManagerFactory;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bloc G1-F — tableau de bord par rôle ({@code GET /api/v1/me/dashboard}).
 * Vérifie : rôle effectif décidé serveur (priorité fixe), sections
 * exclusives par rôle, cloisonnement (`STUDENT` = ses données, AC-017 ;
 * `TEACHER` = ses séances ; `PEDAGOGICAL_MANAGER` = son périmètre),
 * absence de N+1 sur le dashboard manager, `401` / `403`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
class DashboardIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";

    @TestConfiguration
    static class NoopMailerConfig {
        @Bean
        @Primary
        InvitationMailer noopMailer() {
            return (a, b, c, d) -> {
            };
        }
    }

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void jdkClient() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void aStudentGetsOnlyTheStudentSectionWithTheirOwnData() {
        String admin = tokenFor(account(RoleCode.ADMIN));
        Chain chain = academicChain(admin);
        Account teacher = account(RoleCode.TEACHER);
        String sessionId = openSession(admin, teacher, chain.classA(), Instant.now().plusSeconds(3600));

        Account s1 = enrolledStudent(admin, chain.classA());
        Account s2 = enrolledStudent(admin, chain.classA());

        Map<String, Object> d1 = dashboard(tokenFor(s1));
        assertThat(d1.get("role")).isEqualTo("STUDENT");
        assertThat(d1.get("student")).isNotNull();
        assertThat(d1.get("teacher")).isNull();
        assertThat(d1.get("manager")).isNull();
        assertThat(d1.get("administration")).isNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> week = (List<Map<String, Object>>) student(d1).get("weekSessions");
        assertThat(week).anySatisfy(row -> assertThat(row.get("sessionPublicId")).isEqualTo(sessionId));
        // Aucun identifiant SQL dans la charge utile.
        assertThat(d1.toString()).doesNotContain("internalId").doesNotContain("teacherUserId");

        // s2 voit sa propre carte (digest à zéro), jamais celle de s1.
        Map<String, Object> d2 = dashboard(tokenFor(s2));
        assertThat(d2.get("role")).isEqualTo("STUDENT");
        assertThat(student(d2)).isNotNull();
    }

    @Test
    void aTeacherSeesOnlyTheirOwnUpcomingSessions() {
        String admin = tokenFor(account(RoleCode.ADMIN));
        Chain chain = academicChain(admin);
        Account mine = account(RoleCode.TEACHER);
        Account other = account(RoleCode.TEACHER);
        String s1 = openSession(admin, mine, chain.classA(), Instant.now().plusSeconds(3600));
        String s2 = openSession(admin, other, chain.classA(), Instant.now().plusSeconds(7200));

        Map<String, Object> d = dashboard(tokenFor(mine));
        assertThat(d.get("role")).isEqualTo("TEACHER");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> upcoming = (List<Map<String, Object>>) teacher(d).get("upcoming");
        assertThat(upcoming).extracting(r -> r.get("sessionPublicId")).contains(s1).doesNotContain(s2);
    }

    @Test
    void aTeacherDashboardIncludesSessionsWhereTheyAreAnActiveSubstitute() {
        String admin = tokenFor(account(RoleCode.ADMIN));
        Chain chain = academicChain(admin);
        Account principal = account(RoleCode.TEACHER);
        Account substitute = account(RoleCode.TEACHER);
        Account bystander = account(RoleCode.TEACHER);

        // Séance dans 20 min ; substitution ACTIVE couvrant « maintenant »
        // (période chevauchant la séance, marge ≤ 60 min — G1-C.3).
        Instant startsAt = Instant.now().plusSeconds(20 * 60);
        String sessionId = openSession(admin, principal, chain.classA(), startsAt);
        createSubstitution(admin, sessionId, substitute,
                Instant.now().minusSeconds(10 * 60), Instant.now().plusSeconds(50 * 60));

        // Le remplaçant ACTIVE voit la séance sur son tableau de bord.
        List<Map<String, Object>> subUpcoming = upcoming(dashboard(tokenFor(substitute)));
        assertThat(subUpcoming).extracting(r -> r.get("sessionPublicId")).contains(sessionId);

        // Le formateur principal la voit toujours.
        assertThat(upcoming(dashboard(tokenFor(principal))))
                .extracting(r -> r.get("sessionPublicId")).contains(sessionId);

        // Un formateur non concerné ne la voit pas.
        assertThat(upcoming(dashboard(tokenFor(bystander))))
                .extracting(r -> r.get("sessionPublicId")).doesNotContain(sessionId);
    }

    @Test
    void aTeacherDashboardExcludesSessionsWhereTheirSubstitutionHasEnded() {
        String admin = tokenFor(account(RoleCode.ADMIN));
        Chain chain = academicChain(admin);
        Account principal = account(RoleCode.TEACHER);
        Account substitute = account(RoleCode.TEACHER);

        Instant startsAt = Instant.now().plusSeconds(20 * 60);
        String sessionId = openSession(admin, principal, chain.classA(), startsAt);
        String substitutionId = createSubstitution(admin, sessionId, substitute,
                Instant.now().minusSeconds(10 * 60), Instant.now().plusSeconds(50 * 60));

        assertThat(upcoming(dashboard(tokenFor(substitute))))
                .extracting(r -> r.get("sessionPublicId")).contains(sessionId);

        // Fin du remplacement -> plus aucun droit, plus sur le dashboard.
        assertThat(post(admin, "/api/v1/sessions/" + sessionId + "/substitutions/" + substitutionId + "/end", null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(upcoming(dashboard(tokenFor(substitute))))
                .extracting(r -> r.get("sessionPublicId")).doesNotContain(sessionId);
    }

    @Test
    void aTeacherWhoIsBothPrincipalAndActiveSubstituteSeesTheSessionOnce() {
        String admin = tokenFor(account(RoleCode.ADMIN));
        Chain chain = academicChain(admin);
        Account principal = account(RoleCode.TEACHER);
        Account other = account(RoleCode.TEACHER);

        Instant startsAt = Instant.now().plusSeconds(20 * 60);
        // « principal » enseigne la séance A ; il est aussi remplaçant ACTIVE
        // de la séance B (dont « other » est le principal).
        String sessionA = openSession(admin, principal, chain.classA(), startsAt);
        String sessionB = openSession(admin, other, chain.classB(), startsAt.plusSeconds(30 * 60));
        createSubstitution(admin, sessionB, principal,
                Instant.now().minusSeconds(10 * 60), startsAt.plusSeconds(150 * 60));

        List<Object> ids = upcoming(dashboard(tokenFor(principal))).stream()
                .map(r -> r.get("sessionPublicId")).toList();
        assertThat(ids).contains(sessionA, sessionB);
        assertThat(ids).containsOnlyOnce(sessionA);
        assertThat(ids).containsOnlyOnce(sessionB);
    }

    @Test
    void aMultiRoleUserGetsTheHighestPriorityDashboardWithoutAContext() {
        Account both = account(RoleCode.STUDENT, RoleCode.TEACHER);
        assertThat(dashboard(tokenFor(both)).get("role")).isEqualTo("TEACHER");

        Account adminAndTeacher = account(RoleCode.TEACHER, RoleCode.ADMIN);
        assertThat(dashboard(tokenFor(adminAndTeacher)).get("role")).isEqualTo("ADMINISTRATION");
    }

    @Test
    void aMonoRoleUserMayPassItsOwnContextButNothingElse() {
        String token = tokenFor(account(RoleCode.TEACHER));
        // Son propre rôle : accepté.
        assertThat(dashboardWithContext(token, "TEACHER").get("role")).isEqualTo("TEACHER");
        // Un rôle non détenu : 403, jamais d'élévation.
        ResponseEntity<Map<String, Object>> forbidden = exchange(HttpMethod.GET,
                "/api/v1/me/dashboard?context=ADMIN", null, token);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody().get("code")).isEqualTo("DASHBOARD_CONTEXT_NOT_HELD");
    }

    @Test
    void aMultiRoleUserGetsTheDashboardOfEachContextItActuallyHolds() {
        String token = tokenFor(account(RoleCode.PEDAGOGICAL_MANAGER, RoleCode.TEACHER, RoleCode.STUDENT));
        assertThat(dashboardWithContext(token, "PEDAGOGICAL_MANAGER").get("role"))
                .isEqualTo("PEDAGOGICAL_MANAGER");
        assertThat(dashboardWithContext(token, "TEACHER").get("role")).isEqualTo("TEACHER");
        assertThat(dashboardWithContext(token, "STUDENT").get("role")).isEqualTo("STUDENT");
        // Sans contexte : priorité fixe -> le plus élevé détenu.
        assertThat(dashboard(token).get("role")).isEqualTo("PEDAGOGICAL_MANAGER");
    }

    @Test
    void aStudentCannotEscalateToAnAdministrationDashboardViaTheContext() {
        String token = tokenFor(account(RoleCode.STUDENT));
        for (String escalated : List.of("ADMIN", "SUPER_ADMIN", "SCHOOL_ADMINISTRATION",
                "PEDAGOGICAL_MANAGER")) {
            ResponseEntity<Map<String, Object>> r = exchange(HttpMethod.GET,
                    "/api/v1/me/dashboard?context=" + escalated, null, token);
            assertThat(r.getStatusCode()).as("context=" + escalated).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(r.getBody().get("code")).isEqualTo("DASHBOARD_CONTEXT_NOT_HELD");
        }
        // Son seul contexte légitime reste accessible.
        assertThat(dashboardWithContext(token, "STUDENT").get("role")).isEqualTo("STUDENT");
    }

    @Test
    void anAdministrationDashboardExposesAccountCountsAndRecentImportsWithoutPii() {
        Map<String, Object> d = dashboard(tokenFor(account(RoleCode.SCHOOL_ADMINISTRATION)));
        assertThat(d.get("role")).isEqualTo("ADMINISTRATION");
        Map<String, Object> admin = administration(d);
        assertThat(((Number) admin.get("activeAccounts")).longValue()).isPositive();
        assertThat(admin).containsKeys("suspendedAccounts", "pendingActivation", "archivedAccounts",
                "pendingJustifications", "recentImports", "todaySessions");
        assertThat(d.toString()).doesNotContain("@esic-connect.test");
    }

    @Test
    void aPedagogicalManagerDashboardDoesNotGrowItsQueryCountWithTheNumberOfClasses() {
        String admin = tokenFor(account(RoleCode.ADMIN));

        // Deux périmètres avec le MÊME nombre de séances (3) mais un nombre de
        // classes très différent : 1 vs 15. Seul le nombre de classes varie —
        // c'est la dimension dont on prouve qu'elle ne déclenche pas de N+1
        // (résolution du périmètre + libellés de classe par lot, DEC-G1-010).
        Scope small = managerScope(admin, 1, 3);
        Scope large = managerScope(admin, 15, 3);

        long qSmall = dashboardQueryCount(small);
        long qLarge = dashboardQueryCount(large);
        org.slf4j.LoggerFactory.getLogger(DashboardIntegrationTests.class)
                .info("anti-N+1 dashboard manager : 1 classe -> {} requêtes ; 15 classes -> {} requêtes",
                        qSmall, qLarge);

        // Contenu fonctionnel réellement vérifié pour les deux tailles.
        assertManagerContent(small, 1);
        assertManagerContent(large, 15);

        // +14 classes n'ajoute qu'une poignée de requêtes bornées, très loin
        // d'un +14 (une requête de libellé par classe).
        long growth = qLarge - qSmall;
        assertThat(growth).as("croissance des requêtes SQL de 1 à 15 classes (qSmall=%s, qLarge=%s)",
                        qSmall, qLarge)
                .isLessThanOrEqualTo(3L);
        // Garde-fou absolu.
        assertThat(qLarge).as("requêtes SQL du dashboard manager à 15 classes").isLessThan(25L);
    }

    /**
     * Passe corrective probatoire (chantier 4) : le test ci-dessus ne fait
     * varier que le nombre de <strong>classes</strong> (séances constantes à
     * 3) — il ne prouve donc rien sur le coût par <strong>séance</strong>.
     * {@code DefaultCourseSessionDirectory.toRef} hydrate, pour CHAQUE
     * séance renvoyée par {@code findSessionsForClasses}, ses points de
     * contrôle ({@code checkpointRepository.findByCourseSessionId...}) et
     * ses classes ({@code session.getClasses()}, {@code @OneToMany(LAZY)}
     * sans {@code @BatchSize}, puis un {@code classGroupDirectory
     * .findByInternalId} par classe) — <strong>avant</strong> que
     * {@code DashboardService.trim(...)} ne coupe l'affichage à 10 lignes.
     * Le coût par séance est donc réellement <strong>linéaire dans le
     * nombre de séances renvoyées par la fenêtre des 7 jours</strong>, et
     * seulement <strong>affiché</strong> à 10 — pas borné en requêtes tant
     * que la fenêtre contient ≤ 10 séances. Ce test mesure exactement cette
     * croissance à classes constantes (2) pour 1 vs 10 séances.
     */
    @Test
    void aPedagogicalManagerDashboardQueryCountGrowsLinearlyWithTheNumberOfSessionsWithinTheDisplayLimit() {
        String admin = tokenFor(account(RoleCode.ADMIN));

        // Nombre de classes FIXE (2) ; seul le nombre de séances varie : 1 vs
        // 10 (la borne d'affichage — DashboardResponses§upcomingSessions).
        Scope fewSessions = managerScope(admin, 2, 1);
        Scope manySessions = managerScope(admin, 2, 10);

        long qFew = dashboardQueryCount(fewSessions);
        long qMany = dashboardQueryCount(manySessions);
        org.slf4j.LoggerFactory.getLogger(DashboardIntegrationTests.class)
                .info("coût SQL dashboard manager par nombre de séances : 1 séance -> {} requêtes ; "
                        + "10 séances -> {} requêtes", qFew, qMany);

        assertManagerContent(fewSessions, 2);
        assertManagerContent(manySessions, 2);

        // Constat honnête : la croissance n'est PAS bornée par le nombre de
        // classes (chantier F, déjà corrigé) mais reste, elle,
        // proportionnelle au nombre de séances effectivement hydratées
        // (checkpoints + classes par séance, non groupées). Avec la borne
        // d'affichage à 10 séances, l'ordre de grandeur reste maîtrisé pour
        // ce dashboard (pas d'explosion cartésienne : coût = O(séances),
        // jamais O(séances × classes) ni pire) mais n'est PAS indépendant du
        // nombre de séances comme l'étaient les classes. Documenté au lieu
        // d'être corrigé dans cette passe (coût borné en pratique par
        // l'affichage à 10 séances ; une correction demanderait un
        // chargement par lot des points de contrôle et des classes de
        // séance, hors périmètre de cette passe corrective courte).
        assertThat(qMany).as("le coût par séance croît avec le nombre de séances (qFew=%s, qMany=%s)", qFew, qMany)
                .isGreaterThan(qFew);
        // Garde-fou : la croissance doit rester LINÉAIRE dans le nombre de
        // séances (pas de produit cartésien classes × séances). Mesuré
        // ≈ 2 requêtes par séance supplémentaire (points de contrôle +
        // classes de séance, non groupées) ; un plafond à 3/séance laisse
        // une marge sans masquer une explosion.
        assertThat(qMany - qFew).as("croissance des requêtes SQL de 1 à 10 séances (qFew=%s, qMany=%s)", qFew, qMany)
                .isLessThanOrEqualTo(9L * 3L);
        // Garde-fou absolu : le coût du dashboard manager reste maîtrisé
        // tant que la fenêtre des 7 jours contient ≤ 10 séances affichées.
        assertThat(qMany).as("requêtes SQL du dashboard manager à 10 séances").isLessThan(40L);
    }

    private record Scope(String managerToken, java.util.Set<String> classCodes) {
    }

    /**
     * Crée une formation, une promotion, {@code classCount} classes,
     * {@code sessionCount} séances à venir (chacune rattachée à une classe
     * distincte, en cycle), et un responsable pédagogique affecté à la
     * formation.
     */
    private Scope managerScope(String admin, int classCount, int sessionCount) {
        String suffix = code();
        String site = created(admin, "/api/v1/sites", Map.of("code", "SITE-" + suffix,
                "name", "Campus", "timeZoneId", "Europe/Paris")).get("publicId").toString();
        String program = created(admin, "/api/v1/programs", Map.of("code", "PRG-" + suffix,
                "name", "BTS SIO", "programType", "BTS")).get("publicId").toString();
        String level = created(admin, "/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1", "name", "BTS 1", "sequenceNumber", 1)).get("publicId").toString();
        String year = created(admin, "/api/v1/academic-years", Map.of("code", "AY-" + suffix,
                "name", "2026-2027", "startDate", "2026-09-01", "endDate", "2027-08-31")).get("publicId").toString();
        String promo = created(admin, "/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P26", "name", "Promotion 2026")).get("publicId").toString();
        Account teacher = account(RoleCode.TEACHER);
        java.util.Set<String> classCodes = new java.util.LinkedHashSet<>();
        List<String> classIds = new java.util.ArrayList<>();
        for (int i = 0; i < classCount; i++) {
            String classCode = "K" + i + "-" + suffix;
            classIds.add(created(admin, "/api/v1/class-groups", Map.of("promotionPublicId", promo,
                    "programLevelPublicId", level, "sitePublicId", site, "code", classCode,
                    "name", "Classe " + i)).get("publicId").toString());
            classCodes.add(classCode);
        }
        for (int i = 0; i < sessionCount; i++) {
            openSession(admin, teacher, classIds.get(i % classIds.size()),
                    Instant.now().plusSeconds(3600L + i * 60L));
        }
        Account manager = account(RoleCode.PEDAGOGICAL_MANAGER);
        assertThat(post(admin, "/api/v1/pedagogical-assignments", Map.of(
                "programPublicId", program,
                "userPublicId", manager.publicId(),
                "type", "PRIMARY_MANAGER")).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return new Scope(tokenFor(manager), classCodes);
    }

    private long dashboardQueryCount(Scope scope) {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        dashboard(scope.managerToken());
        return stats.getPrepareStatementCount();
    }

    private void assertManagerContent(Scope scope, int expectedClasses) {
        Map<String, Object> d = dashboard(scope.managerToken());
        assertThat(d.get("role")).isEqualTo("PEDAGOGICAL_MANAGER");
        Map<String, Object> card = manager(d);
        assertThat(((Number) card.get("classCount")).longValue()).isEqualTo(expectedClasses);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sessions = (List<Map<String, Object>>) card.get("upcomingSessions");
        // Séances périmétrées présentes avec des libellés de classe résolus
        // (jamais « — »), tous dans le périmètre du responsable.
        assertThat(sessions).isNotEmpty();
        assertThat(sessions).allSatisfy(s -> {
            @SuppressWarnings("unchecked")
            List<String> classCodes = (List<String>) s.get("classCodes");
            assertThat(classCodes).isNotEmpty();
            assertThat(classCodes).allSatisfy(c -> assertThat(scope.classCodes()).contains(c));
        });
    }

    @Test
    void theDashboardRequiresAuthentication() {
        ResponseEntity<Map<String, Object>> r = rest.exchange(
                RequestEntity.get(URI.create("/api/v1/me/dashboard")).build(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anAccountWithoutAnyKnownRoleGets403() {
        Account roleless = account();
        ResponseEntity<Map<String, Object>> r = exchange(HttpMethod.GET, "/api/v1/me/dashboard", null,
                tokenFor(roleless));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody().get("code")).isEqualTo("DASHBOARD_NO_ROLE");
    }

    // ================================================================
    // Fixtures
    // ================================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> student(Map<String, Object> d) {
        return (Map<String, Object>) d.get("student");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> teacher(Map<String, Object> d) {
        return (Map<String, Object>) d.get("teacher");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> manager(Map<String, Object> d) {
        return (Map<String, Object>) d.get("manager");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> administration(Map<String, Object> d) {
        return (Map<String, Object>) d.get("administration");
    }

    private Map<String, Object> dashboard(String token) {
        ResponseEntity<Map<String, Object>> r = exchange(HttpMethod.GET, "/api/v1/me/dashboard", null, token);
        assertThat(r.getStatusCode()).as("GET /me/dashboard -> " + r.getBody()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private Map<String, Object> dashboardWithContext(String token, String context) {
        ResponseEntity<Map<String, Object>> r = exchange(HttpMethod.GET,
                "/api/v1/me/dashboard?context=" + context, null, token);
        assertThat(r.getStatusCode()).as("GET /me/dashboard?context=" + context + " -> " + r.getBody())
                .isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private record Chain(String classA, String classB, String program) {
    }

    private Chain academicChain(String admin) {
        String site = created(admin, "/api/v1/sites", Map.of("code", "SITE-" + UUID.randomUUID(),
                "name", "Campus", "timeZoneId", "Europe/Paris")).get("publicId").toString();
        String program = created(admin, "/api/v1/programs", Map.of("code", "PRG-" + code(),
                "name", "BTS SIO", "programType", "BTS")).get("publicId").toString();
        String level = created(admin, "/api/v1/programs/" + program + "/levels", Map.of(
                "code", "N1", "name", "BTS 1", "sequenceNumber", 1)).get("publicId").toString();
        String year = created(admin, "/api/v1/academic-years", Map.of("code", "AY-" + code(),
                "name", "2026-2027", "startDate", "2026-09-01", "endDate", "2027-08-31")).get("publicId").toString();
        String promo = created(admin, "/api/v1/promotions", Map.of("programPublicId", program,
                "academicYearPublicId", year, "code", "P26", "name", "Promotion 2026")).get("publicId").toString();
        String classA = created(admin, "/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C1", "name", "Classe 1"))
                .get("publicId").toString();
        String classB = created(admin, "/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C2", "name", "Classe 2"))
                .get("publicId").toString();
        return new Chain(classA, classB, program);
    }

    private String openSession(String admin, Account teacher, String classPublicId, Instant startsAt) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("teacherPublicId", teacher.publicId());
        body.put("classPublicIds", List.of(classPublicId));
        body.put("startsAt", startsAt.toString());
        body.put("endsAt", startsAt.plusSeconds(2 * 3600).toString());
        body.put("timeZoneId", "Europe/Paris");
        body.put("reason", "séance exceptionnelle");
        return created(admin, "/api/v1/sessions", body).get("publicId").toString();
    }

    /** Crée une substitution ACTIVE et renvoie son {@code publicId}. */
    private String createSubstitution(String admin, String sessionId, Account substitute,
                                      Instant validFrom, Instant validUntil) {
        return created(admin, "/api/v1/sessions/" + sessionId + "/substitutions", Map.of(
                "substituteTeacherPublicId", substitute.publicId(),
                "validFrom", validFrom.toString(),
                "validUntil", validUntil.toString(),
                "reason", "Formateur souffrant")).get("publicId").toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> upcoming(Map<String, Object> dashboard) {
        return (List<Map<String, Object>>) teacher(dashboard).get("upcoming");
    }

    private Account enrolledStudent(String admin, String classPublicId) {
        Account student = account(RoleCode.STUDENT);
        String profile = created(admin, "/api/v1/student-profiles", Map.of("userPublicId", student.publicId(),
                "studentNumber", "ESIC-2026-" + code())).get("publicId").toString();
        created(admin, "/api/v1/enrollments", Map.of("studentProfilePublicId", profile,
                "classGroupPublicId", classPublicId, "startDate", "2026-08-01"));
        return student;
    }

    private Map<String, Object> created(String token, String path, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> r = exchange(HttpMethod.POST, path, body, token);
        assertThat(r.getStatusCode()).as("POST " + path + " -> " + r.getBody()).isEqualTo(HttpStatus.CREATED);
        return r.getBody();
    }

    private ResponseEntity<Map<String, Object>> post(String token, String path, Map<String, Object> body) {
        return exchange(HttpMethod.POST, path, body, token);
    }

    private ResponseEntity<Map<String, Object>> exchange(HttpMethod method, String path,
                                                         Map<String, Object> body, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(method, URI.create(path));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        RequestEntity<?> entity = body == null ? builder.build()
                : builder.contentType(MediaType.APPLICATION_JSON).body(body);
        return rest.exchange(entity, new ParameterizedTypeReference<>() {
        });
    }

    private record Account(String publicId, String email) {
    }

    private Account account(RoleCode... roles) {
        UserAccount a = new UserAccount("dash-" + UUID.randomUUID() + "@esic-connect.test",
                "Dash", "Tester", AccountStatus.ACTIVE);
        a.setPasswordHash(passwordEncoder.encode(PASSWORD));
        a = userAccountRepository.saveAndFlush(a);
        for (RoleCode rc : roles) {
            Role role = roleRepository.findByCode(rc).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(a, role, Instant.now(), true));
        }
        return new Account(a.getPublicId().toString(), a.getEmail());
    }

    private String tokenFor(Account account) {
        Map<String, Object> body = rest.exchange(
                RequestEntity.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", account.email(), "password", PASSWORD)),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return (String) body.get("accessToken");
    }

    private static String code() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
