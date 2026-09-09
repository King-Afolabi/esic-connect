package com.esic.connect.claim;

import java.util.Collection;
import java.util.UUID;

/**
 * Port public de lecture d'agrégats de réclamations pour les tableaux de
 * bord (EF-REP-007 ; docs/02 §22.6 — « réclamations ouvertes »).
 *
 * <p><strong>Agrégats bornés uniquement</strong> ({@code COUNT}) : ni
 * entité, ni repository, ni contenu de message. Le tableau de bord
 * affiche un nombre et un lien ; lire un dossier passe par l'écran des
 * réclamations, où le contrôle d'accès complet s'applique.
 */
public interface ClaimDashboardDirectory {

    /**
     * Réclamations non closes adressées à un guichet, restreintes aux
     * classes indiquées.
     *
     * @param audience              guichet concerné
     * @param classGroupPublicIds   périmètre ; {@code null} = pas de
     *                              restriction (guichet global)
     */
    long countOpenClaims(ClaimAudience audience, Collection<UUID> classGroupPublicIds);
}
