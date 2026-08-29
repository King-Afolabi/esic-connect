package com.esic.connect.academic.internal;

import com.esic.connect.academic.AcademicChangeAction;
import com.esic.connect.academic.AcademicResourceType;
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
 * Affectations de responsable pédagogique (RG-004, RG-010, RG-011).
 * Gestion réservée à {@code ADMIN}/{@code SUPER_ADMIN} : consultation,
 * création et clôture. Aucune modification en place, aucune suppression.
 *
 * <p>La cible d'une affectation doit exister, ne pas être archivée et
 * porter un rôle actif {@code PEDAGOGICAL_MANAGER} (sinon
 * {@code ACAD_TARGET_NOT_ELIGIBLE}), vérifié via le port
 * {@link UserDirectory}.
 *
 * <p>Un seul {@link PedagogicalAssignmentRole#PRIMARY_MANAGER} actif par
 * formation. Le pré-contrôle renvoie {@code ACAD_PRIMARY_MANAGER_EXISTS}.
 * En cas de course entre deux créations, l'insertion — isolée dans
 * {@link AssignmentPersister} ({@code REQUIRES_NEW}) — échoue sur la
 * contrainte {@code uq_pedagogical_assignment_active_primary} ;
 * l'exception est reçue <em>hors</em> de la transaction d'insertion et
 * n'est retraduite en 409 que si c'est bien cette contrainte qui a été
 * violée. Toute autre violation d'intégrité (FK, {@code CHECK},
 * {@code NOT NULL}, longueur, unicité de {@code public_id}...) est
 * relancée telle quelle.
 */
@Service
class PedagogicalAssignmentService {

    private static final String ACTIVE_PRIMARY_CONSTRAINT = "uq_pedagogical_assignment_active_primary";
    private static final String PEDAGOGICAL_MANAGER_ROLE = "PEDAGOGICAL_MANAGER";
    private static final Set<String> SORTABLE = Set.of("validFrom", "validUntil", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "validFrom");

    private final PedagogicalAssignmentRepository assignmentRepository;
    private final ProgramRepository programRepository;
    private final UserDirectory userDirectory;
    private final AssignmentPersister assignmentPersister;
    private final AcademicChangePublisher changePublisher;
    private final Clock clock;

    PedagogicalAssignmentService(PedagogicalAssignmentRepository assignmentRepository,
                                 ProgramRepository programRepository,
                                 UserDirectory userDirectory,
                                 AssignmentPersister assignmentPersister,
                                 AcademicChangePublisher changePublisher,
                                 Clock clock) {
        this.assignmentRepository = assignmentRepository;
        this.programRepository = programRepository;
        this.userDirectory = userDirectory;
        this.assignmentPersister = assignmentPersister;
        this.changePublisher = changePublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    PageResponse<PedagogicalAssignmentResponse> list(String programPublicId, String userPublicId, String typeFilter,
                                                     String statusFilter, LocalDate activeOn,
                                                     int page, int size, String sort) {
        Pageable pageable = AcademicQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        List<Specification<PedagogicalAssignment>> specs = new ArrayList<>();
        if (programPublicId != null && !programPublicId.isBlank()) {
            specs.add(AcademicSpecifications.assignmentHasProgram(requireProgram(parseUuid(programPublicId,
                    AcademicException.Kind.PROGRAM_NOT_FOUND)).getId()));
        }
        if (userPublicId != null && !userPublicId.isBlank()) {
            UserDirectory.UserRef user = userDirectory.findByPublicId(parseUuid(userPublicId,
                            AcademicException.Kind.ASSIGNMENT_TARGET_NOT_ELIGIBLE))
                    .orElseThrow(() -> new AcademicException(AcademicException.Kind.ASSIGNMENT_TARGET_NOT_ELIGIBLE));
            specs.add(AcademicSpecifications.assignmentHasManager(user.internalId()));
        }
        parseType(typeFilter).ifPresent(type -> specs.add(AcademicSpecifications.assignmentHasType(type)));
        parseStatus(statusFilter).ifPresent(status -> specs.add(AcademicSpecifications.assignmentHasStatus(status)));
        if (activeOn != null) {
            specs.add(AcademicSpecifications.assignmentActiveOn(activeOn));
        }
        Page<PedagogicalAssignment> result = assignmentRepository.findAll(Specification.allOf(specs), pageable);

        Map<Long, UUID> userPublicIds = new HashMap<>();
        return PageResponse.of(result, assignment -> PedagogicalAssignmentResponse.from(assignment,
                userPublicIds.computeIfAbsent(assignment.getManagerUserId(), this::resolveUserPublicId)));
    }

    @Transactional(readOnly = true)
    PedagogicalAssignmentResponse get(UUID publicId) {
        PedagogicalAssignment assignment = require(publicId);
        return PedagogicalAssignmentResponse.from(assignment,
                resolveUserPublicId(assignment.getManagerUserId()));
    }

    /**
     * Non transactionnel : les lectures utilisent des transactions
     * implicites, l'insertion est déléguée à {@link AssignmentPersister}
     * ({@code REQUIRES_NEW}) et l'audit passe par un événement traité dans
     * sa propre transaction. La retraduction de la collision se fait donc
     * hors de toute transaction en échec.
     */
    PedagogicalAssignmentResponse create(PedagogicalAssignmentRequests.Create request, String callerSubject) {
        Program program = requireProgram(parseUuid(request.programPublicId(),
                AcademicException.Kind.PROGRAM_NOT_FOUND));
        if (program.isArchived()) {
            throw new AcademicException(AcademicException.Kind.ARCHIVED_PARENT);
        }
        PedagogicalAssignmentRole type = requireType(request.type());
        UserDirectory.UserRef target = userDirectory.findByPublicId(parseUuid(request.userPublicId(),
                        AcademicException.Kind.ASSIGNMENT_TARGET_NOT_ELIGIBLE))
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.ASSIGNMENT_TARGET_NOT_ELIGIBLE));
        if (target.archived() || !target.activeRoles().contains(PEDAGOGICAL_MANAGER_ROLE)) {
            throw new AcademicException(AcademicException.Kind.ASSIGNMENT_TARGET_NOT_ELIGIBLE);
        }

