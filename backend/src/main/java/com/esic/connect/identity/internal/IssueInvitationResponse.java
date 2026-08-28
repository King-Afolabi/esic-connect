package com.esic.connect.identity.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Réponse à une émission d'invitation. Ne contient jamais le jeton : il
 * n'est transmis qu'au destinataire, par email.
 */
record IssueInvitationResponse(
        UUID invitationId,
        Instant expiresAt) {
}
