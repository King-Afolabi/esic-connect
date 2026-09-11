package com.esic.connect.passwordadmin.internal;

import com.esic.connect.shared.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Traduit {@link AdminPasswordResetException} en réponse {@link ApiError}
 * homogène.
 */
@RestControllerAdvice(assignableTypes = AdminPasswordResetController.class)
class AdminPasswordResetExceptionHandler {

    @ExceptionHandler(AdminPasswordResetException.class)
    ResponseEntity<ApiError> handle(AdminPasswordResetException ex, HttpServletRequest request) {
        HttpStatus status;
        String code;
        String message;
        switch (ex.kind()) {
            case USER_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "PWDADMIN_USER_NOT_FOUND";
                message = "Aucun compte ne correspond à cet identifiant.";
            }
            case NOT_ELIGIBLE -> {
                status = HttpStatus.CONFLICT;
                code = "PWDADMIN_NOT_ELIGIBLE";
                message = "Ce compte est suspendu ou archivé : la réinitialisation est refusée.";
            }
            default -> {
                status = HttpStatus.FORBIDDEN;
                code = "PWDADMIN_FORBIDDEN";
                message = "Vous n'êtes pas autorisé à réinitialiser le mot de passe de ce compte.";
            }
        }
        ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), List.of());
        return ResponseEntity.status(status).body(body);
    }
}
