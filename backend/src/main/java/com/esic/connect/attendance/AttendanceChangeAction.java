package com.esic.connect.attendance;

/** Action tracée par l'audit pour le module {@code attendance}. */
public enum AttendanceChangeAction {
    /** Émargement réussi par l'apprenant (QR / code court). */
    RECORDED,
    /** Présence créée manuellement par un formateur / gestionnaire. */
    MANUAL_RECORDED,
    /** Statut / retard / commentaire d'une présence corrigés. */
    CORRECTED,
    /** Présence annulée logiquement. */
    CANCELLED,
    /** Justificatif d'absence déposé par l'apprenant. */
    JUSTIFICATION_SUBMITTED,
    /** Justificatif modifié par l'apprenant (tant que PENDING). */
    JUSTIFICATION_UPDATED,
    /** Justificatif examiné (accepté / refusé) par un gestionnaire. */
    JUSTIFICATION_REVIEWED,
    /** Pièce jointe d'un justificatif stockée (contenu déplacé, statut {@code STORED}). */
    JUSTIFICATION_ATTACHMENT_STORED,
    /** Apprenant provisoire signalé par le formateur (EF-ATT-007). */
    GUEST_DECLARED,
    /** Entrée provisoire régularisée : rattachée ou écartée (EF-ATT-007). */
    GUEST_RESOLVED,
    /** Départ anticipé signalé par l'apprenant (EF-ATT-013). */
    EARLY_DEPARTURE_DECLARED,
    /** Départ anticipé transmis au responsable pédagogique, avec ou sans avis. */
    EARLY_DEPARTURE_FORWARDED,
    /** Départ anticipé accepté ou refusé — décision finale. */
    EARLY_DEPARTURE_DECIDED,
    /** Export CSV d'un rapport d'assiduité généré. */
    REPORT_EXPORTED
}
