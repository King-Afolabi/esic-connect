package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttendanceChangeAction;
import com.esic.connect.attendance.AttendanceStatus;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory.AccessLevel;
import com.esic.connect.coursesession.CourseSessionDirectory.CheckpointRef;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.esic.connect.identity.UserDirectory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Présence manuelle, correction et annulation logique d'une présence
 * (V10). Toute action manuelle exige un motif et ajoute une ligne
 * <strong>append-only</strong> dans {@code attendance_correction}
 * (docs/04 §19.4). Aucune suppression physique.
 *
 * <p>Le contrôle d'accès réutilise
 * {@link CourseSessionDirectory#resolve} (périmètre décidé côté
 * {@code coursesession}). {@code EXCUSED_ABSENCE} n'est jamais saisi
 * directement : il résulte de l'acceptation d'un justificatif.
 */
@Service
class AttendanceManagementService {

    private final CourseSessionDirectory courseSessionDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final UserDirectory userDirectory;
    private final AttendanceRecordRepository recordRepository;
    private final AttendanceRecordPersister recordPersister;
    private final AttendanceCorrectionRepository correctionRepository;
    private final AttendanceChangePublisher changePublisher;
    private final Clock clock;

    AttendanceManagementService(CourseSessionDirectory courseSessionDirectory,
                                EnrollmentDirectory enrollmentDirectory,
                                UserDirectory userDirectory,
                                AttendanceRecordRepository recordRepository,
                                AttendanceRecordPersister recordPersister,
                                AttendanceCorrectionRepository correctionRepository,
                                AttendanceChangePublisher changePublisher,
                                Clock clock) {
        this.courseSessionDirectory = courseSessionDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.userDirectory = userDirectory;
        this.recordRepository = recordRepository;
        this.recordPersister = recordPersister;
        this.correctionRepository = correctionRepository;
        this.changePublisher = changePublisher;
        this.clock = clock;
    }

