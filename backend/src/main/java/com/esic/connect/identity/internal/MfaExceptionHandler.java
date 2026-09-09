package com.esic.connect.identity.internal;

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
 * Traduit les refus de second facteur en réponses {@link ApiError}.
 *
 * <p>Aucun message ne distingue « ce compte n'existe pas » de « ce défi a
 * expiré » : la granularité utile au débogage serait ici un oracle
 * d'énumération des comptes.
 */
@RestControllerAdvice(assignableTypes = MfaController.class)
class MfaExceptionHandler {

    @ExceptionHandler(MfaException.class)
    ResponseEntity<ApiError> handle(MfaException exception, HttpServletRequest request) {
        return build(exception.code().status(), exception.code().name(),
                exception.getMessage(), request);
    }

    /**
     * Redis injoignable : sans défi vérifiable, aucune émission de jeton
     * n'est sûre. On refuse ({@code 503}) plutôt que de contourner
     * (docs/02 §24.5).
     */
    @ExceptionHandler(MfaBackendUnavailableException.class)
    ResponseEntity<ApiError> handleUnavailable(MfaBackendUnavailableException exception,
                                               HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "MFA_BACKEND_UNAVAILABLE",
                exception.getMessage(), request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), List.of()));
    }
}
