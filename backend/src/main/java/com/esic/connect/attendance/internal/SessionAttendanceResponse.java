package com.esic.connect.attendance.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Liste des présences d'une séance pour l'écran formateur.
 *
 * @param sessionPublicId    séance concernée
 * @param checkpointPublicId point de contrôle unique
 * @param expectedCount      effectif attendu (inscriptions actives des classes de la séance)
 * @param presentCount       nombre de présences enregistrées
 * @param records            lignes de présence, triées par heure d'enregistrement
 */
record SessionAttendanceResponse(
        UUID sessionPublicId,
        UUID checkpointPublicId,
        long expectedCount,
        int presentCount,
        List<Row> records) {

    /**
     * Ligne de présence — identité minimale, jamais d'adresse
     * électronique ni d'identifiant interne.
     */
    record Row(
            UUID studentProfilePublicId,
            UUID enrollmentPublicId,
            String studentNumber,
            String firstName,
            String lastName,
            Instant recordedAt,
            AttendanceRecordSource source) {
    }
}
