package com.esic.connect.notification.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification métier persistante (V15 ; bloc G1-D ; EF-NOTIF-001 /
 * EF-NOTIF-002 ; RG-033).
 *
 * <p>{@code recipientUserId} et {@code resourcePublicId} sont des valeurs
 * techniques : aucune relation JPA vers {@code identity} /
 * {@code coursesession} / {@code planning}. Le corps
 * ({@code title} / {@code body}) est <strong>neutre</strong> : jamais de
 * jeton, code court, IP, contenu de justificatif, chemin de fichier,
 * secret. {@code dedupKey} garantit « au plus une notification par
 * (destinataire, événement) » même si le listener {@code AFTER_COMMIT}
 * est rejoué.
 */
@Entity
@Table(name = "notification")
class Notification extends BaseEntity {

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private Long recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private NotificationType type;

    @Column(name = "title", nullable = false, updatable = false, length = 150)
    private String title;

    @Column(name = "body", nullable = false, updatable = false, length = 500)
    private String body;

    @Column(name = "resource_type", nullable = false, updatable = false, length = 32)
    private String resourceType;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "resource_public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID resourcePublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotificationStatus status;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "dedup_key", nullable = false, updatable = false, length = 64)
    private String dedupKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
        // JPA
    }

    Notification(Long recipientUserId, NotificationType type, String title, String body,
                 String resourceType, UUID resourcePublicId, String dedupKey, Instant createdAt) {
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.resourceType = resourceType;
        this.resourcePublicId = resourcePublicId;
        this.dedupKey = dedupKey;
        this.status = NotificationStatus.UNREAD;
        this.createdAt = createdAt;
    }

    /** Passe la notification en {@code READ} (idempotent : sans effet si déjà lue / archivée). */
    boolean markRead(Instant at) {
        if (status != NotificationStatus.UNREAD) {
            return false;
        }
        this.status = NotificationStatus.READ;
        this.readAt = at;
        return true;
    }

    Long getRecipientUserId() {
        return recipientUserId;
    }

    NotificationType getType() {
        return type;
    }

    String getTitle() {
        return title;
    }

    String getBody() {
        return body;
    }

    String getResourceType() {
        return resourceType;
    }

    UUID getResourcePublicId() {
        return resourcePublicId;
    }

    NotificationStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getReadAt() {
        return readAt;
    }
}
