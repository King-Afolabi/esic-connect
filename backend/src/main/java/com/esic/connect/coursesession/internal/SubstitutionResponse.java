package com.esic.connect.coursesession.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue API d'un remplacement de formateur (G1-C.2) — jamais d'identifiant
 * SQL interne. Le formateur principal ({@code originalTeacher}) et le
 * remplaçant ({@code substitute}) sont exposés côte à côte : l'affectation
 * principale de la séance n'est jamais écrasée.
 *
 * @param publicId        identifiant public du remplacement
 * @param status          {@code ACTIVE} ou {@code ENDED}
 * @param reason          motif (borné à 500)
 * @param validFrom       début de la période de validité (UTC)
 * @param validUntil      fin de la période de validité (UTC, exclue)
 * @param substitute      identité du remplaçant
 * @param originalTeacher identité du formateur principal (figée à la création)
 * @param createdAt       horodatage de création
 * @param endedAt         horodatage de fin ({@code null} tant que {@code ACTIVE})
 */
record SubstitutionResponse(
        UUID publicId,
        String status,
        String reason,
        Instant validFrom,
        Instant validUntil,
        CourseSessionResponse.TeacherView substitute,
        CourseSessionResponse.TeacherView originalTeacher,
        Instant createdAt,
        Instant endedAt) {
}
