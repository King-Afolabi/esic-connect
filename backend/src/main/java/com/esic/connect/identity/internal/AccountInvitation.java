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
 * Invitation d'activation d'un compte (docs/04-modele-donnees.md §10.4,
 * cahier §11).
 *
 * Le jeton brut n'est jamais stocké : seule son empreinte SHA-256 (hex)
 * est conservée dans {@code token_hash}. L'unicité d'une invitation
 * {@code PENDING} par compte est garantie par la colonne générée
 * {@code active_invitation_key} (migration V3), non mappée ici.
 */
@Entity
@Table(name = "account_invitation")
@EntityListeners(AuditingEntityListener.class)
public class AccountInvitation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountInvitationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_id")
    private Long createdById;

    protected AccountInvitation() {
        // JPA
    }

    public AccountInvitation(UserAccount user, String tokenHash, Instant expiresAt, Long createdById) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.status = AccountInvitationStatus.PENDING;
        this.expiresAt = expiresAt;
        this.createdById = createdById;
    }

    public UserAccount getUser() {
        return user;
    }

    public AccountInvitationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public boolean isExpiredAt(Instant reference) {
        return !expiresAt.isAfter(reference);
    }

    /** Vraie seulement si l'invitation est actionnable à l'instant donné. */
    public boolean isUsableAt(Instant reference) {
        return status == AccountInvitationStatus.PENDING && !isExpiredAt(reference);
    }

    public void markAccepted(Instant acceptedAt) {
        this.status = AccountInvitationStatus.ACCEPTED;
        this.usedAt = acceptedAt;
    }

    public void revoke(Instant revokedAt) {
        this.status = AccountInvitationStatus.REVOKED;
        this.revokedAt = revokedAt;
    }
}
