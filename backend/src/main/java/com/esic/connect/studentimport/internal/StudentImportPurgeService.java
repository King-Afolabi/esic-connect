package com.esic.connect.studentimport.internal;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Purge planifiée des imports (rapport §12.C, §7.6 ; docs/07 §10
 * « nettoyage des données temporaires »). Décisions de prototype :
 *
 * <ul>
 *   <li>jobs {@code SIMULATED} / {@code EXPIRED} dont {@code expires_at}
 *       est dépassé et jobs {@code CANCELLED} plus vieux que
 *       {@code app.import.student.simulation-ttl} → <strong>supprimés</strong>
 *       (la chaîne {@code job_issue} / {@code row} / {@code row_issue}
 *       suit en {@code ON DELETE CASCADE}) ;</li>
 *   <li>jobs {@code APPLIED} confirmés depuis plus de
 *       {@code app.import.student.applied-rows-ttl} → <strong>lignes
 *       filles supprimées</strong>, en-tête et agrégats conservés ;</li>
 *   <li>{@code student_number_sequence} n'est jamais purgée (compteur
 *       monotone par année).</li>
 * </ul>
 *
 * Les données métier créées à la confirmation (comptes, profils,
 * inscriptions, invitations) ne dépendent d'aucune table
 * {@code student_import_*} et ne sont donc jamais touchées.
 */
@Component
class StudentImportPurgeService {

    private final StudentImportProperties properties;
    private final Clock clock;
    private final StudentImportJobRepository jobRepository;
    private final StudentImportRowRepository rowRepository;
    private final StudentImportJobIssueRepository jobIssueRepository;

    StudentImportPurgeService(StudentImportProperties properties,
                              Clock clock,
                              StudentImportJobRepository jobRepository,
                              StudentImportRowRepository rowRepository,
                              StudentImportJobIssueRepository jobIssueRepository) {
        this.properties = properties;
        this.clock = clock;
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.jobIssueRepository = jobIssueRepository;
    }

    /** Exécution quotidienne (par défaut 03:30) — surchargeable par {@code app.import.student.purge-cron}. */
    @Scheduled(cron = "${app.import.student.purge-cron:0 30 3 * * *}")
    void scheduledPurge() {
        purge();
    }

    @Transactional
    PurgeReport purge() {
        Instant now = clock.instant();

        List<StudentImportJob> toDelete = new ArrayList<>(jobRepository.findByStatusInAndExpiresAtBefore(
                List.of(StudentImportJobStatus.SIMULATED, StudentImportJobStatus.EXPIRED), now));
        toDelete.addAll(jobRepository.findByStatusAndCreatedAtBefore(StudentImportJobStatus.CANCELLED,
                now.minus(properties.simulationTtl())));
        jobRepository.deleteAll(toDelete); // CASCADE : job_issue / row / row_issue

        List<StudentImportJob> appliedToTrim = jobRepository.findByStatusAndConfirmedAtBefore(
                StudentImportJobStatus.APPLIED, now.minus(properties.appliedRowsTtl()));
        int rowsTrimmed = 0;
        for (StudentImportJob job : appliedToTrim) {
            long before = rowRepository.countByJobId(job.getId());
            rowRepository.deleteByJob_Id(job.getId());
            jobIssueRepository.deleteByJobId(job.getId());
            rowsTrimmed += (int) before;
        }
        return new PurgeReport(toDelete.size(), appliedToTrim.size(), rowsTrimmed);
    }

    /**
     * @param jobsDeleted            jobs {@code SIMULATED}/{@code EXPIRED}/{@code CANCELLED} supprimés
     * @param appliedJobsTrimmed     jobs {@code APPLIED} dont les lignes filles ont été supprimées
     * @param appliedRowsDeleted     nombre de lignes filles supprimées
     */
    record PurgeReport(int jobsDeleted, int appliedJobsTrimmed, int appliedRowsDeleted) {
    }
}
