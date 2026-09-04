package com.esic.connect.enrollment.internal;

/**
 * Statut d'une autorisation de suivi à distance (EF-ENR-004).
 *
 * <p>{@link #EXPIRED} n'est pas calculé à la volée : une autorisation
 * dont la borne est passée reste {@code ACTIVE} en base et ne couvre
 * simplement plus la date. Le statut existe pour une éventuelle tâche de
 * purge, jamais pour décider d'un droit — la couverture se lit sur les
 * dates, seule source qui ne peut pas se désynchroniser.
 */
enum RemoteAuthorizationStatus {
    ACTIVE,
    REVOKED,
    EXPIRED
}
