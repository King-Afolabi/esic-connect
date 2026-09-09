package com.esic.connect.identity.internal;

import com.esic.connect.identity.PasswordChangedEvent;
import com.esic.connect.identity.SessionsRevokedEvent;
import com.esic.connect.shared.ratelimit.IdentityHashing;
import com.esic.connect.shared.ratelimit.RateLimitDecision;
import com.esic.connect.shared.ratelimit.RateLimitExceededException;
import com.esic.connect.shared.ratelimit.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Changement volontaire de mot de passe par un utilisateur connecté
 * (EF-AUTH — docs/02-cahier-des-charges.md §17.1, §34.2). Ouvert à
 * <strong>tous</strong> les rôles : chacun gère ses propres moyens
 * d'authentification, et le serveur déduit le compte du seul sujet du
 * JWT — un utilisateur ne peut donc jamais changer le mot de passe d'un
 * autre.
 *
 * <p>Contrôles serveur, dans l'ordre :
 * <ol>
 *   <li>limitation de débit par compte (empreinte, jamais l'adresse en
 *       clair — docs/02 §17.6) ;</li>
 *   <li>compte chargé depuis le sujet du jeton, doit être {@code ACTIVE} ;</li>
 *   <li>mot de passe <strong>actuel</strong> revérifié (BCrypt) ;</li>
 *   <li>nouveau mot de passe conforme à {@link PasswordPolicy} ;</li>
 *   <li>nouveau mot de passe différent de l'actuel (RG — l'ancien ne doit
 *       pas pouvoir être « réutilisé » via cet écran).</li>
 * </ol>
 *
 * <p>Effets d'un succès (identiques au parcours « mot de passe oublié »,
 * §17.8) : le hachage est remplacé, {@code credentials_invalidated_at}
 * est avancé — donc <em>toutes</em> les sessions du compte, sur tous les
 * appareils, sont invalidées (RG-010) —, l'événement de sécurité est
 * audité et la personne est notifiée. L'appelant est renvoyé vers la
 * connexion : c'est la forme de « renouvellement de session » la plus
 * sûre compatible avec l'arrondi à la seconde de
 * {@code credentials_invalidated_at}.
 *
 * <p>Aucun message ne contient de mot de passe, ni l'ancien ni le
 * nouveau, et aucune réponse ne révèle si le nouveau figurait déjà dans
 * un historique (aucun historique n'est conservé — §17.1).
 */
@Service
class PasswordChangeService {

    private static final String BUCKET = "change-password";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final int attemptLimit;
    private final Duration attemptWindow;

    PasswordChangeService(UserAccountRepository userAccountRepository,
                          PasswordEncoder passwordEncoder,
                          PasswordPolicy passwordPolicy,
                          RateLimiter rateLimiter,
                          ApplicationEventPublisher eventPublisher,
                          Clock clock,
                          @Value("${app.security.password-change.attempt-limit:10}") int attemptLimit,
                          @Value("${app.security.password-change.attempt-window:PT15M}") Duration attemptWindow) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.rateLimiter = rateLimiter;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.attemptLimit = attemptLimit;
        this.attemptWindow = attemptWindow;
    }

    /**
     * @param callerPublicId  sujet du JWT de l'appelant (jamais un
     *                        identifiant fourni dans le corps)
     * @throws AuthException           {@code ACCOUNT_NOT_ELIGIBLE},
     *                                 {@code CURRENT_PASSWORD_MISMATCH},
     *                                 {@code WEAK_PASSWORD},
     *                                 {@code PASSWORD_UNCHANGED}
     * @throws RateLimitExceededException trop de tentatives pour ce compte
     */
    @Transactional
    void change(UUID callerPublicId, String currentPassword, String newPassword) {
        RateLimitDecision decision = rateLimiter.consume(BUCKET,
                IdentityHashing.of(callerPublicId.toString()), attemptLimit, attemptWindow);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfter());
        }

        UserAccount account = userAccountRepository.findByPublicId(callerPublicId)
                .orElseThrow(AuthException::accountNotEligible);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw AuthException.accountNotEligible();
        }
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw AuthException.currentPasswordMismatch();
        }

        List<String> violations = passwordPolicy.violations(newPassword, account.getEmail());
        if (!violations.isEmpty()) {
            throw AuthException.weakPassword(violations);
        }
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw AuthException.passwordUnchanged();
        }

        Instant now = clock.instant();
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.invalidateCredentials(now);
        userAccountRepository.save(account);

        String snapshot = account.getFirstName() + " " + account.getLastName();
        eventPublisher.publishEvent(new PasswordChangedEvent(account.getId(), account.getPublicId(),
                account.getEmail(), account.getFirstName(), snapshot,
                PasswordChangedEvent.ORIGIN_SELF_SERVICE));
        eventPublisher.publishEvent(new SessionsRevokedEvent(account.getId(), account.getPublicId(),
                snapshot, SessionsRevokedEvent.REASON_PASSWORD_CHANGE));
    }
}
