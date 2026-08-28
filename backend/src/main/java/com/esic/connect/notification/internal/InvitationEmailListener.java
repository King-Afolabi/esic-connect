package com.esic.connect.notification.internal;

import com.esic.connect.identity.AccountInvitationIssuedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envoie l'email d'activation après validation de la transaction
 * d'émission ({@code AFTER_COMMIT}).
 *
 * Si l'envoi échoue, l'invitation — déjà committée — est conservée : on
 * journalise uniquement une erreur technique, sans jeton, sans email et
 * sans lien. Il n'existe pas encore de file persistante ni de reprise
 * garantie (dette technique, docs/03-architecture.md §18).
 */
@Component
class InvitationEmailListener {

    private static final Logger log = LoggerFactory.getLogger(InvitationEmailListener.class);

    private final InvitationMailer invitationMailer;

    InvitationEmailListener(InvitationMailer invitationMailer) {
        this.invitationMailer = invitationMailer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountInvitationIssued(AccountInvitationIssuedEvent event) {
        try {
            invitationMailer.sendActivationInvitation(
                    event.email(), event.firstName(), event.rawToken(), event.expiresAt());
        } catch (RuntimeException ex) {
            log.error("Echec d'envoi de l'email d'invitation (invitation conservee) : {}",
                    ex.getClass().getSimpleName());
        }
    }
}
