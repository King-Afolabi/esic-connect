package com.esic.connect.alternation.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Modèle réutilisable de rythme d'alternance (docs/04 §14.1).
 *
 * <p>{@code code} unique et immuable après création ; {@code patternType}
 * immuable. {@code configurationJson} est stocké sous forme canonique
 * (réécrite par {@link AlternationConfigParser}) ; sa validité est
 * garantie à l'écriture. Archivage logique via
 * {@link WorkStudyPatternStatus} — aucune suppression physique.
 */
@Entity
@Table(name = "work_study_pattern")
@EntityListeners(AuditingEntityListener.class)
class WorkStudyPattern extends BaseEntity {

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "pattern_type", nullable = false, updatable = false)
    private WorkStudyPatternType patternType;

    @Column(name = "cycle_length_weeks")
    private Integer cycleLengthWeeks;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration_json", nullable = false)
    private String configurationJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WorkStudyPatternStatus status;

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

    protected WorkStudyPattern() {
        // JPA
    }

    WorkStudyPattern(String code, String name, String description, WorkStudyPatternType patternType,
                     Integer cycleLengthWeeks, String configurationJson) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.patternType = patternType;
        this.cycleLengthWeeks = cycleLengthWeeks;
        this.configurationJson = configurationJson;
        this.status = WorkStudyPatternStatus.ACTIVE;
    }

    void markCreatedBy(Long actorId) {
        this.createdById = actorId;
        this.updatedById = actorId;
    }

    /** Met à jour le nom, la description et la configuration (le type et le code restent figés). */
    void updateDetails(String name, String description, Integer cycleLengthWeeks, String configurationJson,
                       Long actorId) {
        this.name = name;
        this.description = description;
        this.cycleLengthWeeks = cycleLengthWeeks;
        this.configurationJson = configurationJson;
        this.updatedById = actorId;
    }

    void archive(String reason, Long actorId, Instant at) {
        this.status = WorkStudyPatternStatus.ARCHIVED;
        this.archivedAt = at;
        this.archivedById = actorId;
        this.archiveReason = reason;
        this.updatedById = actorId;
    }

    void restore(Long actorId) {
        this.status = WorkStudyPatternStatus.ACTIVE;
        this.archivedAt = null;
        this.archivedById = null;
        this.archiveReason = null;
        this.updatedById = actorId;
    }

    boolean isArchived() {
        return status == WorkStudyPatternStatus.ARCHIVED;
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }

    String getDescription() {
        return description;
    }

    WorkStudyPatternType getPatternType() {
        return patternType;
    }

    Integer getCycleLengthWeeks() {
        return cycleLengthWeeks;
    }

    String getConfigurationJson() {
        return configurationJson;
    }

    WorkStudyPatternStatus getStatus() {
        return status;
    }

    Instant getArchivedAt() {
        return archivedAt;
    }

    String getArchiveReason() {
        return archiveReason;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
