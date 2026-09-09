package com.esic.connect.audit.internal;

import com.esic.connect.identity.AccountLifecycleAction;
import com.esic.connect.identity.AccountLifecycleEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Trace les etapes du cycle de vie d'un compte (emission d'invitation,
 * activation, suspension, reactivation, archivage, attribution / retrait
 * de role) dans {@code audit_event} — docs/02-cahier-des-charges.md
 * §30.1. Ne recoit jamais de jeton ni de donnee personnelle inutile :
 * l'evenement {@link AccountLifecycleEvent} ne transporte que des
 * identifiants, l'action et un motif non sensible.
 *
 * <p><strong>Écriture par l'outbox transactionnelle</strong> (EF-AUD-003 ;
 * docs/02 §23.4) : cet écouteur rejoint la transaction métier et
 * enregistre l'intention via {@link AuditRecorder}, au lieu d'écrire
 * lui-même dans une transaction séparée. Une suspension annulée ne laisse
 * donc plus de trace de succès (RG-097).
 */
@Component
public class AccountLifecycleAuditListener {

    private final AuditRecorder recorder;

    public AccountLifecycleAuditListener(AuditRecorder recorder) {
        this.recorder = recorder;
    }

    @EventListener
    public void onAccountLifecycle(AccountLifecycleEvent event) {
        boolean selfService = event.action() == AccountLifecycleAction.ACCOUNT_ACTIVATED;
        Long actorUserId = selfService ? event.userId() : event.actorUserId();
        String action = switch (event.action()) {
            case ACCOUNT_CREATED -> "ACCOUNT_CREATED";
            case INVITATION_ISSUED -> "ACCOUNT_INVITATION_ISSUED";
            case ACCOUNT_ACTIVATED -> "ACCOUNT_ACTIVATED";
            case ACCOUNT_SUSPENDED -> "ACCOUNT_SUSPENDED";
            case ACCOUNT_REACTIVATED -> "ACCOUNT_REACTIVATED";
            case ACCOUNT_ARCHIVED -> "ACCOUNT_ARCHIVED";
            case ROLE_ASSIGNED -> "ROLE_ASSIGNED";
            case ROLE_REVOKED -> "ROLE_REVOKED";
        };

        AuditIntent intent = AuditIntent.of(Instant.now(), actorUserId, action,
                "IDENTITY", "USER_ACCOUNT", "SUCCESS");
        // Parcours d'invitation : le compte concerne est aussi le sujet
        // (snapshot conserve pour lisibilite apres suppression). Pour les
        // actions d'administration, l'acteur est un tiers : le compte
        // concerne est donc porte par la ressource.
        boolean invitationFlow = event.action() == AccountLifecycleAction.INVITATION_ISSUED
                || event.action() == AccountLifecycleAction.ACCOUNT_ACTIVATED;
        intent = invitationFlow
                ? intent.withActorSnapshot(event.userPublicId(), null, null)
                : intent.withResource(event.userPublicId());
        recorder.record(intent.withReason(event.detail()));
    }
}
