package com.esic.connect.audit.internal;

import com.esic.connect.identity.LoginFailedEvent;
import com.esic.connect.identity.LoginSucceededEvent;
import com.esic.connect.identity.PasswordChangedEvent;
import com.esic.connect.identity.SessionsRevokedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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

    /**
     * Changement de mot de passe (docs/02 §23.1). Le mot de passe, ancien
     * comme nouveau, n'apparaît évidemment jamais : seuls l'auteur,
     * l'instant et l'origine sont conservés.
     *
     * <p><strong>AFTER_COMMIT et non @EventListener synchrone.</strong>
     * La transaction qui change un mot de passe a déjà modifié — et donc
     * verrouillé — la ligne {@code user_account} concernée. Un écouteur
     * synchrone en {@code REQUIRES_NEW} suspendrait cette transaction pour
     * insérer un {@code audit_event} dont la clé étrangère
     * {@code actor_user_id} pointe vers cette même ligne : la nouvelle
     * transaction attendrait un verrou que seule l'ancienne peut libérer,
     * jusqu'au « lock wait timeout ». Publier après le commit supprime la
     * situation, et donne au passage la bonne sémantique : une transaction
     * annulée ne laisse aucune trace d'audit (RG-097).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPasswordChanged(PasswordChangedEvent event) {
        AuditEvent auditEvent = new AuditEvent(Instant.now(), event.userId(), "PASSWORD_CHANGED",
                "SECURITY", "USER_ACCOUNT", "SUCCESS");
        auditEvent.setActorPublicIdSnapshot(event.userPublicId());
        auditEvent.setActorDisplaySnapshot(event.displaySnapshot());
        auditEvent.setResourcePublicId(event.userPublicId());
        auditEvent.setReason(event.origin());
        auditEventRepository.save(auditEvent);
    }

    /**
     * Révocation globale des sessions d'un compte (EF-AUTH-014). Même
     * raison qu'au-dessus pour l'écoute après commit.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSessionsRevoked(SessionsRevokedEvent event) {
        AuditEvent auditEvent = new AuditEvent(Instant.now(), event.userId(), "SESSIONS_REVOKED",
                "SECURITY", "USER_ACCOUNT", "SUCCESS");
        auditEvent.setActorPublicIdSnapshot(event.userPublicId());
        auditEvent.setActorDisplaySnapshot(event.displaySnapshot());
        auditEvent.setResourcePublicId(event.userPublicId());
        auditEvent.setReason(event.reason());
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
