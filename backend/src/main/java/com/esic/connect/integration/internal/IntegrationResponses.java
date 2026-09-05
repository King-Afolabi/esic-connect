package com.esic.connect.integration.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO du module {@code integration}. Le jeton d'un abonnement n'apparaît
 * que dans {@link CreatedSubscription}, une seule fois, à la création :
 * il n'est pas conservé en clair et ne peut donc pas être réaffiché.
 */
final class IntegrationResponses {

    private IntegrationResponses() {
    }

    /**
     * @param feedPath chemin d'abonnement <strong>relatif</strong>,
     *                 jeton compris. Le client le préfixe de son origine :
     *                 le serveur ne connaît pas l'URL publique par laquelle
     *                 on l'atteint et n'a pas à la deviner.
     */
    record CreatedSubscription(UUID publicId, String label, Instant createdAt, String feedPath) {
    }

    record SubscriptionSummary(
            UUID publicId,
            String label,
            Instant createdAt,
            Instant lastUsedAt,
            Instant revokedAt) {

        boolean revoked() {
            return revokedAt != null;
        }
    }

    /**
     * État déclaré des intégrations Microsoft (EF-INT-002, EF-INT-003).
     *
     * @param meetingProvider   nom du fournisseur de réunion, {@code "none"} si aucun
     * @param meetingActive     {@code true} seulement si un fournisseur réel est configuré
     * @param calendarProvider  nom du fournisseur de calendrier
     * @param calendarActive    idem pour l'écriture de calendrier
     * @param notice            mention honnête affichée par l'interface
     */
    record MicrosoftStatus(
            String meetingProvider,
            boolean meetingActive,
            String calendarProvider,
            boolean calendarActive,
            String notice) {
    }
}
