package com.esic.connect.academic.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Vue API d'une promotion — références par identifiant public uniquement. */
record PromotionResponse(
        UUID publicId,
        UUID programPublicId,
        UUID academicYearPublicId,
        String code,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        AcademicStatus status,
        Instant archivedAt,
        String archiveReason,
        Instant createdAt,
        Instant updatedAt) {

    static PromotionResponse from(Promotion promotion) {
        return new PromotionResponse(
                promotion.getPublicId(),
                promotion.getProgram().getPublicId(),
                promotion.getAcademicYear().getPublicId(),
                promotion.getCode(),
                promotion.getName(),
                promotion.getStartDate(),
                promotion.getEndDate(),
                promotion.getStatus(),
                promotion.getArchivedAt(),
                promotion.getArchiveReason(),
                promotion.getCreatedAt(),
                promotion.getUpdatedAt());
    }
}
