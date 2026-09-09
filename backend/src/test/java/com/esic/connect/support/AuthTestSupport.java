package com.esic.connect.support;

import com.esic.connect.identity.internal.TotpGenerator;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;

/**
 * Obtention d'un jeton d'accès dans les tests, second facteur compris.
 *
 * <p>Depuis le sprint 2, un compte {@code ADMIN} ou {@code SUPER_ADMIN}
 * ne reçoit plus de jeton contre son seul mot de passe : la connexion
 * s'arrête sur un défi (RG-007, AC-021). Les tests qui ont besoin d'un
 * jeton d'administration doivent donc franchir ce défi, exactement comme
 * un utilisateur réel.
 *
 * <p>Cette classe fait exactement cela et rien de plus : elle
 * n'introduit aucun raccourci, aucune porte dérobée, aucun profil
 * particulier. Un test qui l'utilise exerce le vrai parcours ; un test
 * qui veut vérifier le défi lui-même appelle {@code /auth/login}
 * directement.
 */
public final class AuthTestSupport {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {
            };

    private AuthTestSupport() {
    }

    /**
     * Connecte un compte et renvoie son jeton d'accès, en franchissant le
     * second facteur si la politique l'exige.
     *
     * @throws AssertionError si la connexion échoue ou si le défi ne peut
     *                        pas être résolu — un test doit s'arrêter net,
     *                        pas continuer avec un jeton nul
     */
    public static String accessToken(TestRestTemplate restTemplate, String email, String password) {
        Map<String, Object> body = requireBody(login(restTemplate, email, password),
                "Connexion refusée pour " + email);
        String token = (String) body.get("accessToken");
        if (token != null) {
            return token;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> challenge = (Map<String, Object>) body.get("mfa");
        if (challenge == null) {
            throw new AssertionError("Ni jeton ni défi dans la réponse de connexion : " + body);
        }
        return resolveChallenge(restTemplate, (String) challenge.get("challengeId"),
                (String) challenge.get("purpose"));
    }

    /** Connexion brute, sans franchir le défi : pour les tests qui l'observent. */
    public static ResponseEntity<Map<String, Object>> login(TestRestTemplate restTemplate,
                                                            String email, String password) {
        return restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("email", email, "password", password)),
                MAP);
    }

    /** Code TOTP courant pour un secret d'enrôlement. */
    public static String currentCode(String base32Secret) {
        return TotpGenerator.codeAt(base32Secret, TotpGenerator.stepOf(Instant.now()));
    }

    private static String resolveChallenge(TestRestTemplate restTemplate, String challengeId,
                                           String purpose) {
        if (!"ENROLL".equals(purpose)) {
            throw new AssertionError("Défi de type " + purpose
                    + " : ce compte a déjà un second facteur, le test doit le fournir lui-même.");
        }
        Map<String, Object> enrollment = requireBody(restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/mfa/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("challengeId", challengeId)),
                MAP), "Ouverture d'enrôlement refusée");

        Map<String, Object> confirmation = requireBody(restTemplate.exchange(
                RequestEntity.post("/api/v1/auth/mfa/enroll/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("challengeId", challengeId,
                                "code", currentCode((String) enrollment.get("secret")))),
                MAP), "Confirmation d'enrôlement refusée");

        @SuppressWarnings("unchecked")
        Map<String, Object> session = (Map<String, Object>) confirmation.get("session");
        if (session == null || session.get("accessToken") == null) {
            throw new AssertionError("Aucun jeton après confirmation d'enrôlement : " + confirmation);
        }
        return (String) session.get("accessToken");
    }

    private static Map<String, Object> requireBody(ResponseEntity<Map<String, Object>> response,
                                                   String message) {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new AssertionError(message + " — statut " + response.getStatusCode()
                    + ", corps " + response.getBody());
        }
        return response.getBody();
    }
}
