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
 * Traduit {@link AuthException} en réponse {@link ApiError} homogène.
 *
 * <p>Un jeton de réinitialisation invalide, expiré ou déjà consommé
 * produit toujours la même réponse : l'appelant ne doit pas pouvoir
 * distinguer ces trois cas, sous peine d'obtenir un oracle sur l'état des
 * demandes en cours.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
class AuthExceptionHandler {

    @ExceptionHandler(AuthException.class)
    ResponseEntity<ApiError> handle(AuthException ex, HttpServletRequest request) {
        HttpStatus status;
        String code;
        String message;
        switch (ex.kind()) {
            case INVALID_RESET_TOKEN -> {
                status = HttpStatus.BAD_REQUEST;
                code = "AUTH_RESET_TOKEN_INVALID";
                message = "Ce lien de réinitialisation n'est plus valable. Demandez-en un nouveau.";
            }
            case WEAK_PASSWORD -> {
                status = HttpStatus.BAD_REQUEST;
                code = "AUTH_PASSWORD_TOO_WEAK";
                message = "Le mot de passe choisi ne respecte pas la politique de sécurité.";
            }
            case ACCOUNT_NOT_ELIGIBLE -> {
                status = HttpStatus.CONFLICT;
                code = "AUTH_ACCOUNT_NOT_ELIGIBLE";
                message = "Ce compte ne permet pas cette opération.";
            }
            case CURRENT_PASSWORD_MISMATCH -> {
                status = HttpStatus.UNAUTHORIZED;
                code = "AUTH_CURRENT_PASSWORD_INVALID";
                message = "Le mot de passe actuel est incorrect.";
            }
            case PASSWORD_UNCHANGED -> {
                status = HttpStatus.BAD_REQUEST;
                code = "AUTH_PASSWORD_UNCHANGED";
                message = "Le nouveau mot de passe doit être différent de l'actuel.";
            }
            case REVOCATION_BACKEND_UNAVAILABLE -> {
                status = HttpStatus.SERVICE_UNAVAILABLE;
                code = "AUTH_REVOCATION_BACKEND_UNAVAILABLE";
                message = "La déconnexion n'a pas pu être enregistrée. Réessayez dans un instant.";
            }
            default -> {
                status = HttpStatus.BAD_REQUEST;
                code = "AUTH_ERROR";
                message = "Requête invalide.";
            }
        }
        ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), List.copyOf(ex.details()));
        return ResponseEntity.status(status).body(body);
    }
}
