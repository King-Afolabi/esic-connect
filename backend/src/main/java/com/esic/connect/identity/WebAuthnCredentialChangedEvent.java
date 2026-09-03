package com.esic.connect.identity;

import java.util.UUID;

/**
 * Publié lors de l'ajout ou de la révocation d'une passkey
 * (EF-AUTH-006 ; docs/02 §23.1 : « ajout ou suppression d'un facteur ou
 * d'une passkey »). Ne porte aucune clé publique ni identifiant de
 * justificatif.
 */
public record WebAuthnCredentialChangedEvent(Long userId, UUID userPublicId, Action action) {

    public enum Action {
        REGISTERED,
        REVOKED
    }
}
