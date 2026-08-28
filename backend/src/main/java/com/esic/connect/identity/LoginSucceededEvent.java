package com.esic.connect.identity;

import java.util.UUID;

/**
 * Publié après une authentification réussie (docs/03-architecture.md
 * §8.3). Consommé par le module {@code audit} — aucune donnée sensible
 * (mot de passe, jeton) n'est transportée.
 */
public record LoginSucceededEvent(
        Long userId,
        UUID userPublicId,
        String displaySnapshot,
        String roleSnapshot) {
}
