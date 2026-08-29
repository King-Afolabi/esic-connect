package com.esic.connect.alternation.internal;

import com.esic.connect.academic.ClassGroupDirectory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Vue API d'une affectation de rythme à une classe — jamais
 * d'identifiant SQL interne (ni {@code id}, ni {@code classGroupId}, ni
 * {@code workStudyPatternId}). La classe est exposée par ses identifiants
 * publics et son code (résolus via {@link ClassGroupDirectory}).
 */
record ClassAssignmentResponse(
        UUID publicId,
        UUID classGroupPublicId,
        String classGroupCode,
        UUID workStudyPatternPublicId,
        String workStudyPatternCode,
        WorkStudyPatternType workStudyPatternType,
        LocalDate cycleStartDate,
        LocalDate validFrom,
        LocalDate validUntil,
        ClassPatternStatus status,
        String closeReason,
        Instant createdAt,
        Instant updatedAt) {

    static ClassAssignmentResponse from(ClassWorkStudyPattern assignment, ClassGroupDirectory.ClassGroupRef classRef) {
        return new ClassAssignmentResponse(
                assignment.getPublicId(),
                classRef != null ? classRef.publicId() : null,
                classRef != null ? classRef.code() : null,
                assignment.getPattern().getPublicId(),
                assignment.getPattern().getCode(),
                assignment.getPattern().getPatternType(),
                assignment.getCycleStartDate(),
                assignment.getValidFrom(),
                assignment.getValidUntil(),
                assignment.getStatus(),
                assignment.getCloseReason(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt());
    }
}
