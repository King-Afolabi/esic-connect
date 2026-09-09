package com.esic.connect.identity.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Appareil reconnu pour un compte (EF-AUTH-013, docs/02 §17.7).
 *
 * <p>L'appareil n'est identifié que par une <strong>empreinte</strong>
 * calculée côté serveur à partir d'un identifiant que le client conserve
 * localement. Ni user-agent en clair, ni adresse IP : l'empreinte suffit
 * à reconnaître un retour, elle ne permet pas de reconstituer le terminal
 * ni de le relier à une personne hors de ce compte (RG-094).
 *
 * <p>La confiance est <strong>bornée dans le temps</strong> : passée
 * {@code expiresAt}, l'appareil n'est plus reconnu et une vérification
 * complète est redemandée, même sans révocation explicite.
 */
@Entity
@Table(name = "trusted_device")
@EntityListeners(AuditingEntityListener.class)
public class TrustedDevice extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_hash", nullable = false, length = 64)
    private String deviceHash;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TrustedDeviceStatus status;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TrustedDevice() {
    }

    static TrustedDevice remembered(Long userId, String deviceHash, String label,
                                    Instant now, Instant expiresAt) {
        TrustedDevice device = new TrustedDevice();
        device.userId = userId;
        device.deviceHash = deviceHash;
        device.label = label;
        device.status = TrustedDeviceStatus.ACTIVE;
        device.firstSeenAt = now;
        device.lastSeenAt = now;
        device.expiresAt = expiresAt;
        return device;
    }

    void touch(Instant now, Instant expiresAt) {
        this.lastSeenAt = now;
        this.expiresAt = expiresAt;
    }

    void revoke(Instant now) {
        this.status = TrustedDeviceStatus.REVOKED;
        this.revokedAt = now;
    }

    boolean isUsableAt(Instant now) {
        return status == TrustedDeviceStatus.ACTIVE && expiresAt.isAfter(now);
    }

    public Long getUserId() {
        return userId;
    }

    public String getLabel() {
        return label;
    }

    public TrustedDeviceStatus getStatus() {
        return status;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
