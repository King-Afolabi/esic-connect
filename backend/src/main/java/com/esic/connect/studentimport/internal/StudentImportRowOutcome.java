package com.esic.connect.studentimport.internal;

/**
 * Résultat effectif d'une ligne appliquée, renseigné à l'état
 * {@code APPLIED} ({@link StudentImportRow#getAppliedOutcome()}).
 * Colonne {@code applied_outcome}, sans contrainte {@code CHECK} (rapport
 * §7.3).
 */
enum StudentImportRowOutcome {
    CREATED,
    ENROLLED,
    UPDATED,
    TRANSFERRED,
    NOOP
}
