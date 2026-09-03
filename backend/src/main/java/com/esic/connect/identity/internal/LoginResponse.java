package com.esic.connect.identity.internal;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Résultat d'une tentative de connexion.
 *
 * <p>Deux issues possibles depuis le sprint 2 :
 * <ul>
 *   <li>la connexion aboutit — {@code accessToken} est renseigné ;</li>
 *   <li>un second facteur est exigé (RG-007, AC-021) — {@code accessToken}
 *       est absent, {@code mfa} porte le défi à résoudre.</li>
 * </ul>
 *
 * <p>Les champs nuls sont omis de la représentation JSON : un client qui
 * reçoit {@code accessToken} sait qu'il est authentifié, sans avoir à
 * inspecter un drapeau supplémentaire.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        String accessToken,
        String tokenType,
        Long expiresInSeconds,
        MfaChallengeResponse mfa) {

    public static LoginResponse authenticated(String accessToken, long expiresInSeconds) {
        return new LoginResponse(accessToken, "Bearer", expiresInSeconds, null);
    }

    public static LoginResponse challenge(MfaChallengeResponse challenge) {
        return new LoginResponse(null, null, null, challenge);
    }

    /**
     * Défi de second facteur.
     *
     * @param challengeId identifiant opaque à renvoyer à
     *                    {@code /auth/mfa/verify} ou {@code /auth/mfa/enroll}
     * @param purpose     {@code VERIFY} si un facteur actif existe,
     *                    {@code ENROLL} si le compte doit d'abord en créer un
     * @param expiresInSeconds durée de vie du défi
     */
    public record MfaChallengeResponse(String challengeId, String purpose, long expiresInSeconds) {
    }
}
