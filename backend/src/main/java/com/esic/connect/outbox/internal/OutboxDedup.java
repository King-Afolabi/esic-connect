package com.esic.connect.outbox.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Clé d'idempotence d'une ligne d'outbox : SHA-256 hexadécimal de
 * {@code messageType | dedupKey}.
 *
 * <p>Le hachage sert deux buts. Il donne une clé de <strong>longueur
 * fixe</strong>, indexable en {@code CHAR(64) UNIQUE} quelle que soit la
 * chaîne fournie par l'appelant. Et il évite qu'une clé lisible — qui
 * peut contenir un identifiant métier — se retrouve telle quelle dans une
 * table consultable depuis un écran d'exploitation.
 */
final class OutboxDedup {

    private OutboxDedup() {
    }

    static String key(String messageType, String rawKey) {
        String raw = messageType + '|' + rawKey;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponible", impossible);
        }
    }
}
