package com.esic.connect.identity.internal;

/**
 * Redis, qui porte les défis de second facteur, est injoignable.
 *
 * <p>Se traduit par un {@code 503} : sans défi vérifiable, il n'existe
 * aucune façon sûre d'émettre un jeton. Aucune validation dégradée n'est
 * acceptée (docs/02 §24.5).
 */
public class MfaBackendUnavailableException extends RuntimeException {

    public MfaBackendUnavailableException(Throwable cause) {
        super("Le service de second facteur est momentanément indisponible.", cause);
    }
}
