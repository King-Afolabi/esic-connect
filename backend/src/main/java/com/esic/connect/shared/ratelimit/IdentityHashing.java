package com.esic.connect.shared.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Empreinte d'une identité utilisée comme clé de limitation.
 *
 * <p>Ni l'adresse électronique ni l'adresse IP ne doivent apparaître en
 * clair dans Redis (RG-094, docs/02 §16.7 et §23.3). Elles sont donc
 * réduites à un SHA-256 hexadécimal tronqué à 32 caractères : suffisant
 * pour éviter les collisions à l'échelle de l'établissement, et
 * non réversible.
 *
 * <p>Un sel fixe propre à l'application préfixe la valeur afin qu'une
 * empreinte extraite de Redis ne puisse pas être rapprochée d'une table
 * arc-en-ciel d'adresses courantes.
 */
public final class IdentityHashing {

    private static final String SALT = "esic-connect:rate-limit:v1:";
    private static final int LENGTH = 32;

    private IdentityHashing() {
    }

    /** Empreinte d'une valeur quelconque ; {@code null} devient {@code "anonymous"}. */
    public static String of(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "anonymous";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((SALT + normalized).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed).substring(0, LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
