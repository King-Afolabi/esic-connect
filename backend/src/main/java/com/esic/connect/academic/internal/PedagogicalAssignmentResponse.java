package com.esic.connect.academic.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Vue API d'une affectation de responsable pédagogique — jamais
 * d'identifiant SQL interne (ni {@code id}, ni {@code programId}, ni
 * {@code managerUserId}, ni {@code delegatedById}).
 */
record PedagogicalAssignmentResponse(
        UUID publicId,
        UUID programPublicId,
        String programCode,
        UUID userPublicId,
        PedagogicalAssignmentRole type,
        PedagogicalAssignmentStatus status,
        LocalDate validFrom,
        LocalDate validUntil,
        String reason,
        String closeReason,
        Instant createdAt,
        Instant updatedAt) {

    static PedagogicalAssignmentResponse from(PedagogicalAssignment assignment, UUID userPublicId) {
        return new PedagogicalAssignmentResponse(
                assignment.getPublicId(),
                assignment.getProgram().getPublicId(),
                assignment.getProgram().getCode(),
                userPublicId,
                assignment.getAssignmentRole(),
                assignment.getStatus(),
                assignment.getValidFrom(),
                assignment.getValidUntil(),
                assignment.getReason(),
                assignment.getCloseReason(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt());
    }
}
