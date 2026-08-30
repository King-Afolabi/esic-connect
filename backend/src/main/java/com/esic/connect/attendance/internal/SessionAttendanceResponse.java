package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttendanceStatus;
import com.esic.connect.coursesession.AttendanceCheckpointStatus;
import com.esic.connect.coursesession.AttendanceCheckpointType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Présences d'une séance pour l'écran de gestion (V10 — plusieurs points
 * de contrôle).
 *
 * <p>Compat V9 : les champs {@code checkpointPublicId}, {@code expectedCount},
 * {@code presentCount} et {@code records} reflètent le <em>premier</em>
 * point de contrôle. {@code checkpoints} donne le détail par point de
 * contrôle (V10).
 *
 * @param sessionPublicId    séance concernée
 * @param checkpointPublicId premier point de contrôle (compat)
 * @param expectedCount      effectif attendu du premier point de contrôle (compat)
 * @param presentCount       présences valides du premier point de contrôle (compat)
 * @param records            lignes du premier point de contrôle (compat)
 * @param checkpoints        détail par point de contrôle
 */
record SessionAttendanceResponse(
        UUID sessionPublicId,
        UUID checkpointPublicId,
        long expectedCount,
        int presentCount,
        List<Row> records,
        List<CheckpointAttendance> checkpoints) {

    /** Présences et compteurs d'un point de contrôle. */
    record CheckpointAttendance(
            UUID checkpointPublicId,
            String label,
            AttendanceCheckpointType type,
            AttendanceCheckpointStatus status,
            boolean required,
            long expectedCount,
            int presentCount,
            int lateCount,
            int absentCount,
            int excusedCount,
            int derivedAbsentCount,
            List<Row> records) {
    }

    /**
     * Ligne de présence — identité minimale, jamais d'adresse
     * électronique ni d'identifiant interne.
     */
    record Row(
            UUID attendancePublicId,
            UUID studentProfilePublicId,
            UUID enrollmentPublicId,
            String studentNumber,
            String firstName,
            String lastName,
            AttendanceStatus status,
            Integer lateMinutes,
            String comment,
            Instant recordedAt,
            AttendanceRecordSource source) {
    }
}
