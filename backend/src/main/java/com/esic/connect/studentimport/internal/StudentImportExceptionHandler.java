package com.esic.connect.studentimport.internal;

import com.esic.connect.shared.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Traduit {@link StudentImportException} (et le dépassement de taille
 * multipart) en réponse {@link ApiError} homogène — codes {@code IMP_*}.
 * Aucun message ne divulgue de donnée personnelle ni de contenu de
 * cellule CSV. Portée limitée aux contrôleurs du module via
 * {@code basePackageClasses} (aligné sur la façon dont
 * {@code enrollment.internal.EnrollmentExceptionHandler} se restreint à
 * ses contrôleurs).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = StudentImportWeb.class)
class StudentImportExceptionHandler {

    @ExceptionHandler(StudentImportException.class)
    ResponseEntity<ApiError> handle(StudentImportException ex, HttpServletRequest request) {
        HttpStatus status;
        String code;
        String message;
        switch (ex.kind()) {
            case UNSUPPORTED_MEDIA_TYPE -> {
                status = HttpStatus.UNSUPPORTED_MEDIA_TYPE;
                code = "IMP_UNSUPPORTED_MEDIA_TYPE";
                message = "Seuls les fichiers CSV texte (UTF-8) sont acceptés.";
            }
            case FILE_TOO_LARGE -> {
                status = HttpStatus.PAYLOAD_TOO_LARGE;
                code = "IMP_FILE_TOO_LARGE";
                message = "Le fichier dépasse la taille maximale autorisée.";
            }
            case ENCODING_INVALID -> {
                status = HttpStatus.BAD_REQUEST;
                code = "IMP_ENCODING_INVALID";
                message = "Le fichier n'est pas encodé en UTF-8.";
            }
            case MISSING_COLUMN -> {
                status = HttpStatus.BAD_REQUEST;
                code = "IMP_MISSING_COLUMN";
                message = "Une ou plusieurs colonnes obligatoires sont absentes de l'en-tête.";
            }
            case TOO_MANY_ROWS -> {
                status = HttpStatus.BAD_REQUEST;
                code = "IMP_TOO_MANY_ROWS";
                message = "Le fichier contient plus de lignes que la limite autorisée.";
            }
            case NO_DATA_ROWS -> {
                status = HttpStatus.BAD_REQUEST;
                code = "IMP_NO_DATA_ROWS";
                message = "Le fichier ne contient aucune ligne de données exploitable.";
            }
            case HEADER_UNREADABLE -> {
                status = HttpStatus.BAD_REQUEST;
                code = "IMP_HEADER_UNREADABLE";
                message = "L'en-tête du fichier est illisible ou absent.";
            }
            case JOB_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "IMP_JOB_NOT_FOUND";
                message = "Aucun import ne correspond à cet identifiant.";
            }
            case JOB_FORBIDDEN -> {
                status = HttpStatus.FORBIDDEN;
                code = "IMP_JOB_FORBIDDEN";
                message = "Vous n'êtes pas autorisé à consulter cet import.";
            }
            case INVALID_SORT -> {
                status = HttpStatus.BAD_REQUEST;
                code = "IMP_INVALID_SORT";
                message = "Champ ou direction de tri non autorisé.";
            }
            case INVALID_FILTER -> {
                status = HttpStatus.BAD_REQUEST;
                code = "IMP_INVALID_FILTER";
                message = "Valeur de filtre invalide.";
            }
            case SCOPE_FORBIDDEN -> {
                status = HttpStatus.FORBIDDEN;
                code = "IMP_SCOPE_FORBIDDEN";
                message = "Le périmètre demandé n'est pas dans votre périmètre pédagogique.";
            }
            case NOT_CONFIRMABLE -> {
                status = HttpStatus.CONFLICT;
                code = "IMP_NOT_CONFIRMABLE";
                message = "Cette simulation n'est pas confirmable : corrigez le fichier et relancez un import.";
            }
            case STALE_SIMULATION -> {
                status = HttpStatus.CONFLICT;
                code = "IMP_STALE_SIMULATION";
                message = "La simulation n'est plus à jour : des lignes sont devenues invalides. "
                        + "Consultez les anomalies rafraîchies et relancez un import.";
            }
            case SIMULATION_EXPIRED -> {
                status = HttpStatus.CONFLICT;
                code = "IMP_SIMULATION_EXPIRED";
                message = "Cette simulation a expiré : relancez un import.";
            }
            case JOB_CANCELLED -> {
                status = HttpStatus.CONFLICT;
                code = "IMP_JOB_CANCELLED";
                message = "Cet import a été annulé.";
            }
            case CONFIRM_FORBIDDEN -> {
                status = HttpStatus.FORBIDDEN;
                code = "IMP_CONFIRM_FORBIDDEN";
                message = "Vous n'êtes pas autorisé à confirmer cet import.";
            }
            case JOB_NOT_CANCELLABLE -> {
                status = HttpStatus.CONFLICT;
                code = "IMP_JOB_NOT_CANCELLABLE";
                message = "Cet import ne peut plus être annulé.";
            }
            case STUDENT_NUMBER_ALLOC_FAILED -> {
                status = HttpStatus.CONFLICT;
                code = "IMP_STUDENT_NUMBER_ALLOC_FAILED";
                message = "L'attribution automatique d'un numéro étudiant a échoué ; aucun import appliqué.";
            }
            case STUDENT_NUMBER_EXHAUSTED -> {
                status = HttpStatus.CONFLICT;
                code = "IMP_STUDENT_NUMBER_EXHAUSTED";
                message = "La série de numéros étudiants est épuisée pour cette année scolaire.";
            }
            default -> {
                status = HttpStatus.BAD_REQUEST;
                code = "IMP_INVALID_REQUEST";
                message = "Requête invalide.";
            }
        }
        return build(status, code, message, request, details(ex.detail()));
    }

    /**
     * Un fichier au-delà de {@code spring.servlet.multipart.max-file-size}
     * est intercepté par le conteneur avant même le contrôleur : on le
     * retraduit en {@code IMP_FILE_TOO_LARGE} plutôt qu'en 500 générique.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleMaxUpload(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "IMP_FILE_TOO_LARGE",
                "Le fichier dépasse la taille maximale autorisée.", request, List.of());
    }

    private static List<String> details(Object detail) {
        if (detail == null) {
            return List.of();
        }
        if (detail instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(detail));
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                                  HttpServletRequest request, List<String> details) {
        ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), details);
        return ResponseEntity.status(status).body(body);
    }
}
