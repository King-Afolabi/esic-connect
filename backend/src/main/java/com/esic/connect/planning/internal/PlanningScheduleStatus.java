package com.esic.connect.planning.internal;

/** Statut d'un {@code planning_schedule} (docs/02 §13.6, réduit à G1-B). */
enum PlanningScheduleStatus {
    /** Créé, aucune version publiée. */
    DRAFT,
    /** Au moins une version publiée. */
    ACTIVE,
    /** Retiré de l'usage courant ; historique conservé. */
    ARCHIVED
}
