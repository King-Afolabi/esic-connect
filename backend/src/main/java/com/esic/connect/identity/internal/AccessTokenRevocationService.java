package com.esic.connect.identity.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Révocation d'un jeton d'accès individuel — la déconnexion
 * (EF-AUTH-014, docs/02 §17.7).
 *
 * <p>L'API est sans état : un JWT reste cryptographiquement valide
 * jusqu'à son expiration. Une déconnexion doit donc inscrire son
 * identifiant ({@code jti}) sur une liste de refus. Cette liste vit dans
 * Redis, avec une durée de vie exactement égale au temps restant du
 * jeton : elle ne grossit jamais indéfiniment et se purge seule.
 *
 * <p>La révocation <em>globale</em> d'un compte (réinitialisation de mot
 * de passe, suspension) n'utilise pas ce mécanisme : elle passe par
 * {@code user_account.credentials_invalidated_at}, qui couvre tous les
 * jetons d'un coup, y compris ceux dont le {@code jti} est inconnu.
 *
 * <p><strong>Indisponibilité de Redis.</strong> Contrairement à la
 * limitation de débit, l'échec est ici signalé à l'appelant : une
 * déconnexion qui ne révoque rien mais répond « c'est fait » serait
 * mensongère sur un point de sécurité.
 */
@Service
public class AccessTokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(AccessTokenRevocationService.class);

    private static final String KEY_PREFIX = "esic:auth:revoked-jti:";
    private static final String VALUE = "1";

    private final StringRedisTemplate redisTemplate;

    AccessTokenRevocationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Inscrit un jeton sur la liste de refus jusqu'à son expiration.
     *
     * @param jti       identifiant du jeton
     * @param expiresAt expiration portée par le jeton
     * @param now       instant courant
     * @throws AuthException si Redis est indisponible
     */
    void revoke(String jti, Instant expiresAt, Instant now) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        Duration remaining = Duration.between(now, expiresAt);
        if (remaining.isZero() || remaining.isNegative()) {
            // Déjà expiré : l'inscrire ne servirait à rien.
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, VALUE, remaining);
        } catch (DataAccessException redisFailure) {
            log.error("Révocation de jeton impossible : {}", redisFailure.getClass().getSimpleName());
            throw AuthException.revocationBackendUnavailable();
        }
    }

    /**
     * Indique si un jeton a été révoqué.
     *
     * <p>Appelé à chaque requête authentifiée. Si Redis ne répond pas, la
     * réponse est {@code false} : la liste de refus ne couvre que les
     * déconnexions explicites, et une panne de Redis ne doit pas
     * invalider toutes les sessions en cours. La révocation qui compte
     * vraiment — celle qui suit un changement de mot de passe — repose
     * sur MySQL et reste appliquée.
     */
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (DataAccessException redisFailure) {
            log.warn("Liste de refus des jetons indisponible : {}", redisFailure.getClass().getSimpleName());
            return false;
        }
    }
}
