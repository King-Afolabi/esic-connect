package com.esic.connect.audit.internal;

import com.esic.connect.coursesession.CourseSessionChangeEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Écriture effective d'une ligne {@code audit_event} pour un changement du
 * module {@code coursesession}, dans une transaction <strong>neuve</strong>
 * ({@code REQUIRES_NEW}).
 *
 * <p>Bean séparé de {@link CourseSessionAuditListener} <em>volontairement</em> :
 * le listener écoute en {@code @TransactionalEventListener(AFTER_COMMIT)}
 * (donc hors de toute transaction) et délègue ici par un appel
 * inter-bean, ce qui garantit que le proxy Spring ouvre bien une nouvelle
 * transaction pour persister la ligne. Un {@code REQUIRES_NEW} posé
 * directement sur la méthode du listener n'aurait aucun effet (auto-appel).
 */
@Component
public class CourseSessionAuditWriter {

    private final AuditEventRepository auditEventRepository;

    public CourseSessionAuditWriter(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(CourseSessionChangeEvent event) {
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
