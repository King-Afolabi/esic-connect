package com.esic.connect.academic.internal;

import com.esic.connect.identity.CurrentUserResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Point unique de décision du périmètre pédagogique pour l'ensemble du
 * référentiel académique (formation, niveau, promotion, classe).
 *
 * <p>Accès <em>global</em> (voit et écrit tout) : tout appelant portant
 * l'une des autorités {@code ROLE_ADMIN}, {@code ROLE_SUPER_ADMIN} ou
 * {@code ROLE_SCHOOL_ADMINISTRATION} — déduites du contexte Spring
 * Security, jamais d'un paramètre client. Sinon (typiquement un
 * {@code PEDAGOGICAL_MANAGER} seul, ou cumulé avec {@code TEACHER}),
 * l'accès est limité aux formations sur lesquelles il détient une
 * affectation {@code ACTIVE} dont la période (bornes inclusives) couvre
 * le jour courant.
 */
@Component
class AcademicScopeGuard {

    private static final Set<String> GLOBAL_AUTHORITIES =
            Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_SCHOOL_ADMINISTRATION");

    private final PedagogicalAssignmentRepository assignmentRepository;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;

    AcademicScopeGuard(PedagogicalAssignmentRepository assignmentRepository,
                       CurrentUserResolver currentUserResolver,
                       Clock clock) {
        this.assignmentRepository = assignmentRepository;
        this.currentUserResolver = currentUserResolver;
        this.clock = clock;
    }

    boolean hasGlobalScope() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (GLOBAL_AUTHORITIES.contains(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return {@code null} si l'appelant a l'accès global (aucun filtrage
     *         à appliquer) ; sinon l'ensemble — éventuellement vide — des
     *         identifiants internes de formations visibles.
     */
    Set<Long> visibleProgramIds() {
        if (hasGlobalScope()) {
            return null;
        }
        Long callerId = resolveCallerId();
        if (callerId == null) {
            return Set.of();
        }
        List<Long> ids = assignmentRepository.findScopedProgramIds(
                callerId, PedagogicalAssignmentStatus.ACTIVE, LocalDate.now(clock));
        return Set.copyOf(ids);
    }

    /**
     * Exige que la formation soit dans le périmètre effectif de
     * l'appelant. Ne fait rien pour un accès global ; sinon lève
     * {@link AcademicException.Kind#OUT_OF_SCOPE} (403 {@code ACAD_FORBIDDEN}).
     */
    void requireProgramInScope(Program program) {
        if (hasGlobalScope()) {
            return;
        }
        Long callerId = resolveCallerId();
        if (callerId == null || program == null || !assignmentRepository.existsEffectiveScope(
                program.getId(), callerId, PedagogicalAssignmentStatus.ACTIVE, LocalDate.now(clock))) {
            throw new AcademicException(AcademicException.Kind.OUT_OF_SCOPE);
        }
    }

    private Long resolveCallerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return currentUserResolver.resolveInternalId(authentication.getName()).orElse(null);
    }
}
