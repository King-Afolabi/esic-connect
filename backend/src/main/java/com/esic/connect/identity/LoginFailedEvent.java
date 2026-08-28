package com.esic.connect.identity;

import java.util.UUID;

/**
 * Publié après un refus de connexion, quel qu'en soit le motif (email
 * inconnu, mot de passe incorrect, compte non actif). {@code userId} et
 * {@code userPublicId} restent nuls lorsque l'adresse ne correspond à
 * aucun compte : l'adresse brute saisie n'est jamais transportée par cet
 * événement ni conservée dans l'audit.
 */
public record LoginFailedEvent(
        Long userId,
        UUID userPublicId,
        String displaySnapshot,
        String reasonCode) {
}
