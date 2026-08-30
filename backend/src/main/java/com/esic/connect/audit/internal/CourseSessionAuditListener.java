package com.esic.connect.audit.internal;

import com.esic.connect.coursesession.CourseSessionChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Trace les changements du module {@code coursesession} (création,
 * ouverture, fermeture d'une séance) dans {@code audit_event} — cahier
 * §30.1 (« ouverture et clôture de séance »). Aucune dépendance vers les
 * classes internes de {@code coursesession} (docs/03 §6.6, vérifié par
 * Spring Modulith).
 *
 * <p>Transaction dédiée ({@code REQUIRES_NEW}) : un incident d'écriture de
 * l'audit ne compromet pas la transaction métier appelante. Aucun jeton
 * ni donnée personnelle : identifiant public de la séance, action et
 * complément non sensible.
 *
 * <p><strong>Dette transactionnelle connue (non résolue dans cette PR).</strong>
 * Comme les autres listeners d'audit du projet, celui-ci est un
 * {@link EventListener} synchrone en {@code REQUIRES_NEW} : la migration
 * globale vers {@code @TransactionalEventListener(AFTER_COMMIT)} /
 * {@code @ApplicationModuleListener} reste à planifier pour tous les
 * modules.
 */
@Component
public class CourseSessionAuditListener {

    private final AuditEventRepository auditEventRepository;

    public CourseSessionAuditListener(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCourseSessionChange(CourseSessionChangeEvent event) {
        String action = "SESSION_" + event.action().name();
        AuditEvent auditEvent = new AuditEvent(Instant.now(), event.actorUserId(), action,
                "COURSE_SESSION", event.resourceType().name(), "SUCCESS");
        auditEvent.setResourcePublicId(event.resourcePublicId());
        if (event.detail() != null) {
            auditEvent.setReason(event.detail());
        }
        auditEventRepository.save(auditEvent);
    }
}
