package com.esic.connect.claim.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Message d'un fil de réclamation (EF-CLAIM-002).
 *
 * <p><strong>Append-only</strong> : ni modification, ni suppression. Une
 * réclamation « conserve son historique complet » (RG-088) — un message
 * effacé rendrait la suite du fil incompréhensible.
 *
 * <p>{@code authorRole} enregistre le rôle <em>employé au moment du
 * message</em>, jamais le rôle courant de l'auteur : relire un fil deux
 * ans plus tard doit montrer qui parlait en quelle qualité.
 */
@Entity
@Table(name = "claim_message")
@EntityListeners(AuditingEntityListener.class)
class ClaimMessage extends BaseEntity {

    @Column(name = "claim_id", nullable = false, updatable = false)
    private Long claimId;

    @Column(name = "author_user_id", nullable = false, updatable = false)
    private Long authorUserId;

    @Column(name = "author_role", nullable = false, length = 32, updatable = false)
    private String authorRole;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String body;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClaimMessage() {
        // JPA
    }

    ClaimMessage(Long claimId, Long authorUserId, String authorRole, String body) {
        this.claimId = claimId;
        this.authorUserId = authorUserId;
        this.authorRole = authorRole;
        this.body = body;
    }

    Long getAuthorUserId() {
        return authorUserId;
    }

    String getAuthorRole() {
        return authorRole;
    }

    String getBody() {
        return body;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
