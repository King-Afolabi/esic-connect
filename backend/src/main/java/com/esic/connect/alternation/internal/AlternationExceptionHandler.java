package com.esic.connect.alternation.internal;

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
 * Traduit {@link AlternationException} en réponse {@link ApiError}
 * homogène (codes {@code ALT_*}). Aucun message ne divulgue de donnée
 * personnelle. Aligné sur
 * {@code enrollment.internal.EnrollmentExceptionHandler}. Le
 * {@code detail} non sensible d'une configuration invalide est ajouté à
 * la liste {@code details} de l'{@link ApiError}.
 */
@RestControllerAdvice(assignableTypes = {
        WorkStudyPatternController.class,
        ClassWorkStudyPatternController.class,
        StudentScheduleExceptionController.class
})
class AlternationExceptionHandler {

    @ExceptionHandler(AlternationException.class)
    ResponseEntity<ApiError> handle(AlternationException ex, HttpServletRequest request) {
        HttpStatus status;
        String code;
        String message;
        switch (ex.kind()) {
            case PATTERN_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "ALT_PATTERN_NOT_FOUND";
                message = "Aucun modèle de rythme ne correspond à cet identifiant.";
            }
            case CLASS_ASSIGNMENT_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "ALT_CLASS_ASSIGNMENT_NOT_FOUND";
                message = "Aucune affectation de rythme ne correspond à cet identifiant.";
            }
            case EXCEPTION_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "ALT_EXCEPTION_NOT_FOUND";
                message = "Aucune exception individuelle ne correspond à cet identifiant.";
            }
            case CLASS_GROUP_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "ALT_CLASS_GROUP_NOT_FOUND";
                message = "Aucune classe ne correspond à cet identifiant.";
            }
            case ENROLLMENT_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "ALT_ENROLLMENT_NOT_FOUND";
                message = "Aucune inscription ne correspond à cet identifiant.";
            }
            case DUPLICATE_CODE -> {
                status = HttpStatus.CONFLICT;
                code = "ALT_DUPLICATE_CODE";
                message = "Ce code de modèle de rythme est déjà utilisé.";
            }
            case INVALID_PATTERN_TYPE -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ALT_INVALID_PATTERN_TYPE";
                message = "Type de rythme invalide.";
            }
            case INVALID_EXCEPTION_TYPE -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ALT_INVALID_EXCEPTION_TYPE";
                message = "Type d'exception invalide.";
            }
            case INVALID_CONFIGURATION -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ALT_INVALID_CONFIGURATION";
                message = "Configuration de rythme invalide ou incohérente.";
            }
            case INVALID_TIME_ZONE -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ALT_INVALID_TIME_ZONE";
                message = "Fuseau horaire inconnu (identifiant IANA attendu, ex. Europe/Paris).";
            }
            case INVALID_PERIOD -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ALT_INVALID_PERIOD";
                message = "La fin doit être postérieure au début.";
            }
            case INVALID_SORT -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ALT_INVALID_SORT";
                message = "Champ ou direction de tri non autorisé.";
            }
            case PATTERN_ARCHIVED -> {
                status = HttpStatus.CONFLICT;
                code = "ALT_PATTERN_ARCHIVED";
                message = "Ce modèle de rythme est archivé : il ne peut pas être affecté.";
            }
            case CLASS_NOT_ASSIGNABLE -> {
                status = HttpStatus.CONFLICT;
                code = "ALT_CLASS_NOT_ASSIGNABLE";
                message = "La classe visée ou un élément parent (promotion, formation, année) est archivé.";
            }
            case INVALID_STATE -> {
                status = HttpStatus.CONFLICT;
                code = "ALT_INVALID_STATE";
                message = "L'état actuel ne permet pas cette opération.";
            }
            case ENROLLMENT_NOT_USABLE -> {
                status = HttpStatus.CONFLICT;
                code = "ALT_ENROLLMENT_NOT_USABLE";
                message = "Cette inscription n'est pas active : aucune exception ne peut y être rattachée.";
            }
            case ASSIGNMENT_OVERLAP -> {
                status = HttpStatus.CONFLICT;
                code = "ALT_ASSIGNMENT_OVERLAP";
                message = "Cette période chevauche une affectation de rythme existante de la classe.";
            }
            case OPEN_ASSIGNMENT_EXISTS -> {
                status = HttpStatus.CONFLICT;
                code = "ALT_OPEN_ASSIGNMENT_EXISTS";
                message = "Cette classe a déjà une affectation de rythme ouverte ; clôturez-la d'abord.";
            }
            case ASSIGNMENT_ALREADY_CLOSED -> {
                status = HttpStatus.CONFLICT;
                code = "ALT_ASSIGNMENT_ALREADY_CLOSED";
                message = "Cette affectation est déjà clôturée.";
            }
            case ASSIGNMENT_CLOSE_CONFLICT -> {
                status = HttpStatus.CONFLICT;
                code = "ALT_ASSIGNMENT_CLOSE_CONFLICT";
                message = "Cette date de clôture chevaucherait l'affectation de rythme suivante de la classe.";
            }
            case EXCEPTION_ALREADY_CANCELLED -> {
                status = HttpStatus.CONFLICT;
                code = "ALT_EXCEPTION_ALREADY_CANCELLED";
                message = "Cette exception est déjà annulée.";
            }
            case EXCEPTION_OVERLAP -> {
                status = HttpStatus.CONFLICT;
                code = "ALT_EXCEPTION_OVERLAP";
                message = "Une exception active de même type recouvre déjà cette période pour cette inscription.";
            }
            case OUT_OF_SCOPE -> {
                status = HttpStatus.FORBIDDEN;
                code = "ALT_FORBIDDEN";
                message = "Cette classe est hors de votre périmètre pédagogique.";
            }
            default -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ALT_INVALID_FILTER";
                message = "Valeur de filtre invalide.";
            }
        }
        List<String> details = ex.detail() == null ? List.of() : List.of(ex.detail());
        ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), details);
        return ResponseEntity.status(status).body(body);
    }
}
