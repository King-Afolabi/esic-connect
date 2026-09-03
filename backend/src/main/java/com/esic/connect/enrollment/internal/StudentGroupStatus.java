package com.esic.connect.enrollment.internal;

/** Cycle de vie d'un groupe temporaire (EF-ACA-007 ; migration V19). */
public enum StudentGroupStatus {
    ACTIVE,
    INACTIVE,
    /** Archivé : conservé pour l'historique, jamais réutilisable tel quel. */
    ARCHIVED
}
