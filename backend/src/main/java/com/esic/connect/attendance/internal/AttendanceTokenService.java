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
 * <p>V10 : le jeton est émis <em>pour un point de contrôle</em> précis
 * d'une séance (plusieurs points de contrôle par séance). Le payload du
 * jeton porte {@code sessionPublicId\ncheckpointPublicId} ; le pointeur
 * courant d'autorité reste indexé par la <em>séance</em>
 * ({@code esic:attendance:session:{sessionId} -> token\ncode\ncheckpointId}) :
 * un seul point de contrôle est actif à l'émargement à un instant donné
 * (émettre un jeton pour un autre point de contrôle fait tourner le
 * précédent).
 *
 * <p>Le jeton opaque est tiré de {@link SecureRandom} (32 octets), encodé
 * URL-safe sans remplissage ; jamais dérivé d'un identifiant public,
 * jamais journalisé, jamais placé dans une URL. Le code court (8
 * caractères d'un alphabet sans caractères ambigus) partage la même
 * autorité et le même TTL.
 *
 * <p><strong>Rotation</strong> : émettre un nouveau couple invalide
 * immédiatement le couple précédent de la séance (suppression des clés
 * + bascule du pointeur). <strong>Fermeture</strong> :
 * {@link #invalidateSession} (fermeture de séance) et
 * {@link #invalidateCheckpoint} (fermeture / annulation d'un point de
 * contrôle) suppriment les clés connues ; au-delà, le TTL garantit
 * l'expiration.
 *
 * <p><strong>Indisponibilité de Redis</strong> : toute lecture / écriture
 * qui échoue lève {@link AttendanceException.Kind#TOKEN_BACKEND_UNAVAILABLE}
 * (503) — aucun repli en mémoire, aucune validation dégradée. Seules
 * {@link #invalidateSession} / {@link #invalidateCheckpoint} avalent
 * l'échec (les jetons expireront par TTL).
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
     * Émet un nouveau couple (jeton opaque, code court) pour un point de
     * contrôle d'une séance, en invalidant le couple précédent de la
     * séance le cas échéant.
     *
     * <p>Ordre volontaire : on écrit d'abord le nouveau couple, puis on
     * bascule le pointeur courant, puis seulement on supprime les clés de
     * l'ancien couple. Le pointeur est l'unique autorité
     * ({@link #resolve} n'accepte que le jeton exactement égal au jeton
     * pointé) : dès qu'il bascule, un ancien jeton résiduel devient
     * inutilisable même si sa suppression échoue ou tarde.
     */
    IssuedAttendanceToken issue(UUID sessionPublicId, UUID checkpointPublicId) {
        try {
            String sessionKey = SESSION_KEY_PREFIX + sessionPublicId;
            String previous = redis.opsForValue().get(sessionKey);

            String token = newToken();
            String shortCode = newShortCode();
            String tokenPayload = sessionPublicId + "\n" + checkpointPublicId;
            redis.opsForValue().set(TOKEN_KEY_PREFIX + token, tokenPayload, ttl);
            redis.opsForValue().set(CODE_KEY_PREFIX + shortCode, token, ttl);
            // Bascule du pointeur courant : à partir d'ici l'ancien couple
            // n'est plus « courant » et sera refusé à la résolution.
            redis.opsForValue().set(sessionKey, token + "\n" + shortCode + "\n" + checkpointPublicId, ttl);

            deletePreviousPair(previous);

            Instant expiresAt = clock.instant().plus(ttl);
            return new IssuedAttendanceToken(token, shortCode, expiresAt, sessionPublicId, checkpointPublicId);
        } catch (DataAccessException redisDown) {
            throw new AttendanceException(AttendanceException.Kind.TOKEN_BACKEND_UNAVAILABLE);
        }
    }

    /**
     * Résout un jeton opaque <em>ou</em> un code court en
     * {@link ResolvedAttendanceToken} (séance + point de contrôle).
     * {@link Optional#empty()} = inconnu / expiré / invalidé /
     * <strong>plus courant</strong>.
     *
     * <p>Invariant : la clé {@code token -> payload} ne suffit jamais à
     * elle seule. La preuve n'est acceptée que si le pointeur courant de
     * la séance existe, est cohérent (exactement trois segments non
     * vides : {@code token\ncode\ncheckpointId}) et désigne exactement le
     * jeton résolu (et, si un code court a été présenté, exactement ce
     * code court).
     */
    Optional<ResolvedAttendanceToken> resolve(String token, String shortCode) {
        try {
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
            String[] payloadParts = payload.split("\n", 2);
            if (payloadParts.length != 2 || payloadParts[0].isEmpty() || payloadParts[1].isEmpty()) {
                return Optional.empty();
            }
            UUID sessionPublicId;
            UUID checkpointPublicId;
            try {
                sessionPublicId = UUID.fromString(payloadParts[0]);
                checkpointPublicId = UUID.fromString(payloadParts[1]);
            } catch (IllegalArgumentException corrupted) {
                return Optional.empty();
            }

            String current = redis.opsForValue().get(SESSION_KEY_PREFIX + sessionPublicId);
            if (current == null) {
                return Optional.empty();
            }
            String[] parts = current.split("\n", 3);
            if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
                return Optional.empty();
            }
            String currentToken = parts[0];
            String currentShortCode = parts[1];

            if (!currentToken.equals(effectiveToken)) {
                return Optional.empty();
            }
            if (token != null && !currentToken.equals(token)) {
                return Optional.empty();
            }
            if (shortCode != null && !currentShortCode.equals(shortCode)) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedAttendanceToken(sessionPublicId, checkpointPublicId));
        } catch (DataAccessException redisDown) {
            throw new AttendanceException(AttendanceException.Kind.TOKEN_BACKEND_UNAVAILABLE);
        }
    }

    /**
     * Supprime les clés Redis connues de la séance (fermeture de séance).
     * Un échec Redis est journalisé sans être propagé.
     */
    void invalidateSession(UUID sessionPublicId) {
        try {
            String sessionKey = SESSION_KEY_PREFIX + sessionPublicId;
            deletePreviousPair(redis.opsForValue().get(sessionKey));
            redis.delete(sessionKey);
        } catch (DataAccessException redisDown) {
            log.warn("Invalidation Redis des jetons d'émargement impossible (les jetons expireront par TTL)");
        }
    }

    /**
     * Invalide le jeton courant de la séance <strong>uniquement s'il
     * appartient au point de contrôle indiqué</strong> (fermeture /
     * annulation d'un point de contrôle individuel). Sans effet si le
     * jeton courant concerne un autre point de contrôle. Échec Redis
     * avalé.
     */
    void invalidateCheckpoint(UUID sessionPublicId, UUID checkpointPublicId) {
        try {
            String sessionKey = SESSION_KEY_PREFIX + sessionPublicId;
            String current = redis.opsForValue().get(sessionKey);
            if (current == null) {
                return;
            }
            String[] parts = current.split("\n", 3);
            if (parts.length == 3 && parts[2].equals(checkpointPublicId.toString())) {
                redis.delete(TOKEN_KEY_PREFIX + parts[0]);
                redis.delete(CODE_KEY_PREFIX + parts[1]);
                redis.delete(sessionKey);
            }
        } catch (DataAccessException redisDown) {
            log.warn("Invalidation Redis d'un point de contrôle impossible (les jetons expireront par TTL)");
        }
    }

    private void deletePreviousPair(String pointerValue) {
        if (pointerValue == null) {
            return;
        }
        String[] parts = pointerValue.split("\n", 3);
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            redis.delete(TOKEN_KEY_PREFIX + parts[0]);
            redis.delete(CODE_KEY_PREFIX + parts[1]);
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
        throw new IllegalStateException("Impossible de générer un code court d'émargement unique");
    }
}
