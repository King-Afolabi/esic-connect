package com.esic.connect.integration.internal;

import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Abonnements iCalendar de l'appelant (EF-INT-001, AC-034).
 *
 * <p>Toutes les routes sont <em>propriétaires</em> : l'utilisateur est
 * résolu depuis le seul JWT, jamais depuis un paramètre. Un abonnement
 * qui n'est pas le sien répond {@code 404}, jamais {@code 403} — son
 * existence n'a pas à être confirmée.
 */
@RestController
@RequestMapping("/api/v1/me/calendar-subscriptions")
@PreAuthorize("isAuthenticated()")
class CalendarSubscriptionController {

    private final CalendarFeedService service;

    CalendarSubscriptionController(CalendarFeedService service) {
        this.service = service;
    }

    @GetMapping
    List<IntegrationResponses.SubscriptionSummary> list(@AuthenticationPrincipal Jwt caller) {
        return service.list(caller.getSubject());
    }

    /** Le chemin d'abonnement renvoyé ici ne sera plus jamais réaffiché. */
    @PostMapping
    ResponseEntity<IntegrationResponses.CreatedSubscription> create(
            @AuthenticationPrincipal Jwt caller,
            @RequestBody(required = false) CreateRequest request) {
        String label = request == null ? null : request.label();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(caller.getSubject(), label));
    }

    @DeleteMapping("/{publicId}")
    ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt caller, @PathVariable UUID publicId) {
        service.revoke(caller.getSubject(), publicId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Téléchargement direct du flux de l'appelant depuis l'application
     * (route {@code GET /api/v1/me/calendar.ics} du cahier §30.2). Ne
     * remplace pas l'abonnement : un agenda externe ne sait pas porter un
     * jeton d'accès.
     */
    @GetMapping(path = "/download")
    ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt caller) {
        byte[] body = service.renderOwnFeed(caller.getSubject()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"esic-connect.ics\"")
                .contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
                .body(body);
    }

    record CreateRequest(@Size(max = 120) String label) {
    }
}
