package com.esic.connect.coursesession.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.coursesession.AttendanceCheckpointChangeAction;
import com.esic.connect.coursesession.AttendanceCheckpointStatus;
import com.esic.connect.coursesession.AttendanceCheckpointType;
import com.esic.connect.coursesession.CourseSessionDirectory.AccessLevel;
import com.esic.connect.coursesession.SessionLifecycle;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gestion des points de contrôle d'émargement d'une séance (V10).
 * Plusieurs points de contrôle par séance, typés
 * {@link AttendanceCheckpointType}, avec un cycle de vie propre
 * {@code PLANNED → OPEN → CLOSED} / {@code CANCELLED}.
 *
 * <p>Le contrôle d'accès fin réutilise {@link CourseSessionAccessGuard}
 * (contexte Spring Security), jamais un paramètre client. Le point
 * {@code START} créé automatiquement avec la séance
 * ({@link CourseSessionService}) n'est pas géré ici mais y apparaît.
 */
@Service
class AttendanceCheckpointService {

    private final CourseSessionRepository sessionRepository;
    private final AttendanceCheckpointRepository checkpointRepository;
    private final CourseSessionAccessGuard accessGuard;
    private final ClassGroupDirectory classGroupDirectory;
    private final CourseSessionChangePublisher changePublisher;
    private final Clock clock;

