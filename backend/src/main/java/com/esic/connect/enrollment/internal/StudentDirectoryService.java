package com.esic.connect.enrollment.internal;

import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.identity.UserDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Écran « Apprenants » (refonte 2026-09) : liste et détail de
 * <strong>tous les comptes porteurs d'un rôle actif {@code STUDENT}</strong>
 * (module {@code identity}, seule source de vérité du statut apprenant),
 * décorés — jamais conditionnés — par les données facultatives et
 * indépendantes que sont le profil apprenant ({@code student_profile}) et
 * l'inscription courante ({@code enrollment}).
 *
 * <p>Ce service part du <strong>compte</strong> : un {@code STUDENT} sans
 * inscription y apparaît, une seule fois même s'il cumule plusieurs
 * inscriptions historiques (la pagination porte sur les comptes, jamais
 * sur les inscriptions). Il n'existe plus de {@code student_profile}
 * distinct (refonte 2026-09) : le numéro étudiant et la date de naissance
 * sont désormais de simples colonnes de {@code user_account}, déjà portées
 * par {@link UserDirectory.AccountSummary}.
 *
 * <p>Le périmètre de consultation ({@link RosterScopeResolver}) : accès
 * global pour {@code ADMIN}/{@code SUPER_ADMIN}/{@code SCHOOL_ADMINISTRATION} ;
 * {@code PEDAGOGICAL_MANAGER}/{@code TEACHER} restreints aux comptes ayant
 * une inscription {@code ACTIVE} dans l'une de leurs classes — un
 * apprenant qui n'a jamais été inscrit dans leur périmètre ne leur est pas
 * montré, ce qui reste une décision de <em>périmètre d'accès</em>
 * (cahier §5.5), distincte de la question « est-ce un apprenant ? ».
 */
@Service
class StudentDirectoryService {

    private static final String STUDENT_ROLE = "STUDENT";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final String DEFAULT_SORT = "createdAt,desc";
    private static final Set<String> SORTABLE = Set.of("lastName", "email", "createdAt", "lastLoginAt");
    private static final Set<String> VALID_STATUSES =
            Set.of("PENDING_ACTIVATION", "ACTIVE", "SUSPENDED", "LOCKED", "ARCHIVED");

    private final UserDirectory userDirectory;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassGroupDirectory classGroupDirectory;
    private final RosterScopeResolver rosterScope;

    StudentDirectoryService(UserDirectory userDirectory,
                            EnrollmentRepository enrollmentRepository,
                            ClassGroupDirectory classGroupDirectory,
                            RosterScopeResolver rosterScope) {
        this.userDirectory = userDirectory;
        this.enrollmentRepository = enrollmentRepository;
        this.classGroupDirectory = classGroupDirectory;
        this.rosterScope = rosterScope;
    }

    @Transactional(readOnly = true)
    PageResponse<StudentResponse> list(String q, String status, int page, int size, String sort,
                                       String callerSubject) {
        String validatedSort = validateSort(sort);
        String validatedStatus = validateStatus(status);
        int effectivePage = Math.max(page, 0);
        int effectiveSize = normalizeSize(size);

        // Périmètre de consultation : accès global (aucun filtre) pour
        // l'administration ; un PEDAGOGICAL_MANAGER / TEACHER ne voit que
        // les comptes ayant une inscription ACTIVE dans l'une de ses
        // classes.
        Optional<Set<Long>> visibleClasses = rosterScope.visibleClassGroupInternalIds(callerSubject);
        List<Long> restrictToUserIds = null;
        if (visibleClasses.isPresent()) {
            if (visibleClasses.get().isEmpty()) {
                return emptyPage(effectivePage, effectiveSize);
            }
            restrictToUserIds = enrollmentRepository.findUserIdsByClassGroupIdInAndStatus(
                    visibleClasses.get(), EnrollmentStatus.ACTIVE);
            if (restrictToUserIds.isEmpty()) {
                return emptyPage(effectivePage, effectiveSize);
            }
        }

        UserDirectory.AccountPage accountPage = userDirectory.listAccountsByActiveRole(
                new UserDirectory.AccountRoleQuery(STUDENT_ROLE, validatedStatus,
                        EnrollmentQuerySupport.normalizeText(q).orElse(null), restrictToUserIds,
                        effectivePage, effectiveSize, validatedSort));

        List<UserDirectory.AccountSummary> accounts = accountPage.content();
        List<Long> userIds = accounts.stream().map(UserDirectory.AccountSummary::internalId).toList();
        Map<Long, Enrollment> currentEnrollments = resolveCurrentEnrollments(userIds);
        Map<Long, ClassGroupDirectory.ClassGroupRef> classRefs = resolveClassRefs(currentEnrollments.values());

        List<StudentResponse> content = accounts.stream()
                .map(account -> StudentResponse.of(account,
                        currentEnrollments.get(account.internalId()),
                        classRefOf(currentEnrollments.get(account.internalId()), classRefs)))
                .toList();

        int totalPages = effectiveSize == 0 ? 0
                : (int) Math.ceil((double) accountPage.totalElements() / effectiveSize);
        return new PageResponse<>(content, effectivePage, effectiveSize, accountPage.totalElements(), totalPages);
    }

