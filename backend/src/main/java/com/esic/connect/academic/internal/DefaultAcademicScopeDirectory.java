package com.esic.connect.academic.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implémentation du port {@link AcademicScopeDirectory}. Reste confinée à
 * {@code academic.internal} : elle délègue toute la décision de périmètre
 * à {@link AcademicScopeGuard} (point unique de vérité, lit le contexte
 * Spring Security) et ne fait que traduire son résultat en réponse
 * neutre (booléens / ensembles d'identifiants) exploitable par un autre
 * module. Aucune logique de sécurité n'est dupliquée hors de ce module.
 */
@Component
class DefaultAcademicScopeDirectory implements AcademicScopeDirectory {

    private final AcademicScopeGuard scopeGuard;
    private final ClassGroupRepository classGroupRepository;

    DefaultAcademicScopeDirectory(AcademicScopeGuard scopeGuard, ClassGroupRepository classGroupRepository) {
        this.scopeGuard = scopeGuard;
        this.classGroupRepository = classGroupRepository;
    }

    @Override
    public boolean hasGlobalScope() {
        return scopeGuard.hasGlobalScope();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isClassInScope(UUID classGroupPublicId) {
        if (scopeGuard.hasGlobalScope()) {
            return true;
        }
        if (classGroupPublicId == null) {
            return false;
        }
        return classGroupRepository.findByPublicId(classGroupPublicId)
                .map(classGroup -> {
                    try {
                        scopeGuard.requireProgramInScope(classGroup.getPromotion().getProgram());
                        return true;
                    } catch (AcademicException outOfScope) {
                        return false;
                    }
                })
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Set<Long>> visibleClassGroupIds() {
        Set<Long> visiblePrograms = scopeGuard.visibleProgramIds();
        if (visiblePrograms == null) {
            return Optional.empty();
        }
        if (visiblePrograms.isEmpty()) {
            return Optional.of(Set.of());
        }
        return Optional.of(new HashSet<>(classGroupRepository.findIdsByProgramIdIn(visiblePrograms)));
    }
}
