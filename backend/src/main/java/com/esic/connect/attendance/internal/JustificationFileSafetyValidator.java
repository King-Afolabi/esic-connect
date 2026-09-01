package com.esic.connect.attendance.internal;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Contrôles de sécurité d'un fichier de pièce jointe de justificatif
 * <em>avant</em> toute écriture (bloc G1-E ; DEC-G1-008 / DEC-G1-009
 * étape 2 ; CDC §21.5). Composant <strong>pur</strong> : opère sur le nom
 * déclaré, le type déclaré et un préfixe du contenu binaire — jamais sur
 * un chemin client, jamais d'écriture disque.
 *
 * <ul>
 *   <li>extension dans {@code .pdf} / {@code .jpg} / {@code .jpeg} / {@code .png} ;</li>
 *   <li>{@code Content-Type} déclaré dans une liste tolérante (ou absent) ;</li>
 *   <li>signature binaire (magic bytes) : {@code %PDF-}, JPEG {@code FF D8 FF},
 *       PNG {@code 89 50 4E 47 0D 0A 1A 0A} — le type retenu est
 *       <strong>re-dérivé</strong> du contenu, jamais celui déclaré ;</li>
 *   <li>rejet ZIP ({@code PK\x03\x04}) et OLE2 ({@code D0 CF 11 E0}) —
 *       polyglotte / conteneur ;</li>
 *   <li>cohérence extension ↔ contenu (un {@code .png} doit être un PNG) ;</li>
 *   <li>taille bornée, fichier vide rejeté ;</li>
 *   <li>nom d'origine <strong>assaini</strong> (basename, sans caractère
 *       de contrôle, borné) pour le seul affichage.</li>
 * </ul>
 */
final class JustificationFileSafetyValidator {

    /** Types MIME déclarés tolérés — les navigateurs varient. */
    static final Set<String> TOLERATED_DECLARED_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/jpg", "image/png", "application/octet-stream");

    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46, 0x2D}; // %PDF-
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    private JustificationFileSafetyValidator() {
    }

    /** Résultat d'une validation réussie. */
    record Validated(String safeFileName, String contentType, long sizeBytes) {
    }

    /**
     * @param fileName    nom d'origine (peut contenir un chemin — seul le basename compte)
     * @param declaredType {@code Content-Type} déclaré, éventuellement {@code null} / vide
     * @param content     octets bruts reçus
     * @param maxBytes    taille maximale autorisée
     * @throws JustificationAttachmentValidationException si un contrôle échoue
     */
    static Validated validate(String fileName, String declaredType, byte[] content, long maxBytes) {
        if (content == null || content.length == 0) {
            throw fail(JustificationAttachmentValidationException.Kind.EMPTY, "Fichier vide.");
        }
        if (content.length > maxBytes) {
            throw fail(JustificationAttachmentValidationException.Kind.TOO_LARGE, "Fichier trop volumineux.");
        }

        String base = sanitizeFileName(fileName);
        String extension = extensionOf(base);
        if (!Set.of("pdf", "jpg", "jpeg", "png").contains(extension)) {
            throw fail(JustificationAttachmentValidationException.Kind.EXTENSION_NOT_ALLOWED,
                    "Extension non autorisée (PDF, JPEG ou PNG attendu).");
        }

        if (declaredType != null && !declaredType.isBlank()) {
            String bare = declaredType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!TOLERATED_DECLARED_TYPES.contains(bare)) {
                throw fail(JustificationAttachmentValidationException.Kind.DECLARED_TYPE_NOT_ALLOWED,
                        "Type de fichier déclaré non autorisé.");
            }
        }

        if (startsWith(content, ZIP_MAGIC) || startsWith(content, OLE2_MAGIC)) {
            throw fail(JustificationAttachmentValidationException.Kind.ARCHIVE_REJECTED,
                    "Archive ou conteneur non autorisé.");
        }

        String detected;
        if (startsWith(content, PDF_MAGIC)) {
            detected = "application/pdf";
        } else if (startsWith(content, JPEG_MAGIC)) {
            detected = "image/jpeg";
        } else if (startsWith(content, PNG_MAGIC)) {
            detected = "image/png";
        } else {
            throw fail(JustificationAttachmentValidationException.Kind.CONTENT_NOT_RECOGNISED,
                    "Contenu non reconnu (PDF, JPEG ou PNG attendu).");
        }

        String expectedForExtension = switch (extension) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            default -> "image/jpeg"; // jpg | jpeg
        };
        if (!expectedForExtension.equals(detected)) {
            throw fail(JustificationAttachmentValidationException.Kind.EXTENSION_CONTENT_MISMATCH,
                    "L'extension ne correspond pas au contenu réel.");
        }

        return new Validated(base, detected, content.length);
    }

    // ------------------------------------------------------------------

    /** Basename sans chemin, sans caractère de contrôle, borné à 255. */
    static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "piece-jointe";
        }
        String base = fileName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        StringBuilder sb = new StringBuilder(base.length());
        base.chars().forEach(c -> {
            if (c >= 0x20 && c != 0x7F) {
                sb.append((char) c);
            }
        });
        String cleaned = sb.toString().strip();
        if (cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned)) {
            return "piece-jointe";
        }
        return cleaned.length() > 255 ? cleaned.substring(cleaned.length() - 255) : cleaned;
    }

    private static String extensionOf(String base) {
        int dot = base.lastIndexOf('.');
        return dot >= 0 && dot < base.length() - 1
                ? base.substring(dot + 1).toLowerCase(Locale.ROOT)
                : "";
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) {
            return false;
        }
        return Arrays.equals(content, 0, magic.length, magic, 0, magic.length);
    }

    private static JustificationAttachmentValidationException fail(
            JustificationAttachmentValidationException.Kind kind, String message) {
        return new JustificationAttachmentValidationException(kind, message);
    }
}
