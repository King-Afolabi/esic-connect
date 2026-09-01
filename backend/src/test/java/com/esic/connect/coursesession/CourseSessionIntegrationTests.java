package com.esic.connect.coursesession;

import com.esic.connect.audit.internal.AuditEvent;
import com.esic.connect.audit.internal.AuditEventRepository;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parcours de bout en bout du module {@code coursesession} : création
 * d'une séance exceptionnelle, cycle de vie {@code PLANNED → OPEN →
 * CLOSED}, consultation filtrée par périmètre (formateur, responsable
 * pédagogique), audit écrit, absence d'identifiant SQL, transitions
 * interdites, contrôles de cohérence (motif, période, formateur éligible,
 * classe active).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CourseSessionIntegrationTests {

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
     * Injection de faute (G1-C.3, §7B / §8) : un {@link org.springframework.context.event.EventListener}
     * <strong>synchrone</strong> (donc exécuté <em>dans</em> la transaction
     * métier, avant le commit) qui lève, quand il est armé, une
     * {@link org.springframework.dao.OptimisticLockingFailureException} —
     * déjà mappée par le {@code CourseSessionExceptionHandler} de
     * <em>production</em> vers un {@code 409 SESSION_INVALID_STATE}
     * contrôlé (jamais un {@code 500} non maîtrisé). Aucun bean de
     * production n'est modifié.
     */
    @TestConfiguration
    static class RollbackFaultConfig {
        static final java.util.concurrent.atomic.AtomicReference<CourseSessionChangeAction> ARMED =
                new java.util.concurrent.atomic.AtomicReference<>();

        static class FaultListener {
            @org.springframework.context.event.EventListener
            public void onChange(CourseSessionChangeEvent event) {
                CourseSessionChangeAction target = ARMED.get();
                if (target != null && target == event.action()) {
                    ARMED.set(null); // one-shot
                    throw new org.springframework.dao.OptimisticLockingFailureException(
                            "injected fault before commit (test)");
                }
            }
        }

        @Bean
        FaultListener rollbackFaultListener() {
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
    private AuditEventRepository auditEventRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void useJdkClient() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void sessionLifecycleIsAuditedAndHidesInternalIds() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);

        Map<String, Object> session = created("/api/v1/sessions", createBody(teacher.publicId(),
                List.of(chain.classA()), "Cours de rattrapage"), admin);
        String id = (String) session.get("publicId");
        assertThat(session.get("status")).isEqualTo("PLANNED");
        assertThat(session.get("exceptionReason")).isEqualTo("séance exceptionnelle");
        assertThat(((Map<?, ?>) session.get("teacher")).get("publicId")).isEqualTo(teacher.publicId());
        assertThat(session.get("checkpointOpen")).isEqualTo(false);
        assertThat(session).doesNotContainKeys("id", "teacherUserId");
        assertThat((List<?>) session.get("classes")).hasSize(1);
        assertThat(auditActions(id)).contains("SESSION_CREATED");

        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        Map<String, Object> opened = getMap("/api/v1/sessions/" + id, admin);
        assertThat(opened.get("status")).isEqualTo("OPEN");
        assertThat(opened.get("checkpointOpen")).isEqualTo(true);
        assertThat(opened.get("openedAt")).isNotNull();

        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/close", null, admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        Map<String, Object> closed = getMap("/api/v1/sessions/" + id, admin);
        assertThat(closed.get("status")).isEqualTo("CLOSED");
        assertThat(closed.get("checkpointOpen")).isEqualTo(false);
        assertThat(auditActions(id)).contains("SESSION_OPENED", "SESSION_CLOSED");
    }

    @Test
    void forbiddenTransitionsAreRejected() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");

        // Fermer une séance PLANNED -> 409
        assertConflict(HttpMethod.POST, "/api/v1/sessions/" + id + "/close", admin, "SESSION_INVALID_STATE");

        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, admin);
        // Ré-ouvrir -> 409
        assertConflict(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", admin, "SESSION_INVALID_STATE");

        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/close", null, admin);
        // Fermer deux fois / rouvrir après CLOSED -> 409
        assertConflict(HttpMethod.POST, "/api/v1/sessions/" + id + "/close", admin, "SESSION_INVALID_STATE");
        assertConflict(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", admin, "SESSION_INVALID_STATE");
    }

    // ------------------------------------------------------------------
    // G1-C — annulation
    // ------------------------------------------------------------------

    @Test
    void cancellingAPlannedSessionKeepsItReadableButBlocksOperationsAndIsAudited() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), "À annuler"), admin).get("publicId");

        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/cancel",
                Map.of("reason", "Formateur indisponible"), admin)).isEqualTo(HttpStatus.NO_CONTENT);

        // G1-C.3 — la séance CANCELLED reste consultable par son UUID
        // public (GET direct + rechargement) : statut, motif, date
        // d'annulation, formateur principal, points de contrôle terminaux,
        // aucun identifiant SQL.
        Map<String, Object> read = getMap("/api/v1/sessions/" + id, admin);
        assertThat(read.get("status")).isEqualTo("CANCELLED");
        assertThat(read.get("cancellationReason")).isEqualTo("Formateur indisponible");
        assertThat(read.get("cancelledAt")).isNotNull();
        assertThat(read.get("closedAt")).isNull();
        assertThat(((Map<?, ?>) read.get("teacher")).get("publicId")).isEqualTo(teacher.publicId());
        assertThat(read).doesNotContainKeys("id", "teacherUserId", "cancelledById");
        // Un « rafraîchissement » (nouvelle requête) donne le même état persisté.
        assertThat(getMap("/api/v1/sessions/" + id, admin).get("status")).isEqualTo("CANCELLED");
        // Points de contrôle terminaux consultables.
        assertThat(list("/api/v1/sessions/" + id + "/checkpoints", admin))
                .extracting(cp -> cp.get("status")).containsOnly("CANCELLED");

        // Inactive pour toute OPÉRATION métier : open / jeton -> 404 ; absente de la liste par défaut.
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, admin).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + id + "/attendance-token", null, admin)
                .getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.CONFLICT);
        List<?> sessionList = (List<?>) getMap(
                "/api/v1/sessions?classGroup=" + chain.classA() + "&size=100", admin).get("content");
        assertThat(sessionList).noneMatch(s -> id.equals(((Map<?, ?>) s).get("publicId")));

        // Double annulation -> 409 (transitions strictes, cohérent avec open/close).
        ResponseEntity<Map<String, Object>> again = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/cancel", Map.of("reason", "encore"), admin);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().get("code")).isEqualTo("SESSION_INVALID_STATE");
        // Exactement une ligne d'audit SESSION_CANCELLED (after-commit, pas de doublon).
        assertThat(auditActions(id)).contains("SESSION_CREATED");
        assertThat(auditActions(id).stream().filter("SESSION_CANCELLED"::equals).count()).isEqualTo(1L);
    }

    @Test
    void aCancelledSessionGetIsStillGuardedByRoleAndScope() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        Account otherTeacher = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/cancel", Map.of("reason", "x"), admin);

        // 401 sans jeton.
        assertThat(exchange(HttpMethod.GET, "/api/v1/sessions/" + id, null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        // 403 : STUDENT n'a aucun accès à ces routes.
        assertThat(status(HttpMethod.GET, "/api/v1/sessions/" + id, null, tokenFor(RoleCode.STUDENT)))
                .isEqualTo(HttpStatus.FORBIDDEN);
        // 403 : un formateur étranger à la séance annulée ne la lit pas.
        assertThat(status(HttpMethod.GET, "/api/v1/sessions/" + id, null, tokenFor(otherTeacher)))
                .isEqualTo(HttpStatus.FORBIDDEN);
        // Le formateur principal, lui, lit toujours sa séance annulée.
        assertThat(getMap("/api/v1/sessions/" + id, tokenFor(teacher)).get("status")).isEqualTo("CANCELLED");
    }

    @Test
    void cancellingAnOpenSessionIsAllowedAndClosedSessionIsNot() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String open = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");
        status(HttpMethod.POST, "/api/v1/sessions/" + open + "/open", null, admin);
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + open + "/cancel",
                Map.of("reason", "Alerte bâtiment"), admin)).isEqualTo(HttpStatus.NO_CONTENT);

        String closed = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");
        status(HttpMethod.POST, "/api/v1/sessions/" + closed + "/open", null, admin);
        status(HttpMethod.POST, "/api/v1/sessions/" + closed + "/close", null, admin);
        ResponseEntity<Map<String, Object>> onClosed = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + closed + "/cancel", Map.of("reason", "trop tard"), admin);
        assertThat(onClosed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(onClosed.getBody().get("code")).isEqualTo("SESSION_INVALID_STATE");
    }

    @Test
    void cancellationRequiresAReasonAndTheRightRole() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");

        // Motif vide -> 400.
        ResponseEntity<Map<String, Object>> blank = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/cancel", Map.of("reason", "   "), admin);
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // STUDENT -> 403 ; SCHOOL_ADMINISTRATION -> 403 (gestion exclue).
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + id + "/cancel",
                Map.of("reason", "x"), tokenFor(RoleCode.STUDENT)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + id + "/cancel",
                Map.of("reason", "x"), tokenFor(RoleCode.SCHOOL_ADMINISTRATION)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------
    // G1-C.2 — remplacements de formateur
    // ------------------------------------------------------------------

    @Test
    void substitutionKeepsThePrincipalGrantsTheSubstituteAndIsAudited() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account principal = accountWithRoles(RoleCode.TEACHER);
        Account substitute = accountWithRoles(RoleCode.TEACHER);
        Account other = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(principal.publicId(), List.of(chain.classA()), "Cours"), admin).get("publicId");

        Map<String, Object> body = subBody(substitute.publicId(), -3600, 3600);
        Map<String, Object> sub = created("/api/v1/sessions/" + id + "/substitutions", body, admin);
        assertThat(sub.get("status")).isEqualTo("ACTIVE");
        assertThat(((Map<?, ?>) sub.get("substitute")).get("publicId")).isEqualTo(substitute.publicId());
        assertThat(((Map<?, ?>) sub.get("originalTeacher")).get("publicId")).isEqualTo(principal.publicId());
        assertThat(sub).doesNotContainKeys("id", "courseSessionId", "substituteTeacherUserId");

        // Le formateur principal est intact : GET séance montre toujours principal.
        assertThat(((Map<?, ?>) getMap("/api/v1/sessions/" + id, admin).get("teacher")).get("publicId"))
                .isEqualTo(principal.publicId());

        // Le remplaçant ACTIF (période couvrant maintenant) peut ouvrir la séance ;
        // le principal aussi ; un autre formateur non.
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, tokenFor(other)))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, tokenFor(substitute)))
                .isEqualTo(HttpStatus.NO_CONTENT);

        // Liste + audit.
        List<Map<String, Object>> list = list("/api/v1/sessions/" + id + "/substitutions", admin);
        assertThat(list).hasSize(1);
        assertThat(auditActions(id)).contains("SESSION_SUBSTITUTION_ADDED");
    }

    @Test
    void substitutionRejectsIneligibleSameAsPrincipalOverlapAndBadPeriod() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account principal = accountWithRoles(RoleCode.TEACHER);
        Account substitute = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(principal.publicId(), List.of(chain.classA()), null), admin).get("publicId");

        // Compte inconnu -> 409 NOT_ELIGIBLE.
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + id + "/substitutions",
                subBody(UUID.randomUUID().toString(), -3600, 3600), admin).getBody().get("code"))
                .isEqualTo("SESSION_SUBSTITUTE_NOT_ELIGIBLE");
        // Remplaçant = principal -> 409 IS_ORIGINAL.
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + id + "/substitutions",
                subBody(principal.publicId(), -3600, 3600), admin).getBody().get("code"))
                .isEqualTo("SESSION_SUBSTITUTE_IS_ORIGINAL");
        // Période inversée -> 400.
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + id + "/substitutions",
                subBody(substitute.publicId(), 3600, -3600), admin).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Chevauchement : 1re OK, 2e qui chevauche -> 409, 3e non chevauchante -> OK.
        created("/api/v1/sessions/" + id + "/substitutions", subBody(substitute.publicId(), 0, 7200), admin);
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions/" + id + "/substitutions",
                subBody(substitute.publicId(), 3600, 10800), admin).getBody().get("code"))
                .isEqualTo("SESSION_SUBSTITUTION_OVERLAP");
        created("/api/v1/sessions/" + id + "/substitutions", subBody(substitute.publicId(), 7200, 14400), admin);
    }

    @Test
    void endingASubstitutionRevokesTheSubstituteAndIsIdempotencySafe() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account principal = accountWithRoles(RoleCode.TEACHER);
        Account substitute = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(principal.publicId(), List.of(chain.classA()), null), admin).get("publicId");
        String subId = (String) created("/api/v1/sessions/" + id + "/substitutions",
                subBody(substitute.publicId(), -3600, 3600), admin).get("publicId");

        // Le remplaçant peut ouvrir tant que la substitution est ACTIVE.
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, tokenFor(substitute)))
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(status(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/substitutions/" + subId + "/end", null, admin))
                .isEqualTo(HttpStatus.NO_CONTENT);
        // Double fin -> 409.
        assertThat(exchange(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/substitutions/" + subId + "/end", null, admin).getBody().get("code"))
                .isEqualTo("SESSION_SUBSTITUTION_ALREADY_ENDED");
        // La substitution terminée retire le droit : le remplaçant ne peut plus fermer.
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/close", null, tokenFor(substitute)))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(auditActions(id)).contains("SESSION_SUBSTITUTION_ENDED");
    }

    @Test
    void anExpiredSubstitutionGrantsNoRight() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account principal = accountWithRoles(RoleCode.TEACHER);
        Account substitute = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(principal.publicId(), List.of(chain.classA()), null), admin).get("publicId");
        // Période entièrement dans le passé.
        created("/api/v1/sessions/" + id + "/substitutions", subBody(substitute.publicId(), -7200, -3600), admin);
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, tokenFor(substitute)))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void substitutionRolesAreEnforcedAndCancelledSessionIsNotSubstitutable() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account principal = accountWithRoles(RoleCode.TEACHER);
        Account substitute = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(principal.publicId(), List.of(chain.classA()), null), admin).get("publicId");

        // TEACHER (principal) -> 403 (CREATE_ROLES exclut TEACHER : « ne valide pas lui-même »).
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/substitutions",
                subBody(substitute.publicId(), -3600, 3600), tokenFor(principal)))
                .isEqualTo(HttpStatus.FORBIDDEN);
        // STUDENT -> 403.
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/substitutions",
                subBody(substitute.publicId(), -3600, 3600), tokenFor(RoleCode.STUDENT)))
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Séance annulée -> plus substituable (404, garde « opérationnelle »).
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/cancel", Map.of("reason", "x"), admin);
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/substitutions",
                subBody(substitute.publicId(), -3600, 3600), admin))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void concurrentOverlappingSubstitutionCreatesResolveToOneWithoutServerError() throws Exception {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account principal = accountWithRoles(RoleCode.TEACHER);
        Account substitute = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(principal.publicId(), List.of(chain.classA()), null), admin).get("publicId");

        Callable<HttpStatus> call = () -> (HttpStatus) exchange(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/substitutions", subBody(substitute.publicId(), 0, 7200), admin)
                .getStatusCode();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<HttpStatus> statuses;
        try {
            List<Future<HttpStatus>> futures = pool.invokeAll(List.of(call, call));
            statuses = List.of(join(futures.get(0)), join(futures.get(1)));
        } finally {
            pool.shutdownNow();
        }
        assertThat(statuses).noneMatch(HttpStatus::is5xxServerError);
        // Au plus une substitution ACTIVE : exactement une création réussie.
        long active = list("/api/v1/sessions/" + id + "/substitutions", admin).stream()
                .filter(s -> "ACTIVE".equals(s.get("status"))).count();
        assertThat(active).isEqualTo(1);
    }

    private Map<String, Object> subBody(String substitutePublicId, long fromOffsetSec, long untilOffsetSec) {
        Instant base = Instant.now();
        return subBodyAbs(substitutePublicId, base.plusSeconds(fromOffsetSec), base.plusSeconds(untilOffsetSec));
    }

    /** Corps de remplacement avec une période absolue (bornes maîtrisées, G1-C.3). */
    private Map<String, Object> subBodyAbs(String substitutePublicId, Instant from, Instant until) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("substituteTeacherPublicId", substitutePublicId);
        body.put("reason", "Formateur principal indisponible");
        body.put("validFrom", from.toString());
        body.put("validUntil", until.toString());
        return body;
    }

    // ------------------------------------------------------------------
    // G1-C.3 — visibilité du remplaçant actif, période vs séance, audit
    // ------------------------------------------------------------------

    @Test
    void anActiveSubstituteSeesTheSessionInGetAndListAndCanManageItButNotOthers() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account principal = accountWithRoles(RoleCode.TEACHER);
        Account substitute = accountWithRoles(RoleCode.TEACHER);
        String covered = (String) created("/api/v1/sessions",
                createBody(principal.publicId(), List.of(chain.classA()), "Couverte"), admin).get("publicId");
        // Une autre séance du même formateur principal, SANS remplacement.
        String uncovered = (String) created("/api/v1/sessions",
                createBody(principal.publicId(), List.of(chain.classA()), "Non couverte"), admin).get("publicId");

        // Remplacement ACTIF couvrant maintenant.
        created("/api/v1/sessions/" + covered + "/substitutions",
                subBody(substitute.publicId(), -3600, 3600), admin);

        String subToken = tokenFor(substitute);
        // GET : le remplaçant lit la séance couverte, pas l'autre.
        assertThat(status(HttpMethod.GET, "/api/v1/sessions/" + covered, null, subToken))
                .isEqualTo(HttpStatus.OK);
        assertThat(status(HttpMethod.GET, "/api/v1/sessions/" + uncovered, null, subToken))
                .isEqualTo(HttpStatus.FORBIDDEN);

        // LISTE : la séance couverte y figure, l'autre non (sans requête par séance).
        List<?> content = (List<?>) getMap("/api/v1/sessions?size=100", subToken).get("content");
        assertThat(content).anyMatch(s -> covered.equals(((Map<?, ?>) s).get("publicId")));
        assertThat(content).noneMatch(s -> uncovered.equals(((Map<?, ?>) s).get("publicId")));

        // GESTION : le remplaçant actif ouvre / gère / ferme la séance couverte.
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + covered + "/open", null, subToken))
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rawStatus(HttpMethod.GET, "/api/v1/sessions/" + covered + "/checkpoints", null, subToken))
                .isEqualTo(HttpStatus.OK);
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + covered + "/close", null, subToken))
                .isEqualTo(HttpStatus.NO_CONTENT);
        // Il ne gère pas l'autre séance.
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + uncovered + "/open", null, subToken))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void futureAndEndedSubstitutesGetNoRightAndSchoolAdministrationGetsNoExtraRight() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account principal = accountWithRoles(RoleCode.TEACHER);
        Account futureSub = accountWithRoles(RoleCode.TEACHER);
        Account endedSub = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(principal.publicId(), List.of(chain.classA()), null), admin).get("publicId");

        // Remplacement futur (ne couvre pas encore maintenant) — bien séparé de l'autre.
        created("/api/v1/sessions/" + id + "/substitutions",
                subBody(futureSub.publicId(), 3 * 3600, 5 * 3600), admin);
        // Remplacement bientôt terminé (couvre maintenant, sans chevaucher le futur).
        String endedId = (String) created("/api/v1/sessions/" + id + "/substitutions",
                subBody(endedSub.publicId(), -3600, 1800), admin).get("publicId");
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/substitutions/" + endedId + "/end", null, admin);

        // Remplaçant futur : ni liste, ni GET, ni gestion.
        String futureToken = tokenFor(futureSub);
        assertThat(((List<?>) getMap("/api/v1/sessions?size=100", futureToken).get("content"))).isEmpty();
        assertThat(status(HttpMethod.GET, "/api/v1/sessions/" + id, null, futureToken))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, futureToken))
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Remplaçant terminé : aucun droit.
        assertThat(status(HttpMethod.GET, "/api/v1/sessions/" + id, null, tokenFor(endedSub)))
                .isEqualTo(HttpStatus.FORBIDDEN);

        // SCHOOL_ADMINISTRATION : lecture (rôle global) mais jamais de gestion — inchangé.
        String schoolAdmin = tokenFor(RoleCode.SCHOOL_ADMINISTRATION);
        assertThat(status(HttpMethod.GET, "/api/v1/sessions/" + id, null, schoolAdmin))
                .isEqualTo(HttpStatus.OK);
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, schoolAdmin))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void substitutionPeriodMustReallyOverlapTheSessionWindow() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account principal = accountWithRoles(RoleCode.TEACHER);
        Account substitute = accountWithRoles(RoleCode.TEACHER);
        Map<String, Object> session = created("/api/v1/sessions",
                createBody(principal.publicId(), List.of(chain.classA()), null), admin);
        String id = (String) session.get("publicId");
        String path = "/api/v1/sessions/" + id + "/substitutions";
        // Bornes RÉELLES de la séance (jamais « maintenant »).
        Instant start = Instant.parse((String) session.get("startsAt"));
        Instant end = Instant.parse((String) session.get("endsAt"));
        java.time.Duration margin = java.time.Duration.ofMinutes(60);

        // Période entièrement AVANT la séance -> 422 OUTSIDE_SESSION.
        assertThat(exchange(HttpMethod.POST, path,
                subBodyAbs(substitute.publicId(), start.minus(margin).minusSeconds(7200),
                        start.minus(margin).minusSeconds(3600)), admin).getBody().get("code"))
                .isEqualTo("SESSION_SUBSTITUTION_OUTSIDE_SESSION");
        // Période entièrement APRÈS la séance -> 422 OUTSIDE_SESSION.
        assertThat(exchange(HttpMethod.POST, path,
                subBodyAbs(substitute.publicId(), end.plus(margin).plusSeconds(3600),
                        end.plus(margin).plusSeconds(7200)), admin).getBody().get("code"))
                .isEqualTo("SESSION_SUBSTITUTION_OUTSIDE_SESSION");
        // Débordement de la marge de 60 min avant le début -> 422.
        assertThat(exchange(HttpMethod.POST, path,
                subBodyAbs(substitute.publicId(), start.minus(margin).minusSeconds(1), start), admin)
                .getBody().get("code"))
                .isEqualTo("SESSION_SUBSTITUTION_OUTSIDE_SESSION");
        // Débordement de la marge de 60 min après la fin -> 422.
        assertThat(exchange(HttpMethod.POST, path,
                subBodyAbs(substitute.publicId(), end, end.plus(margin).plusSeconds(1)), admin)
                .getBody().get("code"))
                .isEqualTo("SESSION_SUBSTITUTION_OUTSIDE_SESSION");
        // validUntil == validFrom -> 400 PERIOD_INVALID (malformée, contrôlée avant l'overlap).
        assertThat(exchange(HttpMethod.POST, path,
                subBodyAbs(substitute.publicId(), start, start), admin).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        // Bornes exactes : start - 60 min pile .. end + 60 min pile -> accepté (chevauche réellement).
        assertThat(status(HttpMethod.POST, path,
                subBodyAbs(substitute.publicId(), start.minus(margin), end.plus(margin)), admin))
                .isEqualTo(HttpStatus.CREATED);
        // Une seconde période valide qui chevauche l'ACTIVE existante
        // (invariant « au plus une ACTIVE applicable ») -> 409 contrôlé, jamais 5xx.
        Account other = accountWithRoles(RoleCode.TEACHER);
        ResponseEntity<Map<String, Object>> overlap = exchange(HttpMethod.POST, path,
                subBodyAbs(other.publicId(), start.plusSeconds(600), start.plusSeconds(4200)), admin);
        assertThat(overlap.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(overlap.getBody().get("code")).isEqualTo("SESSION_SUBSTITUTION_OVERLAP");
    }

    @Test
    void substitutionAuditWritesExactlyOneRowPerSuccessfulChange() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account principal = accountWithRoles(RoleCode.TEACHER);
        Account substitute = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(principal.publicId(), List.of(chain.classA()), null), admin).get("publicId");

        String subId = (String) created("/api/v1/sessions/" + id + "/substitutions",
                subBody(substitute.publicId(), -3600, 3600), admin).get("publicId");
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/substitutions/" + subId + "/end", null, admin);

        assertThat(auditActions(id).stream().filter("SESSION_SUBSTITUTION_ADDED"::equals).count()).isEqualTo(1L);
        assertThat(auditActions(id).stream().filter("SESSION_SUBSTITUTION_ENDED"::equals).count()).isEqualTo(1L);
    }

    @Test
    void aRollbackDuringCancelLeavesNoStateChangeNoAuditAndNoAfterCommitEffect() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, admin);
        // Un jeton d'émargement existe avant la tentative d'annulation.
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/attendance-token", null, admin))
                .isIn(HttpStatus.OK, HttpStatus.CREATED);
        long cancelledBefore = auditActions(id).stream().filter("SESSION_CANCELLED"::equals).count();

        try {
            RollbackFaultConfig.ARMED.set(CourseSessionChangeAction.CANCELLED);
            ResponseEntity<Map<String, Object>> failed = exchange(HttpMethod.POST,
                    "/api/v1/sessions/" + id + "/cancel", Map.of("reason", "sera annulé par un rollback"), admin);
            // Erreur CONTRÔLÉE (mappée par le handler de production), jamais un 5xx.
            assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(failed.getStatusCode().is5xxServerError()).isFalse();
            assertThat(failed.getBody().get("code")).isEqualTo("SESSION_INVALID_STATE");
        } finally {
            RollbackFaultConfig.ARMED.set(null);
        }

        // 1. Transaction métier rollbackée : la séance est toujours OPEN.
        Map<String, Object> read = getMap("/api/v1/sessions/" + id, admin);
        assertThat(read.get("status")).isEqualTo("OPEN");
        assertThat(read.get("cancellationReason")).isNull();
        assertThat(read.get("cancelledAt")).isNull();
        // 2. Le point de contrôle START n'a pas été annulé (rollback).
        assertThat(list("/api/v1/sessions/" + id + "/checkpoints", admin))
                .noneMatch(cp -> "CANCELLED".equals(cp.get("status")));
        // 3. Aucune ligne d'audit SESSION_CANCELLED : le listener AFTER_COMMIT
        //    n'a jamais été atteint.
        assertThat(auditActions(id).stream().filter("SESSION_CANCELLED"::equals).count())
                .isEqualTo(cancelledBefore);
        // 4. Effet Redis après commit non exécuté : la séance reste
        //    pleinement utilisable (émission d'un nouveau jeton OK), et
        //    l'annulation réussit maintenant que la faute est désarmée.
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/attendance-token", null, admin))
                .isIn(HttpStatus.OK, HttpStatus.CREATED);
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/cancel",
                Map.of("reason", "annulation réelle"), admin)).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getMap("/api/v1/sessions/" + id, admin).get("status")).isEqualTo("CANCELLED");
        assertThat(auditActions(id).stream().filter("SESSION_CANCELLED"::equals).count()).isEqualTo(1L);
    }

    @Test
    void concurrentOpenAndCancelResolveToOneTerminalStateWithoutServerError() throws Exception {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");

        Callable<HttpStatus> openCall = () -> (HttpStatus) exchange(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/open", null, admin).getStatusCode();
        Callable<HttpStatus> cancelCall = () -> (HttpStatus) exchange(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/cancel", Map.of("reason", "course"), admin).getStatusCode();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<HttpStatus> statuses;
        try {
            List<Future<HttpStatus>> futures = pool.invokeAll(List.of(openCall, cancelCall));
            statuses = List.of(join(futures.get(0)), join(futures.get(1)));
        } finally {
            pool.shutdownNow();
        }
        // Verrou optimiste : un gagnant (204), un perdant (409 contrôlé), jamais 5xx.
        assertThat(statuses).noneMatch(HttpStatus::is5xxServerError);
        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.CONFLICT);
        // État final cohérent : soit OPEN (open a gagné), soit annulée (GET -> 404).
        HttpStatus getStatus = (HttpStatus) exchange(HttpMethod.GET, "/api/v1/sessions/" + id, null, admin)
                .getStatusCode();
        assertThat(getStatus).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);
    }

    @Test
    void creationRejectsMissingReasonInvalidPeriodAndNonTeacher() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);

        // Motif obligatoire (validation Jakarta @NotBlank -> 400 VALIDATION_ERROR)
        Map<String, Object> noReason = new java.util.HashMap<>(
                createBody(teacher.publicId(), List.of(chain.classA()), null));
        noReason.remove("reason");
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions", noReason, admin).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Période invalide (fin <= début)
        Map<String, Object> badPeriod = new java.util.HashMap<>(
                createBody(teacher.publicId(), List.of(chain.classA()), null));
        badPeriod.put("startsAt", "2026-09-10T12:00:00Z");
        badPeriod.put("endsAt", "2026-09-10T08:00:00Z");
        ResponseEntity<Map<String, Object>> period = exchange(HttpMethod.POST, "/api/v1/sessions", badPeriod, admin);
        assertThat(period.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(period.getBody().get("code")).isEqualTo("SESSION_INVALID_PERIOD");

        // Aucune classe -> 400
        Map<String, Object> noClass = new java.util.HashMap<>(
                createBody(teacher.publicId(), List.of(), null));
        assertThat(exchange(HttpMethod.POST, "/api/v1/sessions", noClass, admin).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Compte non formateur -> 409 SESSION_TEACHER_NOT_ELIGIBLE
        Account notTeacher = accountWithRoles(RoleCode.STUDENT);
        ResponseEntity<Map<String, Object>> eligible = exchange(HttpMethod.POST, "/api/v1/sessions",
                createBody(notTeacher.publicId(), List.of(chain.classA()), null), admin);
        assertThat(eligible.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(eligible.getBody().get("code")).isEqualTo("SESSION_TEACHER_NOT_ELIGIBLE");

        // Formateur inconnu -> 400 SESSION_TEACHER_NOT_FOUND
        ResponseEntity<Map<String, Object>> unknown = exchange(HttpMethod.POST, "/api/v1/sessions",
                createBody(UUID.randomUUID().toString(), List.of(chain.classA()), null), admin);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unknown.getBody().get("code")).isEqualTo("SESSION_TEACHER_NOT_FOUND");
    }

    @Test
    void teacherSeesOnlyTheirOwnSessions() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacherA = accountWithRoles(RoleCode.TEACHER);
        Account teacherB = accountWithRoles(RoleCode.TEACHER);
        String sessionA = (String) created("/api/v1/sessions",
                createBody(teacherA.publicId(), List.of(chain.classA()), null), admin).get("publicId");
        created("/api/v1/sessions", createBody(teacherB.publicId(), List.of(chain.classA()), null), admin);

        String tokenA = tokenFor(teacherA);
        Map<String, Object> list = getMap("/api/v1/sessions", tokenA);
        List<?> content = (List<?>) list.get("content");
        assertThat(content).hasSize(1);
        assertThat(((Map<?, ?>) content.get(0)).get("publicId")).isEqualTo(sessionA);

        // La séance d'un autre formateur -> 403
        Map<String, Object> other = getMap("/api/v1/sessions", admin);
        String someoneElse = (String) ((Map<?, ?>) ((List<?>) other.get("content")).stream()
                .map(item -> (Map<?, ?>) item)
                .filter(item -> !sessionA.equals(item.get("publicId")))
                .findFirst().orElseThrow()).get("publicId");
        assertThat(status(HttpMethod.GET, "/api/v1/sessions/" + someoneElse, null, tokenA))
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Un formateur ne peut pas créer de séance -> 403
        assertThat(status(HttpMethod.POST, "/api/v1/sessions",
                createBody(teacherA.publicId(), List.of(chain.classA()), null), tokenA))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void eligibleTeachersEndpointListsActiveTeachersOnly() {
        String admin = adminToken();
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        // Compte formateur suspendu : exclu.
        Account suspended = accountWithRoles(RoleCode.TEACHER);
        UserAccount suspendedAccount = userAccountRepository.findByEmail(suspended.email()).orElseThrow();
        suspendedAccount.suspend("test", null, Instant.now());
        userAccountRepository.saveAndFlush(suspendedAccount);

        List<Map<String, Object>> teachers = restTemplate.exchange(
                RequestEntity.get(URI.create("/api/v1/sessions/teachers"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).build(),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
        List<Object> ids = teachers.stream().map(t -> t.get("publicId")).map(Object.class::cast).toList();
        assertThat(ids).contains(teacher.publicId());
        assertThat(ids).doesNotContain(suspended.publicId());
        assertThat(teachers).allSatisfy(t -> assertThat(t).doesNotContainKeys("email", "id"));
    }

    @Test
    void studentCannotListSessions() {
        assertThat(status(HttpMethod.GET, "/api/v1/sessions", null, tokenFor(RoleCode.STUDENT)))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------
    // Points de contrôle (V10)
    // ------------------------------------------------------------------

    @Test
    void checkpointLifecycleIsAuditedAndListed() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, admin);

        // À l'ouverture, le point de contrôle START existe et est OUVERT.
        List<Map<String, Object>> checkpoints = list("/api/v1/sessions/" + id + "/checkpoints", admin);
        assertThat(checkpoints).hasSize(1);
        assertThat(checkpoints.get(0).get("type")).isEqualTo("START");
        assertThat(checkpoints.get(0).get("status")).isEqualTo("OPEN");
        assertThat(checkpoints.get(0)).doesNotContainKey("id");

        // Ajout d'un point de contrôle CUSTOM.
        Map<String, Object> custom = created("/api/v1/sessions/" + id + "/checkpoints",
                Map.of("label", "Retour de pause", "type", "CUSTOM"), admin);
        String customId = (String) custom.get("publicId");
        assertThat(custom.get("status")).isEqualTo("PLANNED");
        assertThat(((Number) custom.get("displayOrder")).intValue()).isEqualTo(1);

        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/checkpoints/" + customId + "/open", null, admin);
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/checkpoints/" + customId + "/close", null, admin);

        // Un troisième point de contrôle, annulé.
        String cancelledId = (String) created("/api/v1/sessions/" + id + "/checkpoints",
                Map.of("label", "Point en trop", "type", "CUSTOM", "required", false), admin).get("publicId");
        assertThat(status(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/checkpoints/" + cancelledId + "/cancel",
                Map.of("reason", "erreur de saisie"), admin)).isEqualTo(HttpStatus.NO_CONTENT);

        List<Map<String, Object>> after = list("/api/v1/sessions/" + id + "/checkpoints", admin);
        assertThat(after).extracting(cp -> cp.get("status"))
                .containsExactly("OPEN", "CLOSED", "CANCELLED");

        assertThat(auditActions(customId)).contains("CHECKPOINT_CREATED", "CHECKPOINT_OPENED", "CHECKPOINT_CLOSED");
        assertThat(auditActions(cancelledId)).contains("CHECKPOINT_CREATED", "CHECKPOINT_CANCELLED");

        // La fiche séance porte la liste des points de contrôle.
        Map<String, Object> session = getMap("/api/v1/sessions/" + id, admin);
        assertThat((List<?>) session.get("checkpoints")).hasSize(3);

        // Fermeture de la séance -> le point de contrôle START ouvert est fermé.
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/close", null, admin);
        assertThat(list("/api/v1/sessions/" + id + "/checkpoints", admin))
                .extracting(cp -> cp.get("status"))
                .containsExactly("CLOSED", "CLOSED", "CANCELLED");
    }

    @Test
    void checkpointRulesAndForbiddenTransitionsAreRejected() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");

        // Séance PLANNED : on peut créer un point de contrôle, mais pas l'ouvrir.
        String customId = (String) created("/api/v1/sessions/" + id + "/checkpoints",
                Map.of("label", "Fin", "type", "END"), admin).get("publicId");
        assertConflict(HttpMethod.POST, "/api/v1/sessions/" + id + "/checkpoints/" + customId + "/open",
                admin, "ATT_CHECKPOINT_INVALID_STATE");

        // Deux END actifs -> 400 ATT_CHECKPOINT_INVALID_TYPE.
        ResponseEntity<Map<String, Object>> dupEnd = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/checkpoints", Map.of("label", "Fin bis", "type", "END"), admin);
        assertThat(dupEnd.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(dupEnd.getBody().get("code")).isEqualTo("ATT_CHECKPOINT_INVALID_TYPE");

        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, admin);
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/checkpoints/" + customId + "/open", null, admin);
        // Ouvrir deux fois -> 409.
        assertConflict(HttpMethod.POST, "/api/v1/sessions/" + id + "/checkpoints/" + customId + "/open",
                admin, "ATT_CHECKPOINT_INVALID_STATE");
        // Annuler sans motif -> 400 (validation @NotBlank).
        assertThat(exchange(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/checkpoints/" + customId + "/cancel", Map.of("reason", "  "), admin)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Fermer un point de contrôle inconnu -> 404 ATT_CHECKPOINT_NOT_FOUND.
        ResponseEntity<Map<String, Object>> unknown = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/checkpoints/" + UUID.randomUUID() + "/close", null, admin);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody().get("code")).isEqualTo("ATT_CHECKPOINT_NOT_FOUND");

        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/close", null, admin);
        // Séance CLOSED : plus de création de point de contrôle -> 409.
        ResponseEntity<Map<String, Object>> onClosed = exchange(HttpMethod.POST,
                "/api/v1/sessions/" + id + "/checkpoints", Map.of("label", "x", "type", "CUSTOM"), admin);
        assertThat(onClosed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(onClosed.getBody().get("code")).isEqualTo("ATT_CHECKPOINT_INVALID_STATE");
    }

    @Test
    void checkpointManagementRolesAreEnforced() {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");

        String schoolAdmin = tokenFor(RoleCode.SCHOOL_ADMINISTRATION);
        // Lecture autorisée...
        assertThat(rawStatus(HttpMethod.GET, "/api/v1/sessions/" + id + "/checkpoints", null, schoolAdmin))
                .isEqualTo(HttpStatus.OK);
        // ...mais pas la gestion.
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/checkpoints",
                Map.of("label", "x", "type", "CUSTOM"), schoolAdmin)).isEqualTo(HttpStatus.FORBIDDEN);

        // STUDENT : aucun accès.
        assertThat(rawStatus(HttpMethod.GET, "/api/v1/sessions/" + id + "/checkpoints", null,
                tokenFor(RoleCode.STUDENT))).isEqualTo(HttpStatus.FORBIDDEN);

        // Un formateur non affecté à la séance -> 403.
        Account otherTeacher = accountWithRoles(RoleCode.TEACHER);
        assertThat(status(HttpMethod.POST, "/api/v1/sessions/" + id + "/checkpoints",
                Map.of("label", "x", "type", "CUSTOM"), tokenFor(otherTeacher)))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void concurrentCheckpointOpenAndCloseResolveToAllowedStateWithoutServerError() throws Exception {
        String admin = adminToken();
        Chain chain = academicChain(admin);
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String id = (String) created("/api/v1/sessions",
                createBody(teacher.publicId(), List.of(chain.classA()), null), admin).get("publicId");
        status(HttpMethod.POST, "/api/v1/sessions/" + id + "/open", null, admin);
        // Point de contrôle CUSTOM PLANNED : ouverture et fermeture lancées
        // en parallèle. Une seule transition est valide depuis l'état
        // courant ; l'autre est un conflit contrôlé, jamais un 500.
        String cp = (String) created("/api/v1/sessions/" + id + "/checkpoints",
                Map.of("label", "Retour de pause", "type", "CUSTOM"), admin).get("publicId");

        String base = "/api/v1/sessions/" + id + "/checkpoints/" + cp;
        Callable<HttpStatus> open = () -> (HttpStatus) exchange(HttpMethod.POST, base + "/open", null, admin)
                .getStatusCode();
        Callable<HttpStatus> close = () -> (HttpStatus) exchange(HttpMethod.POST, base + "/close", null, admin)
                .getStatusCode();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<HttpStatus> statuses;
        try {
            List<Future<HttpStatus>> futures = pool.invokeAll(List.of(open, close));
            statuses = List.of(join(futures.get(0)), join(futures.get(1)));
        } finally {
            pool.shutdownNow();
        }
        assertThat(statuses).noneMatch(HttpStatus::is5xxServerError);
        assertThat(statuses).filteredOn(HttpStatus.CONFLICT::equals).hasSize(1);
        assertThat(statuses).anyMatch(HttpStatus.NO_CONTENT::equals);

        List<Map<String, Object>> after = list("/api/v1/sessions/" + id + "/checkpoints", admin);
        Object cpStatus = after.stream().filter(c -> cp.equals(c.get("publicId"))).findFirst()
                .orElseThrow().get("status");
        assertThat(cpStatus).isIn("OPEN", "CLOSED");
    }

    private static HttpStatus join(Future<HttpStatus> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Map<String, Object>> list(String path, String token) {
        return restTemplate.exchange(
                RequestEntity.get(URI.create(path)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
    }

    /** Statut d'une requête sans désérialiser le corps (utile pour les endpoints renvoyant un tableau). */
    private HttpStatus rawStatus(HttpMethod method, String path, Map<String, Object> body, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.method(method, URI.create(path));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        RequestEntity<?> entity = body == null
                ? builder.build()
                : builder.contentType(MediaType.APPLICATION_JSON).body(body);
        return (HttpStatus) restTemplate.exchange(entity, String.class).getStatusCode();
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    /**
     * Fenêtre de séance <strong>relative à « maintenant »</strong> (large :
     * {@code now - 3h} → {@code now + 6h}). Depuis G1-C.3, la période d'un
     * remplacement doit réellement chevaucher la séance : les décalages de
     * {@link #subBody} (exprimés en secondes autour de {@code Instant.now()})
     * tombent alors dans le créneau, ce qui garde les scénarios de
     * remplacement (actif / expiré / chevauchement) sémantiquement
     * inchangés sans horloge figée.
     */
    private Map<String, Object> createBody(String teacherPublicId, List<String> classPublicIds, String title) {
        Instant now = Instant.now();
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("teacherPublicId", teacherPublicId);
        body.put("classPublicIds", classPublicIds);
        body.put("startsAt", now.minusSeconds(3 * 3600).toString());
        body.put("endsAt", now.plusSeconds(6 * 3600).toString());
        body.put("timeZoneId", "Europe/Paris");
        body.put("reason", "séance exceptionnelle");
        if (title != null) {
            body.put("title", title);
        }
        return body;
    }

    private record Chain(String classA, String classB) {
    }

    private Chain academicChain(String admin) {
        String site = (String) created("/api/v1/sites", Map.of("code", "SITE-" + UUID.randomUUID(),
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
        String classB = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C2", "name", "Classe 2"), admin)
                .get("publicId");
        return new Chain(classA, classB);
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST " + path + " -> " + response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET, path, null, token);
        assertThat(response.getStatusCode()).as("GET " + path).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private void assertConflict(HttpMethod method, String path, String token, String expectedCode) {
        ResponseEntity<Map<String, Object>> response = exchange(method, path, null, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo(expectedCode);
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
        UserAccount account = new UserAccount("cs-" + UUID.randomUUID() + "@esic-connect.test",
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
        Map<String, Object> body = restTemplate.exchange(
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
