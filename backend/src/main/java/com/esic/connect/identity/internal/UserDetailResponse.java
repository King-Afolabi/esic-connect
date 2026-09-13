package com.esic.connect.identity.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Vue détaillée d'un compte, identifiée par {@code public_id}. Comme
 * {@link UserSummaryResponse}, elle exclut tout identifiant interne, le
 * hachage du mot de passe et les jetons. {@code roleAssignments} contient
 * l'historique complet des rôles (actifs et clôturés). {@code
 * studentNumber} / {@code birthDate} sont facultatifs et sans lien avec le
 * rôle porté (refonte 2026-09) ; {@code studentNumber} est immuable une
 * fois posé.
 */
record UserDetailResponse(
        UUID publicId,
        String email,
        String firstName,
        String lastName,
        String phone,
        String studentNumber,
        LocalDate birthDate,
        AccountStatus status,
        Instant emailVerifiedAt,
        Instant lastLoginAt,
        Instant suspendedAt,
        String suspensionReason,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt,
        List<RoleAssignmentResponse> roleAssignments) {
}
