package com.esic.connect.enrollment.internal;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Profils apprenants (docs/04-modele-donnees.md §11.1). Gestion réservée à
 * {@code ADMIN}/{@code SUPER_ADMIN}/{@code SCHOOL_ADMINISTRATION} :
 * création et consultation. Aucune modification en place, aucune
 * suppression dans ce lot.
 *
 * <p>La cible d'un profil doit exister, ne pas être archivée et porter un
 * rôle actif {@code STUDENT} (sinon {@code ENR_USER_NOT_ELIGIBLE}),
 * vérifié via le port {@link UserDirectory}. Un seul profil par compte
 * ({@code ENR_PROFILE_EXISTS}) ; numéro étudiant unique
 * ({@code ENR_DUPLICATE_STUDENT_NUMBER}).
 *
 * <p>La création n'est pas transactionnelle : les lectures s'exécutent en
 * transactions implicites et l'insertion est déléguée à
 * {@link EnrollmentPersister} ({@code REQUIRES_NEW}). En cas de course
 * entre deux requêtes, la
 * {@link DataIntegrityViolationException} remonte <em>hors</em> de toute
 * transaction en échec ; elle n'est retraduite en 409 que si elle vise
 * {@code uq_student_profile_user} ou
 * {@code uq_student_profile_student_number}. Toute autre violation
 * d'intégrité est relancée telle quelle (500 via le gestionnaire global).
 */
@Service
class StudentProfileService {

