package com.esic.connect.identity;

/** Actions du cycle de vie d'un compte tracées par l'audit (docs/02 §30.1). */
public enum AccountLifecycleAction {
    /** Compte créé en attente d'activation (EF-USER-001). */
    ACCOUNT_CREATED,
    INVITATION_ISSUED,
    ACCOUNT_ACTIVATED,
    ACCOUNT_SUSPENDED,
    ACCOUNT_REACTIVATED,
    ACCOUNT_ARCHIVED,
    ROLE_ASSIGNED,
    ROLE_REVOKED
}
