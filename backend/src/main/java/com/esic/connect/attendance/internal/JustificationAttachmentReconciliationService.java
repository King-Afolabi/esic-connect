package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.internal.JustificationAttachmentStore.ReconcileOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Réconciliation technique bornée des pièces jointes restées
 * {@code PENDING_STORAGE} (bloc G1-E ; DEC-G1-009 étape 5). Traite le cas
 * d'un arrêt de la JVM entre le commit {@code PENDING_STORAGE} et la
 * bascule {@code STORED}.
 *
 * <ul>
 *   <li>seuil <strong>technique</strong> {@code app.attendance.justification-reconciliation-after}
 *       (défaut {@code PT15M}) — <em>distinct</em> de toute politique de
 *       rétention métier (aucune n'est définie, cf. {@code docs/07}) ;</li>
 *   <li>lot borné ({@code app.attendance.justification-reconciliation-batch},
 *       défaut 100) ;</li>
 *   <li>chaque ligne dans sa propre transaction {@code REQUIRES_NEW}
 *       ({@link JustificationAttachmentFinalizer}) ; une erreur sur une
 *       ligne n'interrompt pas les suivantes ; deux ordonnanceurs
 *       concurrents ne finalisent pas la même ligne (verrou optimiste) ;</li>
 *   <li>journalisation sans donnée personnelle ni chemin.</li>
 * </ul>
 */
@Component
class JustificationAttachmentReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(JustificationAttachmentReconciliationService.class);

    private final JustificationAttachmentRepository repository;
    private final JustificationAttachmentStore store;
    private final Clock clock;
    private final int batchSize;

    JustificationAttachmentReconciliationService(
            JustificationAttachmentRepository repository,
            JustificationAttachmentStore store,
            Clock clock,
            @org.springframework.beans.factory.annotation.Value(
                    "${app.attendance.justification-reconciliation-batch:100}") int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalStateException(
                    "app.attendance.justification-reconciliation-batch doit être strictement positif.");
        }
        this.repository = repository;
        this.store = store;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /** Exécution périodique (défaut toutes les 10 min) — surchargeable. */
    @Scheduled(cron = "${app.attendance.justification-reconciliation-cron:0 */10 * * * *}")
    void scheduledReconcile() {
        reconcile();
    }

    /** Un passage de réconciliation. Renvoie le décompte par issue (pour les tests). */
    Map<ReconcileOutcome, Integer> reconcile() {
        Instant cutoff = clock.instant().minus(store.reconciliationAfter());
        List<JustificationAttachment> stale = repository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                JustificationAttachmentStatus.PENDING_STORAGE, cutoff, PageRequest.of(0, batchSize));

        Map<ReconcileOutcome, Integer> counts = new EnumMap<>(ReconcileOutcome.class);
        for (JustificationAttachment attachment : stale) {
            try {
                ReconcileOutcome outcome = store.reconcileOne(attachment.getId());
                counts.merge(outcome, 1, Integer::sum);
            } catch (RuntimeException failure) {
                // Erreur sur une ligne (verrou optimiste concurrent, E/S) :
                // journalisée sans PII, la ligne suivante est traitée.
                log.warn("Réconciliation d'une pièce jointe échouée : cause={}",
                        failure.getClass().getSimpleName());
            }
        }
        if (!counts.isEmpty()) {
            log.info("Réconciliation des pièces jointes : {}", counts);
        }
        return counts;
    }
}
