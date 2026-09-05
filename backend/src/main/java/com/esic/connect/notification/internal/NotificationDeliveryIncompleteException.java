package com.esic.connect.notification.internal;

/**
 * Au moins un destinataire n'a pas pu être notifié. Fait repasser le
 * message d'outbox en reprise, sans rien révéler du destinataire
 * concerné : seul leur nombre est porté.
 */
class NotificationDeliveryIncompleteException extends RuntimeException {

    NotificationDeliveryIncompleteException(int failedRecipients) {
        super("destinataires en echec : " + failedRecipients);
    }
}
