package com.esic.connect.notification.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Exception au réglage par défaut d'un canal, pour une catégorie et un
 * utilisateur (V33 ; EF-NOTIF-006).
 *
 * <p>Seules les exceptions sont stockées : l'absence de ligne signifie
 * « comme par défaut », c'est-à-dire activé. Une catégorie ajoutée plus
 * tard est donc immédiatement active pour tous, sans migration de données
 * et sans que personne ne cesse silencieusement d'être prévenu.
 */
@Entity
@Table(name = "notification_preference")
class NotificationPreference extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, updatable = false, length = 32)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false, length = 16)
    private NotificationChannel channel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationPreference() {
        // JPA
    }

    NotificationPreference(Long userId, NotificationCategory category, NotificationChannel channel,
                           boolean enabled, Instant now) {
        this.userId = userId;
        this.category = category;
        this.channel = channel;
        this.enabled = enabled;
        this.createdAt = now;
        this.updatedAt = now;
    }

    NotificationCategory getCategory() {
        return category;
    }

    NotificationChannel getChannel() {
        return channel;
    }

    boolean isEnabled() {
        return enabled;
    }

    void setEnabled(boolean enabled, Instant now) {
        this.enabled = enabled;
        this.updatedAt = now;
    }
}
