package com.esic.connect.studentimport.internal;

import java.util.Locale;
import java.util.Set;

/**
 * Contrôles de sécurité d'un classeur Excel <em>avant</em> tout parsing
 * (EF-IMP-003 ; mêmes principes que {@link CsvFileGuard}).
 *
 * <p>Composant pur : opère sur le nom, le type déclaré et le contenu
 * binaire. Le fichier n'est <strong>jamais écrit sur disque</strong>
 * (RG-036) ; seuls le nom assaini, l'empreinte et la taille sont
 * conservés.
 *
 * <p><strong>Un `.xlsx` est un ZIP.</strong> C'est précisément ce que
 * {@link CsvFileGuard} rejette, et à juste titre pour un CSV. Ici la
 * magie ZIP est donc <em>exigée</em> — mais rien d'autre : le format
 * binaire ancien `.xls` (OLE2) est refusé, parce que le cahier ne demande
 * que `.xlsx` (docs/02 §10.1) et qu'accepter OLE2 ouvrirait la porte aux
 * macros et aux objets liés.
 */
final class WorkbookFileGuard {

    /** Types déclarés tolérés — les navigateurs et systèmes varient. */
    static final Set<String> TOLERATED_CONTENT_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/octet-stream",
            "application/zip");

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    private WorkbookFileGuard() {
    }

    /** Vrai si le nom de fichier annonce un classeur `.xlsx`. */
    static boolean looksLikeWorkbook(String fileName) {
        return CsvValueNormalizer.sanitizeFileName(fileName)
                .toLowerCase(Locale.ROOT)
                .endsWith(".xlsx");
    }

    /**
     * Valide le classeur et renvoie ses octets, prêts à être ouverts.
     *
     * @throws StudentImportException si un contrôle échoue ; le message ne
     *                                contient aucune donnée personnelle
     */
    static byte[] validate(String fileName, String contentType, byte[] content, long maxBytes) {
        if (content == null || content.length == 0) {
            throw new StudentImportException(StudentImportException.Kind.HEADER_UNREADABLE);
        }
        if (content.length > maxBytes) {
            throw new StudentImportException(StudentImportException.Kind.FILE_TOO_LARGE);
        }
        if (!looksLikeWorkbook(fileName)) {
            throw new StudentImportException(StudentImportException.Kind.UNSUPPORTED_MEDIA_TYPE);
        }
        requireToleratedContentType(contentType);
        // Le TYPE RÉEL est dérivé du contenu, jamais du nom ni de l'en-tête
        // déclaré : un fichier renommé en `.xlsx` est rejeté ici.
        if (startsWith(content, OLE2_MAGIC)) {
            throw new StudentImportException(StudentImportException.Kind.UNSUPPORTED_MEDIA_TYPE);
        }
        if (!startsWith(content, ZIP_MAGIC)) {
            throw new StudentImportException(StudentImportException.Kind.UNSUPPORTED_MEDIA_TYPE);
        }
        return content;
    }

    private static void requireToleratedContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return;
        }
        String bare = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!TOLERATED_CONTENT_TYPES.contains(bare)) {
            throw new StudentImportException(StudentImportException.Kind.UNSUPPORTED_MEDIA_TYPE);
        }
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
