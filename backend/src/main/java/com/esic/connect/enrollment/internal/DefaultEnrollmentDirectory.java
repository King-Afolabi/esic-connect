package com.esic.connect.enrollment.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.esic.connect.identity.UserDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implémentation du port {@link EnrollmentDirectory}. Reste confinée à
 * {@code enrollment.internal} : les autres modules ne connaissent que
 * l'interface publique et le {@link EnrollmentDirectory.EnrollmentRef}.
 *
 * <p>La classe et l'année scolaire sont résolues via le port
 * {@link ClassGroupDirectory} (déjà consommé par ce module) à partir de
 * la valeur technique {@code enrollment.class_group_id} — aucun partage
 * d'entité JPA avec {@code academic}.
 */
@Component
class DefaultEnrollmentDirectory implements EnrollmentDirectory {

    private final EnrollmentRepository enrollmentRepository;
    private final ClassGroupDirectory classGroupDirectory;
    private final UserDirectory userDirectory;

    DefaultEnrollmentDirectory(EnrollmentRepository enrollmentRepository,
                               ClassGroupDirectory classGroupDirectory,
                               UserDirectory userDirectory) {
        this.enrollmentRepository = enrollmentRepository;
        this.classGroupDirectory = classGroupDirectory;
        this.userDirectory = userDirectory;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EnrollmentRef> findByPublicId(UUID enrollmentPublicId) {
        if (enrollmentPublicId == null) {
            return Optional.empty();
        }
        return enrollmentRepository.findByPublicId(enrollmentPublicId).map(this::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EnrollmentRef> findByInternalId(long enrollmentInternalId) {
        return enrollmentRepository.findById(enrollmentInternalId).map(this::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentRef> findActiveEnrollmentsForUserOn(UUID userPublicId, LocalDate date) {
        return userDirectory.findByPublicId(userPublicId)
                .map(user -> enrollmentRepository
                        .findByStudentProfile_UserIdAndStatus(user.internalId(), EnrollmentStatus.ACTIVE))
                .orElseGet(List::of)
                .stream()
                .filter(enrollment -> coversDate(enrollment, date))
                .map(this::toRef)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentRef> findEnrollmentsForUser(UUID userPublicId) {
        return userDirectory.findByPublicId(userPublicId)
                .map(user -> enrollmentRepository.findByStudentProfile_UserId(user.internalId()))
                .orElseGet(List::of)
                .stream()
                .filter(enrollment -> enrollment.getStatus() != EnrollmentStatus.ARCHIVED)
                .map(this::toRef)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RosterEntry> findActiveRosterForClasses(Collection<UUID> classGroupPublicIds) {
        return roster(classGroupPublicIds, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RosterEntry> findRosterForClassesOn(Collection<UUID> classGroupPublicIds, LocalDate date) {
        return roster(classGroupPublicIds, date);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEnrollmentValidOn(UUID enrollmentPublicId, LocalDate date) {
        if (enrollmentPublicId == null) {
            return false;
        }
        return enrollmentRepository.findByPublicId(enrollmentPublicId)
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE)
                .filter(enrollment -> coversDate(enrollment, date))
                .isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RosterEntry> searchStudents(String query, Collection<UUID> visibleClassGroupPublicIds,
                                            int limit) {
        String pattern = com.esic.connect.shared.SearchPattern.of(query);
        if (pattern == null) {
            return List.of();
        }
        int bounded = com.esic.connect.shared.SearchPattern.bound(limit);
        org.springframework.data.domain.Pageable page =
                org.springframework.data.domain.PageRequest.of(0, bounded);

        // Deux sources, réunies : le numéro étudiant vit dans `enrollment`,
        // le nom dans `identity`. Joindre les deux tables franchirait une
        // frontière de module ; on demande donc les comptes au port, puis
        // leurs inscriptions.
        java.util.LinkedHashMap<Long, Enrollment> found = new java.util.LinkedHashMap<>();
        for (Enrollment enrollment
                : enrollmentRepository.searchByStudentNumber(pattern, EnrollmentStatus.ACTIVE, page)) {
            found.putIfAbsent(enrollment.getId(), enrollment);
        }
        List<Long> userIds = userDirectory.searchByName(query, "STUDENT", bounded).stream()
                .map(UserDirectory.NamedUserRef::internalId)
                .toList();
        if (!userIds.isEmpty()) {
            for (Enrollment enrollment
                    : enrollmentRepository.findActiveByStudentUserIds(userIds, EnrollmentStatus.ACTIVE)) {
                found.putIfAbsent(enrollment.getId(), enrollment);
            }
        }
        if (found.isEmpty()) {
            return List.of();
        }

        Set<Long> visibleClassIds = visibleClassGroupPublicIds == null
                ? null
                : internalIdsOf(visibleClassGroupPublicIds);
        // Périmètre vide ≠ périmètre global : un responsable sans classe
        // visible ne trouve aucun apprenant, jamais tous.
        if (visibleClassIds != null && visibleClassIds.isEmpty()) {
            return List.of();
        }
        return found.values().stream()
                .filter(e -> visibleClassIds == null || visibleClassIds.contains(e.getClassGroupId()))
                .limit(bounded)
                .map(this::toRosterEntry)
                .toList();
    }

    private RosterEntry toRosterEntry(Enrollment enrollment) {
        StudentProfile profile = enrollment.getStudentProfile();
        UserDirectory.PersonName name = userDirectory.findName(profile.getUserId()).orElse(null);
        ClassGroupDirectory.ClassGroupRef classRef =
                classGroupDirectory.findByInternalId(enrollment.getClassGroupId()).orElse(null);
        return new RosterEntry(enrollment.getId(), enrollment.getPublicId(), profile.getPublicId(),
                profile.getStudentNumber(),
                name != null ? name.firstName() : null,
                name != null ? name.lastName() : null,
                classRef != null ? classRef.publicId() : null,
                classRef != null ? classRef.code() : null);
    }

    /**
     * Effectif {@code ACTIVE} des classes indiquées, filtré sur la
     * couverture de {@code date} lorsqu'elle est fournie ({@code null} =
     * pas de filtrage temporel, effectif actif « courant »).
     */
    private List<RosterEntry> roster(Collection<UUID> classGroupPublicIds, LocalDate date) {
        Set<Long> classInternalIds = internalIdsOf(classGroupPublicIds);
        if (classInternalIds.isEmpty()) {
            return List.of();
        }
        List<Enrollment> enrollments = enrollmentRepository
                .findByClassGroupIdInAndStatus(classInternalIds, EnrollmentStatus.ACTIVE).stream()
                .filter(enrollment -> date == null || coversDate(enrollment, date))
                .toList();
        if (enrollments.isEmpty()) {
            return List.of();
        }
        // Résolution par lot du nom et du code de classe : la variante
        // unitaire, appelée par inscription, coûtait deux requêtes par
        // apprenant — un effectif de trente en payait soixante pour une
        // information que la base rend en deux (NFR-PERF-08, dette T-03).
        java.util.Map<Long, UserDirectory.PersonName> names = userDirectory.findNames(
                enrollments.stream().map(e -> e.getStudentProfile().getUserId()).toList());
        java.util.Map<Long, ClassGroupDirectory.ClassGroupRef> classes = new java.util.HashMap<>();
        for (ClassGroupDirectory.ClassGroupRef ref : classGroupDirectory.findByInternalIds(
                enrollments.stream().map(Enrollment::getClassGroupId).distinct().toList())) {
            classes.put(ref.internalId(), ref);
        }
        return enrollments.stream()
                .map(enrollment -> {
                    StudentProfile profile = enrollment.getStudentProfile();
                    UserDirectory.PersonName name = names.get(profile.getUserId());
                    ClassGroupDirectory.ClassGroupRef classRef = classes.get(enrollment.getClassGroupId());
                    return new RosterEntry(
                            enrollment.getId(),
                            enrollment.getPublicId(),
                            profile.getPublicId(),
                            profile.getStudentNumber(),
                            name != null ? name.firstName() : null,
                            name != null ? name.lastName() : null,
                            classRef != null ? classRef.publicId() : null,
                            classRef != null ? classRef.code() : null);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findActiveStudentUserPublicIds(Collection<UUID> classGroupPublicIds, LocalDate date) {
        Set<Long> classInternalIds = internalIdsOf(classGroupPublicIds);
        if (classInternalIds.isEmpty()) {
            return Set.of();
        }
        return enrollmentRepository
                .findByClassGroupIdInAndStatus(classInternalIds, EnrollmentStatus.ACTIVE).stream()
                .filter(enrollment -> date == null || coversDate(enrollment, date))
                .map(enrollment -> enrollment.getStudentProfile().getUserId())
                .filter(java.util.Objects::nonNull)
                .map(userDirectory::findByInternalId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                // Un compte archivé n'est jamais destinataire : le
                // notifier reviendrait à écrire dans une boîte que
                // personne ne relève.
                .filter(ref -> !ref.archived())
                .map(UserDirectory.UserRef::publicId)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttendeeRef> describeAttendee(long enrollmentInternalId) {
        return enrollmentRepository.findById(enrollmentInternalId).map(enrollment -> {
            StudentProfile profile = enrollment.getStudentProfile();
            UserDirectory.PersonName name = userDirectory.findName(profile.getUserId()).orElse(null);
            return new AttendeeRef(
                    profile.getPublicId(),
                    enrollment.getPublicId(),
                    profile.getStudentNumber(),
                    name != null ? name.firstName() : null,
                    name != null ? name.lastName() : null);
        });
    }

    /** Traduction publique -> interne d'un lot de classes ; les inconnues sont ignorées. */
    /**
     * Résout un lot de classes en une <strong>seule</strong> requête
     * (dette T-03).
     *
     * <p>{@code findByPublicId} appelé dans un {@code map} produisait une
     * requête par classe : un responsable pédagogique de quinze classes
     * en payait quinze avant même de lire un effectif. Le port expose
     * {@code findByPublicIds} précisément pour cela — les identifiants
     * inconnus sont simplement absents du résultat, comme avant.
     */
    private Set<Long> internalIdsOf(Collection<UUID> classGroupPublicIds) {
        if (classGroupPublicIds == null || classGroupPublicIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> ids = classGroupPublicIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return classGroupDirectory.findByPublicIds(ids).stream()
                .map(ClassGroupDirectory.ClassGroupRef::internalId)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveEnrollmentsInClasses(Collection<UUID> classGroupPublicIds) {
        Set<Long> classInternalIds = internalIdsOf(classGroupPublicIds);
        if (classInternalIds.isEmpty()) {
            return 0;
        }
        return enrollmentRepository.countByClassGroupIdInAndStatus(classInternalIds, EnrollmentStatus.ACTIVE);
    }

    private static boolean coversDate(Enrollment enrollment, LocalDate date) {
        if (date == null) {
            return true;
        }
        boolean startedByDate = !enrollment.getStartDate().isAfter(date);
        boolean notYetEnded = enrollment.getEndDate() == null || !enrollment.getEndDate().isBefore(date);
        return startedByDate && notYetEnded;
    }

    private EnrollmentRef toRef(Enrollment enrollment) {
        ClassGroupDirectory.ClassGroupRef classRef =
                classGroupDirectory.findByInternalId(enrollment.getClassGroupId()).orElse(null);
        UUID studentUserPublicId = userDirectory.findByInternalId(enrollment.getStudentProfile().getUserId())
                .map(UserDirectory.UserRef::publicId).orElse(null);
        return new EnrollmentRef(
                enrollment.getId(),
                enrollment.getPublicId(),
                enrollment.getStudentProfile().getPublicId(),
                studentUserPublicId,
                classRef != null ? classRef.publicId() : null,
                classRef != null ? classRef.code() : null,
                classRef != null ? classRef.academicYearPublicId() : null,
                classRef != null ? classRef.academicYearCode() : null,
                enrollment.getStatus() == EnrollmentStatus.ACTIVE);
    }
}
