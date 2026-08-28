package com.esic.connect.organization.internal;

/**
 * Statut d'une entité organisationnelle archivable (docs/04-modele-donnees.md
 * §4.2). Aucune suppression physique : une entité retirée de l'usage
 * courant passe en {@link #ARCHIVED} et son historique est conservé.
 */
public enum OrganizationStatus {
    ACTIVE,
    ARCHIVED
}
