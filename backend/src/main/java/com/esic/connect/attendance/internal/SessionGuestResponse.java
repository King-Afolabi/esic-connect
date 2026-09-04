package com.esic.connect.attendance.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Entrée d'apprenant provisoire exposée par l'API (EF-ATT-007).
 *
 * <p>L'identité renvoyée est celle <strong>déclarée</strong> par le
 * formateur : l'écran doit la présenter comme telle, jamais comme un
 * apprenant identifié.
 */
record SessionGuestResponse(
        UUID publicId,
        UUID sessionPublicId,
        String firstName,
        String lastName,
        String email,
        String comment,
        String status,
        Instant recordedAt,
        Instant resolvedAt,
        String resolutionComment,
        UUID linkedEnrollmentPublicId,
        Instant createdAt) {
}
