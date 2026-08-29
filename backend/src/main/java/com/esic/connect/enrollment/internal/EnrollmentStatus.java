package com.esic.connect.enrollment.internal;

/**
 * Statut d'une inscription (docs/04-modele-donnees.md §13.1).
 *
 * <p>Seul {@link #ACTIVE} occupe le créneau d'unicité « une inscription
 * active par apprenant et par année » (docs/04 §13.3) : toute autre valeur
 * libère le créneau. Transitions produites dans ce lot :
 * <ul>
 *   <li>{@link #ACTIVE} à la création et à l'arrivée dans une nouvelle
 *       classe ;</li>
 *   <li>{@link #TRANSFERRED} sur l'inscription quittée lors d'un
 *       changement de classe (docs/04 §13.2) ;</li>
 *   <li>{@link #COMPLETED} / {@link #WITHDRAWN} à la clôture explicite.</li>
 * </ul>
 * {@link #PENDING}, {@link #SUSPENDED} et {@link #ARCHIVED} sont prévus
 * par le modèle mais non pilotés dans ce lot.
 */
public enum EnrollmentStatus {
    PENDING,
    ACTIVE,
    COMPLETED,
    TRANSFERRED,
    WITHDRAWN,
    SUSPENDED,
    ARCHIVED
}
