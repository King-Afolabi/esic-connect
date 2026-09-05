package com.esic.connect.audit.internal;

import com.esic.connect.studentimport.StudentImportChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Trace les imports d'apprenants dans {@code audit_event} — cahier
 * §30.1 (« import d'apprenants », « confirmation d'import »). Aucune
 * dépendance vers les classes internes du module {@code studentimport}
 * (vérifié par Spring Modulith).
 *
 * <p><strong>Écriture par l'outbox transactionnelle</strong> (EF-AUD-003).
 * Cet écouteur était en {@code AFTER_COMMIT} + {@code REQUIRES_NEW} : une
 * confirmation annulée ne laissait déjà aucune trace, mais un incident
 * d'écriture après commit la perdait définitivement. Il rejoint désormais
 * la transaction métier et enregistre l'intention via
 * {@link AuditRecorder}, dont le diffuseur garantit la reprise (RG-096).
 *
 * <p>L'événement ne transporte que des identifiants et un complément non
 * sensible : ni jeton, ni e-mail, ni nom, ni numéro étudiant, ni valeur de
 * cellule, ni adresse IP.
 */
@Component
public class StudentImportAuditListener {

    private final AuditRecorder recorder;

    public StudentImportAuditListener(AuditRecorder recorder) {
        this.recorder = recorder;
    }

    @EventListener
    public void onStudentImportChange(StudentImportChangeEvent event) {
        recorder.record(AuditIntent
                .of(Instant.now(), event.actorUserId(), "STUDENT_IMPORT_" + event.action().name(),
                        "STUDENT_IMPORT", "STUDENT_IMPORT_JOB", "SUCCESS")
                .withResource(event.jobPublicId())
                .withReason(event.detail()));
    }
}
