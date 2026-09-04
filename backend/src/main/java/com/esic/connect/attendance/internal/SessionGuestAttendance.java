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
 * Entrée provisoire créée par un formateur pour une personne présente en
 * séance sans inscription enregistrée (EF-ATT-007 ; docs/02 §16.12).
 *
 * <p>L'identité portée ici est <strong>déclarée</strong> par le formateur,
 * jamais vérifiée. C'est la raison d'être de cette table séparée : la
 * confondre avec une présence enregistrée reviendrait à traiter une
 * affirmation comme un fait.
 *
 * <p>Aucune relation JPA vers {@code coursesession} ni {@code enrollment} :
 * les colonnes sont des clés étrangères SQL, les références passent par
 * les ports publics.
 */
@Entity
@Table(name = "session_guest_attendance")
@EntityListeners(AuditingEntityListener.class)
class SessionGuestAttendance extends BaseEntity {

    @Column(name = "course_session_id", nullable = false, updatable = false)
    private Long courseSessionId;

    @Column(name = "attendance_checkpoint_id", updatable = false)
    private Long attendanceCheckpointId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "comment", length = 500)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionGuestStatus status;

    @Column(name = "recorded_by_id", nullable = false, updatable = false)
    private Long recordedById;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "resolved_by_id")
    private Long resolvedById;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_comment", length = 500)
    private String resolutionComment;

    @Column(name = "linked_enrollment_id")
    private Long linkedEnrollmentId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SessionGuestAttendance() {
        // JPA
    }

    SessionGuestAttendance(Long courseSessionId, Long attendanceCheckpointId, String firstName,
                           String lastName, String email, String comment,
                           SessionGuestStatus status, Long recordedById, Instant recordedAt) {
        this.courseSessionId = courseSessionId;
        this.attendanceCheckpointId = attendanceCheckpointId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.comment = comment;
        this.status = status;
        this.recordedById = recordedById;
        this.recordedAt = recordedAt;
    }

    /**
     * Rattache l'entrée à une inscription réelle. Le rattachement ne
     * <strong>crée pas</strong> de présence : il constate que la personne
     * était bien un apprenant attendu. La présence, elle, se saisit par
     * la voie manuelle ordinaire, motivée et auditée.
     */
    void link(Long enrollmentId, String motive, Long actorId, Instant at) {
        this.status = SessionGuestStatus.LINKED;
        this.linkedEnrollmentId = enrollmentId;
        this.resolutionComment = motive;
        this.resolvedById = actorId;
        this.resolvedAt = at;
    }

    /** Écarte l'entrée avec motif ; elle reste consultable. */
    void dismiss(String motive, Long actorId, Instant at) {
        this.status = SessionGuestStatus.DISMISSED;
        this.resolutionComment = motive;
        this.resolvedById = actorId;
        this.resolvedAt = at;
    }

    boolean isPending() {
        return status.isPending();
    }

    Long getCourseSessionId() {
        return courseSessionId;
    }

    Long getAttendanceCheckpointId() {
        return attendanceCheckpointId;
    }

    String getFirstName() {
        return firstName;
    }

    String getLastName() {
        return lastName;
    }

    String getEmail() {
        return email;
    }

    String getComment() {
        return comment;
    }

    SessionGuestStatus getStatus() {
        return status;
    }

    Instant getRecordedAt() {
        return recordedAt;
    }

    Instant getResolvedAt() {
        return resolvedAt;
    }

    String getResolutionComment() {
        return resolutionComment;
    }

    Long getLinkedEnrollmentId() {
        return linkedEnrollmentId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
