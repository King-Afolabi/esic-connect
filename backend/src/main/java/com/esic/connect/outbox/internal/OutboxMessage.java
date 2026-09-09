package com.esic.connect.outbox.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Ligne de la file transactionnelle des effets de bord (V31).
 *
 * <p>Cette entité décrit une <strong>intention</strong>, pas un résultat :
 * elle est écrite dans la transaction métier et ne dit rien de ce qui a
 * été produit. Ce que le gestionnaire en fait — une trace d'audit, des
 * notifications, un courriel — n'appartient pas à ce module.
 */
@Entity
@Table(name = "outbox_message")
class OutboxMessage extends BaseEntity {

    @Column(name = "message_type", nullable = false, updatable = false)
    private String messageType;

    @Column(name = "dedup_key", nullable = false, updatable = false, columnDefinition = "CHAR(64)")
    private String dedupKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected OutboxMessage() {
        // JPA
    }

    OutboxMessage(String messageType, String dedupKey, String payload, Instant createdAt) {
        this.messageType = messageType;
        this.dedupKey = dedupKey;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.createdAt = createdAt;
        this.nextAttemptAt = createdAt;
    }

    String getMessageType() {
        return messageType;
    }

    String getDedupKey() {
        return dedupKey;
    }

    String getPayload() {
        return payload;
    }

    OutboxStatus getStatus() {
        return status;
    }

    int getAttempts() {
        return attempts;
    }

    Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    String getLastError() {
        return lastError;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getProcessedAt() {
        return processedAt;
    }

    /**
     * Réclamation : la ligne devient invisible des autres diffuseurs
     * jusqu'à {@code until}. Le statut n'est PAS modifié — si la JVM
     * s'arrête avant le résultat, la ligne redevient traitable d'elle-même
     * à l'expiration, sans intervention.
     */
    void reserveUntil(Instant until) {
        this.nextAttemptAt = until;
    }

    /** Le gestionnaire a rendu la main : la ligne est close. */
    void markSent(Instant at) {
        this.status = OutboxStatus.SENT;
        this.processedAt = at;
        this.lastError = null;
    }

    /**
     * Tentative en échec. Tant qu'il reste des tentatives, la ligne
     * retourne en attente avec une reprise différée ; sinon elle rejoint
     * la file d'échec, où seul un humain la relance (EF-OPS-005).
     *
     * @param cause libellé NON sensible (nom de classe / code)
     */
    void markFailed(Instant at, String cause, int maxAttempts, Instant retryAt) {
        this.attempts += 1;
        this.lastError = truncate(cause);
        if (this.attempts >= maxAttempts) {
            this.status = OutboxStatus.DEAD;
            this.processedAt = at;
        } else {
            this.status = OutboxStatus.FAILED;
            this.processedAt = null;
            this.nextAttemptAt = retryAt;
        }
    }

    /**
     * Rejeu manuel depuis la file d'échec : la ligne redevient traitable
     * immédiatement. Le compteur de tentatives est remis à zéro — sans
     * quoi un rejeu retomberait aussitôt en file d'échec et le bouton
     * n'aurait aucun effet observable.
     */
    void requeue(Instant at) {
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = at;
        this.processedAt = null;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 255 ? value : value.substring(0, 255);
    }
}
