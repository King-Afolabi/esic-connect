package com.esic.connect.claim.internal;

import com.esic.connect.claim.ClaimChangeAction;
import com.esic.connect.claim.ClaimChangeEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Publie les changements de réclamation pour la piste d'audit. */
@Component
class ClaimChangePublisher {

    private final ApplicationEventPublisher eventPublisher;

    ClaimChangePublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    void publish(UUID claimPublicId, ClaimChangeAction action, Long actorInternalId, String detail) {
        eventPublisher.publishEvent(new ClaimChangeEvent(claimPublicId, action, actorInternalId, detail));
    }
}
