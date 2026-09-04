package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.EarlyDepartureEffect;

import java.time.Instant;
import java.util.UUID;

/**
 * Dossier de départ anticipé tel qu'exposé par l'API (EF-ATT-013).
 *
 * <p>{@code effect} est calculé à la lecture depuis {@code status} — il
 * n'existe pas en base (docs/02 §16.13).
 *
 * <p>Les acteurs sont désignés par leur <strong>rôle</strong> et non par
 * leur nom : l'apprenant a le droit de savoir quelle fonction a tranché,
 * pas d'obtenir l'identité civile d'un agent (minimisation, docs/02 §14).
 */
record EarlyDepartureResponse(
        UUID publicId,
        UUID sessionPublicId,
        String sessionTitle,
        Instant sessionStartsAt,
        UUID enrollmentPublicId,
        String classCode,
        Instant departureAt,
        String reason,
        String status,
        EarlyDepartureEffect effect,
        Instant requestedAt,
        String teacherOpinion,
        String teacherOpinionComment,
        Instant teacherOpinionAt,
        String decidedByRole,
        Instant decidedAt,
        String decisionComment) {
}
