package com.esic.connect.identity;

import com.esic.connect.audit.internal.AuditEvent;
import com.esic.connect.audit.internal.AuditEventRepository;
import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
import com.esic.connect.identity.internal.TotpGenerator;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.identity.internal.UserRole;
import com.esic.connect.identity.internal.UserRoleRepository;
import com.esic.connect.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Second facteur TOTP et codes de récupération, de bout en bout
 * (EF-AUTH-008, EF-AUTH-009 ; critère AC-021).
 *
 * <p>Ces tests exercent le parcours réel : aucune injection directe en
 * base, aucun profil dérogatoire. Les codes sont calculés comme le ferait
 * une application d'authentification, à partir du secret remis à
 * l'enrôlement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MfaIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word-2026";

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
    @Autowired
    private AuditEventRepository auditEventRepository;

    // ------------------------------------------------------------------
    // AC-021 — un compte privilégié ne se connecte pas sans second facteur
    // ------------------------------------------------------------------

    @Test
    void unAdministrateurNObtientPasDeJetonContreSonSeulMotDePasse() {
        UserAccount admin = persistUser(RoleCode.ADMIN);

        Map<String, Object> body = login(admin.getEmail()).getBody();

        assertThat(body).doesNotContainKey("accessToken");
        assertThat(challengeOf(body)).containsEntry("purpose", "ENROLL");
    }

    @Test
    void unSuperAdministrateurEstSoumisALaMemeExigence() {
        UserAccount superAdmin = persistUser(RoleCode.SUPER_ADMIN);

        assertThat(login(superAdmin.getEmail()).getBody()).doesNotContainKey("accessToken");
    }

    @Test
    void unApprenantSansFacteurSeConnecteNormalement() {
        UserAccount student = persistUser(RoleCode.STUDENT);

        Map<String, Object> body = login(student.getEmail()).getBody();

        assertThat((String) body.get("accessToken")).isNotBlank();
        assertThat(body).doesNotContainKey("mfa");
    }

    @Test
    void lEnrolementForceDebloqueLaConnexionEtRemetDesCodesDeRecuperation() {
        UserAccount admin = persistUser(RoleCode.ADMIN);
        String challengeId = (String) challengeOf(login(admin.getEmail()).getBody()).get("challengeId");

        Map<String, Object> enrollment = post("/api/v1/auth/mfa/enroll",
                Map.of("challengeId", challengeId), null).getBody();
        assertThat((String) enrollment.get("secret")).isNotBlank();
        assertThat((String) enrollment.get("provisioningUri")).startsWith("otpauth://totp/");

        Map<String, Object> confirmation = post("/api/v1/auth/mfa/enroll/confirm",
                Map.of("challengeId", challengeId,
                        "code", AuthTestSupport.currentCode((String) enrollment.get("secret"))),
                null).getBody();

        assertThat((List<?>) confirmation.get("recoveryCodes")).hasSize(10);
        @SuppressWarnings("unchecked")
        Map<String, Object> session = (Map<String, Object>) confirmation.get("session");
        assertThat((String) session.get("accessToken")).isNotBlank();
    }

    @Test
    void unCompteDejaEnroleRecoitUnDefiDeVerificationEtNonDEnrolement() {
        Enrolled admin = enrolledAdmin();

        assertThat(challengeOf(login(admin.email()).getBody())).containsEntry("purpose", "VERIFY");
    }

    @Test
    void unCodeTotpValideDelivreLeJeton() {
        Enrolled admin = enrolledAdmin();
        String challengeId = (String) challengeOf(login(admin.email()).getBody()).get("challengeId");

        ResponseEntity<Map<String, Object>> response = post("/api/v1/auth/mfa/verify",
                Map.of("challengeId", challengeId, "code", AuthTestSupport.currentCode(admin.secret())),
                null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) response.getBody().get("accessToken")).isNotBlank();
    }

    @Test
    void unCodeFauxEstRefuseSansDelivrerDeJeton() {
        Enrolled admin = enrolledAdmin();
        String challengeId = (String) challengeOf(login(admin.email()).getBody()).get("challengeId");

        ResponseEntity<Map<String, Object>> response = post("/api/v1/auth/mfa/verify",
                Map.of("challengeId", challengeId, "code", "000000"), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).doesNotContainKey("accessToken");
    }

    @Test
    void unDefiInconnuEstRefuse() {
        ResponseEntity<Map<String, Object>> response = post("/api/v1/auth/mfa/verify",
                Map.of("challengeId", "defi-inexistant", "code", "123456"), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("CHALLENGE_NOT_FOUND");
    }

    // ------------------------------------------------------------------
    // Anti-rejeu
    // ------------------------------------------------------------------

    @Test
    void unMemeCodeNePeutPasServirDeuxFois() {
        Enrolled admin = enrolledAdmin();
        String code = AuthTestSupport.currentCode(admin.secret());

        String firstChallenge = (String) challengeOf(login(admin.email()).getBody()).get("challengeId");
        assertThat(post("/api/v1/auth/mfa/verify",
                Map.of("challengeId", firstChallenge, "code", code), null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        String secondChallenge = (String) challengeOf(login(admin.email()).getBody()).get("challengeId");
        ResponseEntity<Map<String, Object>> replay = post("/api/v1/auth/mfa/verify",
                Map.of("challengeId", secondChallenge, "code", code), null);

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(replay.getBody().get("code")).isEqualTo("CODE_ALREADY_USED");
    }

    @Test
    void unDefiResoluNePeutPasEtreRejoue() {
        Enrolled admin = enrolledAdmin();
        String challengeId = (String) challengeOf(login(admin.email()).getBody()).get("challengeId");
        post("/api/v1/auth/mfa/verify",
                Map.of("challengeId", challengeId, "code", AuthTestSupport.currentCode(admin.secret())),
                null);

        ResponseEntity<Map<String, Object>> replay = post("/api/v1/auth/mfa/verify",
                Map.of("challengeId", challengeId, "code", AuthTestSupport.currentCode(admin.secret())),
                null);

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------
    // Codes de récupération
    // ------------------------------------------------------------------

    @Test
    void unCodeDeRecuperationOuvreLaSessionUneSeuleFois() {
        Enrolled admin = enrolledAdmin();
        String recoveryCode = admin.recoveryCodes().get(0);

        String firstChallenge = (String) challengeOf(login(admin.email()).getBody()).get("challengeId");
        ResponseEntity<Map<String, Object>> first = post("/api/v1/auth/mfa/verify",
                Map.of("challengeId", firstChallenge, "code", recoveryCode), null);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        String secondChallenge = (String) challengeOf(login(admin.email()).getBody()).get("challengeId");
        ResponseEntity<Map<String, Object>> second = post("/api/v1/auth/mfa/verify",
                Map.of("challengeId", secondChallenge, "code", recoveryCode), null);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void leCodeDeRecuperationDUnAutreCompteEstRefuse() {
        Enrolled first = enrolledAdmin();
        Enrolled second = enrolledAdmin();
        String challengeId = (String) challengeOf(login(second.email()).getBody()).get("challengeId");

        ResponseEntity<Map<String, Object>> response = post("/api/v1/auth/mfa/verify",
                Map.of("challengeId", challengeId, "code", first.recoveryCodes().get(0)), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void laRegenerationInvalideLesAnciensCodes() {
        Enrolled admin = enrolledAdmin();
        String oldCode = admin.recoveryCodes().get(1);

        ResponseEntity<Map<String, Object>> regenerated = post("/api/v1/auth/mfa/recovery-codes",
                Map.of("code", AuthTestSupport.currentCode(admin.secret())), admin.token());
        assertThat(regenerated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) regenerated.getBody().get("recoveryCodes")).hasSize(10);

        String challengeId = (String) challengeOf(login(admin.email()).getBody()).get("challengeId");
        assertThat(post("/api/v1/auth/mfa/verify",
                Map.of("challengeId", challengeId, "code", oldCode), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------
    // Gestion depuis une session ouverte
    // ------------------------------------------------------------------

    @Test
    void lEtatDuFacteurNExposeJamaisLeSecret() {
        Enrolled admin = enrolledAdmin();

        ResponseEntity<Map<String, Object>> status = rest.exchange(
                RequestEntity.get("/api/v1/auth/mfa")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()).build(),
                mapType());

        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody()).containsEntry("enabled", true)
                .containsEntry("requiredByRole", true)
                .containsEntry("activeRecoveryCodes", 10);
        assertThat(status.getBody().toString()).doesNotContain(admin.secret());
    }

    @Test
    void unAdministrateurNePeutPasRetirerSonSecondFacteur() {
        Enrolled admin = enrolledAdmin();

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                RequestEntity.method(org.springframework.http.HttpMethod.DELETE, "/api/v1/auth/mfa")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("code", AuthTestSupport.currentCode(admin.secret()))),
                mapType());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("REQUIRED_BY_ROLE");
    }

    @Test
    void lEtatDuFacteurExigeUnJeton() {
        assertThat(rest.exchange(RequestEntity.get("/api/v1/auth/mfa").build(), mapType())
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------
    // Audit
    // ------------------------------------------------------------------

    @Test
    void lEnrolementEstAuditeSansSecretNiCode() {
        Enrolled admin = enrolledAdmin();
        UserAccount account = userAccountRepository.findByEmail(admin.email()).orElseThrow();

        List<AuditEvent> events = auditEventRepository.findAll().stream()
                .filter(event -> account.getId().equals(event.getActorUserId()))
                .toList();

        assertThat(events).extracting(AuditEvent::getAction).contains("MFA_ENROLLED");
        assertThat(events).allSatisfy(event ->
                assertThat(event.toString()).doesNotContain(admin.secret(), PASSWORD));
    }

    // ------------------------------------------------------------------
    // Réauthentification avant action critique (EF-AUTH-015)
    // ------------------------------------------------------------------

    @Test
    void lAttributionDUnRoleExigeUneAuthentificationForte() {
        Enrolled admin = enrolledAdmin();
        UserAccount target = persistUser(RoleCode.STUDENT);
        String weakToken = AuthTestSupport.accessToken(rest, target.getEmail(), PASSWORD);

        // Jeton obtenu par mot de passe seul : refusé sur une action critique,
        // même si le rôle l'autoriserait.
        ResponseEntity<Map<String, Object>> refused = rest.exchange(
                RequestEntity.post("/api/v1/users/" + target.getPublicId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + weakToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("role", "TEACHER", "reason", "essai")),
                mapType());
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Le jeton de l'administrateur, lui, porte `amr: [pwd, otp]`.
        ResponseEntity<Map<String, Object>> accepted = rest.exchange(
                RequestEntity.post("/api/v1/users/" + target.getPublicId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("role", "TEACHER", "reason", "affectation pédagogique")),
                mapType());
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ------------------------------------------------------------------
    // Outillage
    // ------------------------------------------------------------------

    /** Compte administrateur déjà enrôlé, avec son secret et ses codes. */
    private record Enrolled(String email, String secret, List<String> recoveryCodes, String token) {
    }

    private Enrolled enrolledAdmin() {
        UserAccount admin = persistUser(RoleCode.ADMIN);
        String challengeId = (String) challengeOf(login(admin.getEmail()).getBody()).get("challengeId");
        Map<String, Object> enrollment = post("/api/v1/auth/mfa/enroll",
                Map.of("challengeId", challengeId), null).getBody();
        String secret = (String) enrollment.get("secret");
        Map<String, Object> confirmation = post("/api/v1/auth/mfa/enroll/confirm",
                Map.of("challengeId", challengeId, "code", codeForNextStep(secret)), null).getBody();
        @SuppressWarnings("unchecked")
        List<String> codes = (List<String>) confirmation.get("recoveryCodes");
        @SuppressWarnings("unchecked")
        Map<String, Object> session = (Map<String, Object>) confirmation.get("session");
        return new Enrolled(admin.getEmail(), secret, codes, (String) session.get("accessToken"));
    }

    /**
     * Code du pas <em>précédent</em> pour la confirmation d'enrôlement :
     * le pas courant reste ainsi disponible pour la vérification qui suit
     * dans le test, sans buter sur l'anti-rejeu.
     */
    private static String codeForNextStep(String secret) {
        return TotpGenerator.codeAt(secret, TotpGenerator.stepOf(Instant.now()) - 1);
    }

    private ResponseEntity<Map<String, Object>> login(String email) {
        return AuthTestSupport.login(rest, email, PASSWORD);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> challengeOf(Map<String, Object> loginBody) {
        Map<String, Object> challenge = (Map<String, Object>) loginBody.get("mfa");
        assertThat(challenge).as("défi de second facteur attendu dans %s", loginBody).isNotNull();
        return challenge;
    }

    private ResponseEntity<Map<String, Object>> post(String path, Map<String, ?> body, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.post(path)
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            builder = builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return rest.exchange(builder.body(body), mapType());
    }

    private UserAccount persistUser(RoleCode roleCode) {
        UserAccount account = new UserAccount("mfa-" + UUID.randomUUID() + "@esic-connect.test",
                "Prenom", "Nom", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        return account;
    }

    private static ParameterizedTypeReference<Map<String, Object>> mapType() {
        return new ParameterizedTypeReference<>() {
        };
    }
}
