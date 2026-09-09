package com.esic.connect.coursesession.internal;

/** Cycle de vie d'une demande d'annulation (EF-SES-008 ; migration V22). */
public enum CancellationRequestStatus {
    /** Déposée par le formateur, en attente de décision. */
    REQUESTED,
    /** Acceptée : la séance a été annulée dans la foulée. */
    APPROVED,
    /** Refusée, avec commentaire. */
    REJECTED,
    /** Retirée par le formateur avant décision. */
    WITHDRAWN
}
