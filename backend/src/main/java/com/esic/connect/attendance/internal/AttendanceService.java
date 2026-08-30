package com.esic.connect.attendance.internal;

import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory.AccessLevel;
import com.esic.connect.coursesession.SessionLifecycle;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.esic.connect.identity.UserDirectory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cœur de l'émargement : émission d'un jeton (délégué à
 * {@link AttendanceTokenService}), validation d'une présence et
 * consultation des présences d'une séance.
 *
 * <p>La méthode {@link #validate} n'est volontairement pas
 * {@code @Transactional} : l'écriture est isolée dans
 * {@link AttendanceRecordPersister} ({@code REQUIRES_NEW}) pour retraduire
 * proprement une violation de contrainte concurrente en 409, jamais en
 * 500. Le serveur détermine l'apprenant émargeur à partir du seul JWT.
 */
@Service
class AttendanceService {

    private final AttendanceTokenService tokenService;
    private final AttendanceRecordRepository recordRepository;
    private final AttendanceRecordPersister recordPersister;
    private final CourseSessionDirectory courseSessionDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final UserDirectory userDirectory;
    private final AttendanceChangePublisher changePublisher;
    private final Clock clock;

    AttendanceService(AttendanceTokenService tokenService,
                      AttendanceRecordRepository recordRepository,
                      AttendanceRecordPersister recordPersister,
                      CourseSessionDirectory courseSessionDirectory,
                      EnrollmentDirectory enrollmentDirectory,
                      UserDirectory userDirectory,
                      AttendanceChangePublisher changePublisher,
                      Clock clock) {
        this.tokenService = tokenService;
        this.recordRepository = recordRepository;
        this.recordPersister = recordPersister;
        this.courseSessionDirectory = courseSessionDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.userDirectory = userDirectory;
        this.changePublisher = changePublisher;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Émission d'un jeton (formateur / gestionnaire)
    // ------------------------------------------------------------------

    AttendanceTokenResponse issueToken(String sessionPublicId, String callerSubject) {
        CourseSessionDirectory.SessionRef session = requireManageable(sessionPublicId);
        if (session.status() != SessionLifecycle.OPEN || !session.checkpointOpen()) {
            throw new AttendanceException(AttendanceException.Kind.SESSION_CLOSED);
        }
        IssuedAttendanceToken issued = tokenService.issue(session.publicId());
        return AttendanceTokenResponse.from(issued, tokenService.ttl().toSeconds());
    }

    // ------------------------------------------------------------------
    // Validation d'une présence (apprenant)
    // ------------------------------------------------------------------

    AttendanceRecordResponse validate(AttendanceRequests.Validate request, String callerSubject) {
        String token = blankToNull(request.token());
        String shortCode = normalizeShortCode(request.shortCode());
        if ((token == null) == (shortCode == null)) {
            throw new AttendanceException(AttendanceException.Kind.INVALID_SUBMISSION);
        }

        UUID sessionPublicId = tokenService.resolveSession(token, shortCode)
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.TOKEN_INVALID));

        CourseSessionDirectory.SessionRef session = courseSessionDirectory.findForAttendance(sessionPublicId)
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.TOKEN_INVALID));
        if (session.status() != SessionLifecycle.OPEN || !session.checkpointOpen()) {
            throw new AttendanceException(AttendanceException.Kind.SESSION_CLOSED);
        }

        UUID callerPublicId = parseSubject(callerSubject);
        UserDirectory.UserRef account = userDirectory.findByPublicId(callerPublicId)
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN));
        if (account.archived()) {
            throw new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN);
        }

        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<EnrollmentDirectory.EnrollmentRef> matching = enrollmentDirectory
                .findActiveEnrollmentsForUserOn(callerPublicId, today).stream()
                .filter(enrollment -> session.classGroupPublicIds().contains(enrollment.classGroupPublicId()))
                .toList();
        if (matching.isEmpty()) {
            throw new AttendanceException(AttendanceException.Kind.NOT_ENROLLED);
        }
        if (matching.size() > 1) {
            throw new AttendanceException(AttendanceException.Kind.ENROLLMENT_AMBIGUOUS);
        }
        EnrollmentDirectory.EnrollmentRef enrollment = matching.get(0);

        AttendanceRecordSource source = token != null
                ? AttendanceRecordSource.DYNAMIC_QR
                : AttendanceRecordSource.SHORT_CODE;

        // Pré-contrôle de confort (message clair) ; la contrainte SQL
        // reste l'autorité en cas de concurrence.
        if (recordRepository.existsByAttendanceCheckpointIdAndEnrollmentId(
                session.checkpointInternalId(), enrollment.internalId())) {
            throw new AttendanceException(AttendanceException.Kind.ALREADY_RECORDED);
        }

        AttendanceRecord record = new AttendanceRecord(session.checkpointInternalId(), enrollment.internalId(),
                account.internalId(), clock.instant(), source);
        AttendanceRecord saved;
        try {
            saved = recordPersister.persist(record);
        } catch (DataIntegrityViolationException violation) {
            if (AttendanceRecordPersister.isDuplicateAttendanceViolation(violation)) {
                throw new AttendanceException(AttendanceException.Kind.ALREADY_RECORDED);
            }
            throw violation;
        }

        Long actorId = changePublisher.actorId(callerSubject);
        changePublisher.publishRecorded(saved.getPublicId(), actorId,
                "session=" + sessionPublicId + ";source=" + source.name());
        return new AttendanceRecordResponse(saved.getPublicId(), sessionPublicId, session.title(),
                saved.getRecordedAt(), source);
    }

    // ------------------------------------------------------------------
    // Consultation des présences d'une séance
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    SessionAttendanceResponse listForSession(String sessionPublicId, String callerSubject) {
        CourseSessionDirectory.SessionAccess access = courseSessionDirectory.resolve(
                parseUuid(sessionPublicId), AccessLevel.READ);
        CourseSessionDirectory.SessionRef session = switch (access.access()) {
            case NOT_FOUND -> throw new AttendanceException(AttendanceException.Kind.SESSION_NOT_FOUND);
            case FORBIDDEN -> throw new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN);
            case GRANTED -> access.session();
        };

        List<SessionAttendanceResponse.Row> rows = recordRepository
                .findByAttendanceCheckpointIdOrderByRecordedAtAsc(session.checkpointInternalId()).stream()
                .map(record -> {
                    EnrollmentDirectory.AttendeeRef attendee = enrollmentDirectory
                            .describeAttendee(record.getEnrollmentId()).orElse(null);
                    return new SessionAttendanceResponse.Row(
                            attendee != null ? attendee.studentProfilePublicId() : null,
                            attendee != null ? attendee.enrollmentPublicId() : null,
                            attendee != null ? attendee.studentNumber() : null,
                            attendee != null ? attendee.firstName() : null,
                            attendee != null ? attendee.lastName() : null,
                            record.getRecordedAt(),
                            record.getSource());
                })
                .toList();

        long expected = enrollmentDirectory.countActiveEnrollmentsInClasses(session.classGroupPublicIds());
        return new SessionAttendanceResponse(session.publicId(), session.checkpointPublicId(),
                expected, rows.size(), rows);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private CourseSessionDirectory.SessionRef requireManageable(String sessionPublicId) {
        CourseSessionDirectory.SessionAccess access = courseSessionDirectory.resolve(
                parseUuid(sessionPublicId), AccessLevel.MANAGE);
        return switch (access.access()) {
            case NOT_FOUND -> throw new AttendanceException(AttendanceException.Kind.SESSION_NOT_FOUND);
            case FORBIDDEN -> throw new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN);
            case GRANTED -> access.session();
        };
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Normalise un code court : majuscules, sans espaces ni séparateurs. */
    private static String normalizeShortCode(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static UUID parseSubject(String subject) {
        return Optional.ofNullable(subject)
                .flatMap(AttendanceService::tryUuid)
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN));
    }

    private static UUID parseUuid(String value) {
        return tryUuid(value)
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.SESSION_NOT_FOUND));
    }

    private static Optional<UUID> tryUuid(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }
}
