package com.esic.connect.identity.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestration du renouvellement de session par cookie (EF-AUTH-014,
 * docs/02 §17.7).
 *
 * <p>Deux moments :
 * <ul>
 *   <li>{@link #onAuthenticated} — juste après qu'un des parcours
 *       d'authentification a délivré un jeton d'accès (mot de passe,
 *       second facteur, passkey). Ouvre une famille de renouvellement et
 *       renvoie le cookie à poser. Ne lève jamais : une panne Redis prive
 *       la réponse du cookie, sans compromettre la connexion.</li>
 *   <li>{@link #refresh} — sur {@code POST /api/v1/auth/refresh}. Fait
 *       tourner la famille, revérifie l'état du compte, et réémet un
 *       jeton d'accès portant les mêmes moyens d'authentification que la
 *       session d'origine (EF-AUTH-015).</li>
 * </ul>
 *
 * <p>La révocation globale d'un compte n'a pas de mécanisme propre ici :
 * {@link #refresh} recharge le compte et applique exactement la règle de
 * {@link RevokedTokenValidator} — un renouvellement dont la famille a été
 * ouverte avant {@code credentials_invalidated_at} est refusé. Un
 * changement de mot de passe, une suspension ou un {@code logout-all}
 * neutralisent donc aussi les jetons de renouvellement.
 */
@Service
class RefreshService {

    private static final Logger log = LoggerFactory.getLogger(RefreshService.class);

    private final RefreshTokenStore store;
    private final RefreshCookies cookies;
    private final JwtDecoder jwtDecoder;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final AccessTokenIssuer tokenIssuer;

    RefreshService(RefreshTokenStore store,
                   RefreshCookies cookies,
                   JwtDecoder jwtDecoder,
                   UserAccountRepository userAccountRepository,
                   UserRoleRepository userRoleRepository,
                   AccessTokenIssuer tokenIssuer) {
        this.store = store;
        this.cookies = cookies;
        this.jwtDecoder = jwtDecoder;
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.tokenIssuer = tokenIssuer;
    }

    /**
     * Cookie de renouvellement pour une réponse d'authentification qui
     * porte un jeton d'accès. {@link Optional#empty()} si la réponse est
     * un défi de second facteur (aucun jeton), ou si le magasin est
     * momentanément injoignable.
     */
    Optional<ResponseCookie> onAuthenticated(LoginResponse response, String deviceFingerprint) {
        if (response == null || response.accessToken() == null) {
            return Optional.empty();
        }
        try {
            Jwt jwt = jwtDecoder.decode(response.accessToken());
            List<String> amr = jwt.getClaimAsStringList("amr");
            RefreshTokenStore.Issued issued = store.issue(
                    UUID.fromString(jwt.getSubject()),
                    amr == null ? List.of() : amr,
                    deviceFingerprint);
            return Optional.of(cookies.build(issued.cookieValue(), issued.idleTtl()));
        } catch (DataAccessException redisFailure) {
            log.warn("Cookie de renouvellement non émis, magasin injoignable : {}",
                    redisFailure.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * Fait tourner la famille désignée par le cookie et réémet un jeton
     * d'accès.
     *
     * @throws RefreshTokenException cookie invalide, famille absente ou
     *                               révoquée, compte non actif, magasin
     *                               injoignable
     */
    @Transactional(readOnly = true)
    Refreshed refresh(String rawCookie, String deviceFingerprint) {
        RefreshTokenStore.Rotated rotated = store.rotate(rawCookie, deviceFingerprint);

        UserAccount account = userAccountRepository.findByPublicId(rotated.userPublicId())
                .orElseGet(() -> {
                    store.revoke(rotated.cookieValue());
                    throw new RefreshTokenException();
                });

        Instant invalidatedAt = account.getCredentialsInvalidatedAt();
        if (invalidatedAt != null && rotated.familyIssuedAt().isBefore(invalidatedAt)) {
            store.revoke(rotated.cookieValue());
            throw new RefreshTokenException();
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            store.revoke(rotated.cookieValue());
            throw new RefreshTokenException();
        }

        List<String> roleCodes = userRoleRepository.findActiveRoleCodesByUserId(account.getId())
                .stream().map(Enum::name).toList();
        List<String> methods = rotated.amr().isEmpty()
                ? List.of(AccessTokenIssuer.AMR_PASSWORD)
                : rotated.amr();
        LoginResponse body = tokenIssuer.issue(account, roleCodes, methods);
        return new Refreshed(body, cookies.build(rotated.cookieValue(), rotated.idleTtl()));
    }

    /** Révoque la famille du cookie (si présent) et renvoie le cookie d'effacement. */
    ResponseCookie revoke(String rawCookie) {
        store.revoke(rawCookie);
        return cookies.clear();
    }

    record Refreshed(LoginResponse body, ResponseCookie cookie) {
    }
}
