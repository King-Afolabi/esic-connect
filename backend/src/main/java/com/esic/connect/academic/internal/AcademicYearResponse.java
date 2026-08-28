package com.esic.connect.academic.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Vue API d'une année scolaire — jamais d'identifiant SQL interne. */
record AcademicYearResponse(
        UUID publicId,
        String code,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        AcademicStatus status,
        Instant archivedAt,
        String archiveReason,
        Instant createdAt,
        Instant updatedAt) {

    static AcademicYearResponse from(AcademicYear year) {
        return new AcademicYearResponse(
                year.getPublicId(),
                year.getCode(),
                year.getName(),
                year.getStartDate(),
                year.getEndDate(),
                year.getStatus(),
                year.getArchivedAt(),
                year.getArchiveReason(),
                year.getCreatedAt(),
                year.getUpdatedAt());
    }
}
