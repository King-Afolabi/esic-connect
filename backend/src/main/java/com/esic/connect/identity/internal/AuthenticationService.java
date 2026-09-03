package com.esic.connect.identity.internal;

import com.esic.connect.identity.LoginFailedEvent;
import com.esic.connect.identity.LoginSucceededEvent;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository userAccountRepository;
    private final JwtEncoder jwtEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final RateLimiter rateLimiter;
    private final String issuer;
    private final long accessTokenTtlSeconds;
    private final int identityLimit;
    private final int originLimit;
    private final Duration loginWindow;

    public AuthenticationService(AuthenticationManager authenticationManager,
                                  UserAccountRepository userAccountRepository,
                                  JwtEncoder jwtEncoder,
                                  ApplicationEventPublisher eventPublisher,
                                  RateLimiter rateLimiter,
                                  @Value("${app.security.jwt.issuer}") String issuer,
                                  @Value("${app.security.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds,
                                  @Value("${app.security.login.identity-limit:10}") int identityLimit,
                                  @Value("${app.security.login.origin-limit:60}") int originLimit,
                                  @Value("${app.security.login.window:PT15M}") Duration loginWindow) {
        this.authenticationManager = authenticationManager;
        this.userAccountRepository = userAccountRepository;
        this.jwtEncoder = jwtEncoder;
        this.eventPublisher = eventPublisher;
        this.rateLimiter = rateLimiter;
        this.identityLimit = identityLimit;
        this.originLimit = originLimit;
        this.loginWindow = loginWindow;
        if (accessTokenTtlSeconds <= 0) {
            throw new IllegalStateException(
                    "JWT_ACCESS_TOKEN_TTL_SECONDS doit être strictement positif (valeur reçue : "
                            + accessTokenTtlSeconds + ").");
        }
        this.issuer = issuer;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    /**
     * @param clientOrigin identifiant de l'origine réseau de l'appel
     *                     (adresse distante). Utilisé pendant la décision
     *                     uniquement, sous forme d'empreinte, et jamais
     *                     conservé (docs/02 §16.7 et §23.3). Peut être nul.
     */
    @Transactional
    public LoginResponse login(String rawEmail, String rawPassword, String clientOrigin) {
        String email = EmailNormalization.normalize(rawEmail);
        String identityHash = IdentityHashing.of(email);
        enforce("login", identityHash, identityLimit);
        enforce("login-origin", IdentityHashing.of(clientOrigin), originLimit);
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

            String token = issueAccessToken(account, roleCodes);

            publishSafely(new LoginSucceededEvent(account.getId(), account.getPublicId(),
                    displaySnapshot(account), String.join(",", roleCodes)));

            return new LoginResponse(token, "Bearer", accessTokenTtlSeconds);
        } catch (AuthenticationException ex) {
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

    private String issueAccessToken(UserAccount account, List<String> roleCodes) {
        // Le claim `iat` n'a qu'une précision à la seconde, alors que la
        // révocation globale (`credentials_invalidated_at`) est arrondie à
        // la seconde SUPÉRIEURE pour qu'un jeton émis pendant la seconde
        // de la révocation soit bien refusé. Sans précaution, une
        // reconnexion immédiate après une révocation produirait un jeton
        // aussitôt rejeté, pendant près d'une seconde. On date donc
        // l'émission au plus tard des deux instants : le jeton fraîchement
        // émis est toujours valide, et le trou d'une seconde disparaît des
        // deux côtés.
        Instant now = Instant.now();
        Instant revokedUntil = account.getCredentialsInvalidatedAt();
        Instant issuedAt = revokedUntil != null && revokedUntil.isAfter(now) ? revokedUntil : now;
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(accessTokenTtlSeconds))
                // Identifiant PUBLIC en sujet, jamais l'id interne (docs/04 §3.2).
                .subject(account.getPublicId().toString())
                .id(UUID.randomUUID().toString())
                .claim("roles", roleCodes)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
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
