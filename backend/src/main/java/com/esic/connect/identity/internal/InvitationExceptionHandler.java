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
 * Traduit {@link InvitationException} en réponse {@link ApiError}
 * homogène. Toutes les causes rendant un jeton inutilisable partagent le
 * code {@code INVITATION_INVALID} et le même message : la réponse
 * publique ne révèle jamais le motif exact ni aucune donnée personnelle.
 */
@RestControllerAdvice(assignableTypes = AccountInvitationController.class)
class InvitationExceptionHandler {

    @ExceptionHandler(InvitationException.class)
    ResponseEntity<ApiError> handle(InvitationException ex, HttpServletRequest request) {
        HttpStatus status;
        String code;
        String message;
        switch (ex.kind()) {
            case TARGET_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "INVITATION_TARGET_NOT_FOUND";
                message = "Aucun compte en attente d'activation pour cette adresse.";
            }
            case TARGET_NOT_PENDING -> {
                status = HttpStatus.CONFLICT;
                code = "INVITATION_TARGET_NOT_PENDING";
                message = "Le compte cible n'est pas en attente d'activation.";
            }
            case ROLE_INVALID -> {
                status = HttpStatus.UNPROCESSABLE_ENTITY;
                code = "INVITATION_ROLE_INVALID";
                message = "Role inconnu ou inactif.";
            }
            default -> {
                status = HttpStatus.BAD_REQUEST;
                code = "INVITATION_INVALID";
                message = "Lien d'activation invalide ou expire.";
            }
        }
        ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), List.of());
        return ResponseEntity.status(status).body(body);
    }
}
