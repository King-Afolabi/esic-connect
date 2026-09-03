package com.esic.connect.identity;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Création de compte, émission, suivi et réémission d'invitation
 * (EF-USER-001, EF-USER-007, EF-USER-008 ; docs/02 §11).
 *
 * <p>Le point vérifié en priorité : réémettre <strong>révoque</strong> le
 * jeton précédent. Sans cela, corriger une adresse laisserait un lien
 * d'activation valide dans une boîte qui n'est pas la bonne.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InvitationLifecycleIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word";
    private static final String CHOSEN_PASSWORD = "cheval batterie agrafe correct";

    /** Capture les jetons émis, comme le fait le module {@code notification}. */
    static class CapturedInvitations {
        private final List<String> tokens = new CopyOnWriteArrayList<>();

        void record(String rawToken) {
            tokens.add(rawToken);
        }

        List<String> tokens() {
            return List.copyOf(tokens);
        }
    }

    @TestConfiguration
    static class CapturingMailerConfig {
        @Bean
        CapturedInvitations capturedInvitations() {
            return new CapturedInvitations();
        }

        @Bean
        @Primary
        InvitationMailer capturingMailer(CapturedInvitations captured) {
            return (toEmail, firstName, rawToken, expiresAt) -> captured.record(rawToken);
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
    @Autowired
    private CapturedInvitations captured;

    // ------------------------------------------------------------------
    // EF-USER-001 — création d'un compte en attente d'activation
    // ------------------------------------------------------------------

    @Test
    void unCompteEstCreeEnAttenteDActivationEtInviteDansLaFoulee() {
        String admin = tokenFor(RoleCode.ADMIN);
        String email = "nouveau-" + UUID.randomUUID() + "@esic-connect.test";

        Map<String, Object> created = created("/api/v1/users", body(
                "email", email, "firstName", "Camille", "lastName", "Bernard",
                "role", "STUDENT"), admin);

        assertThat(created.get("status")).isEqualTo("PENDING_ACTIVATION");
        assertThat(created.get("email")).isEqualTo(email);
        // Aucun mot de passe n'est défini ni transmis : la personne le
        // choisira via son lien d'invitation (docs/02 §11.2).
        assertThat(created.toString()).doesNotContain("password");
        assertThat(captured.tokens()).isNotEmpty();
    }

    @Test
    void laCreationSansInvitationLaisseLeCompteEnAttenteSansJeton() {
        String admin = tokenFor(RoleCode.ADMIN);
        int before = captured.tokens().size();

        Map<String, Object> created = created("/api/v1/users", body(
                "email", "sans-invit-" + UUID.randomUUID() + "@esic-connect.test",
                "firstName", "Sans", "lastName", "Invitation",
                "role", "TEACHER", "sendInvitation", false), admin);

        assertThat(created.get("status")).isEqualTo("PENDING_ACTIVATION");
        assertThat(captured.tokens()).hasSize(before);
    }

    @Test
    void uneAdresseDejaUtiliseeEstRefuseeSansCreerDeDoublon() {
        String admin = tokenFor(RoleCode.ADMIN);
        String email = "doublon-" + UUID.randomUUID() + "@esic-connect.test";
        created("/api/v1/users", body("email", email, "firstName", "Un", "lastName", "Compte",
                "role", "STUDENT", "sendInvitation", false), admin);

        ResponseEntity<Map<String, Object>> duplicate = exchange(HttpMethod.POST, "/api/v1/users",
                body("email", email, "firstName", "Autre", "lastName", "Compte",
                        "role", "STUDENT", "sendInvitation", false), admin);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("code")).isEqualTo("USER_EMAIL_ALREADY_USED");
    }

    @Test
    void unAdministrateurNePeutPasCreerUnSuperAdministrateur() {
        String admin = tokenFor(RoleCode.ADMIN);

        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.POST, "/api/v1/users",
                body("email", "super-" + UUID.randomUUID() + "@esic-connect.test",
                        "firstName", "Super", "lastName", "Admin",
                        "role", "SUPER_ADMIN", "sendInvitation", false), admin);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refused.getBody().get("code")).isEqualTo("USER_SUPER_ADMIN_PROTECTED");
    }

    @Test
    void laCreationEstFermeeAuxRolesNonAdministrateurs() {
        String schoolAdmin = tokenFor(RoleCode.SCHOOL_ADMINISTRATION);

        assertThat(exchange(HttpMethod.POST, "/api/v1/users",
                body("email", "x-" + UUID.randomUUID() + "@esic-connect.test",
                        "firstName", "X", "lastName", "Y", "role", "STUDENT"), schoolAdmin)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------
    // EF-USER-007 — suivi et réémission
    // ------------------------------------------------------------------

    @Test
    void lesInvitationsEmisesSontListeesAvecLeurStatut() {
        String admin = tokenFor(RoleCode.ADMIN);
        String email = "suivi-" + UUID.randomUUID() + "@esic-connect.test";
        created("/api/v1/users", body("email", email, "firstName", "Suivi", "lastName", "Test",
                "role", "STUDENT"), admin);

        Map<String, Object> page = getMap("/api/v1/account-invitations?status=PENDING", admin);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
        Map<String, Object> row = content.stream()
                .filter(item -> email.equals(item.get("email")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("invitation absente du suivi : " + content));
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("expired")).isEqualTo(false);
        assertThat(row).containsKeys("expiresAt", "createdAt", "userId");
    }

    @Test
    void laReemissionRevoqueLeJetonPrecedent() {
        String admin = tokenFor(RoleCode.ADMIN);
        String email = "reemission-" + UUID.randomUUID() + "@esic-connect.test";
        created("/api/v1/users", body("email", email, "firstName", "Ré", "lastName", "Émission",
                "role", "STUDENT"), admin);
        String firstToken = captured.tokens().get(captured.tokens().size() - 1);
        String invitationId = invitationIdFor(email, admin);

        assertThat(exchange(HttpMethod.POST,
                "/api/v1/account-invitations/" + invitationId + "/resend", Map.of(), admin)
                .getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String secondToken = captured.tokens().get(captured.tokens().size() - 1);

        assertThat(secondToken).isNotEqualTo(firstToken);
        // L'ancien lien ne doit plus rien ouvrir : sans cela, corriger une
        // adresse laisserait un lien valide dans la mauvaise boîte.
        assertThat(validate(firstToken)).isFalse();
        assertThat(validate(secondToken)).isTrue();
    }

    @Test
    void leNouveauJetonActiveBienLeCompte() {
        String admin = tokenFor(RoleCode.ADMIN);
        String email = "activation-" + UUID.randomUUID() + "@esic-connect.test";
        created("/api/v1/users", body("email", email, "firstName", "Act", "lastName", "Ivation",
                "role", "STUDENT"), admin);
        String invitationId = invitationIdFor(email, admin);
        exchange(HttpMethod.POST, "/api/v1/account-invitations/" + invitationId + "/resend",
                Map.of(), admin);
        String token = captured.tokens().get(captured.tokens().size() - 1);

        assertThat(exchange(HttpMethod.POST, "/api/v1/account-invitations/activate",
                body("token", token, "password", CHOSEN_PASSWORD), null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(userAccountRepository.findByEmail(email).orElseThrow().getStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void unCompteDejaActifNePeutPlusEtreReinvite() {
        String admin = tokenFor(RoleCode.ADMIN);
        String email = "deja-actif-" + UUID.randomUUID() + "@esic-connect.test";
        created("/api/v1/users", body("email", email, "firstName", "Déjà", "lastName", "Actif",
                "role", "STUDENT"), admin);
        String invitationId = invitationIdFor(email, admin);
        exchange(HttpMethod.POST, "/api/v1/account-invitations/activate",
                body("token", captured.tokens().get(captured.tokens().size() - 1),
                        "password", CHOSEN_PASSWORD), null);

        ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.POST,
                "/api/v1/account-invitations/" + invitationId + "/resend", Map.of(), admin);

        // Réémettre rouvrirait un chemin d'activation sur un compte déjà
        // vivant : c'est refusé.
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void uneInvitationInconnueRepond404() {
        String admin = tokenFor(RoleCode.ADMIN);

        assertThat(exchange(HttpMethod.POST,
                "/api/v1/account-invitations/" + UUID.randomUUID() + "/resend", Map.of(), admin)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void leSuiviDesInvitationsExigeUnRoleAutorise() {
        String teacher = tokenFor(RoleCode.TEACHER);

        assertThat(exchange(HttpMethod.GET, "/api/v1/account-invitations", null, teacher)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(HttpMethod.GET, "/api/v1/account-invitations", null, null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------
    // EF-USER-008 — délivrabilité
    // ------------------------------------------------------------------

    @Test
    void chaqueEnvoiLaisseUneTraceDistinguantEnvoiEtDelivrabilite() {
        String admin = tokenFor(RoleCode.ADMIN);
        created("/api/v1/users", body(
                "email", "delivr-" + UUID.randomUUID() + "@esic-connect.test",
                "firstName", "Dé", "lastName", "Livrabilité", "role", "STUDENT"), admin);

        Map<String, Object> page = getMap(
                "/api/v1/email-deliveries?messageType=ACCOUNT_INVITATION", admin);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
        assertThat(content).isNotEmpty();
        Map<String, Object> latest = content.get(0);
        assertThat(latest.get("internalStatus")).isEqualTo("SENT_TO_PROVIDER");
        // « Remis au serveur de messagerie » n'est pas « délivré » : sans
        // retour du fournisseur, l'état reste inconnu (docs/02 §11.3).
        assertThat(latest.get("providerStatus")).isEqualTo("UNKNOWN");
        assertThat(latest.get("attempts")).isEqualTo(1);
    }

    @Test
    void leJournalNExposeQueDesAdressesMasquees() {
        String admin = tokenFor(RoleCode.ADMIN);
        String email = "masque-" + UUID.randomUUID() + "@esic-connect.test";
        created("/api/v1/users", body("email", email, "firstName", "Mas", "lastName", "Qué",
                "role", "STUDENT"), admin);

        Map<String, Object> page = getMap(
                "/api/v1/email-deliveries?messageType=ACCOUNT_INVITATION", admin);

        // Aucune adresse en clair : la table ne doit pas constituer un
        // annuaire exploitable (docs/08, minimisation).
        assertThat(page.toString()).doesNotContain(email);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
        assertThat((String) content.get(0).get("recipientMasked")).contains("…").contains("@");
    }

    @Test
    void unStatutDeFiltreInconnuEstRefuseSansErreurServeur() {
        String admin = tokenFor(RoleCode.ADMIN);

        assertThat(exchange(HttpMethod.GET,
                "/api/v1/email-deliveries?internalStatus=PAS_UN_STATUT", null, admin)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void leJournalDeDelivrabiliteEstFermeAuxApprenants() {
        String student = tokenFor(RoleCode.STUDENT);

        assertThat(exchange(HttpMethod.GET, "/api/v1/email-deliveries", null, student)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------

    private String invitationIdFor(String email, String token) {
        Map<String, Object> page = getMap("/api/v1/account-invitations?status=PENDING&size=100", token);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
        return content.stream()
                .filter(row -> email.equals(row.get("email")))
                .map(row -> (String) row.get("id"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("invitation introuvable pour " + email));
    }

    private boolean validate(String rawToken) {
        Map<String, Object> body = rest.exchange(RequestEntity
                        .get(URI.create("/api/v1/account-invitations/validate?token="
                                + java.net.URLEncoder.encode(rawToken,
                                java.nio.charset.StandardCharsets.UTF_8)))
                        .build(),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }).getBody();
        return Boolean.TRUE.equals(body.get("valid"));
    }

    private static Map<String, Object> body(Object... keyValues) {
        Map<String, Object> body = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            body.put((String) keyValues[i], keyValues[i + 1]);
        }
        return body;
    }

    private Map<String, Object> created(String path, Map<String, Object> body, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.POST, path, body, token);
        assertThat(response.getStatusCode()).as("POST %s -> %s", path, response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String, Object> getMap(String path, String token) {
        ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET, path, null, token);
        assertThat(response.getStatusCode()).as("GET %s", path).isEqualTo(HttpStatus.OK);
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

    private String tokenFor(RoleCode... roles) {
        UserAccount account = new UserAccount("inv-" + UUID.randomUUID() + "@esic-connect.test",
                "Invit", "Testeur", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        for (RoleCode roleCode : roles) {
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        }
        return AuthTestSupport.accessToken(rest, account.getEmail(), PASSWORD);
    }
}
