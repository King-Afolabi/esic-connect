package com.esic.connect.organization.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Plage réseau autorisée pour un site (docs/04-modele-donnees.md §9.4,
 * cahier §17.9). Définie et consultée uniquement par le {@code SUPER_ADMIN}.
 *
 * <p>Modèle « ajout + désactivation » : jamais de suppression physique,
 * {@code cidr} et rattachement au site immuables. L'adresse IP d'un
 * utilisateur n'est jamais stockée ici.
 */
@Entity
@Table(name = "site_network_range")
@EntityListeners(AuditingEntityListener.class)
public class SiteNetworkRange extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "site_id", nullable = false, updatable = false)
    private Site site;

    @Column(name = "cidr", nullable = false, updatable = false)
    private String cidr;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_id")
    private Long createdById;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SiteNetworkRange() {
        // JPA
    }

    public SiteNetworkRange(Site site, String cidr, String label, Instant validFrom) {
        this.site = site;
        this.cidr = cidr;
        this.label = label;
        this.validFrom = validFrom;
        this.active = true;
    }

    public void markCreatedBy(Long actorId) {
        this.createdById = actorId;
    }

    /** Désactive la plage sans la supprimer (historique conservé). */
    public void deactivate(Instant at) {
        this.active = false;
        this.validUntil = at;
    }

    public void reactivate(Instant at) {
        this.active = true;
        this.validFrom = at;
        this.validUntil = null;
    }

    public boolean isActive() {
        return active;
    }

    public Site getSite() {
        return site;
    }

    public String getCidr() {
        return cidr;
    }

    public String getLabel() {
        return label;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
