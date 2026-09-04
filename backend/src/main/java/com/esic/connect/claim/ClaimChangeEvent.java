package com.esic.connect.claim;

import java.util.UUID;

/**
 * Événement publié après une opération sur une réclamation.
 *
 * <p>Ne porte <strong>ni le sujet, ni le corps d'un message</strong> : ce
 * sont des données personnelles, que l'audit métier ne conserve pas
 * (docs/02 §23.3). Le détail se limite à ce qui rend l'événement
 * relisible — guichet, statut, catégorie.
 */
public record ClaimChangeEvent(
        UUID claimPublicId,
        ClaimChangeAction action,
        Long actorInternalId,
        String detail) {
}
