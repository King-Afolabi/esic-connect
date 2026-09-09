package com.esic.connect.claim;

/**
 * Catégorie d'une réclamation (docs/02 §20.2).
 *
 * <p>Liste fermée : elle détermine le destinataire fonctionnel par
 * défaut et permet le rapport « volumes et délais de traitement »
 * (§22.3). Un champ libre ne se compterait pas.
 */
public enum ClaimCategory {
    /** Contestation d'une présence ou d'une absence. */
    ATTENDANCE,
    /** Justificatif : dépôt, décision, délai. */
    JUSTIFICATION,
    /** Planning, séance, salle, formateur. */
    SCHEDULE,
    /** Compte, accès, données personnelles. */
    ACCOUNT,
    /** Autre — le sujet et la description font foi. */
    OTHER
}
