package com.esic.connect.planning.internal;

import com.esic.connect.shared.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Traduit {@link PlanningException} (et le dépassement de taille
 * multipart) en réponse {@link ApiError} homogène — codes {@code PLAN_*}.
 * Aucun message ne divulgue de donnée personnelle, de jeton, de chemin
 * physique ni de contenu de cellule. Portée limitée aux contrôleurs du
 * module via {@code basePackageClasses} (aligné sur
 * {@code studentimport.internal.StudentImportExceptionHandler}).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = PlanningWeb.class)
class PlanningExceptionHandler {

    @ExceptionHandler(PlanningException.class)
    ResponseEntity<ApiError> handle(PlanningException ex, HttpServletRequest request) {
        HttpStatus status;
        String code;
        String message;
        switch (ex.kind()) {
            case JOB_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "PLAN_JOB_NOT_FOUND";
                message = "Aucun import de planning ne correspond à cet identifiant.";
            }
            case SCHEDULE_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "PLAN_SCHEDULE_NOT_FOUND";
                message = "Aucun planning ne correspond à cet identifiant.";
            }
            case VERSION_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "PLAN_VERSION_NOT_FOUND";
                message = "Aucune version de planning ne correspond à cet identifiant.";
            }
            case TARGET_UNRESOLVED -> {
                status = HttpStatus.BAD_REQUEST;
                code = "PLAN_TARGET_UNRESOLVED";
                message = "La classe visée est inconnue.";
            }
            case UNSUPPORTED_FILE -> {
                status = HttpStatus.UNSUPPORTED_MEDIA_TYPE;
                code = "PLAN_UNSUPPORTED_FILE";
                message = "Seuls les fichiers CSV texte (UTF-8) sont acceptés.";
            }
            case FILE_UNREADABLE -> {
                status = HttpStatus.BAD_REQUEST;
                code = "PLAN_FILE_UNREADABLE";
                message = "Le fichier est vide ou ne contient aucune ligne exploitable.";
            }
            case MISSING_COLUMNS -> {
                status = HttpStatus.BAD_REQUEST;
                code = "PLAN_MISSING_COLUMNS";
                message = "Une ou plusieurs colonnes obligatoires sont absentes de l'en-tête.";
            }
            case TOO_MANY_ROWS -> {
                status = HttpStatus.BAD_REQUEST;
                code = "PLAN_TOO_MANY_ROWS";
                message = "Le fichier contient plus de lignes que la limite autorisée.";
            }
            case SCOPE_FORBIDDEN -> {
                status = HttpStatus.FORBIDDEN;
                code = "PLAN_SCOPE_FORBIDDEN";
                message = "Cette classe n'est pas dans votre périmètre pédagogique.";
            }
            case BLOCKING_ISSUES -> {
                status = HttpStatus.CONFLICT;
                code = "PLAN_BLOCKING_ISSUES";
                message = "La publication est impossible tant qu'une ligne est en anomalie bloquante.";
            }
            case INVALID_JOB_STATE -> {
                status = HttpStatus.CONFLICT;
                code = "PLAN_INVALID_JOB_STATE";
                message = "L'état de cet import ne permet pas cette opération.";
            }
            case JOB_EXPIRED -> {
                status = HttpStatus.CONFLICT;
                code = "PLAN_JOB_EXPIRED";
                message = "Cette simulation a expiré : relancez un import.";
            }
            case INVALID_SORT -> {
                status = HttpStatus.BAD_REQUEST;
                code = "PLAN_INVALID_SORT";
                message = "Champ ou direction de tri non autorisé.";
            }
            default -> {
                status = HttpStatus.BAD_REQUEST;
                code = "PLAN_INVALID_FILTER";
                message = "Valeur de filtre invalide.";
            }
        }
        return build(status, code, message, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleMaxUpload(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "PLAN_UNSUPPORTED_FILE",
                "Le fichier dépasse la taille maximale autorisée.", request);
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ApiError> handleMultipart(MultipartException ex, HttpServletRequest request) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("size") || message.contains("exceed") || message.contains("large")) {
            return build(HttpStatus.PAYLOAD_TOO_LARGE, "PLAN_UNSUPPORTED_FILE",
                    "Le fichier dépasse la taille maximale autorisée.", request);
        }
        return build(HttpStatus.BAD_REQUEST, "PLAN_UNSUPPORTED_FILE",
                "Le fichier téléversé n'a pas pu être lu.", request);
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                                  HttpServletRequest request) {
        ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), List.of());
        return ResponseEntity.status(status).body(body);
    }
}
