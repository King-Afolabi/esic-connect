package com.esic.connect.planning.internal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Écrit le statut {@code FAILED} d'un job de publication dans une
 * transaction {@code REQUIRES_NEW} <strong>distincte</strong> de la
 * transaction de publication qui a rollback (DEC-G1-003). Bean séparé :
 * l'auto-invocation depuis {@link PlanningPublicationOrchestrator}
 * contournerait le proxy transactionnel.
 */
@Component
class PlanningPublicationFailureRecorder {

    private final PlanningImportJobRepository jobRepository;
    private final PlanningImportJobIssueRepository jobIssueRepository;

    PlanningPublicationFailureRecorder(PlanningImportJobRepository jobRepository,
                                      PlanningImportJobIssueRepository jobIssueRepository) {
        this.jobRepository = jobRepository;
        this.jobIssueRepository = jobIssueRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void markFailed(UUID jobPublicId, String category) {
        jobRepository.findByPublicId(jobPublicId).ifPresent(job -> {
            if (!job.isSimulated()) {
                // Une requête concurrente a déjà publié (ou annulé) ce job :
                // ne pas écraser son état.
                return;
            }
            job.markFailed("Publication interrompue (" + category + ")");
            jobRepository.save(job);
            jobIssueRepository.save(new PlanningImportJobIssue(job, PlanningIssueSeverity.ERROR,
                    "PLAN_PUBLICATION_FAILED",
                    "La publication a été interrompue par une erreur inattendue ; aucun élément n'a été publié.",
                    null));
        });
    }
}
