package com.esic.connect.shared.ratelimit;

import java.time.Duration;

/**
 * Limitation de débit des opérations sensibles (docs/02-cahier-des-charges.md
 * §17.6 ; EF-AUTH-012 ; RG-092).
 *
 * <p>Les compteurs vivent dans Redis, jamais en base : ce sont des
 * données temporaires (docs/02 §24.1). L'identité qui sert de clé est
 * toujours transmise sous forme d'<strong>empreinte</strong> — jamais une
 * adresse électronique ni une adresse IP en clair (RG-094).
 */
public interface RateLimiter {

    /**
     * Consomme une unité du seau {@code bucket} pour {@code identityHash}.
     *
     * @param bucket       nom du seau, par exemple {@code login} ou
     *                     {@code forgot-password} ; sert de préfixe de clé
     * @param identityHash empreinte de l'identité limitée (jamais la
     *                     valeur en clair)
     * @param limit        nombre maximal d'unités sur la fenêtre
     * @param window       durée de la fenêtre
     * @return la décision : autorisée ou non, avec le délai avant nouvelle
     *         tentative
     */
    RateLimitDecision consume(String bucket, String identityHash, int limit, Duration window);

    /**
     * Efface le compteur d'un seau pour une identité. Appelé après une
     * opération réussie qui doit remettre le compteur à zéro — une
     * connexion réussie, par exemple.
     */
    void reset(String bucket, String identityHash);
}
