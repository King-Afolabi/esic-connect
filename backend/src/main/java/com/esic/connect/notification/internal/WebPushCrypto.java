package com.esic.connect.notification.internal;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Chiffrement de bout en bout d'un message de poussée web :
 * <strong>RFC 8291</strong> (Message Encryption for Web Push) sur le
 * codage de contenu <strong>{@code aes128gcm}</strong> de la RFC 8188.
 *
 * <p><strong>Pourquoi c'est écrit ici plutôt que délégué.</strong> Le
 * contenu poussé est chiffré pour l'appareil de l'abonné : le service de
 * poussée — Google, Mozilla, Apple — relaie un message qu'il ne peut pas
 * lire. C'est la propriété qui rend la poussée acceptable au regard du
 * cahier (§29.3, « le contenu poussé ne comporte aucune donnée
 * sensible »), et elle repose entièrement sur ces quelques dérivations.
 * Elles sont donc vérifiées contre le <em>vecteur de test officiel de la
 * RFC 8291 §5</em> — le seul contrôle qui prouve que l'implémentation est
 * conforme, et non simplement cohérente avec elle-même.
 *
 * <p>Rien ici n'est journalisé : ni clé, ni secret, ni clair, ni chiffré.
 */
final class WebPushCrypto {

    private static final String CURVE = "secp256r1";
    private static final byte[] KEY_INFO_PREFIX = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);
    /** Taille d'enregistrement annoncée dans l'en-tête ; un seul enregistrement est produit. */
    private static final int RECORD_SIZE = 4096;
    private static final int SALT_LENGTH = 16;
    private static final int UNCOMPRESSED_POINT_LENGTH = 65;

    private final SecureRandom random = new SecureRandom();

    /**
     * Chiffre {@code plaintext} pour l'abonné, avec un sel et une paire
     * éphémère tirés au hasard.
     *
     * @param uaPublicKeyB64 clé publique de l'abonné (base64url, point non compressé)
     * @param authSecretB64  secret d'authentification de l'abonné (base64url, 16 octets)
     * @return le corps {@code aes128gcm} complet — en-tête compris
     */
    byte[] encrypt(String uaPublicKeyB64, String authSecretB64, byte[] plaintext) {
        try {
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(CURVE), random);
            KeyPair ephemeral = generator.generateKeyPair();
            return encrypt(uaPublicKeyB64, authSecretB64, plaintext, salt,
                    (ECPrivateKey) ephemeral.getPrivate(), (ECPublicKey) ephemeral.getPublic());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Chiffrement de poussee impossible", failure);
        }
    }

    /**
     * Variante déterministe : sel et paire éphémère fournis. Existe
     * <strong>pour la vérification contre le vecteur de la RFC</strong> —
     * sans elle, la conformité ne serait pas démontrable, puisque chaque
     * exécution produirait un résultat différent.
     */
    byte[] encrypt(String uaPublicKeyB64, String authSecretB64, byte[] plaintext,
                   byte[] salt, ECPrivateKey asPrivate, ECPublicKey asPublic) {
        try {
            byte[] uaPublicBytes = decode(uaPublicKeyB64);
            byte[] authSecret = decode(authSecretB64);
            ECPublicKey uaPublic = publicKeyOf(uaPublicBytes);
            byte[] asPublicBytes = encodePoint(asPublic);

            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(asPrivate);
            agreement.doPhase(uaPublic, true);
            byte[] ecdhSecret = agreement.generateSecret();

            // RFC 8291 §3.4 — combinaison des clés.
            byte[] prkKey = hmac(authSecret, ecdhSecret);
            byte[] keyInfo = concat(KEY_INFO_PREFIX, uaPublicBytes, asPublicBytes, new byte[] {1});
            byte[] ikm = hmac(prkKey, keyInfo);

            // RFC 8188 §2.2 — dérivation de la clé et du nonce.
            byte[] prk = hmac(salt, ikm);
            byte[] cek = Arrays.copyOf(hmac(prk, concat(CEK_INFO, new byte[] {1})), 16);
            byte[] nonce = Arrays.copyOf(hmac(prk, concat(NONCE_INFO, new byte[] {1})), 12);

            // Enregistrement unique : le clair est suivi du délimiteur 0x02
            // (« dernier enregistrement »), puis chiffré d'un bloc.
            byte[] padded = concat(plaintext, new byte[] {2});
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"),
                    new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(padded);

            // En-tête RFC 8188 : sel | taille d'enregistrement | longueur
            // de l'identifiant de clé | identifiant (ici la clé publique
            // éphémère de l'expéditeur, comme l'impose la RFC 8291).
            ByteBuffer header = ByteBuffer.allocate(SALT_LENGTH + 4 + 1 + asPublicBytes.length);
            header.put(salt);
            header.putInt(RECORD_SIZE);
            header.put((byte) asPublicBytes.length);
            header.put(asPublicBytes);
            return concat(header.array(), ciphertext);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Chiffrement de poussee impossible", failure);
        }
    }

    /** Reconstruit une clé privée P-256 à partir de son scalaire brut (32 octets). */
    static ECPrivateKey privateKeyOf(byte[] raw) {
        try {
            ECParameterSpec params = curveParameters();
            KeyFactory factory = KeyFactory.getInstance("EC");
            return (ECPrivateKey) factory.generatePrivate(
                    new ECPrivateKeySpec(new BigInteger(1, raw), params));
        } catch (GeneralSecurityException failure) {
            throw new IllegalArgumentException("Cle privee P-256 invalide", failure);
        }
    }

    /** Reconstruit une clé publique P-256 depuis un point non compressé (0x04 | X | Y). */
    static ECPublicKey publicKeyOf(byte[] point) {
        if (point.length != UNCOMPRESSED_POINT_LENGTH || point[0] != 0x04) {
            throw new IllegalArgumentException("Point P-256 non compresse attendu");
        }
        try {
            BigInteger x = new BigInteger(1, Arrays.copyOfRange(point, 1, 33));
            BigInteger y = new BigInteger(1, Arrays.copyOfRange(point, 33, 65));
            KeyFactory factory = KeyFactory.getInstance("EC");
            return (ECPublicKey) factory.generatePublic(
                    new ECPublicKeySpec(new ECPoint(x, y), curveParameters()));
        } catch (GeneralSecurityException failure) {
            throw new IllegalArgumentException("Cle publique P-256 invalide", failure);
        }
    }

    /** Encode une clé publique P-256 en point non compressé de 65 octets. */
    static byte[] encodePoint(ECPublicKey key) {
        byte[] x = unsigned(key.getW().getAffineX());
        byte[] y = unsigned(key.getW().getAffineY());
        byte[] point = new byte[UNCOMPRESSED_POINT_LENGTH];
        point[0] = 0x04;
        System.arraycopy(x, 0, point, 33 - x.length, x.length);
        System.arraycopy(y, 0, point, 65 - y.length, y.length);
        return point;
    }

    static byte[] decode(String base64Url) {
        return Base64.getUrlDecoder().decode(pad(base64Url));
    }

    static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    /**
     * Les navigateurs émettent du base64url <em>sans</em> remplissage, mais
     * pas tous : le décodeur du JDK refuse une longueur non multiple de 4.
     * On complète plutôt que de rejeter un abonnement valide.
     */
    private static String pad(String value) {
        int remainder = value.length() % 4;
        return remainder == 0 ? value : value + "=".repeat(4 - remainder);
    }

    private static ECParameterSpec curveParameters() throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(CURVE));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    private static byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        // BigInteger ajoute un octet de signe nul devant un nombre dont le
        // bit de poids fort est à 1 : il n'a pas sa place dans une
        // coordonnée de 32 octets.
        return (bytes.length > 1 && bytes[0] == 0) ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
