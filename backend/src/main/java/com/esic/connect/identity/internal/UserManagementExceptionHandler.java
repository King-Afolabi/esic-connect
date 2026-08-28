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
 * Traduit {@link UserManagementException} en réponse {@link ApiError}
 * homogène. Aucun message ne divulgue de donnée personnelle ni le contenu
 * d'un compte.
 */
@RestControllerAdvice(assignableTypes = UserAccountController.class)
class UserManagementExceptionHandler {

    @ExceptionHandler(UserManagementException.class)
    ResponseEntity<ApiError> handle(UserManagementException ex, HttpServletRequest request) {
        HttpStatus status;
        String code;
        String message;
        switch (ex.kind()) {
            case USER_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "USER_NOT_FOUND";
                message = "Aucun compte ne correspond à cet identifiant.";
            }
            case INVALID_STATE_TRANSITION -> {
                status = HttpStatus.CONFLICT;
                code = "USER_INVALID_STATE";
                message = "L'état actuel du compte ne permet pas cette opération.";
            }
            case ROLE_ALREADY_ASSIGNED -> {
                status = HttpStatus.CONFLICT;
                code = "USER_ROLE_ALREADY_ASSIGNED";
                message = "Ce rôle est déjà actif pour ce compte.";
            }
            case ROLE_NOT_ASSIGNED -> {
                status = HttpStatus.CONFLICT;
                code = "USER_ROLE_NOT_ASSIGNED";
                message = "Ce rôle n'est pas actif pour ce compte.";
            }
            case LAST_ACTIVE_ROLE -> {
                status = HttpStatus.CONFLICT;
                code = "USER_LAST_ACTIVE_ROLE";
                message = "Impossible de retirer le dernier rôle actif d'un compte.";
            }
            case SELF_ACTION_FORBIDDEN -> {
                status = HttpStatus.CONFLICT;
                code = "USER_SELF_ACTION_FORBIDDEN";
                message = "Cette opération ne peut pas être effectuée sur son propre compte.";
            }
            case SUPER_ADMIN_PROTECTED -> {
                status = HttpStatus.FORBIDDEN;
                code = "USER_SUPER_ADMIN_PROTECTED";
                message = "Cette opération requiert le rôle super administrateur.";
            }
            case NOT_AUTHORIZED -> {
                status = HttpStatus.FORBIDDEN;
                code = "USER_OPERATION_FORBIDDEN";
                message = "Accès refusé.";
            }
            case ROLE_UNKNOWN -> {
                status = HttpStatus.BAD_REQUEST;
                code = "USER_ROLE_UNKNOWN";
                message = "Code de rôle inconnu.";
            }
            case INVALID_SORT -> {
                status = HttpStatus.BAD_REQUEST;
                code = "USER_INVALID_SORT";
                message = "Champ de tri non autorisé.";
            }
            default -> {
                status = HttpStatus.BAD_REQUEST;
                code = "USER_INVALID_FILTER";
                message = "Valeur de filtre invalide.";
            }
        }
        ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), List.of());
        return ResponseEntity.status(status).body(body);
    }
}
