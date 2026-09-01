package com.esic.connect.audit.internal;

import com.esic.connect.coursesession.CourseSessionChangeEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Trace les changements du module {@code coursesession} (création,
 * ouverture, fermeture, <strong>annulation</strong> d'une séance,
 * <strong>ajout / fin d'un remplacement</strong>) dans
 * {@code audit_event} — cahier §30.1 (« ouverture et clôture de séance »,
 * « remplacement »). Aucune dépendance vers les classes internes de
 * {@code coursesession} (docs/03 §6.6, vérifié par Spring Modulith).
 *
 * <p><strong>Garantie transactionnelle (durcie au checkpoint G1-C.3).</strong>
 * Ce listener est désormais un
 * {@link TransactionalEventListener}{@code (phase = AFTER_COMMIT)} : il
 * n'est invoqué qu'<em>après</em> le commit réussi de la transaction
 * métier qui a publié l'événement. Si cette transaction <em>rollback</em>,
 * la phase {@code AFTER_COMMIT} n'est jamais atteinte — <strong>aucune
 * ligne d'audit de succès n'est écrite</strong> (défaut corrigé : le motif
 * legacy {@code @EventListener} + {@code REQUIRES_NEW} committait l'audit
 * même quand la transaction métier rollbackait ensuite). L'écriture
 * effective est déléguée à {@link CourseSessionAuditWriter} (bean
 * distinct, {@code REQUIRES_NEW}) pour que le proxy Spring ouvre bien une
 * transaction neuve après commit.
 *
 * <p>Ne migre <strong>pas</strong> les autres listeners d'audit du projet
 * (dette connue, documentée dans
 * {@code docs/reports/G1_ARCHITECTURE_DECISIONS.md} §Contexte) : seuls les
 * listeners portant des événements du bloc G1-C sont corrigés ici.
 */
@Component
public class CourseSessionAuditListener {

    private final CourseSessionAuditWriter writer;

    public CourseSessionAuditListener(CourseSessionAuditWriter writer) {
        this.writer = writer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCourseSessionChange(CourseSessionChangeEvent event) {
        writer.write(event);
    }
}
