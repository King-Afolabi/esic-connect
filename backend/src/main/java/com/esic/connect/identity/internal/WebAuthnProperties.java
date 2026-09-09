package com.esic.connect.identity.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration de la partie de confiance WebAuthn (EF-AUTH-006).
 *
 * <p><strong>Contrainte du standard.</strong> Le {@code rpId} doit être
 * le domaine de l'application, et l'origine doit être servie en HTTPS —
 * à la seule exception de {@code localhost}, que les navigateurs
 * considèrent comme un contexte sûr. C'est ce qui rend le développement
 * possible sans certificat ; c'est aussi ce qui impose un nom de domaine
 * et HTTPS avant toute utilisation hors du poste de développement.
 */
@Component
public class WebAuthnProperties {

    private final String relyingPartyId;
    private final String relyingPartyName;
    private final List<String> allowedOrigins;
    private final Duration timeout;

    public WebAuthnProperties(
            @Value("${app.security.webauthn.rp-id:localhost}") String relyingPartyId,
            @Value("${app.security.webauthn.rp-name:ESIC Connect}") String relyingPartyName,
            @Value("${app.security.webauthn.origins:http://localhost:4200}") String origins,
            @Value("${app.security.webauthn.timeout:PT2M}") Duration timeout) {
        this.relyingPartyId = relyingPartyId;
        this.relyingPartyName = relyingPartyName;
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (this.allowedOrigins.isEmpty()) {
            throw new IllegalStateException(
                    "app.security.webauthn.origins doit contenir au moins une origine.");
        }
        this.timeout = timeout;
    }

    public String relyingPartyId() {
        return relyingPartyId;
    }

    public String relyingPartyName() {
        return relyingPartyName;
    }

    public List<String> allowedOrigins() {
        return allowedOrigins;
    }

    public long timeoutMillis() {
        return timeout.toMillis();
    }
}
