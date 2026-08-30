package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttendanceStatus;
import com.esic.connect.coursesession.AttendanceCheckpointStatus;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory.CheckpointRef;
import com.esic.connect.coursesession.CourseSessionDirectory.SessionRef;
import com.esic.connect.enrollment.EnrollmentDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Espace « Mes présences » de l'apprenant (V10). Le serveur résout
 * l'apprenant à partir du <strong>seul JWT</strong> — jamais d'un
 * identifiant fourni par le client. Renvoie, par point de contrôle
 * attendu, la présence réelle ou une absence <em>dérivée</em> (effectif
 * attendu moins présences) sans persister quoi que ce soit.
 */
@Service
class StudentAttendanceService {

    private final CourseSessionDirectory courseSessionDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final AttendanceRecordRepository recordRepository;
    private final AttendanceJustificationRepository justificationRepository;
    private final AttendanceCorrectionRepository correctionRepository;

    StudentAttendanceService(CourseSessionDirectory courseSessionDirectory,
                             EnrollmentDirectory enrollmentDirectory,
                             AttendanceRecordRepository recordRepository,
                             AttendanceJustificationRepository justificationRepository,
                             AttendanceCorrectionRepository correctionRepository) {
        this.courseSessionDirectory = courseSessionDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.recordRepository = recordRepository;
        this.justificationRepository = justificationRepository;
        this.correctionRepository = correctionRepository;
    }

    @Transactional(readOnly = true)
    PageResponse<MyAttendanceRow> listOwn(String studentSubject, Instant from, Instant to,
                                          String statusFilter, int page, int size) {
        String wantedStatus = statusFilter == null || statusFilter.isBlank()
                ? null : statusFilter.trim().toUpperCase(Locale.ROOT);

        List<MyAttendanceRow> rows = buildRows(studentSubject, from, to).stream()
                .filter(row -> wantedStatus == null || wantedStatus.equals(row.status()))
                .sorted((a, b) -> b.sessionStartsAt().compareTo(a.sessionStartsAt()))
                .toList();
        return PageResponse.ofList(rows, page, size);
    }

