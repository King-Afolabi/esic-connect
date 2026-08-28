package com.esic.connect.identity.internal;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Génère les jetons d'invitation et calcule l'empreinte stockée.
 *
 * <ul>
 *   <li>jeton brut : 32 octets issus de {@link SecureRandom}, encodés en
 *       Base64 URL sans remplissage (~43 caractères) ;</li>
 *   <li>empreinte : SHA-256 du jeton brut, en hexadécimal minuscule
 *       (docs/07-securite-rgpd.md, cahier §16.1). Le jeton brut n'est
 *       jamais stocké.</li>
 * </ul>
 *
 * La comparaison se fait par recherche de l'empreinte exacte en base : il
 * n'y a donc pas d'oracle temporel à couvrir ici.
 */
@Component
class InvitationTokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();

    String generateRawToken() {
        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        return urlEncoder.encodeToString(raw);
    }

    String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 fait partie de toute JVM standard.
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
