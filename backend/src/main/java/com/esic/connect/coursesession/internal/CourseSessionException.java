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
        /** L'appelant n'a pas le droit d'ouvrir / fermer / annuler cette séance. */
        OPERATION_FORBIDDEN,
        /** Motif d'annulation manquant ou vide (G1-C). */
        CANCEL_REASON_REQUIRED,
        // --- Remplacements (G1-C.2) ---
        /** Le compte remplaçant est inconnu, non actif ou sans rôle TEACHER actif. */
        SUBSTITUTE_NOT_ELIGIBLE,
        /** Le remplaçant proposé est le formateur principal de la séance. */
        SUBSTITUTE_IS_ORIGINAL,
        /** Période de validité malformée (fin ≤ début) ou motif manquant. */
        SUBSTITUTION_PERIOD_INVALID,
        /**
         * Période de validité syntaxiquement correcte mais sans chevauchement
         * réel avec la séance, ou débordant la marge tolérée avant / après
         * le créneau (G1-C.3).
         */
        SUBSTITUTION_OUTSIDE_SESSION,
        /** Une autre substitution active de la séance chevauche la période demandée. */
        SUBSTITUTION_OVERLAP,
        /** Aucune substitution pour cet identifiant dans cette séance. */
        SUBSTITUTION_NOT_FOUND,
        /** La substitution est déjà terminée. */
        SUBSTITUTION_ALREADY_ENDED,
        // --- Points de contrôle (V10) ---
        /** Aucun point de contrôle pour cet identifiant dans cette séance. */
        CHECKPOINT_NOT_FOUND,
        /** Transition impossible depuis l'état courant du point de contrôle. */
        CHECKPOINT_INVALID_STATE,
        /**
         * Un point de contrôle de ce type est déjà actif sur la séance
         * (EF-ATT-003). Conflit d'état, pas requête malformée : la
         * demande est bien formée, c'est la séance qui ne peut pas en
         * porter un second — deux {@code MORNING_ARRIVAL} rendraient le
         * résultat journalier indéterminé.
         */
        CHECKPOINT_TYPE_ALREADY_PRESENT,
        /** Type de point de contrôle hors liste ({@code START}/{@code END}/{@code CUSTOM}). */
        CHECKPOINT_INVALID_TYPE,
        /** Un ordre d'affichage identique existe déjà pour cette séance. */
        CHECKPOINT_ORDER_CONFLICT,
        /** Motif d'annulation manquant. */
        CHECKPOINT_REASON_REQUIRED,
        /** Séance déjà reportée : un second report rendrait l'historique ambigu. */
        ALREADY_POSTPONED,
        /** Une demande d'annulation est déjà en attente sur cette séance. */
        CANCELLATION_ALREADY_REQUESTED,
        /** Aucune demande d'annulation pour cet identifiant. */
        CANCELLATION_REQUEST_NOT_FOUND,
        /** Impossible d'ajouter / ouvrir un point de contrôle : la séance n'est pas ouverte. */
        CHECKPOINT_SESSION_NOT_OPEN,
        // --- Matière et salle (V35) ---
        /** La matière visée est inconnue. */
        SUBJECT_NOT_FOUND,
        /** La matière visée est archivée : elle ne peut plus être rattachée à une nouvelle séance. */
        SUBJECT_INACTIVE,
        /** La salle visée est inconnue ou archivée. */
        ROOM_NOT_FOUND,
        /**
         * Le formateur enseigne déjà une autre séance sur un horaire qui
         * chevauche celui demandé (RG-105) — physiquement impossible,
         * sauf lorsque les classes suivent la même séance : dans ce cas
         * elles se déclarent ensemble sur une seule séance, pas sur deux.
         */
        TEACHER_DOUBLE_BOOKING,
        /** La salle est déjà occupée par une autre séance sur un horaire qui chevauche celui demandé. */
        ROOM_DOUBLE_BOOKING
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