    @Transactional(readOnly = true)
    MyAttendanceDetail getOwn(String studentSubject, String attendancePublicId) {
        UUID target = parseUuid(attendancePublicId);
        MyAttendanceRow row = buildRows(studentSubject, null, null).stream()
                .filter(r -> target.equals(r.attendancePublicId()))
                .findFirst()
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.RECORD_NOT_FOUND));
        AttendanceRecord record = recordRepository.findByPublicId(target).orElseThrow();
        List<AttendanceCorrectionResponse> history = correctionRepository
                .findByAttendanceRecordIdOrderByOccurredAtAscIdAsc(record.getId()).stream()
                .map(AttendanceCorrectionResponse::from)
                .toList();
        JustificationResponse justification = justificationRepository
                .findByAttendanceRecordIdOrderBySubmittedAtDesc(record.getId()).stream().findFirst()
                .map(j -> lightJustification(j, row))
                .orElse(null);
        return new MyAttendanceDetail(row, history, justification);
    }

    // ------------------------------------------------------------------

    private List<MyAttendanceRow> buildRows(String studentSubject, Instant from, Instant to) {
        UUID userPublicId = parseUuid(studentSubject);
        List<EnrollmentDirectory.EnrollmentRef> enrollments =
                enrollmentDirectory.findEnrollmentsForUser(userPublicId);
        if (enrollments.isEmpty()) {
            return List.of();
        }
        Map<UUID, EnrollmentDirectory.EnrollmentRef> byClass = new HashMap<>();
        for (EnrollmentDirectory.EnrollmentRef e : enrollments) {
            if (e.classGroupPublicId() != null) {
                byClass.putIfAbsent(e.classGroupPublicId(), e);
            }
        }
        Set<Long> enrollmentIds = enrollments.stream()
                .map(EnrollmentDirectory.EnrollmentRef::internalId)
                .collect(Collectors.toUnmodifiableSet());

        List<SessionRef> sessions = courseSessionDirectory.findSessionsForClasses(byClass.keySet(), from, to);
        if (sessions.isEmpty()) {
            return List.of();
        }

        Set<Long> checkpointIds = sessions.stream()
                .flatMap(s -> s.checkpoints().stream())
                .map(CheckpointRef::internalId)
                .collect(Collectors.toUnmodifiableSet());
        Map<String, AttendanceRecord> recordByKey = recordRepository
                .findByAttendanceCheckpointIdInAndEnrollmentIdIn(checkpointIds, enrollmentIds).stream()
                .collect(Collectors.toMap(
                        r -> r.getAttendanceCheckpointId() + ":" + r.getEnrollmentId(), r -> r, (a, b) -> a));
        Map<Long, AttendanceJustification> justificationByRecord = new HashMap<>();
        for (AttendanceRecord r : recordByKey.values()) {
            justificationRepository.findByAttendanceRecordIdOrderBySubmittedAtDesc(r.getId()).stream().findFirst()
                    .ifPresent(j -> justificationByRecord.put(r.getId(), j));
        }

        List<MyAttendanceRow> rows = new ArrayList<>();
        for (SessionRef session : sessions) {
            EnrollmentDirectory.EnrollmentRef enrollment = session.classGroupPublicIds().stream()
                    .map(byClass::get)
                    .filter(java.util.Objects::nonNull)
                    .findFirst().orElse(null);
            if (enrollment == null) {
                continue;
            }
            for (CheckpointRef cp : session.checkpoints()) {
                if (cp.status() == AttendanceCheckpointStatus.CANCELLED) {
                    continue;
                }
                AttendanceRecord record = recordByKey.get(cp.internalId() + ":" + enrollment.internalId());
                rows.add(toRow(session, cp, enrollment,
                        record, record != null ? justificationByRecord.get(record.getId()) : null));
            }
        }
        return rows;
    }

    private MyAttendanceRow toRow(SessionRef session, CheckpointRef cp,
                                  EnrollmentDirectory.EnrollmentRef enrollment,
                                  AttendanceRecord record, AttendanceJustification justification) {
        String status;
        Integer lateMinutes = null;
        String comment = null;
        Instant recordedAt = null;
        UUID attendancePublicId = null;
        if (record != null) {
            status = record.getStatus().name();
            lateMinutes = record.getLateMinutes();
            comment = record.getComment();
            recordedAt = record.getRecordedAt();
            attendancePublicId = record.getPublicId();
        } else if (cp.status() == AttendanceCheckpointStatus.CLOSED) {
            status = AttendanceStatus.ABSENT.name();
        } else {
            status = cp.status().name(); // PLANNED | OPEN
        }
        boolean derivedAbsent = record == null && cp.status() == AttendanceCheckpointStatus.CLOSED;
        boolean realAbsent = record != null && record.getStatus() == AttendanceStatus.ABSENT;
        boolean hasActiveJustification = justification != null
                && justification.getStatus() != JustificationStatus.REJECTED;
        boolean canJustify = (derivedAbsent || realAbsent) && !hasActiveJustification;
        return new MyAttendanceRow(
                attendancePublicId, session.publicId(), session.title(), session.startsAt(),
                cp.publicId(), cp.label(), cp.type(), cp.required(), enrollment.classGroupCode(),
                status, lateMinutes, comment, recordedAt,
                justification != null ? justification.getPublicId() : null,
                justification != null ? justification.getStatus().name() : null,
                canJustify);
    }

    private JustificationResponse lightJustification(AttendanceJustification j, MyAttendanceRow row) {
        return new JustificationResponse(
                j.getPublicId(), j.getStatus().name(), j.getCategory().name(), j.getExternalReference(),
                j.getComment(), j.getSubmittedAt(), j.getReviewedAt(), j.getDecisionReason(),
                row.sessionPublicId(), row.sessionTitle(), row.sessionStartsAt(),
                row.checkpointPublicId(), row.checkpointLabel(), row.classCode(),
                null, null, null, null, row.status());
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(String.valueOf(value).trim());
        } catch (IllegalArgumentException | NullPointerException notAUuid) {
            throw new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN);
        }
    }
}
