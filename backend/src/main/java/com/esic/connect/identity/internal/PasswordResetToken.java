package com.esic.connect.identity.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Demande de réinitialisation de mot de passe (EF-AUTH-005,
 * docs/02-cahier-des-charges.md §17.8 ; migration V17).
 *
 * <p>Le jeton brut n'est jamais stocké : seule son empreinte SHA-256
 * hexadécimale l'est. L'unicité d'une demande {@code PENDING} par compte
 * est garantie en base par la colonne générée {@code active_reset_key},
 * non mappée ici.
 */
@Entity
@Table(name = "password_reset_token")
@EntityListeners(AuditingEntityListener.class)
public class PasswordResetToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PasswordResetStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PasswordResetToken() {
        // JPA
    }

    PasswordResetToken(UserAccount user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.status = PasswordResetStatus.PENDING;
        this.expiresAt = expiresAt;
    }

    UserAccount getUser() {
        return user;
    }

    PasswordResetStatus getStatus() {
        return status;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    boolean isUsable(Instant now) {
        return status == PasswordResetStatus.PENDING && now.isBefore(expiresAt);
    }

    void consume(Instant now) {
        this.status = PasswordResetStatus.CONSUMED;
        this.consumedAt = now;
    }

    void revoke(Instant now) {
        this.status = PasswordResetStatus.REVOKED;
        this.revokedAt = now;
    }

    void markExpired() {
        this.status = PasswordResetStatus.EXPIRED;
    }
}
