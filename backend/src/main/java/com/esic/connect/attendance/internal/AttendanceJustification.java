package com.esic.connect.attendance.internal;

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
 * Justificatif d'absence — <strong>métadonnée métier sans fichier</strong>
 * dans cette tranche (catégorie, référence externe, commentaire). Rattaché
 * à la présence {@code ABSENT} qu'il justifie.
 *
 * <p>Un justificatif accepté fait passer la présence
 * {@code ABSENT → EXCUSED_ABSENCE} (jamais {@code PRESENT}) ; un refus la
 * laisse / la remet {@code ABSENT}. L'unicité d'un justificatif « actif »
 * (non refusé) par absence est garantie par la colonne générée
 * {@code active_justification_key} (migration V10) : après un refus, un
 * nouveau dépôt est possible.
 *
 * <p>{@code attendanceRecordId}, {@code submittedById} et
 * {@code reviewedById} sont des valeurs techniques (FK SQL).
 */
@Entity
@Table(name = "attendance_justification")
@EntityListeners(AuditingEntityListener.class)
class AttendanceJustification extends BaseEntity {

    @Column(name = "attendance_record_id", nullable = false, updatable = false)
    private Long attendanceRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private JustificationCategory category;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "comment", nullable = false)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JustificationStatus status;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "submitted_by_id", nullable = false, updatable = false)
    private Long submittedById;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by_id")
    private Long reviewedById;

    @Column(name = "decision_reason")
    private String decisionReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AttendanceJustification() {
        // JPA
    }

    AttendanceJustification(Long attendanceRecordId, JustificationCategory category, String externalReference,
                            String comment, Long submittedById, Instant submittedAt) {
        this.attendanceRecordId = attendanceRecordId;
        this.category = category;
        this.externalReference = externalReference;
        this.comment = comment;
        this.submittedById = submittedById;
        this.submittedAt = submittedAt;
        this.status = JustificationStatus.PENDING;
    }

    /** Modification par l'apprenant, autorisée seulement tant que {@code PENDING}. */
    void amend(JustificationCategory category, String externalReference, String comment) {
        this.category = category;
        this.externalReference = externalReference;
        this.comment = comment;
    }

    void accept(Long reviewerId, Instant at) {
        this.status = JustificationStatus.ACCEPTED;
        this.reviewedById = reviewerId;
        this.reviewedAt = at;
        this.decisionReason = null;
    }

    void reject(Long reviewerId, Instant at, String reason) {
        this.status = JustificationStatus.REJECTED;
        this.reviewedById = reviewerId;
        this.reviewedAt = at;
        this.decisionReason = reason;
    }

    boolean isPending() {
        return status == JustificationStatus.PENDING;
    }

    Long getAttendanceRecordId() {
        return attendanceRecordId;
    }

    JustificationCategory getCategory() {
        return category;
    }

    String getExternalReference() {
        return externalReference;
    }

    String getComment() {
        return comment;
    }

    JustificationStatus getStatus() {
        return status;
    }

    Instant getSubmittedAt() {
        return submittedAt;
    }

    Long getSubmittedById() {
        return submittedById;
    }

    Instant getReviewedAt() {
        return reviewedAt;
    }

    Long getReviewedById() {
        return reviewedById;
    }

    String getDecisionReason() {
        return decisionReason;
    }
}
