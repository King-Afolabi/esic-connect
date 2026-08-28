package com.esic.connect.identity.internal;

import com.esic.connect.identity.LoginFailedEvent;
import com.esic.connect.identity.LoginSucceededEvent;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Authentification email/mot de passe (docs/02-cahier-des-charges.md
 * §26). Toute tentative — réussie ou refusée — publie un événement à
 * destination du module {@code audit} ; aucun mot de passe ni jeton n'y
 * transite. La réponse publique uniforme en cas d'échec est construite
 * par {@code GlobalExceptionHandler}, pas ici.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository userAccountRepository;
    private final JwtEncoder jwtEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final String issuer;
    private final long accessTokenTtlSeconds;

    public AuthenticationService(AuthenticationManager authenticationManager,
                                  UserAccountRepository userAccountRepository,
                                  JwtEncoder jwtEncoder,
                                  ApplicationEventPublisher eventPublisher,
                                  @Value("${app.security.jwt.issuer}") String issuer,
                                  @Value("${app.security.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds) {
        this.authenticationManager = authenticationManager;
        this.userAccountRepository = userAccountRepository;
        this.jwtEncoder = jwtEncoder;
        this.eventPublisher = eventPublisher;
        if (accessTokenTtlSeconds <= 0) {
            throw new IllegalStateException(
                    "JWT_ACCESS_TOKEN_TTL_SECONDS doit être strictement positif (valeur reçue : "
                            + accessTokenTtlSeconds + ").");
        }
        this.issuer = issuer;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    @Transactional
    public LoginResponse login(String rawEmail, String rawPassword) {
        String email = EmailNormalization.normalize(rawEmail);
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

    private String issueAccessToken(UserAccount account, List<String> roleCodes) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTokenTtlSeconds))
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
