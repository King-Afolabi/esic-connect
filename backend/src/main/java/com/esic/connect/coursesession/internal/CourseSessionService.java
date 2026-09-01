package com.esic.connect.coursesession.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.coursesession.CourseSessionChangeAction;
import com.esic.connect.coursesession.CourseSessionDirectory.AccessLevel;
import com.esic.connect.coursesession.SessionLifecycle;
import com.esic.connect.identity.TeacherDirectory;
import com.esic.connect.identity.UserDirectory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Séances exceptionnelles (V9). Création, consultation filtrée par
 * périmètre, cycle de vie strict {@code PLANNED → OPEN → CLOSED}. Le
 * point de contrôle unique est créé avec la séance et
 * ouvert / fermé avec elle.
 *
 * <p>Le contrôle d'accès fin est délégué à {@link CourseSessionAccessGuard}
 * (contexte Spring Security), jamais dérivé d'un paramètre client.
 */
@Service
class CourseSessionService {

    private static final Set<String> SORTABLE = Set.of("startsAt", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "startsAt");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final CourseSessionRepository sessionRepository;
    private final AttendanceCheckpointRepository checkpointRepository;
    private final TeacherDirectory teacherDirectory;
    private final UserDirectory userDirectory;
    private final ClassGroupDirectory classGroupDirectory;
    private final AcademicScopeDirectory academicScope;
    private final CourseSessionAccessGuard accessGuard;
    private final CourseSessionChangePublisher changePublisher;
    private final Clock clock;

    CourseSessionService(CourseSessionRepository sessionRepository,
                         AttendanceCheckpointRepository checkpointRepository,
                         TeacherDirectory teacherDirectory,
                         UserDirectory userDirectory,
                         ClassGroupDirectory classGroupDirectory,
                         AcademicScopeDirectory academicScope,
                         CourseSessionAccessGuard accessGuard,
                         CourseSessionChangePublisher changePublisher,
                         Clock clock) {
        this.sessionRepository = sessionRepository;
        this.checkpointRepository = checkpointRepository;
        this.teacherDirectory = teacherDirectory;
        this.userDirectory = userDirectory;
        this.classGroupDirectory = classGroupDirectory;
        this.academicScope = academicScope;
        this.accessGuard = accessGuard;
        this.changePublisher = changePublisher;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    PageResponse<CourseSessionResponse> list(String statusFilter, String teacherFilter, String classFilter,
                                             Instant from, Instant to, int page, int size, String sort,
                                             String callerSubject) {
        List<Specification<CourseSession>> specs = new ArrayList<>();
        // Garde centralisée (audit G1-B.1) : seules les séances
        // opérationnelles sont listées — exclut celles retirées par une
        // republication de planning (DEC-G1-004 règle 4).
        specs.add(CourseSessionSpecifications.operational());
        parseStatus(statusFilter).ifPresent(status -> specs.add(CourseSessionSpecifications.hasStatus(status)));
        if (from != null) {
            specs.add(CourseSessionSpecifications.startsFrom(from));
        }
        if (to != null) {
            specs.add(CourseSessionSpecifications.startsUntil(to));
        }
        resolveTeacherFilter(teacherFilter).ifPresent(id -> specs.add(CourseSessionSpecifications.taughtBy(id)));
        resolveClassFilter(classFilter).ifPresent(id ->
                specs.add(CourseSessionSpecifications.hasAnyClassIn(Set.of(id))));

        Optional<Specification<CourseSession>> scopeRestriction = scopeRestriction(callerSubject);
        if (scopeRestriction.isEmpty()) {
            return new PageResponse<>(List.of(), Math.max(page, 0), normalizeSize(size), 0, 0);
        }
        scopeRestriction.filter(spec -> spec != ALLOW_ALL).ifPresent(specs::add);

        Pageable pageable = pageable(page, size, sort);
        Page<CourseSession> result = sessionRepository.findAll(Specification.allOf(specs), pageable);
        return PageResponse.of(result, this::toResponse);
    }

    @Transactional(readOnly = true)
    CourseSessionResponse get(String publicId, String callerSubject) {
        CourseSession session = require(publicId);
        requireAccess(session, AccessLevel.READ, callerSubject);
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    List<TeacherOptionResponse> listEligibleTeachers() {
        return teacherDirectory.listEligibleTeachers().stream()
                .map(TeacherOptionResponse::from)
                .toList();
    }

    // ------------------------------------------------------------------
    // Cycle de vie
    // ------------------------------------------------------------------

    @Transactional
    CourseSessionResponse create(CourseSessionRequests.Create request, String callerSubject) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new CourseSessionException(CourseSessionException.Kind.INVALID_PERIOD);
        }
        String timeZoneId = requireZone(request.timeZoneId());

        TeacherDirectory.TeacherRef teacher = requireEligibleTeacher(request.teacherPublicId());

        LinkedHashSet<Long> classInternalIds = new LinkedHashSet<>();
        boolean globalScope = academicScope.hasGlobalScope();
        for (String rawClassId : request.classPublicIds()) {
            UUID classPublicId = parseUuid(rawClassId, CourseSessionException.Kind.CLASS_NOT_FOUND);
            ClassGroupDirectory.ClassGroupRef classRef = classGroupDirectory.findByPublicId(classPublicId)
                    .orElseThrow(() -> new CourseSessionException(CourseSessionException.Kind.CLASS_NOT_FOUND));
            if (!classRef.openForEnrollment()) {
                throw new CourseSessionException(CourseSessionException.Kind.CLASS_INACTIVE);
            }
            if (!globalScope && !academicScope.isClassInScope(classPublicId)) {
                throw new CourseSessionException(CourseSessionException.Kind.SCOPE_FORBIDDEN);
            }
            classInternalIds.add(classRef.internalId());
        }
        if (classInternalIds.isEmpty()) {
            throw new CourseSessionException(CourseSessionException.Kind.NO_CLASS);
        }

        Long actorId = changePublisher.actorId(callerSubject);
        CourseSession session = new CourseSession(teacher.internalId(), trimToNull(request.title()),
                request.startsAt(), request.endsAt(), timeZoneId, request.reason().trim());
        session.markCreatedBy(actorId);
        classInternalIds.forEach(session::addClass);
        CourseSession saved = sessionRepository.save(session);
        checkpointRepository.save(new AttendanceCheckpoint(saved));

        changePublisher.publish(saved.getPublicId(), CourseSessionChangeAction.CREATED, actorId,
                "teacher=" + teacher.publicId() + ";classes=" + classInternalIds.size());
        return toResponse(saved);
    }

