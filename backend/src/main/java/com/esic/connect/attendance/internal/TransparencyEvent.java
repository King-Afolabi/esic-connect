package com.esic.connect.attendance.internal;

/**
 * Nature d'un fait porté au journal de transparence (EF-ATT-014).
 *
 * <p>Les valeurs reprennent celles de l'historique append-only
 * {@code attendance_correction} et y ajoutent les faits qui n'y figurent
 * pas : l'émargement initial — que l'apprenant est le premier concerné à
 * pouvoir vérifier — et le cycle du départ anticipé.
 */
enum TransparencyEvent {
    /** Présence enregistrée par l'apprenant lui-même (QR, code court, borne). */
    RECORDED,
    /** Présence créée à la main par un formateur ou un gestionnaire. */
    CREATED_MANUALLY,
    /** Statut, retard ou commentaire corrigés. */
    STATUS_CORRECTED,
    /** Présence annulée logiquement — la ligne reste, elle ne compte plus. */
    CANCELLED,
    /** Justificatif rattaché à la présence. */
    JUSTIFICATION_ADDED,
    /** Justificatif modifié tant qu'il n'était pas examiné. */
    JUSTIFICATION_UPDATED,
    /** Justificatif examiné : accepté ou refusé, avec motif. */
    JUSTIFICATION_REVIEWED,
    /** Départ anticipé signalé par l'apprenant. */
    EARLY_DEPARTURE_DECLARED,
    /** Départ anticipé transmis au responsable pédagogique. */
    EARLY_DEPARTURE_FORWARDED,
    /** Départ anticipé accepté ou refusé. */
    EARLY_DEPARTURE_DECIDED
}