    AttendanceRecordResponse recordManual(String sessionPublicId, AttendanceManagementRequests.ManualRecord request,
                                          String callerSubject) {
        CourseSessionDirectory.SessionRef session = requireSession(sessionPublicId, AccessLevel.MANAGE);
        CheckpointRef checkpoint = requireCheckpoint(session, request.checkpointPublicId());

        EnrollmentDirectory.EnrollmentRef enrollment = enrollmentDirectory
                .findByPublicId(parseUuid(request.enrollmentPublicId(), AttendanceException.Kind.NOT_ENROLLED))
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.NOT_ENROLLED));
        if (!session.classGroupPublicIds().contains(enrollment.classGroupPublicId()) || !enrollment.usable()) {
            throw new AttendanceException(AttendanceException.Kind.NOT_ENROLLED);
        }
        long studentUserId = userDirectory.findByPublicId(enrollment.studentUserPublicId())
                .map(UserDirectory.UserRef::internalId)
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.NOT_ENROLLED));

        AttendanceStatus status = parseManualStatus(request.status());
        String comment = requireReason(request.comment(), AttendanceException.Kind.MANUAL_REASON_REQUIRED);
        Integer lateMinutes = status == AttendanceStatus.LATE ? request.lateMinutes() : null;
        Long actorId = changePublisher.actorId(callerSubject);
        Instant now = clock.instant();

        if (recordRepository.existsByAttendanceCheckpointIdAndEnrollmentId(
                checkpoint.internalId(), enrollment.internalId())) {
            throw new AttendanceException(AttendanceException.Kind.ALREADY_RECORDED);
        }
        AttendanceRecord record = new AttendanceRecord(checkpoint.internalId(), enrollment.internalId(),
                studentUserId, actorId, now, AttendanceRecordSource.MANUAL, status, lateMinutes, comment);
        AttendanceRecord saved;
        try {
            saved = recordPersister.persist(record);
        } catch (DataIntegrityViolationException violation) {
            if (AttendanceRecordPersister.isDuplicateAttendanceViolation(violation)) {
                throw new AttendanceException(AttendanceException.Kind.ALREADY_RECORDED);
            }
            throw violation;
        }
        correctionRepository.save(AttendanceCorrection.created(saved.getId(), status, lateMinutes, comment,
                comment, actorId, now));
        changePublisher.publishRecord(saved.getPublicId(), actorId, AttendanceChangeAction.MANUAL_RECORDED,
                "session=" + session.publicId() + ";checkpoint=" + checkpoint.publicId()
                        + ";status=" + status.name());
        return new AttendanceRecordResponse(saved.getPublicId(), session.publicId(), checkpoint.publicId(),
                session.title(), status, lateMinutes, saved.getRecordedAt(), AttendanceRecordSource.MANUAL);
    }

    @Transactional
    AttendanceRecordResponse correct(String sessionPublicId, String attendancePublicId,
                                     AttendanceManagementRequests.Correct request, String callerSubject) {
        CourseSessionDirectory.SessionRef session = requireSession(sessionPublicId, AccessLevel.MANAGE);
        AttendanceRecord record = requireRecord(session, attendancePublicId);
        if (record.isCancelled()) {
            throw new AttendanceException(AttendanceException.Kind.RECORD_INVALID_STATE);
        }
        String reason = requireReason(request.reason(), AttendanceException.Kind.CORRECTION_REASON_REQUIRED);
        if (request.status() == null && request.lateMinutes() == null && request.comment() == null) {
            throw new AttendanceException(AttendanceException.Kind.CORRECTION_REASON_REQUIRED);
        }

        AttendanceStatus previousStatus = record.getStatus();
        Integer previousLate = record.getLateMinutes();
        String previousComment = record.getComment();

        AttendanceStatus newStatus = request.status() != null
                ? parseManualStatus(request.status())
                : previousStatus;
        Integer newLate;
        if (request.lateMinutes() != null) {
            newLate = request.lateMinutes();
        } else if (newStatus == AttendanceStatus.LATE) {
            newLate = previousLate;
        } else {
            newLate = null;
        }
        String newComment = request.comment() != null ? request.comment().trim() : previousComment;

        Instant now = clock.instant();
        Long actorId = changePublisher.actorId(callerSubject);
        record.applyCorrection(newStatus, newLate, newComment, now, actorId);
        try {
            recordRepository.saveAndFlush(record);
        } catch (ObjectOptimisticLockingFailureException concurrent) {
            throw new AttendanceException(AttendanceException.Kind.RECORD_INVALID_STATE);
        }
        correctionRepository.save(AttendanceCorrection.statusCorrected(record.getId(), previousStatus, newStatus,
                previousLate, newLate, previousComment, newComment, reason, actorId, now));
        changePublisher.publishRecord(record.getPublicId(), actorId, AttendanceChangeAction.CORRECTED,
                "session=" + session.publicId() + ";from=" + previousStatus.name() + ";to=" + newStatus.name());
        return toResponse(session, record);
    }

    @Transactional
    AttendanceRecordResponse cancel(String sessionPublicId, String attendancePublicId,
                                    AttendanceManagementRequests.Cancel request, String callerSubject) {
        CourseSessionDirectory.SessionRef session = requireSession(sessionPublicId, AccessLevel.MANAGE);
        AttendanceRecord record = requireRecord(session, attendancePublicId);
        if (record.isCancelled()) {
            throw new AttendanceException(AttendanceException.Kind.RECORD_INVALID_STATE);
        }
        String reason = requireReason(request.reason(), AttendanceException.Kind.CORRECTION_REASON_REQUIRED);
        AttendanceStatus previousStatus = record.getStatus();
        Instant now = clock.instant();
        Long actorId = changePublisher.actorId(callerSubject);
        record.applyCorrection(AttendanceStatus.CANCELLED, null, record.getComment(), now, actorId);
        try {
            recordRepository.saveAndFlush(record);
        } catch (ObjectOptimisticLockingFailureException concurrent) {
            throw new AttendanceException(AttendanceException.Kind.RECORD_INVALID_STATE);
        }
        correctionRepository.save(AttendanceCorrection.cancelled(record.getId(), previousStatus, reason,
                actorId, now));
        changePublisher.publishRecord(record.getPublicId(), actorId, AttendanceChangeAction.CANCELLED,
                "session=" + session.publicId() + ";from=" + previousStatus.name());
        return toResponse(session, record);
    }

    @Transactional(readOnly = true)
    List<AttendanceCorrectionResponse> history(String sessionPublicId, String attendancePublicId,
                                               String callerSubject) {
        CourseSessionDirectory.SessionRef session = requireSession(sessionPublicId, AccessLevel.READ);
        AttendanceRecord record = requireRecord(session, attendancePublicId);
        return correctionRepository.findByAttendanceRecordIdOrderByOccurredAtAscIdAsc(record.getId()).stream()
                .map(AttendanceCorrectionResponse::from)
                .toList();
    }

    /**
     * Candidats à une saisie manuelle : effectif nominatif {@code ACTIVE}
     * des classes de la séance, dédupliqué par inscription
     * (correctif PR #22 §2). Le contrôle fin est celui de la
     * <em>consultation</em> ({@code AccessLevel.READ}) : jamais élargi par
     * un paramètre client ; aucun apprenant d'une classe extérieure.
     */
    @Transactional(readOnly = true)
    List<AttendanceCandidateResponse> candidates(String sessionPublicId, String callerSubject) {
        CourseSessionDirectory.SessionRef session = requireSession(sessionPublicId, AccessLevel.READ);
        java.util.LinkedHashMap<UUID, AttendanceCandidateResponse> byEnrollment = new java.util.LinkedHashMap<>();
        for (EnrollmentDirectory.RosterEntry entry
                : enrollmentDirectory.findActiveRosterForClasses(session.classGroupPublicIds())) {
            if (entry.enrollmentPublicId() == null) {
                continue;
            }
            byEnrollment.putIfAbsent(entry.enrollmentPublicId(), new AttendanceCandidateResponse(
                    entry.studentProfilePublicId(), entry.enrollmentPublicId(), entry.studentNumber(),
                    entry.firstName(), entry.lastName(), entry.classGroupCode()));
        }
        Comparator<AttendanceCandidateResponse> byName = Comparator
                .comparing((AttendanceCandidateResponse c) -> nullSafe(c.lastName()),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(c -> nullSafe(c.firstName()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(c -> c.enrollmentPublicId().toString());
        return byEnrollment.values().stream().sorted(byName).toList();
    }

    /** Contenu et nom de fichier d'un export CSV de séance (correctif PR #22 §8). */
    record SessionCsv(String fileName, String content) {
    }

    /**
     * Export CSV des présences d'une séance. Réutilise le contrôle fin de
     * la consultation ({@code AccessLevel.READ}) — un formateur affecté
     * exporte sa séance. Aucune donnée superflue, aucun identifiant SQL,
     * aucune adresse électronique ; nom de fichier contrôlé.
     */
    @Transactional(readOnly = true)
    SessionCsv exportSessionCsv(String sessionPublicId, String callerSubject) {
        CourseSessionDirectory.SessionRef session = requireSession(sessionPublicId, AccessLevel.READ);
        List<List<String>> body = new java.util.ArrayList<>();
        for (CheckpointRef cp : session.checkpoints()) {
            for (AttendanceRecord record
                    : recordRepository.findByAttendanceCheckpointIdOrderByRecordedAtAsc(cp.internalId())) {
                EnrollmentDirectory.AttendeeRef attendee = enrollmentDirectory
                        .describeAttendee(record.getEnrollmentId()).orElse(null);
                body.add(List.of(
                        nullSafe(cp.label()),
                        attendee != null ? nullSafe(attendee.studentNumber()) : "",
                        attendee != null ? nullSafe(attendee.firstName()) : "",
                        attendee != null ? nullSafe(attendee.lastName()) : "",
                        record.getStatus() != null ? record.getStatus().name() : "",
                        record.getLateMinutes() != null ? record.getLateMinutes().toString() : "",
                        nullSafe(record.getComment()),
                        record.getRecordedAt() != null ? record.getRecordedAt().toString() : "",
                        record.getSource() != null ? record.getSource().name() : ""));
            }
        }
        String content = AttendanceCsvWriter.write(List.of(
                "point_de_controle", "numero_etudiant", "prenom", "nom", "statut", "retard_minutes",
                "commentaire", "enregistre_le", "canal"), body);
        String fileName = "attendance-session_" + session.publicId() + ".csv";
        return new SessionCsv(fileName, content);
    }

    // ------------------------------------------------------------------

    private AttendanceRecordResponse toResponse(CourseSessionDirectory.SessionRef session, AttendanceRecord record) {
        UUID checkpointPublicId = session.checkpoints().stream()
                .filter(cp -> cp.internalId() == record.getAttendanceCheckpointId())
                .map(CheckpointRef::publicId).findFirst().orElse(null);
        return new AttendanceRecordResponse(record.getPublicId(), session.publicId(), checkpointPublicId,
                session.title(), record.getStatus(), record.getLateMinutes(), record.getRecordedAt(),
                record.getSource());
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

    private CheckpointRef requireCheckpoint(CourseSessionDirectory.SessionRef session, String checkpointPublicId) {
        CheckpointRef checkpoint = session.checkpoint(
                        parseUuid(checkpointPublicId, AttendanceException.Kind.CHECKPOINT_NOT_FOUND))
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.CHECKPOINT_NOT_FOUND));
        if (checkpoint.status() == com.esic.connect.coursesession.AttendanceCheckpointStatus.CANCELLED) {
            throw new AttendanceException(AttendanceException.Kind.RECORD_INVALID_STATE);
        }
        return checkpoint;
    }

    private AttendanceRecord requireRecord(CourseSessionDirectory.SessionRef session, String attendancePublicId) {
        AttendanceRecord record = recordRepository
                .findByPublicId(parseUuid(attendancePublicId, AttendanceException.Kind.RECORD_NOT_FOUND))
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.RECORD_NOT_FOUND));
        boolean inSession = session.checkpoints().stream()
                .anyMatch(cp -> cp.internalId() == record.getAttendanceCheckpointId());
        if (!inSession) {
            throw new AttendanceException(AttendanceException.Kind.RECORD_NOT_FOUND);
        }
        return record;
    }

    private static AttendanceStatus parseManualStatus(String value) {
        AttendanceStatus status;
        try {
            status = AttendanceStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new AttendanceException(AttendanceException.Kind.MANUAL_STATUS_INVALID);
        }
        if (status == AttendanceStatus.EXCUSED_ABSENCE || status == AttendanceStatus.CANCELLED) {
            throw new AttendanceException(AttendanceException.Kind.MANUAL_STATUS_INVALID);
        }
        return status;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String requireReason(String value, AttendanceException.Kind kind) {
        String trimmed = value != null ? value.trim() : "";
        if (trimmed.isEmpty()) {
            throw new AttendanceException(kind);
        }
        return trimmed;
    }

    private static UUID parseUuid(String value, AttendanceException.Kind kind) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException | NullPointerException notAUuid) {
            throw new AttendanceException(kind);
        }
    }
}