    AttendanceCheckpointService(CourseSessionRepository sessionRepository,
                                AttendanceCheckpointRepository checkpointRepository,
                                CourseSessionAccessGuard accessGuard,
                                ClassGroupDirectory classGroupDirectory,
                                CourseSessionChangePublisher changePublisher,
                                Clock clock) {
        this.sessionRepository = sessionRepository;
        this.checkpointRepository = checkpointRepository;
        this.accessGuard = accessGuard;
        this.classGroupDirectory = classGroupDirectory;
        this.changePublisher = changePublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    List<CheckpointResponse> list(String sessionPublicId, String callerSubject) {
        CourseSession session = requireSession(sessionPublicId);
        requireAccess(session, AccessLevel.READ, callerSubject);
        return checkpointRepository.findByCourseSessionIdOrderByDisplayOrderAscIdAsc(session.getId()).stream()
                .map(CheckpointResponse::from)
                .toList();
    }

    @Transactional
    CheckpointResponse create(String sessionPublicId, CheckpointRequests.Create request, String callerSubject) {
        CourseSession session = requireSession(sessionPublicId);
        requireAccess(session, AccessLevel.MANAGE, callerSubject);
        if (session.getStatus() == SessionLifecycle.CLOSED) {
            throw new CourseSessionException(CourseSessionException.Kind.CHECKPOINT_SESSION_NOT_OPEN);
        }

        AttendanceCheckpointType type = parseType(request.type());
        List<AttendanceCheckpoint> existing =
                checkpointRepository.findByCourseSessionIdOrderByDisplayOrderAscIdAsc(session.getId());
        // Au plus un START et un END actifs (non annulés) par séance.
        if (type != AttendanceCheckpointType.CUSTOM && existing.stream()
                .anyMatch(cp -> cp.getCheckpointType() == type
                        && cp.getStatus() != AttendanceCheckpointStatus.CANCELLED)) {
            throw new CourseSessionException(CourseSessionException.Kind.CHECKPOINT_INVALID_TYPE);
        }

        int order = request.displayOrder() != null
                ? request.displayOrder()
                : existing.stream().mapToInt(AttendanceCheckpoint::getDisplayOrder).max().orElse(-1) + 1;
        boolean required = request.required() == null || request.required();

        Long actorId = changePublisher.actorId(callerSubject);
        AttendanceCheckpoint checkpoint = new AttendanceCheckpoint(session, request.label().trim(), type,
                order, required, actorId);
        AttendanceCheckpoint saved;
        try {
            saved = checkpointRepository.saveAndFlush(checkpoint);
        } catch (DataIntegrityViolationException violation) {
            if (isOrderConflict(violation)) {
                throw new CourseSessionException(CourseSessionException.Kind.CHECKPOINT_ORDER_CONFLICT);
            }
            throw violation;
        }
        changePublisher.publishCheckpoint(session.getPublicId(), saved.getPublicId(),
                AttendanceCheckpointChangeAction.CREATED, actorId,
                "type=" + type + ";order=" + order);
        return CheckpointResponse.from(saved);
    }

    @Transactional
    void open(String sessionPublicId, String checkpointPublicId, String callerSubject) {
        Ctx ctx = require(sessionPublicId, checkpointPublicId, AccessLevel.MANAGE, callerSubject);
        if (ctx.session().getStatus() != SessionLifecycle.OPEN) {
            throw new CourseSessionException(CourseSessionException.Kind.CHECKPOINT_SESSION_NOT_OPEN);
        }
        if (!ctx.checkpoint().isPlanned()) {
            throw new CourseSessionException(CourseSessionException.Kind.CHECKPOINT_INVALID_STATE);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        ctx.checkpoint().open(clock.instant(), actorId);
        flushCheckpoint(ctx.checkpoint());
        changePublisher.publishCheckpoint(ctx.session().getPublicId(), ctx.checkpoint().getPublicId(),
                AttendanceCheckpointChangeAction.OPENED, actorId, null);
    }

    @Transactional
    void close(String sessionPublicId, String checkpointPublicId, String callerSubject) {
        Ctx ctx = require(sessionPublicId, checkpointPublicId, AccessLevel.MANAGE, callerSubject);
        if (!ctx.checkpoint().isOpen()) {
            throw new CourseSessionException(CourseSessionException.Kind.CHECKPOINT_INVALID_STATE);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        ctx.checkpoint().close(clock.instant(), actorId);
        flushCheckpoint(ctx.checkpoint());
        changePublisher.publishCheckpoint(ctx.session().getPublicId(), ctx.checkpoint().getPublicId(),
                AttendanceCheckpointChangeAction.CLOSED, actorId, null);
    }

    @Transactional
    void cancel(String sessionPublicId, String checkpointPublicId, CheckpointRequests.Cancel request,
                String callerSubject) {
        Ctx ctx = require(sessionPublicId, checkpointPublicId, AccessLevel.MANAGE, callerSubject);
        AttendanceCheckpointStatus status = ctx.checkpoint().getStatus();
        if (status != AttendanceCheckpointStatus.PLANNED && status != AttendanceCheckpointStatus.OPEN) {
            throw new CourseSessionException(CourseSessionException.Kind.CHECKPOINT_INVALID_STATE);
        }
        String reason = request.reason() != null ? request.reason().trim() : "";
        if (reason.isEmpty()) {
            throw new CourseSessionException(CourseSessionException.Kind.CHECKPOINT_REASON_REQUIRED);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        ctx.checkpoint().cancel(reason, actorId);
        flushCheckpoint(ctx.checkpoint());
        changePublisher.publishCheckpoint(ctx.session().getPublicId(), ctx.checkpoint().getPublicId(),
                AttendanceCheckpointChangeAction.CANCELLED, actorId, null);
    }

    // ------------------------------------------------------------------

    private record Ctx(CourseSession session, AttendanceCheckpoint checkpoint) {
    }

    /**
     * Flush explicite d'une transition de point de contrôle : une
     * transition concurrente perdante ({@code @Version}) est retraduite en
     * conflit contrôlé {@code ATT_CHECKPOINT_INVALID_STATE} (409), jamais
     * un 500 (correctif PR #22 §3).
     */
    private void flushCheckpoint(AttendanceCheckpoint checkpoint) {
        try {
            checkpointRepository.saveAndFlush(checkpoint);
        } catch (ObjectOptimisticLockingFailureException concurrent) {
            throw new CourseSessionException(CourseSessionException.Kind.CHECKPOINT_INVALID_STATE);
        }
    }

    private Ctx require(String sessionPublicId, String checkpointPublicId, AccessLevel level,
                        String callerSubject) {
        CourseSession session = requireSession(sessionPublicId);
        requireAccess(session, level, callerSubject);
        AttendanceCheckpoint checkpoint = checkpointRepository
                .findByCourseSessionIdAndPublicId(session.getId(),
                        parseUuid(checkpointPublicId, CourseSessionException.Kind.CHECKPOINT_NOT_FOUND))
                .orElseThrow(() -> new CourseSessionException(CourseSessionException.Kind.CHECKPOINT_NOT_FOUND));
        return new Ctx(session, checkpoint);
    }

    private CourseSession requireSession(String sessionPublicId) {
        CourseSession session = sessionRepository
                .findByPublicId(parseUuid(sessionPublicId, CourseSessionException.Kind.SESSION_NOT_FOUND))
                .orElseThrow(() -> new CourseSessionException(CourseSessionException.Kind.SESSION_NOT_FOUND));
        // Garde centralisée : une séance supersédée (audit G1-B.1) ou
        // annulée (G1-C) est traitée comme inexistante pour la gestion
        // des points de contrôle.
        if (!session.isOperational()) {
            throw new CourseSessionException(CourseSessionException.Kind.SESSION_NOT_FOUND);
        }
        return session;
    }

    private void requireAccess(CourseSession session, AccessLevel level, String callerSubject) {
        Set<UUID> classPublicIds = session.getClasses().stream()
                .map(SessionClass::getClassGroupId)
                .map(classGroupDirectory::findByInternalId)
                .filter(Optional::isPresent)
                .map(ref -> ref.get().publicId())
                .collect(Collectors.toUnmodifiableSet());
        if (!accessGuard.isAllowed(session.getTeacherUserId(), classPublicIds, level, callerSubject)) {
            throw new CourseSessionException(accessGuard.isPedagogicalManagerScoped()
                    ? CourseSessionException.Kind.SCOPE_FORBIDDEN
                    : CourseSessionException.Kind.OPERATION_FORBIDDEN);
        }
    }

    private static AttendanceCheckpointType parseType(String value) {
        try {
            return AttendanceCheckpointType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CourseSessionException(CourseSessionException.Kind.CHECKPOINT_INVALID_TYPE);
        }
    }

    private static UUID parseUuid(String value, CourseSessionException.Kind kind) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException | NullPointerException notAUuid) {
            throw new CourseSessionException(kind);
        }
    }

    private static boolean isOrderConflict(DataIntegrityViolationException violation) {
        for (Throwable cause = violation; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null
                    && message.toLowerCase(Locale.ROOT).contains("uq_attendance_checkpoint_order")) {
                return true;
            }
        }
        return false;
    }
}
