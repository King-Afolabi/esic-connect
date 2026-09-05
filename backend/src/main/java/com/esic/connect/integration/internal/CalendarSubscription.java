package com.esic.connect.integration.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Abonnement iCalendar d'un utilisateur (V34 ; EF-INT-001, AC-034).
 *
 * <p>N'étend pas {@code BaseEntity} : la table n'a ni {@code updated_at}
 * ni {@code created_by}, et son cycle de vie est une révocation datée,
 * pas une suite de modifications.
 *
 * <p>Le jeton n'est <strong>jamais</strong> conservé : seule son
 * empreinte SHA-256 l'est. Le porteur du lien est le seul à détenir la
 * valeur, et une fuite de la base ne rend aucun planning lisible.
 */
@Entity
@Table(name = "calendar_subscription")
class CalendarSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** Référence publique portée par l'URL : sert à retrouver la ligne. */
    @Column(name = "feed_key", nullable = false, updatable = false, columnDefinition = "CHAR(32)")
    private String feedKey;

    @Column(name = "token_hash", nullable = false, updatable = false, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "label", length = 120)
    private String label;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected CalendarSubscription() {
        // JPA
    }

    CalendarSubscription(Long userId, String feedKey, String tokenHash, String label, Instant createdAt) {
        this.publicId = UUID.randomUUID();
        this.userId = userId;
        this.feedKey = feedKey;
        this.tokenHash = tokenHash;
        this.label = label;
        this.createdAt = createdAt;
    }

    Long getId() {
        return id;
    }

    UUID getPublicId() {
        return publicId;
    }

    Long getUserId() {
        return userId;
    }

    String getFeedKey() {
        return feedKey;
    }

    String getTokenHash() {
        return tokenHash;
    }

    String getLabel() {
        return label;
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

    boolean isRevoked() {
        return revokedAt != null;
    }

    void revoke(Instant when) {
        if (revokedAt == null) {
            this.revokedAt = when;
        }
    }

    void markUsed(Instant when) {
        this.lastUsedAt = when;
    }
}
