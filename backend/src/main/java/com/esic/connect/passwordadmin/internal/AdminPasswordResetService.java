package com.esic.connect.passwordadmin.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.esic.connect.identity.AdminPasswordResetDirectory;
import com.esic.connect.identity.UserDirectory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Décide si l'appelant peut déclencher la réinitialisation du mot de
 * passe du compte visé, puis délègue l'action elle-même à
 * {@link AdminPasswordResetDirectory} (module {@code identity}).
 *
 * <p>Hiérarchie (RG-009, docs/passwordadmin) — voir le
 * {@code package-info} du module pour le détail : {@code SUPER_ADMIN}
 * sans restriction ; {@code ADMIN} / {@code SCHOOL_ADMINISTRATION} tout
 * compte sans rôle {@code SUPER_ADMIN} ni {@code ADMIN} ;
 * {@code PEDAGOGICAL_MANAGER} uniquement {@code TEACHER} /
 * {@code STUDENT} de son périmètre effectif.
 */
@Service
class AdminPasswordResetService {

    /** Rôles qu'un {@code ADMIN} / {@code SCHOOL_ADMINISTRATION} ne peut jamais viser. */
    private static final Set<String> OUT_OF_REACH_FOR_UPPER_MANAGEMENT = Set.of("SUPER_ADMIN", "ADMIN");

    /** Seuls rôles qu'un {@code PEDAGOGICAL_MANAGER} peut viser. */
    private static final Set<String> REACHABLE_BY_PEDAGOGICAL_MANAGER = Set.of("TEACHER", "STUDENT");

    private final UserDirectory users;
    private final AdminPasswordResetDirectory resetDirectory;
    private final AcademicScopeDirectory academicScope;
    private final ClassGroupDirectory classGroups;
    private final EnrollmentDirectory enrollments;
    private final CourseSessionDirectory courseSessions;

    AdminPasswordResetService(UserDirectory users,
                              AdminPasswordResetDirectory resetDirectory,
                              AcademicScopeDirectory academicScope,
                              ClassGroupDirectory classGroups,
                              EnrollmentDirectory enrollments,
                              CourseSessionDirectory courseSessions) {
        this.users = users;
        this.resetDirectory = resetDirectory;
        this.academicScope = academicScope;
        this.classGroups = classGroups;
        this.enrollments = enrollments;
        this.courseSessions = courseSessions;
    }

    void triggerReset(UUID targetUserPublicId, String callerSubject, List<String> callerRoles) {
        UserDirectory.UserRef target = users.findByPublicId(targetUserPublicId)
                .orElseThrow(AdminPasswordResetException::userNotFound);
        if (target.archived()) {
            throw AdminPasswordResetException.userNotFound();
        }

        assertHierarchyAllowed(callerRoles, target.activeRoles());
        if (isPedagogicalManagerOnly(callerRoles)) {
            assertInPedagogicalScope(targetUserPublicId, target.activeRoles());
        }

        AdminPasswordResetDirectory.Outcome outcome = resetDirectory.triggerReset(targetUserPublicId);
        switch (outcome) {
            case USER_NOT_FOUND -> throw AdminPasswordResetException.userNotFound();
            case NOT_ELIGIBLE -> throw AdminPasswordResetException.notEligible();
            case RESET_SENT -> { /* succès */ }
        }
    }

    /**
     * Un {@code SUPER_ADMIN} n'est jamais restreint. Un {@code ADMIN} /
     * {@code SCHOOL_ADMINISTRATION} ne peut viser un compte que si aucun
     * de ses rôles actifs n'est {@code SUPER_ADMIN} ni {@code ADMIN}. Un
     * {@code PEDAGOGICAL_MANAGER} (sans les rôles précédents) ne peut
     * viser qu'un compte dont tous les rôles actifs sont
     * {@code TEACHER} et/ou {@code STUDENT}.
     */
    private void assertHierarchyAllowed(List<String> callerRoles, Set<String> targetRoles) {
        if (callerRoles.contains("SUPER_ADMIN")) {
            return;
        }
        boolean callerIsUpperManagement = callerRoles.contains("ADMIN") || callerRoles.contains("SCHOOL_ADMINISTRATION");
        if (callerIsUpperManagement) {
            boolean targetOutOfReach = targetRoles.stream().anyMatch(OUT_OF_REACH_FOR_UPPER_MANAGEMENT::contains);
            if (targetOutOfReach) {
                throw AdminPasswordResetException.forbidden();
            }
            return;
        }
        if (callerRoles.contains("PEDAGOGICAL_MANAGER")) {
            boolean targetOutOfReach = targetRoles.stream().anyMatch(role -> !REACHABLE_BY_PEDAGOGICAL_MANAGER.contains(role));
            if (targetOutOfReach) {
                throw AdminPasswordResetException.forbidden();
            }
            return;
        }
        // Aucun des rôles habilités : ne devrait pas arriver, le
        // @PreAuthorize du contrôleur filtre déjà — filet de sécurité.
        throw AdminPasswordResetException.forbidden();
    }

    private boolean isPedagogicalManagerOnly(List<String> callerRoles) {
        return callerRoles.contains("PEDAGOGICAL_MANAGER")
                && !callerRoles.contains("SUPER_ADMIN")
                && !callerRoles.contains("ADMIN")
                && !callerRoles.contains("SCHOOL_ADMINISTRATION");
    }

    /**
     * Périmètre du responsable pédagogique : même calcul que
     * {@code enrollment.internal.RosterScopeResolver}, appliqué ici à un
     * compte cible plutôt qu'à un filtre de liste. La classe
     * d'inscription active de l'apprenant, ou une classe réellement
     * enseignée par le formateur, doit relever d'une formation dont
     * l'appelant répond au jour courant.
     */
    private void assertInPedagogicalScope(UUID targetUserPublicId, Set<String> targetRoles) {
        Optional<Set<Long>> visible = academicScope.visibleClassGroupIds();
        if (visible.isEmpty()) {
            // Accès global — ne devrait pas se produire pour un
            // PEDAGOGICAL_MANAGER pur, mais aucune restriction à
            // appliquer dans ce cas.
            return;
        }
        Set<Long> visibleInternalIds = visible.get();

        Set<UUID> targetClassPublicIds = new HashSet<>();
        if (targetRoles.contains("STUDENT")) {
            enrollments.findEnrollmentsForUser(targetUserPublicId).stream()
                    .filter(EnrollmentDirectory.EnrollmentRef::usable)
                    .map(EnrollmentDirectory.EnrollmentRef::classGroupPublicId)
                    .forEach(targetClassPublicIds::add);
        }
        if (targetRoles.contains("TEACHER")) {
            targetClassPublicIds.addAll(courseSessions.findTaughtClassGroupPublicIds(targetUserPublicId));
        }
        if (targetClassPublicIds.isEmpty()) {
            throw AdminPasswordResetException.forbidden();
        }

        boolean inScope = classGroups.findByPublicIds(targetClassPublicIds).stream()
                .map(ClassGroupDirectory.ClassGroupRef::internalId)
                .anyMatch(visibleInternalIds::contains);
        if (!inScope) {
            throw AdminPasswordResetException.forbidden();
        }
    }
}
