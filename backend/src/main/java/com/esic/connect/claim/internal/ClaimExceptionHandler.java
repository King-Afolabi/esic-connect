package com.esic.connect.claim.internal;

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
 * Traduit {@link ClaimException} en {@link ApiError} homogène — codes
 * {@code CLAIM_*}. Aucun message ne divulgue le sujet, le corps d'un
 * message, ni l'identité d'un tiers.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ClaimController.class)
class ClaimExceptionHandler {

    @ExceptionHandler(ClaimException.class)
    ResponseEntity<ApiError> handle(ClaimException ex, HttpServletRequest request) {
        HttpStatus status;
        String code;
        String message;
        switch (ex.kind()) {
            case NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "CLAIM_NOT_FOUND";
                message = "Cette réclamation est introuvable.";
            }
            case FORBIDDEN -> {
                status = HttpStatus.FORBIDDEN;
                code = "CLAIM_FORBIDDEN";
                message = "Vous n'avez pas qualité pour cette opération.";
            }
            case SESSION_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "CLAIM_SESSION_NOT_FOUND";
                message = "La séance concernée est introuvable.";
            }
            case INVALID_STATE -> {
                status = HttpStatus.CONFLICT;
                code = "CLAIM_INVALID_STATE";
                message = "Cette opération est incompatible avec l'état de la réclamation.";
            }
            case NO_SCOPE_FOR_AUDIENCE -> {
                status = HttpStatus.CONFLICT;
                code = "CLAIM_NO_SCOPE_FOR_AUDIENCE";
                message = "Vous n'avez pas de classe active : adressez cette réclamation à "
                        + "l'administration scolaire.";
            }
            case INVALID_SORT -> {
                status = HttpStatus.BAD_REQUEST;
                code = "CLAIM_INVALID_SORT";
                message = "Critère de tri non autorisé.";
            }
            default -> {
                status = HttpStatus.BAD_REQUEST;
                code = "CLAIM_INVALID_SUBMISSION";
                message = "La demande est incomplète ou incohérente.";
            }
        }
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code,
                message, request.getRequestURI(), UUID.randomUUID().toString(), List.of()));
    }
}
