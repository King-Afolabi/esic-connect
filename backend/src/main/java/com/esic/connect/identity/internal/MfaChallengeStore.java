package com.esic.connect.identity.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Défi de second facteur émis entre la vérification du mot de passe et
 * l'émission du jeton d'accès (EF-AUTH-008, docs/02 §17.3).
 *
 * <p>Un défi est une donnée <strong>temporaire</strong> : il vit dans
 * Redis, jamais en base (docs/02 §24.1). Il porte l'identifiant public du
 * compte dont le mot de passe vient d'être vérifié, et rien d'autre —
 * ni adresse, ni rôle, ni mot de passe.
 *
 * <p><strong>Indisponibilité de Redis.</strong> Contrairement à la
 * limitation de débit, ce composant porte l'<em>autorité</em> de la
 * décision : sans défi vérifiable, aucun jeton ne peut être émis. Une
 * panne Redis produit donc un refus, jamais un contournement
 * (docs/02 §24.5).
 */
@Component
public class MfaChallengeStore {

    private static final String KEY_PREFIX = "esic:mfa-challenge:";

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Duration ttl;

    public MfaChallengeStore(StringRedisTemplate redis,
                             @Value("${app.security.mfa.challenge-ttl:PT5M}") Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalStateException(
                    "app.security.mfa.challenge-ttl doit être une durée strictement positive.");
        }
        this.redis = redis;
        this.ttl = ttl;
    }

    /**
     * Ouvre un défi pour un compte et renvoie son identifiant opaque.
     *
     * @param purpose distingue une vérification d'un enrôlement forcé :
     *                un défi ouvert pour enrôler ne permet pas de valider
     *                un facteur déjà actif, et réciproquement
     */
    public String open(UUID userPublicId, MfaChallengePurpose purpose) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String challengeId = encoder.encodeToString(raw);
        try {
            redis.opsForValue().set(KEY_PREFIX + challengeId,
                    purpose.name() + "\n" + userPublicId, ttl);
        } catch (DataAccessException e) {
            throw new MfaBackendUnavailableException(e);
        }
        return challengeId;
    }

    /** Lit un défi sans le consommer. */
    public Optional<MfaChallenge> peek(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            return Optional.empty();
        }
        String payload;
        try {
            payload = redis.opsForValue().get(KEY_PREFIX + challengeId);
        } catch (DataAccessException e) {
            throw new MfaBackendUnavailableException(e);
        }
        if (payload == null) {
            return Optional.empty();
        }
        String[] parts = payload.split("\n", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(new MfaChallenge(
                    MfaChallengePurpose.valueOf(parts[0]), UUID.fromString(parts[1])));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /** Supprime un défi : il ne sert qu'une fois, réussite comme échec définitif. */
    public void close(String challengeId) {
        try {
            redis.delete(KEY_PREFIX + challengeId);
        } catch (DataAccessException ignored) {
            // Le défi expirera de lui-même ; échouer ici priverait
            // l'utilisateur d'un jeton pourtant légitimement obtenu.
        }
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    /** Défi ouvert : à quoi il sert, et pour quel compte. */
    public record MfaChallenge(MfaChallengePurpose purpose, UUID userPublicId) {
    }
}
