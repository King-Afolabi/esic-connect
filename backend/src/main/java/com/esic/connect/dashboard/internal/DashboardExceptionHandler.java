package com.esic.connect.dashboard.internal;

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

/** Traduit {@link DashboardException} en {@link ApiError} homogène (bloc G1-F). */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = DashboardController.class)
class DashboardExceptionHandler {

    @ExceptionHandler(DashboardException.class)
    ResponseEntity<ApiError> handle(DashboardException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        String code;
        String message;
        switch (ex.kind()) {
            case CONTEXT_NOT_HELD -> {
                code = "DASHBOARD_CONTEXT_NOT_HELD";
                message = "Le contexte de rôle demandé n'est pas associé à votre compte.";
            }
            default -> {
                code = "DASHBOARD_NO_ROLE";
                message = "Aucun tableau de bord n'est disponible pour votre compte.";
            }
        }
        ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), List.of());
        return ResponseEntity.status(status).body(body);
    }
}
