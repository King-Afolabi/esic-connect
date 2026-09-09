package com.esic.connect.notification.internal;

import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Vues API du centre de notifications (G1-D) — jamais d'identifiant SQL. */
final class NotificationResponses {

    private NotificationResponses() {
    }

    /** Une notification pour l'appelant. */
    record NotificationView(
            UUID publicId,
            String type,
            String title,
            String body,
            String resourceType,
            UUID resourcePublicId,
            String status,
            Instant createdAt,
            Instant readAt) {

        static NotificationView from(Notification n) {
            return new NotificationView(n.getPublicId(), n.getType().name(), n.getTitle(), n.getBody(),
                    n.getResourceType(), n.getResourcePublicId(), n.getStatus().name(),
                    n.getCreatedAt(), n.getReadAt());
        }
    }

    /** Enveloppe de pagination stable (miroir de {@code PageResponse<T>}). */
    record NotificationPage(
            List<NotificationView> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {

        static NotificationPage of(Page<Notification> page, Function<Notification, NotificationView> mapper) {
            return new NotificationPage(page.getContent().stream().map(mapper).toList(),
                    page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }

    /** Compteur de non-lus (cloche + badge). */
    record UnreadCount(long unread) {
    }

    /**
     * Réglage d'un canal pour une catégorie (EF-NOTIF-006).
     *
     * @param locked {@code true} si le réglage ne peut pas être changé —
     *               canal {@code IN_APP} ou catégorie {@code SECURITY}.
     *               L'écran affiche alors la case cochée et désactivée,
     *               plutôt que de laisser croire à un choix qui serait
     *               refusé au clic.
     */
    record PreferenceView(String category, String channel, boolean enabled, boolean locked) {
    }

    record PreferenceList(java.util.List<PreferenceView> preferences) {
    }

    /**
     * Abonnement d'un appareil à la poussée. <strong>Ni la terminaison, ni
     * les clés</strong> : la terminaison est un secret d'appareil, et les
     * clés n'ont aucune utilité côté client.
     */
    record PushSubscriptionView(java.util.UUID publicId, boolean active,
                                       java.time.Instant createdAt, java.time.Instant lastUsedAt,
                                       java.time.Instant revokedAt) {
    }

    /**
     * État de la poussée pour l'appelant.
     *
     * @param providerActive {@code false} lorsqu'aucune clé VAPID n'est
     *                       configurée : <strong>aucune poussée n'a lieu</strong>,
     *                       et l'interface doit le dire au lieu de laisser
     *                       croire à un service en marche.
     */
    record PushStatus(boolean providerActive,
                             java.util.List<PushSubscriptionView> subscriptions) {
    }
}
