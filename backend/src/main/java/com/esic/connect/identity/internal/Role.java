package com.esic.connect.identity.internal;

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

/** Rôle système (docs/04-modele-donnees.md §10.2). */
@Entity
@Table(name = "role")
@EntityListeners(AuditingEntityListener.class)
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false)
    private RoleCode code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @Column(name = "active", nullable = false)
    private boolean active;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Role() {
        // JPA
    }

    public Role(RoleCode code, String name, String description, boolean systemRole, boolean active) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.systemRole = systemRole;
        this.active = active;
    }

    public RoleCode getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
