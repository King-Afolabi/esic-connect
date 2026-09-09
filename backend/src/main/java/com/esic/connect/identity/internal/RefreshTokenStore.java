package com.esic.connect.identity.internal;

import com.esic.connect.shared.ratelimit.IdentityHashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Jetons de renouvellement de session, stockés dans Redis (docs/02 §17.7,
 * §24.1 — un jeton est une donnée temporaire, jamais en base).
 *
 * <p><strong>Une seule clé par session</strong> :
 * {@code esic:auth:refresh:{familyId}}. Elle porte l'empreinte du secret
 * courant et rien d'identifiant en clair. Le cookie remis au navigateur
 * vaut {@code {familyId}.{secret}} ; seul {@code SHA-256(secret)} est
 * conservé, comme pour les jetons d'invitation et de réinitialisation.
 *
 * <p><strong>Rotation.</strong> Chaque renouvellement réécrit un nouveau
 * secret dans la même famille. Un cookie présenté dont le secret ne
 * correspond plus à l'empreinte enregistrée est traité comme un rejeu :
 * la famille entière est supprimée et l'appel échoue. Deux onglets qui
 * renouvellent en même temps peuvent donc s'auto-évincer — cas rare,
 * rattrapé par une reconnexion, et préférable à un cookie volé
 * réutilisable.
 *
 * <p><strong>Deux bornes de temps.</strong> La durée de vie Redis de la
 * clé est l'<em>inactivité</em> (glissante : repoussée à chaque
 * renouvellement). Un <em>plafond absolu</em> est inscrit dans la valeur
 * et vérifié à chaque renouvellement : au-delà, aucun renouvellement,
 * quelle que soit l'activité.
 *
 * <p><strong>Indisponibilité de Redis.</strong> À l'émission, l'échec est
 * silencieux (la connexion réussit, sans cookie de renouvellement — le
 * jeton d'accès reste valable le temps de sa courte durée de vie). Au
 * renouvellement, l'échec est un refus ({@link RefreshTokenException}) :
 * aucune session n'est rétablie sur la foi d'un magasin injoignable.
 */
@Component
class RefreshTokenStore {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenStore.class);

    private static final String KEY_PREFIX = "esic:auth:refresh:";
    private static final int FAMILY_ID_BYTES = 18;
    private static final int SECRET_BYTES = 32;
    /** Séparateur cookie {@code familyId.secret} ; ni l'un ni l'autre encodage ne le contient. */
    private static final char COOKIE_SEPARATOR = '.';
    private static final String FIELD_SEPARATOR = "\n";

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final Duration idleTtl;
    private final Duration absoluteTtl;
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    RefreshTokenStore(StringRedisTemplate redis,
                      Clock clock,
                      @Value("${app.security.jwt.refresh-token-idle-ttl:PT30M}") Duration idleTtl,
                      @Value("${app.security.jwt.refresh-token-absolute-ttl:PT12H}") Duration absoluteTtl) {
        if (idleTtl == null || idleTtl.isZero() || idleTtl.isNegative()) {
            throw new IllegalStateException(
                    "app.security.jwt.refresh-token-idle-ttl doit être une durée strictement positive.");
        }
        if (absoluteTtl == null || absoluteTtl.compareTo(idleTtl) < 0) {
            throw new IllegalStateException(
                    "app.security.jwt.refresh-token-absolute-ttl doit être au moins égale à "
                            + "refresh-token-idle-ttl.");
        }
        this.redis = redis;
        this.clock = clock;
        this.idleTtl = idleTtl;
        this.absoluteTtl = absoluteTtl;
    }

    Duration idleTtl() {
        return idleTtl;
    }

    /**
     * Ouvre une nouvelle famille pour un compte et renvoie la valeur du
     * cookie à remettre au navigateur.
     *
     * @param amr moyens d'authentification employés, repris tels quels
     *            dans le jeton d'accès à chaque renouvellement (EF-AUTH-015)
     */
    Issued issue(UUID userPublicId, List<String> amr, String deviceFingerprint) {
        String familyId = randomToken(FAMILY_ID_BYTES);
        String secret = randomToken(SECRET_BYTES);
        Instant now = clock.instant();
        String value = String.join(FIELD_SEPARATOR,
                userPublicId.toString(),
                sha256(secret),
                Long.toString(now.getEpochSecond()),
                Long.toString(now.plus(absoluteTtl).getEpochSecond()),
                deviceHash(deviceFingerprint),
                String.join(",", amr == null ? List.of() : amr));
        redis.opsForValue().set(KEY_PREFIX + familyId, value, idleTtl);
        return new Issued(familyId + COOKIE_SEPARATOR + secret, idleTtl);
    }

    /**
     * Consomme un cookie, contrôle et fait tourner la famille.
     *
     * @throws RefreshTokenException cookie illisible, famille absente,
     *                               secret ou appareil incohérent, plafond
     *                               absolu atteint, ou Redis injoignable
     */
    Rotated rotate(String cookieValue, String deviceFingerprint) {
        Parsed parsed = parse(cookieValue);
        String key = KEY_PREFIX + parsed.familyId();

        Family family;
        try {
            family = readFamily(redis.opsForValue().get(key));
        } catch (DataAccessException redisFailure) {
            log.warn("Renouvellement impossible, magasin de jetons injoignable : {}",
                    redisFailure.getClass().getSimpleName());
            throw new RefreshTokenException();
        }
        if (family == null) {
            throw new RefreshTokenException();
        }

        boolean secretMatches = MessageDigest.isEqual(
                sha256(parsed.secret()).getBytes(StandardCharsets.UTF_8),
                family.secretHash().getBytes(StandardCharsets.UTF_8));
        if (!secretMatches) {
            // Rejeu d'un secret périmé (rotation déjà passée) ou cookie
            // forgé : on coupe toute la famille, pas seulement cette
            // présentation.
            deleteQuietly(key);
            throw new RefreshTokenException();
        }

        Instant now = clock.instant();
        if (!now.isBefore(family.absoluteExpiry())) {
            deleteQuietly(key);
            throw new RefreshTokenException();
        }
        if (deviceMismatch(family.deviceHash(), deviceFingerprint)) {
            deleteQuietly(key);
            throw new RefreshTokenException();
        }

        String newSecret = randomToken(SECRET_BYTES);
        String rotatedValue = String.join(FIELD_SEPARATOR,
                family.userPublicId().toString(),
                sha256(newSecret),
                Long.toString(family.issuedAt().getEpochSecond()),
                Long.toString(family.absoluteExpiry().getEpochSecond()),
                family.deviceHash(),
                String.join(",", family.amr()));
        try {
            // Durée d'inactivité repoussée ; le plafond absolu, lui, reste
            // celui de l'ouverture — il est dans la valeur, pas dans le TTL.
            redis.opsForValue().set(key, rotatedValue, idleTtl);
        } catch (DataAccessException redisFailure) {
            log.warn("Rotation du jeton de renouvellement impossible : {}",
                    redisFailure.getClass().getSimpleName());
            throw new RefreshTokenException();
        }
        return new Rotated(family.userPublicId(), family.amr(), family.issuedAt(),
                parsed.familyId() + COOKIE_SEPARATOR + newSecret, idleTtl);
    }

    /** Supprime une famille — déconnexion, ou révocation constatée ailleurs. Toujours silencieux. */
    void revoke(String cookieValue) {
        if (cookieValue == null || cookieValue.isBlank()) {
            return;
        }
        int separator = cookieValue.indexOf(COOKIE_SEPARATOR);
        String familyId = separator > 0 ? cookieValue.substring(0, separator) : cookieValue;
        deleteQuietly(KEY_PREFIX + familyId);
    }

    private void deleteQuietly(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException ignored) {
            // La clé expirera d'elle-même ; échouer ici n'apporterait rien.
        }
    }

    private Parsed parse(String cookieValue) {
        if (cookieValue == null || cookieValue.isBlank()) {
            throw new RefreshTokenException();
        }
        int separator = cookieValue.indexOf(COOKIE_SEPARATOR);
        if (separator <= 0 || separator == cookieValue.length() - 1) {
            throw new RefreshTokenException();
        }
        return new Parsed(cookieValue.substring(0, separator), cookieValue.substring(separator + 1));
    }

    private Family readFamily(String payload) {
        if (payload == null) {
            return null;
        }
        String[] parts = payload.split(FIELD_SEPARATOR, -1);
        if (parts.length != 6) {
            return null;
        }
        try {
            List<String> amr = parts[5].isEmpty() ? List.of() : List.of(parts[5].split(","));
            return new Family(UUID.fromString(parts[0]), parts[1],
                    Instant.ofEpochSecond(Long.parseLong(parts[2])),
                    Instant.ofEpochSecond(Long.parseLong(parts[3])),
                    parts[4], amr);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private boolean deviceMismatch(String storedHash, String presentedFingerprint) {
        if (storedHash == null || storedHash.isEmpty() || "anonymous".equals(storedHash)) {
            return false;
        }
        String presented = deviceHash(presentedFingerprint);
        if (presented.isEmpty() || "anonymous".equals(presented)) {
            return false;
        }
        return !storedHash.equals(presented);
    }

    private String deviceHash(String deviceFingerprint) {
        if (deviceFingerprint == null || deviceFingerprint.isBlank()) {
            return "";
        }
        return IdentityHashing.of(deviceFingerprint);
    }

    private String randomToken(int bytes) {
        byte[] raw = new byte[bytes];
        random.nextBytes(raw);
        return encoder.encodeToString(raw);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /** Cookie fraîchement émis et durée de vie d'inactivité à porter sur le {@code Set-Cookie}. */
    record Issued(String cookieValue, Duration idleTtl) {
    }

    /**
     * Résultat d'une rotation réussie.
     *
     * @param familyIssuedAt instant d'ouverture de la famille — comparé à
     *                       {@code credentials_invalidated_at} du compte
     *                       pour appliquer une révocation globale
     */
    record Rotated(UUID userPublicId, List<String> amr, Instant familyIssuedAt,
                   String cookieValue, Duration idleTtl) {
    }

    private record Parsed(String familyId, String secret) {
    }

    private record Family(UUID userPublicId, String secretHash, Instant issuedAt,
                          Instant absoluteExpiry, String deviceHash, List<String> amr) {
    }
}
