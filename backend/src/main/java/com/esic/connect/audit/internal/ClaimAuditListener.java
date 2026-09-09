package com.esic.connect.audit.internal;

import com.esic.connect.claim.ClaimChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Trace les opérations du module {@code claim} dans {@code audit_event} —
 * cahier §23.1 (« transfert de réclamation »).
 *
 * <p>L'événement ne transporte <strong>ni sujet, ni corps de message</strong> :
 * ce sont des données personnelles, que l'audit métier ne conserve pas
 * (§23.3). Seuls l'identifiant de la réclamation, l'action et un
 * complément non sensible (guichet, statut) sont écrits.
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
public class ClaimAuditListener {

    private final AuditRecorder recorder;

    public ClaimAuditListener(AuditRecorder recorder) {
        this.recorder = recorder;
    }

    @EventListener
    public void onClaimChange(ClaimChangeEvent event) {
        recorder.record(AuditIntent
                .of(Instant.now(), event.actorInternalId(), "CLAIM_" + event.action().name(),
                        "CLAIM", "CLAIM", "SUCCESS")
                .withResource(event.claimPublicId())
                .withReason(event.detail()));
    }
}
