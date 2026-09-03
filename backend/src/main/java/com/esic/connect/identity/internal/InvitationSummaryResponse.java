package com.esic.connect.identity.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Invitation telle qu'affichée dans le suivi (EF-USER-007).
 *
 * @param status  statut stocké — {@code PENDING}, {@code ACCEPTED} ou
 *                {@code REVOKED}
 * @param expired vrai si l'invitation est encore {@code PENDING} mais que
 *                sa date d'expiration est passée. L'expiration n'est pas
 *                un statut stocké : elle se déduit de {@code expires_at}
 *                (docs/04 §10.4), et une tâche de balayage n'apporterait
 *                rien qu'un calcul à la lecture ne donne déjà.
 */
public record InvitationSummaryResponse(
        UUID id,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String status,
        boolean expired,
        Instant expiresAt,
        Instant usedAt,
        Instant createdAt) {

    static InvitationSummaryResponse from(AccountInvitation invitation, Instant now) {
        UserAccount account = invitation.getUser();
        boolean expired = invitation.getStatus() == AccountInvitationStatus.PENDING
                && invitation.getExpiresAt().isBefore(now);
        return new InvitationSummaryResponse(
                invitation.getPublicId(),
                account.getPublicId(),
                account.getEmail(),
                account.getFirstName(),
                account.getLastName(),
                invitation.getStatus().name(),
                expired,
                invitation.getExpiresAt(),
                invitation.getUsedAt(),
                invitation.getCreatedAt());
    }
}
