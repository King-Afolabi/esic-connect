package com.esic.connect.planning.internal;

import com.esic.connect.identity.CurrentUserResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Résout l'auteur courant pour les écritures et l'événement de
 * publication du module {@code planning}. Le sujet ({@code sub}) du JWT
 * <em>est</em> l'identifiant public du compte ; l'identifiant interne
 * passe par le port {@link CurrentUserResolver}.
 */
@Component
class PlanningChangePublisher {

    private final CurrentUserResolver currentUserResolver;

    PlanningChangePublisher(CurrentUserResolver currentUserResolver) {
        this.currentUserResolver = currentUserResolver;
    }

    Long currentActorInternalId() {
        return currentUserResolver.resolveInternalId(currentSubject()).orElse(null);
    }

    UUID currentActorPublicId() {
        String subject = currentSubject();
        if (subject == null) {
            return null;
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    private static String currentSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }
}
