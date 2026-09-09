package com.esic.connect.audit.internal;

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
 * Traduit {@link AuditException} en {@link ApiError} — codes
 * {@code AUDIT_*}. Une erreur d'appel du client produit un {@code 400}
 * explicite, jamais un {@code 500} (docs/02 §30.1).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AuditController.class)
class AuditExceptionHandler {

    @ExceptionHandler(AuditException.class)
    ResponseEntity<ApiError> handle(AuditException ex, HttpServletRequest request) {
        String code;
        String message;
        if (ex.kind() == AuditException.Kind.INVALID_FORMAT) {
            code = "AUDIT_INVALID_FORMAT";
            message = "Format d'export non pris en charge (csv, xlsx ou pdf attendu).";
        } else {
            code = "AUDIT_INVALID_FILTER";
            message = "Valeur de filtre invalide.";
        }
        ApiError body = new ApiError(Instant.now(), HttpStatus.BAD_REQUEST.value(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), List.of());
        return ResponseEntity.badRequest().body(body);
    }
}
