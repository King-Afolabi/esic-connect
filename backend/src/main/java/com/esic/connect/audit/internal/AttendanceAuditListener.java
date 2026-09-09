package com.esic.connect.audit.internal;

import com.esic.connect.attendance.AttendanceChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Trace les émargements du module {@code attendance} dans
 * {@code audit_event} — cahier §30.1 (« correction d'une présence »,
 * « ajout manuel » ; ici l'enregistrement initial d'une présence).
 * Aucune dépendance vers les classes internes de {@code attendance}.
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
public class AttendanceAuditListener {

    private final AuditRecorder recorder;

    public AttendanceAuditListener(AuditRecorder recorder) {
        this.recorder = recorder;
    }

    @EventListener
    public void onAttendanceChange(AttendanceChangeEvent event) {
        recorder.record(AuditIntent
                .of(Instant.now(), event.actorUserId(), "ATTENDANCE_" + event.action().name(),
                        "ATTENDANCE", event.resourceType().name(), "SUCCESS")
                .withResource(event.resourcePublicId())
                .withReason(event.detail()));
    }
}
