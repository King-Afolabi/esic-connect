package com.esic.connect.planning.internal;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Contrôles de sécurité du fichier téléversé <em>avant</em> tout parsing
 * (aligné sur {@code studentimport.internal.CsvFileGuard} —
 * {@code DEC-G1-003} : duplication minimale plutôt qu'extraction vers
 * {@code shared}, pour ne pas toucher au module {@code studentimport}).
 * Composant pur : opère sur le nom, le type déclaré et le contenu binaire.
 * Le contenu n'est pas conservé ; seuls le nom assaini, l'empreinte
 * SHA-256 et la taille le seront.
 *
 * <ul>
 *   <li>extension {@code .csv} exigée ;</li>
 *   <li>{@code Content-Type} restreint à une liste tolérante ;</li>
 *   <li>rejet des contenus binaires : octet nul, {@code PK\x03\x04}
 *       (ZIP / XLSX), OLE2 ({@code D0 CF 11 E0}), {@code %PDF} ;</li>
 *   <li>taille bornée ;</li>
 *   <li>décodage UTF-8 strict (BOM toléré et retiré).</li>
 * </ul>
 */
final class PlanningCsvGuard {

    static final Set<String> TOLERATED_CONTENT_TYPES = Set.of(
            "text/csv", "application/csv", "text/plain", "application/vnd.ms-excel",
            "application/octet-stream");

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};
    private static final char BOM = '﻿';

    private PlanningCsvGuard() {
    }

    static String decodeAndValidate(String fileName, String contentType, byte[] content, long maxBytes) {
        if (content == null || content.length == 0) {
            throw new PlanningException(PlanningException.Kind.FILE_UNREADABLE);
        }
        if (content.length > maxBytes) {
            throw new PlanningException(PlanningException.Kind.UNSUPPORTED_FILE);
        }
        requireCsvExtension(fileName);
        requireToleratedContentType(contentType);
        rejectBinaryContent(content);
        return stripBom(decodeStrictUtf8(content));
    }

    private static void requireCsvExtension(String fileName) {
        String base = PlanningCsvValues.sanitizeFileName(fileName);
        if (!base.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new PlanningException(PlanningException.Kind.UNSUPPORTED_FILE);
        }
    }

    private static void requireToleratedContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return;
        }
        String bare = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!TOLERATED_CONTENT_TYPES.contains(bare)) {
            throw new PlanningException(PlanningException.Kind.UNSUPPORTED_FILE);
        }
    }

    private static void rejectBinaryContent(byte[] content) {
        if (startsWith(content, ZIP_MAGIC) || startsWith(content, OLE2_MAGIC) || startsWith(content, PDF_MAGIC)) {
            throw new PlanningException(PlanningException.Kind.UNSUPPORTED_FILE);
        }
        for (byte b : content) {
            if (b == 0x00) {
                throw new PlanningException(PlanningException.Kind.UNSUPPORTED_FILE);
            }
        }
    }

    private static String decodeStrictUtf8(byte[] content) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException invalidUtf8) {
            throw new PlanningException(PlanningException.Kind.UNSUPPORTED_FILE);
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
