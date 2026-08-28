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
 * Formation (docs/04-modele-donnees.md §12.2). {@code code} unique et
 * immuable après création ; archivage logique via {@link AcademicStatus}
 * (docs/04 §5.1). Le responsable pédagogique principal (RG-010) relève du
 * périmètre pédagogique, hors de ce lot.
 */
@Entity
@Table(name = "program")
@EntityListeners(AuditingEntityListener.class)
public class Program extends BaseEntity {

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "program_type", nullable = false)
    private ProgramType programType;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AcademicStatus status;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by_id")
    private Long archivedById;

    @Column(name = "archive_reason")
    private String archiveReason;

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

    protected Program() {
        // JPA
    }

    public Program(String code, String name, ProgramType programType, String description) {
        this.code = code;
        this.name = name;
        this.programType = programType;
        this.description = description;
        this.status = AcademicStatus.ACTIVE;
    }

    public void updateDetails(String name, ProgramType programType, String description, Long actorId) {
        this.name = name;
        this.programType = programType;
        this.description = description;
        this.updatedById = actorId;
    }

    public void markCreatedBy(Long actorId) {
        this.createdById = actorId;
        this.updatedById = actorId;
    }

    public void archive(String reason, Long actorId, Instant at) {
        this.status = AcademicStatus.ARCHIVED;
        this.archivedAt = at;
        this.archivedById = actorId;
        this.archiveReason = reason;
        this.updatedById = actorId;
    }

    public void restore(Long actorId) {
        this.status = AcademicStatus.ACTIVE;
        this.archivedAt = null;
        this.archivedById = null;
        this.archiveReason = null;
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

    public ProgramType getProgramType() {
        return programType;
    }

    public String getDescription() {
        return description;
    }

    public AcademicStatus getStatus() {
        return status;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public String getArchiveReason() {
        return archiveReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
