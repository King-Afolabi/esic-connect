package com.esic.connect.enrollment.internal;

import com.esic.connect.enrollment.EnrollmentChangeAction;
import com.esic.connect.enrollment.EnrollmentResourceType;
import com.esic.connect.identity.UserDirectory;
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
 * ({@code ENR_DUPLICATE_STUDENT_NUMBER}). Les collisions concurrentes sur
 * ces contraintes sont retraduites en 409 par
 * {@link EnrollmentExceptionHandler}.
 */
@Service
class StudentProfileService {

    private static final String STUDENT_ROLE = "STUDENT";
    private static final Set<String> SORTABLE = Set.of("studentNumber", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final StudentProfileRepository profileRepository;
    private final UserDirectory userDirectory;
    private final EnrollmentChangePublisher changePublisher;

    StudentProfileService(StudentProfileRepository profileRepository,
                          UserDirectory userDirectory,
                          EnrollmentChangePublisher changePublisher) {
        this.profileRepository = profileRepository;
        this.userDirectory = userDirectory;
        this.changePublisher = changePublisher;
    }

    @Transactional
    StudentProfileResponse create(StudentProfileRequests.Create request, String callerSubject) {
        UserDirectory.UserRef target = userDirectory.findByPublicId(parseUuid(request.userPublicId(),
                        EnrollmentException.Kind.USER_NOT_ELIGIBLE))
                .orElseThrow(() -> new EnrollmentException(EnrollmentException.Kind.USER_NOT_ELIGIBLE));
        if (target.archived() || !target.activeRoles().contains(STUDENT_ROLE)) {
            throw new EnrollmentException(EnrollmentException.Kind.USER_NOT_ELIGIBLE);
        }

        String studentNumber = request.studentNumber().trim();
        if (profileRepository.existsByStudentNumberIgnoreCase(studentNumber)) {
            throw new EnrollmentException(EnrollmentException.Kind.DUPLICATE_STUDENT_NUMBER);
        }
        if (profileRepository.existsByUserId(target.internalId())) {
            throw new EnrollmentException(EnrollmentException.Kind.PROFILE_ALREADY_EXISTS);
        }

        Long actorId = changePublisher.actorId(callerSubject);
        StudentProfile profile = new StudentProfile(target.internalId(), studentNumber, request.birthDate(),
                Boolean.TRUE.equals(request.workStudy()), EnrollmentQuerySupport.trimToNull(request.companyName()));
        profile.markCreatedBy(actorId);
        StudentProfile saved = profileRepository.saveAndFlush(profile);

        changePublisher.publish(EnrollmentResourceType.STUDENT_PROFILE, saved.getPublicId(),
                EnrollmentChangeAction.CREATED, actorId, null);
        return StudentProfileResponse.from(saved, target.publicId());
    }

    @Transactional(readOnly = true)
    StudentProfileResponse get(UUID publicId) {
        StudentProfile profile = require(publicId);
        return StudentProfileResponse.from(profile, resolveUserPublicId(profile.getUserId()));
    }

    @Transactional(readOnly = true)
    PageResponse<StudentProfileResponse> list(String q, String statusFilter, String userPublicId,
                                              int page, int size, String sort) {
        Pageable pageable = EnrollmentQuerySupport.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        List<Specification<StudentProfile>> specs = new ArrayList<>();
        EnrollmentQuerySupport.normalizeText(q)
                .ifPresent(text -> specs.add(EnrollmentSpecifications.profileMatchesStudentNumber(text)));
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
        return PageResponse.of(result, profile ->
                StudentProfileResponse.from(profile, resolveUserPublicId(profile.getUserId())));
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
