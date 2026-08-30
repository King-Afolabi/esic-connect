package com.esic.connect.attendance.internal;

/**
 * Cycle de vie d'un justificatif d'absence.
 *
 * <ul>
 *   <li>{@link #PENDING} : déposé, en attente d'examen ; modifiable par
 *       l'apprenant ;</li>
 *   <li>{@link #ACCEPTED} : accepté — la présence passe
 *       {@code ABSENT → EXCUSED_ABSENCE} ; terminal ;</li>
 *   <li>{@link #REJECTED} : refusé (motif obligatoire) — la présence
 *       reste / revient {@code ABSENT} ; un nouveau dépôt reste possible.</li>
 * </ul>
 */
enum JustificationStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
