package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttendanceStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Une entrée de l'historique append-only d'une présence — jamais
 * d'identifiant SQL, jamais de commentaire libre au-delà de ce qui est
 * strictement utile à la lisibilité.
 */
record AttendanceCorrectionResponse(
        UUID publicId,
        String action,
        AttendanceStatus previousStatus,
        AttendanceStatus newStatus,
        Integer previousLateMinutes,
        Integer newLateMinutes,
        String previousComment,
        String newComment,
        String reason,
        Instant occurredAt) {

    static AttendanceCorrectionResponse from(AttendanceCorrection c) {
        return new AttendanceCorrectionResponse(c.getPublicId(), c.getAction().name(),
                c.getPreviousStatus(), c.getNewStatus(), c.getPreviousLateMinutes(), c.getNewLateMinutes(),
                c.getPreviousComment(), c.getNewComment(), c.getReason(), c.getOccurredAt());
    }
}
