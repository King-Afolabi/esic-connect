package com.esic.connect.studentimport.internal;

/**
 * Cycle de vie d'un {@link StudentImportJob} (rapport §3.4).
 *
 * <p>{@code SIMULATED → APPLIED} (chemin nominal), {@code SIMULATED →
 * CANCELLED} (annulation explicite), {@code SIMULATED → EXPIRED} (purge).
 * Il n'existe pas de statut {@code CONFIRMED} observable : la transition ne
 * dure que le temps de la transaction verrouillée. Une confirmation qui
 * échoue rollback intégralement et le job reste {@code SIMULATED}. Ces
 * quatre valeurs sont exactement celles autorisées par la contrainte SQL
 * {@code chk_student_import_job_status} de la migration V11.
 */
enum StudentImportJobStatus {
    SIMULATED,
    APPLIED,
    CANCELLED,
    EXPIRED
}
