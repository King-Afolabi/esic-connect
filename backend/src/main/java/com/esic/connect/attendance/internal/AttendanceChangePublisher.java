package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttendanceChangeAction;
import com.esic.connect.attendance.AttendanceChangeEvent;
import com.esic.connect.attendance.AttendanceResourceType;
import com.esic.connect.identity.CurrentUserResolver;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Résout l'auteur courant (port {@link CurrentUserResolver}) et publie
 * les {@link AttendanceChangeEvent} consommés par {@code audit}. Aligné
 * sur {@code alternation.internal.AlternationChangePublisher}. Aucun
 * jeton, code court, nom, numéro étudiant, IP ni commentaire libre
 * complet dans {@code detail}.
 */
@Component
class AttendanceChangePublisher {

    private final CurrentUserResolver currentUserResolver;
    private final ApplicationEventPublisher eventPublisher;

    AttendanceChangePublisher(CurrentUserResolver currentUserResolver,
                              ApplicationEventPublisher eventPublisher) {
        this.currentUserResolver = currentUserResolver;
        this.eventPublisher = eventPublisher;
    }

    Long actorId(String callerSubject) {
        return currentUserResolver.resolveInternalId(callerSubject).orElse(null);
    }

    void publishRecorded(UUID recordPublicId, Long actorId, String detail) {
        publish(AttendanceResourceType.ATTENDANCE_RECORD, recordPublicId, actorId,
                AttendanceChangeAction.RECORDED, detail);
    }

    void publishRecord(UUID recordPublicId, Long actorId, AttendanceChangeAction action, String detail) {
        publish(AttendanceResourceType.ATTENDANCE_RECORD, recordPublicId, actorId, action, detail);
    }

    void publishJustification(UUID justificationPublicId, Long actorId, AttendanceChangeAction action,
                              String detail) {
        publish(AttendanceResourceType.ATTENDANCE_JUSTIFICATION, justificationPublicId, actorId, action, detail);
    }

    void publishExport(Long actorId, String detail) {
        publish(AttendanceResourceType.ATTENDANCE_EXPORT, null, actorId,
                AttendanceChangeAction.REPORT_EXPORTED, detail);
    }

    private void publish(AttendanceResourceType type, UUID resourcePublicId, Long actorId,
                         AttendanceChangeAction action, String detail) {
        eventPublisher.publishEvent(new AttendanceChangeEvent(type, resourcePublicId, actorId, action, detail));
    }
}
