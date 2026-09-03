package com.esic.connect.identity.internal;

/** Cycle de vie d'un code de récupération (migration V18). */
public enum MfaRecoveryCodeStatus {
    ACTIVE,
    /** Consommé : un code de récupération ne sert qu'une fois. */
    CONSUMED,
    /** Invalidé en bloc, par exemple à la régénération de la série. */
    REVOKED
}
