package com.esic.connect.academic.internal;

/**
 * Erreur métier du référentiel académique. Le {@link Kind} détermine le
 * code HTTP et le code d'erreur exposés ({@link AcademicExceptionHandler}).
 * Aucun message ne contient de donnée personnelle.
 */
class AcademicException extends RuntimeException {

    enum Kind {
        /** Aucune année scolaire pour ce {@code public_id}. */
        ACADEMIC_YEAR_NOT_FOUND,
        /** Aucune formation pour ce {@code public_id}. */
        PROGRAM_NOT_FOUND,
        /** Aucun niveau pour ce {@code public_id}. */
        PROGRAM_LEVEL_NOT_FOUND,
        /** Aucune promotion pour ce {@code public_id}. */
        PROMOTION_NOT_FOUND,
        /** Aucune classe/groupe pour ce {@code public_id}. */
        CLASS_GROUP_NOT_FOUND,
        /** Aucun site actif ne correspond au {@code public_id} fourni. */
        SITE_NOT_FOUND,
        /** Code déjà utilisé dans le périmètre concerné. */
        DUPLICATE_CODE,
        /** Type de formation hors {@link ProgramType}. */
        INVALID_PROGRAM_TYPE,
        /** Période incohérente (date de fin antérieure ou égale à la date de début). */
        INVALID_PERIOD,
        /** La période de la promotion sort de celle de son année scolaire. */
        PROMOTION_PERIOD_OUT_OF_YEAR,
        /**
         * Modification d'année scolaire refusée : la nouvelle période
         * exclurait une promotion existante dont la période est renseignée.
         */
        ACADEMIC_YEAR_PERIOD_CONFLICT,
        /** Le niveau choisi n'appartient pas à la formation de la promotion. */
        PROGRAM_LEVEL_MISMATCH,
        /** Opération refusée : un parent (formation, année, promotion, niveau, site) est archivé. */
        ARCHIVED_PARENT,
        /** Opération refusée : l'entité visée est archivée. */
        ENTITY_ARCHIVED,
        /** Transition d'état impossible (ex. archiver une entité déjà archivée). */
        INVALID_STATE,
        /** Archivage refusé : des enfants actifs subsistent. */
        HAS_ACTIVE_CHILDREN,
        /** Champ ou direction de tri hors liste blanche. */
        INVALID_SORT,
        /** Valeur de filtre invalide (statut...). */
        INVALID_FILTER,
        /**
         * L'appelant n'a pas d'affectation effective sur la formation
         * visée (contrôle de périmètre). Exposé {@code ACAD_FORBIDDEN}.
         */
        OUT_OF_SCOPE,
        /** Aucune affectation de responsable pédagogique pour ce {@code public_id} ({@code ACAD_ASSIGNMENT_NOT_FOUND}). */
        PEDAGOGICAL_ASSIGNMENT_NOT_FOUND,
        /** Rôle d'affectation hors {@link PedagogicalAssignmentRole}. */
        INVALID_ASSIGNMENT_ROLE,
        /**
         * Cible d'affectation inéligible : compte inexistant, archivé ou
         * sans rôle actif {@code PEDAGOGICAL_MANAGER}
         * ({@code ACAD_TARGET_NOT_ELIGIBLE}).
         */
        ASSIGNMENT_TARGET_NOT_ELIGIBLE,
        /**
         * Une affectation {@code PRIMARY_MANAGER} active existe déjà pour
         * cette formation ({@code ACAD_PRIMARY_MANAGER_EXISTS}).
         */
        PRIMARY_MANAGER_ALREADY_ASSIGNED,
        /** Clôture demandée sur une affectation déjà clôturée ({@code ACAD_ASSIGNMENT_ALREADY_CLOSED}). */
        ASSIGNMENT_ALREADY_CLOSED,
        /**
         * Date d'affectation invalide : {@code validUntil} &lt;
         * {@code validFrom} à la création, ou {@code effectiveDate} &lt;
         * {@code validFrom} à la clôture ({@code ACAD_ASSIGNMENT_DATE_INVALID}).
         */
        ASSIGNMENT_DATE_INVALID
    }

    private final Kind kind;

    AcademicException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
