package com.esic.connect.coursesession.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Remplacement d'un formateur sur une séance (V14 ; bloc G1-C.2 ;
 * EF-SES-005 ; CAD §24 RG-12 « un remplacement est autorisé et audité » ;
 * CDC §43 RG-015).
 *
 * <p>Le formateur principal de {@code course_session} n'est
 * <strong>jamais</strong> écrasé : {@code originalTeacherUserId} est figé
 * à la création pour la traçabilité, et le remplaçant obtient les droits
 * de gestion de la séance uniquement <em>pendant</em> sa période de
 * validité (voir {@link CourseSessionAccessGuard}). Aucune suppression
 * métier : une substitution se termine logiquement
 * ({@link TeacherSubstitutionStatus#ENDED} + {@code endedAt}).
 *
 * <p>{@code courseSessionId}, {@code substituteTeacherUserId},
 * {@code originalTeacherUserId} sont des valeurs techniques (clés
 * étrangères SQL) — aucun partage d'entité JPA entre modules.
 */
@Entity
@Table(name = "teacher_substitution")
@EntityListeners(AuditingEntityListener.class)
class TeacherSubstitution extends BaseEntity {

    @Column(name = "course_session_id", nullable = false, updatable = false)
    private Long courseSessionId;

    @Column(name = "substitute_teacher_user_id", nullable = false, updatable = false)
    private Long substituteTeacherUserId;

    @Column(name = "original_teacher_user_id", nullable = false, updatable = false)
    private Long originalTeacherUserId;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false, updatable = false)
    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TeacherSubstitutionStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_id")
    private Long createdById;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "ended_by_id")
    private Long endedById;

    protected TeacherSubstitution() {
        // JPA
    }

    TeacherSubstitution(Long courseSessionId, Long substituteTeacherUserId, Long originalTeacherUserId,
                        String reason, Instant validFrom, Instant validUntil, Long actorId) {
        this.courseSessionId = courseSessionId;
        this.substituteTeacherUserId = substituteTeacherUserId;
        this.originalTeacherUserId = originalTeacherUserId;
        this.reason = reason;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.status = TeacherSubstitutionStatus.ACTIVE;
        this.createdById = actorId;
    }

    void end(Instant at, Long actorId) {
        this.status = TeacherSubstitutionStatus.ENDED;
        this.endedAt = at;
        this.endedById = actorId;
    }

    boolean isActive() {
        return status == TeacherSubstitutionStatus.ACTIVE;
    }

    /** Vrai si la substitution est {@code ACTIVE} et couvre l'instant {@code at}. */
    boolean coversInstant(Instant at) {
        return isActive() && !at.isBefore(validFrom) && at.isBefore(validUntil);
    }

    /** Vrai si {@code [validFrom, validUntil)} chevauche {@code [from, until)}. */
    boolean overlaps(Instant from, Instant until) {
        return validFrom.isBefore(until) && from.isBefore(validUntil);
    }

    Long getCourseSessionId() {
        return courseSessionId;
    }

    Long getSubstituteTeacherUserId() {
        return substituteTeacherUserId;
    }

    Long getOriginalTeacherUserId() {
        return originalTeacherUserId;
    }

    String getReason() {
        return reason;
    }

    Instant getValidFrom() {
        return validFrom;
    }

    Instant getValidUntil() {
        return validUntil;
    }

    TeacherSubstitutionStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getEndedAt() {
        return endedAt;
    }
}
