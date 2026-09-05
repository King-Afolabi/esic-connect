package com.esic.connect.claim.internal;

import com.esic.connect.claim.ClaimChangeAction;
import com.esic.connect.claim.ClaimChangeEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Publie les changements de réclamation pour la piste d'audit
 * <strong>et</strong> pour la notification (T-12).
 */
@Component
class ClaimChangePublisher {

    private final ApplicationEventPublisher eventPublisher;

    ClaimChangePublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    void publish(UUID claimPublicId, ClaimChangeAction action, Long actorInternalId, String detail) {
        publish(claimPublicId, action, actorInternalId, detail, Set.of());
    }

    /**
     * @param recipients comptes à prévenir. La clé d'occurrence est tirée
     *                   ici : les écouteurs sont synchrones, un événement
     *                   n'est livré qu'une fois, et cette clé sert
     *                   l'idempotence des effets de bord en aval.
     */
    void publish(UUID claimPublicId, ClaimChangeAction action, Long actorInternalId, String detail,
                 Set<UUID> recipients) {
        eventPublisher.publishEvent(new ClaimChangeEvent(claimPublicId, action, actorInternalId,
                detail, UUID.randomUUID(), recipients));
    }
}
