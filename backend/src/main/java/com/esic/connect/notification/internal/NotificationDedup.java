package com.esic.connect.notification.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Clé d'idempotence stable d'une notification (G1-D, DEC-G1-007) :
 * SHA-256 hexadécimal de
 * {@code type | resourcePublicId | recipientUserId | eventKey}.
 *
 * <p>{@code eventKey} identifie l'<em>occurrence</em> de l'événement
 * source : l'{@code eventId} d'un {@code CourseSessionChangeEvent}, ou le
 * {@code versionPublicId} d'un {@code PlanningPublishedEvent}. Deux
 * livraisons du même événement au même destinataire produisent donc la
 * même clé → au plus une ligne (contrainte {@code uq_notification_dedup}).
 */
final class NotificationDedup {

    private NotificationDedup() {
    }

    static String key(NotificationType type, UUID resourcePublicId, long recipientUserId, UUID eventKey) {
        String raw = type.name() + '|' + resourcePublicId + '|' + recipientUserId + '|' + eventKey;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponible", impossible);
        }
    }
}
