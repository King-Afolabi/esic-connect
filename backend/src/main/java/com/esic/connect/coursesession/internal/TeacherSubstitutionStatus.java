package com.esic.connect.coursesession.internal;

/**
 * Cycle de vie d'un remplacement de formateur (V14 ; G1-C.2).
 *
 * <pre>{@code ACTIVE  →  ENDED}</pre>
 *
 * Aucune suppression métier : une substitution terminée reste tracée.
 */
enum TeacherSubstitutionStatus {
    ACTIVE,
    ENDED
}
