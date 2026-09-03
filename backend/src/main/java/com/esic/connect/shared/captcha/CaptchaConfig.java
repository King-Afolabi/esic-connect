package com.esic.connect.shared.captcha;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Choisit l'adaptateur anti-robot <strong>par configuration</strong>
 * (docs/02 §28.1) : Cloudflare Turnstile dès qu'une clé secrète est
 * présente dans l'environnement, adaptateur local sinon.
 *
 * <p>Aucune clé n'a de valeur par défaut : un secret n'entre jamais dans
 * le dépôt, même « de test » ou « de repli ».
 */
@Configuration
public class CaptchaConfig {

    private static final String DEFAULT_ENDPOINT =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    @Bean
    public CaptchaVerifier captchaVerifier(
            RestClient.Builder restClientBuilder,
            @Value("${app.security.captcha.secret-key:}") String secretKey,
            @Value("${app.security.captcha.endpoint:" + DEFAULT_ENDPOINT + "}") String endpoint,
            @Value("${app.security.captcha.timeout:PT5S}") Duration timeout) {
        if (secretKey == null || secretKey.isBlank()) {
            return new LocalCaptchaVerifier();
        }
        return new TurnstileCaptchaVerifier(restClientBuilder, secretKey, endpoint, timeout);
    }
}
