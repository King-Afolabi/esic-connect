package com.esic.connect.audit.internal;

import com.esic.connect.enrollment.EnrollmentChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Trace les changements du module {@code enrollment} (profil apprenant,
 * inscription) dans {@code audit_event} — cahier §30.1 (« import
 * d'apprenants », changements de classe). Aucune dépendance vers les
 * classes internes du module {@code enrollment} (docs/03 §6.6, vérifié
 * par Spring Modulith).
 *
 * <p>Transaction dédiée ({@code REQUIRES_NEW}) : un incident d'écriture de
 * l'audit ne compromet pas la transaction métier appelante. Aucun jeton
 * ni donnée personnelle : l'événement ne transporte que des identifiants,
 * l'action et un complément non sensible (codes fonctionnels — jamais de
 * numéro étudiant, de nom ni d'adresse).
 */
@Component
public class EnrollmentAuditListener {

    private final AuditEventRepository auditEventRepository;

    public EnrollmentAuditListener(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEnrollmentChange(EnrollmentChangeEvent event) {
        String action = event.resourceType().name() + "_" + event.action().name();
        AuditEvent auditEvent = new AuditEvent(Instant.now(), event.actorUserId(), action,
                "ENROLLMENT", event.resourceType().name(), "SUCCESS");
        auditEvent.setResourcePublicId(event.resourcePublicId());
        if (event.detail() != null) {
            auditEvent.setReason(event.detail());
        }
        auditEventRepository.save(auditEvent);
    }
}
