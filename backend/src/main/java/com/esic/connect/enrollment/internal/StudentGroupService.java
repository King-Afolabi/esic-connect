package com.esic.connect.enrollment.internal;

import com.esic.connect.academic.AcademicReferenceDirectory;
import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.academic.SubjectDirectory;
import com.esic.connect.enrollment.EnrollmentChangeAction;
import com.esic.connect.enrollment.EnrollmentResourceType;
import com.esic.connect.identity.UserDirectory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Groupes temporaires (EF-ACA-007, docs/02 §6.5).
 *
 * <p>Un groupe rassemble des apprenants issus d'une ou plusieurs classes
 * pour une période. Il ne remplace jamais la classe principale : les
 * inscriptions ne sont pas modifiées, seule une appartenance
 * supplémentaire est enregistrée (RG-022).
 *
 * <p><strong>Périmètre.</strong> Un groupe appartient à une formation.
 * Un responsable pédagogique ne voit et ne gère que les groupes de ses
 * formations ; un groupe hors périmètre produit un {@code 403}, jamais un
 * accès silencieux. Les membres, eux, sont contrôlés classe par classe :
 * on ne peut ajouter que des inscriptions dont la classe relève du
 * périmètre.
 */
@Service
@Transactional
class StudentGroupService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "createdAt", "updatedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "code");

    private final StudentGroupRepository groupRepository;
    private final StudentGroupMemberRepository memberRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AcademicReferenceDirectory academicReferences;
    private final AcademicScopeDirectory academicScope;
    private final ClassGroupDirectory classGroups;
    private final SubjectDirectory subjects;
    private final UserDirectory userDirectory;
    private final EnrollmentChangePublisher changePublisher;
    private final Clock clock;

    StudentGroupService(StudentGroupRepository groupRepository,
                        StudentGroupMemberRepository memberRepository,
                        EnrollmentRepository enrollmentRepository,
                        AcademicReferenceDirectory academicReferences,
                        AcademicScopeDirectory academicScope,
                        ClassGroupDirectory classGroups,
                        SubjectDirectory subjects,
                        UserDirectory userDirectory,
                        EnrollmentChangePublisher changePublisher,
                        Clock clock) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.academicReferences = academicReferences;
        this.academicScope = academicScope;
        this.classGroups = classGroups;
        this.subjects = subjects;
        this.userDirectory = userDirectory;
        this.changePublisher = changePublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    PageResponse<StudentGroupResponse> list(String statusFilter, String textFilter,
                                            String programPublicId, int page, int size, String sort) {
        Pageable pageable = EnrollmentQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        List<Specification<StudentGroup>> specs = new ArrayList<>();

        parseStatus(statusFilter).ifPresent(status ->
                specs.add((root, query, cb) -> cb.equal(root.get("status"), status)));
        if (textFilter != null && !textFilter.isBlank()) {
            String text = "%" + textFilter.trim().toLowerCase() + "%";
            specs.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("code")), text),
                    cb.like(cb.lower(root.get("name")), text)));
        }
        if (programPublicId != null && !programPublicId.isBlank()) {
            AcademicReferenceDirectory.ProgramRef program = requireProgram(programPublicId);
            specs.add((root, query, cb) -> cb.equal(root.get("programId"), program.internalId()));
        }
        // Filtrage de périmètre décidé CÔTÉ SERVEUR : un responsable ne
        // voit jamais les groupes d'une formation qui n'est pas la sienne.
        Optional<Set<Long>> visiblePrograms = academicScope.visibleProgramIds();
        if (visiblePrograms.isPresent()) {
            Set<Long> visible = visiblePrograms.get();
            if (visible.isEmpty()) {
                return PageResponse.of(Page.<StudentGroup>empty(pageable), this::toResponse);
            }
            specs.add((root, query, cb) -> root.get("programId").in(visible));
        }

        Page<StudentGroup> result = groupRepository.findAll(Specification.allOf(specs), pageable);
        return PageResponse.of(result, toResponseWith(countsOf(result.getContent())));
    }

    @Transactional(readOnly = true)
    StudentGroupResponse get(UUID publicId) {
        return toResponse(requireInScope(publicId));
    }

    StudentGroupResponse create(StudentGroupRequests.Create request, String callerSubject) {
        AcademicReferenceDirectory.ProgramRef program = requireProgram(request.programPublicId());
        if (!academicScope.isProgramInScope(program.publicId())) {
            throw new EnrollmentException(EnrollmentException.Kind.OUT_OF_SCOPE);
        }
        AcademicReferenceDirectory.AcademicYearRef year =
                requireAcademicYear(request.academicYearPublicId());
        Long subjectId = resolveSubjectId(request.subjectPublicId());
        requireValidPeriod(request.startsOn(), request.endsOn());

        String code = request.code().trim();
        if (groupRepository.existsByCodeAndAcademicYearId(code, year.internalId())) {
            throw new EnrollmentException(EnrollmentException.Kind.DUPLICATE_GROUP_CODE);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        StudentGroup group = groupRepository.save(new StudentGroup(code, request.name().trim(),
                year.internalId(), program.internalId(), subjectId,
                request.startsOn(), request.endsOn(), actorId));
        changePublisher.publish(EnrollmentResourceType.STUDENT_GROUP, group.getPublicId(),
                EnrollmentChangeAction.CREATED, actorId, "code=" + code);
        return toResponse(group);
    }

    StudentGroupResponse update(UUID publicId, StudentGroupRequests.Update request,
                                String callerSubject) {
        StudentGroup group = requireInScope(publicId);
        requireNotArchived(group);
        requireValidPeriod(request.startsOn(), request.endsOn());
        Long actorId = changePublisher.actorId(callerSubject);
        group.updateDetails(request.name().trim(), resolveSubjectId(request.subjectPublicId()),
                request.startsOn(), request.endsOn(), actorId);
        changePublisher.publish(EnrollmentResourceType.STUDENT_GROUP, group.getPublicId(),
                EnrollmentChangeAction.UPDATED, actorId, "code=" + group.getCode());
        return toResponse(group);
    }

    StudentGroupResponse archive(UUID publicId, String callerSubject) {
        StudentGroup group = requireInScope(publicId);
        requireNotArchived(group);
        Long actorId = changePublisher.actorId(callerSubject);
        group.archive(clock.instant(), actorId);
        changePublisher.publish(EnrollmentResourceType.STUDENT_GROUP, group.getPublicId(),
                EnrollmentChangeAction.ARCHIVED, actorId, null);
        return toResponse(group);
    }

    StudentGroupResponse restore(UUID publicId, String callerSubject) {
        StudentGroup group = requireInScope(publicId);
        if (!group.isArchived()) {
            throw new EnrollmentException(EnrollmentException.Kind.INVALID_CLOSE_STATUS);
        }
        Long actorId = changePublisher.actorId(callerSubject);
        group.restore(actorId);
        changePublisher.publish(EnrollmentResourceType.STUDENT_GROUP, group.getPublicId(),
                EnrollmentChangeAction.RESTORED, actorId, null);
        return toResponse(group);
    }

    // ------------------------------------------------------------------
    // Membres
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<StudentGroupResponse.Member> members(UUID publicId) {
        StudentGroup group = requireInScope(publicId);
        List<StudentGroupMember> members = memberRepository.findByStudentGroupIdAndStatus(
                group.getId(), StudentGroupMemberStatus.ACTIVE);
        if (members.isEmpty()) {
            return List.of();
        }
        Map<Long, Enrollment> enrollments = enrollmentRepository
                .findAllById(members.stream().map(StudentGroupMember::getEnrollmentId).toList())
                .stream()
                .collect(Collectors.toMap(Enrollment::getId, Function.identity()));
        // Résolution en lot (anti-N+1) du compte et du numéro étudiant
        // facultatif de chaque apprenant du groupe.
        List<Long> userIds = enrollments.values().stream().map(Enrollment::getUserId).distinct().toList();
        Map<Long, UserDirectory.NamedUserRef> userRefs = userDirectory.findNamedRefs(userIds);
        Map<Long, String> studentNumbers = userDirectory.findStudentNumbers(userIds);
        return members.stream()
                .map(member -> toMember(member, enrollments.get(member.getEnrollmentId()), userRefs, studentNumbers))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(StudentGroupResponse.Member::studentNumber,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    /**
     * Ajoute des inscriptions au groupe. Une inscription déjà membre
     * <strong>n'est pas</strong> un échec de tout le lot : elle est
     * ignorée, et le compte final reflète l'état réel. Une inscription
     * hors périmètre, elle, refuse l'ensemble.
     */
    List<StudentGroupResponse.Member> addMembers(UUID publicId,
                                                 StudentGroupRequests.AddMembers request,
                                                 String callerSubject) {
        StudentGroup group = requireInScope(publicId);
        requireNotArchived(group);
        Long actorId = changePublisher.actorId(callerSubject);
        Instant now = clock.instant();

        for (String rawId : request.enrollmentPublicIds()) {
            UUID enrollmentId = EnrollmentWeb.parseUuid(rawId,
                    EnrollmentException.Kind.ENROLLMENT_NOT_FOUND);
            Enrollment enrollment = enrollmentRepository.findByPublicId(enrollmentId)
                    .orElseThrow(() -> new EnrollmentException(
                            EnrollmentException.Kind.ENROLLMENT_NOT_FOUND));
            UUID classPublicId = classGroups.findByInternalId(enrollment.getClassGroupId())
                    .map(ClassGroupDirectory.ClassGroupRef::publicId)
                    .orElseThrow(() -> new EnrollmentException(
                            EnrollmentException.Kind.CLASS_GROUP_NOT_FOUND));
            if (!academicScope.isClassInScope(classPublicId)) {
                throw new EnrollmentException(EnrollmentException.Kind.OUT_OF_SCOPE);
            }
            if (enrollment.getStatus() != EnrollmentStatus.ACTIVE) {
                throw new EnrollmentException(EnrollmentException.Kind.ENROLLMENT_NOT_ACTIVE);
            }
            boolean alreadyMember = memberRepository
                    .findByStudentGroupIdAndEnrollmentIdAndStatus(group.getId(), enrollment.getId(),
                            StudentGroupMemberStatus.ACTIVE)
                    .isPresent();
            if (!alreadyMember) {
                memberRepository.save(new StudentGroupMember(group.getId(), enrollment.getId(),
                        now, actorId));
            }
        }
        changePublisher.publish(EnrollmentResourceType.STUDENT_GROUP, group.getPublicId(),
                EnrollmentChangeAction.UPDATED, actorId,
                "members+=" + request.enrollmentPublicIds().size());
        return members(publicId);
    }

    /** Retrait logique : la ligne passe en {@code REMOVED}, jamais supprimée. */
    void removeMember(UUID groupPublicId, UUID memberPublicId, String callerSubject) {
        StudentGroup group = requireInScope(groupPublicId);
        requireNotArchived(group);
        StudentGroupMember member = memberRepository
                .findByPublicIdAndStudentGroupId(memberPublicId, group.getId())
                .orElseThrow(() -> new EnrollmentException(
                        EnrollmentException.Kind.STUDENT_GROUP_NOT_FOUND));
        if (member.getStatus() == StudentGroupMemberStatus.ACTIVE) {
            member.remove(clock.instant());
            memberRepository.save(member);
            changePublisher.publish(EnrollmentResourceType.STUDENT_GROUP, group.getPublicId(),
                    EnrollmentChangeAction.UPDATED, changePublisher.actorId(callerSubject),
                    "member-removed");
        }
    }

    // ------------------------------------------------------------------

    private StudentGroup requireInScope(UUID publicId) {
        StudentGroup group = groupRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EnrollmentException(
                        EnrollmentException.Kind.STUDENT_GROUP_NOT_FOUND));
        Optional<Set<Long>> visible = academicScope.visibleProgramIds();
        if (visible.isPresent() && !visible.get().contains(group.getProgramId())) {
            throw new EnrollmentException(EnrollmentException.Kind.OUT_OF_SCOPE);
        }
        return group;
    }

    private void requireNotArchived(StudentGroup group) {
        if (group.isArchived()) {
            throw new EnrollmentException(EnrollmentException.Kind.GROUP_ARCHIVED);
        }
    }

    private void requireValidPeriod(LocalDate startsOn, LocalDate endsOn) {
        if (startsOn != null && endsOn != null && endsOn.isBefore(startsOn)) {
            throw new EnrollmentException(EnrollmentException.Kind.INVALID_GROUP_PERIOD);
        }
    }

    private AcademicReferenceDirectory.ProgramRef requireProgram(String rawPublicId) {
        UUID id = EnrollmentWeb.parseUuid(rawPublicId, EnrollmentException.Kind.PROGRAM_NOT_FOUND);
        return academicReferences.findProgramByPublicId(id)
                .orElseThrow(() -> new EnrollmentException(EnrollmentException.Kind.PROGRAM_NOT_FOUND));
    }

    private AcademicReferenceDirectory.AcademicYearRef requireAcademicYear(String rawPublicId) {
        UUID id = EnrollmentWeb.parseUuid(rawPublicId,
                EnrollmentException.Kind.ACADEMIC_YEAR_NOT_FOUND);
        return academicReferences.findAcademicYearByPublicId(id)
                .orElseThrow(() -> new EnrollmentException(
                        EnrollmentException.Kind.ACADEMIC_YEAR_NOT_FOUND));
    }

    private Long resolveSubjectId(String rawPublicId) {
        if (rawPublicId == null || rawPublicId.isBlank()) {
            return null;
        }
        UUID id = EnrollmentWeb.parseUuid(rawPublicId, EnrollmentException.Kind.SUBJECT_NOT_FOUND);
        return subjects.findByPublicId(id)
                .map(SubjectDirectory.SubjectRef::internalId)
                .orElseThrow(() -> new EnrollmentException(EnrollmentException.Kind.SUBJECT_NOT_FOUND));
    }

    private Optional<StudentGroupStatus> parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(StudentGroupStatus.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            throw new EnrollmentException(EnrollmentException.Kind.INVALID_FILTER);
        }
    }

    private StudentGroupResponse.Member toMember(StudentGroupMember member, Enrollment enrollment,
                                                 Map<Long, UserDirectory.NamedUserRef> userRefs,
                                                 Map<Long, String> studentNumbers) {
        if (enrollment == null) {
            return null;
        }
        Optional<ClassGroupDirectory.ClassGroupRef> classRef =
                classGroups.findByInternalId(enrollment.getClassGroupId());
        UserDirectory.NamedUserRef userRef = userRefs.get(enrollment.getUserId());
        return new StudentGroupResponse.Member(
                member.getPublicId(),
                enrollment.getPublicId(),
                userRef != null ? userRef.publicId() : null,
                studentNumbers.get(enrollment.getUserId()),
                classRef.map(ClassGroupDirectory.ClassGroupRef::publicId).orElse(null),
                classRef.map(ClassGroupDirectory.ClassGroupRef::code).orElse(null),
                member.getJoinedAt());
    }

    private StudentGroupResponse toResponse(StudentGroup group) {
        return toResponseWith(countsOf(List.of(group))).apply(group);
    }

    /**
     * Un seul comptage pour toute la page, jamais une requête par ligne
     * (NFR-PERF-08).
     */
    private Map<Long, Long> countsOf(List<StudentGroup> groups) {
        if (groups.isEmpty()) {
            return Map.of();
        }
        return memberRepository
                .findByStudentGroupIdInAndStatus(groups.stream().map(StudentGroup::getId).toList(),
                        StudentGroupMemberStatus.ACTIVE)
                .stream()
                .collect(Collectors.groupingBy(StudentGroupMember::getStudentGroupId,
                        Collectors.counting()));
    }

    private Function<StudentGroup, StudentGroupResponse> toResponseWith(Map<Long, Long> counts) {
        return group -> {
            Optional<SubjectDirectory.SubjectRef> subject = group.getSubjectId() == null
                    ? Optional.empty()
                    : subjects.findByInternalId(group.getSubjectId());
            Optional<AcademicReferenceDirectory.AcademicYearRef> year =
                    academicReferences.findAcademicYearByInternalId(group.getAcademicYearId());
            Optional<AcademicReferenceDirectory.ProgramRef> program =
                    academicReferences.findProgramByInternalId(group.getProgramId());
            return new StudentGroupResponse(
                    group.getPublicId(),
                    group.getCode(),
                    group.getName(),
                    group.getStatus().name(),
                    year.map(AcademicReferenceDirectory.AcademicYearRef::publicId).orElse(null),
                    year.map(AcademicReferenceDirectory.AcademicYearRef::code).orElse(null),
                    program.map(AcademicReferenceDirectory.ProgramRef::publicId).orElse(null),
                    program.map(AcademicReferenceDirectory.ProgramRef::code).orElse(null),
                    subject.map(SubjectDirectory.SubjectRef::publicId).orElse(null),
                    subject.map(SubjectDirectory.SubjectRef::code).orElse(null),
                    group.getStartsOn(),
                    group.getEndsOn(),
                    counts.getOrDefault(group.getId(), 0L),
                    group.getCreatedAt(),
                    group.getUpdatedAt());
        };
    }
}
