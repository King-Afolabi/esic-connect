package com.esic.connect.integration.internal;

import com.esic.connect.integration.ExternalCalendarWriter;
import com.esic.connect.integration.MeetingProviderException;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Écriture des séances dans le calendrier Microsoft d'un utilisateur
 * (EF-INT-003, priorité {@code COULD} ; docs/02 §28.2).
 *
 * <p><strong>Non vérifié contre un locataire Microsoft réel</strong> —
 * dette T-16.
 */
class MicrosoftGraphCalendarWriter implements ExternalCalendarWriter {

    private final MicrosoftGraphClient client;

    MicrosoftGraphCalendarWriter(MicrosoftGraphClient client) {
        this.client = client;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public String providerName() {
        return "microsoft-graph";
    }

    @Override
    public Optional<String> upsertEvent(CalendarEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subject", event.subject());
        payload.put("start", Map.of(
                "dateTime", DateTimeFormatter.ISO_INSTANT.format(event.startsAt()),
                "timeZone", "UTC"));
        payload.put("end", Map.of(
                "dateTime", DateTimeFormatter.ISO_INSTANT.format(event.endsAt()),
                "timeZone", "UTC"));
        if (event.location() != null) {
            payload.put("location", Map.of("displayName", event.location()));
        }
        if (event.onlineMeetingUrl() != null) {
            payload.put("body", Map.of("contentType", "text",
                    "content", "Lien de la séance : " + event.onlineMeetingUrl()));
        }

        String base = "/users/" + event.ownerHint() + "/events";
        JsonNode body = event.externalEventId() == null
                ? client.post(base, payload)
                : client.patch(base + "/" + event.externalEventId(), payload);
        if (body == null || !body.hasNonNull("id")) {
            throw new MeetingProviderException(
                    "Microsoft Graph : événement écrit sans identifiant exploitable");
        }
        return Optional.of(body.get("id").asText());
    }

    @Override
    public void deleteEvent(String externalEventId) {
        if (externalEventId == null || externalEventId.isBlank()) {
            return;
        }
        client.delete("/me/events/" + externalEventId);
    }
}
