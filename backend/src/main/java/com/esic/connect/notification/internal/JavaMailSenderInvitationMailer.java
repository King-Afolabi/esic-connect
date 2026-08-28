package com.esic.connect.notification.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Implémentation SMTP (Mailpit en développement) de {@link InvitationMailer}.
 *
 * Construit le lien d'activation à partir de {@code app.activation.base-url}
 * et du jeton brut. Le jeton n'est jamais journalisé ici.
 */
@Component
class JavaMailSenderInvitationMailer implements InvitationMailer {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String activationBaseUrl;

    JavaMailSenderInvitationMailer(JavaMailSender mailSender,
                                   @Value("${app.mail.from}") String fromAddress,
                                   @Value("${app.activation.base-url}") String activationBaseUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.activationBaseUrl = activationBaseUrl;
    }

    @Override
    public void sendActivationInvitation(String toEmail, String firstName, String rawToken, Instant expiresAt) {
        String separator = activationBaseUrl.contains("?") ? "&" : "?";
        String link = activationBaseUrl + separator + "token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Activation de votre compte ESIC Connect");
        message.setText("""
                Bonjour %s,

                Un compte ESIC Connect a ete cree pour vous.
                Pour definir votre mot de passe et activer votre compte,
                ouvrez le lien ci-dessous :

                %s

                Ce lien expire le %s.
                Si vous n'etes pas concerne, ignorez ce message.
                """.formatted(firstName, link, expiresAt));

        mailSender.send(message);
    }
}
