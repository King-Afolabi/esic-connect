package com.esic.connect.notification.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Implémentation SMTP (Mailpit en développement) de
 * {@link PasswordResetMailer}.
 *
 * <p>Le lien est construit à partir de
 * {@code app.password-reset.base-url}. Le jeton n'apparaît nulle part
 * ailleurs que dans le message.
 */
@Component
class JavaMailSenderPasswordResetMailer implements PasswordResetMailer {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String resetBaseUrl;

    JavaMailSenderPasswordResetMailer(JavaMailSender mailSender,
                                      @Value("${app.mail.from}") String fromAddress,
                                      @Value("${app.password-reset.base-url}") String resetBaseUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.resetBaseUrl = resetBaseUrl;
    }

    @Override
    public void sendResetLink(String toEmail, String firstName, String rawToken, Instant expiresAt) {
        String separator = resetBaseUrl.contains("?") ? "&" : "?";
        String link = resetBaseUrl + separator + "token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Réinitialisation de votre mot de passe ESIC Connect");
        message.setText("""
                Bonjour %s,

                Une réinitialisation de mot de passe a été demandée pour
                votre compte ESIC Connect. Pour choisir un nouveau mot de
                passe, ouvrez le lien ci-dessous :

                %s

                Ce lien expire le %s et ne peut servir qu'une seule fois.

                Si vous n'êtes pas à l'origine de cette demande, ignorez ce
                message : votre mot de passe actuel reste valable.
                """.formatted(firstName, link, expiresAt));

        mailSender.send(message);
    }

    @Override
    public void sendPasswordChangedNotice(String toEmail, String firstName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Votre mot de passe ESIC Connect a été modifié");
        message.setText("""
                Bonjour %s,

                Le mot de passe de votre compte ESIC Connect vient d'être
                modifié. Toutes vos sessions ouvertes ont été fermées.

                Si vous êtes à l'origine de ce changement, aucune action
                n'est nécessaire.

                Sinon, demandez immédiatement une réinitialisation et
                prévenez l'administration de l'ESIC.
                """.formatted(firstName));

        mailSender.send(message);
    }
}
