package com.esic.connect.identity.internal;

/** Nature d'un défi de second facteur ouvert à la connexion. */
public enum MfaChallengePurpose {
    /** Le compte a un facteur actif : un code TOTP ou de récupération est attendu. */
    VERIFY,
    /**
     * Le compte détient un rôle pour lequel le second facteur est
     * obligatoire mais n'en a pas encore : il doit enrôler un facteur
     * avant d'obtenir un jeton d'accès (RG-007, AC-021).
     */
    ENROLL,
    /**
     * Réauthentification exigée avant une action critique alors qu'une
     * session est déjà ouverte (EF-AUTH-015, RG-009).
     */
    STEP_UP
}
