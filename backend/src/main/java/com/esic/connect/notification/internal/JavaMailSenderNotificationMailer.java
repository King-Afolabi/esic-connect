package com.esic.connect.notification.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Adaptateur SMTP de {@link NotificationMailer} (Mailpit en local,
 * fournisseur réel en production — docs/02 §28.4).
 *
 * <p>Le message ne contient <strong>aucune donnée sensible</strong> : il
 * reprend le titre et le corps neutres déjà écrits au centre de
 * notifications, et renvoie vers l'application. Pas de lien profond
 * calculé côté serveur : le cahier réserve la construction des chemins
 * d'interface au client (§21.5).
 */
@Component
class JavaMailSenderNotificationMailer implements NotificationMailer {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String applicationUrl;

    JavaMailSenderNotificationMailer(JavaMailSender mailSender,
                                     @Value("${app.mail.from}") String fromAddress,
                                     @Value("${app.web.base-url}") String applicationUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.applicationUrl = applicationUrl;
    }

    @Override
    public void sendNotification(String toEmail, String title, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("ESIC Connect — " + title);
        message.setText("""
                Bonjour,

                %s

                Retrouvez le detail dans votre espace ESIC Connect :
                %s

                Vous pouvez regler les notifications que vous recevez par
                courriel depuis vos preferences.
                """.formatted(body, applicationUrl));
        mailSender.send(message);
    }
}
