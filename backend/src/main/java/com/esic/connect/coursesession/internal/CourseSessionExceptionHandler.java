package com.esic.connect.coursesession.internal;

import com.esic.connect.shared.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Traduit {@link CourseSessionException} en réponse {@link ApiError}
 * homogène (codes {@code SESSION_*}). Aligné sur
 * {@code alternation.internal.AlternationExceptionHandler}.
 */
@RestControllerAdvice(assignableTypes = {CourseSessionController.class, AttendanceCheckpointController.class})
class CourseSessionExceptionHandler {

    /**
     * Course concurrente sur le cycle de vie d'une séance / d'un point de
     * contrôle (ouvrir / fermer / annuler simultanés) : le perdant du
     * verrou optimiste voit un {@code 409} contrôlé, jamais un {@code 500}.
     * La transition qu'il tentait n'est de toute façon plus valide depuis
     * l'état courant.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleConcurrentChange(HttpServletRequest request) {
        ApiError body = new ApiError(Instant.now(), HttpStatus.CONFLICT.value(),
                "SESSION_INVALID_STATE",
                "L'état de la séance a changé entre-temps : rechargez avant de réessayer.",
                request.getRequestURI(), UUID.randomUUID().toString(), List.of());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(CourseSessionException.class)
    ResponseEntity<ApiError> handle(CourseSessionException ex, HttpServletRequest request) {
        HttpStatus status;
        String code;
        String message;
        switch (ex.kind()) {
            case SESSION_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "SESSION_NOT_FOUND";
                message = "Aucune séance ne correspond à cet identifiant.";
            }
            case INVALID_STATE -> {
                status = HttpStatus.CONFLICT;
                code = "SESSION_INVALID_STATE";
                message = "L'état actuel de la séance ne permet pas cette opération.";
            }
            case INVALID_PERIOD -> {
                status = HttpStatus.BAD_REQUEST;
                code = "SESSION_INVALID_PERIOD";
                message = "La fin de la séance doit être postérieure à son début.";
            }
            case INVALID_TIME_ZONE -> {
                status = HttpStatus.BAD_REQUEST;
                code = "SESSION_INVALID_TIME_ZONE";
                message = "Fuseau horaire inconnu (identifiant IANA attendu, ex. Europe/Paris).";
            }
            case NO_CLASS -> {
                status = HttpStatus.BAD_REQUEST;
                code = "SESSION_NO_CLASS";
                message = "Une séance doit cibler au moins une classe.";
            }
            case INVALID_SORT -> {
                status = HttpStatus.BAD_REQUEST;
                code = "SESSION_INVALID_SORT";
                message = "Champ ou direction de tri non autorisé.";
            }
            case TEACHER_NOT_FOUND -> {
                status = HttpStatus.BAD_REQUEST;
                code = "SESSION_TEACHER_NOT_FOUND";
                message = "Le formateur indiqué est introuvable.";
            }
            case TEACHER_NOT_ELIGIBLE -> {
                status = HttpStatus.CONFLICT;
                code = "SESSION_TEACHER_NOT_ELIGIBLE";
                message = "Ce compte n'est pas un formateur actif : il ne peut pas animer une séance.";
            }
            case CLASS_NOT_FOUND -> {
                status = HttpStatus.BAD_REQUEST;
                code = "SESSION_CLASS_NOT_FOUND";
                message = "Une des classes indiquées est introuvable.";
            }
            case CLASS_INACTIVE -> {
                status = HttpStatus.CONFLICT;
                code = "SESSION_CLASS_INACTIVE";
                message = "Une des classes indiquées (ou un élément parent) est archivée.";
            }
            case SCOPE_FORBIDDEN -> {
                status = HttpStatus.FORBIDDEN;
                code = "SESSION_SCOPE_FORBIDDEN";
                message = "Cette classe est hors de votre périmètre pédagogique.";
            }
            case OPERATION_FORBIDDEN -> {
                status = HttpStatus.FORBIDDEN;
                code = "SESSION_OPERATION_FORBIDDEN";
                message = "Vous n'êtes pas autorisé à effectuer cette opération sur cette séance.";
            }
            case CANCEL_REASON_REQUIRED -> {
                status = HttpStatus.BAD_REQUEST;
                code = "SESSION_CANCEL_REASON_REQUIRED";
                message = "Un motif est obligatoire pour annuler une séance.";
            }
            case SUBSTITUTE_NOT_ELIGIBLE -> {
                status = HttpStatus.CONFLICT;
                code = "SESSION_SUBSTITUTE_NOT_ELIGIBLE";
                message = "Ce compte n'est pas un formateur actif : il ne peut pas remplacer sur cette séance.";
            }
            case SUBSTITUTE_IS_ORIGINAL -> {
                status = HttpStatus.CONFLICT;
                code = "SESSION_SUBSTITUTE_IS_ORIGINAL";
                message = "Le remplaçant ne peut pas être le formateur principal de la séance.";
            }
            case SUBSTITUTION_PERIOD_INVALID -> {
                status = HttpStatus.BAD_REQUEST;
                code = "SESSION_SUBSTITUTION_PERIOD_INVALID";
                message = "La période du remplacement est invalide (fin postérieure au début, motif obligatoire).";
            }
            case SUBSTITUTION_OUTSIDE_SESSION -> {
                status = HttpStatus.UNPROCESSABLE_ENTITY;
                code = "SESSION_SUBSTITUTION_OUTSIDE_SESSION";
                message = "La période du remplacement doit chevaucher la séance "
                        + "(au plus 60 minutes avant son début et après sa fin).";
            }
            case SUBSTITUTION_OVERLAP -> {
                status = HttpStatus.CONFLICT;
                code = "SESSION_SUBSTITUTION_OVERLAP";
                message = "Un remplacement actif de cette séance chevauche déjà la période demandée.";
            }
            case SUBSTITUTION_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "SESSION_SUBSTITUTION_NOT_FOUND";
                message = "Aucun remplacement ne correspond à cet identifiant pour cette séance.";
            }
            case SUBSTITUTION_ALREADY_ENDED -> {
                status = HttpStatus.CONFLICT;
                code = "SESSION_SUBSTITUTION_ALREADY_ENDED";
                message = "Ce remplacement est déjà terminé.";
            }
            case CHECKPOINT_NOT_FOUND -> {
                status = HttpStatus.NOT_FOUND;
                code = "ATT_CHECKPOINT_NOT_FOUND";
                message = "Aucun point de contrôle ne correspond à cet identifiant.";
            }
            case CHECKPOINT_INVALID_STATE -> {
                status = HttpStatus.CONFLICT;
                code = "ATT_CHECKPOINT_INVALID_STATE";
                message = "L'état actuel du point de contrôle ne permet pas cette opération.";
            }
            case CHECKPOINT_INVALID_TYPE -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ATT_CHECKPOINT_INVALID_TYPE";
                message = "Type de point de contrôle invalide (START, END ou CUSTOM attendu).";
            }
            case CHECKPOINT_ORDER_CONFLICT -> {
                status = HttpStatus.CONFLICT;
                code = "ATT_CHECKPOINT_ORDER_CONFLICT";
                message = "Un point de contrôle occupe déjà cet ordre d'affichage.";
            }
            case CHECKPOINT_REASON_REQUIRED -> {
                status = HttpStatus.BAD_REQUEST;
                code = "ATT_MANUAL_REASON_REQUIRED";
                message = "Un motif est obligatoire pour annuler un point de contrôle.";
            }
            case CHECKPOINT_SESSION_NOT_OPEN -> {
                status = HttpStatus.CONFLICT;
                code = "ATT_CHECKPOINT_INVALID_STATE";
                message = "La séance doit être ouverte pour gérer ses points de contrôle.";
            }
            default -> {
                status = HttpStatus.BAD_REQUEST;
                code = "SESSION_INVALID_FILTER";
                message = "Valeur de filtre invalide.";
            }
        }
        ApiError body = new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID().toString(), List.of());
        return ResponseEntity.status(status).body(body);
    }
}
