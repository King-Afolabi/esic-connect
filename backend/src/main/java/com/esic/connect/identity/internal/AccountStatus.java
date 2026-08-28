package com.esic.connect.identity.internal;

/** Statuts de compte (docs/02-cahier-des-charges.md §9.4). */
public enum AccountStatus {
    PENDING_ACTIVATION,
    ACTIVE,
    SUSPENDED,
    LOCKED,
    ARCHIVED
}
