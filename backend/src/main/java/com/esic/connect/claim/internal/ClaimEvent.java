package com.esic.connect.claim.internal;

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
 * Décision prise sur une réclamation : transfert, changement de statut,
 * réouverture (EF-CLAIM-003, EF-CLAIM-004).
 *
 * <p>Séparé du fil de messages parce qu'une décision n'est pas de la
 * conversation : les mélanger rendrait l'historique des décisions
 * illisible dans un fil bavard. Append-only, motif obligatoire.
 */
@Entity
@Table(name = "claim_event")
@EntityListeners(AuditingEntityListener.class)
class ClaimEvent extends BaseEntity {

    /** Nature de la décision. */
    enum Type {
        CREATED,
        TRANSFERRED,
        STATUS_CHANGED,
        REOPENED
    }

    @Column(name = "claim_id", nullable = false, updatable = false)
    private Long claimId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false)
    private Type eventType;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;

    @Column(name = "from_status", length = 24, updatable = false)
    private String fromStatus;

    @Column(name = "to_status", length = 24, updatable = false)
    private String toStatus;

    @Column(name = "from_audience", length = 32, updatable = false)
    private String fromAudience;

    @Column(name = "to_audience", length = 32, updatable = false)
    private String toAudience;

    @Column(name = "motive", nullable = false, length = 500, updatable = false)
    private String motive;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClaimEvent() {
        // JPA
    }

    ClaimEvent(Long claimId, Type eventType, Long actorUserId, String fromStatus, String toStatus,
               String fromAudience, String toAudience, String motive) {
        this.claimId = claimId;
        this.eventType = eventType;
        this.actorUserId = actorUserId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.fromAudience = fromAudience;
        this.toAudience = toAudience;
        this.motive = motive;
    }

    Type getEventType() {
        return eventType;
    }

    String getFromStatus() {
        return fromStatus;
    }

    String getToStatus() {
        return toStatus;
    }

    String getFromAudience() {
        return fromAudience;
    }

    String getToAudience() {
        return toAudience;
    }

    String getMotive() {
        return motive;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
