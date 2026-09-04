package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttendanceStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Une entrée de l'historique append-only d'une présence — jamais
 * d'identifiant SQL, jamais de commentaire libre au-delà de ce qui est
 * strictement utile à la lisibilité.
 *
 * <p>{@code actorRole} et {@code actorDisplayName} portent l'auteur exigé
 * par AC-018. {@code actorDisplayName} est {@code null} lorsque
 * l'historique est servi à l'apprenant : il a le droit de savoir quelle
 * fonction est intervenue sur sa présence, pas d'obtenir l'identité
 * civile d'un agent (docs/02 §14).
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
        String actorRole,
        String actorDisplayName,
        Instant occurredAt) {

    static AttendanceCorrectionResponse from(AttendanceCorrection c, String actorRole,
                                             String actorDisplayName) {
        return new AttendanceCorrectionResponse(c.getPublicId(), c.getAction().name(),
                c.getPreviousStatus(), c.getNewStatus(), c.getPreviousLateMinutes(), c.getNewLateMinutes(),
                c.getPreviousComment(), c.getNewComment(), c.getReason(),
                actorRole, actorDisplayName, c.getOccurredAt());
    }
}
