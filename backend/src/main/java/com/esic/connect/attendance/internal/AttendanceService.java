package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttendanceStatus;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory.AccessLevel;
import com.esic.connect.coursesession.CourseSessionDirectory.CheckpointRef;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.esic.connect.identity.UserDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Cœur de l'émargement : émission d'un jeton (délégué à
 * {@link AttendanceTokenService}), validation d'une présence et
 * consultation des présences d'une séance.
 *
 * <p>V10 : le jeton est émis <em>pour un point de contrôle</em> ; la
 * validation classe la présence {@code PRESENT} ou {@code LATE} selon le
 * seuil {@code app.attendance.late-threshold} (calcul serveur, horloge
 * injectée) ; la consultation détaille les présences <em>par point de
 * contrôle</em>.
 *
 * <p>{@link #validate} n'est volontairement pas {@code @Transactional} :
 * l'écriture est isolée dans {@link AttendanceRecordPersister}
 * ({@code REQUIRES_NEW}) pour retraduire une violation de contrainte
 * concurrente en 409, jamais en 500. Le serveur détermine l'apprenant
 * émargeur à partir du seul JWT.
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
    private final Duration lateThreshold;

    AttendanceService(AttendanceTokenService tokenService,
                      AttendanceRecordRepository recordRepository,
                      AttendanceRecordPersister recordPersister,
                      CourseSessionDirectory courseSessionDirectory,
                      EnrollmentDirectory enrollmentDirectory,
                      UserDirectory userDirectory,
                      AttendanceChangePublisher changePublisher,
                      Clock clock,
                      @Value("${app.attendance.late-threshold:PT10M}") Duration lateThreshold) {
        if (lateThreshold == null || lateThreshold.isNegative()) {
            throw new IllegalStateException(
                    "app.attendance.late-threshold doit être une durée non négative.");
        }
        this.tokenService = tokenService;
        this.recordRepository = recordRepository;
        this.recordPersister = recordPersister;
        this.courseSessionDirectory = courseSessionDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.userDirectory = userDirectory;
        this.changePublisher = changePublisher;
        this.clock = clock;
        this.lateThreshold = lateThreshold;
    }

    // ------------------------------------------------------------------
    // Émission d'un jeton (formateur / gestionnaire)
    // ------------------------------------------------------------------

    AttendanceTokenResponse issueToken(String sessionPublicId, String checkpointPublicId, String callerSubject) {
        CourseSessionDirectory.SessionRef session = requireManageable(sessionPublicId);
        CheckpointRef checkpoint;
        if (checkpointPublicId != null && !checkpointPublicId.isBlank()) {
            checkpoint = session.checkpoint(parseUuid(checkpointPublicId,
                            AttendanceException.Kind.CHECKPOINT_NOT_FOUND))
                    .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.CHECKPOINT_NOT_FOUND));
        } else {
            checkpoint = session.firstOpenCheckpoint()
                    .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.SESSION_CLOSED));
        }
        if (!checkpoint.isOpen()) {
            throw new AttendanceException(AttendanceException.Kind.SESSION_CLOSED);
        }
        IssuedAttendanceToken issued = tokenService.issue(session.publicId(), checkpoint.publicId());
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

        ResolvedAttendanceToken resolved = tokenService.resolve(token, shortCode)
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.TOKEN_INVALID));

        CourseSessionDirectory.SessionRef session = courseSessionDirectory
                .findForAttendance(resolved.sessionPublicId())
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.TOKEN_INVALID));
        CheckpointRef checkpoint = session.checkpoint(resolved.checkpointPublicId())
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.TOKEN_INVALID));
        if (!checkpoint.isOpen()) {
            throw new AttendanceException(AttendanceException.Kind.SESSION_CLOSED);
        }

        UUID callerPublicId = parseSubject(callerSubject);
        UserDirectory.UserRef account = userDirectory.findByPublicId(callerPublicId)
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN));
        if (account.archived()) {
            throw new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN);
        }

        // Couverture de l'inscription évaluée à la <em>date civile de la
        // séance</em> (startsAt projeté dans son fuseau IANA persisté), et
        // non à « aujourd'hui » : un émargement porte sur le jour de la
        // séance, qui peut différer de la date courante — et, dans la
        // fenêtre où la date locale diffère de la date UTC, « aujourd'hui en
        // UTC » écartait à tort une inscription qui vient d'être créée pour
        // le jour local. Même convention que AttendanceManagementService et
        // AttendanceReportService.
        LocalDate sessionDate = sessionLocalDate(session);
        List<EnrollmentDirectory.EnrollmentRef> matching = enrollmentDirectory
                .findActiveEnrollmentsForUserOn(callerPublicId, sessionDate).stream()
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

        Instant now = clock.instant();
        Duration delay = Duration.between(session.startsAt(), now);
        AttendanceStatus status = AttendanceStatus.PRESENT;
        Integer lateMinutes = null;
        if (delay.compareTo(lateThreshold) > 0) {
            status = AttendanceStatus.LATE;
            lateMinutes = (int) Math.min(Integer.MAX_VALUE, Math.max(0, (delay.toSeconds() + 59) / 60));
        }

        if (recordRepository.existsByAttendanceCheckpointIdAndEnrollmentId(
                checkpoint.internalId(), enrollment.internalId())) {
            throw new AttendanceException(AttendanceException.Kind.ALREADY_RECORDED);
        }

        AttendanceRecord record = new AttendanceRecord(checkpoint.internalId(), enrollment.internalId(),
                account.internalId(), null, now, source, status, lateMinutes, null);
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
                "session=" + session.publicId() + ";checkpoint=" + checkpoint.publicId()
                        + ";source=" + source.name() + ";status=" + status.name());
        return new AttendanceRecordResponse(saved.getPublicId(), session.publicId(), checkpoint.publicId(),
                session.title(), status, lateMinutes, saved.getRecordedAt(), source);
    }

    // ------------------------------------------------------------------
    // Consultation des présences d'une séance
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    SessionAttendanceResponse listForSession(String sessionPublicId, String callerSubject) {
        CourseSessionDirectory.SessionAccess access = courseSessionDirectory.resolve(
                parseUuid(sessionPublicId, AttendanceException.Kind.SESSION_NOT_FOUND), AccessLevel.READ);
        CourseSessionDirectory.SessionRef session = switch (access.access()) {
            case NOT_FOUND -> throw new AttendanceException(AttendanceException.Kind.SESSION_NOT_FOUND);
            case FORBIDDEN -> throw new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN);
            case GRANTED -> access.session();
        };

        long expected = enrollmentDirectory.countActiveEnrollmentsInClasses(session.classGroupPublicIds());

        List<SessionAttendanceResponse.CheckpointAttendance> checkpoints = new ArrayList<>();
        for (CheckpointRef cp : session.checkpoints()) {
            List<SessionAttendanceResponse.Row> rows = recordRepository
                    .findByAttendanceCheckpointIdOrderByRecordedAtAsc(cp.internalId()).stream()
                    .map(this::toRow)
                    .toList();
            int present = 0;
            int late = 0;
            int absent = 0;
            int excused = 0;
            for (SessionAttendanceResponse.Row row : rows) {
                switch (row.status()) {
                    case PRESENT -> present++;
                    case LATE -> {
                        present++;
                        late++;
                    }
                    case ABSENT -> absent++;
                    case EXCUSED_ABSENCE -> excused++;
                    case CANCELLED -> {
                        // exclu des compteurs
                    }
                }
            }
            long accountedFor = present + absent + excused;
            int derivedAbsent = (int) Math.max(0, expected - accountedFor);
            checkpoints.add(new SessionAttendanceResponse.CheckpointAttendance(
                    cp.publicId(), cp.label(), cp.type(), cp.status(), cp.required(),
                    expected, present, late, absent, excused, derivedAbsent, rows));
        }

        SessionAttendanceResponse.CheckpointAttendance firstCp =
                checkpoints.isEmpty() ? null : checkpoints.get(0);
        return new SessionAttendanceResponse(
                session.publicId(),
                firstCp != null ? firstCp.checkpointPublicId() : null,
                firstCp != null ? firstCp.expectedCount() : expected,
                firstCp != null ? firstCp.presentCount() : 0,
                firstCp != null ? firstCp.records() : List.of(),
                checkpoints);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private SessionAttendanceResponse.Row toRow(AttendanceRecord record) {
        EnrollmentDirectory.AttendeeRef attendee = enrollmentDirectory
                .describeAttendee(record.getEnrollmentId()).orElse(null);
        return new SessionAttendanceResponse.Row(
                record.getPublicId(),
                attendee != null ? attendee.studentProfilePublicId() : null,
                attendee != null ? attendee.enrollmentPublicId() : null,
                attendee != null ? attendee.studentNumber() : null,
                attendee != null ? attendee.firstName() : null,
                attendee != null ? attendee.lastName() : null,
                record.getStatus(),
                record.getLateMinutes(),
                record.getComment(),
                record.getRecordedAt(),
                record.getSource());
    }

    private CourseSessionDirectory.SessionRef requireManageable(String sessionPublicId) {
        CourseSessionDirectory.SessionAccess access = courseSessionDirectory.resolve(
                parseUuid(sessionPublicId, AttendanceException.Kind.SESSION_NOT_FOUND), AccessLevel.MANAGE);
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

    private static String normalizeShortCode(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static UUID parseSubject(String subject) {
        return Optional.ofNullable(subject)
                .flatMap(AttendanceService::tryUuid)
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN));
    }

    private static UUID parseUuid(String value, AttendanceException.Kind kind) {
        return tryUuid(value).orElseThrow(() -> new AttendanceException(kind));
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

    /**
     * Date civile <em>de la séance</em> : {@code startsAt} projeté dans le
     * fuseau IANA persisté de la séance. Un fuseau persisté invalide est un
     * état interne corrompu (validé à l'écriture par
     * {@code CourseSessionService}) : erreur interne contrôlée plutôt qu'un
     * repli silencieux sur UTC qui décalerait la date. Même convention que
     * {@code AttendanceManagementService.sessionLocalDate} et
     * {@code AttendanceReportService.persistedZone} — la valeur invalide
     * n'est jamais exposée.
     */
    private static LocalDate sessionLocalDate(CourseSessionDirectory.SessionRef session) {
        return session.startsAt().atZone(persistedZone(session.timeZoneId())).toLocalDate();
    }

    private static ZoneId persistedZone(String id) {
        try {
            return ZoneId.of(id);
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Fuseau horaire persisté invalide pour une séance");
        }
    }
}
