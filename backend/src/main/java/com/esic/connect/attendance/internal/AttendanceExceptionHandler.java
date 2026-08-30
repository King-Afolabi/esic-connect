package com.esic.connect.attendance.internal;

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
 * Traduit {@link AttendanceException} en réponse {@link ApiError}
 * homogène. Messages contrôlés, sans jeton, code court, identifiant
 * interne ni détail d'infrastructure.
 */
@RestControllerAdvice(assignableTypes = AttendanceController.class)
class AttendanceExceptionHandler {

    @ExceptionHandler(AttendanceException.class)
    ResponseEntity<ApiError> handle(AttendanceException ex, HttpServletRequest request) {
        HttpStatus status;
        String code;
        String message;
        switch (ex.kind()) {
            case INVALID_SUBMISSION -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ATT_INVALID_SUBMISSION";
                message = "Indiquez soit un jeton, soit un code court — et un seul des deux.";
            }
            case TOKEN_INVALID -> {
                status = HttpStatus.CONFLICT;
                code = "ATT_TOKEN_INVALID";
                message = "Ce code d'émargement est invalide ou a expiré. "
                        + "Demandez au formateur d'afficher un nouveau code.";
            }
            case SESSION_CLOSED -> {
                status = HttpStatus.CONFLICT;
                code = "ATT_SESSION_CLOSED";
                message = "Cette séance n'est pas ouverte à l'émargement.";
            }
            case NOT_ENROLLED -> {
                status = HttpStatus.CONFLICT;
                code = "ATT_NOT_ENROLLED";
                message = "Vous n'êtes pas inscrit à une classe de cette séance.";
            }
            case ENROLLMENT_AMBIGUOUS -> {
                status = HttpStatus.CONFLICT;
                code = "ATT_ENROLLMENT_AMBIGUOUS";
                message = "Votre inscription ne peut pas être déterminée de façon certaine ; "
                        + "contactez l'administration.";
            }
            case ALREADY_RECORDED -> {
                status = HttpStatus.CONFLICT;
                code = "ATT_ALREADY_RECORDED";
                message = "Votre présence a déjà été enregistrée pour ce point de contrôle.";
            }
            case TOKEN_BACKEND_UNAVAILABLE -> {
                status = HttpStatus.SERVICE_UNAVAILABLE;
                code = "ATT_TOKEN_BACKEND_UNAVAILABLE";
                message = "Le service d'émargement est momentanément indisponible. Réessayez dans un instant.";
            }
            case SESSION_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "SESSION_NOT_FOUND";
                message = "Aucune séance ne correspond à cet identifiant.";
            }
            case CHECKPOINT_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "ATT_CHECKPOINT_NOT_FOUND";
                message = "Aucun point de contrôle ne correspond à cet identifiant.";
            }
            default -> {
                status = HttpStatus.FORBIDDEN;
                code = "ATT_OPERATION_FORBIDDEN";
                message = "Vous n'êtes pas autorisé à effectuer cette opération.";
            }
        }
        ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), List.of());
        return ResponseEntity.status(status).body(body);
    }
}
