package com.esic.connect.identity.internal;

/**
 * Statut d'une invitation d'activation (docs/04-modele-donnees.md §10.4).
 *
 * L'expiration n'est pas un statut stocké : elle se déduit de
 * {@code expires_at}. Une invitation reste {@code PENDING} jusqu'à son
 * acceptation ({@code ACCEPTED}) ou sa révocation ({@code REVOKED}, lors
 * de la réémission d'une nouvelle invitation).
 */
public enum AccountInvitationStatus {
    PENDING,
    ACCEPTED,
    REVOKED
}
