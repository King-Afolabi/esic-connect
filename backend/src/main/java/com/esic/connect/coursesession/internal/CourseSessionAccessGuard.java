package com.esic.connect.coursesession.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory.AccessLevel;
import com.esic.connect.identity.CurrentUserResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Point unique de décision du contrôle d'accès fin aux séances. Lit le
 * contexte Spring Security (jamais un paramètre client), sur le modèle de
 * {@code academic.internal.AcademicScopeGuard}.
 *
 * <ul>
 *   <li>{@code ADMIN} / {@code SUPER_ADMIN} : lecture et gestion de toute
 *       séance ;</li>
 *   <li>{@code SCHOOL_ADMINISTRATION} : lecture de toute séance,
 *       <strong>aucune</strong> gestion (lecture seule dans cette
 *       tranche) ;</li>
 *   <li>{@code PEDAGOGICAL_MANAGER} : lecture et gestion si au moins une
 *       classe de la séance relève de son périmètre effectif
 *       ({@link AcademicScopeDirectory#isClassInScope}) ;</li>
 *   <li>{@code TEACHER} : lecture et gestion uniquement s'il est le
 *       formateur de la séance ;</li>
 *   <li>tout autre rôle : aucun accès.</li>
 * </ul>
 */
@Component
class CourseSessionAccessGuard {

    private final CurrentUserResolver currentUserResolver;
    private final AcademicScopeDirectory academicScope;

    CourseSessionAccessGuard(CurrentUserResolver currentUserResolver,
                             AcademicScopeDirectory academicScope) {
        this.currentUserResolver = currentUserResolver;
        this.academicScope = academicScope;
    }

    /**
     * @param teacherUserId        formateur de la séance (identifiant interne)
     * @param classGroupPublicIds  classes rattachées à la séance
     * @param level                niveau d'accès demandé
     * @param callerSubject        sujet du JWT de l'appelant
     * @return {@code true} si l'accès est accordé
     */
    boolean isAllowed(long teacherUserId, Set<UUID> classGroupPublicIds, AccessLevel level,
                      String callerSubject) {
        Set<String> roles = authorities();
        boolean adminLike = roles.contains("ROLE_ADMIN") || roles.contains("ROLE_SUPER_ADMIN");
        if (adminLike) {
            return true;
        }
        if (roles.contains("ROLE_SCHOOL_ADMINISTRATION")) {
            return level == AccessLevel.READ;
        }
        if (roles.contains("ROLE_PEDAGOGICAL_MANAGER")) {
            return academicScope.hasGlobalScope()
                    || classGroupPublicIds.stream().anyMatch(academicScope::isClassInScope);
        }
        if (roles.contains("ROLE_TEACHER")) {
            return currentUserResolver.resolveInternalId(callerSubject)
                    .map(internalId -> internalId == teacherUserId)
                    .orElse(false);
        }
        return false;
    }

    /** Vrai si l'appelant a un accès global en lecture (filtrage de liste inutile). */
    boolean hasGlobalReadScope() {
        Set<String> roles = authorities();
        return roles.contains("ROLE_ADMIN") || roles.contains("ROLE_SUPER_ADMIN")
                || roles.contains("ROLE_SCHOOL_ADMINISTRATION");
    }

    /** Vrai si l'appelant est un formateur sans rôle d'administration ni de gestion pédagogique. */
    boolean isTeacherOnly() {
        Set<String> roles = authorities();
        return roles.contains("ROLE_TEACHER")
                && !roles.contains("ROLE_ADMIN") && !roles.contains("ROLE_SUPER_ADMIN")
                && !roles.contains("ROLE_SCHOOL_ADMINISTRATION")
                && !roles.contains("ROLE_PEDAGOGICAL_MANAGER");
    }

    boolean isPedagogicalManagerScoped() {
        Set<String> roles = authorities();
        return roles.contains("ROLE_PEDAGOGICAL_MANAGER") && !hasGlobalReadScope();
    }

    java.util.Optional<Long> callerInternalId(String callerSubject) {
        return currentUserResolver.resolveInternalId(callerSubject);
    }

    private static Set<String> authorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }
}
