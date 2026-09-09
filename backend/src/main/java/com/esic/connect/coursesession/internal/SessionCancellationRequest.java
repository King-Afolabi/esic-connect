package com.esic.connect.coursesession.internal;

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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Demande d'annulation d'une séance déposée par un formateur
 * (EF-SES-008 ; docs/02 §14.4 ; migration V22).
 *
 * <p>Le formateur <strong>demande</strong>, il ne décide pas : c'est la
 * même logique que pour le remplacement, qu'il peut proposer sans
 * pouvoir le valider (RG-024). La décision revient au responsable
 * pédagogique ou à l'administration.
 *
 * <p>L'historique est conservé : une demande refusée n'est pas
 * supprimée, et une nouvelle demande peut être déposée ensuite.
 */
@Entity
@Table(name = "session_cancellation_request")
@EntityListeners(AuditingEntityListener.class)
public class SessionCancellationRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_session_id", nullable = false, updatable = false)
    private CourseSession session;

    @Column(name = "requested_by_id", nullable = false, updatable = false)
    private Long requestedById;

    @Column(name = "reason", nullable = false, length = 500, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CancellationRequestStatus status;

    @Column(name = "decided_by_id")
    private Long decidedById;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_comment", length = 500)
    private String decisionComment;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SessionCancellationRequest() {
        // JPA
    }

    SessionCancellationRequest(CourseSession session, Long requestedById, String reason) {
        this.session = session;
        this.requestedById = requestedById;
        this.reason = reason;
        this.status = CancellationRequestStatus.REQUESTED;
    }

    void decide(CancellationRequestStatus decision, Long deciderId, String comment, Instant at) {
        this.status = decision;
        this.decidedById = deciderId;
        this.decisionComment = comment;
        this.decidedAt = at;
    }

    void withdraw(Instant at) {
        this.status = CancellationRequestStatus.WITHDRAWN;
        this.decidedAt = at;
    }

    boolean isPending() {
        return status == CancellationRequestStatus.REQUESTED;
    }

    CourseSession getSession() {
        return session;
    }

    Long getRequestedById() {
        return requestedById;
    }

    String getReason() {
        return reason;
    }

    CancellationRequestStatus getStatus() {
        return status;
    }

    Instant getDecidedAt() {
        return decidedAt;
    }

    String getDecisionComment() {
        return decisionComment;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
