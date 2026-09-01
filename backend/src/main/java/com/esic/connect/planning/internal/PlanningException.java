package com.esic.connect.planning.internal;

/**
 * Erreur métier du module {@code planning}. Le {@link Kind} détermine le
 * code HTTP et le code d'erreur exposés ({@code PlanningExceptionHandler}).
 * Aucun message ne contient de donnée personnelle, de jeton, de chemin
 * physique ni de valeur de cellule.
 */
class PlanningException extends RuntimeException {

    enum Kind {
        /** Job d'import introuvable pour ce {@code public_id}. */
        JOB_NOT_FOUND,
        /** Planning introuvable pour ce {@code public_id}. */
        SCHEDULE_NOT_FOUND,
        /** Version de planning introuvable. */
        VERSION_NOT_FOUND,
        /** Classe / année inconnue ou incohérente (résolution de référence). */
        TARGET_UNRESOLVED,
        /** Fichier non-CSV, binaire, trop volumineux ou encodage invalide. */
        UNSUPPORTED_FILE,
        /** Fichier vide / illisible. */
        FILE_UNREADABLE,
        /** Colonne(s) obligatoire(s) manquante(s) dans l'en-tête. */
        MISSING_COLUMNS,
        /** Trop de lignes de données. */
        TOO_MANY_ROWS,
        /** Le périmètre pédagogique de l'appelant n'inclut pas la classe visée. */
        SCOPE_FORBIDDEN,
        /** Publication refusée : au moins une ligne {@code ERROR} (RG-034). */
        BLOCKING_ISSUES,
        /** État du job incompatible avec l'opération demandée. */
        INVALID_JOB_STATE,
        /** Job expiré : nouvel import requis. */
        JOB_EXPIRED,
        /** Champ ou direction de tri hors liste blanche. */
        INVALID_SORT,
        /** Valeur de filtre invalide. */
        INVALID_FILTER
    }

    private final transient Kind kind;

    PlanningException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
