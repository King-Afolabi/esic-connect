package com.esic.connect.enrollment.internal;

import com.esic.connect.enrollment.EnrollmentChangeAction;
import com.esic.connect.enrollment.EnrollmentChangeEvent;
import com.esic.connect.enrollment.EnrollmentResourceType;
import com.esic.connect.identity.CurrentUserResolver;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Résout l'auteur courant (via le port {@link CurrentUserResolver}) et
 * publie les {@link EnrollmentChangeEvent} consommés par le module
 * {@code audit}. Mutualise ce que feraient sinon les deux services.
 * Aligné sur {@code academic.internal.AcademicChangePublisher}.
 */
@Component
class EnrollmentChangePublisher {

    private final CurrentUserResolver currentUserResolver;
    private final ApplicationEventPublisher eventPublisher;

    EnrollmentChangePublisher(CurrentUserResolver currentUserResolver,
                              ApplicationEventPublisher eventPublisher) {
        this.currentUserResolver = currentUserResolver;
        this.eventPublisher = eventPublisher;
    }

    /** Identifiant interne de l'appelant, ou {@code null} si non résolu. */
    Long actorId(String callerSubject) {
        return currentUserResolver.resolveInternalId(callerSubject).orElse(null);
    }

    void publish(EnrollmentResourceType type, UUID resourcePublicId, EnrollmentChangeAction action,
                 Long actorId, String detail) {
        eventPublisher.publishEvent(new EnrollmentChangeEvent(type, resourcePublicId, actorId, action, detail));
    }
}
