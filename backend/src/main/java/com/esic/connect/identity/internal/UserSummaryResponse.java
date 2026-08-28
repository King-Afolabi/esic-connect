package com.esic.connect.identity.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vue liste d'un compte. N'expose que l'identifiant public : jamais l'id
 * SQL interne, le hachage du mot de passe ni un jeton (cahier §30.3, §41).
 *
 * @param roles codes des rôles actifs
 */
record UserSummaryResponse(
        UUID publicId,
        String email,
        String firstName,
        String lastName,
        AccountStatus status,
        List<String> roles,
        Instant createdAt,
        Instant lastLoginAt) {
}
