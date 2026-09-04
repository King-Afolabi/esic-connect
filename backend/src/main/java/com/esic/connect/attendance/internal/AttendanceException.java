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
        /**
         * Canal distant demandé sur une séance présentielle, sans
         * autorisation individuelle active (EF-ENR-004 ; docs/02 §15.3).
         */
        REMOTE_NOT_AUTHORIZED,
        /**
         * QR fixe présenté hors d'une plage réseau autorisée du site
         * (EF-ATT-008 ; docs/02 §16.7). L'adresse n'est ni conservée ni
         * renvoyée.
         */
        /**
         * Jeton d'affiche inconnu, ou salle archivée (EF-ATT-010).
         * {@code 404} : l'existence d'une salle est une information à
         * protéger (docs/02 §18.2), et un jeton renouvelé doit rendre
         * l'ancienne affiche muette, pas bavarde.
         */
        ROOM_QR_UNKNOWN,
        ROOM_QR_OUT_OF_NETWORK,
        /**
         * QR fixe présenté après le début de la séance (RG-051) : au-delà,
         * c'est le QR dynamique du formateur, sous son contrôle.
         */
        ROOM_QR_SESSION_STARTED,
        /** Aucune séance de cet apprenant dans cette salle à cette heure. */
        ROOM_QR_NO_SESSION,
        /** Plusieurs inscriptions actives correspondent : impossible de trancher sans risque. */
        ENROLLMENT_AMBIGUOUS,
        /** Inscription visée introuvable (régularisation d'un provisoire). */
        ENROLLMENT_NOT_FOUND,
        /** Une présence existe déjà pour cet apprenant et ce point de contrôle. */
        ALREADY_RECORDED,
        /** Backend de jetons (Redis) indisponible — jamais de validation dégradée. */
        TOKEN_BACKEND_UNAVAILABLE,
        /** Aucune séance pour cet identifiant. */
        SESSION_NOT_FOUND,
        /** Aucun point de contrôle pour cet identifiant dans la séance résolue. */
        CHECKPOINT_NOT_FOUND,
        /** L'appelant n'a pas le droit d'effectuer cette opération sur cette séance. */
        OPERATION_FORBIDDEN,
        // --- Présence manuelle / correction / annulation (V10) ---
        /** Aucune présence pour cet identifiant (ou pas dans cette séance). */
        RECORD_NOT_FOUND,
        /** L'état de la présence ne permet pas cette opération (déjà annulée, absence à ne pas justifier...). */
        RECORD_INVALID_STATE,
        /** Motif obligatoire d'une saisie manuelle. */
        MANUAL_REASON_REQUIRED,
        /** Motif obligatoire d'une correction. */
        CORRECTION_REASON_REQUIRED,
        /** Statut de saisie manuelle invalide (EXCUSED_ABSENCE non saisissable directement...). */
        MANUAL_STATUS_INVALID,
        // --- Justificatifs (V10) ---
        /** Aucun justificatif pour cet identifiant (ou hors périmètre). */
        JUSTIFICATION_NOT_FOUND,
        /** Cycle de vie du justificatif incompatible avec l'opération (déjà examiné, doublon actif...). */
        JUSTIFICATION_INVALID_STATE,
        /** Motif de décision obligatoire pour un refus. */
        JUSTIFICATION_DECISION_REASON_REQUIRED,
        // --- Pièces jointes de justificatif (V16 ; bloc G1-E) ---
        /** Aucune pièce jointe disponible (aucune, ou pas encore {@code STORED}). */
        ATTACHMENT_NOT_FOUND,
        /** Une pièce active existe déjà pour ce justificatif. */
        ATTACHMENT_ALREADY_EXISTS,
        /** Le stockage de la pièce a échoué (le justificatif est intact). */
        ATTACHMENT_STORAGE_FAILED,
        /**
         * Signature de logiciel malveillant détectée à l'analyse : le
         * contenu n'est ni écrit, ni référencé, ni servi (EF-JUS-002).
         */
        ATTACHMENT_INFECTED,
        /**
         * Pièce en quarantaine : l'établissement exige une analyse
         * antivirus et celle-ci n'a pas rendu de verdict exploitable
         * (docs/02 §19.2).
         */
        ATTACHMENT_QUARANTINED,
        // --- Départ anticipé (V29 ; EF-ATT-013) ---
        /** Aucun dossier de départ anticipé pour cet identifiant. */
        EARLY_DEPARTURE_NOT_FOUND,
        /** Dossier déjà tranché, ou transmission d'un dossier déjà transmis. */
        EARLY_DEPARTURE_INVALID_STATE,
        /** Un dossier est déjà ouvert pour cet apprenant sur cette séance. */
        EARLY_DEPARTURE_ALREADY_OPEN,
        /**
         * Dossier transmis au responsable : le formateur qui a transmis ne
         * peut plus trancher lui-même (docs/02 §16.13).
         */
        EARLY_DEPARTURE_DECISION_RESERVED,
        /** Heure de départ hors des bornes de la séance. */
        EARLY_DEPARTURE_TIME_OUTSIDE_SESSION,
        // --- Rapports (V10) ---
        /** Filtre de rapport invalide. */
        REPORT_INVALID_FILTER,
        /** Champ ou direction de tri de rapport hors liste blanche. */
        REPORT_INVALID_SORT
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
