package com.esic.connect.studentimport.internal;

/**
 * Erreur métier du module {@code studentimport}. Le {@link Kind} détermine
 * le code HTTP et le code d'erreur {@code IMP_*} exposés
 * ({@link StudentImportExceptionHandler}). Aucun message ne contient de
 * donnée personnelle ni le contenu d'une cellule CSV (cahier §49, rapport
 * §10). Aligné sur {@code enrollment.internal.EnrollmentException}.
 */
class StudentImportException extends RuntimeException {

    enum Kind {
        // --- Téléversement / lecture du fichier (rapport §5, §8, §10) ---
        /** Extension, type ou contenu non {@code .csv} (ZIP/XLSX, octet nul, chiffrement). */
        UNSUPPORTED_MEDIA_TYPE,
        /** Fichier au-delà de la taille maximale autorisée. */
        FILE_TOO_LARGE,
        /** Séquence d'octets non UTF-8 dans le fichier. */
        ENCODING_INVALID,
        /** Une colonne obligatoire est absente de l'en-tête. */
        MISSING_COLUMN,
        /** Plus de lignes de données que la limite configurée. */
        TOO_MANY_ROWS,
        /** Aucune ligne de données exploitable. */
        NO_DATA_ROWS,
        /** Aucun en-tête reconnaissable (fichier vide ou illisible). */
        HEADER_UNREADABLE,

        // --- Consultation ---
        /** Aucun import pour ce {@code public_id}. */
        JOB_NOT_FOUND,
        /** Import existant mais hors du périmètre de l'appelant. */
        JOB_FORBIDDEN,
        /** Champ ou direction de tri hors liste blanche. */
        INVALID_SORT,
        /** Valeur de filtre invalide (statut, gravité, action...). */
        INVALID_FILTER,
        /** Filtre de périmètre ({@code programCode} / {@code classCode}) hors du périmètre de l'appelant. */
        SCOPE_FORBIDDEN,

        // --- Confirmation / cycle de vie (rapport §4.4, §11) ---
        /** Job {@code SIMULATED} mais non {@code confirmable} (anomalie bloquante ou erreur de ligne). */
        NOT_CONFIRMABLE,
        /** Des lignes sont devenues invalides entre la simulation et la confirmation. */
        STALE_SIMULATION,
        /** La simulation a expiré ({@code expires_at} dépassé). */
        SIMULATION_EXPIRED,
        /** Le job a été annulé : il ne peut plus être confirmé. */
        JOB_CANCELLED,
        /** L'appelant n'est pas autorisé à confirmer ce job (périmètre). */
        CONFIRM_FORBIDDEN,
        /** Le job n'est pas dans un état annulable. */
        JOB_NOT_CANCELLABLE,

        // --- Génération de numéro étudiant (rapport §3.2) ---
        /** Échec d'allocation d'un numéro étudiant après le nombre maximal de tentatives. */
        STUDENT_NUMBER_ALLOC_FAILED,
        /** La séquence de numéros a dépassé la borne configurée pour l'année. */
        STUDENT_NUMBER_EXHAUSTED
    }

    private final Kind kind;
    private final transient Object detail;

    StudentImportException(Kind kind) {
        this(kind, null);
    }

    StudentImportException(Kind kind, Object detail) {
        super(kind.name());
        this.kind = kind;
        this.detail = detail;
    }

    Kind kind() {
        return kind;
    }

    /**
     * Complément non sensible transmis dans {@code ApiError.details}
     * (par exemple la liste des colonnes obligatoires manquantes) ;
     * {@code null} si sans objet. Ne contient jamais de valeur de cellule.
     */
    Object detail() {
        return detail;
    }
}
