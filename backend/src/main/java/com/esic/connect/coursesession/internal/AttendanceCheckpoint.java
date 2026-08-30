package com.esic.connect.coursesession.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Point de contrôle d'émargement unique d'une séance (V9).
 *
 * <p>Créé en même temps que la séance ; ouvert et fermé avec elle. Aucun
 * statut propre : l'état vient de {@code openedAt} / {@code closedAt}.
 * {@code attendance_record.attendance_checkpoint_id} référence la clé
 * primaire de cette entité (valeur technique, module {@code attendance}).
 */
@Entity
@Table(name = "attendance_checkpoint")
@EntityListeners(AuditingEntityListener.class)
class AttendanceCheckpoint extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_session_id", nullable = false, unique = true)
    private CourseSession courseSession;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AttendanceCheckpoint() {
        // JPA
    }

    AttendanceCheckpoint(CourseSession courseSession) {
        this.courseSession = courseSession;
    }

    void open(Instant at) {
        this.openedAt = at;
    }

    void close(Instant at) {
        this.closedAt = at;
    }

    boolean isOpen() {
        return openedAt != null && closedAt == null;
    }

    CourseSession getCourseSession() {
        return courseSession;
    }
}
