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
}
