package com.esic.connect.audit.internal;

import com.esic.connect.identity.LoginFailedEvent;
import com.esic.connect.identity.LoginSucceededEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Traduit les événements de connexion du module {@code identity} en
 * {@code audit_event} (docs/04-modele-donnees.md §24), sans dépendance
 * directe vers les classes internes d'identity (docs/03 §6.6).
 *
 * Exécuté en transaction dédiée ({@code REQUIRES_NEW}) : un incident
 * d'écriture de l'audit ne doit jamais faire échouer ni altérer la
 * transaction de connexion appelante (voir
 * AuthenticationService#publishSafely, qui absorbe toute exception
 * remontant d'ici sans exposer de détail sensible à l'appelant).
 */
@Component
public class SecurityAuditEventListener {

    private final AuditEventRepository auditEventRepository;

    public SecurityAuditEventListener(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLoginSucceeded(LoginSucceededEvent event) {
        AuditEvent auditEvent = new AuditEvent(Instant.now(), event.userId(), "LOGIN_SUCCESS",
                "SECURITY", "USER_ACCOUNT", "SUCCESS");
        auditEvent.setActorPublicIdSnapshot(event.userPublicId());
        auditEvent.setActorDisplaySnapshot(event.displaySnapshot());
        auditEvent.setActorRole(event.roleSnapshot());
        auditEventRepository.save(auditEvent);
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLoginFailed(LoginFailedEvent event) {
        AuditEvent auditEvent = new AuditEvent(Instant.now(), event.userId(), "LOGIN_FAILURE",
                "SECURITY", "USER_ACCOUNT", "DENIED");
        auditEvent.setActorPublicIdSnapshot(event.userPublicId());
        auditEvent.setActorDisplaySnapshot(event.displaySnapshot());
        auditEvent.setReason(event.reasonCode());
        auditEventRepository.save(auditEvent);
    }
}
