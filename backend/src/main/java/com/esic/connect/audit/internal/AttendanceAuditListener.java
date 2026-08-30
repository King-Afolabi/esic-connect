package com.esic.connect.audit.internal;

import com.esic.connect.attendance.AttendanceChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Trace les émargements du module {@code attendance} dans
 * {@code audit_event} — cahier §30.1 (« correction d'une présence »,
 * « ajout manuel » ; ici l'enregistrement initial d'une présence).
 * Aucune dépendance vers les classes internes de {@code attendance}.
 *
 * <p>Transaction dédiée ({@code REQUIRES_NEW}). Aucun jeton, code court,
 * numéro étudiant ni nom : seulement l'identifiant public de la présence,
 * l'action et un complément non sensible.
 *
 * <p><strong>Dette transactionnelle connue (non résolue dans cette PR).</strong>
 * Comme les autres listeners d'audit du projet, celui-ci est un
 * {@link EventListener} synchrone en {@code REQUIRES_NEW} : la migration
 * globale vers {@code @TransactionalEventListener(AFTER_COMMIT)} reste à
 * planifier pour tous les modules.
 */
@Component
public class AttendanceAuditListener {

    private final AuditEventRepository auditEventRepository;

    public AttendanceAuditListener(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAttendanceChange(AttendanceChangeEvent event) {
        String action = "ATTENDANCE_" + event.action().name();
        AuditEvent auditEvent = new AuditEvent(Instant.now(), event.actorUserId(), action,
                "ATTENDANCE", event.resourceType().name(), "SUCCESS");
        auditEvent.setResourcePublicId(event.resourcePublicId());
        if (event.detail() != null) {
            auditEvent.setReason(event.detail());
        }
        auditEventRepository.save(auditEvent);
    }
}
