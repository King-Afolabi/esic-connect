package com.esic.connect.academic.internal;

import java.time.Instant;
import java.util.UUID;

/** Vue API d'un niveau — références par identifiant public uniquement. */
record ProgramLevelResponse(
        UUID publicId,
        UUID programPublicId,
        String code,
        String name,
        short sequenceNumber,
        AcademicStatus status,
        Instant archivedAt,
        String archiveReason,
        Instant createdAt,
        Instant updatedAt) {

    static ProgramLevelResponse from(ProgramLevel level) {
        return new ProgramLevelResponse(
                level.getPublicId(),
                level.getProgram().getPublicId(),
                level.getCode(),
                level.getName(),
                level.getSequenceNumber(),
                level.getStatus(),
                level.getArchivedAt(),
                level.getArchiveReason(),
                level.getCreatedAt(),
                level.getUpdatedAt());
    }
}