    @Transactional
    void open(String publicId, String callerSubject) {
        CourseSession session = require(publicId);
        requireAccess(session, AccessLevel.MANAGE, callerSubject);
        if (!session.isPlanned()) {
            throw new CourseSessionException(CourseSessionException.Kind.INVALID_STATE);
        }
        Instant now = clock.instant();
        Long actorId = changePublisher.actorId(callerSubject);
        session.open(now, actorId);
        // Compat V9 : à l'ouverture de la séance, le point de contrôle
        // START (le premier, créé avec la séance) est ouvert d'office.
        // Les points de contrôle supplémentaires (V10) s'ouvrent
        // individuellement.
        firstCheckpoint(session).ifPresent(cp -> {
            if (cp.isPlanned()) {
                cp.open(now, actorId);
            }
        });
        changePublisher.publish(session.getPublicId(), CourseSessionChangeAction.OPENED, actorId, null);
    }

    @Transactional
    void close(String publicId, String callerSubject) {
        CourseSession session = require(publicId);
        requireAccess(session, AccessLevel.MANAGE, callerSubject);
        if (!session.isOpen()) {
            throw new CourseSessionException(CourseSessionException.Kind.INVALID_STATE);
        }
        Instant now = clock.instant();
        Long actorId = changePublisher.actorId(callerSubject);
        session.close(now, actorId);
        // Tous les points de contrôle encore ouverts sont fermés avec la
        // séance (V10). Les jetons Redis sont purgés à la réception de
        // l'événement CLOSED côté module attendance.
        checkpointRepository.findByCourseSessionIdOrderByDisplayOrderAscIdAsc(session.getId())
                .forEach(cp -> {
                    if (cp.isOpen()) {
                        cp.close(now, actorId);
                    }
                });
        changePublisher.publish(session.getPublicId(), CourseSessionChangeAction.CLOSED, actorId, null);
    }

