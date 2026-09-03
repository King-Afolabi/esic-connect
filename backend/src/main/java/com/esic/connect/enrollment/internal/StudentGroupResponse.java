package com.esic.connect.enrollment.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Groupe temporaire tel qu'exposé par l'API (EF-ACA-007).
 *
 * @param memberCount effectif actif — évite au client de charger la liste
 *                    complète pour afficher un simple compteur
 */
public record StudentGroupResponse(
        UUID id,
        String code,
        String name,
        String status,
        UUID academicYearId,
        String academicYearCode,
        UUID programId,
        String programCode,
        UUID subjectId,
        String subjectCode,
        LocalDate startsOn,
        LocalDate endsOn,
        long memberCount,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Membre d'un groupe — identité minimale.
     *
     * <p>Ni nom, ni prénom, ni adresse : exactement ce qu'expose déjà
     * {@code EnrollmentResponse}. Le numéro étudiant et le code de classe
     * suffisent à identifier la ligne dans l'interface, et les données
     * nominatives sont résolues au besoin par les écrans qui en ont le
     * droit (docs/02 §33, minimisation).
     */
    public record Member(
            UUID id,
            UUID enrollmentId,
            UUID studentProfileId,
            String studentNumber,
            UUID classGroupId,
            String classGroupCode,
            Instant joinedAt) {
    }
}
