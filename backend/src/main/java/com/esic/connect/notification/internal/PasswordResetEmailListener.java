package com.esic.connect.notification.internal;

import com.esic.connect.identity.PasswordChangedEvent;
import com.esic.connect.identity.PasswordResetRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envoie les messages du parcours « mot de passe oublié », après
 * validation de la transaction ({@code AFTER_COMMIT}).
 *
 * <p>Aucun courriel ne part si la transaction métier est annulée
 * (RG-097) : un lien de réinitialisation dont le jeton n'existe pas en
 * base serait au mieux inutilisable, au pire déroutant.
 *
 * <p>Un échec d'envoi est journalisé sans jeton, sans lien et sans
 * adresse. Il n'existe pas encore de reprise garantie : c'est la dette
 * T-01 (outbox transactionnelle), planifiée au sprint 10.
 */
@Component
class PasswordResetEmailListener {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailListener.class);

    private final PasswordResetMailer passwordResetMailer;

    PasswordResetEmailListener(PasswordResetMailer passwordResetMailer) {
        this.passwordResetMailer = passwordResetMailer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        try {
            passwordResetMailer.sendResetLink(
                    event.email(), event.firstName(), event.rawToken(), event.expiresAt());
        } catch (RuntimeException ex) {
            log.error("Echec d'envoi du lien de reinitialisation : {}", ex.getClass().getSimpleName());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordChanged(PasswordChangedEvent event) {
        try {
            passwordResetMailer.sendPasswordChangedNotice(event.email(), event.firstName());
        } catch (RuntimeException ex) {
            log.error("Echec d'envoi de l'avertissement de changement de mot de passe : {}",
                    ex.getClass().getSimpleName());
        }
    }
}
