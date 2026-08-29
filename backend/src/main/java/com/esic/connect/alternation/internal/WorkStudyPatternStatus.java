package com.esic.connect.alternation.internal;

/**
 * Statut d'un modèle de rythme. Aucune suppression physique : un modèle
 * retiré de l'usage courant passe en {@link #ARCHIVED} et son historique
 * est conservé (docs/04 §14.1).
 */
enum WorkStudyPatternStatus {
    ACTIVE,
    ARCHIVED
}
