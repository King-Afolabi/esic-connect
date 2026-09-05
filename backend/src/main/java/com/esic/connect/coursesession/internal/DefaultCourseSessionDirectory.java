package com.esic.connect.coursesession.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.identity.UserDirectory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implémentation du port {@link CourseSessionDirectory}. Reste confinée à
 * {@code coursesession.internal} : les autres modules ne connaissent que
 * l'interface publique et ses records.
 *
 * <p>Le contrôle d'accès réutilise {@link CourseSessionAccessGuard}
 * (contexte Spring Security de l'appelant courant) : la décision de
 * périmètre reste dans {@code coursesession}, jamais dans le module
 * appelant.
 */
@Component
class DefaultCourseSessionDirectory implements CourseSessionDirectory {

    private final CourseSessionRepository sessionRepository;
    private final AttendanceCheckpointRepository checkpointRepository;
    private final TeacherSubstitutionRepository substitutionRepository;
    private final ClassGroupDirectory classGroupDirectory;
    private final UserDirectory userDirectory;
    private final CourseSessionAccessGuard accessGuard;
    private final Clock clock;

    DefaultCourseSessionDirectory(CourseSessionRepository sessionRepository,
                                  AttendanceCheckpointRepository checkpointRepository,
                                  TeacherSubstitutionRepository substitutionRepository,
                                  ClassGroupDirectory classGroupDirectory,
                                  UserDirectory userDirectory,
                                  CourseSessionAccessGuard accessGuard,
                                  Clock clock) {
        this.sessionRepository = sessionRepository;
        this.checkpointRepository = checkpointRepository;
        this.substitutionRepository = substitutionRepository;
        this.classGroupDirectory = classGroupDirectory;
        this.userDirectory = userDirectory;
        this.accessGuard = accessGuard;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public SessionAccess resolve(UUID sessionPublicId, AccessLevel level) {
        if (sessionPublicId == null) {
            return new SessionAccess(Access.NOT_FOUND, null);
        }
        Optional<CourseSession> found = sessionRepository.findByPublicId(sessionPublicId);
        if (found.isEmpty() || !found.get().isOperational()) {
            // Séance inexistante OU retirée par une republication de
            // planning (DEC-G1-004 règle 4) : indistinguable d'une
            // absence pour tout accès métier (audit G1-B.1).
            return new SessionAccess(Access.NOT_FOUND, null);
        }
        CourseSession session = found.get();
        Set<UUID> classPublicIds = classPublicIds(session);
        if (!accessGuard.isAllowed(session.getTeacherUserId(), session.getId(), classPublicIds, level, currentSubject())) {
            return new SessionAccess(Access.FORBIDDEN, null);
        }
        return new SessionAccess(Access.GRANTED, toRef(session, classPublicIds));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionRef> findForAttendance(UUID sessionPublicId) {
        if (sessionPublicId == null) {
            return Optional.empty();
        }
        return sessionRepository.findByPublicId(sessionPublicId)
                .filter(CourseSession::isOperational)
                .map(session -> toRef(session, classPublicIds(session)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CheckpointRef> findCheckpointForAttendance(UUID sessionPublicId, UUID checkpointPublicId) {
        if (sessionPublicId == null || checkpointPublicId == null) {
            return Optional.empty();
        }
        return sessionRepository.findByPublicId(sessionPublicId)
                .filter(CourseSession::isOperational)
                .flatMap(session -> toRef(session, classPublicIds(session)).checkpoint(checkpointPublicId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionRef> findSessionsForClasses(Set<UUID> classGroupPublicIds, Instant from, Instant to) {
        if (classGroupPublicIds == null || classGroupPublicIds.isEmpty()) {
            return List.of();
        }
        // Résolution des classes en UNE requête (anti-N+1) : jamais un
        // findByPublicId par identifiant dans la boucle.
        Set<Long> internalIds = classGroupDirectory.findByPublicIds(classGroupPublicIds).stream()
                .map(ClassGroupDirectory.ClassGroupRef::internalId)
                .collect(Collectors.toUnmodifiableSet());
        if (internalIds.isEmpty()) {
            return List.of();
        }
        List<Specification<CourseSession>> specs = new ArrayList<>();
        specs.add(CourseSessionSpecifications.operational());
        specs.add(CourseSessionSpecifications.hasAnyClassIn(internalIds));
        if (from != null) {
            specs.add(CourseSessionSpecifications.startsFrom(from));
        }
        if (to != null) {
            specs.add(CourseSessionSpecifications.startsUntil(to));
        }
        return toRefs(sessionRepository.findAll(Specification.allOf(specs),
                Sort.by(Sort.Direction.ASC, "startsAt")));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionRef> findSessionByCheckpointPublicId(UUID checkpointPublicId) {
        if (checkpointPublicId == null) {
            return Optional.empty();
        }
        return checkpointRepository.findByPublicId(checkpointPublicId)
                .map(cp -> cp.getCourseSession())
                .filter(CourseSession::isOperational)
                .map(session -> toRef(session, classPublicIds(session)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionRef> findSessionByInternalId(long sessionInternalId) {
        // Une séance annulée reste consultable en historique (G1-C.3) :
        // une entrée provisoire s'y régularise encore.
        return sessionRepository.findById(sessionInternalId)
                .filter(CourseSession::isHistoricallyReadable)
                .map(session -> toRef(session, classPublicIds(session)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionRef> findSessionsInRange(Instant from, Instant to) {
        List<Specification<CourseSession>> specs = new ArrayList<>();
        specs.add(CourseSessionSpecifications.operational());
        if (from != null) {
            specs.add(CourseSessionSpecifications.startsFrom(from));
        }
        if (to != null) {
            specs.add(CourseSessionSpecifications.startsUntil(to));
        }
        return toRefs(sessionRepository.findAll(Specification.allOf(specs),
                Sort.by(Sort.Direction.ASC, "startsAt")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionRef> findUpcomingForTeacher(UUID teacherPublicId, Instant from, Instant to, int limit) {
        Long teacherId = userDirectory.findByPublicId(teacherPublicId)
                .map(UserDirectory.UserRef::internalId)
                .orElse(null);
        if (teacherId == null) {
            return List.of();
        }
        int bounded = Math.max(1, Math.min(limit, 10));
        List<Specification<CourseSession>> specs = new ArrayList<>();
        specs.add(CourseSessionSpecifications.operational());
        if (from != null) {
            specs.add(CourseSessionSpecifications.startsFrom(from));
        }
        if (to != null) {
            specs.add(CourseSessionSpecifications.startsUntil(to));
        }
        // Formateur principal OU remplaçant ACTIVE couvrant l'instant courant
        // (mêmes règles que GET /sessions — G1-C.3). Une seule requête de
        // substitutions ; l'OR sur une même table ne produit aucun doublon.
        Specification<CourseSession> assignedToTeacher = CourseSessionSpecifications.taughtBy(teacherId);
        List<Long> substituted =
                substitutionRepository.findActiveSubstitutedSessionIds(teacherId, clock.instant());
        if (!substituted.isEmpty()) {
            assignedToTeacher = Specification.anyOf(assignedToTeacher,
                    CourseSessionSpecifications.hasInternalIdIn(substituted));
        }
        specs.add(assignedToTeacher);
        return toRefs(sessionRepository.findAll(Specification.allOf(specs),
                        org.springframework.data.domain.PageRequest.of(0, bounded,
                                Sort.by(Sort.Direction.ASC, "startsAt")))
                .getContent());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionRef> searchSessions(String query, Set<UUID> visibleClassGroupPublicIds, int limit) {
        String pattern = com.esic.connect.shared.SearchPattern.of(query);
        if (pattern == null) {
            return List.of();
        }
        List<Specification<CourseSession>> specs = new ArrayList<>();
        specs.add(CourseSessionSpecifications.notSupersededByScheduling());
        specs.add(CourseSessionSpecifications.titleLike(pattern));
        if (visibleClassGroupPublicIds != null) {
            // Périmètre vide ≠ périmètre global : un responsable sans
            // classe visible ne trouve rien, jamais tout.
            if (visibleClassGroupPublicIds.isEmpty()) {
                return List.of();
            }
            Set<Long> internalIds = classGroupDirectory.findByPublicIds(visibleClassGroupPublicIds).stream()
                    .map(ClassGroupDirectory.ClassGroupRef::internalId)
                    .collect(Collectors.toUnmodifiableSet());
            if (internalIds.isEmpty()) {
                return List.of();
            }
            specs.add(CourseSessionSpecifications.hasAnyClassIn(internalIds));
        }
        return toRefs(sessionRepository.findAll(Specification.allOf(specs),
                        org.springframework.data.domain.PageRequest.of(0,
                                com.esic.connect.shared.SearchPattern.bound(limit),
                                Sort.by(Sort.Direction.DESC, "startsAt")))
                .getContent());
    }

    /** Borne haute d'un calendrier : au-delà, ce n'est plus une lecture d'agenda. */
    private static final int SCHEDULE_LIMIT = 750;

    @Override
    @Transactional(readOnly = true)
    public List<SessionRef> findTeacherSchedule(UUID teacherPublicId, Instant from, Instant to, int limit) {
        Long teacherId = userDirectory.findByPublicId(teacherPublicId)
                .map(UserDirectory.UserRef::internalId)
                .orElse(null);
        if (teacherId == null) {
            return List.of();
        }
        List<Specification<CourseSession>> specs = new ArrayList<>();
        // `notSupersededByScheduling` sans `operational` : une séance
        // ANNULÉE reste dans le calendrier, avec son statut, afin qu'un
        // agenda externe puisse refléter l'annulation (EF-INT-001).
        specs.add(CourseSessionSpecifications.notSupersededByScheduling());
        if (from != null) {
            specs.add(CourseSessionSpecifications.startsFrom(from));
        }
        if (to != null) {
            specs.add(CourseSessionSpecifications.startsUntil(to));
        }
        Specification<CourseSession> assigned = CourseSessionSpecifications.taughtBy(teacherId);
        List<Long> substituted =
                substitutionRepository.findActiveSubstitutedSessionIds(teacherId, clock.instant());
        if (!substituted.isEmpty()) {
            assigned = Specification.anyOf(assigned,
                    CourseSessionSpecifications.hasInternalIdIn(substituted));
        }
        specs.add(assigned);
        return toRefs(sessionRepository.findAll(Specification.allOf(specs),
                        org.springframework.data.domain.PageRequest.of(0, bound(limit),
                                Sort.by(Sort.Direction.ASC, "startsAt")))
                .getContent());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionRef> findClassSchedule(Set<UUID> classGroupPublicIds, Instant from, Instant to,
                                              int limit) {
        if (classGroupPublicIds == null || classGroupPublicIds.isEmpty()) {
            return List.of();
        }
        Set<Long> internalIds = classGroupDirectory.findByPublicIds(classGroupPublicIds).stream()
                .map(ClassGroupDirectory.ClassGroupRef::internalId)
                .collect(Collectors.toUnmodifiableSet());
        if (internalIds.isEmpty()) {
            return List.of();
        }
        List<Specification<CourseSession>> specs = new ArrayList<>();
        specs.add(CourseSessionSpecifications.notSupersededByScheduling());
        specs.add(CourseSessionSpecifications.hasAnyClassIn(internalIds));
        if (from != null) {
            specs.add(CourseSessionSpecifications.startsFrom(from));
        }
        if (to != null) {
            specs.add(CourseSessionSpecifications.startsUntil(to));
        }
        return toRefs(sessionRepository.findAll(Specification.allOf(specs),
                        org.springframework.data.domain.PageRequest.of(0, bound(limit),
                                Sort.by(Sort.Direction.ASC, "startsAt")))
                .getContent());
    }

    private static int bound(int limit) {
        return Math.max(1, Math.min(limit, SCHEDULE_LIMIT));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExistingSessionWindow> findOperationalSessionWindows(Instant from, Instant to) {
        List<Specification<CourseSession>> specs = new ArrayList<>();
        specs.add(CourseSessionSpecifications.operational());
        if (from != null) {
            specs.add(CourseSessionSpecifications.endsAfter(from));
        }
        if (to != null) {
            specs.add(CourseSessionSpecifications.startsBefore(to));
        }
        return sessionRepository.findAll(Specification.allOf(specs), Sort.by(Sort.Direction.ASC, "startsAt"))
                .stream()
                .map(session -> new ExistingSessionWindow(
                        session.getPublicId(),
                        session.getPlanningSlotPublicId(),
                        userDirectory.findByInternalId(session.getTeacherUserId())
                                .map(UserDirectory.UserRef::publicId).orElse(null),
                        classPublicIds(session),
                        session.getStartsAt(),
                        session.getEndsAt(),
                        session.getRoomCode()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionNotificationInfo> findSessionNotificationInfo(UUID sessionPublicId) {
        if (sessionPublicId == null) {
            return Optional.empty();
        }
        return sessionRepository.findByPublicId(sessionPublicId).map(session -> {
            UUID principal = userDirectory.findByInternalId(session.getTeacherUserId())
                    .map(UserDirectory.UserRef::publicId).orElse(null);
            Set<UUID> substitutes = substitutionRepository
                    .findByCourseSessionIdAndStatus(session.getId(), TeacherSubstitutionStatus.ACTIVE).stream()
                    .map(sub -> userDirectory.findByInternalId(sub.getSubstituteTeacherUserId())
                            .map(UserDirectory.UserRef::publicId).orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
            return new SessionNotificationInfo(session.getPublicId(), session.getTitle(),
                    principal, substitutes, classPublicIds(session));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findPrincipalTeacherPublicIds(java.util.Collection<UUID> sessionPublicIds) {
        if (sessionPublicIds == null || sessionPublicIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> distinct = sessionPublicIds.stream()
                .filter(java.util.Objects::nonNull).collect(Collectors.toUnmodifiableSet());
        if (distinct.isEmpty()) {
            return Set.of();
        }
        return sessionRepository.findByPublicIdIn(distinct).stream()
                .map(session -> userDirectory.findByInternalId(session.getTeacherUserId())
                        .map(UserDirectory.UserRef::publicId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Convertit une <strong>liste</strong> de séances en références, en
     * bornant le coût SQL (dette T-03).
     *
     * <p>La conversion unitaire ({@link #toRef}) émet une requête de
     * points de contrôle par séance, et une résolution de classe par
     * rattachement : sur une fenêtre d'une semaine, un tableau de bord ou
     * un rapport payait plusieurs dizaines d'allers-retours pour un
     * résultat que la base rend en deux requêtes. Ici, points de contrôle
     * et identifiants publics de classes sont chargés <strong>en
     * bloc</strong>, puis distribués en mémoire — le coût cesse d'être
     * proportionnel au nombre de séances affichées (NFR-PERF-08).
     */
    private List<SessionRef> toRefs(List<CourseSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        Set<Long> sessionIds = sessions.stream()
                .map(CourseSession::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // Initialise `classes` pour tout le lot : la collection est LAZY,
        // et la parcourir séance par séance coûterait une requête par
        // séance. Le résultat est ignoré — c'est le contexte de
        // persistance qui garde les entités désormais initialisées, et
        // les instances de `sessions` en font partie.
        sessionRepository.findAllWithClassesByIdIn(sessionIds);
        Map<Long, List<CheckpointRef>> checkpointsBySession = new HashMap<>();
        for (AttendanceCheckpoint cp : checkpointRepository
                .findByCourseSessionIdInOrderByCourseSessionIdAscDisplayOrderAscIdAsc(sessionIds)) {
            checkpointsBySession
                    .computeIfAbsent(cp.getCourseSession().getId(), key -> new ArrayList<>())
                    .add(toCheckpointRef(cp));
        }

        Set<Long> classInternalIds = new LinkedHashSet<>();
        for (CourseSession session : sessions) {
            for (SessionClass link : session.getClasses()) {
                classInternalIds.add(link.getClassGroupId());
            }
        }
        Map<Long, UUID> publicIdByInternalId = new HashMap<>();
        if (!classInternalIds.isEmpty()) {
            for (ClassGroupDirectory.ClassGroupRef ref : classGroupDirectory.findByInternalIds(classInternalIds)) {
                publicIdByInternalId.put(ref.internalId(), ref.publicId());
            }
        }

        List<SessionRef> refs = new ArrayList<>(sessions.size());
        for (CourseSession session : sessions) {
            Set<UUID> classPublicIds = session.getClasses().stream()
                    .map(SessionClass::getClassGroupId)
                    .map(publicIdByInternalId::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
            refs.add(new SessionRef(session.getId(), session.getPublicId(), session.getTitle(),
                    session.getStatus(), session.getTeacherUserId(),
                    checkpointsBySession.getOrDefault(session.getId(), List.of()), classPublicIds,
                    session.getTimeZoneId(), session.getStartsAt(), session.getEndsAt(),
                    session.getAttendanceMode(), session.getRoomCode()));
        }
        return refs;
    }

    private static CheckpointRef toCheckpointRef(AttendanceCheckpoint cp) {
        return new CheckpointRef(cp.getId(), cp.getPublicId(), cp.getLabel(),
                cp.getCheckpointType(), cp.getStatus(), cp.isRequired(), cp.getDisplayOrder(),
                cp.getOpenedAt(), cp.getClosedAt());
    }

    private SessionRef toRef(CourseSession session, Set<UUID> classPublicIds) {
        List<CheckpointRef> checkpoints = checkpointRepository
                .findByCourseSessionIdOrderByDisplayOrderAscIdAsc(session.getId()).stream()
                .map(DefaultCourseSessionDirectory::toCheckpointRef)
                .toList();
        return new SessionRef(session.getId(), session.getPublicId(), session.getTitle(),
                session.getStatus(), session.getTeacherUserId(), checkpoints, classPublicIds,
                session.getTimeZoneId(), session.getStartsAt(), session.getEndsAt(),
                session.getAttendanceMode(), session.getRoomCode());
    }

    private Set<UUID> classPublicIds(CourseSession session) {
        return session.getClasses().stream()
                .map(SessionClass::getClassGroupId)
                .map(classGroupDirectory::findByInternalId)
                .filter(Optional::isPresent)
                .map(ref -> ref.get().publicId())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String currentSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }
}
