package com.esic.connect.audit.internal;

import com.esic.connect.document.DocumentIdentity;
import com.esic.connect.document.DocumentRenderer;
import com.esic.connect.document.TabularDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
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
import java.util.Locale;
import java.util.UUID;

/**
 * Consultation et export de la piste d'audit (EF-AUD-002 ;
 * docs/02 §23.4).
 *
 * <p>Réservé à {@code ADMIN} et {@code SUPER_ADMIN} : « consulter
 * l'audit fonctionnel » figure aux prérogatives de l'administration
 * fonctionnelle (§5.3), « consulter les journaux de sécurité » à celles
 * du super administrateur (§5.2). Ni responsable pédagogique, ni
 * formateur, ni apprenant — l'apprenant dispose de son
 * <em>journal de transparence</em>, qui est la bonne granularité pour
 * lui (EF-ATT-014).
 *
 * <p><strong>Aucune route d'écriture ni de suppression n'existe.</strong>
 */
@RestController
@RequestMapping("/api/v1/audit-events")
class AuditController {

    static final String AUDIT_ROLES = "hasAnyRole('ADMIN','SUPER_ADMIN')";

    private static final String DEFAULT_ISSUER =
            "ESIC — École Supérieure d'Informatique et de Commerce";

    private final AuditQueryService service;
    private final DocumentRenderer renderer;
    private final Clock clock;
    private final String issuer;

    AuditController(AuditQueryService service,
                    DocumentRenderer renderer,
                    Clock clock,
                    @Value("${app.reporting.issuer:" + DEFAULT_ISSUER + "}") String issuer) {
        this.service = service;
        this.renderer = renderer;
        this.clock = clock;
        this.issuer = issuer;
    }

    @GetMapping
    @PreAuthorize(AUDIT_ROLES)
    Page<AuditResponses.AuditRow> list(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String correlation,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(query(from, to, action, category, resourceType, result,
                actor, resource, correlation), page, size);
    }

    @GetMapping("/facets")
    @PreAuthorize(AUDIT_ROLES)
    AuditResponses.AuditFacets facets() {
        return service.facets();
    }

    /**
     * Export de la piste d'audit. Trois formats, même contenu — la
     * divergence entre formats est ce qui fait douter d'un export.
     */
    @GetMapping("/export")
    @PreAuthorize(AUDIT_ROLES)
    ResponseEntity<byte[]> export(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String correlation,
            @RequestParam(required = false) String format) {
        TabularDocument document = service.export(query(from, to, action, category, resourceType,
                result, actor, resource, correlation), clock.getZone());
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
                body = renderer.toPdf(document,
                        new DocumentIdentity("AUDIT-" + now.toEpochMilli(), issuer,
                                "Système ESIC Connect", now));
                mediaType = MediaType.APPLICATION_PDF_VALUE;
                extension = "pdf";
            }
            case "csv" -> {
                body = renderer.toCsv(document);
                mediaType = "text/csv";
                extension = "csv";
            }
            // Un format inconnu est refusé : renvoyer un CSV à qui a
            // demandé un PDF est un mensonge silencieux.
            default -> throw new AuditException(AuditException.Kind.INVALID_FORMAT);
        }
        String name = "audit_" + now.toEpochMilli() + "." + extension;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(mediaType))
                .body(body);
    }

    private static AuditQueryService.AuditQuery query(Instant from, Instant to, String action,
                                                      String category, String resourceType, String result,
                                                      String actor, String resource, String correlation) {
        return new AuditQueryService.AuditQuery(from, to, trim(action), trim(category),
                trim(resourceType), trim(result), uuid(actor), uuid(resource), uuid(correlation));
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Un identifiant mal formé filtre sur rien plutôt que de lever un 500. */
    private static UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException notAUuid) {
            throw new AuditException(AuditException.Kind.INVALID_FILTER);
        }
    }
}
