package com.esic.connect.identity.internal;

import com.esic.connect.identity.SessionsRevokedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Révocation globale des jetons d'accès d'un compte (EF-AUTH-014,
 * RG-010).
 *
 * <p>Écrit {@code credentials_invalidated_at} : tout jeton dont le claim
 * {@code iat} est antérieur devient inutilisable, y compris ceux dont
 * l'identifiant est inconnu du serveur — ce que la liste de refus Redis,
 * qui ne connaît que les jetons explicitement déconnectés, ne peut pas
 * garantir.
 */
@Service
public class SessionRevocationService {

    private final UserAccountRepository userAccountRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    SessionRevocationService(UserAccountRepository userAccountRepository,
                             ApplicationEventPublisher eventPublisher,
                             Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Révoque toutes les sessions du compte désigné.
     *
     * @param userPublicId identifiant public du compte
     * @param reason       motif, repris dans l'audit (voir les constantes
     *                     de {@link SessionsRevokedEvent})
     */
    @Transactional
    public void revokeAll(UUID userPublicId, String reason) {
        UserAccount account = userAccountRepository.findByPublicId(userPublicId)
                .orElseThrow(AuthException::accountNotEligible);
        Instant now = clock.instant();
        account.invalidateCredentials(now);
        userAccountRepository.save(account);
        eventPublisher.publishEvent(new SessionsRevokedEvent(
                account.getId(), account.getPublicId(),
                account.getFirstName() + " " + account.getLastName(), reason));
    }

    /**
     * Instant de révocation globale d'un compte, ou {@code null} s'il n'y
     * en a jamais eu. Lecture seule, appelée à chaque requête authentifiée
     * par {@link RevokedTokenValidator}.
     */
    @Transactional(readOnly = true)
    public Instant credentialsInvalidatedAt(UUID userPublicId) {
        return userAccountRepository.findByPublicId(userPublicId)
                .map(UserAccount::getCredentialsInvalidatedAt)
                .orElse(null);
    }
}
