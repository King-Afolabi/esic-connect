package com.esic.connect.identity;

import java.util.UUID;

/**
 * Publié lorsqu'un appareil de confiance est ajouté ou révoqué
 * (EF-AUTH-013 ; docs/02 §23.1). Ne porte aucune empreinte d'appareil ni
 * adresse : seulement le compte concerné et la nature du changement.
 */
public record TrustedDeviceChangedEvent(Long userId, UUID userPublicId, Action action) {

    public enum Action {
        ADDED,
        REVOKED
    }
}
