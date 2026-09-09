package com.esic.connect.shared.captcha;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Adaptateur Cloudflare Turnstile (EF-AUTH-011, docs/02 §17.9 et §28.5).
 *
 * <p><strong>Activation.</strong> Uniquement si
 * {@code app.security.captcha.secret-key} est renseignée — la clé secrète
 * vit dans l'environnement, jamais dans le dépôt. Sans elle, c'est
 * {@link LocalCaptchaVerifier} qui est actif : le produit reste
 * intégralement utilisable en local (docs/02 §28.1).
 *
 * <p><strong>Politique de repli — décision DEC-S2-004.</strong> Si
 * Cloudflare ne répond pas, la requête est <em>laissée passer</em> et
 * l'incident journalisé en {@code WARN}. Refuser transformerait
 * l'indisponibilité d'un tiers en panne totale de la connexion et de la
 * réinitialisation de mot de passe, déclenchable de l'extérieur. Les
 * autres protections restent actives pendant ce temps : limitation de
 * débit par identité et par origine, réponse uniforme, audit. C'est le
 * même arbitrage que pour {@code RedisRateLimiter} (DEC-S2-001) : un
 * garde-fou en panne ne doit pas devenir un déni de service.
 *
 * <p>Un jeton Turnstile est à <strong>usage unique</strong> : Cloudflare
 * refuse lui-même une seconde présentation. Aucun cache local n'est donc
 * nécessaire, et aucun ne doit être ajouté — il rouvrirait la porte au
 * rejeu.
 */
public class TurnstileCaptchaVerifier implements CaptchaVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileCaptchaVerifier.class);

    private static final String DEFAULT_ENDPOINT =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final RestClient restClient;
    private final String secretKey;
    private final boolean enforced;

    public TurnstileCaptchaVerifier(RestClient.Builder restClientBuilder,
                                    @Value("${app.security.captcha.secret-key:}") String secretKey,
                                    @Value("${app.security.captcha.endpoint:" + DEFAULT_ENDPOINT + "}")
                                    String endpoint,
                                    @Value("${app.security.captcha.timeout:PT5S}") Duration timeout) {
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.enforced = !this.secretKey.isEmpty();
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        this.restClient = restClientBuilder.clone()
                .requestFactory(factory)
                .baseUrl(endpoint)
                .build();
        if (!enforced) {
            log.info("Anti-robot Turnstile inactif : aucune clé secrète configurée. "
                    + "Le contrôle local prend le relais (EF-AUTH-011).");
        }
    }

    @Override
    public boolean isEnforced() {
        return enforced;
    }

    @Override
    public CaptchaVerdict verify(String token, String clientOrigin) {
        if (!enforced) {
            return CaptchaVerdict.pass();
        }
        if (token == null || token.isBlank()) {
            return CaptchaVerdict.reject("missing-token");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token);
        if (clientOrigin != null && !clientOrigin.isBlank()) {
            // Transmis pour le contrôle de Cloudflare, jamais conservé ici.
            form.add("remoteip", clientOrigin);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.post()
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                return CaptchaVerdict.providerUnavailable();
            }
            if (Boolean.TRUE.equals(body.get("success"))) {
                return CaptchaVerdict.pass();
            }
            Object codes = body.get("error-codes");
            return CaptchaVerdict.reject(codes instanceof List<?> list ? list.toString() : "rejected");
        } catch (RuntimeException providerFailure) {
            log.warn("Turnstile injoignable ({}) : la requête est laissée passer (DEC-S2-004).",
                    providerFailure.getClass().getSimpleName());
            return CaptchaVerdict.providerUnavailable();
        }
    }
}