        LocalDate validFrom = request.validFrom() != null ? request.validFrom() : LocalDate.now(clock);
        LocalDate validUntil = request.validUntil();
        if (validUntil != null && validUntil.isBefore(validFrom)) {
            throw new AcademicException(AcademicException.Kind.ASSIGNMENT_DATE_INVALID);
        }
        if (type == PedagogicalAssignmentRole.PRIMARY_MANAGER
                && assignmentRepository.existsByProgramIdAndAssignmentRoleAndStatus(program.getId(),
                        PedagogicalAssignmentRole.PRIMARY_MANAGER, PedagogicalAssignmentStatus.ACTIVE)) {
            throw new AcademicException(AcademicException.Kind.PRIMARY_MANAGER_ALREADY_ASSIGNED);
        }

        Long actorId = changePublisher.actorId(callerSubject);
        PedagogicalAssignment assignment = new PedagogicalAssignment(program, target.internalId(), type,
                validFrom, validUntil, AcademicQuerySupport.trimToNull(request.reason()), actorId);
        assignment.markCreatedBy(actorId);
        PedagogicalAssignment saved;
        try {
            saved = assignmentPersister.persist(assignment);
        } catch (DataIntegrityViolationException violation) {
            if (isActivePrimaryUniqueViolation(violation)) {
                throw new AcademicException(AcademicException.Kind.PRIMARY_MANAGER_ALREADY_ASSIGNED);
            }
            throw violation;
        }
        changePublisher.publish(AcademicResourceType.PEDAGOGICAL_ASSIGNMENT, saved.getPublicId(),
                AcademicChangeAction.CREATED, actorId, detail(program, type));
        return PedagogicalAssignmentResponse.from(saved, target.publicId());
    }

    @Transactional
    void close(UUID publicId, String reason, LocalDate effectiveDate, String callerSubject) {
        PedagogicalAssignment assignment = require(publicId);
        if (assignment.isClosed()) {
            throw new AcademicException(AcademicException.Kind.ASSIGNMENT_ALREADY_CLOSED);
        }
        LocalDate effective = effectiveDate != null ? effectiveDate : LocalDate.now(clock);
        if (effective.isBefore(assignment.getValidFrom())) {
            throw new AcademicException(AcademicException.Kind.ASSIGNMENT_DATE_INVALID);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        assignment.close(reason, actorId, effective);
        changePublisher.publish(AcademicResourceType.PEDAGOGICAL_ASSIGNMENT, assignment.getPublicId(),
                AcademicChangeAction.CLOSED, actorId,
                detail(assignment.getProgram(), assignment.getAssignmentRole()));
    }

    /**
     * Vrai uniquement si la violation d'intégrité concerne la contrainte
     * d'unicité du responsable principal actif — jamais une autre FK,
     * {@code CHECK}, {@code NOT NULL} ou unicité. Recherche à la fois le
     * nom de contrainte structuré (Hibernate) et le message SQL, en
     * exigeant une sémantique de doublon.
     */
    static boolean isActivePrimaryUniqueViolation(DataIntegrityViolationException violation) {
        for (Throwable cause = violation; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint) {
                String name = constraint.getConstraintName();
                if (name != null && name.toLowerCase(Locale.ROOT).contains(ACTIVE_PRIMARY_CONSTRAINT)) {
                    return true;
                }
            }
            String message = cause.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains(ACTIVE_PRIMARY_CONSTRAINT)
                        && (lower.contains("duplicate entry") || lower.contains("unique"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private UUID resolveUserPublicId(Long userInternalId) {
        return userDirectory.findByInternalId(userInternalId)
                .map(UserDirectory.UserRef::publicId)
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.ASSIGNMENT_TARGET_NOT_ELIGIBLE));
    }

    private PedagogicalAssignment require(UUID publicId) {
        return assignmentRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.PEDAGOGICAL_ASSIGNMENT_NOT_FOUND));
    }

    private Program requireProgram(UUID publicId) {
        return programRepository.findByPublicId(publicId)
                .orElseThrow(() -> new AcademicException(AcademicException.Kind.PROGRAM_NOT_FOUND));
    }

    private static String detail(Program program, PedagogicalAssignmentRole type) {
        return "program=" + program.getCode() + ";type=" + type.name();
    }

    private static PedagogicalAssignmentRole requireType(String value) {
        try {
            return PedagogicalAssignmentRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new AcademicException(AcademicException.Kind.INVALID_ASSIGNMENT_ROLE);
        }
    }

    private static Optional<PedagogicalAssignmentRole> parseType(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(PedagogicalAssignmentRole.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException invalid) {
            throw new AcademicException(AcademicException.Kind.INVALID_FILTER);
        }
    }

    private static Optional<PedagogicalAssignmentStatus> parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(PedagogicalAssignmentStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException invalid) {
            throw new AcademicException(AcademicException.Kind.INVALID_FILTER);
        }
    }

    private static UUID parseUuid(String value, AcademicException.Kind notFound) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException notAUuid) {
            throw new AcademicException(notFound);
        }
    }
}
