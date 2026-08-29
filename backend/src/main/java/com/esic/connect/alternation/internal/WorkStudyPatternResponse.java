package com.esic.connect.alternation.internal;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue API d'un modèle de rythme — jamais d'identifiant SQL interne.
 * {@code configuration} est renvoyée sous sa forme canonique normalisée
 * (semaines et jours triés) telle que stockée après validation.
 */
record WorkStudyPatternResponse(
        UUID publicId,
        String code,
        String name,
        String description,
        WorkStudyPatternType type,
        Integer cycleLengthWeeks,
        JsonNode configuration,
        WorkStudyPatternStatus status,
        String archiveReason,
        Instant createdAt,
        Instant updatedAt) {

    static WorkStudyPatternResponse from(WorkStudyPattern pattern, JsonNode configuration) {
        return new WorkStudyPatternResponse(
                pattern.getPublicId(),
                pattern.getCode(),
                pattern.getName(),
                pattern.getDescription(),
                pattern.getPatternType(),
                pattern.getCycleLengthWeeks(),
                configuration,
                pattern.getStatus(),
                pattern.getArchiveReason(),
                pattern.getCreatedAt(),
                pattern.getUpdatedAt());
    }
}
