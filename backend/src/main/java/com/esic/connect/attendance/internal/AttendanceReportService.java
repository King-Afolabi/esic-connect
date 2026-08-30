package com.esic.connect.attendance.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.alternation.AlternationDirectory;
import com.esic.connect.attendance.AttendanceStatus;
import com.esic.connect.coursesession.AttendanceCheckpointStatus;
import com.esic.connect.coursesession.AttendanceCheckpointType;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory.CheckpointRef;
import com.esic.connect.coursesession.CourseSessionDirectory.SessionRef;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.esic.connect.enrollment.EnrollmentDirectory.RosterEntry;
import com.esic.connect.identity.UserDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Calcul d'assiduité et rapports (V10). Unité de calcul : la
 * demi-journée (docs/02 §24.2), formule figée dans
 * {@code docs/reports/ATTENDANCE_MANAGEMENT_DESIGN.md} §4.C.
 *
 * <p>Décisions de comptage :
 * <ul>
 *   <li>demi-journée <em>présente</em> ⇔ tous ses points de contrôle
 *       <strong>obligatoires</strong> non annulés portent une présence
 *       {@code PRESENT} / {@code LATE} ;</li>
 *   <li>demi-journée <em>excusée</em> ⇔ tous obligatoires satisfaits et
 *       au moins un {@code EXCUSED_ABSENCE} ;</li>
 *   <li>contexte d'alternance {@code COMPANY} : demi-journée exclue du
 *       dénominateur scolaire (bucket {@code company}) ;</li>
 *   <li>contexte {@code UNKNOWN} : demi-journée sortie du calcul et
 *       comptée à part (bucket {@code unknown}) — jamais transformée en
 *       absence certaine ;</li>
 *   <li>{@code LATE} compté séparément (indicateur retards).</li>
 * </ul>
 *
 * <p>Périmètre : {@code ADMIN} / {@code SUPER_ADMIN} /
 * {@code SCHOOL_ADMINISTRATION} = global ; {@code PEDAGOGICAL_MANAGER} =
 * classes de son périmètre ({@code AcademicScopeDirectory}).
 * {@code TEACHER} n'accède pas aux rapports (il consulte les présences de
 * ses séances via {@code GET /sessions/{id}/attendance}).
 */
@Service
class AttendanceReportService {

    private final CourseSessionDirectory courseSessionDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final AcademicScopeDirectory academicScope;
    private final ClassGroupDirectory classGroupDirectory;
    private final AlternationDirectory alternationDirectory;
    private final UserDirectory userDirectory;
    private final AttendanceRecordRepository recordRepository;
    private final AttendanceJustificationRepository justificationRepository;
    private final AttendanceChangePublisher changePublisher;

    AttendanceReportService(CourseSessionDirectory courseSessionDirectory,
                            EnrollmentDirectory enrollmentDirectory,
                            AcademicScopeDirectory academicScope,
                            ClassGroupDirectory classGroupDirectory,
                            AlternationDirectory alternationDirectory,
                            UserDirectory userDirectory,
                            AttendanceRecordRepository recordRepository,
                            AttendanceJustificationRepository justificationRepository,
                            AttendanceChangePublisher changePublisher) {
        this.courseSessionDirectory = courseSessionDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.academicScope = academicScope;
        this.classGroupDirectory = classGroupDirectory;
        this.alternationDirectory = alternationDirectory;
        this.userDirectory = userDirectory;
        this.recordRepository = recordRepository;
        this.justificationRepository = justificationRepository;
        this.changePublisher = changePublisher;
    }

    // ------------------------------------------------------------------
    // Rapports
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<AttendanceReports.SessionRow> sessionReport(Instant from, Instant to, String classGroupFilter,
                                                    String sort) {
        UUID classFilter = parseOptionalUuid(classGroupFilter);
        List<AttendanceReports.SessionRow> rows = new ArrayList<>();
        for (SessionRef session : scopedSessions(from, to, classFilter)) {
            List<CheckpointRef> checkpoints = session.checkpoints().stream()
                    .filter(cp -> cp.status() != AttendanceCheckpointStatus.CANCELLED)
                    .toList();
            long roster = enrollmentDirectory.countActiveEnrollmentsInClasses(session.classGroupPublicIds());
            long requiredCheckpoints = checkpoints.stream().filter(CheckpointRef::required).count();
            long expected = roster * Math.max(1, requiredCheckpoints);

            List<AttendanceRecord> records = recordRepository.findByAttendanceCheckpointIdIn(
                    checkpoints.stream().map(CheckpointRef::internalId).collect(Collectors.toSet()));
            int present = 0;
            int late = 0;
            int absent = 0;
            int excused = 0;
            for (AttendanceRecord r : records) {
                switch (r.getStatus()) {
                    case PRESENT -> present++;
                    case LATE -> {
                        present++;
                        late++;
                    }
                    case ABSENT -> absent++;
                    case EXCUSED_ABSENCE -> excused++;
                    case CANCELLED -> { }
                }
            }
            double rate = expected == 0 ? 0d : round((double) present / expected);
            rows.add(new AttendanceReports.SessionRow(session.publicId(), session.title(), session.startsAt(),
                    session.endsAt(), classCodes(session), teacherName(session), checkpoints.size(),
                    expected, present, late, absent, excused, rate));
        }
        return AttendanceReportSort.sortSessions(rows, sort);
    }

