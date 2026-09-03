package com.esic.connect.enrollment.internal;

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
import java.time.LocalDate;

/**
 * Groupe temporaire d'apprenants (EF-ACA-007 ; docs/02 §6.5 ;
 * migration V19).
 *
 * <p>Rassemble des apprenants issus d'<strong>une ou plusieurs
 * classes</strong> pour une période — langues, options, projets. Il peut
 * être la cible d'un créneau de planning, mais ne remplace jamais la
 * classe principale de l'apprenant : celle-ci reste unique et active
 * (RG-022).
 *
 * <p><strong>Pourquoi ce module et pas {@code academic}.</strong> Un
 * membre de groupe est une <em>inscription</em>. Placer le groupe dans
 * {@code academic} créerait une dépendance {@code academic → enrollment},
 * alors qu'{@code enrollment → academic} existe déjà : un cycle entre
 * modules, que {@code ModularityTests} refuse à juste titre. Les
 * références vers la formation, l'année et la matière passent par les
 * ports publics {@code ClassGroupDirectory}, {@code AcademicScopeDirectory}
 * et {@code SubjectDirectory}.
 */
@Entity
@Table(name = "student_group")
@EntityListeners(AuditingEntityListener.class)
public class StudentGroup extends BaseEntity {

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

    @Column(name = "program_id", nullable = false)
    private Long programId;

    @Column(name = "subject_id")
    private Long subjectId;

    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StudentGroupStatus status;

    @Column(name = "archived_at")
    private Instant archivedAt;

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

    protected StudentGroup() {
        // JPA
    }

    public StudentGroup(String code, String name, Long academicYearId, Long programId,
                        Long subjectId, LocalDate startsOn, LocalDate endsOn, Long actorId) {
        this.code = code;
        this.name = name;
        this.academicYearId = academicYearId;
        this.programId = programId;
        this.subjectId = subjectId;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.status = StudentGroupStatus.ACTIVE;
        this.createdById = actorId;
        this.updatedById = actorId;
    }

    public void updateDetails(String name, Long subjectId, LocalDate startsOn, LocalDate endsOn,
                              Long actorId) {
        this.name = name;
        this.subjectId = subjectId;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.updatedById = actorId;
    }

    public void archive(Instant at, Long actorId) {
        this.status = StudentGroupStatus.ARCHIVED;
        this.archivedAt = at;
        this.updatedById = actorId;
    }

    public void restore(Long actorId) {
        this.status = StudentGroupStatus.ACTIVE;
        this.archivedAt = null;
        this.updatedById = actorId;
    }

    public boolean isArchived() {
        return status == StudentGroupStatus.ARCHIVED;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Long getAcademicYearId() {
        return academicYearId;
    }

    public Long getProgramId() {
        return programId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public StudentGroupStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
