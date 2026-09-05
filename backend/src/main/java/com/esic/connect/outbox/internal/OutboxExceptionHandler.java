package com.esic.connect.outbox.internal;

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

/** Traduit {@link OutboxException} en {@link ApiError} — codes {@code OUTBOX_*}. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = OutboxAdminController.class)
class OutboxExceptionHandler {

    @ExceptionHandler(OutboxException.class)
    ResponseEntity<ApiError> handle(OutboxException ex, HttpServletRequest request) {
        HttpStatus status;
        String code;
        String message;
        switch (ex.kind()) {
            case INVALID_STATUS -> {
                status = HttpStatus.BAD_REQUEST;
                code = "OUTBOX_INVALID_STATUS";
                message = "Filtre de statut invalide (PENDING, SENT, FAILED ou DEAD attendu).";
            }
            case NOT_REPLAYABLE -> {
                status = HttpStatus.CONFLICT;
                code = "OUTBOX_NOT_REPLAYABLE";
                message = "Seul un effet de bord de la file d'échec peut être rejoué.";
            }
            default -> {
                status = HttpStatus.NOT_FOUND;
                code = "OUTBOX_NOT_FOUND";
                message = "Aucun effet de bord ne correspond à cet identifiant.";
            }
        }
        ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), List.of());
        return ResponseEntity.status(status).body(body);
    }
}
