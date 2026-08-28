package com.esic.connect.audit.internal;

import com.esic.connect.organization.OrganizationChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Trace les changements du référentiel organisationnel (site, bâtiment,
 * salle, plage réseau) dans {@code audit_event} — cahier
 * §30.1 (« modification des plages réseau » y figure explicitement), sans
 * dépendance vers les classes internes du module {@code organization}
 * (docs/03 §6.6, vérifié par Spring Modulith).
 *
 * <p>Transaction dédiée ({@code REQUIRES_NEW}) : un incident d'écriture de
 * l'audit ne compromet pas la transaction métier appelante. Aucun jeton
 * ni donnée personnelle : l'événement ne transporte que des identifiants,
 * l'action et un complément non sensible (code fonctionnel, notation CIDR).
 */
@Component
public class OrganizationAuditListener {

    private final AuditEventRepository auditEventRepository;

    public OrganizationAuditListener(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrganizationChange(OrganizationChangeEvent event) {
        String action = event.resourceType().name() + "_" + event.action().name();
        AuditEvent auditEvent = new AuditEvent(Instant.now(), event.actorUserId(), action,
                "ORGANIZATION", event.resourceType().name(), "SUCCESS");
        auditEvent.setResourcePublicId(event.resourcePublicId());
        if (event.detail() != null) {
            auditEvent.setReason(event.detail());
        }
        auditEventRepository.save(auditEvent);
    }
}
