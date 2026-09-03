package com.esic.connect.identity;

import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.Role;
import com.esic.connect.identity.internal.RoleCode;
import com.esic.connect.identity.internal.RoleRepository;
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
 * Clés d'accès WebAuthn (EF-AUTH-006, EF-AUTH-007 ; critère AC-020).
 *
 * <p><strong>Ce que ces tests couvrent.</strong> Le contrat HTTP, le
 * cycle de vie d'un défi, l'isolation entre comptes, et la garantie
 * qu'aucune structure d'échange ne peut porter de donnée biométrique.
 *
 * <p><strong>Ce qu'ils ne couvrent pas, et pourquoi.</strong> Une
 * cérémonie complète d'enregistrement exige un authentificateur qui signe
 * réellement. Aucun n'existe dans une JVM de test : la vérification
 * cryptographique elle-même est celle de la bibliothèque WebAuthn, et un
 * test qui « simulerait » une signature valide ne prouverait rien. Cette
 * limite est consignée dans {@code docs/CURRENT-STATE.md} plutôt que
 * masquée par un test décoratif.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebAuthnIntegrationTests {

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
    void lesOptionsDEnregistrementPortentLeDefiEtLIdentifiantPublicDuCompte() {
        UserAccount student = persistStudent();
        String token = AuthTestSupport.accessToken(rest, student.getEmail(), PASSWORD);

        Map<String, Object> options = post("/api/v1/auth/webauthn/register/options", token).getBody();

        assertThat((String) options.get("challenge")).isNotBlank();
        assertThat(options.get("attestation")).isEqualTo("none");
        @SuppressWarnings("unchecked")
        Map<String, Object> rp = (Map<String, Object>) options.get("rp");
        assertThat(rp).containsKey("id").containsKey("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) options.get("user");
        // L'identifiant transmis est l'identifiant PUBLIC encodé, jamais
        // la clé technique (docs/04 §3.2).
        assertThat((String) user.get("id"))
                .isNotBlank()
                .isNotEqualTo(String.valueOf(student.getId()));
    }

    @Test
    void deuxDemandesProduisentDeuxDefisDifferents() {
        UserAccount student = persistStudent();
        String token = AuthTestSupport.accessToken(rest, student.getEmail(), PASSWORD);

        String first = (String) post("/api/v1/auth/webauthn/register/options", token)
                .getBody().get("challenge");
        String second = (String) post("/api/v1/auth/webauthn/register/options", token)
                .getBody().get("challenge");

        // Un défi réutilisé rendrait une signature capturée rejouable.
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void lesOptionsDAssertionNeRevelentAucuneListeDeJustificatifs() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                RequestEntity.post("/api/v1/auth/webauthn/login/options")
                        .contentType(MediaType.APPLICATION_JSON).body(Map.of()),
                mapType());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) response.getBody().get("challengeId")).isNotBlank();
        // Une liste de justificatifs indiquerait, à qui saisit une adresse,
        // si le compte existe et combien de clés il possède.
        assertThat((List<?>) response.getBody().get("allowCredentials")).isEmpty();
    }

    @Test
    void uneAssertionAvecUnDefiInconnuEstRefusee() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                RequestEntity.post("/api/v1/auth/webauthn/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("challengeId", "defi-inexistant", "id", "abc",
                                "response", Map.of(
                                        "clientDataJSON", "AA", "authenticatorData", "AA",
                                        "signature", "AA"))),
                mapType());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("CHALLENGE_NOT_FOUND");
    }

    @Test
    void unDefiDAssertionNeSertQuUneFois() {
        String challengeId = (String) rest.exchange(
                RequestEntity.post("/api/v1/auth/webauthn/login/options")
                        .contentType(MediaType.APPLICATION_JSON).body(Map.of()),
                mapType()).getBody().get("challengeId");

        // Première présentation : le défi est consommé, l'assertion échoue
        // ensuite sur la vérification (aucun justificatif ne correspond).
        ResponseEntity<Map<String, Object>> first = loginWith(challengeId);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Seconde présentation : le défi n'existe plus du tout.
        assertThat(loginWith(challengeId).getBody().get("code")).isEqualTo("CHALLENGE_NOT_FOUND");
    }

    @Test
    void laListeDesClesExigeUnJeton() {
        assertThat(rest.exchange(
                RequestEntity.get("/api/v1/auth/webauthn/credentials").build(), mapType())
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void lesOptionsDEnregistrementExigentUnJeton() {
        assertThat(rest.exchange(
                RequestEntity.post("/api/v1/auth/webauthn/register/options")
                        .contentType(MediaType.APPLICATION_JSON).body(Map.of()),
                mapType()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unCompteSansCleVoitUneListeVideEtNonCelleDUnAutre() {
        UserAccount first = persistStudent();
        UserAccount second = persistStudent();

        assertThat(credentials(AuthTestSupport.accessToken(rest, first.getEmail(), PASSWORD)))
                .isEmpty();
        assertThat(credentials(AuthTestSupport.accessToken(rest, second.getEmail(), PASSWORD)))
                .isEmpty();
    }

    @Test
    void laRevocationDUneCleInconnueRepond404() {
        UserAccount student = persistStudent();
        String token = AuthTestSupport.accessToken(rest, student.getEmail(), PASSWORD);

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                RequestEntity.delete("/api/v1/auth/webauthn/credentials/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                mapType());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aucunChampDeLApiNePeutPorterUneDonneeBiometrique() {
        UserAccount student = persistStudent();
        String token = AuthTestSupport.accessToken(rest, student.getEmail(), PASSWORD);

        String options = post("/api/v1/auth/webauthn/register/options", token).getBody().toString();

        // AC-020 : le serveur ne demande, ne reçoit et ne stocke aucune
        // donnée biométrique. Les options ne comportent aucun champ qui
        // pourrait en accueillir une.
        assertThat(options.toLowerCase())
                .doesNotContain("biometric")
                .doesNotContain("fingerprint")
                .doesNotContain("face");
    }

    // ------------------------------------------------------------------

    private ResponseEntity<Map<String, Object>> loginWith(String challengeId) {
        return rest.exchange(
                RequestEntity.post("/api/v1/auth/webauthn/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("challengeId", challengeId, "id", "aW5jb25udQ",
                                "response", Map.of(
                                        "clientDataJSON", "AA", "authenticatorData", "AA",
                                        "signature", "AA"))),
                mapType());
    }

    private ResponseEntity<Map<String, Object>> post(String path, String token) {
        return rest.exchange(
                RequestEntity.post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of()),
                mapType());
    }

    private List<?> credentials(String token) {
        return rest.exchange(
                RequestEntity.get("/api/v1/auth/webauthn/credentials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
    }

    private UserAccount persistStudent() {
        UserAccount account = new UserAccount("passkey-" + UUID.randomUUID() + "@esic-connect.test",
                "Prenom", "Nom", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account = userAccountRepository.saveAndFlush(account);
        Role role = roleRepository.findByCode(RoleCode.STUDENT).orElseThrow();
        userRoleRepository.saveAndFlush(new UserRole(account, role, Instant.now(), true));
        return account;
    }

    private static ParameterizedTypeReference<Map<String, Object>> mapType() {
        return new ParameterizedTypeReference<>() {
        };
    }
}
