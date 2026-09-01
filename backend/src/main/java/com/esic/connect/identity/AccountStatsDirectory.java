package com.esic.connect.identity;

/**
 * Port public de lecture d'agrégats de comptes (bloc G1-F ; DEC-G1-010).
 * Le module {@code dashboard} l'utilise pour la carte
 * « comptes actifs / suspendus / en attente » des tableaux de bord
 * d'administration — <strong>un seul agrégat borné</strong>
 * ({@code GROUP BY status}), aucune entité ni repository exposé.
 */
public interface AccountStatsDirectory {

    /** Décompte des comptes par grande catégorie de statut. */
    AccountStats counts();

    /**
     * @param active            comptes {@code ACTIVE}
     * @param suspended         comptes {@code SUSPENDED} ou {@code LOCKED}
     * @param pendingActivation comptes {@code PENDING_ACTIVATION}
     * @param archived          comptes {@code ARCHIVED}
     */
    record AccountStats(long active, long suspended, long pendingActivation, long archived) {
    }
}
