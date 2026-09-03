package com.esic.connect.notification.internal;

/**
 * Ce que le <strong>fournisseur</strong> nous remonte (docs/02 §11.3).
 *
 * <p>Reste {@link #UNKNOWN} tant qu'il ne dit rien — ce qui est le cas de
 * Mailpit en développement, qui n'expose aucun retour de délivrabilité.
 * Afficher « délivré » par défaut serait une affirmation sans preuve.
 */
public enum EmailProviderStatus {
    UNKNOWN,
    DELIVERED,
    BOUNCED,
    REJECTED,
    COMPLAINED
}
