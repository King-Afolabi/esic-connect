package com.esic.connect.planning.internal;

import java.util.Locale;
import java.util.Set;

/**
 * Contrôles de sécurité d'un classeur de planning avant tout parsing
 * (EF-PLAN-011 ; mêmes principes que {@link PlanningCsvGuard}).
 *
 * <p>Le fichier n'est <strong>jamais écrit sur disque</strong> (RG-036) ;
 * seuls le nom assaini, l'empreinte et la taille sont conservés.
 *
 * <p>Un `.xlsx` est un ZIP : la magie ZIP est donc exigée ici, alors
 * qu'elle est rejetée pour un CSV. Le format binaire ancien `.xls`
 * (OLE2) reste refusé — le cahier ne demande que `.xlsx`, et OLE2 ouvre
 * la porte aux macros.
 */
final class PlanningWorkbookGuard {

    static final Set<String> TOLERATED_CONTENT_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/octet-stream",
            "application/zip");

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    private PlanningWorkbookGuard() {
    }

    static byte[] validate(String fileName, String contentType, byte[] content, long maxBytes) {
        if (content == null || content.length == 0) {
            throw new PlanningException(PlanningException.Kind.FILE_UNREADABLE);
        }
        if (content.length > maxBytes) {
            throw new PlanningException(PlanningException.Kind.UNSUPPORTED_FILE);
        }
        if (contentType != null && !contentType.isBlank()) {
            String bare = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!TOLERATED_CONTENT_TYPES.contains(bare)) {
                throw new PlanningException(PlanningException.Kind.UNSUPPORTED_FILE);
            }
        }
        // Le type RÉEL est dérivé du contenu, jamais du nom : un fichier
        // renommé en `.xlsx` est rejeté ici.
        if (startsWith(content, OLE2_MAGIC) || !startsWith(content, ZIP_MAGIC)) {
            throw new PlanningException(PlanningException.Kind.UNSUPPORTED_FILE);
        }
        return content;
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
