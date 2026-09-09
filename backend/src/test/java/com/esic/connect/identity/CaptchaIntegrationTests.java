package com.esic.connect.identity;

import com.esic.connect.identity.internal.AccountStatus;
import com.esic.connect.identity.internal.UserAccount;
import com.esic.connect.identity.internal.UserAccountRepository;
import com.esic.connect.shared.captcha.CaptchaVerdict;
import com.esic.connect.shared.captcha.CaptchaVerifier;
import com.esic.connect.shared.ratelimit.IdentityHashing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Protection anti-robot (EF-AUTH-011, RG-092 ; critère AC-022).
 *
 * <p>Un vérificateur de test remplace l'adaptateur Cloudflare : appeler
 * un service externe depuis une suite automatisée la rendrait dépendante
 * d'un réseau et d'une clé, et ne prouverait rien de plus sur
 * <em>notre</em> code. Ce qui est vérifié ici, c'est ce dont le produit
 * est responsable : le contrôle est fait <strong>côté serveur</strong>,
 * il se déclenche au bon moment, et son refus ne divulgue rien.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.login.captcha-after-failures=3")
@ActiveProfiles("test")
class CaptchaIntegrationTests {

    private static final String PASSWORD = "S3cure-Pass!word-2026";
    private static final String VALID_TOKEN = "jeton-anti-robot-valide";

    @TestConfiguration
    static class StubCaptchaConfig {
        static final AtomicInteger calls = new AtomicInteger();

        @Bean
        @Primary
        CaptchaVerifier stubCaptchaVerifier() {
            return new CaptchaVerifier() {
                @Override
                public CaptchaVerdict verify(String token, String clientOrigin) {
                    calls.incrementAndGet();
                    return VALID_TOKEN.equals(token)
                            ? CaptchaVerdict.pass()
                            : CaptchaVerdict.reject("invalid-input-response");
                }

                @Override
                public boolean isEnforced() {
                    return true;
                }
            };
        }
    }

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void resetSharedCounters() {
        // Le compteur d'échecs par ORIGINE est partagé par toute la suite
        // (127.0.0.1) : sans remise à zéro, ces tests hériteraient d'un
        // état construit par d'autres classes.
        redis.delete("esic:rate-limit:login-failures:" + IdentityHashing.of("127.0.0.1"));
        redis.delete("esic:rate-limit:login-origin:" + IdentityHashing.of("127.0.0.1"));
        StubCaptchaConfig.calls.set(0);
    }

    @Test
    void laConfigurationPubliqueEstLisibleSansJeton() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                RequestEntity.get("/api/v1/auth/captcha").build(), mapType());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("enforced", true);
        // La clé de SITE est publique par construction ; la clé secrète ne
        // doit apparaître nulle part.
        assertThat(response.getBody()).containsKey("siteKey");
    }

    @Test
    void uneConnexionNormaleNExigePasDeControleAntiRobot() {
        UserAccount account = persistUser();

        assertThat(login(account.getEmail(), PASSWORD, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(StubCaptchaConfig.calls.get())
                .as("aucun contrôle tant qu'aucun comportement suspect n'est constaté")
                .isZero();
    }

    @Test
    void apresTroisEchecsLeControleAntiRobotDevientObligatoire() {
        UserAccount account = persistUser();

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(login(account.getEmail(), "mauvais-mot-de-passe", null).getStatusCode())
                    .as("tentative %d", attempt)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // AC-022 : au-delà du seuil, un contrôle renforcé est déclenché —
        // même avec le BON mot de passe.
        ResponseEntity<Map<String, Object>> refused = login(account.getEmail(), PASSWORD, null);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("code")).isEqualTo("CAPTCHA_REJECTED");
    }

    @Test
    void unJetonAntiRobotValideDebloqueLaConnexion() {
        UserAccount account = persistUser();
        for (int attempt = 1; attempt <= 3; attempt++) {
            login(account.getEmail(), "mauvais-mot-de-passe", null);
        }

        assertThat(login(account.getEmail(), PASSWORD, VALID_TOKEN).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void leRefusAntiRobotNeRevelePasLeMotifExact() {
        UserAccount account = persistUser();
        for (int attempt = 1; attempt <= 3; attempt++) {
            login(account.getEmail(), "mauvais-mot-de-passe", null);
        }

        ResponseEntity<Map<String, Object>> refused =
                login(account.getEmail(), PASSWORD, "jeton-inconnu");

        // Le motif renvoyé par le fournisseur indiquerait à un robot ce
        // qu'il doit corriger : il reste côté serveur.
        assertThat(refused.getBody().get("message").toString())
                .doesNotContain("invalid-input-response");
    }

    @Test
    void laDemandeDeReinitialisationEstProtegeeSystematiquement() {
        ResponseEntity<Map<String, Object>> refused = rest.exchange(
                RequestEntity.post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", "inconnu-" + UUID.randomUUID() + "@esic-connect.test")),
                mapType());

        // Formulaire public : le contrôle s'applique dès la première
        // demande, sans attendre un comportement suspect (docs/02 §17.9).
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody().get("code")).isEqualTo("CAPTCHA_REJECTED");
    }

    @Test
    void laDemandeDeReinitialisationAboutitAvecUnJetonValide() {
        Map<String, Object> body = new HashMap<>();
        body.put("email", "inconnu-" + UUID.randomUUID() + "@esic-connect.test");
        body.put("captchaToken", VALID_TOKEN);

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                RequestEntity.post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON).body(body),
                mapType());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    private ResponseEntity<Map<String, Object>> login(String email, String password, String captcha) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("captchaToken", captcha);
        return rest.exchange(
                RequestEntity.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).body(body),
                mapType());
    }

    private UserAccount persistUser() {
        UserAccount account = new UserAccount("captcha-" + UUID.randomUUID() + "@esic-connect.test",
                "Prenom", "Nom", AccountStatus.ACTIVE);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return userAccountRepository.saveAndFlush(account);
    }

    private static ParameterizedTypeReference<Map<String, Object>> mapType() {
        return new ParameterizedTypeReference<>() {
        };
    }
}
