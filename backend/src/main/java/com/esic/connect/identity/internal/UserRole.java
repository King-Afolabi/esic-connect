package com.esic.connect.identity.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Affectation d'un rôle à un utilisateur (docs/04-modele-donnees.md §10.3).
 *
 * L'unicité d'une affectation active par couple (utilisateur, rôle) est
 * garantie par la colonne générée {@code active_assignment_key} au niveau
 * de la migration SQL (non mappée ici) ; elle n'est donc pas dupliquée
 * dans cette entité. La cohérence temporelle complète des périodes reste
 * à valider par le service métier lors d'une étape future.
 */
@Entity
@Table(name = "user_role")
@EntityListeners(AuditingEntityListener.class)
public class UserRole extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "assigned_by_id")
    private Long assignedById;

    @Column(name = "assignment_reason")
    private String assignmentReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserRole() {
        // JPA
    }

    public UserRole(UserAccount user, Role role, Instant validFrom, boolean active) {
        this.user = user;
        this.role = role;
        this.validFrom = validFrom;
        this.active = active;
    }

    /** Trace l'auteur et le motif de l'affectation (docs/02 §30). */
    public void recordAssignment(Long assignedById, String assignmentReason) {
        this.assignedById = assignedById;
        this.assignmentReason = assignmentReason;
    }

    public UserAccount getUser() {
        return user;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }
}
