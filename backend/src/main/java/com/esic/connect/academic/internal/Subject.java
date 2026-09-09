package com.esic.connect.academic.internal;

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
 * Matière (EF-ACA-006 ; docs/02-cahier-des-charges.md §6.4 ; migration V19).
 *
 * <p><strong>Aucun formateur ici.</strong> Le cahier est explicite :
 * « une matière n'a pas de formateur unique global ; l'affectation se
 * fait au niveau de la séance, d'une période, ou d'une association
 * classe–matière–période ». Ajouter une colonne {@code teacher_id}
 * reviendrait à contredire cette règle et à casser les remplacements.
 *
 * <p>Le volume horaire est <strong>indicatif</strong> : il sert au
 * cadrage pédagogique, jamais au calcul d'assiduité, qui se fonde sur les
 * séances réellement attendues (docs/02 §8.3).
 */
@Entity
@Table(name = "subject")
@EntityListeners(AuditingEntityListener.class)
public class Subject extends BaseEntity {

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "hourly_volume")
    private Integer hourlyVolume;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AcademicStatus status;

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

    protected Subject() {
        // JPA
    }

    public Subject(String code, String name, String description, Integer hourlyVolume) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.hourlyVolume = hourlyVolume;
        this.status = AcademicStatus.ACTIVE;
    }

    public void markCreatedBy(Long actorId) {
        this.createdById = actorId;
        this.updatedById = actorId;
    }

    public void updateDetails(String name, String description, Integer hourlyVolume, Long actorId) {
        this.name = name;
        this.description = description;
        this.hourlyVolume = hourlyVolume;
        this.updatedById = actorId;
    }

    public void archive(Instant at, Long actorId) {
        this.status = AcademicStatus.ARCHIVED;
        this.archivedAt = at;
        this.updatedById = actorId;
    }

    public void restore(Long actorId) {
        this.status = AcademicStatus.ACTIVE;
        this.archivedAt = null;
        this.updatedById = actorId;
    }

    public boolean isArchived() {
        return status == AcademicStatus.ARCHIVED;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Integer getHourlyVolume() {
        return hourlyVolume;
    }

    public AcademicStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
