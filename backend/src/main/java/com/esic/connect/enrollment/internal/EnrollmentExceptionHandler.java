package com.esic.connect.enrollment.internal;

import com.esic.connect.shared.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Traduit {@link EnrollmentException} en réponse {@link ApiError} homogène
 * (codes {@code ENR_*}), et retraduit une collision concurrente sur la
 * contrainte {@code uq_enrollment_active_per_year} en 409 plutôt qu'en
 * 500 générique. Aucun message ne divulgue de donnée personnelle. Aligné
 * sur {@code academic.internal.AcademicExceptionHandler}.
 */
@RestControllerAdvice(assignableTypes = {
        StudentProfileController.class,
        EnrollmentController.class
})
class EnrollmentExceptionHandler {

    @ExceptionHandler(EnrollmentException.class)
    ResponseEntity<ApiError> handle(EnrollmentException ex, HttpServletRequest request) {
        HttpStatus status;
        String code;
        String message;
        switch (ex.kind()) {
            case STUDENT_PROFILE_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "ENR_STUDENT_PROFILE_NOT_FOUND";
                message = "Aucun profil apprenant ne correspond à cet identifiant.";
            }
            case ENROLLMENT_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "ENR_ENROLLMENT_NOT_FOUND";
                message = "Aucune inscription ne correspond à cet identifiant.";
            }
            case CLASS_GROUP_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "ENR_CLASS_GROUP_NOT_FOUND";
                message = "Aucune classe ne correspond à cet identifiant.";
            }
            case USER_NOT_ELIGIBLE -> {
                status = HttpStatus.UNPROCESSABLE_ENTITY;
                code = "ENR_USER_NOT_ELIGIBLE";
                message = "Le compte ciblé doit exister, être actif et porter le rôle apprenant.";
            }
            case PROFILE_ALREADY_EXISTS -> {
                status = HttpStatus.CONFLICT;
                code = "ENR_PROFILE_EXISTS";
                message = "Ce compte possède déjà un profil apprenant.";
            }
            case DUPLICATE_STUDENT_NUMBER -> {
                status = HttpStatus.CONFLICT;
                code = "ENR_DUPLICATE_STUDENT_NUMBER";
                message = "Ce numéro étudiant est déjà attribué.";
            }
            case STUDENT_PROFILE_ARCHIVED -> {
                status = HttpStatus.CONFLICT;
                code = "ENR_STUDENT_PROFILE_ARCHIVED";
                message = "Ce profil apprenant est archivé.";
            }
            case ARCHIVED_PARENT -> {
                status = HttpStatus.CONFLICT;
                code = "ENR_ARCHIVED_PARENT";
                message = "La classe visée ou un élément parent (promotion, formation, année) est archivé.";
            }
            case ACTIVE_ENROLLMENT_EXISTS -> {
                status = HttpStatus.CONFLICT;
                code = "ENR_ACTIVE_ENROLLMENT_EXISTS";
                message = "Cet apprenant a déjà une inscription active pour cette année scolaire ; "
                        + "clôturez-la ou utilisez un changement de classe.";
            }
            case ENROLLMENT_NOT_ACTIVE -> {
                status = HttpStatus.CONFLICT;
                code = "ENR_ENROLLMENT_NOT_ACTIVE";
                message = "Cette inscription n'est pas active : l'opération est impossible.";
            }
            case SAME_CLASS -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ENR_SAME_CLASS";
                message = "L'apprenant est déjà inscrit dans cette classe.";
            }
            case DATE_INVALID -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ENR_DATE_INVALID";
                message = "La date de fin doit être postérieure ou égale à la date de début de l'inscription.";
            }
            case INVALID_CLOSE_STATUS -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ENR_INVALID_CLOSE_STATUS";
                message = "Statut de clôture invalide (COMPLETED ou WITHDRAWN attendu).";
            }
            case INVALID_SORT -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ENR_INVALID_SORT";
                message = "Champ ou direction de tri non autorisé.";
            }
            default -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ENR_INVALID_FILTER";
                message = "Valeur de filtre invalide.";
            }
        }
        return build(status, code, message, request);
    }

    /**
     * Filet de sécurité pour les courses entre deux écritures : la
     * transaction du service est déjà annulée quand l'exception parvient
     * ici (contrainte violée au {@code flush}). Seules les collisions
     * <em>connues</em> deviennent un 409 ciblé ; toute autre violation
     * d'intégrité est relancée (500 via le gestionnaire global).
     *
     * <ul>
     *   <li>{@code uq_enrollment_active_per_year} → une seconde inscription
     *       {@code ACTIVE} sur le même couple (apprenant, année) ;</li>
     *   <li>{@code uq_student_profile_user} → un second profil pour le même
     *       compte ;</li>
     *   <li>{@code uq_student_profile_student_number} → numéro étudiant
     *       déjà attribué.</li>
     * </ul>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        if (EnrollmentPersistence.matchesConstraint(ex, EnrollmentPersistence.ACTIVE_ENROLLMENT_CONSTRAINT)) {
            return build(HttpStatus.CONFLICT, "ENR_ACTIVE_ENROLLMENT_EXISTS",
                    "Cet apprenant a déjà une inscription active pour cette année scolaire ; "
                            + "clôturez-la ou utilisez un changement de classe.", request);
        }
        if (EnrollmentPersistence.matchesConstraint(ex, EnrollmentPersistence.PROFILE_USER_CONSTRAINT)) {
            return build(HttpStatus.CONFLICT, "ENR_PROFILE_EXISTS",
                    "Ce compte possède déjà un profil apprenant.", request);
        }
        if (EnrollmentPersistence.matchesConstraint(ex, EnrollmentPersistence.PROFILE_STUDENT_NUMBER_CONSTRAINT)) {
            return build(HttpStatus.CONFLICT, "ENR_DUPLICATE_STUDENT_NUMBER",
                    "Ce numéro étudiant est déjà attribué.", request);
        }
        throw ex;
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                                  HttpServletRequest request) {
        ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), List.of());
        return ResponseEntity.status(status).body(body);
    }
}
