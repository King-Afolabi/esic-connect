package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttendanceStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Confirmation d'un émargement — jamais d'identifiant SQL, jamais le
 * jeton ou le code court soumis.
 *
 * @param attendancePublicId identifiant public de la présence
 * @param sessionPublicId    séance concernée
 * @param checkpointPublicId point de contrôle concerné (V10)
 * @param sessionTitle       libellé de la séance ({@code null} possible)
 * @param status             {@code PRESENT} ou {@code LATE} (calculé serveur)
 * @param lateMinutes        minutes de retard si {@code LATE}, {@code null} sinon
 * @param manualValidationRequired retard au-delà du second palier
 *                                 (docs/02 §16.4 ; RG-072) : la présence
 *                                 est enregistrée, mais elle demande une
 *                                 confirmation humaine. Refuser
 *                                 l'émargement produirait une absence là
 *                                 où il y a un retard constaté.
 * @param recordedAt         instant d'enregistrement (horloge serveur)
 * @param source             canal utilisé
 */
record AttendanceRecordResponse(
        UUID attendancePublicId,
        UUID sessionPublicId,
        UUID checkpointPublicId,
        String sessionTitle,
        AttendanceStatus status,
        Integer lateMinutes,
        boolean manualValidationRequired,
        Instant recordedAt,
        AttendanceRecordSource source) {
}
