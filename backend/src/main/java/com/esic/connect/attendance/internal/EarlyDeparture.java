package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.EarlyDepartureOpinion;
import com.esic.connect.attendance.EarlyDepartureStatus;
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
 * Dossier de départ anticipé (EF-ATT-013 ; docs/02 §16.13).
 *
 * <p>Aucune relation JPA vers {@code coursesession} ni {@code enrollment} :
 * les colonnes sont des clés étrangères SQL, les références se résolvent
 * par les ports publics.
 *
 * <p>L'effet ({@code PARTIAL} / {@code EXCUSED_PARTIAL} /
 * {@code TO_CONFIRM}) n'est pas une colonne : il se déduit du statut. Le
 * stocker créerait deux vérités pour une seule décision.
 */
@Entity
@Table(name = "early_departure")
@EntityListeners(AuditingEntityListener.class)
class EarlyDeparture extends BaseEntity {

    @Column(name = "course_session_id", nullable = false, updatable = false)
    private Long courseSessionId;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private Long enrollmentId;

    @Column(name = "departure_at", nullable = false, updatable = false)
    private Instant departureAt;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private EarlyDepartureStatus status;

    @Column(name = "requested_by_id", nullable = false, updatable = false)
    private Long requestedById;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "teacher_opinion", length = 16)
    private EarlyDepartureOpinion teacherOpinion;

    @Column(name = "teacher_opinion_comment", length = 500)
    private String teacherOpinionComment;

    @Column(name = "teacher_opinion_by_id")
    private Long teacherOpinionById;

    @Column(name = "teacher_opinion_at")
    private Instant teacherOpinionAt;

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

    protected EarlyDeparture() {
        // JPA
    }

    EarlyDeparture(Long courseSessionId, Long enrollmentId, Instant departureAt, String reason,
                   Long requestedById, Instant requestedAt) {
        this.courseSessionId = courseSessionId;
        this.enrollmentId = enrollmentId;
        this.departureAt = departureAt;
        this.reason = reason;
        this.status = EarlyDepartureStatus.REQUESTED;
        this.requestedById = requestedById;
        this.requestedAt = requestedAt;
    }

    /**
     * Le formateur transmet au responsable pédagogique, avec ou sans avis.
     * Le dossier reste ouvert : transmettre n'est pas décider.
     */
    void forward(EarlyDepartureOpinion opinion, String comment, Long actorId, Instant at) {
        this.status = EarlyDepartureStatus.FORWARDED;
        this.teacherOpinion = opinion;
        this.teacherOpinionComment = comment;
        this.teacherOpinionById = opinion == null ? null : actorId;
        this.teacherOpinionAt = opinion == null ? null : at;
    }

    /** Décision finale, toujours horodatée et attribuée. */
    void decide(boolean accepted, String comment, Long actorId, Instant at) {
        this.status = accepted ? EarlyDepartureStatus.ACCEPTED : EarlyDepartureStatus.REFUSED;
        this.decisionComment = comment;
        this.decidedById = actorId;
        this.decidedAt = at;
    }

    Long getCourseSessionId() {
        return courseSessionId;
    }

    Long getEnrollmentId() {
        return enrollmentId;
    }

    Instant getDepartureAt() {
        return departureAt;
    }

    String getReason() {
        return reason;
    }

    EarlyDepartureStatus getStatus() {
        return status;
    }

    Long getRequestedById() {
        return requestedById;
    }

    Instant getRequestedAt() {
        return requestedAt;
    }

    EarlyDepartureOpinion getTeacherOpinion() {
        return teacherOpinion;
    }

    String getTeacherOpinionComment() {
        return teacherOpinionComment;
    }

    Instant getTeacherOpinionAt() {
        return teacherOpinionAt;
    }

    Long getDecidedById() {
        return decidedById;
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
