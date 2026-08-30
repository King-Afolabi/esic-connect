package com.esic.connect.coursesession.internal;

import com.esic.connect.coursesession.AttendanceCheckpointStatus;
import com.esic.connect.coursesession.AttendanceCheckpointType;
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
 * Point de contrôle d'émargement d'une séance (V9, enrichi par V10).
 *
 * <p>V9 : un unique point de contrôle par séance, sans état propre.
 * V10 : plusieurs points de contrôle par séance, typés
 * ({@link AttendanceCheckpointType}), ordonnés ({@code displayOrder}),
 * obligatoires ou optionnels, avec un cycle de vie propre
 * ({@link AttendanceCheckpointStatus}). Le point {@code START} reste créé
 * automatiquement avec la séance pour préserver le parcours V9.
 *
 * <p>{@code attendance_record.attendance_checkpoint_id} (module
 * {@code attendance}) référence la clé primaire de cette entité (valeur
 * technique, résolue par le port {@code CourseSessionDirectory}).
 */
@Entity
@Table(name = "attendance_checkpoint")
@EntityListeners(AuditingEntityListener.class)
class AttendanceCheckpoint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_session_id", nullable = false)
    private CourseSession courseSession;

    @Column(name = "label", nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "checkpoint_type", nullable = false)
    private AttendanceCheckpointType checkpointType;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttendanceCheckpointStatus status;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_id")
    private Long createdById;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by_id")
    private Long updatedById;

    protected AttendanceCheckpoint() {
        // JPA
    }

    /**
     * Point de contrôle {@code START} créé automatiquement avec la séance
     * (compat V9 : « Arrivée », ordre 0, obligatoire, PLANNED).
     */
    AttendanceCheckpoint(CourseSession courseSession) {
        this(courseSession, "Arrivée", AttendanceCheckpointType.START, 0, true, null);
    }

    /**
     * Point de contrôle créé explicitement (V10).
     *
     * @param actorId auteur de la création ({@code null} si non résolu)
     */
    AttendanceCheckpoint(CourseSession courseSession, String label, AttendanceCheckpointType type,
                         int displayOrder, boolean required, Long actorId) {
        this.courseSession = courseSession;
        this.label = label;
        this.checkpointType = type;
        this.displayOrder = displayOrder;
        this.required = required;
        this.status = AttendanceCheckpointStatus.PLANNED;
        this.createdById = actorId;
        this.updatedById = actorId;
    }

    void open(Instant at) {
        open(at, null);
    }

    void open(Instant at, Long actorId) {
        this.status = AttendanceCheckpointStatus.OPEN;
        this.openedAt = at;
        this.updatedById = actorId;
    }

    void close(Instant at) {
        close(at, null);
    }

    void close(Instant at, Long actorId) {
        this.status = AttendanceCheckpointStatus.CLOSED;
        if (this.openedAt == null) {
            // Fermeture d'un point de contrôle jamais ouvert : on aligne
            // opened_at pour respecter chk_attendance_checkpoint_open_state.
            this.openedAt = at;
        }
        this.closedAt = at;
        this.updatedById = actorId;
    }

    void cancel(String reason, Long actorId) {
        this.status = AttendanceCheckpointStatus.CANCELLED;
        this.cancelReason = reason;
        this.updatedById = actorId;
    }

    boolean isPlanned() {
        return status == AttendanceCheckpointStatus.PLANNED;
    }

    boolean isOpen() {
        return status == AttendanceCheckpointStatus.OPEN;
    }

    boolean isCancelled() {
        return status == AttendanceCheckpointStatus.CANCELLED;
    }

    CourseSession getCourseSession() {
        return courseSession;
    }

    String getLabel() {
        return label;
    }

    AttendanceCheckpointType getCheckpointType() {
        return checkpointType;
    }

    int getDisplayOrder() {
        return displayOrder;
    }

    AttendanceCheckpointStatus getStatus() {
        return status;
    }

    boolean isRequired() {
        return required;
    }

    Instant getOpenedAt() {
        return openedAt;
    }

    Instant getClosedAt() {
        return closedAt;
    }

    String getCancelReason() {
        return cancelReason;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
