package com.esic.connect.alternation.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.alternation.AlternationChangeAction;
import com.esic.connect.alternation.AlternationResourceType;
import com.esic.connect.enrollment.EnrollmentDirectory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Exceptions individuelles de calendrier (docs/04 §14.3, docs/02 §8.3).
 *
 * <p>Une exception est rattachée à une inscription {@code ACTIVE}. Aucune
 * suppression physique : l'annulation ({@code CANCELLED}) conserve
 * l'historique. Règle de chevauchement — minimale et testée : deux
 * exceptions <em>ACTIVE de même type</em> ne peuvent pas se recouper pour
 * une même inscription ; des types différents peuvent coexister sur la
 * même période (ex. {@code REMOTE_ALLOWED} sur une semaine et
 * {@code ON_SITE_REQUIRED} un jour de cette semaine).
 *
 * <p>Le {@code PEDAGOGICAL_MANAGER} n'accède qu'aux exceptions dont la
 * classe de l'inscription relève de son périmètre — vérifié via
 * {@link EnrollmentDirectory} (classe de l'inscription) puis
 * {@link AcademicScopeDirectory} (décision de périmètre dans
 * {@code academic}). Ce lot ne calcule aucune assiduité.
 */
@Service
class StudentScheduleExceptionService {

    private static final Set<String> SORTABLE = Set.of("startAt", "endAt", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "startAt");

    private final StudentScheduleExceptionRepository exceptionRepository;
    private final EnrollmentDirectory enrollmentDirectory;
    private final AcademicScopeDirectory academicScope;
    private final AlternationChangePublisher changePublisher;

    StudentScheduleExceptionService(StudentScheduleExceptionRepository exceptionRepository,
                                    EnrollmentDirectory enrollmentDirectory,
                                    AcademicScopeDirectory academicScope,
                                    AlternationChangePublisher changePublisher) {
        this.exceptionRepository = exceptionRepository;
        this.enrollmentDirectory = enrollmentDirectory;
        this.academicScope = academicScope;
        this.changePublisher = changePublisher;
    }

    @Transactional
    StudentExceptionResponse create(StudentExceptionRequests.Create request, String callerSubject) {
        EnrollmentDirectory.EnrollmentRef enrollment = requireEnrollment(parseUuid(request.enrollmentPublicId(),
                AlternationException.Kind.ENROLLMENT_NOT_FOUND));
        if (!enrollment.usable()) {
            throw new AlternationException(AlternationException.Kind.ENROLLMENT_NOT_USABLE);
        }
        requireInScope(enrollment.classGroupPublicId());

        ScheduleExceptionType type = requireType(request.type());
        Instant startAt = request.startAt();
        Instant endAt = request.endAt();
        if (!endAt.isAfter(startAt)) {
            throw new AlternationException(AlternationException.Kind.INVALID_PERIOD);
        }
        String timeZoneId = requireZone(request.timeZoneId());

        boolean overlapSameType = exceptionRepository
                .findActiveOverlapping(enrollment.internalId(), startAt, endAt).stream()
                .anyMatch(existing -> existing.getExceptionType() == type);
        if (overlapSameType) {
            throw new AlternationException(AlternationException.Kind.EXCEPTION_OVERLAP);
        }

        Long actorId = changePublisher.actorId(callerSubject);
        StudentScheduleException exception = new StudentScheduleException(enrollment.internalId(), type,
                startAt, endAt, timeZoneId, request.reason().trim());
        exception.markCreatedBy(actorId);
        StudentScheduleException saved = exceptionRepository.save(exception);

        changePublisher.publish(AlternationResourceType.STUDENT_SCHEDULE_EXCEPTION, saved.getPublicId(),
                AlternationChangeAction.CREATED, actorId, detail(enrollment, type));
        return StudentExceptionResponse.from(saved, enrollment);
    }

    @Transactional
    void cancel(UUID publicId, StudentExceptionRequests.Cancel request, String callerSubject) {
        StudentScheduleException exception = require(publicId);
        if (exception.isCancelled()) {
            throw new AlternationException(AlternationException.Kind.EXCEPTION_ALREADY_CANCELLED);
        }
        EnrollmentDirectory.EnrollmentRef enrollment = enrollmentDirectory
                .findByInternalId(exception.getEnrollmentId()).orElse(null);
        requireInScope(enrollment != null ? enrollment.classGroupPublicId() : null);

        Long actorId = changePublisher.actorId(callerSubject);
        exception.cancel(request.reason().trim(), actorId);
        changePublisher.publish(AlternationResourceType.STUDENT_SCHEDULE_EXCEPTION, exception.getPublicId(),
                AlternationChangeAction.CANCELLED, actorId,
                enrollment != null ? detail(enrollment, exception.getExceptionType())
                        : "type=" + exception.getExceptionType().name());
    }

    @Transactional(readOnly = true)
    StudentExceptionResponse get(UUID publicId) {
        StudentScheduleException exception = require(publicId);
        EnrollmentDirectory.EnrollmentRef enrollment = enrollmentDirectory
                .findByInternalId(exception.getEnrollmentId()).orElse(null);
        requireInScope(enrollment != null ? enrollment.classGroupPublicId() : null);
        return StudentExceptionResponse.from(exception, enrollment);
    }

    @Transactional(readOnly = true)
    PageResponse<StudentExceptionResponse> listByEnrollment(String enrollmentPublicId, int page, int size,
                                                            String sort) {
        EnrollmentDirectory.EnrollmentRef enrollment = requireEnrollment(parseUuid(enrollmentPublicId,
                AlternationException.Kind.ENROLLMENT_NOT_FOUND));
        requireInScope(enrollment.classGroupPublicId());
        Pageable pageable = AlternationQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        Page<StudentScheduleException> result = exceptionRepository.findByEnrollmentId(enrollment.internalId(),
                pageable);
        return PageResponse.of(result, exception -> StudentExceptionResponse.from(exception, enrollment));
    }

    // ------------------------------------------------------------------

    private StudentScheduleException require(UUID publicId) {
        return exceptionRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AlternationException(AlternationException.Kind.EXCEPTION_NOT_FOUND));
    }

    private EnrollmentDirectory.EnrollmentRef requireEnrollment(UUID publicId) {
        return enrollmentDirectory.findByPublicId(publicId)
                .orElseThrow(() -> new AlternationException(AlternationException.Kind.ENROLLMENT_NOT_FOUND));
    }

    private void requireInScope(UUID classGroupPublicId) {
        if (!academicScope.hasGlobalScope() && !academicScope.isClassInScope(classGroupPublicId)) {
            throw new AlternationException(AlternationException.Kind.OUT_OF_SCOPE);
        }
    }

    private static String detail(EnrollmentDirectory.EnrollmentRef enrollment, ScheduleExceptionType type) {
        String classCode = enrollment.classGroupCode() != null ? enrollment.classGroupCode() : "?";
        return "class=" + classCode + ";type=" + type.name();
    }

    private static ScheduleExceptionType requireType(String value) {
        try {
            return ScheduleExceptionType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new AlternationException(AlternationException.Kind.INVALID_EXCEPTION_TYPE);
        }
    }

    private static String requireZone(String value) {
        try {
            return ZoneId.of(value.trim()).getId();
        } catch (RuntimeException invalid) {
            throw new AlternationException(AlternationException.Kind.INVALID_TIME_ZONE);
        }
    }

    private static UUID parseUuid(String value, AlternationException.Kind kind) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException | NullPointerException notAUuid) {
            throw new AlternationException(kind);
        }
    }

    // Exposé pour la résolution effective (AlternationContextService).
    List<StudentScheduleException> activeExceptionsCovering(long enrollmentInternalId, Instant from, Instant to) {
        return exceptionRepository.findActiveOverlapping(enrollmentInternalId, from, to);
    }
}
