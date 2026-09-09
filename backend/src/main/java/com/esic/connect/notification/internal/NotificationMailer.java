package com.esic.connect.notification.internal;

/**
 * Envoi du courriel correspondant à une notification métier
 * (EF-NOTIF-004 ; docs/02 §21.1).
 *
 * <p>Abstraction distincte de {@link InvitationMailer} : une invitation
 * porte un jeton et un lien à usage unique, une notification ne porte
 * qu'un libellé neutre et renvoie vers l'application. Les confondre
 * ferait courir le risque d'un envoi de jeton par un chemin qui n'est pas
 * conçu pour.
 */
interface NotificationMailer {

    /**
     * @param toEmail adresse du destinataire, résolue au moment de
     *                l'envoi — jamais conservée dans la file
     * @param title   titre de la notification, déjà neutre
     * @param body    corps de la notification, déjà neutre
     */
    void sendNotification(String toEmail, String title, String body);
}
