package com.esic.connect.integration.internal;

import com.esic.connect.integration.MeetingProvider;
import com.esic.connect.integration.MeetingProviderException;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Création d'une réunion Teams pour une séance distancielle ou hybride
 * (EF-INT-002 ; docs/02 §28.2), via {@code POST /users/{id}/onlineMeetings}.
 *
 * <p><strong>Non vérifié contre un locataire Microsoft réel</strong> —
 * dette T-16. Actif uniquement si {@link MicrosoftGraphProperties} est
 * complètement configuré.
 *
 * <p>Un échec de Graph lève {@link MeetingProviderException} plutôt que
 * de renvoyer {@code Optional.empty()} : l'appelant doit pouvoir
 * distinguer « pas d'intégration » de « l'intégration a refusé », faute
 * de quoi une panne Teams se lirait comme une absence de configuration.
 */
class MicrosoftGraphMeetingProvider implements MeetingProvider {

    private final MicrosoftGraphClient client;

    MicrosoftGraphMeetingProvider(MicrosoftGraphClient client) {
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
    public Optional<Meeting> createMeeting(MeetingRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subject", request.subject());
        payload.put("startDateTime", DateTimeFormatter.ISO_INSTANT.format(request.startsAt()));
        payload.put("endDateTime", DateTimeFormatter.ISO_INSTANT.format(request.endsAt()));

        JsonNode body = client.post("/users/" + request.organizerHint() + "/onlineMeetings", payload);
        if (body == null || !body.hasNonNull("joinWebUrl")) {
            throw new MeetingProviderException(
                    "Microsoft Graph : réunion créée sans lien de participation exploitable");
        }
        return Optional.of(new Meeting(body.get("joinWebUrl").asText(),
                body.path("id").asText(null)));
    }
}