    @Transactional(readOnly = true)
    StudentResponse get(UUID userPublicId, String callerSubject) {
        UserDirectory.UserRef ref = requireStudent(userPublicId);

        // Périmètre de consultation : hors périmètre ⇒ 404 (cahier §18.2),
        // pas 403 — l'existence même de la fiche est une information à
        // protéger.
        rosterScope.visibleClassGroupInternalIds(callerSubject).ifPresent(visible -> {
            List<Enrollment> active = enrollmentRepository.findByUserIdAndStatus(ref.internalId(),
                    EnrollmentStatus.ACTIVE);
            boolean inScope = active.stream().anyMatch(e -> visible.contains(e.getClassGroupId()));
            if (!inScope) {
                throw new EnrollmentException(EnrollmentException.Kind.STUDENT_NOT_FOUND);
            }
        });

        UserDirectory.AccountPage single = userDirectory.listAccountsByActiveRole(
                new UserDirectory.AccountRoleQuery(STUDENT_ROLE, null, null, Set.of(ref.internalId()), 0, 1, null));
        UserDirectory.AccountSummary account = single.content().stream().findFirst()
                .orElseThrow(() -> new EnrollmentException(EnrollmentException.Kind.STUDENT_NOT_FOUND));

        Enrollment current = currentEnrollmentOf(ref.internalId());
        ClassGroupDirectory.ClassGroupRef classRef = current == null ? null
                : classGroupDirectory.findByInternalId(current.getClassGroupId()).orElse(null);
        return StudentResponse.of(account, current, classRef);
    }

    // ------------------------------------------------------------------

    /**
     * Exige un compte existant, non archivé, porteur d'un rôle actif
     * {@code STUDENT} — exactement la même règle d'éligibilité que
     * {@link EnrollmentService#enroll}.
     */
    private UserDirectory.UserRef requireStudent(UUID userPublicId) {
        UserDirectory.UserRef ref = userDirectory.findByPublicId(userPublicId)
                .orElseThrow(() -> new EnrollmentException(EnrollmentException.Kind.STUDENT_NOT_FOUND));
        if (ref.archived() || !ref.activeRoles().contains(STUDENT_ROLE)) {
            throw new EnrollmentException(EnrollmentException.Kind.STUDENT_NOT_FOUND);
        }
        return ref;
    }

    private Enrollment currentEnrollmentOf(long userInternalId) {
        List<Enrollment> all = enrollmentRepository.findByUserId(userInternalId);
        return pickCurrent(all);
    }

    /**
     * Résout, pour un lot de comptes, l'inscription « courante » de
     * chacun — celle {@code ACTIVE}, ou à défaut la plus récente
     * ({@code startDate} décroissant) — en <strong>une</strong> requête
     * (anti-N+1, NFR-PERF-08). Un compte sans aucune inscription est
     * simplement absent du résultat : ce n'est jamais une erreur.
     */
    private Map<Long, Enrollment> resolveCurrentEnrollments(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Enrollment>> byUser = enrollmentRepository.findByUserIdIn(userIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(Enrollment::getUserId));
        Map<Long, Enrollment> current = new HashMap<>();
        byUser.forEach((userId, enrollments) -> {
            Enrollment picked = pickCurrent(enrollments);
            if (picked != null) {
                current.put(userId, picked);
            }
        });
        return current;
    }

    private static Enrollment pickCurrent(List<Enrollment> enrollments) {
        return enrollments.stream().filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE).findFirst()
                .orElseGet(() -> enrollments.stream()
                        .max(Comparator.comparing(Enrollment::getStartDate))
                        .orElse(null));
    }

    private Map<Long, ClassGroupDirectory.ClassGroupRef> resolveClassRefs(java.util.Collection<Enrollment> enrollments) {
        List<Long> classIds = enrollments.stream().map(Enrollment::getClassGroupId).distinct().toList();
        if (classIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ClassGroupDirectory.ClassGroupRef> refs = new HashMap<>();
        for (ClassGroupDirectory.ClassGroupRef ref : classGroupDirectory.findByInternalIds(classIds)) {
            refs.put(ref.internalId(), ref);
        }
        return refs;
    }

    private static ClassGroupDirectory.ClassGroupRef classRefOf(Enrollment enrollment,
                                                                Map<Long, ClassGroupDirectory.ClassGroupRef> refs) {
        return enrollment == null ? null : refs.get(enrollment.getClassGroupId());
    }

    private static PageResponse<StudentResponse> emptyPage(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0, 0);
    }

    private static int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static String validateSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }
        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        if (!SORTABLE.contains(field)) {
            throw new EnrollmentException(EnrollmentException.Kind.INVALID_SORT);
        }
        if (parts.length == 2) {
            String direction = parts[1].trim().toLowerCase(Locale.ROOT);
            if (!direction.equals("asc") && !direction.equals("desc")) {
                throw new EnrollmentException(EnrollmentException.Kind.INVALID_SORT);
            }
        }
        return sort;
    }

    private static String validateStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(normalized)) {
            throw new EnrollmentException(EnrollmentException.Kind.INVALID_FILTER);
        }
        return normalized;
    }
}
