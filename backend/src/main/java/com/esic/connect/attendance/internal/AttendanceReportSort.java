package com.esic.connect.attendance.internal;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tri serveur <strong>borné</strong> des rapports d'assiduité (correctif
 * PR #22 §6). Format accepté : {@code field,asc} | {@code field,desc}.
 *
 * <p>Tout champ ou sens hors liste blanche lève
 * {@link AttendanceException.Kind#REPORT_INVALID_SORT} → 400
 * {@code ATT_REPORT_INVALID_SORT}. Aucun SQL n'est construit à partir du
 * texte client : le tri est appliqué <em>en mémoire</em>, sur la liste
 * complète, <em>avant</em> la pagination, et il est rendu déterministe
 * par un tri secondaire stable sur l'identifiant public de la ligne.
 *
 * <p>Listes blanches (documentées) :
 * <ul>
 *   <li>séances : {@code startsAt} (défaut, asc), {@code attendanceRate},
 *       {@code presentCount} ;</li>
 *   <li>classes : {@code classCode} (défaut, asc), {@code attendanceRate},
 *       {@code absentHalfDays} ;</li>
 *   <li>apprenants : {@code lastName} (défaut, asc), {@code studentNumber},
 *       {@code attendanceRate}, {@code absentHalfDays}.</li>
 * </ul>
 */
final class AttendanceReportSort {

    static final Set<String> SESSION_FIELDS = Set.of("startsAt", "attendanceRate", "presentCount");
    static final Set<String> CLASS_FIELDS = Set.of("classCode", "attendanceRate", "absentHalfDays");
    static final Set<String> STUDENT_FIELDS =
            Set.of("lastName", "studentNumber", "attendanceRate", "absentHalfDays");

    private AttendanceReportSort() {
    }

    static List<AttendanceReports.SessionRow> sortSessions(List<AttendanceReports.SessionRow> rows, String sort) {
        Parsed p = parse(sort, "startsAt", SESSION_FIELDS);
        Comparator<AttendanceReports.SessionRow> c = switch (p.field()) {
            case "startsAt" -> Comparator.comparing(AttendanceReports.SessionRow::startsAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "attendanceRate" -> Comparator.comparingDouble(AttendanceReports.SessionRow::attendanceRate);
            case "presentCount" -> Comparator.comparingInt(AttendanceReports.SessionRow::presentCount);
            default -> throw invalid();
        };
        if (p.desc()) {
            c = c.reversed();
        }
        return rows.stream()
                .sorted(c.thenComparing(r -> r.sessionPublicId().toString()))
                .toList();
    }

    static List<AttendanceReports.ClassRow> sortClasses(List<AttendanceReports.ClassRow> rows, String sort) {
        Parsed p = parse(sort, "classCode", CLASS_FIELDS);
        Comparator<AttendanceReports.ClassRow> c = switch (p.field()) {
            case "classCode" -> Comparator.comparing(r -> nz(r.classCode()), String.CASE_INSENSITIVE_ORDER);
            case "attendanceRate" -> Comparator.comparingDouble(r -> r.totals().attendanceRate());
            case "absentHalfDays" -> Comparator.comparingLong(r -> r.totals().absentHalfDays());
            default -> throw invalid();
        };
        if (p.desc()) {
            c = c.reversed();
        }
        return rows.stream()
                .sorted(c.thenComparing(r -> r.classGroupPublicId().toString()))
                .toList();
    }

    static List<AttendanceReports.StudentRow> sortStudents(List<AttendanceReports.StudentRow> rows, String sort) {
        Parsed p = parse(sort, "lastName", STUDENT_FIELDS);
        Comparator<AttendanceReports.StudentRow> c = switch (p.field()) {
            case "lastName" -> Comparator.comparing(r -> nz(r.lastName()), String.CASE_INSENSITIVE_ORDER);
            case "studentNumber" -> Comparator.comparing(r -> nz(r.studentNumber()),
                    String.CASE_INSENSITIVE_ORDER);
            case "attendanceRate" -> Comparator.comparingDouble(r -> r.totals().attendanceRate());
            case "absentHalfDays" -> Comparator.comparingLong(r -> r.totals().absentHalfDays());
            default -> throw invalid();
        };
        if (p.desc()) {
            c = c.reversed();
        }
        return rows.stream()
                .sorted(c.thenComparing(r -> r.enrollmentPublicId().toString()))
                .toList();
    }

    // ------------------------------------------------------------------

    private record Parsed(String field, boolean desc) {
    }

    private static Parsed parse(String sort, String defaultField, Set<String> allowed) {
        if (sort == null || sort.isBlank()) {
            return new Parsed(defaultField, false);
        }
        String[] parts = sort.split(",", -1);
        if (parts.length != 2) {
            throw invalid();
        }
        String field = parts[0].trim();
        String direction = parts[1].trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(field)) {
            throw invalid();
        }
        if (!direction.equals("asc") && !direction.equals("desc")) {
            throw invalid();
        }
        return new Parsed(field, direction.equals("desc"));
    }

    private static AttendanceException invalid() {
        return new AttendanceException(AttendanceException.Kind.REPORT_INVALID_SORT);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
