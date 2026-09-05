package com.esic.connect.integration;

import java.time.Instant;
import java.util.Optional;

/**
 * Port sortant d'écriture d'une séance dans un calendrier externe
 * (EF-INT-003 ; docs/02 §28.2). Priorité {@code COULD}.
 *
 * <p>Comme {@link MeetingProvider}, l'adaptateur inactif <em>déclare</em>
 * qu'il n'écrit nulle part plutôt que de faire semblant d'avoir écrit.
 */
public interface ExternalCalendarWriter {

    boolean isActive();

    String providerName();

    /**
     * Crée ou met à jour l'événement correspondant à une séance.
     *
     * @return la référence de l'événement chez le fournisseur, ou
     *         {@link Optional#empty()} si aucun fournisseur n'est actif
     */
    Optional<String> upsertEvent(CalendarEvent event);

    /** Supprime l'événement d'une séance annulée. */
    void deleteEvent(String externalEventId);

    /**
     * @param externalEventId référence existante chez le fournisseur, ou
     *                        {@code null} pour une création
     * @param ownerHint       référence du propriétaire du calendrier
     *                        (identifiant public de compte)
     */
    record CalendarEvent(
            String externalEventId,
            String ownerHint,
            String subject,
            String location,
            String onlineMeetingUrl,
            Instant startsAt,
            Instant endsAt) {
    }
}
