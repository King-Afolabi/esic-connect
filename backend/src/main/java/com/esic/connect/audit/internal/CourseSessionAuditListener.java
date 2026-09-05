package com.esic.connect.audit.internal;

import com.esic.connect.coursesession.CourseSessionChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Trace les changements du module {@code coursesession} (création,
 * ouverture, fermeture, <strong>annulation</strong> d'une séance,
 * <strong>ajout / fin d'un remplacement</strong>) dans
 * {@code audit_event} — cahier §30.1 (« ouverture et clôture de séance »,
 * « remplacement »). Aucune dépendance vers les classes internes de
 * {@code coursesession} (docs/03 §6.6, vérifié par Spring Modulith).
 *
 * <p><strong>Écriture par l'outbox transactionnelle</strong> (EF-AUD-003).
 * Cet écouteur était en {@code AFTER_COMMIT} — ce qui tenait déjà RG-097,
 * mais laissait la trace perdue si l'écriture échouait ensuite. Il rejoint
 * désormais la transaction métier et enregistre l'intention via
 * {@link AuditRecorder} : l'atomicité est vraie des deux côtés, et le
 * diffuseur garantit la reprise (RG-096).
 */
@Component
public class CourseSessionAuditListener {

    private final AuditRecorder recorder;

    public CourseSessionAuditListener(AuditRecorder recorder) {
        this.recorder = recorder;
    }

    @EventListener
    public void onCourseSessionChange(CourseSessionChangeEvent event) {
        recorder.record(AuditIntent
                .of(Instant.now(), event.actorUserId(), "SESSION_" + event.action().name(),
                        "COURSE_SESSION", event.resourceType().name(), "SUCCESS")
                .withResource(event.resourcePublicId())
                .withReason(event.detail()));
    }
}
