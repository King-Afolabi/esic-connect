package com.esic.connect.academic.internal;

/**
 * Rôle d'une affectation de responsable pédagogique sur une formation
 * (docs/02-cahier-des-charges.md §6.5 : responsable principal /
 * responsables délégués).
 *
 * <ul>
 *   <li>{@link #PRIMARY_MANAGER} : un seul actif par formation
 *       (contrainte SQL {@code uq_pedagogical_assignment_active_primary}) ;</li>
 *   <li>{@link #DELEGATE} : plusieurs autorisés, chevauchements permis,
 *       toujours sur l'ensemble de la formation.</li>
 * </ul>
 */
public enum PedagogicalAssignmentRole {
    PRIMARY_MANAGER,
    DELEGATE
}
