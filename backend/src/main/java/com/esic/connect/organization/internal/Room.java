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
 * Salle rattachée à un {@link Site} et, éventuellement, à un
 * {@link Building} (docs/04-modele-donnees.md §9.3).
 *
 * <p>{@code code} unique par site et immuable ; le rattachement au site
 * est immuable. Le bâtiment est optionnel mais, s'il est renseigné, il
 * doit appartenir au même site (contrôle assuré par le service). Une
 * salle archivée reste référencée par les anciennes séances : jamais de
 * suppression physique.
 */
@Entity
@Table(name = "room")
@EntityListeners(AuditingEntityListener.class)
public class Room extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "site_id", nullable = false, updatable = false)
    private Site site;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "building_id")
    private Building building;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "floor_label")
    private String floorLabel;

    @Column(name = "static_qr_reference")
    private String staticQrReference;

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

    protected Room() {
        // JPA
    }

    public Room(Site site, Building building, String code, String name, Integer capacity,
                String floorLabel, String staticQrReference) {
        this.site = site;
        this.building = building;
        this.code = code;
        this.name = name;
        this.capacity = capacity;
        this.floorLabel = floorLabel;
        this.staticQrReference = staticQrReference;
        this.status = OrganizationStatus.ACTIVE;
    }

    /**
     * Remplace les champs modifiables. Le {@code building} fourni (ou
     * {@code null}) devient le rattachement courant ; sa cohérence avec le
     * site est vérifiée en amont par le service.
     */
    public void updateDetails(String name, Integer capacity, String floorLabel, String staticQrReference,
                              Building building, Long actorId) {
        this.name = name;
        this.capacity = capacity;
        this.floorLabel = floorLabel;
        this.staticQrReference = staticQrReference;
        this.building = building;
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

    public Building getBuilding() {
        return building;
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

    public String getFloorLabel() {
        return floorLabel;
    }

    public String getStaticQrReference() {
        return staticQrReference;
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
