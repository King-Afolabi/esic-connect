package com.esic.connect.planning.internal;

import com.esic.connect.planning.internal.PlanningPublicationService.PublicationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Orchestre la publication d'un job de planning en séparant strictement
 * les transactions (DEC-G1-003) :
 *
 * <ol>
 *   <li>{@link PlanningPublicationService#publish} — <strong>une</strong>
 *       transaction atomique ; toute exception fait rollback l'ensemble
 *       (aucune version, aucune séance publiée) ;</li>
 *   <li>un <strong>conflit métier attendu</strong> ({@link PlanningException})
 *       est propagé tel quel → {@code ProblemDetail} contrôlé, le job
 *       reste {@code SIMULATED}, republiable ;</li>
 *   <li>un <strong>échec inattendu</strong> (le port {@code coursesession}
 *       lève, ou toute autre {@code RuntimeException}) : la transaction de
 *       publication a déjà rollback ; l'orchestrateur écrit alors
 *       {@code status = FAILED} + une issue explicative dans une
 *       transaction {@code REQUIRES_NEW} <strong>distincte, sans aucune
 *       donnée métier publiée</strong>, puis lève
 *       {@link PlanningException.Kind#PUBLICATION_FAILED}.</li>
 * </ol>
 */
@Component
class PlanningPublicationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PlanningPublicationOrchestrator.class);

    private final PlanningPublicationService publicationService;
    private final PlanningPublicationFailureRecorder failureRecorder;

    PlanningPublicationOrchestrator(PlanningPublicationService publicationService,
                                    PlanningPublicationFailureRecorder failureRecorder) {
        this.publicationService = publicationService;
        this.failureRecorder = failureRecorder;
    }

    PublicationResult publish(UUID jobPublicId, Long requesterInternalId, boolean globalScope) {
        try {
            return publicationService.publish(jobPublicId, requesterInternalId, globalScope);
        } catch (PlanningException businessConflict) {
            throw businessConflict;
        } catch (RuntimeException unexpected) {
            // La transaction de publication (REQUIRES_NEW) a rollback :
            // aucune version, aucune séance, aucune entrée. On journalise
            // sans PII et on marque le job FAILED dans une transaction
            // distincte (bean séparé, sinon l'auto-invocation contourne
            // le proxy transactionnel).
            log.warn("Publication du planning {} échouée ({}) — job marqué FAILED",
                    jobPublicId, unexpected.getClass().getSimpleName());
            failureRecorder.markFailed(jobPublicId, unexpected.getClass().getSimpleName());
            throw new PlanningException(PlanningException.Kind.PUBLICATION_FAILED);
        }
    }
}
