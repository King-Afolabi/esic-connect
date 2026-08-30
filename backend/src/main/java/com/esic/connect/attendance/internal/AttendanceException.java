package com.esic.connect.attendance.internal;

/**
 * Erreur métier du module {@code attendance}. Le {@link Kind} détermine
 * le code HTTP et le code d'erreur {@code ATT_*} / {@code SESSION_*}
 * exposés ({@link AttendanceExceptionHandler}). Aucun message ne divulgue
 * de jeton, de code court, d'identifiant interne, de SQL ni de trace.
 */
class AttendanceException extends RuntimeException {

    enum Kind {
        /** Aucun des deux champs (jeton / code court) — ou les deux à la fois. */
        INVALID_SUBMISSION,
        /**
         * Jeton ou code court absent de Redis : inconnu, expiré ou déjà
         * invalidé (rotation / fermeture de séance). Un seul code : Redis
         * ne distingue pas « expiré » de « jamais émis ».
         */
        TOKEN_INVALID,
        /** La séance n'est plus ouverte : plus aucun émargement possible. */
        SESSION_CLOSED,
        /** L'apprenant n'a pas d'inscription active dans une classe de la séance. */
        NOT_ENROLLED,
        /** Plusieurs inscriptions actives correspondent : impossible de trancher sans risque. */
        ENROLLMENT_AMBIGUOUS,
        /** Une présence existe déjà pour cet apprenant et ce point de contrôle. */
        ALREADY_RECORDED,
        /** Backend de jetons (Redis) indisponible — jamais de validation dégradée. */
        TOKEN_BACKEND_UNAVAILABLE,
        /** Aucune séance pour cet identifiant. */
        SESSION_NOT_FOUND,
        /** L'appelant n'a pas le droit d'effectuer cette opération sur cette séance. */
        OPERATION_FORBIDDEN
    }

    private final Kind kind;

    AttendanceException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
