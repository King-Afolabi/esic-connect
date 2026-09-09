package com.esic.connect.integration.internal;

import com.esic.connect.shared.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Traduit {@link IntegrationException} en {@link ApiError} — codes {@code INT_*}. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {CalendarSubscriptionController.class, CalendarFeedController.class,
        MicrosoftIntegrationController.class})
class IntegrationExceptionHandler {

    @ExceptionHandler(IntegrationException.class)
    ResponseEntity<ApiError> handle(IntegrationException ex, HttpServletRequest request) {
        HttpStatus status;
        String code;
        String message;
        switch (ex.kind()) {
            case FEED_REVOKED -> {
                // 410 et non 404 : la personne qui a révoqué doit pouvoir
                // constater que c'est bien sa révocation qui agit.
                status = HttpStatus.GONE;
                code = "INT_FEED_REVOKED";
                message = "Cet abonnement au calendrier a été révoqué.";
            }
            case TOO_MANY_SUBSCRIPTIONS -> {
                status = HttpStatus.CONFLICT;
                code = "INT_TOO_MANY_SUBSCRIPTIONS";
                message = "Trop d'abonnements actifs. Révoquez-en un avant d'en créer un autre.";
            }
            default -> {
                status = HttpStatus.NOT_FOUND;
                code = "INT_NOT_FOUND";
                message = "Aucun abonnement au calendrier ne correspond.";
            }
        }
        ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), List.of());
        return ResponseEntity.status(status).body(body);
    }
}
