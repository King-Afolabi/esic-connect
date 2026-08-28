package com.esic.connect.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Publié après l'émission d'une invitation d'activation (cahier §11).
 * Consommé uniquement par le module {@code notification} pour l'envoi de
 * l'email : il transporte donc le jeton <strong>brut</strong> (jamais
 * persisté, jamais journalisé) et les données strictement nécessaires au
 * message. La trace d'audit passe par un événement distinct
 * ({@link AccountLifecycleEvent}) dépourvu de donnée sensible.
 */
public record AccountInvitationIssuedEvent(
        Long userId,
        UUID userPublicId,
        String email,
        String firstName,
        String rawToken,
        Instant expiresAt) {
}
