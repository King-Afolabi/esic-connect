package com.esic.connect.planning.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Purge planifiée des jobs d'import de planning temporaires (aligné sur
 * {@code studentimport.internal.StudentImportPurgeService}) : un job
 * {@code SIMULATED} dont le TTL est dépassé passe {@code EXPIRED} et ses
 * lignes filles sont supprimées (les {@code planning_import_row_issue}
 * partent en {@code CASCADE}). Les données publiées
 * ({@code planning_schedule} / {@code planning_version} /
 * {@code planning_entry} / {@code course_session}) ne dépendent d'aucune
 * de ces clés et ne sont jamais touchées.
 */
@Component
class PlanningPurgeService {

    private static final Logger log = LoggerFactory.getLogger(PlanningPurgeService.class);

    private final PlanningImportJobRepository jobRepository;
    private final PlanningImportRowRepository rowRepository;
    private final Clock clock;

    PlanningPurgeService(PlanningImportJobRepository jobRepository,
                         PlanningImportRowRepository rowRepository,
                         Clock clock) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.planning.purge-interval-ms:3600000}")
    @Transactional
    public void purgeExpiredSimulations() {
        List<PlanningImportJob> expired = jobRepository.findByStatusAndExpiresAtBefore(
                PlanningImportJobStatus.SIMULATED, clock.instant());
        if (expired.isEmpty()) {
            return;
        }
        for (PlanningImportJob job : expired) {
            job.markExpired();
            jobRepository.save(job);
            rowRepository.deleteByJob_Id(job.getId());
        }
        log.info("Purge planning : {} simulation(s) expirée(s) nettoyée(s)", expired.size());
    }
}
