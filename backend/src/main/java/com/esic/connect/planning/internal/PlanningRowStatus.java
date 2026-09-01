package com.esic.connect.planning.internal;

/** Statut d'une ligne de {@code planning_import_row} (DEC-G1-005). */
enum PlanningRowStatus {
    VALID,
    WARNING,
    /** Anomalie bloquante : la ligne empêche la publication (RG-034). */
    ERROR
}
