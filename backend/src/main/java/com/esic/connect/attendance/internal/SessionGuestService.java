package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttendanceChangeAction;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory.AccessLevel;
import com.esic.connect.coursesession.CourseSessionDirectory.CheckpointRef;
import com.esic.connect.enrollment.EnrollmentDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Apprenant provisoire en séance (EF-ATT-007 ; docs/02 §16.12).
 *
 * <p>Le formateur signale une personne présente sans inscription
 * enregistrée. L'entrée <strong>ne crée pas d'inscription officielle</strong>
 * et n'entre dans aucun calcul d'assiduité : c'est un signalement, adressé
 * au responsable pédagogique, qui devra le régulariser.
 *
 * <p>La régularisation est une décision humaine tracée : rattacher à une
 * inscription réelle, ou écarter avec motif. Le rattachement ne fabrique
 * pas une présence — celle-ci se saisit ensuite par la voie manuelle
 * ordinaire, motivée et auditée. Convertir silencieusement reviendrait à
 * créer une présence que personne n'a décidée.
 */
@Service
class SessionGuestService {

    private final SessionGuestAttendanceRepository repository;
    private final CourseSessionDirectory courseSessionDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final AttendanceChangePublisher changePublisher;
    private final Clock clock;

    SessionGuestService(SessionGuestAttendanceRepository repository,
                        CourseSessionDirectory courseSessionDirectory,
                        EnrollmentDirectory enrollmentDirectory,
                        AttendanceChangePublisher changePublisher,
                        Clock clock) {
        this.repository = repository;
        this.courseSessionDirectory = courseSessionDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.changePublisher = changePublisher;
        this.clock = clock;
    }

    @Transactional
    SessionGuestResponse declare(String sessionPublicId, AttendanceManagementRequests.DeclareGuest request,
                                 String callerSubject) {
        // MANAGE et non READ : signaler quelqu'un en séance est un acte de
        // conduite de séance, réservé à qui la tient.
        CourseSessionDirectory.SessionRef session = requireSession(sessionPublicId, AccessLevel.MANAGE);

        Long checkpointId = null;
        if (request.checkpointPublicId() != null && !request.checkpointPublicId().isBlank()) {
            CheckpointRef checkpoint = session.checkpoint(parseUuid(request.checkpointPublicId(),
                            AttendanceException.Kind.CHECKPOINT_NOT_FOUND))
                    .orElseThrow(() -> new AttendanceException(
                            AttendanceException.Kind.CHECKPOINT_NOT_FOUND));
            checkpointId = checkpoint.internalId();
        }

        SessionGuestStatus status = parseStatus(request.status());
        if (!status.isPending()) {
            // LINKED / DISMISSED sont des RÉSULTATS de régularisation :
            // les accepter à la création laisserait créer une entrée déjà
            // « résolue » sans qu'aucune décision n'ait été prise.
            throw new AttendanceException(AttendanceException.Kind.INVALID_SUBMISSION);
        }

        Long actorId = changePublisher.actorId(callerSubject);
        if (actorId == null) {
            throw new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN);
        }

        SessionGuestAttendance guest = new SessionGuestAttendance(session.internalId(), checkpointId,
                request.firstName().trim(), request.lastName().trim(),
                trimToNull(request.email()), trimToNull(request.comment()),
                status, actorId, clock.instant());
        SessionGuestAttendance saved = repository.saveAndFlush(guest);

