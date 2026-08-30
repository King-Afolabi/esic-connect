package com.esic.connect.coursesession.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    private final ClassGroupDirectory classGroupDirectory;
    private final CourseSessionAccessGuard accessGuard;

    DefaultCourseSessionDirectory(CourseSessionRepository sessionRepository,
                                  AttendanceCheckpointRepository checkpointRepository,
                                  ClassGroupDirectory classGroupDirectory,
                                  CourseSessionAccessGuard accessGuard) {
        this.sessionRepository = sessionRepository;
        this.checkpointRepository = checkpointRepository;
        this.classGroupDirectory = classGroupDirectory;
        this.accessGuard = accessGuard;
    }

    @Override
    @Transactional(readOnly = true)
    public SessionAccess resolve(UUID sessionPublicId, AccessLevel level) {
        if (sessionPublicId == null) {
            return new SessionAccess(Access.NOT_FOUND, null);
        }
        Optional<CourseSession> found = sessionRepository.findByPublicId(sessionPublicId);
        if (found.isEmpty()) {
            return new SessionAccess(Access.NOT_FOUND, null);
        }
        CourseSession session = found.get();
        Set<UUID> classPublicIds = classPublicIds(session);
        if (!accessGuard.isAllowed(session.getTeacherUserId(), classPublicIds, level, currentSubject())) {
            return new SessionAccess(Access.FORBIDDEN, null);
        }
        AttendanceCheckpoint checkpoint = checkpointRepository.findByCourseSessionId(session.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Point de contrôle manquant pour une séance existante"));
        SessionRef ref = new SessionRef(session.getId(), session.getPublicId(), session.getTitle(),
                session.getStatus(), checkpoint.getId(), checkpoint.getPublicId(), checkpoint.isOpen(),
                classPublicIds, session.getStartsAt(), session.getEndsAt());
        return new SessionAccess(Access.GRANTED, ref);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionRef> findForAttendance(UUID sessionPublicId) {
        if (sessionPublicId == null) {
            return Optional.empty();
        }
        return sessionRepository.findByPublicId(sessionPublicId).map(session -> {
            AttendanceCheckpoint checkpoint = checkpointRepository.findByCourseSessionId(session.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Point de contrôle manquant pour une séance existante"));
            return new SessionRef(session.getId(), session.getPublicId(), session.getTitle(),
                    session.getStatus(), checkpoint.getId(), checkpoint.getPublicId(), checkpoint.isOpen(),
                    classPublicIds(session), session.getStartsAt(), session.getEndsAt());
        });
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