    private static final String STUDENT_ROLE = "STUDENT";
    private static final Set<String> SORTABLE = Set.of("studentNumber", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final StudentProfileRepository profileRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentPersister persister;
    private final UserDirectory userDirectory;
    private final EnrollmentChangePublisher changePublisher;
    private final StudentNumberAllocator studentNumberAllocator;
    private final RosterScopeResolver rosterScope;

    StudentProfileService(StudentProfileRepository profileRepository,
                          EnrollmentRepository enrollmentRepository,
                          EnrollmentPersister persister,
                          UserDirectory userDirectory,
                          EnrollmentChangePublisher changePublisher,
                          StudentNumberAllocator studentNumberAllocator,
                          RosterScopeResolver rosterScope) {
        this.profileRepository = profileRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.persister = persister;
        this.userDirectory = userDirectory;
        this.changePublisher = changePublisher;
        this.studentNumberAllocator = studentNumberAllocator;
        this.rosterScope = rosterScope;
    }

    /**
     * Non transactionnel : voir la note de classe. L'insertion passe par
     * {@link EnrollmentPersister} ({@code REQUIRES_NEW}) et la
     * retraduction de collision se fait donc hors de toute transaction en
     * échec.
     */
    StudentProfileResponse create(StudentProfileRequests.Create request, String callerSubject) {
        UserDirectory.UserRef target = userDirectory.findByPublicId(parseUuid(request.userPublicId(),
                        EnrollmentException.Kind.USER_NOT_ELIGIBLE))
                .orElseThrow(() -> new EnrollmentException(EnrollmentException.Kind.USER_NOT_ELIGIBLE));
        if (target.archived() || !target.activeRoles().contains(STUDENT_ROLE)) {
            throw new EnrollmentException(EnrollmentException.Kind.USER_NOT_ELIGIBLE);
        }

        // Numéro étudiant : fourni -> contrôle d'unicité immédiat ;
        // laissé vide -> génération au format normalisé
        // ESIC-{année}-{séquence} pour garantir une norme de nommage
        // homogène (une saisie libre finissait par produire des numéros
        // hétérogènes). L'unicité SQL reste l'autorité finale.
        String studentNumber = EnrollmentQuerySupport.trimToNull(request.studentNumber());
        if (studentNumber == null) {
            studentNumber = allocateFreshStudentNumber();
        } else if (profileRepository.existsByStudentNumberIgnoreCase(studentNumber)) {
            throw new EnrollmentException(EnrollmentException.Kind.DUPLICATE_STUDENT_NUMBER);
        }
        if (profileRepository.existsByUserId(target.internalId())) {
            throw new EnrollmentException(EnrollmentException.Kind.PROFILE_ALREADY_EXISTS);
        }

        Long actorId = changePublisher.actorId(callerSubject);
        StudentProfile profile = new StudentProfile(target.internalId(), studentNumber, request.birthDate(),
                Boolean.TRUE.equals(request.workStudy()), EnrollmentQuerySupport.trimToNull(request.companyName()));
        profile.markCreatedBy(actorId);

        StudentProfile saved;
        try {
            saved = persister.persist(profile);
        } catch (DataIntegrityViolationException collision) {
            if (EnrollmentPersistence.isProfileUserUniqueViolation(collision)) {
                throw new EnrollmentException(EnrollmentException.Kind.PROFILE_ALREADY_EXISTS);
            }
            if (EnrollmentPersistence.isProfileStudentNumberUniqueViolation(collision)) {
                throw new EnrollmentException(EnrollmentException.Kind.DUPLICATE_STUDENT_NUMBER);
            }
            throw collision;
        }

        changePublisher.publish(EnrollmentResourceType.STUDENT_PROFILE, saved.getPublicId(),
                EnrollmentChangeAction.CREATED, actorId, null);
        UserDirectory.PersonName name = userDirectory.findName(target.internalId()).orElse(null);
        return StudentProfileResponse.from(saved, target.publicId(),
                name == null ? null : name.firstName(),
                name == null ? null : name.lastName());
    }

    @Transactional(readOnly = true)
    StudentProfileResponse get(UUID publicId, String callerSubject) {
        StudentProfile profile = require(publicId);
        // Périmètre de consultation : un PEDAGOGICAL_MANAGER / TEACHER ne
        // voit que les apprenants de ses classes. Hors périmètre ⇒ 404
        // (l'existence de la fiche est elle-même une information à
        // protéger — cahier §18.2), pas 403.
        rosterScope.visibleClassGroupInternalIds(callerSubject).ifPresent(visible -> {
            if (!hasActiveEnrollmentIn(profile, visible)) {
                throw new EnrollmentException(EnrollmentException.Kind.STUDENT_PROFILE_NOT_FOUND);
            }
        });
        UserDirectory.NamedUserRef ref = userDirectory.findNamedRefs(List.of(profile.getUserId()))
                .get(profile.getUserId());
        if (ref == null) {
            return StudentProfileResponse.from(profile, resolveUserPublicId(profile.getUserId()));
        }
        return StudentProfileResponse.from(profile, ref.publicId(), ref.firstName(), ref.lastName());
    }

    @Transactional(readOnly = true)
    PageResponse<StudentProfileResponse> list(String q, String statusFilter, String userPublicId,
                                              int page, int size, String sort, String callerSubject) {
        Pageable pageable = EnrollmentQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        List<Specification<StudentProfile>> specs = new ArrayList<>();
        // Périmètre de consultation : un PEDAGOGICAL_MANAGER / TEACHER ne
        // voit que les apprenants ayant une inscription ACTIVE dans l'une
        // de ses classes ; l'administration a l'accès global (aucun
        // filtre). Périmètre vide ⇒ page vide, jamais tous.
        Optional<java.util.Set<Long>> visibleClasses =
                rosterScope.visibleClassGroupInternalIds(callerSubject);
        if (visibleClasses.isPresent()) {
            // L'inscription rattache un compte, pas un profil (refonte
            // 2026-09) : on résout d'abord les comptes visibles, puis les
            // profils correspondants — un compte visible sans profil ne
            // produit simplement aucun identifiant ici, sans erreur.
            List<Long> scopedUserIds = visibleClasses.get().isEmpty()
                    ? List.of()
                    : enrollmentRepository.findUserIdsByClassGroupIdInAndStatus(
                            visibleClasses.get(), EnrollmentStatus.ACTIVE);
            List<Long> scopedProfileIds = scopedUserIds.isEmpty()
                    ? List.of()
                    : profileRepository.findIdsByUserIdIn(scopedUserIds);
            if (scopedProfileIds.isEmpty()) {
                return PageResponse.of(Page.<StudentProfile>empty(pageable),
                        profile -> StudentProfileResponse.from(profile, null));
            }
            specs.add(EnrollmentSpecifications.profileIdIn(scopedProfileIds));
        }
        // Recherche : numéro étudiant OU nom / prénom. Le nom n'est pas
        // une colonne de `student_profile` — `identity` résout d'abord les
        // comptes STUDENT dont le nom correspond (borné à 200), puis le
        // filtre porte sur `student_number LIKE … OR user_id IN (…)`.
        // L'adresse électronique reste exclue (énumération, RG-001).
        EnrollmentQuerySupport.normalizeText(q).ifPresent(text -> {
            // Inclut les apprenants encore en attente d'activation :
            // l'administration doit retrouver par le nom un apprenant
            // fraîchement importé ou créé.
            List<Long> nameHits = userDirectory.searchByNameIncludingInactive(text, STUDENT_ROLE, 200).stream()
                    .map(UserDirectory.NamedUserRef::internalId)
                    .toList();
            specs.add(EnrollmentSpecifications.profileMatchesNumberOrUsers(text, nameHits));
        });
        parseStatus(statusFilter).ifPresent(status -> specs.add(EnrollmentSpecifications.profileHasStatus(status)));
        if (userPublicId != null && !userPublicId.isBlank()) {
            Optional<UserDirectory.UserRef> user = userDirectory.findByPublicId(parseUuid(userPublicId,
                    EnrollmentException.Kind.USER_NOT_ELIGIBLE));
            if (user.isEmpty()) {
                return PageResponse.of(Page.<StudentProfile>empty(pageable),
                        profile -> StudentProfileResponse.from(profile, null));
            }
            specs.add(EnrollmentSpecifications.profileHasUser(user.get().internalId()));
        }
        Page<StudentProfile> result = profileRepository.findAll(Specification.allOf(specs), pageable);
        // Noms + identifiant public résolus en UNE requête pour toute la
        // page (anti-N+1, NFR-PERF-08) — remplace la résolution unitaire
        // par ligne de `resolveUserPublicId`.
        java.util.Map<Long, UserDirectory.NamedUserRef> refs = userDirectory.findNamedRefs(
                result.getContent().stream().map(StudentProfile::getUserId).toList());
        return PageResponse.of(result, profile -> {
            UserDirectory.NamedUserRef ref = refs.get(profile.getUserId());
            return ref == null
                    ? StudentProfileResponse.from(profile, null)
                    : StudentProfileResponse.from(profile, ref.publicId(), ref.firstName(), ref.lastName());
        });
    }

    /**
     * Numéro généré, avec quelques tentatives si la valeur allouée entre
     * en collision avec un numéro déjà saisi manuellement (rare : la
     * séquence est propre, le recouvrement ne peut venir que d'une saisie
     * libre passée). L'unicité SQL reste l'autorité.
     */
    private String allocateFreshStudentNumber() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = studentNumberAllocator.allocate();
            if (!profileRepository.existsByStudentNumberIgnoreCase(candidate)) {
                return candidate;
            }
        }
        throw new EnrollmentException(EnrollmentException.Kind.STUDENT_NUMBER_EXHAUSTED);
    }

    private boolean hasActiveEnrollmentIn(StudentProfile profile, java.util.Set<Long> classGroupIds) {
        if (classGroupIds.isEmpty()) {
            return false;
        }
        return enrollmentRepository
                .findByUserIdAndStatus(profile.getUserId(), EnrollmentStatus.ACTIVE)
                .stream()
                .anyMatch(enrollment -> classGroupIds.contains(enrollment.getClassGroupId()));
    }

    private UUID resolveUserPublicId(Long userInternalId) {
        return userDirectory.findByInternalId(userInternalId)
                .map(UserDirectory.UserRef::publicId)
                .orElseThrow(() -> new EnrollmentException(EnrollmentException.Kind.USER_NOT_ELIGIBLE));
    }

    private StudentProfile require(UUID publicId) {
        return profileRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EnrollmentException(EnrollmentException.Kind.STUDENT_PROFILE_NOT_FOUND));
    }

    private static Optional<StudentProfileStatus> parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(StudentProfileStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
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
