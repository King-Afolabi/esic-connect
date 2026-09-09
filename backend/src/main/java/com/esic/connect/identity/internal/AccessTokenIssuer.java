package com.esic.connect.identity.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Émission du jeton d'accès HS256.
 *
 * <p>Extrait de {@code AuthenticationService} lors du sprint 2 : la
 * connexion n'est plus le seul chemin qui délivre un jeton — la
 * vérification d'un second facteur (EF-AUTH-008) et la connexion par
 * passkey (EF-AUTH-007) en délivrent aussi. Un seul endroit décide donc
 * de la forme du jeton.
 *
 * <p><strong>Claim {@code amr}</strong> (<em>authentication methods
 * references</em>, RFC 8176) : liste les moyens réellement employés —
 * {@code pwd}, {@code otp}, {@code webauthn}, {@code recovery}. C'est lui
 * qui permet d'exiger une réauthentification forte avant une action
 * critique (EF-AUTH-015) sans redemander un mot de passe déjà fourni.
 */
@Component
public class AccessTokenIssuer {

    /** Mot de passe vérifié. */
    public static final String AMR_PASSWORD = "pwd";
    /** Code TOTP vérifié. */
    public static final String AMR_OTP = "otp";
    /** Passkey WebAuthn vérifiée. */
    public static final String AMR_WEBAUTHN = "webauthn";
    /** Code de récupération consommé — volontairement distinct d'un TOTP. */
    public static final String AMR_RECOVERY = "recovery";

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final long accessTokenTtlSeconds;

    public AccessTokenIssuer(JwtEncoder jwtEncoder,
                             @Value("${app.security.jwt.issuer}") String issuer,
                             @Value("${app.security.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds) {
        if (accessTokenTtlSeconds <= 0) {
            throw new IllegalStateException(
                    "JWT_ACCESS_TOKEN_TTL_SECONDS doit être strictement positif (valeur reçue : "
                            + accessTokenTtlSeconds + ").");
        }
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long ttlSeconds() {
        return accessTokenTtlSeconds;
    }

    /**
     * @param methods moyens d'authentification employés, dans l'ordre
     *                d'utilisation ; jamais vide
     */
    public LoginResponse issue(UserAccount account, List<String> roleCodes, List<String> methods) {
        // Le claim `iat` n'a qu'une précision à la seconde, alors que la
        // révocation globale (`credentials_invalidated_at`) est arrondie à
        // la seconde SUPÉRIEURE pour qu'un jeton émis pendant la seconde
        // de la révocation soit bien refusé. Sans précaution, une
        // reconnexion immédiate après une révocation produirait un jeton
        // aussitôt rejeté, pendant près d'une seconde. On date donc
        // l'émission au plus tard des deux instants.
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
                .claim("amr", methods)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return LoginResponse.authenticated(token, accessTokenTtlSeconds);
    }
}
