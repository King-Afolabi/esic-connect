package com.esic.connect.audit.internal;

import com.esic.connect.coursesession.AttendanceCheckpointChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Trace le cycle de vie des points de contrôle d'émargement (V10) dans
 * {@code audit_event} — cahier §30.1 (« ouverture et clôture de
 * séance » ; ici au grain du point de contrôle). Catégorie
 * {@code COURSE_SESSION}, ressource {@code ATTENDANCE_CHECKPOINT}. Aucune
 * dépendance vers les classes internes de {@code coursesession}.
 *
 * <p><strong>Écriture par l'outbox transactionnelle</strong> (EF-AUD-003 ;
 * docs/02 §23.4). Cet écouteur est un {@link EventListener} synchrone
 * <em>sans</em> {@code REQUIRES_NEW} : il rejoint la transaction métier et
 * n'écrit rien lui-même. Il enregistre l'intention via
 * {@link AuditRecorder} ; la ligne d'outbox commite avec l'action — ou
 * disparaît avec son annulation (RG-097, AC-027) — et le diffuseur écrit
 * la trace après commit, avec reprise garantie (RG-096).
 *
 * <p>Le motif précédent ({@code REQUIRES_NEW}) écrivait la trace dans une
 * transaction séparée ouverte AVANT le commit métier : une action ensuite
 * annulée laissait sa trace de succès, et un incident d'écriture perdait
 * la trace en silence.
 */
@Component
public class AttendanceCheckpointAuditListener {

    private final AuditRecorder recorder;

    public AttendanceCheckpointAuditListener(AuditRecorder recorder) {
        this.recorder = recorder;
    }

    @EventListener
    public void onCheckpointChange(AttendanceCheckpointChangeEvent event) {
        String detail = "session=" + event.sessionPublicId()
                + (event.detail() != null ? ";" + event.detail() : "");
        recorder.record(AuditIntent
                .of(Instant.now(), event.actorUserId(), "CHECKPOINT_" + event.action().name(),
                        "COURSE_SESSION", "ATTENDANCE_CHECKPOINT", "SUCCESS")
                .withResource(event.checkpointPublicId())
                .withReason(detail));
    }
}
