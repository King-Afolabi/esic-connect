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
 * activation, suspension, reactivation, archivage, attribution / retrait
 * de role) dans {@code audit_event} — docs/02-cahier-des-charges.md
 * §30.1. Ne recoit jamais de jeton ni de donnee personnelle inutile :
 * l'evenement {@link AccountLifecycleEvent} ne transporte que des
 * identifiants, l'action et un motif non sensible.
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
            case ACCOUNT_SUSPENDED -> "ACCOUNT_SUSPENDED";
            case ACCOUNT_REACTIVATED -> "ACCOUNT_REACTIVATED";
            case ACCOUNT_ARCHIVED -> "ACCOUNT_ARCHIVED";
            case ROLE_ASSIGNED -> "ROLE_ASSIGNED";
            case ROLE_REVOKED -> "ROLE_REVOKED";
        };

        AuditEvent auditEvent = new AuditEvent(Instant.now(), actorUserId, action,
                "IDENTITY", "USER_ACCOUNT", "SUCCESS");
        // Parcours d'invitation : le compte concerne est aussi le sujet
        // (snapshot conserve pour lisibilite apres suppression). Pour les
        // actions d'administration, l'acteur est un tiers : le compte
        // concerne est donc porte par la ressource.
        boolean invitationFlow = event.action() == AccountLifecycleAction.INVITATION_ISSUED
                || event.action() == AccountLifecycleAction.ACCOUNT_ACTIVATED;
        if (invitationFlow) {
            auditEvent.setActorPublicIdSnapshot(event.userPublicId());
        } else {
            auditEvent.setResourcePublicId(event.userPublicId());
        }
        if (event.detail() != null) {
            auditEvent.setReason(event.detail());
        }
        auditEventRepository.save(auditEvent);
    }
}
