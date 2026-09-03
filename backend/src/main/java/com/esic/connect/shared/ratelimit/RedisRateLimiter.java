package com.esic.connect.shared.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Compteur à fenêtre fixe dans Redis.
 *
 * <p>Une clé {@code esic:rate-limit:{bucket}:{identityHash}} porte le
 * nombre de tentatives de la fenêtre courante. La première incrémentation
 * pose le TTL : la fenêtre expire donc d'elle-même, sans tâche de purge.
 *
 * <p><strong>Indisponibilité de Redis — décision DEC-S2-001.</strong> Si
 * Redis ne répond pas, la limitation <em>laisse passer</em> au lieu de
 * bloquer. Refuser fermerait l'authentification à tous les utilisateurs
 * légitimes dès qu'une dépendance de <em>protection</em> tombe : la panne
 * d'un garde-fou deviendrait un déni de service complet, déclenchable de
 * l'extérieur. L'événement est journalisé en {@code WARN} pour être
 * visible en supervision. Les contrôles de fond restent actifs pendant ce
 * temps : hachage BCrypt, réponse uniforme, audit de chaque tentative.
 *
 * <p>Ce choix est l'inverse de celui retenu pour l'émargement, où
 * l'indisponibilité de Redis produit un {@code 503} : là, Redis porte
 * l'<em>autorité</em> de la décision, pas une simple protection.
 */
@Component
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private static final String KEY_PREFIX = "esic:rate-limit:";

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitDecision consume(String bucket, String identityHash, int limit, Duration window) {
        if (limit <= 0) {
            throw new IllegalArgumentException("La limite doit être strictement positive.");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("La fenêtre doit être une durée strictement positive.");
        }

        String key = key(bucket, identityHash);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                return RateLimitDecision.backendUnavailable();
            }
            if (count == 1L) {
                // Première tentative de la fenêtre : c'est elle qui fixe
                // la durée de vie. Un échec de pose de TTL laisserait une
                // clé éternelle, donc on la supprime plutôt que de la
                // laisser bloquer l'identité indéfiniment.
                Boolean expireSet = redisTemplate.expire(key, window);
                if (!Boolean.TRUE.equals(expireSet)) {
                    redisTemplate.delete(key);
                    return RateLimitDecision.backendUnavailable();
                }
            }
            if (count > limit) {
                return RateLimitDecision.denied(remainingWindow(key, window));
            }
            return RateLimitDecision.allowed(limit - count);
        } catch (DataAccessException redisFailure) {
            log.warn("Limitation de débit indisponible (seau {}) : {} — la requête est laissée passer (DEC-S2-001).",
                    bucket, redisFailure.getClass().getSimpleName());
            return RateLimitDecision.backendUnavailable();
        }
    }

    @Override
    public void reset(String bucket, String identityHash) {
        try {
            redisTemplate.delete(key(bucket, identityHash));
        } catch (DataAccessException redisFailure) {
            // Le compteur expirera de lui-même : un échec ici ne doit
            // jamais faire échouer l'opération métier qui vient de
            // réussir.
            log.warn("Remise à zéro du seau {} impossible : {}", bucket, redisFailure.getClass().getSimpleName());
        }
    }

    private Duration remainingWindow(String key, Duration window) {
        Long ttlSeconds = redisTemplate.getExpire(key);
        if (ttlSeconds == null || ttlSeconds < 0) {
            return window;
        }
        return Duration.ofSeconds(ttlSeconds);
    }

    private String key(String bucket, String identityHash) {
        return KEY_PREFIX + bucket + ":" + identityHash;
    }
}
