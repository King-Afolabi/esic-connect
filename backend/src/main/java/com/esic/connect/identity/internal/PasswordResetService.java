package com.esic.connect.identity.internal;

import com.esic.connect.identity.AdminPasswordResetDirectory;
import com.esic.connect.identity.PasswordChangedEvent;
import com.esic.connect.identity.PasswordResetRequestedEvent;
import com.esic.connect.identity.SessionsRevokedEvent;
import com.esic.connect.shared.ratelimit.IdentityHashing;
import com.esic.connect.shared.ratelimit.RateLimitDecision;
import com.esic.connect.shared.ratelimit.RateLimitExceededException;
import com.esic.connect.shared.ratelimit.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Parcours « mot de passe oublié » (EF-AUTH-005,
 * docs/02-cahier-des-charges.md §17.8).
 *
 * <p><strong>Neutralité de la réponse.</strong> {@link #requestReset}
 * ne renvoie rien et ne lève jamais d'exception métier : que l'adresse
 * soit connue, inconnue, suspendue ou archivée, l'appelant reçoit la même
 * réponse. Sans cela, le formulaire deviendrait un moyen de vérifier
 * qu'une adresse est inscrite à l'ESIC.
 *
 * <p><strong>Jeton.</strong> 32 octets de {@link java.security.SecureRandom},
 * usage unique, durée de vie courte. Seule l'empreinte SHA-256 est
 * stockée ; le jeton brut n'existe qu'en mémoire, le temps de partir dans
 * le courriel. Émettre une nouvelle demande révoque la précédente.
 *
 * <p><strong>Effets d'une réinitialisation réussie.</strong> Le mot de
 * passe est remplacé, le jeton consommé, <em>toutes les sessions en cours
 * sont révoquées</em> (RG-010) et la personne est prévenue. Un compte en
 * attente d'activation qui réinitialise son mot de passe devient actif :
 * il a prouvé qu'il contrôle l'adresse.
 */
@Service
public class PasswordResetService implements AdminPasswordResetDirectory {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    /** Seaux de limitation (docs/02 §17.6). */
    private static final String BUCKET_REQUEST = "forgot-password";
    private static final String BUCKET_CONSUME = "reset-password";

    private final UserAccountRepository userAccountRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final InvitationTokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final Duration tokenTtl;
    private final int requestLimit;
    private final Duration requestWindow;
    private final int consumeLimit;
    private final Duration consumeWindow;

    PasswordResetService(UserAccountRepository userAccountRepository,
                         PasswordResetTokenRepository tokenRepository,
                         InvitationTokenService tokenService,
                         PasswordEncoder passwordEncoder,
                         PasswordPolicy passwordPolicy,
                         RateLimiter rateLimiter,
                         ApplicationEventPublisher eventPublisher,
                         Clock clock,
                         @Value("${app.security.password-reset.token-ttl:PT30M}") Duration tokenTtl,
                         @Value("${app.security.password-reset.request-limit:5}") int requestLimit,
                         @Value("${app.security.password-reset.request-window:PT1H}") Duration requestWindow,
                         @Value("${app.security.password-reset.consume-limit:10}") int consumeLimit,
                         @Value("${app.security.password-reset.consume-window:PT1H}") Duration consumeWindow) {
        if (tokenTtl == null || tokenTtl.isZero() || tokenTtl.isNegative()) {
            throw new IllegalStateException(
                    "app.security.password-reset.token-ttl doit être une durée strictement positive.");
        }
        this.userAccountRepository = userAccountRepository;
        this.tokenRepository = tokenRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.rateLimiter = rateLimiter;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.tokenTtl = tokenTtl;
        this.requestLimit = requestLimit;
        this.requestWindow = requestWindow;
        this.consumeLimit = consumeLimit;
        this.consumeWindow = consumeWindow;
    }

    /**
     * Demande une réinitialisation. Ne renvoie jamais d'information sur
     * l'existence du compte.
     *
     * @throws RateLimitExceededException si le seuil de demandes est
     *         dépassé pour cette adresse — seule exception possible, et
     *         elle ne révèle rien : le compteur existe pour toute adresse,
     *         connue ou non.
     */
    @Transactional
    public void requestReset(String rawEmail) {
        String email = EmailNormalization.normalize(rawEmail);
        enforceLimit(BUCKET_REQUEST, email, requestLimit, requestWindow);

        Optional<UserAccount> maybeAccount = userAccountRepository.findByEmail(email);
        if (maybeAccount.isEmpty()) {
            // Adresse inconnue : aucun jeton, aucun courriel, aucune
            // trace nominative. Le temps de réponse reste comparable, le
            // travail évité étant négligeable devant la latence réseau.
            log.debug("Demande de réinitialisation pour une adresse inconnue — ignorée silencieusement.");
            return;
        }

        UserAccount account = maybeAccount.get();
        if (!isResettable(account)) {
            log.debug("Demande de réinitialisation sur un compte non éligible — ignorée silencieusement.");
            return;
        }

        Instant now = clock.instant();
        revokePendingTokens(account, now);
        // La contrainte `uq_password_reset_token_active` n'autorise qu'une
        // demande PENDING par compte : la révocation doit atteindre la
        // base avant l'insertion de la nouvelle.
        tokenRepository.flush();

        String rawToken = tokenService.generateRawToken();
        Instant expiresAt = now.plus(tokenTtl);
        tokenRepository.save(new PasswordResetToken(account, tokenService.hash(rawToken), expiresAt));

        // Publié après commit par le module notification : aucun courriel
        // ne part si la transaction échoue.
        eventPublisher.publishEvent(new PasswordResetRequestedEvent(
                account.getEmail(), account.getFirstName(), rawToken, expiresAt));
    }

    /**
     * Consomme un jeton et définit le nouveau mot de passe.
     *
     * @throws AuthException {@code INVALID_RESET_TOKEN} si le jeton est
     *         inconnu, expiré, révoqué ou déjà consommé — les quatre cas
     *         étant volontairement indistinguables ;
     *         {@code WEAK_PASSWORD} si la politique n'est pas respectée.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw AuthException.invalidResetToken();
        }
        // Le seau est indexé sur le jeton présenté, pas sur un compte :
        // limiter le nombre d'essais d'un même jeton, et le nombre de
        // jetons essayés par un attaquant, sans jamais avoir besoin de
        // savoir à qui il appartient.
        enforceLimit(BUCKET_CONSUME, rawToken, consumeLimit, consumeWindow);

        Instant now = clock.instant();
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenService.hash(rawToken))
                .orElseThrow(AuthException::invalidResetToken);

        if (!token.isUsable(now)) {
            if (token.getStatus() == PasswordResetStatus.PENDING) {
                // Périmé : on fige l'état pour que l'historique soit
                // lisible, sans changer la réponse renvoyée.
                token.markExpired();
            }
            throw AuthException.invalidResetToken();
        }

        UserAccount account = token.getUser();
        if (!isResettable(account)) {
            token.revoke(now);
            throw AuthException.invalidResetToken();
        }

        List<String> violations = passwordPolicy.violations(newPassword, account.getEmail());
        if (!violations.isEmpty()) {
            // Le jeton n'est PAS consommé : la personne doit pouvoir
            // corriger son mot de passe sans redemander un lien.
            throw AuthException.weakPassword(violations);
        }

        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.invalidateCredentials(now);
        if (account.getStatus() == AccountStatus.PENDING_ACTIVATION) {
            // Contrôler l'adresse de réception vaut activation : c'est le
            // même niveau de preuve que le parcours d'invitation.
            account.setStatus(AccountStatus.ACTIVE);
        }
        token.consume(now);
        revokePendingTokens(account, now);

        userAccountRepository.save(account);

        eventPublisher.publishEvent(new PasswordChangedEvent(
                account.getId(), account.getPublicId(), account.getEmail(), account.getFirstName(),
                displaySnapshot(account), PasswordChangedEvent.ORIGIN_RESET));
        eventPublisher.publishEvent(new SessionsRevokedEvent(
                account.getId(), account.getPublicId(), displaySnapshot(account),
                SessionsRevokedEvent.REASON_PASSWORD_RESET));
    }

    /**
     * Implémentation d'{@link AdminPasswordResetDirectory} — déclenchement
     * par un tiers habilité (module {@code passwordadmin}), et non par la
     * personne elle-même. Même mécanique que {@link #requestReset} (jeton,
     * révocation des demandes en attente, événement de notification) mais
     * ciblage par identifiant public déjà résolu, et réponse franche : le
     * contrôle d'autorisation a déjà eu lieu chez l'appelant.
     */
    @Override
    @Transactional
    public Outcome triggerReset(UUID targetUserPublicId) {
        Optional<UserAccount> maybeAccount = userAccountRepository.findByPublicId(targetUserPublicId);
        if (maybeAccount.isEmpty()) {
            return Outcome.USER_NOT_FOUND;
        }
        UserAccount account = maybeAccount.get();
        if (!isResettable(account)) {
            return Outcome.NOT_ELIGIBLE;
        }

        Instant now = clock.instant();
        revokePendingTokens(account, now);
        tokenRepository.flush();

        String rawToken = tokenService.generateRawToken();
        Instant expiresAt = now.plus(tokenTtl);
        tokenRepository.save(new PasswordResetToken(account, tokenService.hash(rawToken), expiresAt));

        eventPublisher.publishEvent(new PasswordResetRequestedEvent(
                account.getEmail(), account.getFirstName(), rawToken, expiresAt));
        return Outcome.RESET_SENT;
    }

    /**
     * Un compte suspendu, verrouillé ou archivé ne peut pas réinitialiser
     * son mot de passe : ce serait un contournement de la décision
     * administrative qui l'a mis dans cet état.
     */
    private boolean isResettable(UserAccount account) {
        return account.getStatus() == AccountStatus.ACTIVE
                || account.getStatus() == AccountStatus.PENDING_ACTIVATION;
    }

    /**
     * Révoque les demandes encore en attente du compte.
     *
     * <p>Ne force PAS le vidage du contexte de persistance : celui-ci
     * verrouillerait la ligne {@code user_account} déjà modifiée, et tout
     * travail déclenché ensuite dans une transaction distincte se
     * bloquerait dessus. L'appelant vide explicitement lorsque l'ordre
     * des écritures l'exige — voir {@link #requestReset}.
     */
    private void revokePendingTokens(UserAccount account, Instant now) {
        tokenRepository.findByUserIdAndStatus(account.getId(), PasswordResetStatus.PENDING)
                .forEach(pending -> pending.revoke(now));
    }

    private void enforceLimit(String bucket, String identity, int limit, Duration window) {
        RateLimitDecision decision = rateLimiter.consume(bucket, IdentityHashing.of(identity), limit, window);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfter());
        }
    }

    private String displaySnapshot(UserAccount account) {
        return account.getFirstName() + " " + account.getLastName();
    }
}
