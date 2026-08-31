package com.esic.connect.studentimport.internal;

import com.esic.connect.studentimport.internal.StudentImportIssueDrafts.RowIssueDraft;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persiste les anomalies <strong>rafraîchies</strong> quand la
 * re-validation d'une confirmation invalide la simulation (rapport §9,
 * §11 : « anomalies rafraîchies persistées », « rien appliqué »).
 *
 * <p>Appelé par {@link StudentImportConfirmationService#confirm} —
 * <strong>hors de toute transaction</strong> — <em>après</em> le retour de
 * {@code runConfirmation}. Cette transaction de confirmation a déjà
 * <strong>commité</strong> (retour normal d'un chemin qui n'a appliqué
 * aucune donnée métier : ni {@code applyRow}, ni {@code markApplied}), et
 * son verrou {@code SELECT … FOR UPDATE} sur le job est relâché. Il n'y a
 * donc plus aucune transaction à suspendre ni aucun verrou à croiser : un
 * simple {@code @Transactional} ({@code REQUIRED}) ouvre ici une
 * transaction neuve, comme le ferait {@code REQUIRES_NEW}, mais
 * <strong>sans</strong> le motif de transaction autonome proscrit par
 * l'invariant T2. Écrit uniquement les tables techniques
 * {@code student_import_*} (jamais de donnée métier). L'appelant lève
 * ensuite {@code STALE_SIMULATION}.
 */
@Component
class StaleRevalidationPersister {

    private final StudentImportJobRepository jobRepository;
    private final StudentImportRowRepository rowRepository;
    private final StudentImportRowIssueRepository rowIssueRepository;

    StaleRevalidationPersister(StudentImportJobRepository jobRepository,
                               StudentImportRowRepository rowRepository,
                               StudentImportRowIssueRepository rowIssueRepository) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.rowIssueRepository = rowIssueRepository;
    }

    @Transactional
    void persist(UUID jobPublicId, Map<Integer, RevalidatedRow> byRowNumber) {
        StudentImportJob job = jobRepository.findByPublicId(jobPublicId)
                .orElseThrow(() -> new IllegalStateException("Job d'import introuvable pour la re-validation."));
        List<StudentImportRow> rows = rowRepository.findByJobIdOrderByRowNumberAsc(job.getId());

        int valid = 0;
        int warning = 0;
        int error = 0;
        int create = 0;
        int update = 0;
        int transfer = 0;
        int noop = 0;
        for (StudentImportRow row : rows) {
            RevalidatedRow revalidated = byRowNumber.get(row.getRowNumber());
            if (revalidated == null) {
                continue;
            }
            row.setRowStatus(revalidated.status());
            row.setPlannedAction(revalidated.action());
            row.setResolution(revalidated.resolvedClassPublicId(), revalidated.resolvedUserPublicId(),
                    revalidated.resolvedEnrollmentPublicId());
            rowIssueRepository.deleteAll(rowIssueRepository.findByRow_IdInOrderByIdAsc(List.of(row.getId())));
            final StudentImportRow persistedRow = row;
            revalidated.issues().forEach(draft -> rowIssueRepository.save(new StudentImportRowIssue(persistedRow,
                    draft.severity(), draft.code(), draft.message(), draft.columnName(), draft.receivedValue(),
                    draft.suggestedValue())));
            rowRepository.save(row);
            switch (revalidated.status()) {
                case VALID -> valid++;
                case WARNING -> warning++;
                case ERROR -> error++;
            }
            switch (revalidated.action()) {
                case CREATE_ACCOUNT_AND_ENROLL -> create++;
                case ENROLL_EXISTING, UPDATE_PROFILE -> update++;
                case TRANSFER_CLASS -> transfer++;
                case NONE -> noop++;
            }
        }
        job.recordStaleRevalidation(valid, warning, error, create, update, transfer, noop);
        jobRepository.save(job);
    }

    /**
     * @param status                     nouveau statut de ligne
     * @param action                     nouvelle action calculée
     * @param resolvedClassPublicId       classe résolue rafraîchie
     * @param resolvedUserPublicId        compte rapproché rafraîchi
     * @param resolvedEnrollmentPublicId  inscription courante rafraîchie
     * @param issues                      anomalies rafraîchies
     */
    record RevalidatedRow(
            StudentImportRowStatus status,
            StudentImportPlannedAction action,
            UUID resolvedClassPublicId,
            UUID resolvedUserPublicId,
            UUID resolvedEnrollmentPublicId,
            List<RowIssueDraft> issues) {
    }
}
