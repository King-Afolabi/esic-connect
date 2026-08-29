package com.esic.connect.alternation.internal;

import com.esic.connect.alternation.AlternationChangeAction;
import com.esic.connect.alternation.AlternationChangeEvent;
import com.esic.connect.alternation.AlternationResourceType;
import com.esic.connect.identity.CurrentUserResolver;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Résout l'auteur courant (via le port {@link CurrentUserResolver}) et
 * publie les {@link AlternationChangeEvent} consommés par le module
 * {@code audit}. Mutualise ce que feraient sinon les trois services.
 * Aligné sur {@code enrollment.internal.EnrollmentChangePublisher}.
 */
@Component
class AlternationChangePublisher {

    private final CurrentUserResolver currentUserResolver;
    private final ApplicationEventPublisher eventPublisher;

    AlternationChangePublisher(CurrentUserResolver currentUserResolver,
                               ApplicationEventPublisher eventPublisher) {
        this.currentUserResolver = currentUserResolver;
        this.eventPublisher = eventPublisher;
    }

    /** Identifiant interne de l'appelant, ou {@code null} si non résolu. */
    Long actorId(String callerSubject) {
        return currentUserResolver.resolveInternalId(callerSubject).orElse(null);
    }

    void publish(AlternationResourceType type, UUID resourcePublicId, AlternationChangeAction action,
                 Long actorId, String detail) {
        eventPublisher.publishEvent(new AlternationChangeEvent(type, resourcePublicId, actorId, action, detail));
    }
}
