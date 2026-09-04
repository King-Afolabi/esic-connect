package com.esic.connect.coursesession.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Demande d'annulation telle qu'exposée par l'API (EF-SES-008).
 *
 * <p>Ne porte ni l'identité du demandeur ni celle du décideur : elles
 * relèvent de la piste d'audit, pas de l'affichage courant. Le motif,
 * lui, est nécessaire à la décision.
 */
public record CancellationRequestResponse(
        UUID publicId,
        UUID sessionPublicId,
        String status,
        String reason,
        String decisionComment,
        Instant decidedAt,
        Instant createdAt) {

    static CancellationRequestResponse from(SessionCancellationRequest request, UUID sessionPublicId) {
        return new CancellationRequestResponse(
                request.getPublicId(),
                sessionPublicId,
                request.getStatus().name(),
                request.getReason(),
                request.getDecisionComment(),
                request.getDecidedAt(),
                request.getCreatedAt());
    }
}
