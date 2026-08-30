package com.esic.connect.coursesession.internal;

import com.esic.connect.coursesession.CourseSessionChangeAction;
import com.esic.connect.coursesession.CourseSessionChangeEvent;
import com.esic.connect.coursesession.CourseSessionResourceType;
import com.esic.connect.identity.CurrentUserResolver;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Résout l'auteur courant (port {@link CurrentUserResolver}) et publie
 * les {@link CourseSessionChangeEvent} consommés par {@code audit} et
 * {@code attendance}. Aligné sur
 * {@code alternation.internal.AlternationChangePublisher}.
 */
@Component
class CourseSessionChangePublisher {

    private final CurrentUserResolver currentUserResolver;
    private final ApplicationEventPublisher eventPublisher;

    CourseSessionChangePublisher(CurrentUserResolver currentUserResolver,
                                 ApplicationEventPublisher eventPublisher) {
        this.currentUserResolver = currentUserResolver;
        this.eventPublisher = eventPublisher;
    }

    Long actorId(String callerSubject) {
        return currentUserResolver.resolveInternalId(callerSubject).orElse(null);
    }

    void publish(UUID sessionPublicId, CourseSessionChangeAction action, Long actorId, String detail) {
        eventPublisher.publishEvent(new CourseSessionChangeEvent(
                CourseSessionResourceType.COURSE_SESSION, sessionPublicId, actorId, action, detail));
    }
}
