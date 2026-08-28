package com.esic.connect.audit.internal;

import com.esic.connect.identity.AccountLifecycleAction;
import com.esic.connect.identity.AccountLifecycleEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Trace les etapes du cycle de vie d'un compte (emission d'invitation,
 * activation) dans {@code audit_event} — docs/02-cahier-des-charges.md
 * §30.1. Ne recoit jamais de jeton ni de donnee personnelle : l'evenement
 * {@link AccountLifecycleEvent} n'en transporte pas.
 *
 * Transaction dediee ({@code REQUIRES_NEW}) : un incident d'ecriture de
 * l'audit ne compromet pas la transaction metier appelante.
 */
@Component
public class AccountLifecycleAuditListener {

    private final AuditEventRepository auditEventRepository;

    public AccountLifecycleAuditListener(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAccountLifecycle(AccountLifecycleEvent event) {
        boolean selfService = event.action() == AccountLifecycleAction.ACCOUNT_ACTIVATED;
        Long actorUserId = selfService ? event.userId() : event.actorUserId();
        String action = switch (event.action()) {
            case INVITATION_ISSUED -> "ACCOUNT_INVITATION_ISSUED";
            case ACCOUNT_ACTIVATED -> "ACCOUNT_ACTIVATED";
        };

        AuditEvent auditEvent = new AuditEvent(Instant.now(), actorUserId, action,
                "IDENTITY", "USER_ACCOUNT", "SUCCESS");
        auditEvent.setActorPublicIdSnapshot(event.userPublicId());
        if (event.detail() != null) {
            auditEvent.setReason(event.detail());
        }
        auditEventRepository.save(auditEvent);
    }
}
