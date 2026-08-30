package com.esic.connect.attendance.internal;

/**
 * Nature d'une entrée de l'historique append-only
 * {@code attendance_correction} (docs/04 §19.4).
 *
 * <ul>
 *   <li>{@link #CREATED_MANUALLY} : la présence a été créée à la main
 *       (formateur / gestionnaire) ou à la volée lors d'un dépôt de
 *       justificatif ;</li>
 *   <li>{@link #STATUS_CORRECTED} : statut / retard / commentaire
 *       corrigés (motif obligatoire) ;</li>
 *   <li>{@link #CANCELLED} : présence annulée logiquement ;</li>
 *   <li>{@link #JUSTIFICATION_ADDED} / {@link #JUSTIFICATION_UPDATED} /
 *       {@link #JUSTIFICATION_REVIEWED} : cycle de vie du justificatif
 *       rattaché.</li>
 * </ul>
 */
enum AttendanceCorrectionAction {
    CREATED_MANUALLY,
    STATUS_CORRECTED,
    CANCELLED,
    JUSTIFICATION_ADDED,
    JUSTIFICATION_UPDATED,
    JUSTIFICATION_REVIEWED
}
