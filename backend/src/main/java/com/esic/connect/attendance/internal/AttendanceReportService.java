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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Calcul d'assiduité et rapports (V10). Unité de calcul : la
 * demi-journée (docs/02 §24.2), formule figée dans
 * {@code docs/02-cahier-des-charges.md} §4.C.
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

        Map<UUID, List<RosterEntry>> rosterByClass = rosterByClass(classes);
        // Contexte d'alternance de tout l'effectif sur toute la fenêtre,
        // résolu en lot (quelques requêtes ensemblistes) plutôt qu'une
        // poignée de requêtes par couple (inscription, jour) : c'était le
        // coût dominant des rapports et du tableau de bord (ANO-PERF-001/002).
        Map<AlternationKey, AlternationDirectory.Axis> alternationMemo =
                resolveAlternation(rosterByClass, sessions);
        List<AttendanceReports.ClassRow> rows = new ArrayList<>();
        for (UUID classPublicId : classes) {
            List<RosterEntry> roster = rosterByClass.getOrDefault(classPublicId, List.of());
            Accrual acc = new Accrual();
            for (RosterEntry entry : roster) {
                for (SessionRef session : sessions) {
                    if (session.classGroupPublicIds().contains(classPublicId)) {
                        accrueHalfDays(acc, session, entry.enrollmentInternalId(), entry.enrollmentPublicId(),
                                recordIndex, alternationMemo);
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

        Map<UUID, List<RosterEntry>> rosterByClass = rosterByClass(classes);
        Map<AlternationKey, AlternationDirectory.Axis> alternationMemo =
                resolveAlternation(rosterByClass, sessions);
        List<AttendanceReports.StudentRow> rows = new ArrayList<>();
        for (UUID classPublicId : classes) {
            for (RosterEntry entry : rosterByClass.getOrDefault(classPublicId, List.of())) {
                if (studentFilter != null && !studentFilter.equals(entry.studentProfilePublicId())) {
                    continue;
                }
                Accrual acc = new Accrual();
                for (SessionRef session : sessions) {
                    if (session.classGroupPublicIds().contains(classPublicId)) {
                        accrueHalfDays(acc, session, entry.enrollmentInternalId(), entry.enrollmentPublicId(),
                                recordIndex, alternationMemo);
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

        Map<UUID, List<RosterEntry>> rosterByClass = rosterByClass(classes);
        Map<AlternationKey, AlternationDirectory.Axis> alternationMemo =
                resolveAlternation(rosterByClass, sessions);
        Accrual acc = new Accrual();
        for (UUID classPublicId : classes) {
            for (RosterEntry entry : rosterByClass.getOrDefault(classPublicId, List.of())) {
                for (SessionRef session : sessions) {
                    if (session.classGroupPublicIds().contains(classPublicId)) {
                        accrueHalfDays(acc, session, entry.enrollmentInternalId(), entry.enrollmentPublicId(),
                                recordIndex, alternationMemo);
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

    /**
     * Justificatifs {@code PENDING} du périmètre de l'appelant, sur une
     * fenêtre (EF-REP-007).
     *
     * <p>Ne passe <strong>pas</strong> par {@link #summary} : celle-ci
     * recalcule toutes les demi-journées de la fenêtre pour produire, au
     * passage, ce compteur. L'appeler avec des bornes ouvertes pour un
     * seul nombre faisait recalculer l'assiduité de toute la base à
     * chaque affichage du tableau de bord — un coût qui grandissait à
     * chaque séance créée (dette T-03, NFR-PERF-08).
     */
    @Transactional(readOnly = true)
    long countPendingJustificationsInScope(Instant from, Instant to) {
        return countPendingJustifications(scopedSessions(from, to, null));
    }

    /**
     * Effectif actif de <strong>toutes</strong> les classes du rapport, en
     * un appel, groupé par classe (dette T-03).
     *
     * <p>Interroger l'effectif classe par classe coûtait une requête —
     * plus la résolution de chaque apprenant — par classe du périmètre.
     * Un responsable pédagogique de quinze classes payait donc quinze
     * allers-retours pour un rapport qui en affiche quinze lignes
     * (NFR-PERF-08).
     */
    private Map<UUID, List<RosterEntry>> rosterByClass(Set<UUID> classes) {
        if (classes.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<RosterEntry>> byClass = new HashMap<>();
        for (RosterEntry entry : enrollmentDirectory.findActiveRosterForClasses(classes)) {
            if (entry.classGroupPublicId() != null) {
                byClass.computeIfAbsent(entry.classGroupPublicId(), key -> new ArrayList<>()).add(entry);
            }
        }
        return byClass;
    }

    /**
     * Contexte d'alternance de tout l'effectif du rapport, sur toute la
     * fenêtre de jours couverte par les séances, résolu <strong>en
     * lot</strong> (ANO-PERF-001/002, dette T-03).
     *
     * <p>Avant : {@code accrueHalfDays} résolvait le contexte à la demande,
     * couple (inscription, jour) par couple — chacun coûtant plusieurs
     * requêtes SQL (classe, affectation de rythme, exceptions). Sur une
     * fenêtre d'un mois pour un responsable de plusieurs classes, cela
     * faisait des milliers d'allers-retours et la requête du tableau de
     * bord / de la synthèse durait une minute. Ici, {@code alternation}
     * charge tout en quelques requêtes ensemblistes et résout chaque jour
     * en mémoire. La mémoire reste alimentée à la demande en dernier
     * recours ({@code computeIfAbsent} dans {@code accrueHalfDays}), pour
     * un couple qui sortirait de l'intervalle pré-calculé.
     */
    private Map<AlternationKey, AlternationDirectory.Axis> resolveAlternation(
            Map<UUID, List<RosterEntry>> rosterByClass, List<SessionRef> sessions) {
        Map<AlternationKey, AlternationDirectory.Axis> memo = new HashMap<>();
        if (rosterByClass.isEmpty() || sessions.isEmpty()) {
            return memo;
        }
        LocalDate min = null;
        LocalDate max = null;
        for (SessionRef s : sessions) {
            LocalDate day = LocalDate.ofInstant(s.startsAt(), persistedZone(s.timeZoneId()));
            if (min == null || day.isBefore(min)) {
                min = day;
            }
            if (max == null || day.isAfter(max)) {
                max = day;
            }
        }
        List<AlternationDirectory.EnrollmentDescriptor> descriptors = new ArrayList<>();
        for (List<RosterEntry> roster : rosterByClass.values()) {
            for (RosterEntry entry : roster) {
                descriptors.add(new AlternationDirectory.EnrollmentDescriptor(
                        entry.enrollmentPublicId(), entry.enrollmentInternalId(), entry.classGroupPublicId()));
            }
        }
        alternationDirectory.resolveEnrollmentContexts(descriptors, min, max).forEach((key, axis) ->
                memo.put(new AlternationKey(key.enrollmentPublicId(), key.day()), axis));
        return memo;
    }

    /** Identifiant interne de l'appelant, pour nommer l'auteur d'un document. */
    Long actorId(String callerSubject) {
        return changePublisher.actorId(callerSubject);
    }

    void auditExport(String type, Instant from, Instant to, int rowCount, String callerSubject) {
        changePublisher.publishExport(changePublisher.actorId(callerSubject),
                "report=" + type + ";from=" + from + ";to=" + to + ";rows=" + rowCount);
    }

    // ------------------------------------------------------------------
    // Cœur du calcul
    // ------------------------------------------------------------------

    private void accrueHalfDays(Accrual acc, SessionRef session, long enrollmentInternalId,
                                UUID enrollmentPublicId, Map<String, AttendanceRecord> recordIndex,
                                Map<AlternationKey, AlternationDirectory.Axis> alternationMemo) {
        ZoneId zone = persistedZone(session.timeZoneId());
        LocalDate day = LocalDate.ofInstant(session.startsAt(), zone);
        // Le contexte d'alternance dépend de l'inscription et du JOUR,
        // jamais de la séance : deux cours du même après-midi pour le même
        // apprenant donnent forcément la même réponse. Sans mémorisation,
        // le coût suivait le nombre de séances affichées (dette T-03,
        // NFR-PERF-08) alors que la donnée demandée, elle, ne change pas.
        AlternationDirectory.Axis axis = alternationMemo.computeIfAbsent(
                new AlternationKey(enrollmentPublicId, day),
                key -> alternationDirectory.resolveEnrollmentContext(key.enrollmentPublicId(), key.day())
                        .effective());

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

    /** Clé de mémorisation du contexte d'alternance : inscription + jour. */
    private record AlternationKey(UUID enrollmentPublicId, LocalDate day) {
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

    /**
     * Périmètre de l'appelant, résolu <strong>une fois</strong>
     * (dette T-03).
     *
     * <p>{@code AcademicScopeDirectory.isClassInScope(uuid)} interroge la
     * base à chaque appel. L'appeler dans une boucle sur les séances
     * faisait une requête par séance et par classe rattachée : sur une
     * fenêtre d'un mois, le contrôle de périmètre coûtait plus cher que
     * le calcul d'assiduité lui-même (NFR-PERF-08). Le périmètre est ici
     * chargé en bloc, puis interrogé en mémoire.
     *
     * @return les identifiants publics des classes visibles, ou
     *         {@link Optional#empty()} pour un appelant à périmètre global
     */
    private Optional<Set<UUID>> visibleClassPublicIds() {
        Optional<Set<Long>> visible = academicScope.visibleClassGroupIds();
        if (visible.isEmpty()) {
            return Optional.empty();
        }
        if (visible.get().isEmpty()) {
            // Périmètre vide ≠ périmètre global : ne rien voir, jamais tout voir.
            return Optional.of(Set.of());
        }
        Set<UUID> publicIds = new LinkedHashSet<>();
        for (ClassGroupDirectory.ClassGroupRef ref : classGroupDirectory.findByInternalIds(visible.get())) {
            publicIds.add(ref.publicId());
        }
        return Optional.of(publicIds);
    }

    private List<SessionRef> scopedSessions(Instant from, Instant to, UUID classFilter) {
        Optional<Set<UUID>> scope = visibleClassPublicIds();
        boolean global = scope.isEmpty();
        Set<UUID> visible = scope.orElse(Set.of());
        if (classFilter != null && !global && !visible.contains(classFilter)) {
            throw new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN);
        }
        List<SessionRef> all = courseSessionDirectory.findSessionsInRange(from, to);
        List<SessionRef> kept = new ArrayList<>();
        for (SessionRef s : all) {
            boolean inScope = global || s.classGroupPublicIds().stream().anyMatch(visible::contains);
            if (!inScope) {
                continue;
            }
            if (classFilter != null && !s.classGroupPublicIds().contains(classFilter)) {
                continue;
            }
            kept.add(s);
        }
        return kept;
    }

    private Set<UUID> scopedClasses(List<SessionRef> sessions, UUID classFilter) {
        Optional<Set<UUID>> scope = visibleClassPublicIds();
        boolean global = scope.isEmpty();
        Set<UUID> visible = scope.orElse(Set.of());
        LinkedHashSet<UUID> classes = new LinkedHashSet<>();
        for (SessionRef s : sessions) {
            for (UUID classPublicId : s.classGroupPublicIds()) {
                if (classFilter != null && !classFilter.equals(classPublicId)) {
                    continue;
                }
                if (global || visible.contains(classPublicId)) {
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
