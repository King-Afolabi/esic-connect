package com.esic.connect.integration.internal;

import com.esic.connect.integration.MeetingProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Client HTTP Microsoft Graph — flux « identifiants client »
 * (application permissions).
 *
 * <p><strong>Ce client n'a jamais été exercé contre un locataire
 * Microsoft réel</strong> (dette T-16, {@code docs/STATUS.md}) :
 * il est écrit d'après la documentation de l'API, couvert par des tests
 * contre un serveur HTTP local, et reste <em>inactif par défaut</em>. Ne
 * pas présenter EF-INT-002 ni EF-INT-003 comme vérifiés.
 *
 * <p>Le jeton d'application est mis en cache jusqu'à un peu avant son
 * expiration : en redemander un à chaque appel ferait de la création de
 * réunion deux allers-retours au lieu d'un, et Entra ID limite le débit
 * de cette route.
 */
class MicrosoftGraphClient {

    /** Marge avant expiration : une horloge peut dériver de quelques secondes. */
    private static final Duration RENEW_MARGIN = Duration.ofSeconds(60);

    private final RestClient http;
    private final MicrosoftGraphProperties properties;
    private final java.time.Clock clock;

    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiry = Instant.EPOCH;

    MicrosoftGraphClient(RestClient.Builder builder, MicrosoftGraphProperties properties,
                         java.time.Clock clock) {
        this.http = builder.build();
        this.properties = properties;
        this.clock = clock;
    }

    /** Jeton d'application, obtenu puis réutilisé jusqu'à sa péremption. */
    String accessToken() {
        Instant now = clock.instant();
        String token = cachedToken;
        if (token != null && now.isBefore(cachedTokenExpiry)) {
            return token;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("scope", "https://graph.microsoft.com/.default");
        form.add("grant_type", "client_credentials");
        try {
            JsonNode body = http.post()
                    .uri(properties.tokenUrl() + "/" + properties.tenantId() + "/oauth2/v2.0/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || !body.hasNonNull("access_token")) {
                throw new MeetingProviderException("Microsoft Graph : jeton d'accès absent de la réponse");
            }
            long expiresIn = body.path("expires_in").asLong(3600L);
            cachedToken = body.get("access_token").asText();
            cachedTokenExpiry = now.plusSeconds(expiresIn).minus(RENEW_MARGIN);
            return cachedToken;
        } catch (RestClientException transportFailure) {
            // Le message de la cause peut contenir l'URL, jamais le secret :
            // il est passé dans le corps du formulaire, pas dans l'URI.
            throw new MeetingProviderException("Microsoft Graph : échec d'obtention du jeton",
                    transportFailure);
        }
    }

    /** Appel Graph authentifié renvoyant du JSON. */
    JsonNode post(String path, Map<String, Object> payload) {
        try {
            return http.post()
                    .uri(properties.baseUrl() + path)
                    .header("Authorization", "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException transportFailure) {
            throw new MeetingProviderException("Microsoft Graph : appel " + path + " en échec",
                    transportFailure);
        }
    }

    JsonNode patch(String path, Map<String, Object> payload) {
        try {
            return http.patch()
                    .uri(properties.baseUrl() + path)
                    .header("Authorization", "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException transportFailure) {
            throw new MeetingProviderException("Microsoft Graph : appel " + path + " en échec",
                    transportFailure);
        }
    }

    void delete(String path) {
        try {
            http.delete()
                    .uri(properties.baseUrl() + path)
                    .header("Authorization", "Bearer " + accessToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException transportFailure) {
            throw new MeetingProviderException("Microsoft Graph : suppression " + path + " en échec",
                    transportFailure);
        }
    }
}
