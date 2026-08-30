package com.esic.connect.attendance.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Réponse d'émission d'un jeton d'émargement. {@code token} et
 * {@code shortCode} transitent uniquement dans ce corps HTTPS ; l'écran
 * formateur ne doit ni les journaliser, ni les stocker, ni les placer
 * dans une URL.
 *
 * @param token              jeton opaque à encoder dans le QR
 * @param shortCode          code court équivalent
 * @param expiresAt          instant d'expiration
 * @param sessionPublicId    séance concernée
 * @param checkpointPublicId point de contrôle concerné (V10)
 * @param ttlSeconds         durée de vie en secondes (compte à rebours client)
 */
record AttendanceTokenResponse(
        String token,
        String shortCode,
        Instant expiresAt,
        UUID sessionPublicId,
        UUID checkpointPublicId,
        long ttlSeconds) {

    static AttendanceTokenResponse from(IssuedAttendanceToken issued, long ttlSeconds) {
        return new AttendanceTokenResponse(issued.token(), issued.shortCode(), issued.expiresAt(),
                issued.sessionPublicId(), issued.checkpointPublicId(), ttlSeconds);
    }
}
