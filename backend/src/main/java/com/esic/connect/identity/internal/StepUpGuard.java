package com.esic.connect.identity.internal;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Réauthentification avant une action critique (EF-AUTH-015, RG-009 ;
 * docs/02 §17.4).
 *
 * <p>Le jeton d'accès porte le claim {@code amr} — les moyens
 * d'authentification réellement employés pour l'obtenir. Une action
 * critique exige que ce jeton ait été délivré contre un facteur
 * <strong>fort</strong> : un code TOTP, une passkey ou un code de
 * récupération. Un jeton obtenu par mot de passe seul ne suffit pas.
 *
 * <p>Ce contrôle ne remplace pas l'autorisation par rôle : il s'y ajoute.
 * Un utilisateur qui n'a pas le droit d'agir reçoit un {@code 403} avant
 * même d'arriver ici.
 */
@Component
public class StepUpGuard {

    /**
     * Moyens considérés comme forts. Le mot de passe en est volontairement
     * absent : il est ce que l'on cherche justement à compléter.
     */
    private static final Set<String> STRONG_METHODS = Set.of(
            AccessTokenIssuer.AMR_OTP,
            AccessTokenIssuer.AMR_WEBAUTHN,
            AccessTokenIssuer.AMR_RECOVERY);

    /**
     * @throws StepUpRequiredException si le jeton n'atteste d'aucun
     *                                 facteur fort ; l'appelant doit se
     *                                 réauthentifier avant de recommencer
     */
    public void requireStrongAuthentication(Jwt caller) {
        if (caller == null) {
            throw new StepUpRequiredException();
        }
        List<String> methods = caller.getClaimAsStringList("amr");
        if (methods == null || methods.stream().noneMatch(STRONG_METHODS::contains)) {
            throw new StepUpRequiredException();
        }
    }
}
