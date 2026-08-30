package com.esic.connect.coursesession.internal;

/**
 * Erreur métier du module {@code coursesession}. Le {@link Kind}
 * détermine le code HTTP et le code d'erreur {@code SESSION_*} exposés
 * ({@link CourseSessionExceptionHandler}). Aucun message ne divulgue de
 * donnée personnelle ni de détail d'infrastructure.
 */
class CourseSessionException extends RuntimeException {

    enum Kind {
        /** Aucune séance pour ce {@code public_id} (ou identifiant mal formé). */
        SESSION_NOT_FOUND,
        /** Transition de cycle de vie impossible depuis l'état courant. */
        INVALID_STATE,
        /** Période incohérente : fin antérieure ou égale au début. */
        INVALID_PERIOD,
        /** Fuseau horaire absent de la base IANA. */
        INVALID_TIME_ZONE,
        /** Aucune classe fournie à la création. */
        NO_CLASS,
        /** Champ ou direction de tri hors liste blanche. */
        INVALID_SORT,
        /** Valeur de filtre invalide (statut...). */
        INVALID_FILTER,
        /** Le compte formateur visé est inconnu. */
        TEACHER_NOT_FOUND,
        /** Le compte visé n'est pas un formateur éligible (compte non actif ou sans rôle TEACHER actif). */
        TEACHER_NOT_ELIGIBLE,
        /** Une classe visée est inconnue. */
        CLASS_NOT_FOUND,
        /** Une classe visée (ou un maillon de sa chaîne) est archivée. */
        CLASS_INACTIVE,
        /** La classe visée est hors du périmètre pédagogique de l'appelant. */
        SCOPE_FORBIDDEN,
        /** L'appelant n'a pas le droit d'ouvrir / fermer cette séance. */
        OPERATION_FORBIDDEN,
        // --- Points de contrôle (V10) ---
        /** Aucun point de contrôle pour cet identifiant dans cette séance. */
        CHECKPOINT_NOT_FOUND,
        /** Transition impossible depuis l'état courant du point de contrôle. */
        CHECKPOINT_INVALID_STATE,
        /** Type de point de contrôle hors liste ({@code START}/{@code END}/{@code CUSTOM}). */
        CHECKPOINT_INVALID_TYPE,
        /** Un ordre d'affichage identique existe déjà pour cette séance. */
        CHECKPOINT_ORDER_CONFLICT,
        /** Motif d'annulation manquant. */
        CHECKPOINT_REASON_REQUIRED,
        /** Impossible d'ajouter / ouvrir un point de contrôle : la séance n'est pas ouverte. */
        CHECKPOINT_SESSION_NOT_OPEN
    }

    private final Kind kind;

    CourseSessionException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
