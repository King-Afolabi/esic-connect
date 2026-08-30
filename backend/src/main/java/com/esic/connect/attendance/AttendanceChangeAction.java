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
    /** Export CSV d'un rapport d'assiduité généré. */
    REPORT_EXPORTED
}
