package com.esic.connect.identity.internal;

import com.esic.connect.document.DocumentIdentity;
import com.esic.connect.document.DocumentRenderer;
import com.esic.connect.document.TabularDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Rapport des invitations non activées (EF-REP-010).
 *
 * <p>Mêmes rôles que le suivi des invitations : c'est le même besoin —
 * savoir qui n'est pas entré, et relancer.
 */
@RestController
@RequestMapping("/api/v1/reports/pending-invitations")
class PendingInvitationReportController {

    static final String ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','PEDAGOGICAL_MANAGER','SCHOOL_ADMINISTRATION')";
    private static final String DEFAULT_ISSUER =
            "ESIC — École Supérieure d'Informatique et de Commerce";

    private final PendingInvitationReportService service;
    private final DocumentRenderer renderer;
    private final Clock clock;
    private final String issuer;

    PendingInvitationReportController(PendingInvitationReportService service,
                                      DocumentRenderer renderer,
                                      Clock clock,
                                      @Value("${app.reporting.issuer:" + DEFAULT_ISSUER + "}") String issuer) {
        this.service = service;
        this.renderer = renderer;
        this.clock = clock;
        this.issuer = issuer;
    }

    @GetMapping
    @PreAuthorize(ROLES)
    List<PendingInvitationReportService.PendingInvitationRow> list() {
        return service.rows();
    }

    @GetMapping("/export")
    @PreAuthorize(ROLES)
    ResponseEntity<byte[]> export(@RequestParam(required = false) String format) {
        TabularDocument document = service.document(clock.getZone());
        Instant now = clock.instant();
        String target = format == null ? "csv" : format.trim().toLowerCase(Locale.ROOT);
        byte[] body;
        String mediaType;
        String extension;
        switch (target) {
            case "xlsx", "excel" -> {
                body = renderer.toSpreadsheet(document);
                mediaType = DocumentRenderer.SPREADSHEET_MEDIA_TYPE;
                extension = "xlsx";
            }
            case "pdf" -> {
                body = renderer.toPdf(document, new DocumentIdentity(
                        "INVITATIONS-" + now.toEpochMilli(), issuer, "Système ESIC Connect", now));
                mediaType = MediaType.APPLICATION_PDF_VALUE;
                extension = "pdf";
            }
            case "csv" -> {
                body = renderer.toCsv(document);
                mediaType = "text/csv";
                extension = "csv";
            }
            default -> throw new InvitationException(InvitationException.Kind.INVALID_EXPORT_FORMAT);
        }
        String name = "invitations-non-activees_" + now.toEpochMilli() + "." + extension;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(mediaType))
                .body(body);
    }
}
