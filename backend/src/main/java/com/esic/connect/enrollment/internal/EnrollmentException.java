package com.esic.connect.enrollment.internal;

/**
 * Erreur métier du module {@code enrollment}. Le {@link Kind} détermine le
 * code HTTP et le code d'erreur exposés ({@link EnrollmentExceptionHandler}).
 * Aucun message ne contient de donnée personnelle (cahier §49).
 */
class EnrollmentException extends RuntimeException {

    enum Kind {
        /** Aucun profil apprenant pour ce {@code public_id}. */
        STUDENT_PROFILE_NOT_FOUND,
        /** Aucune inscription pour ce {@code public_id}. */
        ENROLLMENT_NOT_FOUND,
        /** Aucune classe/groupe pour ce {@code public_id}. */
        CLASS_GROUP_NOT_FOUND,
        /** Aucun groupe temporaire pour ce {@code public_id} (EF-ACA-007). */
        STUDENT_GROUP_NOT_FOUND,
        /** Aucune formation pour ce {@code public_id}. */
        PROGRAM_NOT_FOUND,
        /** Aucune année scolaire pour ce {@code public_id}. */
        ACADEMIC_YEAR_NOT_FOUND,
        /** Aucune matière pour ce {@code public_id} (EF-ACA-006). */
        SUBJECT_NOT_FOUND,
        /** Code de groupe déjà utilisé pour cette année scolaire. */
        DUPLICATE_GROUP_CODE,
        /** Groupe archivé : il n'accepte plus de modification ni de membre. */
        GROUP_ARCHIVED,
        /** L'inscription visée est déjà membre actif de ce groupe. */
        ALREADY_MEMBER,
        /** Période incohérente : la fin précède le début. */
        INVALID_GROUP_PERIOD,
        /**
         * Compte cible inéligible : inexistant, archivé ou sans rôle actif
         * {@code STUDENT} ({@code ENR_USER_NOT_ELIGIBLE}).
         */
        USER_NOT_ELIGIBLE,
        /** Un profil apprenant existe déjà pour ce compte ({@code ENR_PROFILE_EXISTS}). */
        PROFILE_ALREADY_EXISTS,
        /** Numéro étudiant déjà attribué ({@code ENR_DUPLICATE_STUDENT_NUMBER}). */
        DUPLICATE_STUDENT_NUMBER,
        /**
         * La séquence de numéros étudiants de l'année est épuisée (borne de
         * largeur atteinte) — {@code ENR_STUDENT_NUMBER_EXHAUSTED}.
         */
        STUDENT_NUMBER_EXHAUSTED,
        /** Le profil apprenant visé est archivé. */
        STUDENT_PROFILE_ARCHIVED,
        /**
         * Inscription refusée : la classe ou un maillon de sa chaîne de
         * rattachement (promotion, formation, année scolaire) est archivé
         * ({@code ENR_ARCHIVED_PARENT}).
         */
        ARCHIVED_PARENT,
        /**
         * Une inscription {@code ACTIVE} existe déjà pour cet apprenant et
         * cette année scolaire ({@code ENR_ACTIVE_ENROLLMENT_EXISTS},
         * docs/04 §13.3, RG-012).
         */
        ACTIVE_ENROLLMENT_EXISTS,
        /** L'inscription visée n'est pas {@code ACTIVE} : opération impossible. */
        ENROLLMENT_NOT_ACTIVE,
        /** Changement de classe demandé vers la classe déjà occupée. */
        SAME_CLASS,
        /**
         * Date invalide : {@code end_date} / {@code effectiveDate} &lt;
         * {@code start_date} de l'inscription ({@code ENR_DATE_INVALID}).
         */
        DATE_INVALID,
        /** Statut de clôture hors {@code COMPLETED} / {@code WITHDRAWN}. */
        INVALID_CLOSE_STATUS,
        /** Champ ou direction de tri hors liste blanche. */
        INVALID_SORT,
        /** Valeur de filtre invalide (statut...). */
        INVALID_FILTER,
        /**
         * Ressource hors du périmètre pédagogique de l'appelant
         * ({@code ENR_FORBIDDEN}, 403). Distinct d'un « introuvable » :
         * la ressource existe, mais ne relève pas de l'appelant.
         */
        OUT_OF_SCOPE,
        /**
         * Autorisation de suivi à distance déjà révoquée (EF-ENR-004).
         * Conflit d'état, pas requête malformée : la seconde révocation
         * écraserait le motif de la première sans rien changer.
         */
        AUTHORIZATION_NOT_ACTIVE,
        /** Période d'autorisation incohérente : la fin précède le début. */
        INVALID_REMOTE_PERIOD
    }

    private final Kind kind;

    EnrollmentException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
