package com.esic.connect.notification.internal;

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
 * Trace d'envoi d'un courriel (EF-USER-008 ; docs/02 §11.3 ;
 * migration V19).
 *
 * <p><strong>Deux axes, jamais confondus.</strong> {@code internalStatus}
 * dit ce que nous avons fait ; {@code providerStatus} dit ce que le
 * fournisseur a constaté. Un message peut être {@code SENT_TO_PROVIDER}
 * et pourtant {@code BOUNCED} : c'est précisément le cas qu'un
 * responsable doit voir pour corriger une adresse.
 *
 * <p><strong>L'adresse n'est pas stockée en clair.</strong> Seules son
 * empreinte — pour retrouver les envois d'une même adresse — et une forme
 * masquée pour l'affichage le sont. Un export de cette table ne constitue
 * pas un annuaire exploitable.
 */
@Entity
@Table(name = "email_delivery")
@EntityListeners(AuditingEntityListener.class)
public class EmailDelivery extends BaseEntity {

    @Column(name = "recipient_hash", nullable = false, length = 64)
    private String recipientHash;

    @Column(name = "recipient_masked", nullable = false, length = 120)
    private String recipientMasked;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "message_type", nullable = false, length = 40)
    private String messageType;

    @Enumerated(EnumType.STRING)
    @Column(name = "internal_status", nullable = false)
    private EmailInternalStatus internalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_status", nullable = false)
    private EmailProviderStatus providerStatus;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmailDelivery() {
        // JPA
    }

    public EmailDelivery(String recipientHash, String recipientMasked, Long userId, String messageType) {
        this.recipientHash = recipientHash;
        this.recipientMasked = recipientMasked;
        this.userId = userId;
        this.messageType = messageType;
        this.internalStatus = EmailInternalStatus.QUEUED;
        this.providerStatus = EmailProviderStatus.UNKNOWN;
        this.attempts = 0;
    }

    /** Le message est parti vers le serveur de messagerie sans erreur. */
    public void markSentToProvider(Instant at) {
        this.internalStatus = EmailInternalStatus.SENT_TO_PROVIDER;
        this.attempts++;
        this.lastAttemptAt = at;
        this.lastError = null;
    }

    /**
     * @param reason catégorie technique de l'échec, jamais le corps du
     *               message ni le jeton qu'il contenait
     */
    public void markFailed(Instant at, String reason) {
        this.internalStatus = EmailInternalStatus.PROCESSING_FAILED;
        this.attempts++;
        this.lastAttemptAt = at;
        this.lastError = reason == null ? null
                : reason.substring(0, Math.min(reason.length(), 500));
    }

    /** Retour de délivrabilité du fournisseur, quand il en donne un. */
    public void applyProviderStatus(EmailProviderStatus status) {
        this.providerStatus = status;
    }

    public String getRecipientHash() {
        return recipientHash;
    }

    public String getRecipientMasked() {
        return recipientMasked;
    }

    public Long getUserId() {
        return userId;
    }

    public String getMessageType() {
        return messageType;
    }

    public EmailInternalStatus getInternalStatus() {
        return internalStatus;
    }

    public EmailProviderStatus getProviderStatus() {
        return providerStatus;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
