package com.esic.connect.audit.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement d'audit (docs/04-modele-donnees.md §24).
 *
 * {@code actorUserId} est un identifiant technique simple, sans relation
 * JPA vers {@code identity.internal.UserAccount} : le module audit ne doit
 * pas dépendre des classes internes du module identity (docs/03 §6.6,
 * vérifié par Spring Modulith). Les colonnes "snapshot" figent les
 * informations nécessaires à la lisibilité de l'audit même après
 * suppression du compte (FK SQL {@code ON DELETE SET NULL}).
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent extends BaseEntity {

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_public_id_snapshot")
    private UUID actorPublicIdSnapshot;

    @Column(name = "actor_display_snapshot")
    private String actorDisplaySnapshot;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_public_id")
    private UUID resourcePublicId;

    @Column(name = "result", nullable = false)
    private String result;

    @Column(name = "reason")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values_json")
    private String oldValuesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values_json")
    private String newValuesJson;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json")
    private String metadataJson;

    protected AuditEvent() {
        // JPA
    }

    public AuditEvent(Instant occurredAt, Long actorUserId, String action, String category,
                       String resourceType, String result) {
        this.occurredAt = occurredAt;
        this.actorUserId = actorUserId;
        this.action = action;
        this.category = category;
        this.resourceType = resourceType;
        this.result = result;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public void setActorDisplaySnapshot(String actorDisplaySnapshot) {
        this.actorDisplaySnapshot = actorDisplaySnapshot;
    }

    public String getActorDisplaySnapshot() {
        return actorDisplaySnapshot;
    }

    public String getAction() {
        return action;
    }

    public String getResult() {
        return result;
    }
}
