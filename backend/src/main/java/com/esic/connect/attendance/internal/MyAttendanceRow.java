package com.esic.connect.attendance.internal;

import com.esic.connect.coursesession.AttendanceCheckpointType;

import java.time.Instant;
import java.util.UUID;

/**
 * Une ligne de l'espace « Mes présences » (V10) : un point de contrôle
 * attendu d'une séance d'une classe de l'apprenant, avec sa présence
 * réelle si elle existe, ou une absence <em>dérivée</em>
 * ({@code attendancePublicId == null}) pour un point de contrôle fermé
 * sans émargement. Jamais d'identifiant SQL, jamais d'adresse
 * électronique.
 *
 * @param attendancePublicId   présence réelle, {@code null} si dérivée
 * @param status               {@code PRESENT} / {@code LATE} / {@code ABSENT} /
 *                             {@code EXCUSED_ABSENCE} / {@code CANCELLED} (réel) ;
 *                             {@code ABSENT} (dérivé, point de contrôle fermé) ;
 *                             {@code OPEN} / {@code PLANNED} (point de contrôle
 *                             pas encore fermé, aucun émargement)
 * @param canJustify           {@code true} si l'apprenant peut déposer un
 *                             justificatif pour ce point de contrôle
 */
record MyAttendanceRow(
        UUID attendancePublicId,
        UUID sessionPublicId,
        String sessionTitle,
        Instant sessionStartsAt,
        UUID checkpointPublicId,
        String checkpointLabel,
        AttendanceCheckpointType checkpointType,
        boolean checkpointRequired,
        String classCode,
        String status,
        Integer lateMinutes,
        String comment,
        Instant recordedAt,
        UUID justificationPublicId,
        String justificationStatus,
        boolean canJustify) {
}
