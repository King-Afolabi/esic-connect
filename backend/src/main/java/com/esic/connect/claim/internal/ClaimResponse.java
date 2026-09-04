package com.esic.connect.claim.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * En-tête d'une réclamation. Aucun identifiant SQL ; l'auteur est exposé
 * par son identifiant public, jamais par son nom — l'affichage résout
 * l'identité s'il en a le droit.
 */
record ClaimResponse(
        UUID publicId,
        UUID authorPublicId,
        String category,
        String subject,
        String status,
        String audience,
        UUID sessionPublicId,
        UUID classGroupPublicId,
        LocalDate periodStart,
        LocalDate periodEnd,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt) {
}