    /**
     * Annule une séance {@code PLANNED} ou {@code OPEN} avec un motif
     * obligatoire (G1-C ; EF-SES-004, CDC §15.4). Terminal : pas de
     * réouverture. Purge des jetons Redis (via l'événement
     * {@code CANCELLED}), annulation des points de contrôle non
     * terminaux, aucune présence ni absence dérivée. Une séance
     * {@code CLOSED} ou déjà {@code CANCELLED} → {@code 409}
     * ({@code SESSION_INVALID_STATE}) — cohérent avec {@link #open} /
     * {@link #close} (transitions strictes, pas d'idempotence).
     */
    @Transactional
    void cancel(String publicId, String reason, String callerSubject) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            throw new CourseSessionException(CourseSessionException.Kind.CANCEL_REASON_REQUIRED);
        }
        if (trimmed.length() > 500) {
            trimmed = trimmed.substring(0, 500);
        }
        // Lookup SANS le filtre « opérationnel » : une séance supersédée
        // reste annulable (elle passe alors doublement inactive) ; une
        // séance déjà CANCELLED est détectée ci-dessous pour renvoyer un
        // 409 explicite plutôt qu'un 404.
        CourseSession session = sessionRepository
                .findByPublicId(parseUuid(publicId, CourseSessionException.Kind.SESSION_NOT_FOUND))
                .orElseThrow(() -> new CourseSessionException(CourseSessionException.Kind.SESSION_NOT_FOUND));
        requireAccess(session, AccessLevel.MANAGE, callerSubject);
        if (!session.isCancellable()) {
            throw new CourseSessionException(CourseSessionException.Kind.INVALID_STATE);
        }
        Instant now = clock.instant();
        Long actorId = changePublisher.actorId(callerSubject);
        session.cancel(trimmed, now, actorId);
        checkpointRepository.findByCourseSessionIdOrderByDisplayOrderAscIdAsc(session.getId())
                .forEach(cp -> {
                    if (cp.isPlanned() || cp.isOpen()) {
                        cp.cancel("Séance annulée", actorId);
                    }
                });
        // Détail non sensible : le motif (potentiellement nominatif) n'entre
        // jamais dans l'événement / l'audit — il reste sur l'entité, visible
        // aux seuls rôles autorisés via GET /sessions/{id}.
        changePublisher.publish(session.getPublicId(), CourseSessionChangeAction.CANCELLED, actorId, null);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private CourseSession require(String publicId) {
        CourseSession session = sessionRepository
                .findByPublicId(parseUuid(publicId, CourseSessionException.Kind.SESSION_NOT_FOUND))
                .orElseThrow(() -> new CourseSessionException(CourseSessionException.Kind.SESSION_NOT_FOUND));
        // Garde centralisée (audit G1-B.1) : une séance retirée par une
        // republication de planning (DEC-G1-004 règle 4) est traitée
        // comme inexistante pour toute consultation / gestion.
        if (!session.isOperational()) {
            throw new CourseSessionException(CourseSessionException.Kind.SESSION_NOT_FOUND);
        }
        return session;
    }

    private Optional<AttendanceCheckpoint> firstCheckpoint(CourseSession session) {
        return checkpointRepository.findFirstByCourseSessionIdOrderByDisplayOrderAscIdAsc(session.getId());
    }

    private void requireAccess(CourseSession session, AccessLevel level, String callerSubject) {
        Set<UUID> classPublicIds = classPublicIds(session);
        if (!accessGuard.isAllowed(session.getTeacherUserId(), session.getId(), classPublicIds, level, callerSubject)) {
            throw new CourseSessionException(accessGuard.isPedagogicalManagerScoped()
                    ? CourseSessionException.Kind.SCOPE_FORBIDDEN
                    : CourseSessionException.Kind.OPERATION_FORBIDDEN);
        }
    }

    /**
     * Restriction de liste selon le périmètre de l'appelant :
     * <ul>
     *   <li>accès global → {@link #ALLOW_ALL} (aucune restriction) ;</li>
     *   <li>formateur seul → ses séances ;</li>
     *   <li>responsable pédagogique restreint → séances comportant au
     *       moins une de ses classes visibles ;</li>
     *   <li>aucune classe visible / appelant non résolu →
     *       {@link Optional#empty()} (page vide).</li>
     * </ul>
     */
    private Optional<Specification<CourseSession>> scopeRestriction(String callerSubject) {
        if (accessGuard.hasGlobalReadScope()) {
            return Optional.of(ALLOW_ALL);
        }
        if (accessGuard.isTeacherOnly()) {
            return accessGuard.callerInternalId(callerSubject)
                    .map(CourseSessionSpecifications::taughtBy);
        }
        if (accessGuard.isPedagogicalManagerScoped()) {
            Set<Long> visible = academicScope.visibleClassGroupIds().orElse(Set.of());
            if (visible.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(CourseSessionSpecifications.hasAnyClassIn(visible));
        }
        return Optional.empty();
    }

    private Optional<Long> resolveTeacherFilter(String teacherPublicId) {
        if (teacherPublicId == null || teacherPublicId.isBlank()) {
            return Optional.empty();
        }
        return userDirectory.findByPublicId(parseUuid(teacherPublicId, CourseSessionException.Kind.INVALID_FILTER))
                .map(UserDirectory.UserRef::internalId)
                .or(() -> Optional.of(-1L)); // identifiant impossible → aucune séance
    }

    private Optional<Long> resolveClassFilter(String classPublicId) {
        if (classPublicId == null || classPublicId.isBlank()) {
            return Optional.empty();
        }
        UUID publicId = parseUuid(classPublicId, CourseSessionException.Kind.INVALID_FILTER);
        if (!academicScope.hasGlobalScope() && !academicScope.isClassInScope(publicId)) {
            throw new CourseSessionException(CourseSessionException.Kind.SCOPE_FORBIDDEN);
        }
        return classGroupDirectory.findByPublicId(publicId)
                .map(ClassGroupDirectory.ClassGroupRef::internalId)
                .or(() -> Optional.of(-1L));
    }

    private Set<UUID> classPublicIds(CourseSession session) {
        return session.getClasses().stream()
                .map(SessionClass::getClassGroupId)
                .map(classGroupDirectory::findByInternalId)
                .filter(Optional::isPresent)
                .map(ref -> ref.get().publicId())
                .collect(Collectors.toUnmodifiableSet());
    }

    private CourseSessionResponse toResponse(CourseSession session) {
        long teacherId = session.getTeacherUserId();
        UUID teacherPublicId = userDirectory.findByInternalId(teacherId)
                .map(UserDirectory.UserRef::publicId).orElse(null);
        UserDirectory.PersonName name = userDirectory.findName(teacherId).orElse(null);
        CourseSessionResponse.TeacherView teacherView = new CourseSessionResponse.TeacherView(teacherPublicId,
                name != null ? name.firstName() : null, name != null ? name.lastName() : null);

        List<CourseSessionResponse.SessionClassView> classViews = session.getClasses().stream()
                .map(SessionClass::getClassGroupId)
                .map(classGroupDirectory::findByInternalId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(ref -> new CourseSessionResponse.SessionClassView(ref.publicId(), ref.code()))
                .toList();

        List<AttendanceCheckpoint> checkpoints = checkpointRepository
                .findByCourseSessionIdOrderByDisplayOrderAscIdAsc(session.getId());
        List<CourseSessionResponse.CheckpointView> checkpointViews = checkpoints.stream()
                .map(CourseSessionResponse.CheckpointView::from)
                .toList();
        // Compat V9 : checkpointPublicId / checkpointOpen reflètent le
        // premier point de contrôle (START).
        AttendanceCheckpoint first = checkpoints.isEmpty() ? null : checkpoints.get(0);
        UUID checkpointPublicId = first != null ? first.getPublicId() : null;
        boolean checkpointOpen = first != null && first.isOpen();

        return new CourseSessionResponse(session.getPublicId(), session.getStatus(), session.getTitle(),
                session.getExceptionReason(), teacherView, classViews, session.getStartsAt(), session.getEndsAt(),
                session.getTimeZoneId(), session.getOpenedAt(), session.getClosedAt(),
                session.getCancellationReason(), session.getCancelledAt(),
                checkpointPublicId, checkpointOpen, checkpointViews,
                session.getCreatedAt(), session.getUpdatedAt());
    }

    private TeacherDirectory.TeacherRef requireEligibleTeacher(String teacherPublicId) {
        UUID publicId = parseUuid(teacherPublicId, CourseSessionException.Kind.TEACHER_NOT_FOUND);
        if (userDirectory.findByPublicId(publicId).isEmpty()) {
            throw new CourseSessionException(CourseSessionException.Kind.TEACHER_NOT_FOUND);
        }
        return teacherDirectory.findEligibleTeacher(publicId)
                .orElseThrow(() -> new CourseSessionException(CourseSessionException.Kind.TEACHER_NOT_ELIGIBLE));
    }

    private Pageable pageable(int page, int size, String sort) {
        return org.springframework.data.domain.PageRequest.of(Math.max(page, 0), normalizeSize(size),
                parseSort(sort));
    }

    private static int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }
        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        if (!SORTABLE.contains(field)) {
            throw new CourseSessionException(CourseSessionException.Kind.INVALID_SORT);
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            direction = Sort.Direction.fromOptionalString(parts[1].trim())
                    .orElseThrow(() -> new CourseSessionException(CourseSessionException.Kind.INVALID_SORT));
        }
        return Sort.by(direction, field);
    }

    private static Optional<SessionLifecycle> parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(SessionLifecycle.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException invalid) {
            throw new CourseSessionException(CourseSessionException.Kind.INVALID_FILTER);
        }
    }

    private static String requireZone(String value) {
        try {
            return ZoneId.of(value.trim()).getId();
        } catch (RuntimeException invalid) {
            throw new CourseSessionException(CourseSessionException.Kind.INVALID_TIME_ZONE);
        }
    }

    private static UUID parseUuid(String value, CourseSessionException.Kind kind) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException | NullPointerException notAUuid) {
            throw new CourseSessionException(kind);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Sentinelle « aucune restriction » pour {@link #scopeRestriction}. */
    private static final Specification<CourseSession> ALLOW_ALL = (root, query, cb) -> cb.conjunction();
}
