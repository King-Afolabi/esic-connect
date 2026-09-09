package com.esic.connect.notification.internal;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Abonnements de l'appelant à la poussée web (EF-NOTIF-005 ; docs/02
 * §29.3).
 *
 * <p>{@code GET /status} expose {@code providerActive} : lorsque aucune
 * clé VAPID n'est configurée, l'interface doit annoncer que la poussée
 * est <strong>inactive</strong> plutôt que d'afficher un interrupteur
 * sans effet.
 */
@RestController
@RequestMapping("/api/v1/me/push")
@PreAuthorize("isAuthenticated()")
class PushSubscriptionController {

    private final PushSubscriptionService service;

    PushSubscriptionController(PushSubscriptionService service) {
        this.service = service;
    }

    @GetMapping("/status")
    NotificationResponses.PushStatus status(@AuthenticationPrincipal Jwt caller) {
        return service.status(subject(caller));
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    NotificationResponses.PushSubscriptionView subscribe(@RequestBody Subscribe request,
                                                         @AuthenticationPrincipal Jwt caller) {
        return service.subscribe(subject(caller), request.endpoint(), request.p256dh(), request.auth());
    }

    @DeleteMapping("/subscriptions/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unsubscribe(@PathVariable String publicId, @AuthenticationPrincipal Jwt caller) {
        service.unsubscribe(subject(caller), publicId);
    }

    /** Reprend la forme produite par {@code PushSubscription.toJSON()} du navigateur. */
    record Subscribe(String endpoint, String p256dh, String auth) {
    }

    private static String subject(Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }
}
