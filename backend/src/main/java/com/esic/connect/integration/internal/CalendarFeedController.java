package com.esic.connect.integration.internal;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Flux iCalendar public, authentifié par son seul jeton (EF-INT-001).
 *
 * <p><strong>Route volontairement non authentifiée</strong> : Outlook,
 * Google Calendar et Apple Calendar ne savent pas porter un jeton
 * d'accès — ils rappellent une URL. Le secret est donc le jeton, et le
 * contrôle est fait par {@link CalendarFeedService}, pas par Spring
 * Security. Trois conséquences assumées et traitées :
 * <ul>
 *   <li>le jeton est aléatoire (32 octets) et comparé par empreinte ;</li>
 *   <li>un jeton faux et une clé inconnue produisent la
 *       <strong>même</strong> réponse ;</li>
 *   <li>la réponse porte {@code no-store} : un flux planning n'a pas à
 *       rester dans le cache d'un intermédiaire.</li>
 * </ul>
 */
@RestController
class CalendarFeedController {

    private final CalendarFeedService service;

    CalendarFeedController(CalendarFeedService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/calendar/{feedKey}.ics")
    ResponseEntity<byte[]> feed(@PathVariable String feedKey,
                                @RequestParam(required = false) String token) {
        byte[] body = service.renderFeed(feedKey, token).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
                .header("Content-Disposition", "inline; filename=\"esic-connect.ics\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    /** Fenêtre d'expiration du cache client, documentée dans le flux. */
    static final Duration REFRESH_HINT = Duration.ofHours(1);
}
