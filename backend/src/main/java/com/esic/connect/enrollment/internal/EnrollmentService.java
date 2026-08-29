package com.esic.connect.enrollment.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.enrollment.EnrollmentChangeAction;
import com.esic.connect.enrollment.EnrollmentResourceType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Inscriptions historiques (docs/04-modele-donnees.md §13 ; RG-012,
 * RG-023 ; AC-006). Gestion réservée à
 * {@code ADMIN}/{@code SUPER_ADMIN}/{@code SCHOOL_ADMINISTRATION}.
 *
 * <p>Règle centrale : un apprenant possède au maximum une inscription
 * {@code ACTIVE} par année scolaire (docs/04 §13.3) — pré-contrôle
 * applicatif renvoyant {@code ENR_ACTIVE_ENROLLMENT_EXISTS}, doublé par la
 * contrainte SQL {@code uq_enrollment_active_per_year} (colonnes
 * générées).
 *
 * <p>Frontières transactionnelles et courses concurrentes :
 * <ul>
 *   <li>{@link #enroll} n'est pas transactionnel ; l'insertion passe par
 *       {@link EnrollmentPersister} ({@code REQUIRES_NEW}). Une collision
 *       sur {@code uq_enrollment_active_per_year} est reçue <em>hors</em>
 *       de toute transaction en échec et retraduite en 409 sur place ;
 *       toute autre violation d'intégrité est relancée telle quelle.</li>
 *   <li>{@link #transfer} est transactionnel : la clôture de l'ancienne
 *       inscription (UPDATE) et la création de la nouvelle (INSERT) sont
 *       atomiques, et l'INSERT doit voir, dans la même transaction, le
 *       créneau d'unicité libéré par la clôture. Il ne peut donc pas
 *       utiliser {@link EnrollmentPersister} ni capter la collision
 *       localement (la transaction serait déjà rollback-only) : une
 *       course résiduelle est retraduite par
 *       {@link EnrollmentExceptionHandler}, après l'annulation faite par
 *       le proxy, en 409 ciblé sur cette seule contrainte.</li>
 * </ul>
 *
 * <p>Un changement de classe ({@link #transfer}) clôture l'inscription
 * courante en {@code TRANSFERRED} ({@code end_date} = date effective,
 * borne inclusive) et crée une nouvelle inscription {@code ACTIVE}
 * débutant le lendemain ({@code effectiveDate.plusDays(1)}) — aucun
 * chevauchement de période — avec {@code previous_enrollment_id}. Aucune
 * ligne n'est jamais supprimée (docs/04 §13.2, §13.4) : l'ancienne
 * inscription reste consultable (AC-006).
 */
@Service
class EnrollmentService {

    private static final Set<String> SORTABLE = Set.of("startDate", "endDate", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "startDate");

    private final EnrollmentRepository enrollmentRepository;
    private final StudentProfileRepository profileRepository;
    private final EnrollmentPersister persister;
    private final ClassGroupDirectory classGroupDirectory;
    private final EnrollmentChangePublisher changePublisher;
    private final Clock clock;

    EnrollmentService(EnrollmentRepository enrollmentRepository,
                      StudentProfileRepository profileRepository,
                      EnrollmentPersister persister,
                      ClassGroupDirectory classGroupDirectory,
                      EnrollmentChangePublisher changePublisher,
                      Clock clock) {
        this.enrollmentRepository = enrollmentRepository;
        this.profileRepository = profileRepository;
        this.persister = persister;
        this.classGroupDirectory = classGroupDirectory;
        this.changePublisher = changePublisher;
        this.clock = clock;
    }

    /**
     * Non transactionnel : voir la note de classe. L'insertion passe par
     * {@link EnrollmentPersister} ({@code REQUIRES_NEW}).
     */
    EnrollmentResponse enroll(EnrollmentRequests.Enroll request, String callerSubject) {
        StudentProfile profile = requireProfile(parseUuid(request.studentProfilePublicId(),
                EnrollmentException.Kind.STUDENT_PROFILE_NOT_FOUND));
        if (profile.isArchived()) {
            throw new EnrollmentException(EnrollmentException.Kind.STUDENT_PROFILE_ARCHIVED);
        }
        ClassGroupDirectory.ClassGroupRef classRef = requireOpenClass(request.classGroupPublicId());

        LocalDate startDate = request.startDate() != null ? request.startDate() : LocalDate.now(clock);
        guardNoActiveEnrollment(profile.getId(), classRef.academicYearInternalId());

        Long actorId = changePublisher.actorId(callerSubject);
        Enrollment enrollment = new Enrollment(profile, classRef.internalId(), classRef.academicYearInternalId(),
                startDate, EnrollmentSource.MANUAL, null, null);
        enrollment.markCreatedBy(actorId);

        Enrollment saved;
        try {
            saved = persister.persist(enrollment);
        } catch (DataIntegrityViolationException collision) {
            if (EnrollmentPersistence.isActiveEnrollmentUniqueViolation(collision)) {
                throw new EnrollmentException(EnrollmentException.Kind.ACTIVE_ENROLLMENT_EXISTS);
            }
            throw collision;
        }

        changePublisher.publish(EnrollmentResourceType.ENROLLMENT, saved.getPublicId(),
                EnrollmentChangeAction.CREATED, actorId, detail(classRef));
        return EnrollmentResponse.from(saved, classRef, null);
    }

    @Transactional
    EnrollmentResponse transfer(UUID publicId, EnrollmentRequests.Transfer request, String callerSubject) {
        Enrollment current = require(publicId);
        if (!current.isActive()) {
            throw new EnrollmentException(EnrollmentException.Kind.ENROLLMENT_NOT_ACTIVE);
        }
        ClassGroupDirectory.ClassGroupRef targetRef = requireOpenClass(request.classGroupPublicId());
        if (targetRef.internalId() == current.getClassGroupId()) {
            throw new EnrollmentException(EnrollmentException.Kind.SAME_CLASS);
        }

        LocalDate effectiveDate = request.effectiveDate() != null ? request.effectiveDate() : LocalDate.now(clock);
        if (effectiveDate.isBefore(current.getStartDate())) {
            throw new EnrollmentException(EnrollmentException.Kind.DATE_INVALID);
        }
        String reason = request.reason().trim();

        // Vers une autre année : l'inscription courante ne libère pas ce
        // créneau-là ; contrôle explicite avant écriture.
        if (targetRef.academicYearInternalId() != current.getAcademicYearId()) {
            guardNoActiveEnrollment(current.getStudentProfile().getId(), targetRef.academicYearInternalId());
        }

        Long actorId = changePublisher.actorId(callerSubject);
        current.close(EnrollmentStatus.TRANSFERRED, reason, effectiveDate, actorId);
        // Flush de l'UPDATE d'abord : les colonnes générées de l'ancienne
        // inscription passent à NULL et libèrent le créneau (apprenant,
        // même année) avant l'INSERT suivant.
        enrollmentRepository.saveAndFlush(current);

        // `end_date` est une borne inclusive (dernier jour dans l'ancienne
        // classe) : la nouvelle inscription débute le lendemain, sans
        // chevauchement de période (docs/04 §13.2 ne fixe pas de valeur
        // de `start_date` ; la non-superposition découle des bornes
        // inclusives et de l'unicité d'une inscription active — §13.3).
        LocalDate newStartDate = effectiveDate.plusDays(1);
        Enrollment next = new Enrollment(current.getStudentProfile(), targetRef.internalId(),
                targetRef.academicYearInternalId(), newStartDate, EnrollmentSource.CLASS_TRANSFER, reason,
                current.getId());
        next.markCreatedBy(actorId);
        Enrollment saved = enrollmentRepository.saveAndFlush(next);

        changePublisher.publish(EnrollmentResourceType.ENROLLMENT, current.getPublicId(),
                EnrollmentChangeAction.TRANSFERRED, actorId, detail(classRefOf(current.getClassGroupId())));
        changePublisher.publish(EnrollmentResourceType.ENROLLMENT, saved.getPublicId(),
                EnrollmentChangeAction.CREATED, actorId, detail(targetRef));
        return EnrollmentResponse.from(saved, targetRef, current.getPublicId());
    }

    @Transactional
    EnrollmentResponse close(UUID publicId, EnrollmentRequests.Close request, String callerSubject) {
        Enrollment enrollment = require(publicId);
        if (!enrollment.isActive()) {
            throw new EnrollmentException(EnrollmentException.Kind.ENROLLMENT_NOT_ACTIVE);
        }
        EnrollmentStatus newStatus = parseCloseStatus(request.status());
        LocalDate effectiveDate = request.effectiveDate() != null ? request.effectiveDate() : LocalDate.now(clock);
        if (effectiveDate.isBefore(enrollment.getStartDate())) {
            throw new EnrollmentException(EnrollmentException.Kind.DATE_INVALID);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        enrollment.close(newStatus, request.reason().trim(), effectiveDate, actorId);

        ClassGroupDirectory.ClassGroupRef classRef = classRefOf(enrollment.getClassGroupId());
        changePublisher.publish(EnrollmentResourceType.ENROLLMENT, enrollment.getPublicId(),
                EnrollmentChangeAction.CLOSED, actorId,
                "class=" + classRef.code() + ";status=" + newStatus.name());
        return EnrollmentResponse.from(enrollment, classRef, resolvePreviousPublicId(enrollment));
    }

    @Transactional(readOnly = true)
    EnrollmentResponse get(UUID publicId) {
        Enrollment enrollment = require(publicId);
        return EnrollmentResponse.from(enrollment, classRefOf(enrollment.getClassGroupId()),
                resolvePreviousPublicId(enrollment));
    }

    @Transactional(readOnly = true)
    PageResponse<EnrollmentResponse> list(String studentProfilePublicId, String classGroupPublicId,
                                          String statusFilter, int page, int size, String sort) {
        Pageable pageable = EnrollmentQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        List<Specification<Enrollment>> specs = new ArrayList<>();

        if (studentProfilePublicId != null && !studentProfilePublicId.isBlank()) {
            Optional<StudentProfile> profile = profileRepository.findByPublicId(parseUuid(studentProfilePublicId,
                    EnrollmentException.Kind.STUDENT_PROFILE_NOT_FOUND));
            if (profile.isEmpty()) {
                return PageResponse.of(Page.<Enrollment>empty(pageable), e -> null);
            }
            specs.add(EnrollmentSpecifications.enrollmentHasStudentProfile(profile.get().getId()));
        }
        if (classGroupPublicId != null && !classGroupPublicId.isBlank()) {
            Optional<ClassGroupDirectory.ClassGroupRef> classRef = classGroupDirectory.findByPublicId(
                    parseUuid(classGroupPublicId, EnrollmentException.Kind.CLASS_GROUP_NOT_FOUND));
            if (classRef.isEmpty()) {
                return PageResponse.of(Page.<Enrollment>empty(pageable), e -> null);
            }
            specs.add(EnrollmentSpecifications.enrollmentHasClassGroup(classRef.get().internalId()));
        }
        parseStatus(statusFilter).ifPresent(status -> specs.add(EnrollmentSpecifications.enrollmentHasStatus(status)));

        Page<Enrollment> result = enrollmentRepository.findAll(Specification.allOf(specs), pageable);
        Map<Long, ClassGroupDirectory.ClassGroupRef> classRefs = new HashMap<>();
        return PageResponse.of(result, enrollment -> EnrollmentResponse.from(enrollment,
                classRefs.computeIfAbsent(enrollment.getClassGroupId(), this::classRefOf),
                resolvePreviousPublicId(enrollment)));
    }

    // ------------------------------------------------------------------

    private void guardNoActiveEnrollment(Long studentProfileId, long academicYearId) {
        if (enrollmentRepository.existsByStudentProfileIdAndAcademicYearIdAndStatus(
                studentProfileId, academicYearId, EnrollmentStatus.ACTIVE)) {
            throw new EnrollmentException(EnrollmentException.Kind.ACTIVE_ENROLLMENT_EXISTS);
        }
    }

    private ClassGroupDirectory.ClassGroupRef requireOpenClass(String classGroupPublicId) {
        ClassGroupDirectory.ClassGroupRef ref = classGroupDirectory.findByPublicId(parseUuid(classGroupPublicId,
                        EnrollmentException.Kind.CLASS_GROUP_NOT_FOUND))
                .orElseThrow(() -> new EnrollmentException(EnrollmentException.Kind.CLASS_GROUP_NOT_FOUND));
        if (!ref.openForEnrollment()) {
            throw new EnrollmentException(EnrollmentException.Kind.ARCHIVED_PARENT);
        }
        return ref;
    }

    private ClassGroupDirectory.ClassGroupRef classRefOf(long classGroupInternalId) {
        return classGroupDirectory.findByInternalId(classGroupInternalId)
                .orElseThrow(() -> new EnrollmentException(EnrollmentException.Kind.CLASS_GROUP_NOT_FOUND));
    }

    private UUID resolvePreviousPublicId(Enrollment enrollment) {
        Long previousId = enrollment.getPreviousEnrollmentId();
        if (previousId == null) {
            return null;
        }
        return enrollmentRepository.findById(previousId).map(Enrollment::getPublicId).orElse(null);
    }

    private Enrollment require(UUID publicId) {
        return enrollmentRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EnrollmentException(EnrollmentException.Kind.ENROLLMENT_NOT_FOUND));
    }

    private StudentProfile requireProfile(UUID publicId) {
        return profileRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EnrollmentException(EnrollmentException.Kind.STUDENT_PROFILE_NOT_FOUND));
    }

    private static String detail(ClassGroupDirectory.ClassGroupRef classRef) {
        return "class=" + classRef.code() + ";year=" + classRef.academicYearCode();
    }

    private static EnrollmentStatus parseCloseStatus(String value) {
        try {
            EnrollmentStatus status = EnrollmentStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (status == EnrollmentStatus.COMPLETED || status == EnrollmentStatus.WITHDRAWN) {
                return status;
            }
        } catch (IllegalArgumentException ignored) {
            // tombe sur l'exception métier ci-dessous
        }
        throw new EnrollmentException(EnrollmentException.Kind.INVALID_CLOSE_STATUS);
    }

    private static Optional<EnrollmentStatus> parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(EnrollmentStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException invalid) {
            throw new EnrollmentException(EnrollmentException.Kind.INVALID_FILTER);
        }
    }

    private static UUID parseUuid(String value, EnrollmentException.Kind kind) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException notAUuid) {
            throw new EnrollmentException(kind);
        }
    }
}
