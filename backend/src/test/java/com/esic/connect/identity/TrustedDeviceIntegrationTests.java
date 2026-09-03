package com.esic.connect.identity;

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
 * Appareils de confiance et authentification adaptative (EF-AUTH-010,
 * EF-AUTH-013 ; docs/02 §17.4 et §17.7).
 *
 * <p>Ce qui est vérifié ici, au-delà du parcours nominal : un appareil
 * reconnu allège la connexion d'un compte ordinaire, mais <strong>ne
 * dispense jamais</strong> un compte privilégié de son second facteur, et
 * un compte ne voit ni ne révoque les appareils d'un autre.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TrustedDeviceIntegrationTests {

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

    @Test
    void unAppareilInconnuDeclencheLeSecondFacteurDUnCompteOrdinaire() {
        Enrolled student = enrolledStudent("appareil-connu");

        // Même compte, autre appareil : le facteur est redemandé.
        Map<String, Object> body = loginWithDevice(student.email(), "appareil-inconnu").getBody();

        assertThat(body).doesNotContainKey("accessToken");
        assertThat(body).containsKey("mfa");
    }

    @Test
    void unAppareilReconnuAllegeLaReconnexionDUnCompteOrdinaire() {
        Enrolled student = enrolledStudent("appareil-connu");

        Map<String, Object> body = loginWithDevice(student.email(), "appareil-connu").getBody();

        assertThat((String) body.get("accessToken")).isNotBlank();
        assertThat(body).doesNotContainKey("mfa");
    }

    @Test
    void unAppareilReconnuNeDispensePasUnAdministrateurDeSonSecondFacteur() {
        Enrolled admin = enrolledPrivileged("poste-administrateur");

        // Le même appareil vient pourtant d'authentifier complètement ce
        // compte : la politique de rôle prime (RG-007, AC-021).
        Map<String, Object> body = loginWithDevice(admin.email(), "poste-administrateur").getBody();

        assertThat(body).doesNotContainKey("accessToken");
        assertThat(body).containsKey("mfa");
    }

    @Test
    void lAppareilNEstMemoriseQuApresUneAuthentificationComplete() {
        UserAccount student = persistUser(RoleCode.STUDENT);
        // Mot de passe faux : la tentative ne doit rien enregistrer.
        rest.exchange(RequestEntity.post("/api/v1/auth/login")
                        .header("X-Device-Id", "appareil-de-lattaquant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", student.getEmail(), "password", "mauvais-mot-de-passe")),
                mapType());

        String token = AuthTestSupport.accessToken(rest, student.getEmail(), PASSWORD);

        assertThat(devices(token)).isEmpty();
    }

    @Test
    void unAppareilUtiliseEstListePuisRevocable() {
        Enrolled student = enrolledStudent("tablette-de-salle");

        List<Map<String, Object>> devices = devices(student.token());
        assertThat(devices).hasSize(1);
        assertThat(devices.get(0)).containsEntry("usable", true);

        String deviceId = (String) devices.get(0).get("id");
        assertThat(rest.exchange(RequestEntity
                        .delete("/api/v1/auth/devices/" + deviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token()).build(),
                Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(devices(student.token())).isEmpty();
    }

    @Test
    void unAppareilRevoqueRedeclencheLeSecondFacteur() {
        Enrolled student = enrolledStudent("portable-perdu");
        String deviceId = (String) devices(student.token()).get(0).get("id");
        rest.exchange(RequestEntity.delete("/api/v1/auth/devices/" + deviceId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + student.token()).build(), Void.class);

        assertThat(loginWithDevice(student.email(), "portable-perdu").getBody())
                .doesNotContainKey("accessToken");
    }

    @Test
    void unCompteNePeutPasRevoquerLAppareilDUnAutre() {
        Enrolled first = enrolledStudent("appareil-de-premier");
        Enrolled second = enrolledStudent("appareil-de-second");
        String foreignDevice = (String) devices(first.token()).get(0).get("id");

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                RequestEntity.delete("/api/v1/auth/devices/" + foreignDevice)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + second.token()).build(),
                mapType());

        // 404 et non 403 : l'existence de l'appareil d'autrui est elle-même
        // une information à protéger (docs/02 §18.2).
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(devices(first.token())).hasSize(1);
    }

    @Test
    void laListeDesAppareilsExigeUnJeton() {
        assertThat(rest.exchange(RequestEntity.get("/api/v1/auth/devices").build(), mapType())
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aucuneEmpreinteDAppareilNEstRenvoyeeAuClient() {
        Enrolled student = enrolledStudent("empreinte-a-ne-pas-exposer");

        assertThat(devices(student.token()).toString())
                .doesNotContain("empreinte-a-ne-pas-exposer")
                .doesNotContain("deviceHash");
    }

    // ------------------------------------------------------------------

    private record Enrolled(String email, String secret, String token) {
    }

    /** Apprenant doté d'un second facteur actif et d'un appareil déjà reconnu. */
    private Enrolled enrolledStudent(String deviceId) {
        return enroll(persistUser(RoleCode.STUDENT), deviceId);
    }

    private Enrolled enrolledPrivileged(String deviceId) {
        return enroll(persistUser(RoleCode.ADMIN), deviceId);
    }

    /**
     * Amène un compte à l'état « second facteur actif, appareil reconnu »
     * en passant par le parcours réel. Deux chemins, selon la politique :
     * un compte privilégié est enrôlé de force depuis le défi de
     * connexion, un compte ordinaire l'est depuis sa session.
     */
    private Enrolled enroll(UserAccount account, String deviceId) {
        Map<String, Object> first = loginWithDevice(account.getEmail(), deviceId).getBody();

        if (first.containsKey("mfa")) {
            String challengeId = (String) challengeOf(first).get("challengeId");
            String secret = (String) postWithDevice("/api/v1/auth/mfa/enroll",
                    Map.of("challengeId", challengeId), deviceId, null).getBody().get("secret");
            Map<String, Object> confirmation = postWithDevice("/api/v1/auth/mfa/enroll/confirm",
                    Map.of("challengeId", challengeId, "code", previousCode(secret)),
                    deviceId, null).getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> session = (Map<String, Object>) confirmation.get("session");
            return new Enrolled(account.getEmail(), secret, (String) session.get("accessToken"));
        }

        // Compte ordinaire : la première connexion a déjà fait reconnaître
        // l'appareil ; le facteur s'ajoute ensuite depuis la session.
        String token = (String) first.get("accessToken");
        String secret = (String) postWithDevice("/api/v1/auth/mfa/enroll", Map.of(), deviceId, token)
                .getBody().get("secret");
        postWithDevice("/api/v1/auth/mfa/enroll/confirm", Map.of("code", previousCode(secret)),
                deviceId, token);
        return new Enrolled(account.getEmail(), secret, token);
    }

    /**
     * Code du pas précédent : la confirmation consomme un pas, et le pas
     * courant reste alors disponible pour la vérification suivante sans
     * buter sur l'anti-rejeu.
     */
    private static String previousCode(String secret) {
        return TotpGenerator.codeAt(secret, TotpGenerator.stepOf(Instant.now()) - 1);
    }

    private ResponseEntity<Map<String, Object>> postWithDevice(String path, Map<String, ?> body,
                                                               String deviceId, String token) {
        RequestEntity.BodyBuilder builder = RequestEntity.post(path)
                .header("X-Device-Id", deviceId)
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            builder = builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return rest.exchange(builder.body(body), mapType());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> devices(String token) {
        return rest.exchange(RequestEntity.get("/api/v1/auth/devices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
    }

    private ResponseEntity<Map<String, Object>> loginWithDevice(String email, String deviceId) {
        return rest.exchange(RequestEntity.post("/api/v1/auth/login")
                        .header("X-Device-Id", deviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "password", PASSWORD)),
                mapType());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> challengeOf(Map<String, Object> loginBody) {
        Map<String, Object> challenge = (Map<String, Object>) loginBody.get("mfa");
        assertThat(challenge).as("défi attendu dans %s", loginBody).isNotNull();
        return challenge;
    }

    private UserAccount persistUser(RoleCode roleCode) {
        UserAccount account = new UserAccount("device-" + UUID.randomUUID() + "@esic-connect.test",
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
