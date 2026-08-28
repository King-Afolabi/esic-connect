package com.esic.connect.organization.internal;

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
 * Bâtiment rattaché à un {@link Site} (docs/04-modele-donnees.md §9.2).
 * {@code code} unique dans le périmètre du site et immuable après
 * création ; le rattachement au site est lui aussi immuable.
 */
@Entity
@Table(name = "building")
@EntityListeners(AuditingEntityListener.class)
public class Building extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "site_id", nullable = false, updatable = false)
    private Site site;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrganizationStatus status;

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

    protected Building() {
        // JPA
    }

    public Building(Site site, String code, String name) {
        this.site = site;
        this.code = code;
        this.name = name;
        this.status = OrganizationStatus.ACTIVE;
    }

    public void rename(String name, Long actorId) {
        this.name = name;
        this.updatedById = actorId;
    }

    public void markCreatedBy(Long actorId) {
        this.createdById = actorId;
        this.updatedById = actorId;
    }

    public void archive(String reason, Long actorId, Instant at) {
        this.status = OrganizationStatus.ARCHIVED;
        this.archivedAt = at;
        this.archivedById = actorId;
        this.archiveReason = reason;
        this.updatedById = actorId;
    }

    public void restore(Long actorId) {
        this.status = OrganizationStatus.ACTIVE;
        this.archivedAt = null;
        this.archivedById = null;
        this.archiveReason = null;
        this.updatedById = actorId;
    }

    public boolean isArchived() {
        return status == OrganizationStatus.ARCHIVED;
    }

    public Site getSite() {
        return site;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public OrganizationStatus getStatus() {
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
