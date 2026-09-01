package com.esic.connect.notification.internal;

import com.esic.connect.notification.internal.NotificationResponses.NotificationPage;
import com.esic.connect.notification.internal.NotificationResponses.UnreadCount;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Centre de notifications de l'appelant (G1-D). Toutes les routes portent
 * sur <strong>ses</strong> notifications : le destinataire est le sujet du
 * JWT, jamais un paramètre. {@code @PreAuthorize("isAuthenticated()")} —
 * accessible à tout rôle ; l'isolation par destinataire est faite dans le
 * service.
 */
@RestController
@RequestMapping("/api/v1/me/notifications")
@PreAuthorize("isAuthenticated()")
class NotificationController {

    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    NotificationPage list(@RequestParam(required = false) String status,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size,
                          @AuthenticationPrincipal Jwt caller) {
        return service.list(subject(caller), status, page, size);
    }

    @GetMapping("/unread-count")
    UnreadCount unreadCount(@AuthenticationPrincipal Jwt caller) {
        return service.unreadCount(subject(caller));
    }

    @PostMapping("/{publicId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        service.markRead(subject(caller), publicId);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markAllRead(@AuthenticationPrincipal Jwt caller) {
        service.markAllRead(subject(caller));
    }

    private static String subject(Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }
}
