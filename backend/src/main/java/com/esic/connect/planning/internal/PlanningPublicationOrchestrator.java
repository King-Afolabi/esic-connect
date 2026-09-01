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
 *   <li>une <strong>course concurrente idempotente</strong> perdue
 *       (verrou pessimiste, {@code ObjectOptimisticLockingFailureException},
 *       violation d'unicité de version…) : si une autre requête a
 *       <em>déjà</em> publié le job, l'orchestrateur renvoie le
 *       <strong>résultat idempotent</strong> — jamais {@code FAILED}
 *       (audit G1-B.1) ;</li>
 *   <li>un <strong>échec inattendu</strong> réel (le port {@code coursesession}
 *       lève, ou toute autre {@code RuntimeException} sans job publié) :
 *       la transaction de publication a déjà rollback ; l'orchestrateur
 *       écrit alors {@code status = FAILED} + une issue explicative dans
 *       une transaction {@code REQUIRES_NEW} <strong>distincte, sans aucune
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
            // aucune version, aucune séance, aucune entrée pour CETTE
            // requête. Avant de marquer FAILED, vérifier si une requête
            // CONCURRENTE a déjà publié ce job (course idempotente perdue
            // sur un verrou / un lock optimiste / une violation d'unicité
            // de numéro de version). Le cas échéant, renvoyer le résultat
            // idempotent — ne JAMAIS marquer FAILED (audit G1-B.1).
            var alreadyPublished = publicationService.alreadyPublishedResult(jobPublicId);
            if (alreadyPublished.isPresent()) {
                log.info("Publication du planning {} : course concurrente perdue ({}) — "
                                + "job déjà PUBLISHED, résultat idempotent renvoyé",
                        jobPublicId, unexpected.getClass().getSimpleName());
                return alreadyPublished.get();
            }
            // Échec réel : aucune donnée publiée. On journalise sans PII et
            // on marque le job FAILED dans une transaction distincte (bean
            // séparé, sinon l'auto-invocation contourne le proxy
            // transactionnel).
            log.warn("Publication du planning {} échouée ({}) — job marqué FAILED",
                    jobPublicId, unexpected.getClass().getSimpleName());
            failureRecorder.markFailed(jobPublicId, unexpected.getClass().getSimpleName());
            throw new PlanningException(PlanningException.Kind.PUBLICATION_FAILED);
        }
    }
}
