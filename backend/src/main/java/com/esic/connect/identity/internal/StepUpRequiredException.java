package com.esic.connect.identity.internal;

/**
 * L'action demandée exige une authentification forte que le jeton
 * présenté n'atteste pas (EF-AUTH-015).
 *
 * <p>Se traduit par un {@code 403} portant un code stable, que
 * l'interface reconnaît pour proposer la réauthentification plutôt que
 * d'afficher un refus définitif.
 */
public class StepUpRequiredException extends RuntimeException {

    public StepUpRequiredException() {
        super("Cette action exige une vérification renforcée. Reconnectez-vous "
                + "avec votre second facteur ou votre clé d'accès.");
    }
}
