package com.esic.connect.identity.internal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

/**
 * Mot de passe à usage unique fondé sur le temps — RFC 6238 (TOTP), avec
 * HMAC-SHA1 sur des pas de 30 secondes et un code à 6 chiffres, seule
 * combinaison universellement acceptée par les applications
 * d'authentification du marché (EF-AUTH-008, docs/02 §17.3).
 *
 * <p>Classe volontairement <strong>pure</strong> : aucune dépendance
 * Spring, aucune horloge implicite, aucun accès à la base. Elle se teste
 * donc directement contre les vecteurs de la RFC 6238.
 *
 * <p><strong>Fenêtre de tolérance.</strong> {@link #matches} accepte le
 * pas courant et les {@code tolerance} pas voisins, afin d'absorber la
 * dérive d'horloge du téléphone et le temps de saisie. La protection
 * contre le rejeu n'est pas ici : elle est portée par
 * {@code MfaCredential#lastUsedStep}, qui refuse un pas déjà consommé.
 */
public final class TotpGenerator {

    /** Pas de temps de la RFC 6238, en secondes. */
    public static final long STEP_SECONDS = 30L;

    private static final int DIGITS = 6;
    private static final int MODULO = 1_000_000;
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    /** Alphabet base32 de la RFC 4648, celui qu'attendent les applications TOTP. */
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    /** 20 octets = taille du bloc HMAC-SHA1 ; en dessous, le secret est le maillon faible. */
    private static final int SECRET_BYTES = 20;

    private TotpGenerator() {
    }

    /** Secret partagé aléatoire, encodé en base32 sans remplissage. */
    public static String newSecret(SecureRandom random) {
        byte[] raw = new byte[SECRET_BYTES];
        random.nextBytes(raw);
        return encodeBase32(raw);
    }

    /** Pas de temps correspondant à un instant. */
    public static long stepOf(Instant instant) {
        return Math.floorDiv(instant.getEpochSecond(), STEP_SECONDS);
    }

    /** Code à six chiffres, zéros de tête compris, pour un pas donné. */
    public static String codeAt(String base32Secret, long step) {
        byte[] key = decodeBase32(base32Secret);
        byte[] counter = ByteBuffer.allocate(Long.BYTES).putLong(step).array();
        byte[] mac = hmac(key, counter);
        // Troncature dynamique de la RFC 4226 §5.3.
        int offset = mac[mac.length - 1] & 0x0F;
        int binary = ((mac[offset] & 0x7F) << 24)
                | ((mac[offset + 1] & 0xFF) << 16)
                | ((mac[offset + 2] & 0xFF) << 8)
                | (mac[offset + 3] & 0xFF);
        return String.format(Locale.ROOT, "%0" + DIGITS + "d", binary % MODULO);
    }

    /**
     * Vérifie un code et renvoie le pas de temps qui l'a produit, ou
     * {@code null} si aucun pas de la fenêtre ne correspond.
     *
     * <p>Renvoyer le pas — et non un simple booléen — est ce qui permet à
     * l'appelant d'interdire le rejeu : un pas déjà consommé est refusé
     * même si le code est arithmétiquement correct.
     */
    public static Long matches(String base32Secret, String candidate, Instant now, int tolerance) {
        if (candidate == null) {
            return null;
        }
        String normalized = candidate.trim().replace(" ", "");
        if (normalized.length() != DIGITS || !normalized.chars().allMatch(Character::isDigit)) {
            return null;
        }
        long current = stepOf(now);
        for (long step = current - tolerance; step <= current + tolerance; step++) {
            if (constantTimeEquals(codeAt(base32Secret, step), normalized)) {
                return step;
            }
        }
        return null;
    }

    /**
     * URI {@code otpauth://} à encoder dans un QR code par le client.
     * Ne contient que l'adresse de l'utilisateur et le nom de l'émetteur :
     * c'est un secret d'enrôlement, jamais journalisé.
     */
    public static String provisioningUri(String issuer, String accountName, String base32Secret) {
        return "otpauth://totp/"
                + urlEncode(issuer) + ":" + urlEncode(accountName)
                + "?secret=" + base32Secret
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 indisponible", e);
        }
    }

    /** Comparaison à temps constant : un code TOTP se devine chiffre par chiffre sinon. */
    private static boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < left.length(); i++) {
            difference |= left.charAt(i) ^ right.charAt(i);
        }
        return difference == 0;
    }

    public static String encodeBase32(byte[] raw) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : raw) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return out.toString();
    }

    public static byte[] decodeBase32(String encoded) {
        String normalized = encoded.trim().toUpperCase(Locale.ROOT).replace("=", "");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < normalized.length(); i++) {
            int value = BASE32_ALPHABET.indexOf(normalized.charAt(i));
            if (value < 0) {
                throw new IllegalArgumentException("Secret base32 invalide.");
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }
}
