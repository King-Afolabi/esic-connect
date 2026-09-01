package com.esic.connect.coursesession.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.coursesession.CourseSessionChangeAction;
import com.esic.connect.coursesession.PlanningSessionWriter;
import com.esic.connect.coursesession.SessionLifecycle;
import com.esic.connect.identity.TeacherDirectory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implémentation du port {@link PlanningSessionWriter} (DEC-G1-001).
 * Confinée à {@code coursesession.internal} : le module {@code planning}
 * ne connaît que l'interface publique et ses records.
 *
 * <p>Exécutée <strong>dans la transaction de publication</strong> de
 * {@code planning} : toute exception (dont
 * {@link PlanningSessionSyncException}) fait rollback l'ensemble. Aucun
 * état partiel.
 */
@Component
class DefaultPlanningSessionWriter implements PlanningSessionWriter {

    private final CourseSessionRepository sessionRepository;
    private final AttendanceCheckpointRepository checkpointRepository;
    private final TeacherDirectory teacherDirectory;
    private final ClassGroupDirectory classGroupDirectory;
    private final CourseSessionChangePublisher changePublisher;

    DefaultPlanningSessionWriter(CourseSessionRepository sessionRepository,
                                 AttendanceCheckpointRepository checkpointRepository,
                                 TeacherDirectory teacherDirectory,
                                 ClassGroupDirectory classGroupDirectory,
                                 CourseSessionChangePublisher changePublisher) {
        this.sessionRepository = sessionRepository;
        this.checkpointRepository = checkpointRepository;
        this.teacherDirectory = teacherDirectory;
        this.classGroupDirectory = classGroupDirectory;
        this.changePublisher = changePublisher;
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public PlanningSyncResult sync(PlanningSyncCommand command) {
        ClassGroupDirectory.ClassGroupRef classRef = classGroupDirectory
                .findByPublicId(command.classGroupPublicId())
                .orElseThrow(() -> new PlanningSessionSyncException(
                        PlanningSessionSyncException.Kind.CLASS_UNKNOWN, null));
        Long actorId = currentActorId();

        List<SyncedSession> created = new ArrayList<>();
        List<SyncedSession> reused = new ArrayList<>();
        Set<UUID> incomingSlotIds = new HashSet<>();

        for (PlannedSession entry : command.entries()) {
            incomingSlotIds.add(entry.slotPublicId());
            if (entry.title() == null || entry.title().isBlank()
                    || entry.startsAt() == null || entry.endsAt() == null
                    || !entry.endsAt().isAfter(entry.startsAt())
                    || entry.timeZoneId() == null || entry.timeZoneId().isBlank()) {
                throw new PlanningSessionSyncException(
                        PlanningSessionSyncException.Kind.INVALID_ENTRY, entry.slotPublicId());
            }
            TeacherDirectory.TeacherRef teacher = teacherDirectory
                    .findEligibleTeacher(entry.teacherPublicId())
                    .orElseThrow(() -> new PlanningSessionSyncException(
                            PlanningSessionSyncException.Kind.TEACHER_NOT_ELIGIBLE, entry.slotPublicId()));

            Optional<CourseSession> existing = sessionRepository
                    .findByPlanningSlotPublicId(entry.slotPublicId());
            if (existing.isEmpty()) {
                CourseSession session = CourseSession.fromPlanningSlot(entry.slotPublicId(),
                        teacher.internalId(), entry.title().trim(), entry.startsAt(), entry.endsAt(),
                        entry.timeZoneId());
                session.markCreatedBy(actorId);
                session.addClass(classRef.internalId());
                CourseSession saved = sessionRepository.save(session);
                checkpointRepository.save(new AttendanceCheckpoint(saved));
                changePublisher.publish(saved.getPublicId(), CourseSessionChangeAction.CREATED, actorId,
                        "planningSlot=" + entry.slotPublicId());
                created.add(new SyncedSession(entry.slotPublicId(), saved.getPublicId()));
            } else {
                CourseSession session = existing.get();
                if (session.isPlanned() && !session.isSupersededByScheduling()
                        && hasChanged(session, teacher.internalId(), entry)) {
                    session.applyPlanningUpdate(teacher.internalId(), entry.title().trim(),
                            entry.startsAt(), entry.endsAt(), entry.timeZoneId(), actorId);
                }
                reused.add(new SyncedSession(entry.slotPublicId(), session.getPublicId()));
            }
        }

        // Supersession des séances planning PLANNED dont le créneau stable a disparu.
        List<SupersededSession> superseded = new ArrayList<>();
        for (CourseSession candidate : sessionRepository.findPlanningSessionsForClass(
                classRef.internalId(), SessionLifecycle.PLANNED)) {
            UUID previousSlot = candidate.getPlanningSlotPublicId();
            if (previousSlot != null && !incomingSlotIds.contains(previousSlot)) {
                candidate.markSupersededByScheduling(actorId);
                superseded.add(new SupersededSession(candidate.getPublicId(), previousSlot));
            }
        }

        return new PlanningSyncResult(created, reused, superseded);
    }

    private static boolean hasChanged(CourseSession session, long teacherInternalId, PlannedSession entry) {
        return !Objects.equals(session.getTeacherUserId(), teacherInternalId)
                || !Objects.equals(session.getTitle(), entry.title().trim())
                || !session.getStartsAt().equals(entry.startsAt())
                || !session.getEndsAt().equals(entry.endsAt())
                || !Objects.equals(session.getTimeZoneId(), entry.timeZoneId());
    }

    private Long currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String subject = authentication != null ? authentication.getName() : null;
        return changePublisher.actorId(subject);
    }
}
