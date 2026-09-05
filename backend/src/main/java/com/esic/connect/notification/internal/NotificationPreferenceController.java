package com.esic.connect.notification.internal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Préférences de notification de l'appelant (EF-NOTIF-006).
 *
 * <p>Toutes les routes portent sur <strong>ses</strong> préférences : le
 * sujet du JWT est le propriétaire, jamais un paramètre.
 */
@RestController
@RequestMapping("/api/v1/me/notification-preferences")
@PreAuthorize("isAuthenticated()")
class NotificationPreferenceController {

    private final NotificationPreferenceService service;

    NotificationPreferenceController(NotificationPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    NotificationResponses.PreferenceList list(@AuthenticationPrincipal Jwt caller) {
        return service.list(subject(caller));
    }

    @PutMapping
    NotificationResponses.PreferenceList update(@RequestBody UpdatePreference request,
                                                @AuthenticationPrincipal Jwt caller) {
        return service.update(subject(caller), request.category(), request.channel(), request.enabled());
    }

    /** @param enabled objet et non primitif : une valeur absente est une erreur, pas un « false ». */
    record UpdatePreference(String category, String channel, Boolean enabled) {
    }

    private static String subject(Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }
}
