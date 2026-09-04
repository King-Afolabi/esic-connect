package com.esic.connect.audit.internal;

import com.esic.connect.claim.ClaimChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
 * <p>Transaction dédiée ({@code REQUIRES_NEW}) : un incident d'écriture de
 * l'audit ne compromet pas la transaction métier appelante.
 */
@Component
public class ClaimAuditListener {

    private final AuditEventRepository auditEventRepository;

    public ClaimAuditListener(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onClaimChange(ClaimChangeEvent event) {
        AuditEvent auditEvent = new AuditEvent(Instant.now(), event.actorInternalId(),
                "CLAIM_" + event.action().name(), "CLAIM", "CLAIM", "SUCCESS");
        auditEvent.setResourcePublicId(event.claimPublicId());
        if (event.detail() != null) {
            auditEvent.setReason(event.detail());
        }
        auditEventRepository.save(auditEvent);
    }
}
