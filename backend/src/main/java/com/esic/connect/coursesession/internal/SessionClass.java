package com.esic.connect.coursesession.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Rattachement d'une classe à une séance (table de jointure V9).
 *
 * <p>{@code classGroupId} est une valeur technique (clé étrangère SQL
 * vers {@code class_group}) : aucune relation JPA vers {@code academic},
 * la résolution du code / de l'identifiant public passe par
 * {@link com.esic.connect.academic.ClassGroupDirectory}. Unicité
 * {@code (course_session_id, class_group_id)} garantie par la migration.
 */
@Entity
@Table(name = "session_class")
@EntityListeners(AuditingEntityListener.class)
class SessionClass extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_session_id", nullable = false)
    private CourseSession courseSession;

    @Column(name = "class_group_id", nullable = false)
    private Long classGroupId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SessionClass() {
        // JPA
    }

    SessionClass(CourseSession courseSession, Long classGroupId) {
        this.courseSession = courseSession;
        this.classGroupId = classGroupId;
    }

    Long getClassGroupId() {
        return classGroupId;
    }
}
