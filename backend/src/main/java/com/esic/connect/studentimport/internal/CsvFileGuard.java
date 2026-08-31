package com.esic.connect.studentimport.internal;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Contrôles de sécurité appliqués au fichier téléversé <em>avant</em> tout
 * parsing (rapport §5.1, §5.4, §10). Composant pur : opère sur le nom, le
 * type déclaré et le contenu binaire — jamais sur un chemin fourni par le
 * client, jamais d'écriture sur disque. Le contenu du fichier n'est pas
 * conservé ; seuls le nom assaini, l'empreinte SHA-256 et la taille le
 * seront (checkpoints suivants).
 *
 * <ul>
 *   <li>extension {@code .csv} exigée ;</li>
 *   <li>{@code Content-Type} restreint à une liste tolérante ;</li>
 *   <li>rejet des contenus binaires : octet nul, magie {@code PK\x03\x04}
 *       (ZIP / XLSX / DOCX), OLE2 ({@code D0 CF 11 E0}), {@code %PDF} ;</li>
 *   <li>taille bornée ;</li>
 *   <li>décodage <strong>UTF-8 strict</strong> (BOM toléré et retiré) —
 *       toute séquence invalide lève {@link StudentImportException.Kind#ENCODING_INVALID}.</li>
 * </ul>
 */
final class CsvFileGuard {

    /** Types tolérés — les navigateurs varient (rapport §10 : « Content-Type toléré »). */
    static final Set<String> TOLERATED_CONTENT_TYPES = Set.of(
            "text/csv", "application/csv", "text/plain", "application/vnd.ms-excel",
            "application/octet-stream");

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};
    private static final char BOM = '﻿';

    private CsvFileGuard() {
    }

    /**
     * Valide le fichier et renvoie son contenu décodé en UTF-8, BOM retiré.
     *
     * @param fileName    nom d'origine (peut contenir un chemin — seul le basename est considéré)
     * @param contentType type MIME déclaré, éventuellement {@code null} / vide
     * @param content     octets bruts reçus
     * @param maxBytes    taille maximale autorisée
     * @throws StudentImportException si un contrôle échoue (aucune donnée personnelle dans le message)
     */
    static String decodeAndValidate(String fileName, String contentType, byte[] content, long maxBytes) {
        if (content == null || content.length == 0) {
            throw new StudentImportException(StudentImportException.Kind.HEADER_UNREADABLE);
        }
        if (content.length > maxBytes) {
            throw new StudentImportException(StudentImportException.Kind.FILE_TOO_LARGE);
        }
        requireCsvExtension(fileName);
        requireToleratedContentType(contentType);
        rejectBinaryContent(content);

        String decoded = decodeStrictUtf8(content);
        return stripBom(decoded);
    }

    private static void requireCsvExtension(String fileName) {
        String base = CsvValueNormalizer.sanitizeFileName(fileName);
        if (!base.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new StudentImportException(StudentImportException.Kind.UNSUPPORTED_MEDIA_TYPE);
        }
    }

    private static void requireToleratedContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return; // certains clients n'en envoient pas ; l'extension + le contenu font foi
        }
        String bare = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!TOLERATED_CONTENT_TYPES.contains(bare)) {
            throw new StudentImportException(StudentImportException.Kind.UNSUPPORTED_MEDIA_TYPE);
        }
    }

    private static void rejectBinaryContent(byte[] content) {
        if (startsWith(content, ZIP_MAGIC) || startsWith(content, OLE2_MAGIC) || startsWith(content, PDF_MAGIC)) {
            throw new StudentImportException(StudentImportException.Kind.UNSUPPORTED_MEDIA_TYPE);
        }
        for (byte b : content) {
            if (b == 0x00) {
                throw new StudentImportException(StudentImportException.Kind.UNSUPPORTED_MEDIA_TYPE);
            }
        }
    }

    private static String decodeStrictUtf8(byte[] content) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer decoded = decoder.decode(ByteBuffer.wrap(content));
            return decoded.toString();
        } catch (CharacterCodingException invalidUtf8) {
            throw new StudentImportException(StudentImportException.Kind.ENCODING_INVALID);
        }
    }

    private static String stripBom(String value) {
        return !value.isEmpty() && value.charAt(0) == BOM ? value.substring(1) : value;
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
