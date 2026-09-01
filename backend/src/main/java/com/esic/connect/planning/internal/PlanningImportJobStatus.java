package com.esic.connect.planning.internal;

/**
 * Statut d'un {@code planning_import_job} (DEC-G1-003).
 *
 * <ul>
 *   <li>{@link #SIMULATED} : simulation faite, aucune écriture métier
 *       (invariant T1) ; republiable ;</li>
 *   <li>{@link #PUBLISHED} : séances + version créées (transaction
 *       atomique) ; republication idempotente ;</li>
 *   <li>{@link #CANCELLED} : abandonné par l'utilisateur ;</li>
 *   <li>{@link #EXPIRED} : TTL de simulation dépassé (purge) ;</li>
 *   <li>{@link #FAILED} : échec <em>inattendu</em> après re-validation,
 *       écrit dans une transaction {@code REQUIRES_NEW} distincte, sans
 *       aucune donnée métier publiée. Non republiable (nouvel import
 *       requis).</li>
 * </ul>
 */
enum PlanningImportJobStatus {
    SIMULATED,
    PUBLISHED,
    CANCELLED,
    EXPIRED,
    FAILED
}
