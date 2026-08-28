package com.esic.connect.academic.internal;

/**
 * Statut d'une entité académique archivable (docs/04-modele-donnees.md
 * §5.1 : formation et classe = archivage). Aucune suppression physique :
 * une entité retirée de l'usage courant passe en {@link #ARCHIVED} et son
 * historique est conservé.
 */
public enum AcademicStatus {
    ACTIVE,
    ARCHIVED
}
