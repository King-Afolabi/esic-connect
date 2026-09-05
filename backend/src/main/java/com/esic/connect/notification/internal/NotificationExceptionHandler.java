package com.esic.connect.notification.internal;

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

/**
 * Traduit {@link NotificationException} en réponse {@link ApiError}
 * homogène — codes {@code NOTIF_*}. Portée limitée aux contrôleurs du
 * module. Aucun message ne divulgue de donnée personnelle.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        NotificationController.class,
        EmailDeliveryController.class,
        NotificationPreferenceController.class,
        PushSubscriptionController.class
})
class NotificationExceptionHandler {

    @ExceptionHandler(NotificationException.class)
    ResponseEntity<ApiError> handle(NotificationException ex, HttpServletRequest request) {
        HttpStatus status;
        String code;
        String message;
        switch (ex.kind()) {
            case NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "NOTIF_NOT_FOUND";
                message = "Aucune notification ne correspond à cet identifiant.";
            }
            case INVALID_PREFERENCE -> {
                status = HttpStatus.BAD_REQUEST;
                code = "NOTIF_INVALID_PREFERENCE";
                message = "Préférence invalide (catégorie, canal ou valeur inconnue).";
            }
            case PREFERENCE_LOCKED -> {
                status = HttpStatus.CONFLICT;
                code = "NOTIF_PREFERENCE_LOCKED";
                message = "Ce réglage ne peut pas être désactivé : le centre de notifications "
                        + "et les alertes de sécurité restent toujours actifs.";
            }
            case INVALID_SUBSCRIPTION -> {
                status = HttpStatus.BAD_REQUEST;
                code = "NOTIF_INVALID_SUBSCRIPTION";
                message = "Abonnement de notification poussée invalide.";
            }
            case INVALID_STATUS -> {
                status = HttpStatus.BAD_REQUEST;
                code = "NOTIF_INVALID_STATUS";
                message = "Filtre de statut invalide (UNREAD, READ ou ARCHIVED attendu).";
            }
            default -> {
                status = HttpStatus.UNAUTHORIZED;
                code = "NOTIF_UNAUTHENTICATED";
                message = "Authentification requise.";
            }
        }
        ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), List.of());
        return ResponseEntity.status(status).body(body);
    }
}
