package com.esic.connect.enrollment.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.enrollment.EnrollmentChangeAction;
import com.esic.connect.enrollment.EnrollmentResourceType;
import com.esic.connect.identity.UserDirectory;
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
 * <p>Une inscription rattache directement un <strong>compte</strong>
 * apprenant ({@code user_id}, résolu via {@link UserDirectory}) : il
 * n'existe plus de {@code student_profile} intermédiaire (refonte
 * 2026-09) : la cible d'une inscription doit exister, ne pas être
 * archivée et porter un rôle actif {@code STUDENT}
 * ({@code ENR_USER_NOT_ELIGIBLE} sinon).
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

    private static final String STUDENT_ROLE = "STUDENT";
    private static final Set<String> SORTABLE = Set.of("startDate", "endDate", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "startDate");

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentPersister persister;
    private final ClassGroupDirectory classGroupDirectory;
    private final UserDirectory userDirectory;
    private final EnrollmentChangePublisher changePublisher;
    private final RosterScopeResolver rosterScope;
    private final Clock clock;

    EnrollmentService(EnrollmentRepository enrollmentRepository,
                      EnrollmentPersister persister,
                      ClassGroupDirectory classGroupDirectory,
                      UserDirectory userDirectory,
                      EnrollmentChangePublisher changePublisher,
                      RosterScopeResolver rosterScope,
                      Clock clock) {
        this.enrollmentRepository = enrollmentRepository;
        this.persister = persister;
        this.classGroupDirectory = classGroupDirectory;
        this.userDirectory = userDirectory;
        this.changePublisher = changePublisher;
        this.rosterScope = rosterScope;
        this.clock = clock;
    }

    /**
     * Non transactionnel : voir la note de classe. L'insertion passe par
     * {@link EnrollmentPersister} ({@code REQUIRES_NEW}).
     */
    EnrollmentResponse enroll(EnrollmentRequests.Enroll request, String callerSubject) {
        UserDirectory.UserRef target = requireEligibleStudent(request.studentUserPublicId());
        ClassGroupDirectory.ClassGroupRef classRef = requireOpenClass(request.classGroupPublicId());

        LocalDate startDate = request.startDate() != null ? request.startDate() : LocalDate.now(clock);
        guardNoActiveEnrollment(target.internalId(), classRef.academicYearInternalId());

        Long actorId = changePublisher.actorId(callerSubject);
        Enrollment enrollment = new Enrollment(target.internalId(), classRef.internalId(),
                classRef.academicYearInternalId(), startDate, EnrollmentSource.MANUAL, null, null,
                Boolean.TRUE.equals(request.workStudy()), EnrollmentQuerySupport.trimToNull(request.companyName()));
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
        return toResponse(saved, target.publicId(), classRef, null);
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
            guardNoActiveEnrollment(current.getUserId(), targetRef.academicYearInternalId());
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
        // La situation d'alternance est reprise telle quelle depuis
        // l'inscription clôturée : un changement de classe n'est pas, en
        // soi, un changement d'alternance (celle-ci se corrige séparément,
        // ex. via l'import CSV — action UPDATE_PROFILE).
        Enrollment next = new Enrollment(current.getUserId(), targetRef.internalId(),
                targetRef.academicYearInternalId(), newStartDate, EnrollmentSource.CLASS_TRANSFER, reason,
                current.getId(), current.isWorkStudy(), current.getCompanyName());
        next.markCreatedBy(actorId);
        Enrollment saved = enrollmentRepository.saveAndFlush(next);

        changePublisher.publish(EnrollmentResourceType.ENROLLMENT, current.getPublicId(),
                EnrollmentChangeAction.TRANSFERRED, actorId, detail(classRefOf(current.getClassGroupId())));
        changePublisher.publish(EnrollmentResourceType.ENROLLMENT, saved.getPublicId(),
                EnrollmentChangeAction.CREATED, actorId, detail(targetRef));
        return toResponse(saved, resolveUserPublicId(saved.getUserId()), targetRef, current.getPublicId());
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
        return toResponse(enrollment, resolveUserPublicId(enrollment.getUserId()), classRef,
                resolvePreviousPublicId(enrollment));
    }

    @Transactional(readOnly = true)
    EnrollmentResponse get(UUID publicId, String callerSubject) {
        Enrollment enrollment = require(publicId);
        // Périmètre de consultation : un PEDAGOGICAL_MANAGER / TEACHER ne
        // voit que les inscriptions rattachées à l'une de ses classes.
        // Hors périmètre ⇒ 404 (cahier §18.2), pas 403.
        rosterScope.visibleClassGroupInternalIds(callerSubject).ifPresent(visible -> {
            if (!visible.contains(enrollment.getClassGroupId())) {
                throw new EnrollmentException(EnrollmentException.Kind.ENROLLMENT_NOT_FOUND);
            }
        });
        return toResponse(enrollment, resolveUserPublicId(enrollment.getUserId()),
                classRefOf(enrollment.getClassGroupId()), resolvePreviousPublicId(enrollment));
    }

    @Transactional(readOnly = true)
    PageResponse<EnrollmentResponse> list(String studentUserPublicId, String classGroupPublicId,
                                          String statusFilter, int page, int size, String sort,
                                          String callerSubject) {
        Pageable pageable = EnrollmentQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        List<Specification<Enrollment>> specs = new ArrayList<>();

        // Périmètre de consultation : restreint la liste aux inscriptions
        // des classes visibles par l'appelant (PEDAGOGICAL_MANAGER /
        // TEACHER). Accès global ⇒ aucun filtre ; périmètre vide ⇒ page
        // vide.
        Optional<java.util.Set<Long>> visibleClasses =
                rosterScope.visibleClassGroupInternalIds(callerSubject);
        if (visibleClasses.isPresent()) {
            if (visibleClasses.get().isEmpty()) {
                return PageResponse.of(Page.<Enrollment>empty(pageable), e -> null);
            }
            specs.add(EnrollmentSpecifications.enrollmentClassGroupIn(visibleClasses.get()));
        }

        if (studentUserPublicId != null && !studentUserPublicId.isBlank()) {
            Optional<UserDirectory.UserRef> user = userDirectory.findByPublicId(
                    parseUuid(studentUserPublicId, EnrollmentException.Kind.USER_NOT_ELIGIBLE));
            if (user.isEmpty()) {
                return PageResponse.of(Page.<Enrollment>empty(pageable), e -> null);
            }
            specs.add(EnrollmentSpecifications.enrollmentHasUser(user.get().internalId()));
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
        List<Enrollment> content = result.getContent();

        // Résolution en lot (anti-N+1, NFR-PERF-08) : identifiants de
        // compte, numéro étudiant facultatif et classes, chacun en une
        // requête pour toute la page plutôt qu'une par ligne.
        List<Long> userIds = content.stream().map(Enrollment::getUserId).toList();
        Map<Long, UserDirectory.NamedUserRef> userRefs = userDirectory.findNamedRefs(userIds);
        Map<Long, String> studentNumbers = userDirectory.findStudentNumbers(userIds);
        Map<Long, ClassGroupDirectory.ClassGroupRef> classRefs = new HashMap<>();

        return PageResponse.of(result, enrollment -> {
            UserDirectory.NamedUserRef userRef = userRefs.get(enrollment.getUserId());
            return EnrollmentResponse.from(enrollment, userRef != null ? userRef.publicId() : null,
                    studentNumbers.get(enrollment.getUserId()),
                    classRefs.computeIfAbsent(enrollment.getClassGroupId(), this::classRefOf),
                    resolvePreviousPublicId(enrollment));
        });
    }

    // ------------------------------------------------------------------

    /**
     * Construit la réponse d'une inscription unitaire (création,
     * transfert, clôture, détail) : résout le numéro étudiant facultatif
     * du compte — une requête de plus, sans conséquence hors d'une boucle
     * de page.
     */
    private EnrollmentResponse toResponse(Enrollment enrollment, UUID studentUserPublicId,
                                          ClassGroupDirectory.ClassGroupRef classRef, UUID previousPublicId) {
        String studentNumber = userDirectory.findStudentNumbers(List.of(enrollment.getUserId()))
                .get(enrollment.getUserId());
        return EnrollmentResponse.from(enrollment, studentUserPublicId, studentNumber, classRef, previousPublicId);
    }

    /**
     * Valide la cible d'une inscription : le compte doit exister, ne pas
     * être archivé et porter un rôle actif {@code STUDENT}
     * ({@code ENR_USER_NOT_ELIGIBLE} sinon).
     */
    private UserDirectory.UserRef requireEligibleStudent(String rawUserPublicId) {
        UserDirectory.UserRef target = userDirectory.findByPublicId(
                        parseUuid(rawUserPublicId, EnrollmentException.Kind.USER_NOT_ELIGIBLE))
                .orElseThrow(() -> new EnrollmentException(EnrollmentException.Kind.USER_NOT_ELIGIBLE));
        if (target.archived() || !target.activeRoles().contains(STUDENT_ROLE)) {
            throw new EnrollmentException(EnrollmentException.Kind.USER_NOT_ELIGIBLE);
        }
        return target;
    }

    private UUID resolveUserPublicId(Long userInternalId) {
        return userDirectory.findByInternalId(userInternalId)
                .map(UserDirectory.UserRef::publicId)
                .orElseThrow(() -> new EnrollmentException(EnrollmentException.Kind.USER_NOT_ELIGIBLE));
    }

    private void guardNoActiveEnrollment(Long userId, long academicYearId) {
        if (enrollmentRepository.existsByUserIdAndAcademicYearIdAndStatus(
                userId, academicYearId, EnrollmentStatus.ACTIVE)) {
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
