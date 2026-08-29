package com.esic.connect.alternation.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.alternation.AlternationChangeAction;
import com.esic.connect.alternation.AlternationResourceType;
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
 * Affectations historisées d'un rythme à une classe (docs/04 §14.2).
 *
 * <p>Une classe peut changer de rythme au fil du temps : l'ancienne
 * affectation est clôturée ({@code CLOSED}, {@code valid_until}
 * renseigné), jamais supprimée ; une nouvelle affectation ne remplace
 * pas la précédente. Invariants :
 * <ul>
 *   <li>{@code valid_until >= valid_from} (borne inclusive) ;</li>
 *   <li>aucun chevauchement de périodes ACTIVE pour une même classe —
 *       deux périodes strictement adjacentes sont autorisées
 *       (pré-contrôle {@link ClassWorkStudyPatternRepository#findActiveOverlapping}) ;</li>
 *   <li>au plus une affectation ACTIVE « ouverte » par classe — garanti
 *       aussi par la contrainte SQL {@code uq_class_work_study_pattern_active_open},
 *       une course concurrente étant retraduite en 409 par
 *       {@link ClassAssignmentPersister}.</li>
 * </ul>
 *
 * <p>Le {@code PEDAGOGICAL_MANAGER} est limité à son périmètre via le
 * port {@link AcademicScopeDirectory} (décision prise dans {@code academic},
 * jamais d'après un paramètre client).
 */
@Service
class ClassWorkStudyPatternService {

    private static final Set<String> SORTABLE = Set.of("validFrom", "validUntil", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "validFrom");
    /** Sentinelle « +infini » pour une affectation ouverte (compatible DATE MySQL). */
    private static final LocalDate OPEN_END = LocalDate.of(9999, 12, 31);

    private final ClassWorkStudyPatternRepository assignmentRepository;
    private final WorkStudyPatternService patternService;
    private final ClassAssignmentPersister persister;
    private final ClassGroupDirectory classGroupDirectory;
    private final AcademicScopeDirectory academicScope;
    private final AlternationChangePublisher changePublisher;
    private final Clock clock;

    ClassWorkStudyPatternService(ClassWorkStudyPatternRepository assignmentRepository,
                                 WorkStudyPatternService patternService,
                                 ClassAssignmentPersister persister,
                                 ClassGroupDirectory classGroupDirectory,
                                 AcademicScopeDirectory academicScope,
                                 AlternationChangePublisher changePublisher,
                                 Clock clock) {
        this.assignmentRepository = assignmentRepository;
        this.patternService = patternService;
        this.persister = persister;
        this.classGroupDirectory = classGroupDirectory;
        this.academicScope = academicScope;
        this.changePublisher = changePublisher;
        this.clock = clock;
    }

    /**
     * Non transactionnel : les lectures sont en transactions implicites,
     * l'insertion est isolée dans {@link ClassAssignmentPersister}
     * ({@code REQUIRES_NEW}). La retraduction d'une collision se fait donc
     * hors de toute transaction en échec.
     */
    ClassAssignmentResponse assign(ClassAssignmentRequests.Assign request, String callerSubject) {
        WorkStudyPattern pattern = patternService.require(parseUuid(request.workStudyPatternPublicId(),
                AlternationException.Kind.PATTERN_NOT_FOUND));
        if (pattern.isArchived()) {
            throw new AlternationException(AlternationException.Kind.PATTERN_ARCHIVED);
        }
        ClassGroupDirectory.ClassGroupRef classRef = requireClass(request.classGroupPublicId());
        requireInScope(classRef.publicId());
        if (!classRef.openForEnrollment()) {
            throw new AlternationException(AlternationException.Kind.CLASS_NOT_ASSIGNABLE);
        }

        LocalDate validFrom = request.validFrom();
        LocalDate validUntil = request.validUntil();
        if (validUntil != null && validUntil.isBefore(validFrom)) {
            throw new AlternationException(AlternationException.Kind.INVALID_PERIOD);
        }
        LocalDate rangeEnd = validUntil != null ? validUntil : OPEN_END;
        if (!assignmentRepository.findActiveOverlapping(classRef.internalId(), validFrom, rangeEnd).isEmpty()) {
            throw new AlternationException(AlternationException.Kind.ASSIGNMENT_OVERLAP);
        }

        Long actorId = changePublisher.actorId(callerSubject);
        ClassWorkStudyPattern assignment = new ClassWorkStudyPattern(classRef.internalId(), pattern,
                request.cycleStartDate(), validFrom, validUntil);
        assignment.markCreatedBy(actorId);

        ClassWorkStudyPattern saved;
        try {
            saved = persister.persist(assignment);
        } catch (DataIntegrityViolationException collision) {
            if (ClassAssignmentPersister.isOpenAssignmentUniqueViolation(collision)) {
                throw new AlternationException(AlternationException.Kind.OPEN_ASSIGNMENT_EXISTS);
            }
            throw collision;
        }
        changePublisher.publish(AlternationResourceType.CLASS_WORK_STUDY_PATTERN, saved.getPublicId(),
                AlternationChangeAction.ASSIGNED, actorId, detail(classRef, pattern));
        return ClassAssignmentResponse.from(saved, classRef);
    }

    @Transactional
    void close(UUID publicId, ClassAssignmentRequests.Close request, String callerSubject) {
        ClassWorkStudyPattern assignment = require(publicId);
        if (assignment.isClosed()) {
            throw new AlternationException(AlternationException.Kind.ASSIGNMENT_ALREADY_CLOSED);
        }
        requireInScope(classRefOf(assignment.getClassGroupId()).publicId());
        LocalDate effective = request.effectiveDate() != null ? request.effectiveDate() : LocalDate.now(clock);
        if (effective.isBefore(assignment.getValidFrom())) {
            throw new AlternationException(AlternationException.Kind.INVALID_PERIOD);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        assignment.close(request.reason().trim(), actorId, effective);
        ClassGroupDirectory.ClassGroupRef classRef = classRefOf(assignment.getClassGroupId());
        changePublisher.publish(AlternationResourceType.CLASS_WORK_STUDY_PATTERN, assignment.getPublicId(),
                AlternationChangeAction.CLOSED, actorId, detail(classRef, assignment.getPattern()));
    }

    @Transactional(readOnly = true)
    ClassAssignmentResponse get(UUID publicId) {
        ClassWorkStudyPattern assignment = require(publicId);
        ClassGroupDirectory.ClassGroupRef classRef = classRefOf(assignment.getClassGroupId());
        requireInScope(classRef.publicId());
        return ClassAssignmentResponse.from(assignment, classRef);
    }

    @Transactional(readOnly = true)
    PageResponse<ClassAssignmentResponse> list(String classGroupPublicId, String statusFilter,
                                               int page, int size, String sort) {
        Pageable pageable = AlternationQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        List<Specification<ClassWorkStudyPattern>> specs = new ArrayList<>();

        Optional<Set<Long>> visible = academicScope.visibleClassGroupIds();
        if (visible.isPresent()) {
            if (visible.get().isEmpty()) {
                return PageResponse.of(Page.<ClassWorkStudyPattern>empty(pageable), a -> null);
            }
            specs.add(AlternationSpecifications.assignmentClassGroupIn(visible.get()));
        }
        if (classGroupPublicId != null && !classGroupPublicId.isBlank()) {
            ClassGroupDirectory.ClassGroupRef classRef = requireClass(classGroupPublicId);
            requireInScope(classRef.publicId());
            specs.add(AlternationSpecifications.assignmentHasClassGroup(classRef.internalId()));
        }
        parseStatus(statusFilter).ifPresent(status -> specs.add(AlternationSpecifications.assignmentHasStatus(status)));

        Page<ClassWorkStudyPattern> result = assignmentRepository.findAll(Specification.allOf(specs), pageable);
        Map<Long, ClassGroupDirectory.ClassGroupRef> refs = new HashMap<>();
        return PageResponse.of(result, assignment -> ClassAssignmentResponse.from(assignment,
                refs.computeIfAbsent(assignment.getClassGroupId(), this::classRefOrNull)));
    }

    @Transactional(readOnly = true)
    PageResponse<ClassAssignmentResponse> listByClass(String classGroupPublicId, String statusFilter,
                                                      int page, int size, String sort) {
        ClassGroupDirectory.ClassGroupRef classRef = requireClass(classGroupPublicId);
        requireInScope(classRef.publicId());
        Pageable pageable = AlternationQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        List<Specification<ClassWorkStudyPattern>> specs = new ArrayList<>();
        specs.add(AlternationSpecifications.assignmentHasClassGroup(classRef.internalId()));
        parseStatus(statusFilter).ifPresent(status -> specs.add(AlternationSpecifications.assignmentHasStatus(status)));
        Page<ClassWorkStudyPattern> result = assignmentRepository.findAll(Specification.allOf(specs), pageable);
        return PageResponse.of(result, assignment -> ClassAssignmentResponse.from(assignment, classRef));
    }

    // ------------------------------------------------------------------

    ClassWorkStudyPattern require(UUID publicId) {
        return assignmentRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AlternationException(
                        AlternationException.Kind.CLASS_ASSIGNMENT_NOT_FOUND));
    }

    private void requireInScope(UUID classGroupPublicId) {
        if (!academicScope.hasGlobalScope() && !academicScope.isClassInScope(classGroupPublicId)) {
            throw new AlternationException(AlternationException.Kind.OUT_OF_SCOPE);
        }
    }

    private ClassGroupDirectory.ClassGroupRef requireClass(String classGroupPublicId) {
        return classGroupDirectory.findByPublicId(parseUuid(classGroupPublicId,
                        AlternationException.Kind.CLASS_GROUP_NOT_FOUND))
                .orElseThrow(() -> new AlternationException(AlternationException.Kind.CLASS_GROUP_NOT_FOUND));
    }

    private ClassGroupDirectory.ClassGroupRef classRefOf(long classGroupInternalId) {
        return classGroupDirectory.findByInternalId(classGroupInternalId)
                .orElseThrow(() -> new AlternationException(AlternationException.Kind.CLASS_GROUP_NOT_FOUND));
    }

    private ClassGroupDirectory.ClassGroupRef classRefOrNull(long classGroupInternalId) {
        return classGroupDirectory.findByInternalId(classGroupInternalId).orElse(null);
    }

    private static String detail(ClassGroupDirectory.ClassGroupRef classRef, WorkStudyPattern pattern) {
        return "class=" + classRef.code() + ";pattern=" + pattern.getCode();
    }

    private static Optional<ClassPatternStatus> parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ClassPatternStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException invalid) {
            throw new AlternationException(AlternationException.Kind.INVALID_FILTER);
        }
    }

    private static UUID parseUuid(String value, AlternationException.Kind kind) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException | NullPointerException notAUuid) {
            throw new AlternationException(kind);
        }
    }
}
