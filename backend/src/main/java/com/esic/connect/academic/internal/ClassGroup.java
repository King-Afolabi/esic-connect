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
 * Classe ou groupe principal (docs/04-modele-donnees.md §12.5). Rattachée
 * à une {@link Promotion}, à un {@link ProgramLevel} de la même formation
 * que la promotion, et à un site.
 *
 * <p>Le site est stocké en <b>valeur technique</b> ({@link #siteId}) : le
 * module {@code academic} n'importe jamais {@code organization.internal} et
 * ne partage pas d'entité JPA inter-module (décision D4). La cohérence est
 * garantie par la clé étrangère SQL {@code fk_class_group_site} et par le
 * port {@code organization.SiteDirectory} côté service.
 *
 * <p>{@code code} unique dans la promotion et immuable ; les rattachements
 * (promotion, niveau, site) sont immuables. Une classe archivée reste
 * référençable par les inscriptions et séances (§12.5) : aucune
 * suppression physique.
 */
@Entity
@Table(name = "class_group")
@EntityListeners(AuditingEntityListener.class)
public class ClassGroup extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false, updatable = false)
    private Promotion promotion;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "program_level_id", nullable = false, updatable = false)
    private ProgramLevel programLevel;

    @Column(name = "site_id", nullable = false, updatable = false)
    private Long siteId;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "capacity")
    private Integer capacity;

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

    protected ClassGroup() {
        // JPA
    }

    public ClassGroup(Promotion promotion, ProgramLevel programLevel, Long siteId, String code,
                      String name, Integer capacity) {
        this.promotion = promotion;
        this.programLevel = programLevel;
        this.siteId = siteId;
        this.code = code;
        this.name = name;
        this.capacity = capacity;
        this.status = AcademicStatus.ACTIVE;
    }

    public void updateDetails(String name, Integer capacity, Long actorId) {
        this.name = name;
        this.capacity = capacity;
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

    public Promotion getPromotion() {
        return promotion;
    }

    public ProgramLevel getProgramLevel() {
        return programLevel;
    }

    public Long getSiteId() {
        return siteId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Integer getCapacity() {
        return capacity;
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
