package com.esic.connect.attendance.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Émission, rotation, résolution et invalidation des capacités
 * d'émargement — <strong>exclusivement dans Redis</strong>, jamais en
 * base MySQL.
 *
 * <p>Le jeton opaque est tiré de {@link SecureRandom} (32 octets, 256
 * bits d'entropie), encodé URL-safe sans remplissage ; il n'est jamais
 * dérivé d'un identifiant public, jamais journalisé, jamais placé dans
 * une URL. Le code court (8 caractères d'un alphabet sans caractères
 * ambigus) partage la même autorité et le même TTL ; il n'est pas un
 * fragment prévisible du jeton.
 *
 * <p><strong>Rotation</strong> : émettre un nouveau couple invalide
 * immédiatement le couple précédent de la séance (suppression des clés).
 * <strong>Fermeture</strong> : {@link #invalidateSession} supprime toutes
 * les clés connues de la séance ; au-delà, le TTL garantit de toute façon
 * l'expiration.
 *
 * <p><strong>Indisponibilité de Redis</strong> : toute opération de
 * lecture / écriture qui échoue lève
 * {@link AttendanceException.Kind#TOKEN_BACKEND_UNAVAILABLE} (503) — aucun
 * repli en mémoire, aucune validation dégradée. Seule
 * {@link #invalidateSession} (déclenchée par l'événement de fermeture)
 * avale l'échec et se contente de journaliser : les jetons expireront par
 * TTL.
 */
@Service
class AttendanceTokenService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceTokenService.class);

    private static final String TOKEN_KEY_PREFIX = "esic:attendance:token:";
    private static final String CODE_KEY_PREFIX = "esic:attendance:code:";
    private static final String SESSION_KEY_PREFIX = "esic:attendance:session:";

    private static final int TOKEN_BYTES = 32;
    private static final int SHORT_CODE_LENGTH = 8;
    // Alphabet sans 0/O/1/I/L pour limiter les erreurs de saisie manuelle.
    private static final char[] SHORT_CODE_ALPHABET =
            "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int SHORT_CODE_MAX_ATTEMPTS = 6;

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder tokenEncoder = Base64.getUrlEncoder().withoutPadding();

    AttendanceTokenService(StringRedisTemplate redis, Clock clock,
                           @Value("${app.attendance.token-ttl:PT30S}") Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalStateException(
                    "app.attendance.token-ttl doit être une durée strictement positive.");
        }
        this.redis = redis;
        this.clock = clock;
        this.ttl = ttl;
    }

    Duration ttl() {
        return ttl;
    }

    /**
     * Émet un nouveau couple (jeton opaque, code court) pour la séance,
     * en invalidant le couple précédent le cas échéant.
     *
     * <p>Ordre volontaire : on écrit d'abord le nouveau couple, puis on
     * bascule le pointeur courant {@code session -> token\ncode}, puis
     * seulement on supprime les clés de l'ancien couple. Le pointeur est
     * l'unique référence d'autorité ({@link #resolveSession} n'accepte que
     * le jeton exactement égal au jeton pointé) : dès qu'il bascule, un
     * ancien jeton résiduel devient inutilisable, même si sa suppression
     * échoue ou tarde.
     */
    IssuedAttendanceToken issue(UUID sessionPublicId) {
        try {
            String sessionKey = SESSION_KEY_PREFIX + sessionPublicId;
            String previous = redis.opsForValue().get(sessionKey);

            String token = newToken();
            String shortCode = newShortCode();
            String payload = sessionPublicId.toString();
            redis.opsForValue().set(TOKEN_KEY_PREFIX + token, payload, ttl);
            redis.opsForValue().set(CODE_KEY_PREFIX + shortCode, token, ttl);
            // Bascule du pointeur courant : à partir d'ici l'ancien couple
            // n'est plus « courant » et sera refusé à la résolution.
            redis.opsForValue().set(sessionKey, token + "\n" + shortCode, ttl);

            if (previous != null) {
                String[] parts = previous.split("\n", 2);
                if (parts.length == 2) {
                    redis.delete(TOKEN_KEY_PREFIX + parts[0]);
                    redis.delete(CODE_KEY_PREFIX + parts[1]);
                }
            }

            Instant expiresAt = clock.instant().plus(ttl);
            return new IssuedAttendanceToken(token, shortCode, expiresAt, sessionPublicId);
        } catch (DataAccessException redisDown) {
            throw new AttendanceException(AttendanceException.Kind.TOKEN_BACKEND_UNAVAILABLE);
        }
    }

    /**
     * Résout un jeton opaque <em>ou</em> un code court en identifiant
     * public de séance. {@link Optional#empty()} = inconnu / expiré /
     * invalidé / <strong>plus courant</strong>.
     *
     * <p>Invariant : la clé {@code token -> session} ne suffit jamais à
     * elle seule. Une clé résiduelle (rotation concurrente ou partiellement
     * échouée) doit être refusée. La preuve n'est acceptée que si le
     * pointeur courant de la séance ({@code session -> token\ncode})
     * <em>existe</em>, est <em>cohérent</em> (exactement deux segments) et
     * <em>désigne exactement</em> le jeton résolu (et, si un code court a
     * été présenté, exactement ce code court). Pointeur absent, illisible,
     * incohérent ou divergent ⇒ {@link Optional#empty()}.
     */
    Optional<UUID> resolveSession(String token, String shortCode) {
        try {
            // 1. token -> session (directement, ou via le code court).
            String effectiveToken = token;
            if (effectiveToken == null) {
                if (shortCode == null) {
                    return Optional.empty();
                }
                effectiveToken = redis.opsForValue().get(CODE_KEY_PREFIX + shortCode);
                if (effectiveToken == null) {
                    return Optional.empty();
                }
            }
            String payload = redis.opsForValue().get(TOKEN_KEY_PREFIX + effectiveToken);
            if (payload == null) {
                return Optional.empty();
            }
            UUID sessionPublicId;
            try {
                sessionPublicId = UUID.fromString(payload);
            } catch (IllegalArgumentException corrupted) {
                return Optional.empty();
            }

            // 2. Le pointeur courant de la séance fait autorité.
            String current = redis.opsForValue().get(SESSION_KEY_PREFIX + sessionPublicId);
            if (current == null) {
                return Optional.empty();
            }
            String[] parts = current.split("\n", 2);
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                return Optional.empty();
            }
            String currentToken = parts[0];
            String currentShortCode = parts[1];

            // 3. Le jeton résolu doit être exactement le jeton courant ;
            //    un code court présenté doit être exactement le code courant.
            if (!currentToken.equals(effectiveToken)) {
                return Optional.empty();
            }
            if (token != null && !currentToken.equals(token)) {
                return Optional.empty();
            }
            if (shortCode != null && !currentShortCode.equals(shortCode)) {
                return Optional.empty();
            }
            return Optional.of(sessionPublicId);
        } catch (DataAccessException redisDown) {
            throw new AttendanceException(AttendanceException.Kind.TOKEN_BACKEND_UNAVAILABLE);
        }
    }

    /**
     * Supprime toutes les clés Redis connues de la séance (fermeture).
     * Un échec Redis est journalisé sans être propagé : le TTL fera
     * expirer les clés.
     */
    void invalidateSession(UUID sessionPublicId) {
        try {
            String sessionKey = SESSION_KEY_PREFIX + sessionPublicId;
            String current = redis.opsForValue().get(sessionKey);
            if (current != null) {
                String[] parts = current.split("\n", 2);
                if (parts.length == 2) {
                    redis.delete(TOKEN_KEY_PREFIX + parts[0]);
                    redis.delete(CODE_KEY_PREFIX + parts[1]);
                }
            }
            redis.delete(sessionKey);
        } catch (DataAccessException redisDown) {
            log.warn("Invalidation Redis des jetons d'émargement impossible (les jetons expireront par TTL)");
        }
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return tokenEncoder.encodeToString(bytes);
    }

    private String newShortCode() {
        for (int attempt = 0; attempt < SHORT_CODE_MAX_ATTEMPTS; attempt++) {
            StringBuilder builder = new StringBuilder(SHORT_CODE_LENGTH);
            for (int i = 0; i < SHORT_CODE_LENGTH; i++) {
                builder.append(SHORT_CODE_ALPHABET[random.nextInt(SHORT_CODE_ALPHABET.length)]);
            }
            String candidate = builder.toString();
            Boolean exists = redis.hasKey(CODE_KEY_PREFIX + candidate);
            if (exists == null || !exists) {
                return candidate;
            }
        }
        // Collision improbable répétée : incident interne, jamais renvoyé au client tel quel.
        throw new IllegalStateException("Impossible de générer un code court d'émargement unique");
    }
}
