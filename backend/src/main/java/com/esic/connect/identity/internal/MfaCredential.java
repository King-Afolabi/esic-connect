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
 * Second facteur TOTP d'un compte (EF-AUTH-008 ; migration V18).
 *
 * <p>Le secret partagé est stocké <strong>chiffré</strong>
 * ({@link SecretCipher}) : il n'apparaît jamais en clair en base, ni dans
 * un journal, ni dans une réponse HTTP après l'écran d'enrôlement.
 *
 * <p>{@code lastUsedStep} porte l'anti-rejeu : un code TOTP reste
 * arithmétiquement valide pendant tout son pas de 30 secondes et, avec la
 * tolérance de dérive, un peu au-delà. Mémoriser le dernier pas consommé
 * interdit qu'un code intercepté serve une seconde fois.
 */
@Entity
@Table(name = "mfa_credential")
@EntityListeners(AuditingEntityListener.class)
public class MfaCredential extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "secret_cipher", nullable = false, length = 512)
    private String secretCipher;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MfaCredentialStatus status;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_step")
    private Long lastUsedStep;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MfaCredential() {
    }

    private MfaCredential(Long userId, String secretCipher) {
        this.userId = userId;
        this.secretCipher = secretCipher;
        this.status = MfaCredentialStatus.PENDING;
    }

    static MfaCredential pending(Long userId, String secretCipher) {
        return new MfaCredential(userId, secretCipher);
    }

    void confirm(Instant now, long step) {
        this.status = MfaCredentialStatus.ACTIVE;
        this.confirmedAt = now;
        this.lastUsedStep = step;
    }

    void recordUse(long step) {
        this.lastUsedStep = step;
    }

    void revoke(Instant now) {
        this.status = MfaCredentialStatus.REVOKED;
        this.revokedAt = now;
    }

    /** Vrai si ce pas de temps a déjà servi : le code est alors refusé. */
    boolean alreadyUsed(long step) {
        return lastUsedStep != null && step <= lastUsedStep;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSecretCipher() {
        return secretCipher;
    }

    public MfaCredentialStatus getStatus() {
        return status;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Long getLastUsedStep() {
        return lastUsedStep;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
