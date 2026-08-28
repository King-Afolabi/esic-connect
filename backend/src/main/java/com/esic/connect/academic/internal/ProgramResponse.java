package com.esic.connect.academic.internal;

import java.time.Instant;
import java.util.UUID;

/** Vue API d'une formation — jamais d'identifiant SQL interne. */
record ProgramResponse(
        UUID publicId,
        String code,
        String name,
        ProgramType programType,
        String description,
        AcademicStatus status,
        Instant archivedAt,
        String archiveReason,
        Instant createdAt,
        Instant updatedAt) {

    static ProgramResponse from(Program program) {
        return new ProgramResponse(
                program.getPublicId(),
                program.getCode(),
                program.getName(),
                program.getProgramType(),
                program.getDescription(),
                program.getStatus(),
                program.getArchivedAt(),
                program.getArchiveReason(),
                program.getCreatedAt(),
                program.getUpdatedAt());
    }
}
