package com.esic.connect.coursesession.internal;

import com.esic.connect.coursesession.SessionLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Fermeture automatique périodique des séances {@code OPEN} restées sans
 * intervention humaine au-delà de leur délai de grâce (Lot 9 ; RG. §16).
 *
 * <p><strong>Fréquence technique ≠ délai métier.</strong> Ce planificateur
 * ne fait que déclencher un <em>balayage</em> à intervalle régulier
 * ({@code app.coursesession.auto-close.check-interval-ms}, défaut 2 min) ;
 * le délai de grâce métier après {@code endsAt}
 * ({@code app.coursesession.auto-close.grace-period}, défaut 15 min,
 * {@link CourseSessionAutoCloseProperties}) est un réglage entièrement
 * distinct.
 *
 * <p><strong>Deux beans, pas un seul.</strong> La transaction par séance
 * est portée par {@link CourseSessionAutoCloseService#closeIfStillEligible},
 * un bean <em>distinct</em> de celui-ci. La gestion transactionnelle
 * déclarative de Spring repose sur un proxy : un appel {@code this.xxx()}
 * depuis la même classe ne passe jamais par ce proxy et n'ouvre donc
 * aucune transaction. Découper en deux beans est la seule façon de
 * garantir qu'une transaction indépendante encadre chaque séance.
 *
 * <p>Chaque séance est traitée indépendamment : l'échec de l'une (course
 * concurrente, verrou optimiste, etc.) est journalisé puis n'empêche
 * jamais le traitement des suivantes (pas de panne de lot).
 */
@Component
@ConditionalOnProperty(prefix = "app.coursesession.auto-close", name = "enabled", havingValue = "true",
        matchIfMissing = true)
class CourseSessionAutoCloseScheduler {

    private static final Logger log = LoggerFactory.getLogger(CourseSessionAutoCloseScheduler.class);

    private final CourseSessionRepository sessionRepository;
    private final CourseSessionAutoCloseService autoCloseService;
    private final CourseSessionAutoCloseProperties properties;
    private final Clock clock;

    CourseSessionAutoCloseScheduler(CourseSessionRepository sessionRepository,
                                    CourseSessionAutoCloseService autoCloseService,
                                    CourseSessionAutoCloseProperties properties,
                                    Clock clock) {
        this.sessionRepository = sessionRepository;
        this.autoCloseService = autoCloseService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.coursesession.auto-close.check-interval-ms:120000}")
    void closeOverdueSessions() {
        Instant now = clock.instant();
        Pageable batch = PageRequest.of(0, properties.batchSize());
        List<CourseSession> candidates = sessionRepository.findAutoCloseCandidates(SessionLifecycle.OPEN, now, batch);
        if (candidates.isEmpty()) {
            return;
        }
        int closed = 0;
        int failed = 0;
        for (CourseSession candidate : candidates) {
            long id = candidate.getId();
            try {
                if (autoCloseService.closeIfStillEligible(id)) {
                    closed++;
                }
            } catch (RuntimeException failure) {
                // Séance individuelle en échec (course concurrente, verrou
                // optimiste…) : journalisée, jamais fatale pour le lot.
                failed++;
                log.warn("Fermeture automatique : échec sur la séance interne {} ({})",
                        id, failure.getClass().getSimpleName(), failure);
            }
        }
        log.info("Fermeture automatique : {} candidate(s), {} fermée(s), {} échec(s)",
                candidates.size(), closed, failed);
    }
}
