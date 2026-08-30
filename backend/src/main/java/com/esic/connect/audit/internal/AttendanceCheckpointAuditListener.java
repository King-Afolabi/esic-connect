package com.esic.connect.audit.internal;

import com.esic.connect.coursesession.AttendanceCheckpointChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Trace le cycle de vie des points de contrôle d'émargement (V10) dans
 * {@code audit_event} — cahier §30.1 (« ouverture et clôture de
 * séance » ; ici au grain du point de contrôle). Catégorie
 * {@code COURSE_SESSION}, ressource {@code ATTENDANCE_CHECKPOINT}. Aucune
 * dépendance vers les classes internes de {@code coursesession}.
 *
 * <p>Transaction dédiée ({@code REQUIRES_NEW}). Aucun jeton ni donnée
 * personnelle : identifiant public du point de contrôle, action et
 * complément non sensible (type, ordre).
 *
 * <p><strong>Dette transactionnelle connue (non résolue dans cette PR).</strong>
 * Comme les autres listeners d'audit du projet, celui-ci est un
 * {@link EventListener} synchrone en {@code REQUIRES_NEW} : la migration
 * globale vers {@code @TransactionalEventListener(AFTER_COMMIT)} reste à
 * planifier pour tous les modules.
 */
@Component
public class AttendanceCheckpointAuditListener {

    private final AuditEventRepository auditEventRepository;

    public AttendanceCheckpointAuditListener(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCheckpointChange(AttendanceCheckpointChangeEvent event) {
        String action = "CHECKPOINT_" + event.action().name();
        AuditEvent auditEvent = new AuditEvent(Instant.now(), event.actorUserId(), action,
                "COURSE_SESSION", "ATTENDANCE_CHECKPOINT", "SUCCESS");
        auditEvent.setResourcePublicId(event.checkpointPublicId());
        String detail = "session=" + event.sessionPublicId()
                + (event.detail() != null ? ";" + event.detail() : "");
        auditEvent.setReason(detail);
        auditEventRepository.save(auditEvent);
    }
}
