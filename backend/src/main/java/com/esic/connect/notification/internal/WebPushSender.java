package com.esic.connect.notification.internal;

/**
 * Port d'envoi d'une notification poussée (EF-NOTIF-005 ; docs/02 §29.3).
 *
 * <p>Comme le port antivirus, il énonce honnêtement son état : sans
 * paire de clés VAPID configurée, {@link #isActive()} renvoie
 * {@code false} et l'API le dit. Le produit ne simule jamais un envoi
 * qu'il n'a pas fait.
 */
interface WebPushSender {

    /**
     * @return {@code true} si un fournisseur de poussée est réellement
     *         joignable et configuré. {@code false} signifie qu'aucune
     *         poussée n'a lieu — l'appelant doit le dire, pas le taire.
     */
    boolean isActive();

    /**
     * @param endpoint   URL de terminaison du service de poussée
     * @param p256dhKey  clé publique ECDH de l'abonné (base64url)
     * @param authSecret secret d'authentification de l'abonné (base64url)
     * @param payload    contenu chiffré de bout en bout ; ne comporte
     *                   aucune donnée sensible (§29.3)
     */
    Outcome send(String endpoint, String p256dhKey, String authSecret, String payload);

    /** Résultat d'un envoi. */
    enum Outcome {
        /** Le service de poussée a accepté le message. */
        SENT,
        /**
         * L'abonnement n'existe plus chez le service (404 / 410). Ce n'est
         * pas un incident : l'appareil s'est désabonné ou a été réinstallé.
         * L'abonnement doit être révoqué, jamais retenté.
         */
        GONE,
        /** Échec temporaire ou inattendu : à replanifier. */
        FAILED,
        /** Aucun fournisseur configuré : rien n'a été tenté. */
        INACTIVE
    }
}
