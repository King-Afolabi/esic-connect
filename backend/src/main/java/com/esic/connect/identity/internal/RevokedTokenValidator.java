package com.esic.connect.identity.internal;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Refuse un jeton d'accès révoqué (EF-AUTH-014, RG-010).
 *
 * <p>Deux mécanismes complémentaires, vérifiés à chaque requête :
 * <ol>
 *   <li>le jeton figure sur la liste de refus Redis — c'est une
 *       déconnexion explicite de cette session ;</li>
 *   <li>le jeton a été émis avant {@code credentials_invalidated_at} du
 *       compte — c'est une révocation globale : changement de mot de
 *       passe, suspension, incident.</li>
 * </ol>
 *
 * <p>Ce composant est déclaré dans le module {@code identity} et non dans
 * {@code shared} : la révocation dépend de l'état des comptes, qui
 * appartient à ce module. Il est fourni à la configuration de sécurité
 * sous la forme d'un {@link OAuth2TokenValidator}, type tiers, ce qui
 * évite toute dépendance de {@code shared} vers {@code identity}.
 *
 * <p>Le message d'erreur reste générique : l'appelant reçoit de toute
 * façon un {@code 401} nu, l'entrée d'authentification n'exposant aucun
 * détail de validation.
 */
@Component
class RevokedTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED =
            new OAuth2Error("invalid_token", "Le jeton a été révoqué.", null);

    private final AccessTokenRevocationService accessTokenRevocationService;
    private final SessionRevocationService sessionRevocationService;

    RevokedTokenValidator(AccessTokenRevocationService accessTokenRevocationService,
                          SessionRevocationService sessionRevocationService) {
        this.accessTokenRevocationService = accessTokenRevocationService;
        this.sessionRevocationService = sessionRevocationService;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (accessTokenRevocationService.isRevoked(jwt.getId())) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }

        UUID subject = parseSubject(jwt.getSubject());
        if (subject == null) {
            // Sujet illisible : le jeton n'est de toute façon rattachable
            // à aucun compte, donc inutilisable.
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }

        Instant invalidatedAt = sessionRevocationService.credentialsInvalidatedAt(subject);
        Instant issuedAt = jwt.getIssuedAt();
        if (invalidatedAt != null && issuedAt != null && issuedAt.isBefore(invalidatedAt)) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private UUID parseSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(subject.trim());
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }
}
