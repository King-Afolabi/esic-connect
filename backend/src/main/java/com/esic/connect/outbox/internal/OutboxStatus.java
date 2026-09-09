package com.esic.connect.outbox.internal;

/**
 * Cycle de vie d'une ligne d'outbox (docs/02 §25.2).
 *
 * <p>{@link #FAILED} et {@link #DEAD} sont volontairement distincts : une
 * tentative en échec qui sera reprise n'est pas la même situation qu'un
 * effet de bord abandonné par le diffuseur et qui attend une décision
 * humaine. Les confondre reviendrait à masquer la file d'échec.
 */
enum OutboxStatus {
    /** Jamais tenté, ou replanifié après un échec. */
    PENDING,
    /** Le gestionnaire a rendu la main sans erreur. */
    SENT,
    /** Dernière tentative en échec ; une reprise est planifiée. */
    FAILED,
    /** Tentatives épuisées : file d'échec, rejeu manuel (EF-OPS-005). */
    DEAD;

    boolean claimable() {
        return this == PENDING || this == FAILED;
    }
}
