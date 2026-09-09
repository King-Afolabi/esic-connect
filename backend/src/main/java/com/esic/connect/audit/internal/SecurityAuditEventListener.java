package com.esic.connect.audit.internal;

import com.esic.connect.identity.LoginFailedEvent;
import com.esic.connect.identity.LoginSucceededEvent;
import com.esic.connect.identity.MfaChangedEvent;
import com.esic.connect.identity.PasswordChangedEvent;
import com.esic.connect.identity.SessionsRevokedEvent;
import com.esic.connect.identity.TrustedDeviceChangedEvent;
import com.esic.connect.identity.WebAuthnCredentialChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Traduit les événements de sécurité du module {@code identity} en
 * {@code audit_event} (docs/04-modele-donnees.md §24), sans dépendance
 * directe vers les classes internes d'identity (docs/03 §6.6).
 *
 * <p><strong>Écriture par l'outbox transactionnelle</strong> (EF-AUD-003).
 * Tous ces écouteurs rejoignent la transaction courante et enregistrent
 * l'intention via {@link AuditRecorder}. Deux conséquences heureuses :
 * une opération annulée ne laisse plus de trace de succès (RG-097), et le
 * verrou que prenait l'ancien motif disparaît — la ligne d'outbox ne
 * porte aucune clé étrangère vers {@code user_account}, contrairement à
 * {@code audit_event.actor_user_id}. C'est ce verrou qui obligeait
 * jusqu'ici le changement de mot de passe et la révocation de sessions à
 * écouter en {@code AFTER_COMMIT}, sous peine d'attendre indéfiniment un
 * verrou que seule la transaction suspendue pouvait libérer.
 */
@Component
public class SecurityAuditEventListener {

    private final AuditRecorder recorder;

    public SecurityAuditEventListener(AuditRecorder recorder) {
        this.recorder = recorder;
    }

    @EventListener
    public void onLoginSucceeded(LoginSucceededEvent event) {
        recorder.record(AuditIntent
                .of(Instant.now(), event.userId(), "LOGIN_SUCCESS", "SECURITY", "USER_ACCOUNT", "SUCCESS")
                .withActorSnapshot(event.userPublicId(), event.displaySnapshot(), event.roleSnapshot()));
    }

    /**
     * Changement de mot de passe (docs/02 §23.1). Le mot de passe, ancien
     * comme nouveau, n'apparaît évidemment jamais : seuls l'auteur,
     * l'instant et l'origine sont conservés.
     */
    @EventListener
    public void onPasswordChanged(PasswordChangedEvent event) {
        recorder.record(AuditIntent
                .of(Instant.now(), event.userId(), "PASSWORD_CHANGED", "SECURITY", "USER_ACCOUNT", "SUCCESS")
                .withActorSnapshot(event.userPublicId(), event.displaySnapshot(), null)
                .withResource(event.userPublicId())
                .withReason(event.origin()));
    }

    /** Révocation globale des sessions d'un compte (EF-AUTH-014). */
    @EventListener
    public void onSessionsRevoked(SessionsRevokedEvent event) {
        recorder.record(AuditIntent
                .of(Instant.now(), event.userId(), "SESSIONS_REVOKED", "SECURITY", "USER_ACCOUNT", "SUCCESS")
                .withActorSnapshot(event.userPublicId(), event.displaySnapshot(), null)
                .withResource(event.userPublicId())
                .withReason(event.reason()));
    }

    /**
     * Changement de second facteur (docs/02 §23.1 : « ajout ou
     * suppression d'un facteur ou d'une passkey »). Aucun secret partagé,
     * aucun code : seule la nature du changement est conservée.
     */
    @EventListener
    public void onMfaChanged(MfaChangedEvent event) {
        record(event.userId(), event.userPublicId(), "MFA_" + event.action().name());
    }

    /** Ajout ou révocation d'une passkey (EF-AUTH-006, RG-008). */
    @EventListener
    public void onWebAuthnCredentialChanged(WebAuthnCredentialChangedEvent event) {
        record(event.userId(), event.userPublicId(), "PASSKEY_" + event.action().name());
    }

    /**
     * Ajout ou révocation d'un appareil de confiance (EF-AUTH-013).
     * Aucune empreinte d'appareil ni adresse réseau n'est écrite : elles
     * n'ont pas leur place dans l'audit métier (RG-094).
     */
    @EventListener
    public void onTrustedDeviceChanged(TrustedDeviceChangedEvent event) {
        record(event.userId(), event.userPublicId(), "TRUSTED_DEVICE_" + event.action().name());
    }

    /**
     * Échec de connexion.
     *
     * <p><strong>Seul écouteur d'audit qui conserve un
     * {@code REQUIRES_NEW}, et c'est délibéré.</strong> La transaction de
     * connexion est <em>toujours</em> annulée quand l'authentification
     * échoue : elle relance l'exception après avoir publié cet événement.
     * Rejoindre cette transaction ferait disparaître l'enregistrement avec
     * elle, et une tentative infructueuse — donc l'essentiel de ce qu'un
     * responsable sécurité cherche dans le journal — ne serait jamais
     * tracée.
     *
     * <p>Ce n'est pas une entorse à RG-097 : cette règle interdit de
     * produire l'effet de bord d'une action <em>annulée</em>. Ici, rien
     * n'est annulé — le refus a bien eu lieu, et c'est lui qu'on trace.
     * L'intention passe malgré tout par l'outbox : la transaction dédiée
     * ne sert qu'à lui donner un commit propre, indépendant du rollback
     * de la connexion.
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLoginFailed(LoginFailedEvent event) {
        recorder.record(AuditIntent
                .of(Instant.now(), event.userId(), "LOGIN_FAILURE", "SECURITY", "USER_ACCOUNT", "DENIED")
                .withActorSnapshot(event.userPublicId(), event.displaySnapshot(), null)
                .withReason(event.reasonCode()));
    }

    private void record(Long userId, UUID userPublicId, String action) {
        recorder.record(AuditIntent
                .of(Instant.now(), userId, action, "SECURITY", "USER_ACCOUNT", "SUCCESS")
                .withActorSnapshot(userPublicId, null, null)
                .withResource(userPublicId));
    }
}
