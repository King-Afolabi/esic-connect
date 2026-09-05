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
     * Comptes {@code PENDING_ACTIVATION} parmi les identifiants publics
     * fournis (EF-REP-007 — « comptes non activés » de la carte du
     * responsable pédagogique).
     *
     * <p>Prend une liste plutôt qu'un périmètre : {@code identity} ne
     * connaît pas les classes, et ce n'est pas à lui de les résoudre.
     * L'appelant fournit les comptes de son périmètre, déjà résolus par
     * {@code enrollment}.
     */
    long countPendingActivationAmong(java.util.Collection<java.util.UUID> userPublicIds);

    /**
     * Invitations en attente dont la date d'expiration est passée
     * (§22.6 — « invitations échouées »). L'expiration n'est pas un
     * statut stocké : elle se déduit de {@code expires_at}.
     */
    long countExpiredPendingInvitations();

    /**
     * @param active            comptes {@code ACTIVE}
     * @param suspended         comptes {@code SUSPENDED} ou {@code LOCKED}
     * @param pendingActivation comptes {@code PENDING_ACTIVATION}
     * @param archived          comptes {@code ARCHIVED}
     */
    record AccountStats(long active, long suspended, long pendingActivation, long archived) {
    }
}
