package com.esic.connect.attendance.internal;

import com.esic.connect.document.DocumentIdentity;
import com.esic.connect.document.DocumentRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Rapports d'assiduité (V10) : par séance, par classe, par apprenant, et
 * synthèse ; exports <strong>CSV, Excel {@code .xlsx} et PDF</strong>
 * (EF-REP-003, EF-REP-004, EF-REP-005) ; attestation d'assiduité
 * identifiable (EF-REP-006, AC-033). Réservés à
 * {@code ADMIN}/{@code SUPER_ADMIN}/{@code SCHOOL_ADMINISTRATION}/
 * {@code PEDAGOGICAL_MANAGER} (périmètre appliqué par
 * {@link AttendanceReportService}). Filtres {@code from}/{@code to}
 * (instants sur le début de séance), {@code classGroup}, {@code student}.
 * Pagination bornée (≤ 100). Les exports neutralisent l'injection de
 * formule — au classeur comme au CSV : le vecteur est le tableur qui
 * ouvre le fichier, pas son extension (AC-032).
 */
@RestController
@RequestMapping("/api/v1/attendance/reports")
class AttendanceReportController {

    private static final String DEFAULT_ISSUER =
            "ESIC — École Supérieure d'Informatique et de Commerce";

    private final AttendanceReportService service;
    private final DailyAttendanceService dailyService;
    private final AttestationService attestationService;
    private final DocumentRenderer renderer;
    private final AttendanceActorResolver actorResolver;
    private final Clock clock;
    private final String issuer;

    AttendanceReportController(AttendanceReportService service,
                               DailyAttendanceService dailyService,
                               AttestationService attestationService,
                               DocumentRenderer renderer,
                               AttendanceActorResolver actorResolver,
                               Clock clock,
                               @Value("${app.reporting.issuer:" + DEFAULT_ISSUER + "}") String issuer) {
        this.service = service;
        this.dailyService = dailyService;
        this.attestationService = attestationService;
        this.renderer = renderer;
        this.actorResolver = actorResolver;
        this.clock = clock;
        this.issuer = issuer;
    }

    /**
     * Résultat journalier d'une classe (EF-ATT-004 ; docs/02 §16.3) —
     * journée complète, demi-journée, partiel, à confirmer, absent,
     * excusé, entreprise ou non attendu, par apprenant.
     *
     * <p>{@code zone} : fuseau dans lequel la journée civile est
     * découpée. Sans lui, une séance de 8 h à Paris tomberait la veille
     * en UTC pendant l'heure d'été.
     */
    @GetMapping("/daily")
    @PreAuthorize(AttendanceManagementWeb.REPORT_ROLES)
    DailyAttendanceService.DailyReport daily(
            @RequestParam String classGroup,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate date,
            @RequestParam(required = false) String zone) {
        java.util.UUID classId;
        try {
            classId = java.util.UUID.fromString(classGroup);
        } catch (IllegalArgumentException notAUuid) {
            throw new AttendanceException(AttendanceException.Kind.INVALID_SUBMISSION);
        }
        java.time.ZoneId zoneId;
        try {
            zoneId = zone == null || zone.isBlank()
                    ? java.time.ZoneId.of("Europe/Paris")
                    : java.time.ZoneId.of(zone);
        } catch (java.time.DateTimeException unknownZone) {
            throw new AttendanceException(AttendanceException.Kind.INVALID_SUBMISSION);
        }
        return dailyService.compute(classId, date, zoneId);
    }

    // --- JSON ---------------------------------------------------------

    @GetMapping("/sessions")
    @PreAuthorize(AttendanceManagementWeb.REPORT_ROLES)
    PageResponse<AttendanceReports.SessionRow> sessions(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String classGroup,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.ofList(service.sessionReport(from, to, classGroup, sort), page, size);
    }

    @GetMapping("/classes")
    @PreAuthorize(AttendanceManagementWeb.REPORT_ROLES)
    PageResponse<AttendanceReports.ClassRow> classes(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String classGroup,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.ofList(service.classReport(from, to, classGroup, sort), page, size);
    }

    @GetMapping("/students")
    @PreAuthorize(AttendanceManagementWeb.REPORT_ROLES)
    PageResponse<AttendanceReports.StudentRow> students(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String classGroup,
            @RequestParam(required = false) String student,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.ofList(service.studentReport(from, to, classGroup, student, sort), page, size);
    }

    @GetMapping("/summary")
    @PreAuthorize(AttendanceManagementWeb.REPORT_ROLES)
    AttendanceReports.Summary summary(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String classGroup) {
        return service.summary(from, to, classGroup);
    }

    // --- Exports CSV / Excel / PDF ------------------------------------
    //
    // Un seul paramètre `format` plutôt que trois routes : le contenu du
    // rapport est identique, seule sa mise en forme change. Trois routes
    // laisseraient diverger les colonnes d'un même rapport selon le
    // bouton cliqué. Défaut `csv` : les appels existants ne changent pas.

    @GetMapping("/sessions/export")
    @PreAuthorize(AttendanceManagementWeb.REPORT_ROLES)
    ResponseEntity<byte[]> exportSessions(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String classGroup,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String format,
            @AuthenticationPrincipal Jwt caller) {
        ReportExportFormat target = ReportExportFormat.parse(format);
        List<AttendanceReports.SessionRow> rows = service.sessionReport(from, to, classGroup, sort);
        service.auditExport("sessions." + target.extension(), from, to, rows.size(),
                AttendanceManagementWeb.subject(caller));
        return render("attendance-sessions", from, to, target,
                AttendanceDocuments.sessions(rows, from, to, clock.getZone()),
                AttendanceManagementWeb.subject(caller));
    }

