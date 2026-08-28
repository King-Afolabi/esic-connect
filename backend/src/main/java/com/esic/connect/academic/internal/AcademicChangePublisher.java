package com.esic.connect.academic.internal;

import com.esic.connect.academic.AcademicChangeAction;
import com.esic.connect.academic.AcademicChangeEvent;
import com.esic.connect.academic.AcademicResourceType;
import com.esic.connect.identity.CurrentUserResolver;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Résout l'auteur courant (via le port {@link CurrentUserResolver}) et
 * publie les {@link AcademicChangeEvent} consommés par le module
 * {@code audit}. Mutualise ce que feraient sinon les cinq services.
 */
@Component
class AcademicChangePublisher {

    private final CurrentUserResolver currentUserResolver;
    private final ApplicationEventPublisher eventPublisher;

    AcademicChangePublisher(CurrentUserResolver currentUserResolver,
                            ApplicationEventPublisher eventPublisher) {
        this.currentUserResolver = currentUserResolver;
        this.eventPublisher = eventPublisher;
    }

    /** Identifiant interne de l'appelant, ou {@code null} si non résolu. */
    Long actorId(String callerSubject) {
        return currentUserResolver.resolveInternalId(callerSubject).orElse(null);
    }

    void publish(AcademicResourceType type, UUID resourcePublicId, AcademicChangeAction action,
                 Long actorId, String detail) {
        eventPublisher.publishEvent(new AcademicChangeEvent(type, resourcePublicId, actorId, action, detail));
    }
}
