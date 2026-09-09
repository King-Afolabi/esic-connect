package com.esic.connect.claim.internal;

import java.time.Instant;
import java.util.UUID;

/** Vues du fil d'une réclamation. */
final class ClaimResponses {

    private ClaimResponses() {
    }

    /**
     * Message du fil. {@code authorRole} est le rôle employé <em>au moment
     * du message</em> : relire un fil deux ans plus tard doit montrer qui
     * parlait en quelle qualité.
     */
    record MessageView(
            UUID publicId,
            UUID authorPublicId,
            String authorRole,
            String body,
            Instant createdAt) {
    }

    /** Décision : transfert, changement de statut, réouverture. */
    record EventView(
            UUID publicId,
            String eventType,
            String fromStatus,
            String toStatus,
            String fromAudience,
            String toAudience,
            String motive,
            Instant createdAt) {
    }
}
