package com.esic.connect.identity.internal;

/**
 * Réponse neutre du parcours « mot de passe oublié ».
 *
 * <p>Identique que l'adresse soit connue ou non (docs/02 §17.8) : elle
 * décrit ce que la personne doit faire, jamais ce que le système a
 * trouvé.
 */
public record PasswordResetTtlResponse(String message) {

    static PasswordResetTtlResponse neutral() {
        return new PasswordResetTtlResponse(
                "Si un compte existe pour cette adresse, un lien de réinitialisation vient d'être envoyé. "
                        + "Vérifiez votre messagerie, y compris les indésirables.");
    }
}
