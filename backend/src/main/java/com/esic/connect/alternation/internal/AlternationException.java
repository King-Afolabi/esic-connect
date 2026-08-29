package com.esic.connect.alternation.internal;

/**
 * Erreur métier du module {@code alternation}. Le {@link Kind} détermine
 * le code HTTP et le code d'erreur exposés
 * ({@link AlternationExceptionHandler}). Un {@code detail} facultatif,
 * non sensible, précise la cause exacte d'une configuration de rythme
 * invalide (aucune donnée personnelle — cahier §49).
 */
class AlternationException extends RuntimeException {

    enum Kind {
        /** Aucun modèle de rythme pour ce {@code public_id}. */
        PATTERN_NOT_FOUND,
        /** Aucune affectation de rythme à une classe pour ce {@code public_id}. */
        CLASS_ASSIGNMENT_NOT_FOUND,
        /** Aucune exception individuelle pour ce {@code public_id}. */
        EXCEPTION_NOT_FOUND,
        /** Aucune classe pour ce {@code public_id}. */
        CLASS_GROUP_NOT_FOUND,
        /** Aucune inscription pour ce {@code public_id}. */
        ENROLLMENT_NOT_FOUND,
        /** Code de modèle déjà utilisé ({@code ALT_DUPLICATE_CODE}). */
        DUPLICATE_CODE,
        /** Type de rythme hors {@link WorkStudyPatternType}. */
        INVALID_PATTERN_TYPE,
        /** Type d'exception hors {@link ScheduleExceptionType}. */
        INVALID_EXCEPTION_TYPE,
        /**
         * {@code configuration_json} inconnu, incohérent ou incompatible
         * avec le {@code pattern_type} ({@code ALT_INVALID_CONFIGURATION}).
         */
        INVALID_CONFIGURATION,
        /** Fuseau horaire absent de la base IANA ({@code ALT_INVALID_TIME_ZONE}). */
        INVALID_TIME_ZONE,
        /** Période incohérente : fin antérieure ou égale au début. */
        INVALID_PERIOD,
        /** Champ ou direction de tri hors liste blanche. */
        INVALID_SORT,
        /** Valeur de filtre invalide (statut, type...). */
        INVALID_FILTER,
        /** Le modèle de rythme visé est archivé : il ne peut pas être affecté. */
        PATTERN_ARCHIVED,
        /**
         * La classe visée n'est pas affectable : elle-même ou un maillon
         * de sa chaîne (promotion, formation, année) est archivé
         * ({@code ALT_CLASS_NOT_ASSIGNABLE}).
         */
        CLASS_NOT_ASSIGNABLE,
        /** Opération refusée : le modèle de rythme est déjà dans cet état. */
        INVALID_STATE,
        /** L'inscription visée n'est pas exploitable (statut non {@code ACTIVE}). */
        ENROLLMENT_NOT_USABLE,
        /**
         * Chevauchement de périodes : une affectation de rythme ACTIVE de
         * la classe partage au moins un jour avec la période demandée
         * ({@code ALT_ASSIGNMENT_OVERLAP}). Deux périodes strictement
         * adjacentes sont autorisées.
         */
        ASSIGNMENT_OVERLAP,
        /**
         * Une affectation de rythme ACTIVE « ouverte » (sans date de fin)
         * existe déjà pour cette classe ({@code ALT_OPEN_ASSIGNMENT_EXISTS}) —
         * y compris détecté par course concurrente sur la contrainte SQL.
         */
        OPEN_ASSIGNMENT_EXISTS,
        /** Clôture demandée sur une affectation déjà clôturée. */
        ASSIGNMENT_ALREADY_CLOSED,
        /**
         * Clôture refusée : la {@code effectiveDate} atteint ou dépasse le
         * {@code valid_from} de l'affectation historisée suivante de la
         * même classe — elle produirait un historique qui se chevauche
         * ({@code ALT_ASSIGNMENT_CLOSE_CONFLICT}). Avec bornes inclusives,
         * la date maximale autorisée est {@code next.validFrom - 1 jour}.
         */
        ASSIGNMENT_CLOSE_CONFLICT,
        /** Annulation demandée sur une exception déjà annulée. */
        EXCEPTION_ALREADY_CANCELLED,
        /**
         * Chevauchement d'exceptions : une exception ACTIVE de <em>même
         * type</em> recouvre déjà tout ou partie de la période demandée
         * pour cette inscription ({@code ALT_EXCEPTION_OVERLAP}).
         */
        EXCEPTION_OVERLAP,
        /**
         * L'appelant ({@code PEDAGOGICAL_MANAGER}) n'a pas de périmètre
         * effectif sur la classe visée ({@code ALT_FORBIDDEN}).
         */
        OUT_OF_SCOPE
    }

    private final Kind kind;
    private final String detail;

    AlternationException(Kind kind) {
        this(kind, null);
    }

    AlternationException(Kind kind, String detail) {
        super(detail == null ? kind.name() : kind.name() + ": " + detail);
        this.kind = kind;
        this.detail = detail;
    }

    Kind kind() {
        return kind;
    }

    String detail() {
        return detail;
    }
}
