package com.esic.connect.organization.internal;

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
 * Site ou campus ESIC (docs/04-modele-donnees.md §9.1).
 *
 * <p>Entité archivable : {@code code} immuable après création,
 * {@link OrganizationStatus} pour l'archivage logique (aucune suppression
 * physique). Les colonnes auteur ({@code *_by_id}) sont renseignées
 * manuellement par le service ({@code JpaAuditingConfig} n'expose pas
 * d'{@code AuditorAware}).
 */
@Entity
@Table(name = "site")
@EntityListeners(AuditingEntityListener.class)
public class Site extends BaseEntity {

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "city")
    private String city;

    // Colonne SQL CHAR(2) (ISO 3166-1 alpha-2, docs/04 §9.1) : le type
    // JDBC doit être explicite, sinon Hibernate attend un VARCHAR et la
    // validation de schéma échoue.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "time_zone_id", nullable = false)
    private String timeZoneId;

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

    protected Site() {
        // JPA
    }

    public Site(String code, String name, String timeZoneId) {
        this.code = code;
        this.name = name;
        this.timeZoneId = timeZoneId;
        this.status = OrganizationStatus.ACTIVE;
    }

    /** Applique les champs modifiables (le code n'en fait jamais partie). */
    public void updateDetails(String name, String addressLine1, String addressLine2, String postalCode,
                              String city, String countryCode, String timeZoneId, Long actorId) {
        this.name = name;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.postalCode = postalCode;
        this.city = city;
        this.countryCode = countryCode;
        this.timeZoneId = timeZoneId;
        this.updatedById = actorId;
    }

    public void markCreatedBy(Long actorId) {
        this.createdById = actorId;
        this.updatedById = actorId;
    }

    /** Archivage logique : aucune suppression, historique conservé (cahier §9.7 esprit). */
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

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCity() {
        return city;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getTimeZoneId() {
        return timeZoneId;
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
