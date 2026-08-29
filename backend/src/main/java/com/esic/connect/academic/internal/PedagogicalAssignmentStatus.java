package com.esic.connect.academic.internal;

/**
 * Statut d'une affectation de responsable pédagogique.
 *
 * <p>{@link #ACTIVE} : l'affectation occupe son créneau (et, si sa période
 * couvre l'instant courant, ouvre l'accès au périmètre). {@link #CLOSED} :
 * clôturée explicitement — seule cette transition libère le créneau d'un
 * {@link PedagogicalAssignmentRole#PRIMARY_MANAGER}, y compris lorsque la
 * période est déjà expirée. Aucune suppression physique.
 */
public enum PedagogicalAssignmentStatus {
    ACTIVE,
    CLOSED
}
