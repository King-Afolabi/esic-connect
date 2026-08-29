package com.esic.connect.alternation.internal;

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
import java.time.LocalDate;

/**
 * Affectation historisée d'un rythme à une classe (docs/04 §14.2).
 *
 * <p>{@code pattern} est une relation intra-module. {@code classGroupId}
 * est une simple valeur technique (clé étrangère SQL vers
 * {@code class_group}), résolue via le port
 * {@link com.esic.connect.academic.ClassGroupDirectory}.
 *
 * <p>{@code cycleStartDate} est l'ancre du cycle (jour de la semaine 1 du
 * rythme). Rattachements, {@code cycleStartDate} et {@code validFrom} sont
 * immuables ; seule la clôture ({@link #close}) fait évoluer l'entité —
 * elle renseigne {@code validUntil} (borne inclusive) et libère le
 * créneau de l'affectation « ouverte » courante. Aucune suppression
 * physique.
 */
@Entity
@Table(name = "class_work_study_pattern")
@EntityListeners(AuditingEntityListener.class)
class ClassWorkStudyPattern extends BaseEntity {

    @Column(name = "class_group_id", nullable = false, updatable = false)
    private Long classGroupId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "work_study_pattern_id", nullable = false, updatable = false)
    private WorkStudyPattern pattern;

    @Column(name = "cycle_start_date", nullable = false, updatable = false)
    private LocalDate cycleStartDate;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClassPatternStatus status;

    @Column(name = "close_reason")
    private String closeReason;

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

    protected ClassWorkStudyPattern() {
        // JPA
    }

    ClassWorkStudyPattern(Long classGroupId, WorkStudyPattern pattern, LocalDate cycleStartDate,
                          LocalDate validFrom, LocalDate validUntil) {
        this.classGroupId = classGroupId;
        this.pattern = pattern;
        this.cycleStartDate = cycleStartDate;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.status = ClassPatternStatus.ACTIVE;
    }

    void markCreatedBy(Long actorId) {
        this.createdById = actorId;
        this.updatedById = actorId;
    }

    /**
     * Clôture explicite : quitte le statut {@code ACTIVE}, fixe la fin de
     * validité (inclusive, ≥ {@code validFrom} — contrôlé par le service)
     * et libère le créneau de l'affectation ouverte (colonne générée
     * {@code active_open_key}).
     */
    void close(String reason, Long actorId, LocalDate effectiveDate) {
        this.status = ClassPatternStatus.CLOSED;
        this.validUntil = effectiveDate;
        this.closeReason = reason;
        this.updatedById = actorId;
    }

    boolean isClosed() {
        return status == ClassPatternStatus.CLOSED;
    }

    boolean isActive() {
        return status == ClassPatternStatus.ACTIVE;
    }

    Long getClassGroupId() {
        return classGroupId;
    }

    WorkStudyPattern getPattern() {
        return pattern;
    }

    LocalDate getCycleStartDate() {
        return cycleStartDate;
    }

    LocalDate getValidFrom() {
        return validFrom;
    }

    LocalDate getValidUntil() {
        return validUntil;
    }

    ClassPatternStatus getStatus() {
        return status;
    }

    String getCloseReason() {
        return closeReason;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
