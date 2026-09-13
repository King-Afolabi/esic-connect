package com.esic.connect.claim.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * En-tête d'une réclamation. Aucun identifiant SQL ; les {@code *PublicId}
 * restent la référence stable pour tout lien ou appel API.
 *
 * <p>Les champs {@code authorName}, {@code sessionLabel}, {@code
 * classLabel} et {@code targetTeacherName} (Lot 18) résolvent un affichage
 * lisible <strong>côté serveur</strong>, par les ports publics déjà
 * disponibles ({@code identity}, {@code coursesession}, {@code academic})
 * — jamais en ouvrant {@code GET /users/{id}} à un rôle supplémentaire.
 * Tous facultatifs : {@code null} si la personne ou l'objet visé n'existe
 * plus, sans jamais faire échouer la lecture de la réclamation elle-même.
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
        Instant updatedAt,
        String authorName,
        String sessionLabel,
        String classLabel,
        /** Formateur explicitement ciblé (Lot 19) ; {@code null} si aucun. */
        UUID targetTeacherPublicId,
        String targetTeacherName) {
}
