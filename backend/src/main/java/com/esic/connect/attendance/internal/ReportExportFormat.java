package com.esic.connect.attendance.internal;

import com.esic.connect.document.DocumentRenderer;

import java.util.Locale;

/**
 * Format d'export d'un rapport (EF-REP-003, EF-REP-004, EF-REP-005).
 *
 * <p>Une <strong>liste fermée</strong>, résolue côté serveur : un format
 * inconnu produit un {@code 400} explicite, jamais un {@code 500} ni un
 * repli silencieux sur le CSV — l'exploitant qui demande un PDF doit
 * savoir qu'il n'en a pas eu un.
 */
enum ReportExportFormat {

    CSV("csv", "text/csv"),
    XLSX("xlsx", DocumentRenderer.SPREADSHEET_MEDIA_TYPE),
    PDF("pdf", "application/pdf");

    private final String extension;
    private final String mediaType;

    ReportExportFormat(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    String extension() {
        return extension;
    }

    String mediaType() {
        return mediaType;
    }

    static ReportExportFormat parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return CSV;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "csv" -> CSV;
            case "xlsx", "excel" -> XLSX;
            case "pdf" -> PDF;
            default -> throw new AttendanceException(AttendanceException.Kind.REPORT_INVALID_FILTER);
        };
    }
}
