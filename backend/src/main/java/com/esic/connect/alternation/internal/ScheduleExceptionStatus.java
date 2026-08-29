package com.esic.connect.alternation.internal;

/**
 * Statut d'une exception individuelle. Aucune suppression physique :
 * l'annulation fait passer l'exception en {@link #CANCELLED} et conserve
 * l'historique (docs/04 §14.3).
 */
enum ScheduleExceptionStatus {
    ACTIVE,
    CANCELLED
}
