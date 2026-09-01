package com.esic.connect.planning.internal;

/**
 * Codes d'anomalie stables des lignes / en-têtes d'un import de planning
 * (DEC-G1-002, DEC-G1-005, DEC-G1-006). Aucun message ne contient de
 * donnée personnelle ; {@code received_value} est tronquée et n'entre
 * jamais dans l'audit.
 */
final class PlanningIssueCodes {

    // En-tête / fichier
    static final String MISSING_COLUMN = "PLAN_MISSING_COLUMN";
    static final String EMPTY_FILE = "PLAN_EMPTY_FILE";
    static final String TOO_MANY_ROWS = "PLAN_TOO_MANY_ROWS";
    static final String ENCODING_INVALID = "PLAN_ENCODING_INVALID";
    static final String DUPLICATE_HEADER = "PLAN_DUPLICATE_HEADER";

    // Ligne — valeurs
    static final String SLOT_KEY_REQUIRED = "PLAN_SLOT_KEY_REQUIRED";
    static final String SLOT_KEY_DUPLICATED = "PLAN_SLOT_KEY_DUPLICATED";
    static final String TITLE_REQUIRED = "PLAN_TITLE_REQUIRED";
    static final String DATE_INVALID = "PLAN_DATE_INVALID";
    static final String TIME_INVALID = "PLAN_TIME_INVALID";
    static final String PERIOD_INVALID = "PLAN_PERIOD_INVALID";
    static final String TIME_ZONE_INVALID = "PLAN_TIME_ZONE_INVALID";
    static final String DURATION_ABNORMAL = "PLAN_DURATION_ABNORMAL";

    // Ligne — résolution de références
    static final String TEACHER_UNKNOWN = "PLAN_TEACHER_UNKNOWN";
    static final String TEACHER_NOT_ELIGIBLE = "PLAN_TEACHER_NOT_ELIGIBLE";

    // Ligne — conflits (DEC-G1-005)
    static final String CONFLICT_TEACHER = "PLAN_CONFLICT_TEACHER";
    static final String CONFLICT_CLASS = "PLAN_CONFLICT_CLASS";
    static final String CONFLICT_ROOM = "PLAN_CONFLICT_ROOM";
    static final String OUTSIDE_WORKING_HOURS = "PLAN_OUTSIDE_WORKING_HOURS";

    // Ligne — alternance (DEC-G1-006)
    static final String ALTERNATION_COMPANY_DAY = "PLAN_ALTERNATION_COMPANY_DAY";
    static final String ALTERNATION_UNKNOWN = "PLAN_ALTERNATION_UNKNOWN";

    private PlanningIssueCodes() {
    }
}
