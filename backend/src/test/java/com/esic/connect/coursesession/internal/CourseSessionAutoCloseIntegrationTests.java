package com.esic.connect.coursesession.internal;

import com.esic.connect.audit.internal.AuditEvent;
import com.esic.connect.audit.internal.AuditEventRepository;
import com.esic.connect.coursesession.CourseSessionChangeAction;
import com.esic.connect.coursesession.CourseSessionChangeEvent;
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
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.OptimisticLockingFailureException;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fermeture automatique des séances {@code OPEN} restées sans
 * intervention (Lot 9). <strong>Aucune attente réelle</strong> : le délai
 * de grâce n'est jamais laissé s'écouler en le vivant — chaque séance est
 * créée avec un {@code endsAt} déjà antérieur de l'écart voulu à
 * l'instant courant ({@code Instant.now()}, horloge réelle). L'horloge de
 * l'application n'est volontairement <strong>pas</strong> figée ici :
 * elle est partagée par la validation TOTP du second facteur
 * ({@link com.esic.connect.support.AuthTestSupport}, qui calcule son code
 * avec l'horloge réelle de la JVM) — la figer aurait désynchronisé les
 * deux et cassé la connexion des comptes de test. Le planificateur —
 * désactivé par défaut en profil {@code test}, voir
 * {@code application-test.yml} — est réactivé explicitement et invoqué
 * <strong>directement</strong> (jamais via {@code @Scheduled}, jamais en
 * attendant réellement).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.coursesession.auto-close.enabled=true",
                // Large : la base de test partagée accumule des séances OPEN
                // d'autres suites (jamais fermées, hors du périmètre de ces
                // tests) ; un lot large garantit que les séances CRÉÉES ICI
                // sont bien traitées en un seul passage, quel que soit ce
                // passif. Le comportement de bornage lui-même (plusieurs
                // passages nécessaires, un lot précis) est vérifié de façon
                // isolée et déterministe par CourseSessionAutoCloseSchedulerTests
                // (dépôts simulés, aucune interférence possible).
                "app.coursesession.auto-close.batch-size=5000"
        })
@ActiveProfiles("test")
class CourseSessionAutoCloseIntegrationTests {

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

    /**
     * Injection de faute (même technique que
     * {@code coursesession.CourseSessionIntegrationTests.RollbackFaultConfig}) :
     * un écouteur synchrone qui lève, quand armé pour un
     * {@code resourcePublicId} précis, une {@link OptimisticLockingFailureException}
     * — simule une course concurrente pendant la fermeture d'UNE séance du
     * lot, sans affecter les autres.
     */
    @TestConfiguration
    static class FaultConfig {
        static final AtomicReference<UUID> ARMED_FOR = new AtomicReference<>();

        static class FaultListener {
            @EventListener
            public void onChange(CourseSessionChangeEvent event) {
                UUID target = ARMED_FOR.get();
                if (target != null && target.equals(event.resourcePublicId())
                        && event.action() == CourseSessionChangeAction.AUTO_CLOSED) {
                    ARMED_FOR.set(null); // coup unique
                    throw new OptimisticLockingFailureException("panne injectée (test)");
                }
            }
        }

        @Bean
        FaultListener faultListener() {
            return new FaultListener();
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
    private AuditEventRepository auditEventRepository;
    @Autowired
    private CourseSessionAutoCloseScheduler scheduler;

    @BeforeEach
    void useJdkClient() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        FaultConfig.ARMED_FOR.set(null);
    }

    // ------------------------------------------------------------------
    // Éligibilité (délai de grâce, défaut 15 min)
    // ------------------------------------------------------------------

    @Test
    void aSessionPastItsGracePeriodIsClosedAutomatically() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        String id = openSessionEndingAt(admin, chain, now().minusSeconds(16 * 60)); // finie il y a 16 min > 15 min

        scheduler.closeOverdueSessions();

        Map<String, Object> after = getMap("/api/v1/sessions/" + id, admin);
        assertThat(after.get("status")).isEqualTo("CLOSED");
        assertThat(auditActions(id)).contains("SESSION_AUTO_CLOSED").doesNotContain("SESSION_CLOSED");
    }

