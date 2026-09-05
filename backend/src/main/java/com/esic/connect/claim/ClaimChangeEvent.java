package com.esic.connect.claim;

import java.util.Set;
import java.util.UUID;

/**
 * Événement publié après une opération sur une réclamation.
 *
 * <p>Ne porte <strong>ni le sujet, ni le corps d'un message</strong> : ce
 * sont des données personnelles, que l'audit métier ne conserve pas
 * (docs/02 §23.3). Le détail se limite à ce qui rend l'événement
 * relisible — guichet, statut, catégorie.
 *
 * <p><strong>Les destinataires sont portés par l'événement</strong>
 * (T-12). C'est le module {@code claim} qui sait qui siège au guichet
 * visé et qui participe au fil ; le laisser déduire au module
 * {@code notification} l'obligerait à connaître la notion de guichet, de
 * transfert et de participation — c'est-à-dire tout le métier de la
 * réclamation.
 *
 * @param eventId               identifiant de l'occurrence, clé
 *                              d'idempotence des effets de bord
 * @param recipientUserPublicIds comptes à prévenir, acteur exclu : on ne
 *                              notifie personne de sa propre action
 */
public record ClaimChangeEvent(
        UUID claimPublicId,
        ClaimChangeAction action,
        Long actorInternalId,
        String detail,
        UUID eventId,
        Set<UUID> recipientUserPublicIds) {

    public ClaimChangeEvent {
        recipientUserPublicIds = recipientUserPublicIds == null
                ? Set.of() : Set.copyOf(recipientUserPublicIds);
    }
}
