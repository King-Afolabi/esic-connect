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

    /**
     * Option de séance pour la recherche assistée du dépôt (Lot 18) — le
     * client ne saisit jamais un identifiant de séance à la main.
     */
    record SessionOption(UUID publicId, String label) {
    }

    /**
     * Option de formateur pour le ciblage facultatif du guichet TEACHER
     * (Lot 19) — jamais la liste complète : uniquement des résultats de
     * recherche, bornés.
     */
    record TeacherOption(UUID publicId, String firstName, String lastName) {
    }
}
