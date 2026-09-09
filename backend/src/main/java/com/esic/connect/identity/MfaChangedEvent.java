package com.esic.connect.identity;

import java.util.UUID;

/**
 * Publié lorsque le second facteur d'un compte change d'état
 * (EF-AUTH-008, EF-AUTH-009 ; docs/02 §23.1 : « ajout ou suppression
 * d'un facteur »).
 *
 * <p>Ne porte ni secret, ni code, ni adresse : uniquement l'identité
 * technique du compte et la nature du changement.
 */
public record MfaChangedEvent(Long userId, UUID userPublicId, Action action) {

    public enum Action {
        /** Un second facteur vient d'être confirmé et activé. */
        ENROLLED,
        /** Le second facteur a été retiré. */
        DISABLED,
        /** Un code de récupération à usage unique a été consommé. */
        RECOVERY_CODE_USED,
        /** La série de codes de récupération a été renouvelée. */
        RECOVERY_CODES_REGENERATED
    }
}
