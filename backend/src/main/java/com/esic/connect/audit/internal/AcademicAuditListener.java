package com.esic.connect.audit.internal;

import com.esic.connect.academic.AcademicChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Trace les changements du référentiel académique (année scolaire,
 * formation, niveau, promotion, classe) dans {@code audit_event} —
 * cahier §30.1 (« import d'apprenants », « création d'un utilisateur »…
 * relèvent d'autres modules ; les référentiels pédagogiques sont audités
 * ici au même titre que le référentiel organisationnel). Aucune
 * dépendance vers les classes internes du module {@code academic}
 * (docs/03 §6.6, vérifié par Spring Modulith).
 *
 * <p>Transaction dédiée ({@code REQUIRES_NEW}) : un incident d'écriture de
 * l'audit ne compromet pas la transaction métier appelante. Aucun jeton
 * ni donnée personnelle : l'événement ne transporte que des identifiants,
 * l'action et un complément non sensible (code fonctionnel).
 */
@Component
public class AcademicAuditListener {

    private final AuditEventRepository auditEventRepository;

    public AcademicAuditListener(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAcademicChange(AcademicChangeEvent event) {
        String action = event.resourceType().name() + "_" + event.action().name();
        AuditEvent auditEvent = new AuditEvent(Instant.now(), event.actorUserId(), action,
                "ACADEMIC", event.resourceType().name(), "SUCCESS");
        auditEvent.setResourcePublicId(event.resourcePublicId());
        if (event.detail() != null) {
            auditEvent.setReason(event.detail());
        }
        auditEventRepository.save(auditEvent);
    }
}
