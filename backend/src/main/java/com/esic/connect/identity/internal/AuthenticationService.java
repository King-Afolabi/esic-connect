package com.esic.connect.identity.internal;

import com.esic.connect.identity.LoginFailedEvent;
import com.esic.connect.identity.LoginSucceededEvent;
import com.esic.connect.shared.captcha.CaptchaGuard;
import com.esic.connect.shared.ratelimit.IdentityHashing;
import com.esic.connect.shared.ratelimit.RateLimitDecision;
import com.esic.connect.shared.ratelimit.RateLimitExceededException;
import com.esic.connect.shared.ratelimit.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Authentification email/mot de passe (docs/02-cahier-des-charges.md
 * §17.1). Toute tentative — réussie ou refusée — publie un événement à
 * destination du module {@code audit} ; aucun mot de passe ni jeton n'y
 * transite. La réponse publique uniforme en cas d'échec est construite
 * par {@code GlobalExceptionHandler}, pas ici.
 *
 * <p><strong>Limitation de débit (EF-AUTH-012, RG-092).</strong> Deux
 * seaux indépendants sont consommés à chaque tentative :
 * <ul>
 *   <li>par <em>identité</em> : borne les essais contre un compte précis ;</li>
 *   <li>par <em>origine réseau</em> : borne un balayage qui essaierait un
 *       mot de passe courant sur de nombreux comptes, ce que le premier
 *       seau ne verrait pas.</li>
 * </ul>
 * Les deux clés sont des empreintes : ni l'adresse électronique ni
 * l'adresse IP n'est écrite en clair dans Redis (RG-094). Une connexion
 * réussie remet le seau d'identité à zéro, jamais celui de l'origine —
 * sinon un attaquant disposant d'un compte valide effacerait sa propre
 * limite entre deux salves.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    /** Compteur d'échecs, distinct du seau de limitation : il ne bloque pas, il alerte. */
    private static final String BUCKET_FAILURES = "login-failures";

    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository userAccountRepository;
    private final AccessTokenIssuer tokenIssuer;
    private final MfaService mfaService;
    private final TrustedDeviceService trustedDeviceService;
    private final ApplicationEventPublisher eventPublisher;
    private final RateLimiter rateLimiter;
    private final CaptchaGuard captchaGuard;
    private final int identityLimit;
    private final int originLimit;
    private final Duration loginWindow;
    private final int captchaAfterFailures;

    public AuthenticationService(AuthenticationManager authenticationManager,
                                  UserAccountRepository userAccountRepository,
                                  AccessTokenIssuer tokenIssuer,
                                  MfaService mfaService,
                                  TrustedDeviceService trustedDeviceService,
                                  ApplicationEventPublisher eventPublisher,
                                  RateLimiter rateLimiter,
                                  CaptchaGuard captchaGuard,
                                  @Value("${app.security.login.identity-limit:10}") int identityLimit,
                                  @Value("${app.security.login.origin-limit:60}") int originLimit,
                                  @Value("${app.security.login.window:PT15M}") Duration loginWindow,
                                  @Value("${app.security.login.captcha-after-failures:3}")
                                  int captchaAfterFailures) {
        this.authenticationManager = authenticationManager;
        this.userAccountRepository = userAccountRepository;
        this.tokenIssuer = tokenIssuer;
        this.mfaService = mfaService;
        this.trustedDeviceService = trustedDeviceService;
        this.eventPublisher = eventPublisher;
        this.rateLimiter = rateLimiter;
        this.captchaGuard = captchaGuard;
        this.identityLimit = identityLimit;
        this.originLimit = originLimit;
        this.loginWindow = loginWindow;
        this.captchaAfterFailures = captchaAfterFailures;
    }

    /**
     * @param clientOrigin identifiant de l'origine réseau de l'appel
     *                     (adresse distante). Utilisé pendant la décision
     *                     uniquement, sous forme d'empreinte, et jamais
     *                     conservé (docs/02 §16.7 et §23.3). Peut être nul.
     */
    @Transactional
    public LoginResponse login(String rawEmail, String rawPassword, String clientOrigin,
                               String deviceFingerprint, String captchaToken) {
        String email = EmailNormalization.normalize(rawEmail);
        String identityHash = IdentityHashing.of(email);
        String originHash = IdentityHashing.of(clientOrigin);
        enforce("login", identityHash, identityLimit);
        enforce("login-origin", originHash, originLimit);
        // Contrôle renforcé après des échecs répétés (RG-092, AC-022) : le
        // compteur d'identité borne l'acharnement sur un compte, celui
        // d'origine borne le balayage de plusieurs comptes depuis un même
        // point. L'un ou l'autre suffit à exiger la preuve anti-robot.
        if (rateLimiter.currentCount(BUCKET_FAILURES, identityHash) >= captchaAfterFailures
                || rateLimiter.currentCount(BUCKET_FAILURES, originHash) >= captchaAfterFailures) {
            captchaGuard.require(captchaToken, clientOrigin);
        }
        // Lecture interne uniquement, pour enrichir l'audit : jamais
        // transmise à l'appelant, ne révèle donc rien publiquement.
        Optional<UserAccount> maybeAccount = userAccountRepository.findByEmail(email);

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, rawPassword));
            UserAccountUserDetails principal = (UserAccountUserDetails) authentication.getPrincipal();
            UserAccount account = principal.getAccount();

            List<String> roleCodes = principal.getAuthorities().stream()
                    .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                    .toList();

            account.recordSuccessfulLogin(Instant.now());
            userAccountRepository.save(account);
            rateLimiter.reset("login", identityHash);
            rateLimiter.reset(BUCKET_FAILURES, identityHash);

            publishSafely(new LoginSucceededEvent(account.getId(), account.getPublicId(),
                    displaySnapshot(account), String.join(",", roleCodes)));

            // Le mot de passe est vérifié : la connexion peut encore être
            // suspendue par un second facteur (RG-007, AC-021) ou par le
            // caractère inhabituel de l'appareil (EF-AUTH-010).
            boolean trustedDevice = trustedDeviceService.isTrusted(account.getId(), deviceFingerprint);
            Optional<LoginResponse.MfaChallengeResponse> challenge =
                    mfaService.challengeFor(account, roleCodes, trustedDevice);
            if (challenge.isPresent()) {
                return LoginResponse.challenge(challenge.get());
            }
            trustedDeviceService.remember(account.getId(), deviceFingerprint);
            return tokenIssuer.issue(account, roleCodes, List.of(AccessTokenIssuer.AMR_PASSWORD));
        } catch (AuthenticationException ex) {
            rateLimiter.observe(BUCKET_FAILURES, identityHash, loginWindow);
            rateLimiter.observe(BUCKET_FAILURES, originHash, loginWindow);
            publishSafely(new LoginFailedEvent(
                    maybeAccount.map(UserAccount::getId).orElse(null),
                    maybeAccount.map(UserAccount::getPublicId).orElse(null),
                    maybeAccount.map(this::displaySnapshot).orElse(null),
                    ex.getClass().getSimpleName()));
            throw ex;
        }
    }

    private void enforce(String bucket, String identityHash, int limit) {
        RateLimitDecision decision = rateLimiter.consume(bucket, identityHash, limit, loginWindow);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfter());
        }
    }

    private String displaySnapshot(UserAccount account) {
        return account.getFirstName() + " " + account.getLastName();
    }

    private void publishSafely(Object event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (RuntimeException auditFailure) {
            // Un échec de journalisation ne doit jamais modifier ni
            // révéler d'information sur le résultat de l'authentification :
            // seul le type d'exception est journalisé, sans détail sensible.
            log.warn("Échec de journalisation de l'audit de connexion : {}",
                    auditFailure.getClass().getSimpleName());
        }
    }
}
