package com.esic.connect.identity.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Chiffrement au repos des secrets partagés TOTP (docs/02 §17.3).
 *
 * <p>Un secret TOTP n'est pas un mot de passe : il ne peut pas être
 * haché, le serveur doit pouvoir le relire pour recalculer le code. Il
 * est donc chiffré en <strong>AES-256-GCM</strong>, avec un vecteur
 * d'initialisation aléatoire par enregistrement, concaténé au
 * cryptogramme puis encodé en base64. GCM est authentifié : une valeur
 * altérée en base est rejetée au déchiffrement, elle ne produit pas un
 * secret silencieusement faux.
 *
 * <p><strong>Provenance de la clé.</strong> Elle est lue dans
 * {@code app.security.mfa.encryption-key} (variable d'environnement
 * {@code MFA_ENCRYPTION_KEY}), jamais dans le dépôt. Si cette valeur est
 * absente, la clé est <em>dérivée</em> du secret de signature JWT par un
 * SHA-256 sur une chaîne de séparation de domaine : le produit démarre
 * donc sans configuration supplémentaire en local, sans jamais recourir
 * à une clé écrite en dur. Un avertissement est journalisé, car ce repli
 * lie la rotation des deux secrets et n'est pas souhaitable en
 * production.
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    /** Séparation de domaine : la clé dérivée ne peut pas servir à signer un JWT. */
    private static final String DERIVATION_LABEL = "esic-connect:mfa-secret-encryption:v1:";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${app.security.mfa.encryption-key:}") String configuredKey,
                        @Value("${app.security.jwt.secret}") String jwtSecret) {
        String material;
        if (configuredKey == null || configuredKey.isBlank()) {
            log.warn("MFA_ENCRYPTION_KEY absente : la clé de chiffrement des secrets TOTP est "
                    + "dérivée du secret JWT. Acceptable en local, à renseigner en production.");
            material = DERIVATION_LABEL + jwtSecret;
        } else {
            material = DERIVATION_LABEL + configuredKey;
        }
        this.key = new SecretKeySpec(sha256(material), "AES");
    }

    /** Chiffre une valeur en clair ; le résultat est {@code base64(iv || cryptogramme)}. */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, envelope, 0, iv.length);
            System.arraycopy(encrypted, 0, envelope, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(envelope);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Chiffrement AES-GCM indisponible", e);
        }
    }

    /**
     * Déchiffre une valeur produite par {@link #encrypt}.
     *
     * @throws IllegalStateException si la valeur a été altérée ou si la
     *                               clé a changé — jamais un secret faux
     */
    public String decrypt(String envelopeBase64) {
        try {
            byte[] envelope = Base64.getDecoder().decode(envelopeBase64);
            if (envelope.length <= IV_BYTES) {
                throw new IllegalStateException("Cryptogramme MFA tronqué.");
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(envelope, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(envelope, IV_BYTES, envelope.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Secret MFA illisible : altéré ou clé changée.", e);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
