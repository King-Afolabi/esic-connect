package com.esic.connect.studentimport.internal;

/**
 * Action calculée pour une ligne à la simulation et re-vérifiée à la
 * confirmation (rapport §3.3). Contrainte SQL
 * {@code chk_student_import_row_action}.
 */
enum StudentImportPlannedAction {
    /** Compte absent : création {@code PENDING_ACTIVATION} + rôle STUDENT + invitation + profil + inscription. */
    CREATE_ACCOUNT_AND_ENROLL,
    /** Compte existant : profil (si absent) et/ou nouvelle inscription. */
    ENROLL_EXISTING,
    /** Profil existant : mise à jour de {@code phone} / {@code work_study} / {@code company_name} uniquement. */
    UPDATE_PROFILE,
    /** Inscription active dans une autre classe de la même année : changement de classe conservant l'historique. */
    TRANSFER_CLASS,
    /** Situation déjà conforme : aucun effet (ligne « ignorée » du bilan). */
    NONE
}
