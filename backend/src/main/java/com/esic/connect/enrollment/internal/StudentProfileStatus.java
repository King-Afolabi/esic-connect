package com.esic.connect.enrollment.internal;

/**
 * Statut d'un profil apprenant (docs/04-modele-donnees.md §11.1). Aucune
 * suppression physique : un profil retiré de l'usage courant passe en
 * {@link #ARCHIVED} et son historique d'inscriptions est conservé.
 *
 * <p>Dans ce lot, seul {@link #ACTIVE} est produit (création) ; la
 * transition vers {@link #ARCHIVED} — pilotée avec l'archivage du compte —
 * relève d'un lot ultérieur.
 */
public enum StudentProfileStatus {
    ACTIVE,
    ARCHIVED
}
