package com.esic.connect.audit.internal;

import com.esic.connect.alternation.AlternationChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Trace les changements du module {@code alternation} (modèle de rythme,
 * affectation de classe, exception individuelle) dans {@code audit_event}
 * — cahier §30.1 (une exception au calendrier « doit être auditée »,
 * docs/02 §8.4). Aucune dépendance vers les classes internes du module
 * {@code alternation} (docs/03 §6.6, vérifié par Spring Modulith).
 *
 * <p>Transaction dédiée ({@code REQUIRES_NEW}) : un incident d'écriture de
 * l'audit ne compromet pas la transaction métier appelante. Aucun jeton
 * ni donnée personnelle : l'événement ne transporte que des identifiants
 * publics, l'action et un complément non sensible (codes fonctionnels,
 * types).
 */
@Component
public class AlternationAuditListener {

    private final AuditEventRepository auditEventRepository;

    public AlternationAuditListener(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAlternationChange(AlternationChangeEvent event) {
        String action = event.resourceType().name() + "_" + event.action().name();
        AuditEvent auditEvent = new AuditEvent(Instant.now(), event.actorUserId(), action,
                "ALTERNATION", event.resourceType().name(), "SUCCESS");
        auditEvent.setResourcePublicId(event.resourcePublicId());
        if (event.detail() != null) {
            auditEvent.setReason(event.detail());
        }
        auditEventRepository.save(auditEvent);
    }
}
