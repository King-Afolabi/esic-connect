package com.esic.connect.identity.internal;

/** Cycle de vie d'un second facteur TOTP (migration V18). */
public enum MfaCredentialStatus {
    /** Secret généré, pas encore confirmé par un premier code valide. */
    PENDING,
    /** Facteur confirmé et exigible à la connexion. */
    ACTIVE,
    /** Facteur retiré ; conservé pour l'audit, plus jamais accepté. */
    REVOKED
}
