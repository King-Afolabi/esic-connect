package com.esic.connect.academic.internal;

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
 * Niveau au sein d'une {@link Program} (docs/04-modele-donnees.md §12.3),
 * par exemple « BTS 1 », « Master 2 ». {@code code} unique dans le
 * périmètre de la formation et immuable ; le rattachement à la formation
 * est lui aussi immuable.
 */
@Entity
@Table(name = "program_level")
@EntityListeners(AuditingEntityListener.class)
public class ProgramLevel extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "program_id", nullable = false, updatable = false)
    private Program program;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sequence_number", nullable = false)
    private short sequenceNumber;

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

    protected ProgramLevel() {
        // JPA
    }

    public ProgramLevel(Program program, String code, String name, short sequenceNumber) {
        this.program = program;
        this.code = code;
        this.name = name;
        this.sequenceNumber = sequenceNumber;
        this.status = AcademicStatus.ACTIVE;
    }

    public void updateDetails(String name, short sequenceNumber, Long actorId) {
        this.name = name;
        this.sequenceNumber = sequenceNumber;
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

    public Program getProgram() {
        return program;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public short getSequenceNumber() {
        return sequenceNumber;
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
