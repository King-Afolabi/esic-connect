package com.esic.connect.planning.internal;

/**
 * Statut d'une {@code planning_version} (docs/02 §13.6 ; DEC-G1-004).
 *
 * <pre>{@code DRAFT → PUBLISHED → SUPERSEDED}</pre>
 */
enum PlanningVersionStatus {
    DRAFT,
    PUBLISHED,
    SUPERSEDED
}
