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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Réclamations (EF-CLAIM-001 à 004 ; docs/02 §20).
 *
 * <p>Ce qui est vérifié en priorité n'est pas « on peut écrire un
 * message », mais l'isolement : personne ne voit la réclamation d'un
 * autre apprenant (`AC-017`), et l'historique survit au transfert comme à
 * la réouverture (RG-088).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ClaimIntegrationTests {

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

    // ------------------------------------------------------------------
    // EF-CLAIM-001 / 002 — dépôt et conversation
    // ------------------------------------------------------------------

    @Test
    void laDescriptionDuDepotDevientLePremierMessageDuFil() {
        Account student = enrolledStudent();
        String token = tokenFor(student);

        Map<String, Object> claim = created("/api/v1/claims", Map.of(
                "category", "ATTENDANCE", "subject", "Absence du 12 octobre",
                "description", "J'étais présent, mon émargement n'a pas fonctionné.",
                "audience", "PEDAGOGICAL_MANAGER"), token);

        assertThat(claim.get("status")).isEqualTo("OPEN");
        assertThat(claim.get("audience")).isEqualTo("PEDAGOGICAL_MANAGER");

        Map<String, Object> thread = getMap("/api/v1/claims/" + claim.get("publicId"), token);
        List<Map<String, Object>> messages = listOf(thread, "messages");
        // Le cahier veut une conversation, pas un formulaire suivi d'un
        // fil vide (docs/02 §20.2).
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).get("body")).asString().contains("mon émargement");
        assertThat(messages.get(0).get("authorRole")).isEqualTo("STUDENT");
    }

    @Test
    void laPremiereReponseDUnIntervenantFaitPasserEnCours() {
        ClassContext context = classContext();
        Account student = context.student();
        Account manager = context.manager();
        String claimId = claimId(openClaim(tokenFor(student), "PEDAGOGICAL_MANAGER"));

        Map<String, Object> thread = created("/api/v1/claims/" + claimId + "/messages",
                Map.of("body", "Je vérifie auprès du formateur."), tokenFor(manager));

        assertThat(castMap(thread.get("claim")).get("status")).isEqualTo("IN_PROGRESS");
        assertThat(listOf(thread, "messages")).hasSize(2);
    }

    @Test
    void uneReponseDeLAuteurNeChangePasLeStatut() {
        Account student = enrolledStudent();
        String token = tokenFor(student);
        String claimId = claimId(openClaim(token, "PEDAGOGICAL_MANAGER"));

        Map<String, Object> thread = created("/api/v1/claims/" + claimId + "/messages",
                Map.of("body", "Je complète ma demande."), token);

        // L'apprenant expose, il ne décide de rien.
        assertThat(castMap(thread.get("claim")).get("status")).isEqualTo("OPEN");
    }

    // ------------------------------------------------------------------
    // Isolement (AC-017)
    // ------------------------------------------------------------------

    @Test
    void unApprenantNeVoitJamaisLaReclamationDUnAutre() {
        Account first = enrolledStudent();
        Account second = enrolledStudent();
        String claimId = claimId(openClaim(tokenFor(first), "PEDAGOGICAL_MANAGER"));

        ResponseEntity<Map<String, Object>> denied = exchange(HttpMethod.GET,
                "/api/v1/claims/" + claimId, null, tokenFor(second));

        // 404 et non 403 : l'existence même d'une réclamation d'autrui est
        // une information à protéger (docs/02 §18.2).
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unIntervenantDUnAutreGuichetNeVoitPasLaReclamation() {
        Account student = enrolledStudent();
        Account teacher = accountWithRoles(RoleCode.TEACHER);
        String claimId = claimId(openClaim(tokenFor(student), "SCHOOL_ADMINISTRATION"));

        assertThat(exchange(HttpMethod.GET, "/api/v1/claims/" + claimId, null, tokenFor(teacher))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void laListeDUnApprenantNeContientQueLesSiennes() {
        Account first = enrolledStudent();
        Account second = enrolledStudent();
        openClaim(tokenFor(first), "PEDAGOGICAL_MANAGER");
        Map<String, Object> mine = openClaim(tokenFor(second), "PEDAGOGICAL_MANAGER");

        Map<String, Object> page = getMap("/api/v1/claims?size=50", tokenFor(second));
        List<Map<String, Object>> content = listOf(page, "content");

        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("publicId")).isEqualTo(mine.get("publicId"));
    }

    // ------------------------------------------------------------------
    // EF-CLAIM-003 — transfert
    // ------------------------------------------------------------------

    @Test
    void leTransfertChangeDeGuichetEtResteDansLHistorique() {
        ClassContext context = classContext();
        Account student = context.student();
        Account manager = context.manager();
        Account administration = accountWithRoles(RoleCode.SCHOOL_ADMINISTRATION);
        String claimId = claimId(openClaim(tokenFor(student), "PEDAGOGICAL_MANAGER"));

        Map<String, Object> transferred = post("/api/v1/claims/" + claimId + "/transfer",
                Map.of("audience", "SCHOOL_ADMINISTRATION",
                        "motive", "relève de l'administration scolaire"), tokenFor(manager));

        assertThat(transferred.get("audience")).isEqualTo("SCHOOL_ADMINISTRATION");
        assertThat(transferred.get("status")).isEqualTo("TRANSFERRED");

        // L'historique conserve la décision, motif compris (RG-088).
        Map<String, Object> thread = getMap("/api/v1/claims/" + claimId, tokenFor(administration));
        List<Map<String, Object>> events = listOf(thread, "events");
        assertThat(events.stream().map(event -> event.get("eventType")))
                .containsExactly("CREATED", "TRANSFERRED");
        assertThat(events.get(1).get("motive")).isEqualTo("relève de l'administration scolaire");
        assertThat(events.get(1).get("fromAudience")).isEqualTo("PEDAGOGICAL_MANAGER");
    }

    @Test
    void lAuteurNeTransferePasSaPropreReclamation() {
        Account student = enrolledStudent();
        String token = tokenFor(student);
        String claimId = claimId(openClaim(token, "PEDAGOGICAL_MANAGER"));

        // Il choisit son guichet au dépôt ; il ne se promène pas ensuite
        // d'un service à l'autre.
        assertThat(exchange(HttpMethod.POST, "/api/v1/claims/" + claimId + "/transfer",
                Map.of("audience", "SCHOOL_ADMINISTRATION", "motive", "je préfère"), token)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unTransfertVersLeMemeGuichetEstRefuse() {
        ClassContext context = classContext();
        Account student = context.student();
        Account manager = context.manager();
        String claimId = claimId(openClaim(tokenFor(student), "PEDAGOGICAL_MANAGER"));

        assertThat(exchange(HttpMethod.POST, "/api/v1/claims/" + claimId + "/transfer",
                Map.of("audience", "PEDAGOGICAL_MANAGER", "motive", "sans effet"), tokenFor(manager))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------------
    // EF-CLAIM-004 — décision et réouverture
    // ------------------------------------------------------------------

    @Test
    void uneReclamationResolueEstRouvrableEtGardeSonHistorique() {
        ClassContext context = classContext();
        Account student = context.student();
        Account manager = context.manager();
        String studentToken = tokenFor(student);
        String claimId = claimId(openClaim(studentToken, "PEDAGOGICAL_MANAGER"));
        post("/api/v1/claims/" + claimId + "/decision",
                Map.of("status", "RESOLVED", "motive", "présence corrigée"), tokenFor(manager));

        Map<String, Object> reopened = post("/api/v1/claims/" + claimId + "/reopen",
                Map.of("motive", "la correction n'apparaît pas dans mon relevé"), studentToken);

        assertThat(reopened.get("status")).isEqualTo("REOPENED");
        // La réouverture efface la clôture : la garder laisserait croire
        // que la réclamation est close alors qu'elle est active.
        assertThat(reopened.get("closedAt")).isNull();

        Map<String, Object> thread = getMap("/api/v1/claims/" + claimId, studentToken);
        assertThat(listOf(thread, "events").stream().map(event -> event.get("eventType")))
                .containsExactly("CREATED", "STATUS_CHANGED", "REOPENED");
        // Le motif de réouverture rejoint le fil : c'est ce que lira
        // l'intervenant qui reprend le dossier.
        assertThat(listOf(thread, "messages")).hasSize(2);
    }

    @Test
    void onNEcritPasDansUnFilClosSansLeRouvrir() {
        ClassContext context = classContext();
        Account student = context.student();
        Account manager = context.manager();
        String studentToken = tokenFor(student);
        String claimId = claimId(openClaim(studentToken, "PEDAGOGICAL_MANAGER"));
        post("/api/v1/claims/" + claimId + "/decision",
                Map.of("status", "CLOSED", "motive", "sans suite"), tokenFor(manager));

        assertThat(exchange(HttpMethod.POST, "/api/v1/claims/" + claimId + "/messages",
                Map.of("body", "j'insiste"), studentToken).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unApprenantNeDecidePasDeSaPropreReclamation() {
        Account student = enrolledStudent();
        String token = tokenFor(student);
        String claimId = claimId(openClaim(token, "PEDAGOGICAL_MANAGER"));

        assertThat(exchange(HttpMethod.POST, "/api/v1/claims/" + claimId + "/decision",
                Map.of("status", "RESOLVED", "motive", "je me donne raison"), token)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unStatutReserveAUneOperationDedieeEstRefuseEnDecision() {
        ClassContext context = classContext();
        Account student = context.student();
        Account manager = context.manager();
        String claimId = claimId(openClaim(tokenFor(student), "PEDAGOGICAL_MANAGER"));

        // TRANSFERRED et REOPENED sont des RÉSULTATS : les poser
        // directement contournerait leur traçabilité.
        assertThat(exchange(HttpMethod.POST, "/api/v1/claims/" + claimId + "/decision",
                Map.of("status", "TRANSFERRED", "motive", "contournement"), tokenFor(manager))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void uneReclamationOuverteNEstPasRouvrable() {
        Account student = enrolledStudent();
        String token = tokenFor(student);
        String claimId = claimId(openClaim(token, "PEDAGOGICAL_MANAGER"));

        assertThat(exchange(HttpMethod.POST, "/api/v1/claims/" + claimId + "/reopen",
                Map.of("motive", "déjà ouverte"), token).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }


    @Test
    void unApprenantSansClasseNePeutViserUnGuichetPerimetre() {
        Account student = accountWithRoles(RoleCode.STUDENT);

        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.POST, "/api/v1/claims",
                Map.of("category", "ATTENDANCE", "subject", "Sujet", "description", "Corps",
                        "audience", "PEDAGOGICAL_MANAGER"), tokenFor(student));

        // Sans classe active, aucun responsable pédagogique ne pourrait
        // lire la réclamation : la refuser en le disant vaut mieux que de
        // l'accepter dans le vide.
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().get("code")).isEqualTo("CLAIM_NO_SCOPE_FOR_AUDIENCE");
    }

    @Test
    void lAdministrationScolaireResteJoignableSansClasse() {
        Account student = accountWithRoles(RoleCode.STUDENT);

        Map<String, Object> claim = created("/api/v1/claims", Map.of(
                "category", "ACCOUNT", "subject", "Accès à mon compte",
                "description", "Je ne parviens plus à me connecter.",
                "audience", "SCHOOL_ADMINISTRATION"), tokenFor(student));

        // Le guichet global reste toujours accessible : personne ne se
        // retrouve sans interlocuteur.
        assertThat(claim.get("audience")).isEqualTo("SCHOOL_ADMINISTRATION");
    }

    // ------------------------------------------------------------------
    // Divers
    // ------------------------------------------------------------------

    @Test
    void uneCategorieInconnueEstRefusee() {
        Account student = accountWithRoles(RoleCode.STUDENT);

        assertThat(exchange(HttpMethod.POST, "/api/v1/claims", Map.of(
                "category", "PLAINTE_LIBRE", "subject", "Sujet",
                "description", "Corps", "audience", "PEDAGOGICAL_MANAGER"), tokenFor(student))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unePeriodeInverseeEstRefusee() {
        Account student = accountWithRoles(RoleCode.STUDENT);

        assertThat(exchange(HttpMethod.POST, "/api/v1/claims", Map.of(
                "category", "ATTENDANCE", "subject", "Sujet", "description", "Corps",
                "audience", "PEDAGOGICAL_MANAGER",
                "periodStart", "2026-10-20", "periodEnd", "2026-10-01"), tokenFor(student))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unTriHorsListeBlancheEstRefuse() {
        Account student = accountWithRoles(RoleCode.STUDENT);

        assertThat(exchange(HttpMethod.GET, "/api/v1/claims?sort=subject,asc", null,
                tokenFor(student)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unAnonymeNAccedePasAuxReclamations() {
        assertThat(exchange(HttpMethod.GET, "/api/v1/claims", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------

    /**
     * Apprenant <strong>inscrit</strong> — le cas nominal. Sans classe
     * active, un guichet périmétré ne pourrait pas lire la réclamation, ce
     * que le service refuse explicitement.
     */
    private Account enrolledStudent() {
        return classContext().student();
    }

    /**
     * Apprenant inscrit <strong>et</strong> responsable pédagogique
     * réellement affecté à sa formation.
     *
     * <p>Un responsable sans affectation n'a aucun périmètre et ne voit
     * rien — comportement voulu, mais qui ferait passer les tests de
     * traitement pour des refus légitimes. Les deux vont donc ensemble.
     */
    private record ClassContext(Account student, Account manager) {
    }

    private ClassContext classContext() {
        String admin = tokenFor(accountWithRoles(RoleCode.ADMIN));
        Account student = accountWithRoles(RoleCode.STUDENT);
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
        String classId = (String) created("/api/v1/class-groups", Map.of("promotionPublicId", promo,
                "programLevelPublicId", level, "sitePublicId", site, "code", "C-" + suffix,
                "name", "Classe"), admin).get("publicId");
        created("/api/v1/student-profiles", Map.of(
                "userPublicId", student.publicId(),
                "studentNumber", "ESIC-2026-" + suffix), admin).get("publicId");
        created("/api/v1/enrollments", Map.of("studentUserPublicId", student.publicId(),
                "classGroupPublicId", classId,
                "startDate", java.time.LocalDate.now().minusDays(30).toString()), admin);

        Account manager = accountWithRoles(RoleCode.PEDAGOGICAL_MANAGER);
        created("/api/v1/pedagogical-assignments", Map.of(
                "programPublicId", program, "userPublicId", manager.publicId(),
                "type", "PRIMARY_MANAGER", "reason", "responsable de la formation"), admin);
        return new ClassContext(student, manager);
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
    private static List<Map<String, Object>> listOf(Map<String, Object> body, String field) {
        return (List<Map<String, Object>>) body.get(field);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> post(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET, path, null, token);
        assertThat(response.getStatusCode()).as("GET %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.OK);
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
        UserAccount account = new UserAccount("claim-" + UUID.randomUUID() + "@esic-connect.test",
                "Reclamation", "Testeur", AccountStatus.ACTIVE);
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
}
