package com.esic.connect.attendance.internal;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Rapports d'assiduité (V10) : par séance, par classe, par apprenant, et
 * synthèse ; exports CSV correspondants. Réservés à
 * {@code ADMIN}/{@code SUPER_ADMIN}/{@code SCHOOL_ADMINISTRATION}/
 * {@code PEDAGOGICAL_MANAGER} (périmètre appliqué par
 * {@link AttendanceReportService}). Filtres {@code from}/{@code to}
 * (instants sur le début de séance), {@code classGroup}, {@code studentProfile}.
 * Pagination bornée (≤ 100). Les exports neutralisent l'injection de
 * formule CSV.
 */
@RestController
@RequestMapping("/api/v1/attendance/reports")
class AttendanceReportController {

    private final AttendanceReportService service;

    AttendanceReportController(AttendanceReportService service) {
        this.service = service;
    }

    // --- JSON ---------------------------------------------------------

    @GetMapping("/sessions")
    @PreAuthorize(AttendanceManagementWeb.REPORT_ROLES)
    PageResponse<AttendanceReports.SessionRow> sessions(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String classGroup,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.ofList(service.sessionReport(from, to, classGroup), page, size);
    }

    @GetMapping("/classes")
    @PreAuthorize(AttendanceManagementWeb.REPORT_ROLES)
    PageResponse<AttendanceReports.ClassRow> classes(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String classGroup,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.ofList(service.classReport(from, to, classGroup), page, size);
    }

    @GetMapping("/students")
    @PreAuthorize(AttendanceManagementWeb.REPORT_ROLES)
    PageResponse<AttendanceReports.StudentRow> students(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String classGroup,
            @RequestParam(required = false) String studentProfile,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.ofList(service.studentReport(from, to, classGroup, studentProfile), page, size);
    }

    @GetMapping("/summary")
    @PreAuthorize(AttendanceManagementWeb.REPORT_ROLES)
    AttendanceReports.Summary summary(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String classGroup) {
        return service.summary(from, to, classGroup);
    }

    // --- CSV ---------------------------------------------------------

    @GetMapping("/sessions/export")
    @PreAuthorize(AttendanceManagementWeb.REPORT_ROLES)
    ResponseEntity<byte[]> exportSessions(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String classGroup,
            @AuthenticationPrincipal Jwt caller) {
        List<AttendanceReports.SessionRow> rows = service.sessionReport(from, to, classGroup);
        List<List<String>> body = new ArrayList<>();
        for (AttendanceReports.SessionRow r : rows) {
            body.add(List.of(str(r.sessionPublicId()), nz(r.sessionTitle()), str(r.startsAt()), str(r.endsAt()),
                    nz(r.classCodes()), nz(r.teacherName()), Integer.toString(r.checkpointCount()),
                    Long.toString(r.expectedCount()), Integer.toString(r.presentCount()),
                    Integer.toString(r.lateCount()), Integer.toString(r.absentCount()),
                    Integer.toString(r.excusedCount()), pct(r.attendanceRate())));
        }
        service.auditExport("sessions", from, to, rows.size(), AttendanceManagementWeb.subject(caller));
        return csv("attendance-sessions", from, to, AttendanceCsvWriter.write(List.of(
                "session_id", "titre", "debut", "fin", "classes", "formateur", "points_de_controle",
                "attendu", "present", "retard", "absent", "excuse", "taux_presence"), body));
    }

    @GetMapping("/classes/export")
    @PreAuthorize(AttendanceManagementWeb.REPORT_ROLES)
    ResponseEntity<byte[]> exportClasses(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String classGroup,
            @AuthenticationPrincipal Jwt caller) {
        List<AttendanceReports.ClassRow> rows = service.classReport(from, to, classGroup);
        List<List<String>> body = new ArrayList<>();
        for (AttendanceReports.ClassRow r : rows) {
            AttendanceReports.HalfDayTotals t = r.totals();
            body.add(List.of(str(r.classGroupPublicId()), nz(r.classCode()), Integer.toString(r.studentCount()),
                    Long.toString(t.expectedHalfDays()), Long.toString(t.presentHalfDays()),
                    Long.toString(t.absentHalfDays()), Long.toString(t.excusedHalfDays()),
                    Long.toString(t.companyHalfDays()), Long.toString(t.unknownHalfDays()),
                    Long.toString(t.lateCount()), pct(t.attendanceRate()), pct(t.unjustifiedAbsenceRate())));
        }
        service.auditExport("classes", from, to, rows.size(), AttendanceManagementWeb.subject(caller));
        return csv("attendance-classes", from, to, AttendanceCsvWriter.write(List.of(
                "classe_id", "classe", "effectif", "demi_journees_attendues", "presentes", "absentes",
                "excusees", "entreprise", "inconnues", "retards", "taux_presence", "taux_absence_injustifiee"),
                body));
    }

    @GetMapping("/students/export")
    @PreAuthorize(AttendanceManagementWeb.REPORT_ROLES)
    ResponseEntity<byte[]> exportStudents(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String classGroup,
            @RequestParam(required = false) String studentProfile,
            @AuthenticationPrincipal Jwt caller) {
        List<AttendanceReports.StudentRow> rows = service.studentReport(from, to, classGroup, studentProfile);
        List<List<String>> body = new ArrayList<>();
        for (AttendanceReports.StudentRow r : rows) {
            AttendanceReports.HalfDayTotals t = r.totals();
            body.add(List.of(str(r.studentProfilePublicId()), nz(r.studentNumber()), nz(r.firstName()),
                    nz(r.lastName()), nz(r.classCode()), Long.toString(t.expectedHalfDays()),
                    Long.toString(t.presentHalfDays()), Long.toString(t.absentHalfDays()),
                    Long.toString(t.excusedHalfDays()), Long.toString(t.companyHalfDays()),
                    Long.toString(t.unknownHalfDays()), Long.toString(t.lateCount()),
                    pct(t.attendanceRate()), pct(t.unjustifiedAbsenceRate())));
        }
        service.auditExport("students", from, to, rows.size(), AttendanceManagementWeb.subject(caller));
        return csv("attendance-students", from, to, AttendanceCsvWriter.write(List.of(
                "apprenant_id", "numero_etudiant", "prenom", "nom", "classe", "demi_journees_attendues",
                "presentes", "absentes", "excusees", "entreprise", "inconnues", "retards", "taux_presence",
                "taux_absence_injustifiee"), body));
    }

    // ---------------------------------------------------------------

    private static ResponseEntity<byte[]> csv(String prefix, Instant from, Instant to, String content) {
        String name = prefix + "_" + safe(from) + "_" + safe(to) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String safe(Instant instant) {
        return instant == null ? "debut" : instant.toString().replaceAll("[^0-9A-Za-z]", "");
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String pct(double ratio) {
        return String.format(java.util.Locale.ROOT, "%.2f", ratio * 100d);
    }
}
