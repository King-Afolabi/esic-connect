package com.esic.connect.academic.internal;

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
 * Traduit {@link AcademicException} en réponse {@link ApiError} homogène.
 * Aucun message ne divulgue de donnée personnelle. Aligné sur
 * {@code organization.internal.OrganizationExceptionHandler}.
 */
@RestControllerAdvice(assignableTypes = {
        AcademicYearController.class,
        ProgramController.class,
        ProgramLevelController.class,
        PromotionController.class,
        ClassGroupController.class
})
class AcademicExceptionHandler {

    @ExceptionHandler(AcademicException.class)
    ResponseEntity<ApiError> handle(AcademicException ex, HttpServletRequest request) {
        HttpStatus status;
        String code;
        String message;
        switch (ex.kind()) {
            case ACADEMIC_YEAR_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "ACADEMIC_YEAR_NOT_FOUND";
                message = "Aucune année scolaire ne correspond à cet identifiant.";
            }
            case PROGRAM_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "PROGRAM_NOT_FOUND";
                message = "Aucune formation ne correspond à cet identifiant.";
            }
            case PROGRAM_LEVEL_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "PROGRAM_LEVEL_NOT_FOUND";
                message = "Aucun niveau ne correspond à cet identifiant.";
            }
            case PROMOTION_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "PROMOTION_NOT_FOUND";
                message = "Aucune promotion ne correspond à cet identifiant.";
            }
            case CLASS_GROUP_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "CLASS_GROUP_NOT_FOUND";
                message = "Aucune classe ne correspond à cet identifiant.";
            }
            case SITE_NOT_FOUND -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ACAD_SITE_NOT_FOUND";
                message = "Aucun site ne correspond à l'identifiant fourni.";
            }
            case DUPLICATE_CODE -> {
                status = HttpStatus.CONFLICT;
                code = "ACAD_DUPLICATE_CODE";
                message = "Ce code est déjà utilisé dans ce périmètre.";
            }
            case INVALID_PROGRAM_TYPE -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ACAD_INVALID_PROGRAM_TYPE";
                message = "Type de formation invalide (BTS, BACHELOR, MASTER ou OTHER attendu).";
            }
            case INVALID_PERIOD -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ACAD_INVALID_PERIOD";
                message = "La date de fin doit être postérieure à la date de début.";
            }
            case PROMOTION_PERIOD_OUT_OF_YEAR -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ACAD_PROMOTION_PERIOD_OUT_OF_YEAR";
                message = "La période de la promotion doit être incluse dans celle de l'année scolaire.";
            }
            case ACADEMIC_YEAR_PERIOD_CONFLICT -> {
                status = HttpStatus.CONFLICT;
                code = "ACAD_ACADEMIC_YEAR_PERIOD_CONFLICT";
                message = "La nouvelle période exclut une promotion existante de cette année scolaire ; "
                        + "ajustez d'abord les promotions concernées.";
            }
            case PROGRAM_LEVEL_MISMATCH -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ACAD_PROGRAM_LEVEL_MISMATCH";
                message = "Le niveau choisi n'appartient pas à la formation de la promotion.";
            }
            case ARCHIVED_PARENT -> {
                status = HttpStatus.CONFLICT;
                code = "ACAD_ARCHIVED_PARENT";
                message = "Un élément parent est archivé.";
            }
            case ENTITY_ARCHIVED -> {
                status = HttpStatus.CONFLICT;
                code = "ACAD_ENTITY_ARCHIVED";
                message = "Cette entité est archivée : restaurez-la avant de la modifier.";
            }
            case INVALID_STATE -> {
                status = HttpStatus.CONFLICT;
                code = "ACAD_INVALID_STATE";
                message = "L'état actuel ne permet pas cette opération.";
            }
            case HAS_ACTIVE_CHILDREN -> {
                status = HttpStatus.CONFLICT;
                code = "ACAD_HAS_ACTIVE_CHILDREN";
                message = "Archivez d'abord les éléments actifs rattachés.";
            }
            case INVALID_SORT -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ACAD_INVALID_SORT";
                message = "Champ ou direction de tri non autorisé.";
            }
            default -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ACAD_INVALID_FILTER";
                message = "Valeur de filtre invalide.";
            }
        }
        ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), List.of());
        return ResponseEntity.status(status).body(body);
    }
}