    @Transactional(readOnly = true)
    List<AttendanceReports.ClassRow> classReport(Instant from, Instant to, String classGroupFilter, String sort) {
        UUID classFilter = parseOptionalUuid(classGroupFilter);
        List<SessionRef> sessions = scopedSessions(from, to, classFilter);
        Set<UUID> classes = scopedClasses(sessions, classFilter);
        Map<String, AttendanceRecord> recordIndex = indexRecords(sessions);

        List<AttendanceReports.ClassRow> rows = new ArrayList<>();
        for (UUID classPublicId : classes) {
            List<RosterEntry> roster = enrollmentDirectory.findActiveRosterForClasses(Set.of(classPublicId));
            Accrual acc = new Accrual();
            for (RosterEntry entry : roster) {
                for (SessionRef session : sessions) {
                    if (session.classGroupPublicIds().contains(classPublicId)) {
                        accrueHalfDays(acc, session, entry.enrollmentInternalId(), entry.enrollmentPublicId(),
                                recordIndex);
                    }
                }
            }
            String code = roster.isEmpty() ? classCode(classPublicId) : roster.get(0).classGroupCode();
            rows.add(new AttendanceReports.ClassRow(classPublicId, code, roster.size(), acc.toTotals()));
        }
        return AttendanceReportSort.sortClasses(rows, sort);
    }

    @Transactional(readOnly = true)
    List<AttendanceReports.StudentRow> studentReport(Instant from, Instant to, String classGroupFilter,
                                                     String studentProfileFilter, String sort) {
        UUID classFilter = parseOptionalUuid(classGroupFilter);
        UUID studentFilter = parseOptionalUuid(studentProfileFilter);
        List<SessionRef> sessions = scopedSessions(from, to, classFilter);
        Set<UUID> classes = scopedClasses(sessions, classFilter);
        Map<String, AttendanceRecord> recordIndex = indexRecords(sessions);

        List<AttendanceReports.StudentRow> rows = new ArrayList<>();
        for (UUID classPublicId : classes) {
            for (RosterEntry entry : enrollmentDirectory.findActiveRosterForClasses(Set.of(classPublicId))) {
                if (studentFilter != null && !studentFilter.equals(entry.studentProfilePublicId())) {
                    continue;
                }
                Accrual acc = new Accrual();
                for (SessionRef session : sessions) {
                    if (session.classGroupPublicIds().contains(classPublicId)) {
                        accrueHalfDays(acc, session, entry.enrollmentInternalId(), entry.enrollmentPublicId(),
                                recordIndex);
                    }
                }
                rows.add(new AttendanceReports.StudentRow(entry.studentProfilePublicId(),
                        entry.enrollmentPublicId(), entry.studentNumber(), entry.firstName(), entry.lastName(),
                        entry.classGroupCode(), acc.toTotals()));
            }
        }
        return AttendanceReportSort.sortStudents(rows, sort);
    }

    @Transactional(readOnly = true)
    AttendanceReports.Summary summary(Instant from, Instant to, String classGroupFilter) {
        UUID classFilter = parseOptionalUuid(classGroupFilter);
        List<SessionRef> sessions = scopedSessions(from, to, classFilter);
        Set<UUID> classes = scopedClasses(sessions, classFilter);
        Map<String, AttendanceRecord> recordIndex = indexRecords(sessions);

        Accrual acc = new Accrual();
        for (UUID classPublicId : classes) {
            for (RosterEntry entry : enrollmentDirectory.findActiveRosterForClasses(Set.of(classPublicId))) {
                for (SessionRef session : sessions) {
                    if (session.classGroupPublicIds().contains(classPublicId)) {
                        accrueHalfDays(acc, session, entry.enrollmentInternalId(), entry.enrollmentPublicId(),
                                recordIndex);
                    }
                }
            }
        }
        long pending = countPendingJustifications(sessions);
        List<String> notes = List.of(
                "Les demi-journées en contexte d'alternance COMPANY sont exclues du dénominateur.",
                "Les demi-journées en contexte UNKNOWN sont comptées séparément, jamais comme absence.");
        return new AttendanceReports.Summary(from, to, classes.size(), sessions.size(), acc.toTotals(),
                pending, notes);
    }

