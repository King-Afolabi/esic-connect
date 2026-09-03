package com.esic.connect.notification.internal;

/**
 * Ce que <strong>nous</strong> savons de l'envoi (docs/02 §11.3).
 *
 * <p>Volontairement séparé de {@link EmailProviderStatus} : « remis au
 * serveur de messagerie » n'est <em>pas</em> « délivré ». Les confondre
 * ferait croire qu'une invitation est arrivée alors que l'adresse est
 * erronée — exactement le défaut que le cahier demande d'éviter.
 */
public enum EmailInternalStatus {
    /** Enregistré, pas encore remis au serveur de messagerie. */
    QUEUED,
    /** Remis au serveur de messagerie sans erreur. Ne dit rien de la réception. */
    SENT_TO_PROVIDER,
    /** Le traitement a échoué de notre côté (SMTP injoignable, refus immédiat). */
    PROCESSING_FAILED
}
