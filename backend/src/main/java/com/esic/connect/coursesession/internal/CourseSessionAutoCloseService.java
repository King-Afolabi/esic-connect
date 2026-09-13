package com.esic.connect.coursesession.internal;

import com.esic.connect.coursesession.CourseSessionChangeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Fermeture automatique <strong>d'une seule</strong> séance {@code OPEN}
 * (Lot 9), dans sa propre transaction — bean distinct de
 * {@link CourseSessionAutoCloseScheduler} pour que cet appel passe
 * réellement par le proxy transactionnel de Spring (l'auto-invocation
 * d'une méthode {@code @Transactional} du même bean ne déclenche pas
 * l'interception : voir la note de {@link CourseSessionAutoCloseScheduler}).
 *
 * <p>Revérifie l'éligibilité précisément ici, dans la transaction : le
 * candidat sélectionné par le planificateur n'est qu'une présélection
 * large et sûre (voir {@code CourseSessionRepository.findAutoCloseCandidates}).
 * Une séance déjà fermée / annulée entre la sélection et cet appel, ou
 * dont le délai de grâce n'est en réalité pas encore écoulé, est
 * silencieusement ignorée — ce n'est jamais une erreur.
 *
 * <p>Jamais d'acteur humain : {@code actorId = null}, action
 * {@link CourseSessionChangeAction#AUTO_CLOSED}.
 */
@Service
class CourseSessionAutoCloseService {

    private static final Logger log = LoggerFactory.getLogger(CourseSessionAutoCloseService.class);

    private final CourseSessionRepository sessionRepository;
    private final CourseSessionService sessionService;
    private final CourseSessionAutoCloseProperties properties;
    private final Clock clock;

    CourseSessionAutoCloseService(CourseSessionRepository sessionRepository,
                                  CourseSessionService sessionService,
                                  CourseSessionAutoCloseProperties properties,
                                  Clock clock) {
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @return {@code true} si la séance a réellement été fermée ici,
     *         {@code false} si elle a été ignorée (déjà traitée,
     *         annulée, ou pas encore éligible)
     */
    @Transactional
    boolean closeIfStillEligible(long sessionInternalId) {
        CourseSession session = sessionRepository.findById(sessionInternalId).orElse(null);
        if (session == null) {
            log.debug("Fermeture automatique : séance {} introuvable (supprimée ?), ignorée", sessionInternalId);
            return false;
        }
        if (!session.isOpen()) {
            // Fermée ou annulée manuellement entre la sélection et cet
            // appel : plus rien à faire, ce n'est pas une anomalie.
            log.debug("Fermeture automatique : séance {} déjà {} depuis la sélection, ignorée",
                    session.getPublicId(), session.getStatus());
            return false;
        }
        Instant now = clock.instant();
        if (session.getEndsAt().plus(properties.gracePeriod()).isAfter(now)) {
            // Sélectionnée large par le planificateur (endsAt <= now),
            // mais son délai de grâce propre n'est pas encore écoulé :
            // jamais fermée avant son propre délai.
            log.debug("Fermeture automatique : séance {} pas encore éligible (délai de grâce en cours), ignorée",
                    session.getPublicId());
            return false;
        }
        sessionService.closeCore(session, now, null, CourseSessionChangeAction.AUTO_CLOSED);
        log.debug("Fermeture automatique : séance {} fermée", session.getPublicId());
        return true;
    }
}
