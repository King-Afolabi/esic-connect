package com.esic.connect.notification.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Abonnement d'un appareil à la poussée web (V33 ; EF-NOTIF-005 ;
 * docs/02 §29.3).
 *
 * <p>L'URL de terminaison contient un jeton opaque propre à l'appareil :
 * elle est traitée comme un secret — jamais journalisée, jamais renvoyée
 * par l'API. Son empreinte porte l'unicité, de sorte qu'un renouvellement
 * réutilise la ligne au lieu d'en empiler.
 *
 * <p>La révocation est une date et non une suppression : sans cela, une
 * page restée ouverte recréerait aussitôt l'abonnement que la personne
 * vient de retirer.
 */
@Entity
@Table(name = "push_subscription")
class PushSubscription extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "endpoint", nullable = false, length = 1024)
    private String endpoint;

    @Column(name = "endpoint_hash", nullable = false, updatable = false, columnDefinition = "CHAR(64)")
    private String endpointHash;

    @Column(name = "p256dh_key", nullable = false)
    private String p256dhKey;

    @Column(name = "auth_secret", nullable = false)
    private String authSecret;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected PushSubscription() {
        // JPA
    }

    PushSubscription(Long userId, String endpoint, String endpointHash, String p256dhKey,
                     String authSecret, Instant now) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.endpointHash = endpointHash;
        this.p256dhKey = p256dhKey;
        this.authSecret = authSecret;
        this.createdAt = now;
    }

    Long getUserId() {
        return userId;
    }

    String getEndpoint() {
        return endpoint;
    }

    String getP256dhKey() {
        return p256dhKey;
    }

    String getAuthSecret() {
        return authSecret;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getLastUsedAt() {
        return lastUsedAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }

    /** Réactive et met à jour les clés d'un abonnement renouvelé par le navigateur. */
    void renew(Long userId, String p256dhKey, String authSecret) {
        this.userId = userId;
        this.p256dhKey = p256dhKey;
        this.authSecret = authSecret;
        this.revokedAt = null;
    }

    void markUsed(Instant at) {
        this.lastUsedAt = at;
    }

    void revoke(Instant at) {
        this.revokedAt = at;
    }

    boolean isActive() {
        return revokedAt == null;
    }
}
