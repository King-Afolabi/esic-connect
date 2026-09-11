package com.esic.connect.coursesession;

/**
 * Action du cycle de vie d'une séance, tracée par l'audit (cahier §30.1)
 * et écoutée par le module {@code attendance} ({@link #CLOSED} et
 * {@link #CANCELLED} déclenchent la purge des jetons Redis de la séance).
 */
public enum CourseSessionChangeAction {
    CREATED,
    OPENED,
    CLOSED,
    /** Annulation avec motif d'une séance {@code PLANNED} / {@code OPEN} (G1-C). */
    CANCELLED,
    /** Remplaçant affecté à la séance (G1-C). */
    SUBSTITUTION_ADDED,
    /** Fin d'un remplacement (G1-C). */
    SUBSTITUTION_ENDED,
    /** Séance reportée : une séance de remplacement a été créée (EF-SES-007). */
    POSTPONED,
    /** Demande d'annulation déposée par un formateur (EF-SES-008). */
    CANCELLATION_REQUESTED,
    /** Décision rendue sur une demande d'annulation (EF-SES-008). */
    CANCELLATION_DECIDED,
    /** Salle affectée ou changée a posteriori (V35) — décidée souvent au dernier moment. */
    ROOM_ASSIGNED
}
