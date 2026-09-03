package com.esic.connect.notification.internal;

import java.time.Instant;

/**
 * Port d'envoi des messages liés au mot de passe. Séparé de
 * {@link InvitationMailer} : les deux parcours ont des contenus, des
 * destinataires et des contraintes de sécurité distincts.
 */
interface PasswordResetMailer {

    /** Lien de réinitialisation. Le jeton brut ne doit jamais être journalisé. */
    void sendResetLink(String toEmail, String firstName, String rawToken, Instant expiresAt);

    /**
     * Avertissement envoyé après un changement effectif de mot de passe.
     * Ne contient aucun lien d'action : son seul rôle est d'alerter la
     * personne si le changement ne vient pas d'elle.
     */
    void sendPasswordChangedNotice(String toEmail, String firstName);
}
