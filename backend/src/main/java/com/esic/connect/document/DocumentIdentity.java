package com.esic.connect.document;

import java.time.Instant;

/**
 * Identité d'un document officiel (docs/02 §22.4 ; AC-033).
 *
 * <p>Un document produit par ESIC Connect doit pouvoir être rattaché à
 * son émetteur et retrouvé : sans identifiant, une attestation imprimée
 * n'est qu'une feuille de papier que rien ne distingue d'une autre.
 *
 * @param documentId    identifiant du document, vérifiable et non deviné
 * @param issuer        émetteur (l'établissement)
 * @param authorDisplay auteur humain ou « système », jamais une adresse
 * @param generatedAt   date et heure de génération
 */
public record DocumentIdentity(
        String documentId,
        String issuer,
        String authorDisplay,
        Instant generatedAt) {

    /** Mention obligatoire portée par tout document officiel. */
    public static final String ELECTRONIC_NOTICE =
            "Document électronique produit par ESIC Connect. Sa validité peut être vérifiée "
                    + "auprès de l'établissement au moyen de son identifiant.";
}
