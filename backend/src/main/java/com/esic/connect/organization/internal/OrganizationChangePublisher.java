package com.esic.connect.organization.internal;

import com.esic.connect.identity.CurrentUserResolver;
import com.esic.connect.organization.OrganizationChangeAction;
import com.esic.connect.organization.OrganizationChangeEvent;
import com.esic.connect.organization.OrganizationResourceType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Résout l'auteur courant (via le port {@link CurrentUserResolver}) et
 * publie les {@link OrganizationChangeEvent} consommés par le module
 * {@code audit}. Mutualise ce que feraient sinon les quatre services.
 */
@Component
class OrganizationChangePublisher {

    private final CurrentUserResolver currentUserResolver;
    private final ApplicationEventPublisher eventPublisher;

    OrganizationChangePublisher(CurrentUserResolver currentUserResolver,
                                ApplicationEventPublisher eventPublisher) {
        this.currentUserResolver = currentUserResolver;
        this.eventPublisher = eventPublisher;
    }

    /** Identifiant interne de l'appelant, ou {@code null} si non résolu. */
    Long actorId(String callerSubject) {
        return currentUserResolver.resolveInternalId(callerSubject).orElse(null);
    }

    void publish(OrganizationResourceType type, UUID resourcePublicId, OrganizationChangeAction action,
                 Long actorId, String detail) {
        eventPublisher.publishEvent(new OrganizationChangeEvent(type, resourcePublicId, actorId, action, detail));
    }
}
