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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    DefaultCourseSessionDirectory(CourseSessionRepository sessionRepository,
                                  AttendanceCheckpointRepository checkpointRepository,
                                  TeacherSubstitutionRepository substitutionRepository,
                                  ClassGroupDirectory classGroupDirectory,
                                  UserDirectory userDirectory,
                                  CourseSessionAccessGuard accessGuard) {
        this.sessionRepository = sessionRepository;
        this.checkpointRepository = checkpointRepository;
        this.substitutionRepository = substitutionRepository;
        this.classGroupDirectory = classGroupDirectory;
        this.userDirectory = userDirectory;
        this.accessGuard = accessGuard;
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
        Set<Long> internalIds = classGroupPublicIds.stream()
                .map(classGroupDirectory::findByPublicId)
                .filter(Optional::isPresent)
                .map(ref -> ref.get().internalId())
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
        return sessionRepository.findAll(Specification.allOf(specs), Sort.by(Sort.Direction.ASC, "startsAt"))
                .stream()
                .map(session -> toRef(session, classPublicIds(session)))
                .toList();
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
    public List<SessionRef> findSessionsInRange(Instant from, Instant to) {
        List<Specification<CourseSession>> specs = new ArrayList<>();
        specs.add(CourseSessionSpecifications.operational());
        if (from != null) {
            specs.add(CourseSessionSpecifications.startsFrom(from));
        }
        if (to != null) {
            specs.add(CourseSessionSpecifications.startsUntil(to));
        }
        return sessionRepository.findAll(Specification.allOf(specs), Sort.by(Sort.Direction.ASC, "startsAt"))
                .stream()
                .map(session -> toRef(session, classPublicIds(session)))
                .toList();
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
        specs.add(CourseSessionSpecifications.taughtBy(teacherId));
        if (from != null) {
            specs.add(CourseSessionSpecifications.startsFrom(from));
        }
        if (to != null) {
            specs.add(CourseSessionSpecifications.startsUntil(to));
        }
        return sessionRepository.findAll(Specification.allOf(specs),
                        org.springframework.data.domain.PageRequest.of(0, bounded,
                                Sort.by(Sort.Direction.ASC, "startsAt")))
                .stream()
                .map(session -> toRef(session, classPublicIds(session)))
                .toList();
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
                        session.getEndsAt()))
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
            return new SessionNotificationInfo(session.getPublicId(), session.getTitle(), principal, substitutes);
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

    private SessionRef toRef(CourseSession session, Set<UUID> classPublicIds) {
        List<CheckpointRef> checkpoints = checkpointRepository
                .findByCourseSessionIdOrderByDisplayOrderAscIdAsc(session.getId()).stream()
                .map(cp -> new CheckpointRef(cp.getId(), cp.getPublicId(), cp.getLabel(),
                        cp.getCheckpointType(), cp.getStatus(), cp.isRequired(), cp.getDisplayOrder(),
                        cp.getOpenedAt(), cp.getClosedAt()))
                .toList();
        return new SessionRef(session.getId(), session.getPublicId(), session.getTitle(),
                session.getStatus(), session.getTeacherUserId(), checkpoints, classPublicIds,
                session.getTimeZoneId(), session.getStartsAt(), session.getEndsAt());
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
