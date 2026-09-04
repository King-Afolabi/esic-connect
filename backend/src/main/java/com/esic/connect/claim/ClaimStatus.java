package com.esic.connect.claim;

/**
 * Cycle de vie d'une réclamation (docs/02 §20.4).
 *
 * <p>Aucun statut n'est terminal au sens strict : une réclamation
 * {@code CLOSED} ou {@code RESOLVED} peut être rouverte avec motif
 * (EF-CLAIM-004), et l'historique est alors conservé — RG-088 l'exige
 * « y compris après réouverture ».
 */
public enum ClaimStatus {
    OPEN,
    IN_PROGRESS,
    WAITING_FOR_STUDENT,
    TRANSFERRED,
    RESOLVED,
    CLOSED,
    REJECTED,
    REOPENED;

    /** Une réclamation close ou résolue est rouvrable, pas modifiable. */
    public boolean isClosed() {
        return this == RESOLVED || this == CLOSED || this == REJECTED;
    }

    /** Statuts acceptant un nouveau message sans réouverture préalable. */
    public boolean acceptsMessages() {
        return !isClosed();
    }
}
