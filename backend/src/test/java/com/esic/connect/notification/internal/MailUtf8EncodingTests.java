package com.esic.connect.notification.internal;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tous les courriels fonctionnels d'ESIC Connect restituent correctement
 * les caractères UTF-8 français (accents, œ, ç…) dans l'objet comme dans
 * le corps, données dynamiques comprises.
 *
 * <p>Vérification au niveau du message MIME réellement produit : aucun
 * SMTP externe, aucun envoi. Un {@link JavaMailSenderImpl} configuré
 * exactement comme en production ({@code default-encoding: UTF-8} —
 * {@code application.yml}) convertit le {@code SimpleMailMessage} en
 * {@link MimeMessage} ; on intercepte ce message avant l'expédition.
 *
 * <p>La corruption d'accents se manifeste de deux façons, toutes deux
 * couvertes ici : un objet ou un corps encodé dans un charset ASCII /
 * plateforme (accents perdus), ou une double conversion UTF-8 →
 * Latin-1 → UTF-8 (mojibake « Ã© » pour « é »).
 */
class MailUtf8EncodingTests {

    /** Adresse d'expéditeur avec accents dans le nom affiché (cas « si configuré »). */
    private static final String FROM_WITH_ACCENTS = "ÉSIC Connect <no-reply@esic.test>";
    private static final String APP_URL = "https://esic.test/app";

    private final List<MimeMessage> sent = new ArrayList<>();

    /** Sender identique à la production, mais qui capture le MIME au lieu de l'expédier. */
    private JavaMailSenderImpl capturingSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl() {
            @Override
            protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
                sent.addAll(List.of(mimeMessages));
            }
        };
        sender.setDefaultEncoding("UTF-8");
        return sender;
    }

    private String rawOf(MimeMessage message) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString("US-ASCII"); // le flux MIME est ASCII : accents encodés (RFC 2047 / QP / base64)
    }

    private String bodyOf(MimeMessage message) throws Exception {
        return message.getContent().toString();
    }

    // ------------------------------------------------------------------

    @Test
    void invitationEmailKeepsFrenchAccentsInSubjectAndBody() throws Exception {
        JavaMailSenderImpl sender = capturingSender();
        new JavaMailSenderInvitationMailer(sender, FROM_WITH_ACCENTS, "https://esic.test/activation")
                .sendActivationInvitation("eleve@esic.test", "Élève Frédéric Nöel",
                        "jeton-brut", Instant.parse("2026-12-31T23:00:00Z"));

        MimeMessage message = sent.get(0);
        String body = bodyOf(message);

        assertThat(body)
                .contains("Un compte ESIC Connect a été créé pour vous.")
                .contains("Pour définir votre mot de passe")
                .contains("Si vous n'êtes pas concerné")
                .contains("Bonjour Élève Frédéric Nöel,")
                .doesNotContain("Ã©").doesNotContain("Ã¨");

        String raw = rawOf(message);
        assertThat(raw).contains("charset=UTF-8");
        assertThat(raw).doesNotContain("Ã©");
    }

    @Test
    void passwordResetEmailKeepsAccentsInSubjectAndBody() throws Exception {
        JavaMailSenderImpl sender = capturingSender();
        new JavaMailSenderPasswordResetMailer(sender, FROM_WITH_ACCENTS, "https://esic.test/reset")
                .sendResetLink("eleve@esic.test", "Éloïse", "jeton-brut",
                        Instant.parse("2026-12-31T23:00:00Z"));

        MimeMessage message = sent.get(0);

        // getSubject() : JavaMail décode l'objet RFC 2047 -> chaîne accentuée intacte.
        assertThat(message.getSubject())
                .isEqualTo("Réinitialisation de votre mot de passe ESIC Connect");
        assertThat(bodyOf(message))
                .contains("Une réinitialisation de mot de passe a été demandée")
                .contains("Si vous n'êtes pas à l'origine de cette demande")
                .contains("Bonjour Éloïse,")
                .doesNotContain("Ã");

        String raw = rawOf(message);
        // L'objet non-ASCII est un mot encodé UTF-8, jamais du Latin-1 ni du texte brut mojibaké.
        assertThat(raw).containsPattern("Subject: =\\?UTF-8\\?");
        assertThat(raw).doesNotContain("=?ISO-8859-1?").doesNotContain("=?us-ascii?");
    }

    @Test
    void passwordChangedNoticeKeepsAccents() throws Exception {
        JavaMailSenderImpl sender = capturingSender();
        new JavaMailSenderPasswordResetMailer(sender, FROM_WITH_ACCENTS, "https://esic.test/reset")
                .sendPasswordChangedNotice("eleve@esic.test", "Agnès");

        MimeMessage message = sent.get(0);
        assertThat(message.getSubject()).isEqualTo("Votre mot de passe ESIC Connect a été modifié");
        assertThat(bodyOf(message))
                .contains("vient d'être")
                .contains("ont été fermées")
                .contains("prévenez l'administration de l'ESIC")
                .contains("Bonjour Agnès,")
                .doesNotContain("Ã");
    }

    @Test
    void notificationEmailKeepsAccentsIncludingDynamicData() throws Exception {
        JavaMailSenderImpl sender = capturingSender();
        new JavaMailSenderNotificationMailer(sender, FROM_WITH_ACCENTS, APP_URL)
                .sendNotification("eleve@esic.test",
                        "Présence enregistrée",
                        "Bonjour Élève, votre présence a été enregistrée à l'entrée de la salle "
                                + "« Amphithéâtre André Gœury ».");

        MimeMessage message = sent.get(0);
        assertThat(message.getSubject()).isEqualTo("ESIC Connect — Présence enregistrée");
        assertThat(bodyOf(message))
                .contains("votre présence a été enregistrée à l'entrée de la salle")
                .contains("« Amphithéâtre André Gœury »")
                .contains("Retrouvez le détail dans votre espace ESIC Connect")
                .contains("depuis vos préférences.")
                .doesNotContain("Ã");

        assertThat(rawOf(message)).contains("charset=UTF-8");
    }

    @Test
    void senderDisplayNameWithAccentsIsPreserved() throws Exception {
        JavaMailSenderImpl sender = capturingSender();
        new JavaMailSenderNotificationMailer(sender, FROM_WITH_ACCENTS, APP_URL)
                .sendNotification("eleve@esic.test", "Titre neutre", "Corps neutre.");

        InternetAddress from = (InternetAddress) sent.get(0).getFrom()[0];
        assertThat(from.getPersonal()).isEqualTo("ÉSIC Connect");
        assertThat(from.getAddress()).isEqualTo("no-reply@esic.test");
    }
}
