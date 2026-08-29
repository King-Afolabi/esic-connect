package com.esic.connect.enrollment.internal;

/**
 * Origine d'une inscription (docs/04-modele-donnees.md §13.1
 * {@code enrollment_source}).
 *
 * <ul>
 *   <li>{@link #MANUAL} : saisie depuis l'administration ;</li>
 *   <li>{@link #CLASS_TRANSFER} : inscription créée par un changement de
 *       classe, avec {@code previous_enrollment_id} renseigné.</li>
 * </ul>
 *
 * <p>L'origine « import CSV » relève d'un lot ultérieur.
 */
public enum EnrollmentSource {
    MANUAL,
    CLASS_TRANSFER
}