    void auditExport(String type, Instant from, Instant to, int rowCount, String callerSubject) {
        changePublisher.publishExport(changePublisher.actorId(callerSubject),
                "report=" + type + ";from=" + from + ";to=" + to + ";rows=" + rowCount);
    }

    // ------------------------------------------------------------------
    // Cœur du calcul
    // ------------------------------------------------------------------

    private void accrueHalfDays(Accrual acc, SessionRef session, long enrollmentInternalId,
                                UUID enrollmentPublicId, Map<String, AttendanceRecord> recordIndex) {
        ZoneId zone = persistedZone(session.timeZoneId());
        LocalDate day = LocalDate.ofInstant(session.startsAt(), zone);
        AlternationDirectory.Axis axis = alternationDirectory
                .resolveEnrollmentContext(enrollmentPublicId, day).effective();

        List<CheckpointRef> morning = new ArrayList<>();
        List<CheckpointRef> afternoon = new ArrayList<>();
        for (CheckpointRef cp : session.checkpoints()) {
            if (cp.status() == AttendanceCheckpointStatus.CANCELLED || !cp.required()) {
                continue;
            }
            Instant ref = cp.type() == AttendanceCheckpointType.END
                    ? session.endsAt()
                    : (cp.openedAt() != null ? cp.openedAt() : session.startsAt());
            int hour = ref.atZone(zone).getHour();
            (hour < 13 ? morning : afternoon).add(cp);
        }

        accrueOneHalfDay(acc, axis, morning, enrollmentInternalId, recordIndex);
        accrueOneHalfDay(acc, axis, afternoon, enrollmentInternalId, recordIndex);
    }

    private void accrueOneHalfDay(Accrual acc, AlternationDirectory.Axis axis, List<CheckpointRef> checkpoints,
                                  long enrollmentInternalId, Map<String, AttendanceRecord> recordIndex) {
        if (checkpoints.isEmpty()) {
            return;
        }
        // Contexte d'alternance ENTREPRISE : demi-journée hors dénominateur scolaire.
        if (axis == AlternationDirectory.Axis.COMPANY) {
            acc.company++;
            return;
        }

        boolean allPresentOrExcused = true;
        boolean anyExcused = false;
        boolean allPresent = true;
        for (CheckpointRef cp : checkpoints) {
            AttendanceRecord r = recordIndex.get(key(cp.internalId(), enrollmentInternalId));
            AttendanceStatus status = r != null ? r.getStatus() : null;
            if (status == AttendanceStatus.LATE) {
                acc.late++;
            }
            boolean present = status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE;
            boolean excused = status == AttendanceStatus.EXCUSED_ABSENCE;
            if (excused) {
                anyExcused = true;
            }
            if (!present) {
                allPresent = false;
            }
            if (!present && !excused) {
                allPresentOrExcused = false;
            }
        }

        boolean unknownContext = axis == AlternationDirectory.Axis.UNKNOWN;
        if (allPresent) {
            acc.expected++;
            acc.present++;
            if (unknownContext) {
                acc.unknown++;
            }
        } else if (allPresentOrExcused && anyExcused) {
            acc.expected++;
            acc.excused++;
            if (unknownContext) {
                acc.unknown++;
            }
        } else if (unknownContext) {
            // Contexte indéterminé + demi-journée non satisfaite : signalée
            // à part, JAMAIS comptée comme absence certaine (design §4.C).
            acc.unknown++;
        } else {
            // Contexte SCHOOL, demi-journée non satisfaite : absence dérivée
            // (comptée par HalfDayTotals.of via expected - present - excused).
            acc.expected++;
        }
    }

    private Map<String, AttendanceRecord> indexRecords(List<SessionRef> sessions) {
        Set<Long> checkpointIds = sessions.stream()
                .flatMap(s -> s.checkpoints().stream())
                .map(CheckpointRef::internalId)
                .collect(Collectors.toUnmodifiableSet());
        if (checkpointIds.isEmpty()) {
            return Map.of();
        }
        Map<String, AttendanceRecord> index = new HashMap<>();
        for (AttendanceRecord r : recordRepository.findByAttendanceCheckpointIdIn(checkpointIds)) {
            index.put(key(r.getAttendanceCheckpointId(), r.getEnrollmentId()), r);
        }
        return index;
    }

