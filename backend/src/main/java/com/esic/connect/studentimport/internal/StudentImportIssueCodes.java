package com.esic.connect.studentimport.internal;

/**
 * Codes d'anomalie {@code IMP_*} portés par
 * {@code student_import_job_issue.error_code} (anomalie globale) et
 * {@code student_import_row_issue.error_code} (anomalie de ligne) —
 * rapport §5, §14. Contrairement à {@link StudentImportException.Kind}
 * (qui déclenche un code HTTP), ces codes ne sont que des étiquettes
 * stables persistées et rendues à la revue humaine. Ils ne sont jamais
 * repris dans l'audit métier avec la valeur de cellule associée.
 */
final class StudentImportIssueCodes {

    private StudentImportIssueCodes() {
    }

    // --- Anomalies globales (en-tête / fichier) ---
    static final String MISSING_COLUMN = "IMP_MISSING_COLUMN";
    static final String UNKNOWN_COLUMN = "IMP_UNKNOWN_COLUMN";
    static final String COLUMN_IGNORED = "IMP_COLUMN_IGNORED";
    static final String ENCODING_INVALID = "IMP_ENCODING_INVALID";
    static final String TOO_MANY_ROWS = "IMP_TOO_MANY_ROWS";
    static final String NO_DATA_ROWS = "IMP_NO_DATA_ROWS";
    static final String SCOPE_FILTER_FORBIDDEN = "IMP_SCOPE_FILTER_FORBIDDEN";

    // --- Anomalies de ligne : structure ---
    static final String COLUMN_COUNT = "IMP_COLUMN_COUNT";
    static final String REQUIRED_VALUE_MISSING = "IMP_REQUIRED_VALUE_MISSING";

    // --- Anomalies de ligne : champs ---
    static final String EMAIL_INVALID = "IMP_EMAIL_INVALID";
    static final String EMAIL_DUPLICATE_IN_FILE = "IMP_EMAIL_DUPLICATE_IN_FILE";
    static final String PHONE_FORMAT = "IMP_PHONE_FORMAT";
    static final String BIRTH_DATE_FORMAT = "IMP_BIRTH_DATE_FORMAT";
    static final String WORK_STUDY_INVALID = "IMP_WORK_STUDY_INVALID";
    static final String COMPANY_NAME_MISSING = "IMP_COMPANY_NAME_MISSING";
    static final String STUDENT_NUMBER_TAKEN = "IMP_STUDENT_NUMBER_TAKEN";
    static final String STUDENT_NUMBER_DUPLICATE_IN_FILE = "IMP_STUDENT_NUMBER_DUPLICATE_IN_FILE";
    static final String STUDENT_NUMBER_WILL_BE_GENERATED = "IMP_STUDENT_NUMBER_WILL_BE_GENERATED";

    // --- Anomalies de ligne : résolution de la classe / année ---
    static final String PROGRAM_UNKNOWN = "IMP_PROGRAM_UNKNOWN";
    static final String CLASS_UNKNOWN = "IMP_CLASS_UNKNOWN";
    static final String CLASS_NOT_IN_PROGRAM = "IMP_CLASS_NOT_IN_PROGRAM";
    static final String ACADEMIC_YEAR_UNKNOWN = "IMP_ACADEMIC_YEAR_UNKNOWN";
    static final String CLASS_NOT_IN_YEAR = "IMP_CLASS_NOT_IN_YEAR";
    static final String CHAIN_ARCHIVED = "IMP_CHAIN_ARCHIVED";
    static final String CLASS_OUT_OF_SCOPE = "IMP_CLASS_OUT_OF_SCOPE";

    // --- Anomalies de ligne : compte existant ---
    static final String ACCOUNT_NOT_USABLE = "IMP_ACCOUNT_NOT_USABLE";
}
