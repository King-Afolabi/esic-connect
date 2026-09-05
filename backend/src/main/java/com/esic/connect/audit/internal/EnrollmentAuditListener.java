package com.esic.connect.audit.internal;

import com.esic.connect.enrollment.EnrollmentChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Trace les changements du module {@code enrollment} (profil apprenant,
 * inscription) dans {@code audit_event} — cahier §30.1 (« import
 * d'apprenants », changements de classe). Aucune dépendance vers les
 * classes internes du module {@code enrollment} (docs/03 §6.6, vérifié
 * par Spring Modulith).
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
public class EnrollmentAuditListener {

    private final AuditRecorder recorder;

    public EnrollmentAuditListener(AuditRecorder recorder) {
        this.recorder = recorder;
    }

    @EventListener
    public void onEnrollmentChange(EnrollmentChangeEvent event) {
        String action = event.resourceType().name() + "_" + event.action().name();
        recorder.record(AuditIntent
                .of(Instant.now(), event.actorUserId(), action, "ENROLLMENT",
                        event.resourceType().name(), "SUCCESS")
                .withResource(event.resourcePublicId())
                .withReason(event.detail()));
    }
}