    private long countPendingJustifications(List<SessionRef> sessions) {
        Set<Long> checkpointIds = sessions.stream()
                .flatMap(s -> s.checkpoints().stream())
                .map(CheckpointRef::internalId)
                .collect(Collectors.toUnmodifiableSet());
        if (checkpointIds.isEmpty()) {
            return 0;
        }
        Set<Long> recordIds = recordRepository.findByAttendanceCheckpointIdIn(checkpointIds).stream()
                .map(AttendanceRecord::getId)
                .collect(Collectors.toUnmodifiableSet());
        return justificationRepository
                .findByStatusInOrderBySubmittedAtAsc(List.of(JustificationStatus.PENDING)).stream()
                .filter(j -> recordIds.contains(j.getAttendanceRecordId()))
                .count();
    }

    private List<SessionRef> scopedSessions(Instant from, Instant to, UUID classFilter) {
        boolean global = academicScope.hasGlobalScope();
        List<SessionRef> all = courseSessionDirectory.findSessionsInRange(from, to);
        List<SessionRef> kept = new ArrayList<>();
        for (SessionRef s : all) {
            boolean inScope = global || s.classGroupPublicIds().stream().anyMatch(academicScope::isClassInScope);
            if (!inScope) {
                continue;
            }
            if (classFilter != null && !s.classGroupPublicIds().contains(classFilter)) {
                continue;
            }
            if (classFilter != null && !global && !academicScope.isClassInScope(classFilter)) {
                throw new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN);
            }
            kept.add(s);
        }
        return kept;
    }

    private Set<UUID> scopedClasses(List<SessionRef> sessions, UUID classFilter) {
        boolean global = academicScope.hasGlobalScope();
        LinkedHashSet<UUID> classes = new LinkedHashSet<>();
        for (SessionRef s : sessions) {
            for (UUID classPublicId : s.classGroupPublicIds()) {
                if (classFilter != null && !classFilter.equals(classPublicId)) {
                    continue;
                }
                if (global || academicScope.isClassInScope(classPublicId)) {
                    classes.add(classPublicId);
                }
            }
        }
        return classes;
    }

    private String classCodes(SessionRef session) {
        return session.classGroupPublicIds().stream()
                .sorted()
                .map(this::classCode)
                .collect(Collectors.joining(", "));
    }

    /**
     * Code fonctionnel lisible d'une classe (ex. {@code C-DEMO}) résolu
     * via le port public {@link ClassGroupDirectory} — jamais l'UUID
     * public comme libellé (correctif PR #22 §7). Repli {@code "?"} si la
     * classe n'est plus résoluble (ne fuite pas l'identifiant SQL).
     */
    private String classCode(UUID classGroupPublicId) {
        return classGroupDirectory.findByPublicId(classGroupPublicId)
                .map(ClassGroupDirectory.ClassGroupRef::code)
                .filter(code -> code != null && !code.isBlank())
                .orElse("?");
    }

    private String teacherName(SessionRef session) {
        return userDirectory.findName(session.teacherUserId())
                .map(n -> ((n.firstName() != null ? n.firstName() : "") + " "
                        + (n.lastName() != null ? n.lastName() : "")).trim())
                .filter(s -> !s.isEmpty())
                .orElse("—");
    }

    private static String key(long checkpointId, long enrollmentId) {
        return checkpointId + ":" + enrollmentId;
    }

    /**
     * Résout le fuseau IANA <em>persisté</em> d'une séance. Une valeur
     * invalide est un état interne corrompu (validée à l'écriture par
     * {@code CourseSessionService}) : elle lève une erreur interne
     * explicite plutôt que d'être remplacée silencieusement par UTC, ce
     * qui classerait un point de contrôle dans la mauvaise demi-journée
     * (correctif PR #22 §1 ; même convention que
     * {@code AlternationContextService.persistedZone}). La valeur invalide
     * n'est jamais exposée au client.
     */
    private static ZoneId persistedZone(String id) {
        try {
            return ZoneId.of(id);
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Fuseau horaire persisté invalide pour une séance");
        }
    }

    private static UUID parseOptionalUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException notAUuid) {
            throw new AttendanceException(AttendanceException.Kind.REPORT_INVALID_FILTER);
        }
    }

    private static double round(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    /** Accumulateur mutable de demi-journées. */
    private static final class Accrual {
        long expected;
        long present;
        long excused;
        long company;
        long unknown;
        long late;

        AttendanceReports.HalfDayTotals toTotals() {
            return AttendanceReports.HalfDayTotals.of(expected, present, excused, company, unknown, late);
        }
    }
}
