package com.esic.connect.notification.internal;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

/**
 * Signature VAPID (<strong>RFC 8292</strong>) : le jeton qui prouve au
 * service de poussée l'identité de l'expéditeur.
 *
 * <p>C'est ce qui empêche n'importe qui, connaissant une URL de
 * terminaison, de pousser des messages vers l'appareil d'un utilisateur.
 * Le service refuse tout message dont la signature ne correspond pas à la
 * clé publique déclarée lors de l'abonnement.
 *
 * <p><strong>Conversion de signature.</strong> Java produit une signature
 * ECDSA au format DER ; JOSE (ES256) attend la concaténation brute de
 * {@code r} et {@code s} sur 32 octets chacun. Omettre cette conversion
 * donne un jeton syntaxiquement correct que tout service rejette — panne
 * silencieuse s'il en est.
 */
class VapidSigner {

    private static final String HEADER =
            "{\"typ\":\"JWT\",\"alg\":\"ES256\"}";
    /** Durée de validité du jeton ; la RFC 8292 §2 impose au plus 24 h. */
    private static final Duration TOKEN_TTL = Duration.ofHours(12);

    private final ECPrivateKey privateKey;
    private final String publicKeyB64;
    private final String subject;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    VapidSigner(ECPrivateKey privateKey, String publicKeyB64, String subject,
                ObjectMapper objectMapper, Clock clock) {
        this.privateKey = privateKey;
        this.publicKeyB64 = publicKeyB64;
        this.subject = subject;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * @param endpoint URL de terminaison ; seule son <em>origine</em> entre
     *                 dans la revendication {@code aud}, jamais le chemin —
     *                 celui-ci contient le jeton propre à l'appareil.
     * @return la valeur de l'en-tête {@code Authorization}
     */
    String authorizationHeader(String endpoint) {
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getHost()
                + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        Map<String, Object> claims = Map.of(
                "aud", audience,
                "exp", clock.instant().plus(TOKEN_TTL).getEpochSecond(),
                "sub", subject);
        String signingInput = WebPushCrypto.encode(HEADER.getBytes(StandardCharsets.UTF_8))
                + '.' + WebPushCrypto.encode(toJson(claims));
        String signature = WebPushCrypto.encode(sign(signingInput.getBytes(StandardCharsets.US_ASCII)));
        return "vapid t=" + signingInput + '.' + signature + ", k=" + publicKeyB64;
    }

    private byte[] toJson(Map<String, Object> claims) {
        try {
            return objectMapper.writeValueAsBytes(claims);
        } catch (com.fasterxml.jackson.core.JsonProcessingException impossible) {
            throw new IllegalStateException("Revendications VAPID non serialisables", impossible);
        }
    }

    private byte[] sign(byte[] signingInput) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(signingInput);
            return derToJose(signature.sign());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Signature VAPID impossible", failure);
        }
    }

    /** SEQUENCE { INTEGER r, INTEGER s } -> r||s sur 64 octets. */
    private static byte[] derToJose(byte[] der) {
        int offset = 3;
        if (der[1] == (byte) 0x81) {
            offset = 4; // longueur sur un octet supplémentaire
        }
        int rLength = der[offset - 1];
        int sOffset = offset + rLength + 2;
        int sLength = der[sOffset - 1];
        BigInteger r = new BigInteger(Arrays.copyOfRange(der, offset, offset + rLength));
        BigInteger s = new BigInteger(Arrays.copyOfRange(der, sOffset, sOffset + sLength));
        byte[] jose = new byte[64];
        copyRightAligned(r, jose, 0);
        copyRightAligned(s, jose, 32);
        return jose;
    }

    private static void copyRightAligned(BigInteger value, byte[] target, int offset) {
        byte[] bytes = value.toByteArray();
        int start = (bytes.length > 32) ? bytes.length - 32 : 0;
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, start, target, offset + 32 - length, length);
    }
}
