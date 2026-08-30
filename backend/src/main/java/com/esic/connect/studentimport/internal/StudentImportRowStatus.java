package com.esic.connect.studentimport.internal;

/**
 * Verdict d'analyse d'une ligne de données CSV
 * ({@link StudentImportRow#getRowStatus()}). {@code VALID} : aucune
 * anomalie ; {@code WARNING} : anomalies non bloquantes ; {@code ERROR} :
 * au moins une anomalie qui interdit l'application de la ligne.
 * Contrainte SQL {@code chk_student_import_row_status}.
 */
enum StudentImportRowStatus {
    VALID,
    WARNING,
    ERROR
}
