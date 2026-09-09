package com.esic.connect.claim.internal;

import com.esic.connect.claim.ClaimAudience;
import com.esic.connect.claim.ClaimCategory;
import com.esic.connect.claim.ClaimStatus;
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
import java.time.LocalDate;

/**
 * Réclamation (EF-CLAIM-001 ; docs/02 §20).
 *
 * <p>Adressée à un <strong>guichet</strong> ({@link ClaimAudience}) et non
 * à une personne : le transfert d'un guichet à l'autre est prévu par le
 * cahier, et nommer un destinataire le rendrait impossible dès qu'il est
 * absent.
 *
 * <p>Aucune relation JPA vers un autre module : les colonnes de séance et
 * de classe sont des clés étrangères SQL, résolues par les ports publics.
 */
@Entity
@Table(name = "claim")
@EntityListeners(AuditingEntityListener.class)
class Claim extends BaseEntity {

    @Column(name = "author_user_id", nullable = false, updatable = false)
    private Long authorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private ClaimCategory category;

    @Column(name = "subject", nullable = false, length = 191)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClaimStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false)
    private ClaimAudience audience;

    @Column(name = "course_session_id", updatable = false)
    private Long courseSessionId;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "class_group_id", updatable = false)
    private Long classGroupId;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by_id")
    private Long closedById;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Claim() {
        // JPA
    }

    Claim(Long authorUserId, ClaimCategory category, String subject, ClaimAudience audience,
          Long courseSessionId, LocalDate periodStart, LocalDate periodEnd, Long classGroupId) {
        this.authorUserId = authorUserId;
        this.category = category;
        this.subject = subject;
        this.audience = audience;
        this.courseSessionId = courseSessionId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.classGroupId = classGroupId;
        this.status = ClaimStatus.OPEN;
    }

    void transferTo(ClaimAudience target) {
        this.audience = target;
        this.status = ClaimStatus.TRANSFERRED;
    }

    void changeStatus(ClaimStatus target, Long actorId, Instant at) {
        this.status = target;
        if (target.isClosed()) {
            this.closedAt = at;
            this.closedById = actorId;
        } else {
            // Une réouverture efface la clôture : garder la date laisserait
            // croire que la réclamation est close alors qu'elle est active.
            this.closedAt = null;
            this.closedById = null;
        }
    }

    Long getAuthorUserId() {
        return authorUserId;
    }

    ClaimCategory getCategory() {
        return category;
    }

    String getSubject() {
        return subject;
    }

    ClaimStatus getStatus() {
        return status;
    }

    ClaimAudience getAudience() {
        return audience;
    }

    Long getCourseSessionId() {
        return courseSessionId;
    }

    LocalDate getPeriodStart() {
        return periodStart;
    }

    LocalDate getPeriodEnd() {
        return periodEnd;
    }

    Long getClassGroupId() {
        return classGroupId;
    }

    Instant getClosedAt() {
        return closedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
