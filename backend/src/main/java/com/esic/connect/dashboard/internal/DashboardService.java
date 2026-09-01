package com.esic.connect.dashboard.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.academic.ClassGroupDirectory.ClassGroupRef;
import com.esic.connect.attendance.AttendanceDashboardDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory.SessionRef;
import com.esic.connect.coursesession.SessionLifecycle;
import com.esic.connect.dashboard.internal.DashboardResponses.AdministrationCard;
import com.esic.connect.dashboard.internal.DashboardResponses.Dashboard;
import com.esic.connect.dashboard.internal.DashboardResponses.ImportLine;
import com.esic.connect.dashboard.internal.DashboardResponses.ManagerCard;
import com.esic.connect.dashboard.internal.DashboardResponses.SessionLine;
import com.esic.connect.dashboard.internal.DashboardResponses.StudentCard;
import com.esic.connect.dashboard.internal.DashboardResponses.TeacherCard;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.esic.connect.identity.AccountStatsDirectory;
import com.esic.connect.studentimport.StudentImportDashboardDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Assemble le tableau de bord d'un rôle (bloc G1-F ; DEC-G1-010).
 * <strong>Lecture seule</strong>, agrégats bornés, périmètre décidé côté
 * serveur — jamais d'un paramètre client.
 *
 * <p>Rôle effectif : le <em>contexte</em> demandé par l'appelant s'il
 * correspond à un rôle réellement présent dans son JWT (un rôle non
 * détenu ⇒ {@code 403}, jamais d'élévation) ; à défaut, priorité fixe
 * {@code SUPER_ADMIN > ADMIN > SCHOOL_ADMINISTRATION > PEDAGOGICAL_MANAGER
 * > TEACHER > STUDENT}.
 */
@Service
class DashboardService {

    private static final int LIST_LIMIT = 10;
    private static final Duration WEEK = Duration.ofDays(7);

    private final EnrollmentDirectory enrollmentDirectory;
    private final CourseSessionDirectory courseSessionDirectory;
    private final AttendanceDashboardDirectory attendanceDashboard;
    private final AcademicScopeDirectory academicScope;
    private final ClassGroupDirectory classGroupDirectory;
    private final AccountStatsDirectory accountStats;
    private final StudentImportDashboardDirectory studentImportDashboard;
    private final Clock clock;

    DashboardService(EnrollmentDirectory enrollmentDirectory,
                     CourseSessionDirectory courseSessionDirectory,
                     AttendanceDashboardDirectory attendanceDashboard,
                     AcademicScopeDirectory academicScope,
                     ClassGroupDirectory classGroupDirectory,
                     AccountStatsDirectory accountStats,
                     StudentImportDashboardDirectory studentImportDashboard,
                     Clock clock) {
        this.enrollmentDirectory = enrollmentDirectory;
        this.courseSessionDirectory = courseSessionDirectory;
        this.attendanceDashboard = attendanceDashboard;
        this.academicScope = academicScope;
        this.classGroupDirectory = classGroupDirectory;
        this.accountStats = accountStats;
        this.studentImportDashboard = studentImportDashboard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    Dashboard forCaller(String subject, List<String> roleCodes, String requestedContext) {
        UUID userPublicId = parseUuid(subject);
        DashboardRole role = resolveRole(roleCodes, requestedContext);
        Instant now = clock.instant();
        List<String> notes = new ArrayList<>();

        return switch (role) {
            case STUDENT -> new Dashboard(role.name(), now, student(userPublicId, now, notes),
                    null, null, null, notes);
            case TEACHER -> new Dashboard(role.name(), now, null, teacher(userPublicId, now),
                    null, null, notes);
            case PEDAGOGICAL_MANAGER -> new Dashboard(role.name(), now, null, null,
                    manager(now, notes), null, notes);
            case ADMINISTRATION -> new Dashboard(role.name(), now, null, null, null,
                    administration(now), notes);
        };
    }

    /**
     * Rôle effectif du tableau de bord.
     *
     * <ul>
     *   <li>{@code requestedContext} fourni : il doit correspondre à un
     *       rôle <strong>réellement présent</strong> dans le JWT de
     *       l'appelant — sinon {@code 403 DASHBOARD_CONTEXT_NOT_HELD}
     *       (jamais d'élévation de privilèges) ;</li>
     *   <li>absent : priorité fixe déterministe
     *       ({@link DashboardRole#effective}).</li>
     * </ul>
     */
    private DashboardRole resolveRole(List<String> roleCodes, String requestedContext) {
        if (requestedContext != null && !requestedContext.isBlank()) {
            String ctx = requestedContext.trim().toUpperCase(java.util.Locale.ROOT);
            if (roleCodes == null || !roleCodes.contains(ctx)) {
                throw new DashboardException(DashboardException.Kind.CONTEXT_NOT_HELD);
            }
            return DashboardRole.forRole(ctx)
                    .orElseThrow(() -> new DashboardException(DashboardException.Kind.CONTEXT_NOT_HELD));
        }
        return DashboardRole.effective(roleCodes)
                .orElseThrow(() -> new DashboardException(DashboardException.Kind.NO_ROLE));
    }

    // ------------------------------------------------------------------

    private StudentCard student(UUID userPublicId, Instant now, List<String> notes) {
        LocalDate today = LocalDate.ofInstant(now, clock.getZone());
        Set<UUID> classIds = enrollmentDirectory.findActiveEnrollmentsForUserOn(userPublicId, today).stream()
                .map(EnrollmentDirectory.EnrollmentRef::classGroupPublicId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<SessionLine> week = classIds.isEmpty() ? List.of()
                : lines(trim(courseSessionDirectory.findSessionsForClasses(classIds, now, now.plus(WEEK))));

        AttendanceDashboardDirectory.StudentAttendanceDigest d = attendanceDashboard.studentDigest(userPublicId);
        return new StudentCard(week.isEmpty() ? null : week.get(0), week,
                d.present(), d.late(), d.absent(), d.excused(),
                d.pendingJustifications(), d.rejectedJustifications());
    }

    private TeacherCard teacher(UUID userPublicId, Instant now) {
        List<SessionLine> upcoming = lines(courseSessionDirectory
                .findUpcomingForTeacher(userPublicId, now, now.plus(WEEK), LIST_LIMIT));
        List<SessionLine> toOpen = lines(courseSessionDirectory
                .findUpcomingForTeacher(userPublicId, now.minus(Duration.ofHours(12)), now, LIST_LIMIT)
                .stream()
                // s.status() est un SessionLifecycle : comparer à l'enum, pas à
                // la chaîne « PLANNED » (qui ne serait jamais égale).
                .filter(s -> s.status() == SessionLifecycle.PLANNED)
                .toList());
        return new TeacherCard(upcoming.isEmpty() ? null : upcoming.get(0), upcoming, toOpen);
    }

    private ManagerCard manager(Instant now, List<String> notes) {
        Optional<Set<Long>> visible = academicScope.visibleClassGroupIds();
        if (visible.isEmpty()) {
            // Un compte à périmètre global ne devrait pas être routé ici
            // (priorité de rôle) ; défensif.
            notes.add("Périmètre global : utilisez le tableau de bord d'administration.");
            return new ManagerCard(0, List.of(), List.of());
        }
        List<ClassGroupRef> classes = classGroupDirectory.findByInternalIds(visible.get());
        Map<UUID, String> known = new HashMap<>();
        Set<UUID> classPublicIds = new LinkedHashSet<>();
        List<String> classCodes = new ArrayList<>();
        for (ClassGroupRef c : classes) {
            known.put(c.publicId(), c.code());
            classPublicIds.add(c.publicId());
            if (classCodes.size() < LIST_LIMIT) {
                classCodes.add(c.code());
            }
        }
        // Les codes des classes du périmètre sont déjà connus (findByInternalIds
        // ci-dessus) : aucune requête de libellé supplémentaire pour les séances.
        List<SessionLine> upcoming = classPublicIds.isEmpty() ? List.of()
                : lines(trim(courseSessionDirectory.findSessionsForClasses(classPublicIds, now, now.plus(WEEK))),
                        known);
        // Cartes non exposées faute de port agrégé borné (dette G1-F, non
        // inventée) : justificatifs en attente périmétrés, alternance
        // UNKNOWN, planning actif, conflits récents.
        notes.add("Justificatifs en attente périmétrés, alternance UNKNOWN, planning actif et conflits : "
                + "non exposés au tableau de bord (dette G1-F) — voir « Suivi d'assiduité » et « Planning ».");
        return new ManagerCard(classes.size(), upcoming, classCodes);
    }

    private AdministrationCard administration(Instant now) {
        AccountStatsDirectory.AccountStats a = accountStats.counts();
        LocalDate today = LocalDate.ofInstant(now, clock.getZone());
        Instant dayStart = today.atStartOfDay(clock.getZone()).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        List<SessionLine> todaySessions = lines(trim(courseSessionDirectory.findSessionsInRange(dayStart, dayEnd)));
        List<ImportLine> imports = studentImportDashboard.recentJobs(LIST_LIMIT).stream()
                .map(j -> new ImportLine(j.publicId(), j.status(), j.totalRows(), j.createdAt()))
                .toList();
        return new AdministrationCard(a.active(), a.suspended(), a.pendingActivation(), a.archived(),
                attendanceDashboard.countPendingJustifications(), imports, todaySessions);
    }

    // ------------------------------------------------------------------

    private static List<SessionRef> trim(List<SessionRef> sessions) {
        return sessions.size() <= LIST_LIMIT ? sessions : sessions.subList(0, LIST_LIMIT);
    }

    private List<SessionLine> lines(List<SessionRef> sessions) {
        return lines(sessions, Map.of());
    }

    /**
     * Convertit des séances en lignes de tableau de bord en résolvant les
     * <strong>codes de classe en une seule requête</strong> (anti-N+1,
     * DEC-G1-010) : les codes déjà connus ({@code known} — par exemple le
     * périmètre d'un responsable pédagogique déjà chargé par lot) sont
     * réutilisés, seuls les codes manquants sont demandés via
     * {@link ClassGroupDirectory#findByPublicIds}.
     */
    private List<SessionLine> lines(List<SessionRef> sessions, Map<UUID, String> known) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> codes = new HashMap<>(known);
        Set<UUID> missing = new LinkedHashSet<>();
        for (SessionRef s : sessions) {
            for (UUID classPublicId : s.classGroupPublicIds()) {
                if (classPublicId != null && !codes.containsKey(classPublicId)) {
                    missing.add(classPublicId);
                }
            }
        }
        if (!missing.isEmpty()) {
            for (ClassGroupRef c : classGroupDirectory.findByPublicIds(missing)) {
                codes.put(c.publicId(), c.code());
            }
        }
        return sessions.stream()
                .map(s -> new SessionLine(s.publicId(), s.title(), statusName(s.status()),
                        s.startsAt(), s.endsAt(),
                        s.classGroupPublicIds().stream()
                                .map(id -> codes.getOrDefault(id, "—"))
                                .toList()))
                .toList();
    }

    private static String statusName(SessionLifecycle status) {
        return status != null ? status.name() : null;
    }

    private static UUID parseUuid(String subject) {
        try {
            return UUID.fromString(subject == null ? "" : subject.trim());
        } catch (IllegalArgumentException notAUuid) {
            throw new DashboardException(DashboardException.Kind.NO_ROLE);
        }
    }

    /** Rôle effectif du tableau de bord. */
    enum DashboardRole {
        ADMINISTRATION, PEDAGOGICAL_MANAGER, TEACHER, STUDENT;

        /** Rôle effectif par priorité fixe (aucun contexte demandé). */
        static Optional<DashboardRole> effective(Collection<String> roleCodes) {
            if (roleCodes == null) {
                return Optional.empty();
            }
            Set<String> roles = new java.util.HashSet<>(roleCodes);
            if (roles.contains("SUPER_ADMIN") || roles.contains("ADMIN")
                    || roles.contains("SCHOOL_ADMINISTRATION")) {
                return Optional.of(ADMINISTRATION);
            }
            if (roles.contains("PEDAGOGICAL_MANAGER")) {
                return Optional.of(PEDAGOGICAL_MANAGER);
            }
            if (roles.contains("TEACHER")) {
                return Optional.of(TEACHER);
            }
            if (roles.contains("STUDENT")) {
                return Optional.of(STUDENT);
            }
            return Optional.empty();
        }

        /**
         * Rôle effectif d'<strong>un</strong> code de rôle (contexte
         * explicitement demandé, déjà vérifié comme présent dans le JWT).
         * Les rôles d'administration partagent le même tableau de bord.
         */
        static Optional<DashboardRole> forRole(String roleCode) {
            return switch (roleCode) {
                case "SUPER_ADMIN", "ADMIN", "SCHOOL_ADMINISTRATION" -> Optional.of(ADMINISTRATION);
                case "PEDAGOGICAL_MANAGER" -> Optional.of(PEDAGOGICAL_MANAGER);
                case "TEACHER" -> Optional.of(TEACHER);
                case "STUDENT" -> Optional.of(STUDENT);
                default -> Optional.empty();
            };
        }
    }
}