        // Détail d'audit sans identité : le nom déclaré est une donnée
        // personnelle, l'audit métier n'en porte pas (docs/02 §23.3).
        changePublisher.publishRecord(saved.getPublicId(), actorId,
                AttendanceChangeAction.GUEST_DECLARED,
                "session=" + session.publicId() + ";status=" + status.name());
        return toResponse(saved, session.publicId());
    }

    @Transactional(readOnly = true)
    List<SessionGuestResponse> listForSession(String sessionPublicId, String callerSubject) {
        CourseSessionDirectory.SessionRef session = requireSession(sessionPublicId, AccessLevel.READ);
        return repository.findByCourseSessionIdOrderByRecordedAtAsc(session.internalId()).stream()
                .map(guest -> toResponse(guest, session.publicId()))
                .toList();
    }

    @Transactional
    SessionGuestResponse resolve(String guestPublicId,
                                 AttendanceManagementRequests.ResolveGuest request,
                                 String callerSubject) {
        SessionGuestAttendance guest = repository.findByPublicId(
                        parseUuid(guestPublicId, AttendanceException.Kind.RECORD_NOT_FOUND))
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.RECORD_NOT_FOUND));
        if (!guest.isPending()) {
            throw new AttendanceException(AttendanceException.Kind.RECORD_INVALID_STATE);
        }

        CourseSessionDirectory.SessionRef session = courseSessionDirectory
                .findSessionByInternalId(guest.getCourseSessionId())
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.SESSION_NOT_FOUND));
        requireSession(session.publicId().toString(), AccessLevel.MANAGE);

        Long actorId = changePublisher.actorId(callerSubject);
        if (Boolean.TRUE.equals(request.link())) {
            EnrollmentDirectory.EnrollmentRef enrollment = enrollmentDirectory
                    .findByPublicId(parseUuid(request.enrollmentPublicId(),
                            AttendanceException.Kind.ENROLLMENT_NOT_FOUND))
                    .orElseThrow(() -> new AttendanceException(
                            AttendanceException.Kind.ENROLLMENT_NOT_FOUND));
            if (!session.classGroupPublicIds().contains(enrollment.classGroupPublicId())) {
                // Rattacher à une inscription d'une autre classe
                // fabriquerait une présence dans une séance qui n'attendait
                // pas cet apprenant.
                throw new AttendanceException(AttendanceException.Kind.NOT_ENROLLED);
            }
            guest.link(enrollment.internalId(), trimToNull(request.comment()), actorId, clock.instant());
        } else {
            guest.dismiss(trimToNull(request.comment()), actorId, clock.instant());
        }
        SessionGuestAttendance saved = repository.saveAndFlush(guest);

        changePublisher.publishRecord(saved.getPublicId(), actorId,
                AttendanceChangeAction.GUEST_RESOLVED,
                "session=" + session.publicId() + ";status=" + saved.getStatus().name());
        return toResponse(saved, session.publicId());
    }

    // ------------------------------------------------------------------

    private SessionGuestResponse toResponse(SessionGuestAttendance guest, UUID sessionPublicId) {
        UUID enrollmentPublicId = guest.getLinkedEnrollmentId() == null ? null
                : enrollmentDirectory.findByInternalId(guest.getLinkedEnrollmentId())
                        .map(EnrollmentDirectory.EnrollmentRef::publicId).orElse(null);
        return new SessionGuestResponse(guest.getPublicId(), sessionPublicId,
                guest.getFirstName(), guest.getLastName(), guest.getEmail(), guest.getComment(),
                guest.getStatus().name(), guest.getRecordedAt(), guest.getResolvedAt(),
                guest.getResolutionComment(), enrollmentPublicId, guest.getCreatedAt());
    }

    private CourseSessionDirectory.SessionRef requireSession(String sessionPublicId, AccessLevel level) {
        CourseSessionDirectory.SessionAccess access = courseSessionDirectory.resolve(
                parseUuid(sessionPublicId, AttendanceException.Kind.SESSION_NOT_FOUND), level);
        return switch (access.access()) {
            case NOT_FOUND -> throw new AttendanceException(AttendanceException.Kind.SESSION_NOT_FOUND);
            case FORBIDDEN -> throw new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN);
            case GRANTED -> access.session();
        };
    }

    private static SessionGuestStatus parseStatus(String value) {
        try {
            return SessionGuestStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new AttendanceException(AttendanceException.Kind.INVALID_SUBMISSION);
        }
    }

    private static UUID parseUuid(String value, AttendanceException.Kind kind) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new AttendanceException(kind);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
