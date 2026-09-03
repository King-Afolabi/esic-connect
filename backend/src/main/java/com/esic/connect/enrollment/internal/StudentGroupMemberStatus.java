package com.esic.connect.enrollment.internal;

/** Appartenance à un groupe temporaire (migration V19). */
public enum StudentGroupMemberStatus {
    ACTIVE,
    /** Retiré du groupe ; la ligne reste pour l'historique. */
    REMOVED
}
