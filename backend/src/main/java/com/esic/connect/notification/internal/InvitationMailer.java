package com.esic.connect.notification.internal;

import java.time.Instant;

/**
 * Envoi de l'email d'activation. Abstraction permettant de substituer un
 * double dans les tests sans ouvrir de connexion SMTP réelle.
 */
public interface InvitationMailer {

    void sendActivationInvitation(String toEmail, String firstName, String rawToken, Instant expiresAt);
}