    @GetMapping("/classes/export")
    @PreAuthorize(AttendanceManagementWeb.REPORT_ROLES)
    ResponseEntity<byte[]> exportClasses(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String classGroup,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String format,
            @AuthenticationPrincipal Jwt caller) {
        ReportExportFormat target = ReportExportFormat.parse(format);
        List<AttendanceReports.ClassRow> rows = service.classReport(from, to, classGroup, sort);
        service.auditExport("classes." + target.extension(), from, to, rows.size(),
                AttendanceManagementWeb.subject(caller));
        return render("attendance-classes", from, to, target,
                AttendanceDocuments.classes(rows, from, to, clock.getZone()),
                AttendanceManagementWeb.subject(caller));
    }

    @GetMapping("/students/export")
    @PreAuthorize(AttendanceManagementWeb.REPORT_ROLES)
    ResponseEntity<byte[]> exportStudents(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String classGroup,
            @RequestParam(required = false) String student,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String format,
            @AuthenticationPrincipal Jwt caller) {
        ReportExportFormat target = ReportExportFormat.parse(format);
        List<AttendanceReports.StudentRow> rows =
                service.studentReport(from, to, classGroup, student, sort);
        service.auditExport("students." + target.extension(), from, to, rows.size(),
                AttendanceManagementWeb.subject(caller));
        return render("attendance-students", from, to, target,
                AttendanceDocuments.students(rows, from, to, clock.getZone()),
                AttendanceManagementWeb.subject(caller));
    }

    // --- Attestation d'assiduité (EF-REP-006, AC-033) ------------------

    /**
     * Émet une attestation pour un apprenant sur une période, l'inscrit au
     * registre et renvoie le PDF. L'identifiant du document est aussi
     * renvoyé en en-tête {@code X-Document-Id} : le client l'affiche sans
     * avoir à ouvrir le PDF.
     */
    @PostMapping("/attestation")
    @PreAuthorize(AttendanceManagementWeb.REPORT_ROLES)
    ResponseEntity<byte[]> attestation(
            @RequestParam String student,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @AuthenticationPrincipal Jwt caller) {
        java.util.UUID studentId;
        try {
            studentId = java.util.UUID.fromString(student);
        } catch (IllegalArgumentException notAUuid) {
            throw new AttendanceException(AttendanceException.Kind.INVALID_SUBMISSION);
        }
        AttestationService.Attestation issued = attestationService.issue(
                studentId, from, to, AttendanceManagementWeb.subject(caller));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + issued.documentId() + ".pdf\"")
                .header("X-Document-Id", issued.documentId())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_PDF)
                .body(issued.pdf());
    }

    /** Registre des documents émis (écran d'administration). */
    @GetMapping("/attestation")
    @PreAuthorize(AttendanceManagementWeb.REPORT_ROLES)
    List<AttendanceReports.DocumentSummary> attestations(
            @RequestParam(defaultValue = "20") int limit) {
        return attestationService.recent(limit);
    }

    /**
     * Vérifie un identifiant de document présenté par un tiers (AC-033).
     * Ouvert aux rôles de restitution : la vérification ne divulgue
     * aucune donnée d'assiduité.
     */
    @GetMapping("/attestation/{documentId}")
    @PreAuthorize(AttendanceManagementWeb.REPORT_ROLES)
    AttendanceReports.DocumentCheck verify(@PathVariable String documentId) {
        return attestationService.verify(documentId)
                .orElseThrow(() -> new AttendanceException(
                        AttendanceException.Kind.ATTESTATION_NOT_FOUND));
    }

    // ---------------------------------------------------------------

    private ResponseEntity<byte[]> render(String prefix, Instant from, Instant to,
                                          ReportExportFormat format,
                                          com.esic.connect.document.TabularDocument content,
                                          String callerSubject) {
        byte[] body = switch (format) {
            case CSV -> renderer.toCsv(content);
            case XLSX -> renderer.toSpreadsheet(content);
            case PDF -> renderer.toPdf(content, identity(callerSubject));
        };
        String name = prefix + "_" + safe(from) + "_" + safe(to) + "." + format.extension();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(format.mediaType()))
                .body(body);
    }

    /**
     * Identité portée par un rapport exporté en PDF. Un rapport n'est pas
     * une attestation : il n'est pas inscrit au registre et son
     * identifiant n'est pas opposable — il sert à retrouver l'export dans
     * l'audit, où la génération est tracée.
     */
    private DocumentIdentity identity(String callerSubject) {
        Instant now = clock.instant();
        String author = java.util.Optional.ofNullable(service.actorId(callerSubject))
                .map(id -> actorResolver.lookup().displayName(id))
                .filter(name -> name != null && !name.isBlank())
                .orElse("Système ESIC Connect");
        return new DocumentIdentity("RAPPORT-" + now.toEpochMilli(), issuer, author, now);
    }

    private static String safe(Instant instant) {
        return instant == null ? "debut" : instant.toString().replaceAll("[^0-9A-Za-z]", "");
    }
}
