package com.esic.connect.attendance.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Liste blanche du tri serveur des rapports (correctif PR #22 §6) : seuls
 * les champs et sens documentés sont acceptés ; tout le reste →
 * {@link AttendanceException.Kind#REPORT_INVALID_SORT}. Le tri est
 * déterministe (tri secondaire stable sur l'identifiant public).
 */
class AttendanceReportSortTests {

    private static AttendanceReports.SessionRow session(String title, Instant startsAt, int present,
                                                       double rate, UUID id) {
        return new AttendanceReports.SessionRow(id, title, startsAt, startsAt.plusSeconds(3600),
                "C-DEMO", "Formateur", 1, 10, present, 0, 0, 0, rate);
    }

    private static AttendanceReports.ClassRow clazz(String code, long expected, long present, UUID id) {
        return new AttendanceReports.ClassRow(id, code, 1,
                AttendanceReports.HalfDayTotals.of(expected, present, 0, 0, 0, 0));
    }

    private static AttendanceReports.StudentRow student(String last, String number, UUID enrollmentId) {
        return new AttendanceReports.StudentRow(UUID.randomUUID(), enrollmentId, number, "Prénom", last,
                "C-DEMO", AttendanceReports.HalfDayTotals.of(4, 2, 0, 0, 0, 0));
    }

    @Test
    void defaultSortsPreserveHistoricalOrder() {
        Instant t0 = Instant.parse("2026-09-01T08:00:00Z");
        List<AttendanceReports.SessionRow> rows = List.of(
                session("b", t0.plusSeconds(120), 5, 0.5, UUID.randomUUID()),
                session("a", t0, 9, 0.9, UUID.randomUUID()));
        assertThat(AttendanceReportSort.sortSessions(rows, null))
                .extracting(AttendanceReports.SessionRow::sessionTitle).containsExactly("a", "b");
        assertThat(AttendanceReportSort.sortSessions(rows, "  "))
                .extracting(AttendanceReports.SessionRow::sessionTitle).containsExactly("a", "b");
    }

    @Test
    void acceptsWhitelistedFieldsAscAndDesc() {
        UUID id = UUID.randomUUID();
        List<AttendanceReports.SessionRow> rows = List.of(
                session("low", Instant.parse("2026-09-02T08:00:00Z"), 2, 0.2, id),
                session("high", Instant.parse("2026-09-01T08:00:00Z"), 8, 0.8, UUID.randomUUID()));
        assertThat(AttendanceReportSort.sortSessions(rows, "presentCount,asc"))
                .extracting(AttendanceReports.SessionRow::sessionTitle).containsExactly("low", "high");
        assertThat(AttendanceReportSort.sortSessions(rows, "attendanceRate,desc"))
                .extracting(AttendanceReports.SessionRow::sessionTitle).containsExactly("high", "low");

        assertThat(AttendanceReportSort.sortClasses(
                List.of(clazz("Z", 4, 1, UUID.randomUUID()), clazz("A", 4, 4, UUID.randomUUID())),
                "classCode,asc")).extracting(AttendanceReports.ClassRow::classCode).containsExactly("A", "Z");
        assertThat(AttendanceReportSort.sortStudents(
                List.of(student("Zephyr", "S2", UUID.randomUUID()), student("Alba", "S1", UUID.randomUUID())),
                "lastName,desc")).extracting(AttendanceReports.StudentRow::lastName)
                .containsExactly("Zephyr", "Alba");
    }

    @Test
    void secondarySortOnPublicIdIsDeterministic() {
        Instant same = Instant.parse("2026-09-01T08:00:00Z");
        UUID lower = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higher = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        List<AttendanceReports.SessionRow> rows = List.of(
                session("x", same, 5, 0.5, higher),
                session("y", same, 5, 0.5, lower));
        assertThat(AttendanceReportSort.sortSessions(rows, "startsAt,asc"))
                .extracting(AttendanceReports.SessionRow::sessionPublicId).containsExactly(lower, higher);
    }

    @Test
    void rejectsUnknownFieldDirectionAndArity() {
        List<AttendanceReports.SessionRow> rows = List.of(
                session("a", Instant.parse("2026-09-01T08:00:00Z"), 1, 0.1, UUID.randomUUID()));
        for (String bad : List.of("email,asc", "startsAt,sideways", "startsAt", "startsAt,asc,extra",
                "classCode,asc")) {
            assertThatThrownBy(() -> AttendanceReportSort.sortSessions(rows, bad))
                    .isInstanceOfSatisfying(AttendanceException.class,
                            ex -> assertThat(ex.kind()).isEqualTo(AttendanceException.Kind.REPORT_INVALID_SORT));
        }
        assertThatThrownBy(() -> AttendanceReportSort.sortStudents(List.of(), "startsAt,asc"))
                .isInstanceOfSatisfying(AttendanceException.class,
                        ex -> assertThat(ex.kind()).isEqualTo(AttendanceException.Kind.REPORT_INVALID_SORT));
    }
}
