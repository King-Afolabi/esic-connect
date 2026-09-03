package com.esic.connect.notification.internal;

import com.esic.connect.identity.AccountInvitationIssuedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envoie le courriel d'activation après validation de la transaction
 * d'émission ({@code AFTER_COMMIT}) et <strong>trace la tentative</strong>
 * (EF-USER-008).
 *
 * <p>Si l'envoi échoue, l'invitation — déjà committée — est conservée :
 * on journalise une erreur technique, sans jeton, sans adresse et sans
 * lien, et la trace passe en {@code PROCESSING_FAILED}. Un responsable
 * voit alors, dans le journal de délivrabilité, qu'il faut corriger
 * l'adresse et réémettre — au lieu d'attendre indéfiniment une activation
 * qui n'arrivera jamais.
 *
 * <p>Ce qui reste absent, et qui est assumé : il n'existe pas encore de
 * file persistante avec reprise garantie (dette T-01, outbox planifiée au
 * sprint 10). La trace dit ce qui s'est passé ; elle ne rejoue pas.
 */
@Component
class InvitationEmailListener {

    private static final Logger log = LoggerFactory.getLogger(InvitationEmailListener.class);

    /** Catégorie de message, pour filtrer le journal de délivrabilité. */
    static final String MESSAGE_TYPE = "ACCOUNT_INVITATION";

    private final InvitationMailer invitationMailer;
    private final EmailDeliveryService deliveryService;

    InvitationEmailListener(InvitationMailer invitationMailer, EmailDeliveryService deliveryService) {
        this.invitationMailer = invitationMailer;
        this.deliveryService = deliveryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountInvitationIssued(AccountInvitationIssuedEvent event) {
        EmailDelivery delivery = deliveryService.open(event.email(), null, MESSAGE_TYPE);
        try {
            invitationMailer.sendActivationInvitation(
                    event.email(), event.firstName(), event.rawToken(), event.expiresAt());
            // « Remis au serveur de messagerie » — jamais « délivré » :
            // seul le fournisseur peut l'affirmer (docs/02 §11.3).
            deliveryService.recordSent(delivery.getId());
        } catch (RuntimeException ex) {
            log.error("Echec d'envoi de l'email d'invitation (invitation conservee) : {}",
                    ex.getClass().getSimpleName());
            deliveryService.recordFailure(delivery.getId(), ex.getClass().getSimpleName());
        }
    }
}
