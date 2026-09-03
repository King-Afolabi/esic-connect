package com.esic.connect.identity.internal;

/** Cycle de vie d'une passkey (migration V18). */
public enum WebAuthnCredentialStatus {
    ACTIVE,
    /** Révoquée individuellement (RG-008) ; conservée pour l'audit. */
    REVOKED
}
