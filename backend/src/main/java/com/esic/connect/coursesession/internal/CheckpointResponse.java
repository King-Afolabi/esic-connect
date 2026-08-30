package com.esic.connect.coursesession.internal;

import com.esic.connect.coursesession.AttendanceCheckpointStatus;
import com.esic.connect.coursesession.AttendanceCheckpointType;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue API d'un point de contrôle d'émargement (V10) — jamais
 * d'identifiant SQL interne.
 */
record CheckpointResponse(
        UUID publicId,
        String label,
        AttendanceCheckpointType type,
        AttendanceCheckpointStatus status,
        boolean required,
        int displayOrder,
        Instant openedAt,
        Instant closedAt,
        String cancelReason,
        Instant createdAt,
        Instant updatedAt) {

    static CheckpointResponse from(AttendanceCheckpoint cp) {
        return new CheckpointResponse(cp.getPublicId(), cp.getLabel(), cp.getCheckpointType(),
                cp.getStatus(), cp.isRequired(), cp.getDisplayOrder(), cp.getOpenedAt(), cp.getClosedAt(),
                cp.getCancelReason(), cp.getCreatedAt(), cp.getUpdatedAt());
    }
}
