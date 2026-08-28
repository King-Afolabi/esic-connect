package com.esic.connect.identity.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vue détaillée d'un compte, identifiée par {@code public_id}. Comme
 * {@link UserSummaryResponse}, elle exclut tout identifiant interne, le
 * hachage du mot de passe et les jetons. {@code roleAssignments} contient
 * l'historique complet des rôles (actifs et clôturés).
 */
record UserDetailResponse(
        UUID publicId,
        String email,
        String firstName,
        String lastName,
        String phone,
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
