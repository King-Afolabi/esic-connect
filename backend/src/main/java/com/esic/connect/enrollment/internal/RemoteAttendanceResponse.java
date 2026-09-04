package com.esic.connect.enrollment.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Autorisation de suivi à distance telle qu'exposée par l'API
 * (EF-ENR-004).
 *
 * <p>Ne porte ni identité du décideur ni identité du révocateur : elles
 * relèvent de la piste d'audit. Le motif, lui, est nécessaire — c'est ce
 * qui permet à un autre responsable de comprendre une décision prise
 * avant lui.
 */
record RemoteAttendanceResponse(
        UUID publicId,
        UUID studentUserPublicId,
        UUID classGroupPublicId,
        String status,
        String reason,
        LocalDate validFrom,
        LocalDate validUntil,
        Instant decidedAt,
        Instant revokedAt,
        String revocationReason,
        Instant createdAt) {
}
