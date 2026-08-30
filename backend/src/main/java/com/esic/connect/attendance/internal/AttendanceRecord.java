package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttendanceStatus;
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
 * Présence enregistrée (V9, enrichie par V10).
 *
 * <p>{@code attendanceCheckpointId}, {@code enrollmentId},
 * {@code studentUserId}, {@code recordedById} et {@code correctedById}
 * sont des valeurs techniques (clés étrangères SQL) résolues via des
 * ports publics — aucune relation JPA inter-module. L'unicité
 * {@code (attendance_checkpoint_id, enrollment_id)} est garantie par la
 * migration : c'est elle, et non un pré-contrôle applicatif, qui empêche
 * le double émargement concurrent (tous statuts confondus).
 *
 * <p>Aucune suppression physique : une présence retirée passe
 * {@link AttendanceStatus#CANCELLED} (ligne conservée) ; l'historique des
 * corrections est porté par {@code attendance_correction}.
 */
@Entity
@Table(name = "attendance_record")
@EntityListeners(AuditingEntityListener.class)
class AttendanceRecord extends BaseEntity {

    @Column(name = "attendance_checkpoint_id", nullable = false)
    private Long attendanceCheckpointId;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "recorded_by_id")
    private Long recordedById;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private AttendanceRecordSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttendanceStatus status;

    @Column(name = "late_minutes")
    private Integer lateMinutes;

    @Column(name = "comment")
    private String comment;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_corrected_at")
    private Instant lastCorrectedAt;

    @Column(name = "corrected_by_id")
    private Long correctedById;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected AttendanceRecord() {
        // JPA
    }

    /**
     * Émargement réussi par l'apprenant lui-même (QR / code court) :
     * {@code recordedById} nul (l'apprenant est {@code studentUserId}),
     * statut {@code PRESENT} ou {@code LATE}.
     */
    AttendanceRecord(Long attendanceCheckpointId, Long enrollmentId, Long studentUserId,
                     Instant recordedAt, AttendanceRecordSource source) {
        this(attendanceCheckpointId, enrollmentId, studentUserId, null, recordedAt, source,
                AttendanceStatus.PRESENT, null, null);
    }

    /**
     * Présence créée avec un statut explicite (émargement classé
     * {@code LATE}, saisie manuelle, absence à justifier).
     *
     * @param recordedById auteur de la saisie ({@code null} si l'apprenant
     *                     émarge lui-même)
     */
    AttendanceRecord(Long attendanceCheckpointId, Long enrollmentId, Long studentUserId, Long recordedById,
                     Instant recordedAt, AttendanceRecordSource source, AttendanceStatus status,
                     Integer lateMinutes, String comment) {
        this.attendanceCheckpointId = attendanceCheckpointId;
        this.enrollmentId = enrollmentId;
        this.studentUserId = studentUserId;
        this.recordedById = recordedById;
        this.recordedAt = recordedAt;
        this.source = source;
        this.status = status;
        this.lateMinutes = lateMinutes;
        this.comment = comment;
    }

    /**
     * Applique une correction : statut / retard / commentaire nouveaux,
     * traçabilité de correction. Ne touche jamais la {@code source}
     * d'origine.
     */
    void applyCorrection(AttendanceStatus newStatus, Integer newLateMinutes, String newComment,
                         Instant at, Long actorId) {
        this.status = newStatus;
        this.lateMinutes = newLateMinutes;
        this.comment = newComment;
        this.lastCorrectedAt = at;
        this.correctedById = actorId;
        if (newStatus == AttendanceStatus.CANCELLED) {
            this.cancelledAt = at;
        }
    }

    boolean isCancelled() {
        return status == AttendanceStatus.CANCELLED;
    }

    Long getAttendanceCheckpointId() {
        return attendanceCheckpointId;
    }

    Long getEnrollmentId() {
        return enrollmentId;
    }

    Long getStudentUserId() {
        return studentUserId;
    }

    Long getRecordedById() {
        return recordedById;
    }

    Instant getRecordedAt() {
        return recordedAt;
    }

    AttendanceRecordSource getSource() {
        return source;
    }

    AttendanceStatus getStatus() {
        return status;
    }

    Integer getLateMinutes() {
        return lateMinutes;
    }

    String getComment() {
        return comment;
    }

    Instant getLastCorrectedAt() {
        return lastCorrectedAt;
    }

    Instant getCancelledAt() {
        return cancelledAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
