package com.esic.connect.attendance.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Confirmation d'un émargement — jamais d'identifiant SQL, jamais le
 * jeton ou le code court soumis.
 *
 * @param attendancePublicId identifiant public de la présence
 * @param sessionPublicId    séance concernée
 * @param sessionTitle       libellé de la séance ({@code null} possible)
 * @param recordedAt         instant d'enregistrement (horloge serveur)
 * @param source             canal utilisé (DYNAMIC_QR ou SHORT_CODE)
 */
record AttendanceRecordResponse(
        UUID attendancePublicId,
        UUID sessionPublicId,
        String sessionTitle,
        Instant recordedAt,
        AttendanceRecordSource source) {
}