    @Test
    void aSessionStillWithinItsGracePeriodIsLeftOpen() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        String id = openSessionEndingAt(admin, chain, now().minusSeconds(5 * 60)); // finie il y a 5 min < 15 min

        scheduler.closeOverdueSessions();

        assertThat(getMap("/api/v1/sessions/" + id, admin).get("status")).isEqualTo("OPEN");
        assertThat(auditActions(id)).doesNotContain("SESSION_AUTO_CLOSED");
    }

    @Test
    void aSessionExactlyAtItsGracePeriodBoundaryIsEligible() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        // endsAt + 15 min == now() pile : bornes inclusives (>=), doit être fermée.
        String id = openSessionEndingAt(admin, chain, now().minusSeconds(15 * 60));

        scheduler.closeOverdueSessions();

        assertThat(getMap("/api/v1/sessions/" + id, admin).get("status")).isEqualTo("CLOSED");
    }

    @Test
    void aFutureOpenSessionIsNeverSelected() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        // endsAt dans le futur par rapport à maintenant : jamais un candidat.
        String id = openSessionEndingAt(admin, chain, now().plusSeconds(3600));

        scheduler.closeOverdueSessions();

        assertThat(getMap("/api/v1/sessions/" + id, admin).get("status")).isEqualTo("OPEN");
    }

    // ------------------------------------------------------------------
    // Statuts non concernés
    // ------------------------------------------------------------------

    @Test
    void anAlreadyClosedSessionIsNotTouchedAgain() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        String id = openSessionEndingAt(admin, chain, now().minusSeconds(20 * 60));
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/close", null, admin);
        int auditBefore = auditActions(id).size();

        scheduler.closeOverdueSessions();

        assertThat(getMap("/api/v1/sessions/" + id, admin).get("status")).isEqualTo("CLOSED");
        // Course avec une fermeture manuelle survenue avant le passage du
        // planificateur : ignorée silencieusement, aucun second événement.
        assertThat(auditActions(id)).hasSize(auditBefore);
        assertThat(auditActions(id)).doesNotContain("SESSION_AUTO_CLOSED");
    }

    @Test
    void aCancelledSessionIsNotClosed() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        String id = (String) created("/api/v1/sessions",
                createBody(chain, now().minusSeconds(20 * 60)), admin).get("publicId");
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, admin);
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/cancel", Map.of("reason", "annulée"), admin);

        scheduler.closeOverdueSessions();

        assertThat(getMap("/api/v1/sessions/" + id, admin).get("status")).isEqualTo("CANCELLED");
        assertThat(auditActions(id)).doesNotContain("SESSION_AUTO_CLOSED");
    }

    // ------------------------------------------------------------------
    // Points de contrôle
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    void openCheckpointsCloseWithTheSessionAndCancelledOnesAreLeftAlone() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        String id = openSessionEndingAt(admin, chain, now().minusSeconds(20 * 60));
        String extra = (String) created("/api/v1/sessions/" + id + "/checkpoints",
                Map.of("label", "Reprise", "type", "CUSTOM"), admin).get("publicId");
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/checkpoints/" + extra + "/cancel", null, admin);

        scheduler.closeOverdueSessions();

        Map<String, Object> after = getMap("/api/v1/sessions/" + id, admin);
        assertThat(after.get("status")).isEqualTo("CLOSED");
        List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) after.get("checkpoints");
        assertThat(checkpoints).anySatisfy(cp -> {
            if (extra.equals(cp.get("publicId"))) {
                assertThat(cp.get("status")).isEqualTo("CANCELLED"); // pas rouvert / refermé
            }
        });
        assertThat(checkpoints).filteredOn(cp -> !extra.equals(cp.get("publicId")))
                .allSatisfy(cp -> assertThat(cp.get("status")).isEqualTo("CLOSED"));
    }

    // ------------------------------------------------------------------
    // Purge Redis (au même titre qu'une fermeture manuelle)
    // ------------------------------------------------------------------

    @Test
    void autoCloseInvalidatesAttendanceTokensLikeAManualClose() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        String id = openSessionEndingAt(admin, chain, now().minusSeconds(20 * 60));
        Account student = accountWithRoles(RoleCode.STUDENT);
        created("/api/v1/enrollments", Map.of("studentUserPublicId", student.publicId(),
                "classGroupPublicId", chain.classA()), admin);
        Map<String, Object> issued = post("/api/v1/sessions/" + id + "/attendance-token", null, admin);
        String shortCode = (String) issued.get("shortCode");

        scheduler.closeOverdueSessions();

        ResponseEntity<Map<String, Object>> afterClose = exchange(HttpMethod.POST, "/api/v1/attendance/validate",
                Map.of("shortCode", shortCode), tokenFor(student));
        assertThat(afterClose.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(afterClose.getBody().get("code")).isIn("ATT_SESSION_CLOSED", "ATT_TOKEN_INVALID");
    }

    // ------------------------------------------------------------------
    // Poursuite malgré un échec individuel (le bornage du lot lui-même
    // est vérifié, isolé et déterministe, par CourseSessionAutoCloseSchedulerTests)
    // ------------------------------------------------------------------

    @Test
    void aFailureOnOneSessionDoesNotStopTheOthers() {
        String admin = adminToken();
        String faulty = openSessionEndingAt(admin, academicChain(admin), now().minusSeconds(30 * 60));
        String healthy = openSessionEndingAt(admin, academicChain(admin), now().minusSeconds(20 * 60));
        FaultConfig.ARMED_FOR.set(UUID.fromString(faulty));

        scheduler.closeOverdueSessions();

        // La séance en échec reste OPEN (sa transaction, indépendante, a
        // été annulée) ; l'autre, traitée dans sa propre transaction, est
        // fermée normalement — un incident isolé n'interrompt pas le lot.
        assertThat(getMap("/api/v1/sessions/" + faulty, admin).get("status")).isEqualTo("OPEN");
        assertThat(getMap("/api/v1/sessions/" + healthy, admin).get("status")).isEqualTo("CLOSED");
    }

    // ------------------------------------------------------------------

    private String openSessionEndingAt(String admin, Chain chain, Instant endsAt) {
        String id = (String) created("/api/v1/sessions", createBody(chain, endsAt), admin).get("publicId");
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, admin);
        return id;
    }

    private Map<String, Object> createBody(Chain chain, Instant endsAt) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("teacherPublicId", chain.teacherPublicId());
        body.put("classPublicIds", List.of(chain.classA()));
        body.put("startsAt", endsAt.minusSeconds(3 * 3600).toString());
        body.put("endsAt", endsAt.toString());
        body.put("timeZoneId", "Europe/Paris");
        body.put("reason", "séance exceptionnelle (auto-close)");
        return body;
    }

    private record Chain(String classA, String teacherPublicId) {
    }

    private Chain academicChain(String admin) {
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
        String classA = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C1", "name", "Classe 1"), admin)
                .get("publicId");
        String teacherPublicId = accountWithRoles(RoleCode.TEACHER).publicId();
        return new Chain(classA, teacherPublicId);
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> post(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET, path, null, token);
        assertThat(response.getStatusCode()).as("GET " + path).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private HttpStatus status(HttpMethod method, String path, Map<String, Object> body, String token) {
        return (HttpStatus) exchange(method, path, body, token).getStatusCode();
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
        return restTemplate.exchange(entity, new ParameterizedTypeReference<>() {
        });
    }

    private List<String> auditActions(String resourcePublicId) {
        UUID target = UUID.fromString(resourcePublicId);
        return auditEventRepository.findAll().stream()
                .filter(event -> target.equals(event.getResourcePublicId()))
                .map(AuditEvent::getAction)
                .toList();
    }

    private record Account(String publicId, String email) {
    }

    private Account accountWithRoles(RoleCode... roles) {
        UserAccount account = new UserAccount("cs-auto-" + UUID.randomUUID() + "@esic-connect.test",
                "Cs", "Tester", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return new Account(account.getPublicId().toString(), account.getEmail());
    }

    private String adminToken() {
        return tokenFor(RoleCode.ADMIN);
    }

    private String tokenFor(RoleCode... roles) {
        return tokenFor(accountWithRoles(roles));
    }

    private String tokenFor(Account account) {
        return AuthTestSupport.accessToken(restTemplate, account.email(), PASSWORD);
    }

    private static Instant now() {
        return Instant.now();
    }

    private static String code() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
