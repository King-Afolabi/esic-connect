package com.esic.connect.audit.internal;

import com.esic.connect.studentimport.StudentImportChangeEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * Trace les imports CSV d'apprenants dans {@code audit_event} — cahier
 * §30.1 (« import d'apprenants », « confirmation d'import ») ;
 * rapport §4.4, §10. Aucune dépendance vers les classes internes du
 * module {@code studentimport} (vérifié par Spring Modulith).
 *
 * <p><strong>Déviation volontaire du motif legacy des autres listeners
 * d'audit</strong> (rapport §5 « invariant T5 », §16) : ce listener porte
 * <em>à la fois</em>
 * {@link TransactionalEventListener}{@code (phase = AFTER_COMMIT)}
 * <em>et</em> {@link Transactional}{@code (propagation = REQUIRES_NEW)}.
 *
 * <ul>
 *   <li>{@code AFTER_COMMIT} : le listener n'est invoqué qu'<em>après</em>
 *       le commit réussi de la transaction (de simulation ou de
 *       confirmation) qui a publié l'événement. Si cette transaction
 *       <em>rollback</em>, la phase {@code AFTER_COMMIT} n'est jamais
 *       atteinte : aucun événement n'est traité, <strong>aucune ligne
 *       d'audit</strong> n'est écrite. Une confirmation annulée ne laisse
 *       donc aucune trace.</li>
 *   <li>{@code REQUIRES_NEW} : la transaction métier est déjà terminée
 *       quand {@code AFTER_COMMIT} s'exécute — une transaction neuve est
 *       nécessaire pour persister la ligne {@code audit_event}.</li>
 * </ul>
 *
 * <p>Ce {@code REQUIRES_NEW} est sûr ici, contrairement au motif
 * {@code @EventListener} + {@code REQUIRES_NEW} du reste du projet, parce
 * qu'il ne démarre <strong>jamais</strong> avant le commit métier.
 * L'événement ne transporte que des identifiants et un complément non
 * sensible : ni jeton, ni e-mail, ni nom, ni numéro étudiant, ni valeur
 * de cellule, ni IP.
 */
@Component
public class StudentImportAuditListener {

    private final AuditEventRepository auditEventRepository;

    public StudentImportAuditListener(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStudentImportChange(StudentImportChangeEvent event) {
        String action = "STUDENT_IMPORT_" + event.action().name();
        AuditEvent auditEvent = new AuditEvent(Instant.now(), event.actorUserId(), action,
                "STUDENT_IMPORT", "STUDENT_IMPORT_JOB", "SUCCESS");
        auditEvent.setResourcePublicId(event.jobPublicId());
        if (event.detail() != null) {
            auditEvent.setReason(event.detail());
        }
        auditEventRepository.save(auditEvent);
    }
}
