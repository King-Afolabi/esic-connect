package com.esic.connect.planning.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.identity.TeacherDirectory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Résolution des références inter-modules pour l'import de planning, via
 * <strong>ports publics uniquement</strong> ({@code DEC-G1-001}) :
 * {@link ClassGroupDirectory} (classe ↔ année),
 * {@link AcademicScopeDirectory} (périmètre pédagogique de l'appelant),
 * {@link TeacherDirectory} (formateur éligible par identifiant public).
 *
 * <p>Aucune décision de sécurité n'est dupliquée : le périmètre est
 * décidé côté {@code academic} à partir du contexte Spring Security.
 */
@Component
class PlanningReferenceResolver {

    private final ClassGroupDirectory classGroupDirectory;
    private final AcademicScopeDirectory academicScopeDirectory;
    private final TeacherDirectory teacherDirectory;

    PlanningReferenceResolver(ClassGroupDirectory classGroupDirectory,
                              AcademicScopeDirectory academicScopeDirectory,
                              TeacherDirectory teacherDirectory) {
        this.classGroupDirectory = classGroupDirectory;
        this.academicScopeDirectory = academicScopeDirectory;
        this.teacherDirectory = teacherDirectory;
    }

    /**
     * Résout la classe cible d'un import et vérifie qu'elle est dans le
     * périmètre de l'appelant courant.
     *
     * @throws PlanningException {@link PlanningException.Kind#TARGET_UNRESOLVED}
     *         si la classe est inconnue, {@link PlanningException.Kind#SCOPE_FORBIDDEN}
     *         si elle est hors périmètre
     */
    ResolvedTarget resolveTarget(UUID classGroupPublicId) {
        ClassGroupDirectory.ClassGroupRef ref = classGroupDirectory.findByPublicId(classGroupPublicId)
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.TARGET_UNRESOLVED));
        if (!academicScopeDirectory.isClassInScope(classGroupPublicId)) {
            throw new PlanningException(PlanningException.Kind.SCOPE_FORBIDDEN);
        }
        return new ResolvedTarget(ref.internalId(), ref.publicId(), ref.code(),
                ref.academicYearInternalId(), ref.academicYearPublicId(), ref.academicYearCode());
    }

    /**
     * Résout un formateur par identifiant public. {@link Optional#empty()}
     * si l'identifiant est mal formé, inconnu, ou le compte non éligible
     * (inactif / sans rôle {@code TEACHER} actif).
     */
    Optional<TeacherDirectory.TeacherRef> resolveTeacher(String teacherPublicIdRaw) {
        if (teacherPublicIdRaw == null || teacherPublicIdRaw.isBlank()) {
            return Optional.empty();
        }
        UUID id;
        try {
            id = UUID.fromString(teacherPublicIdRaw.trim());
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
        return teacherDirectory.findEligibleTeacher(id);
    }

    /**
     * @param classInternalId       clé SQL de la classe (interne au module)
     * @param classPublicId         identifiant public de la classe
     * @param classCode             code fonctionnel de la classe
     * @param academicYearInternalId clé SQL de l'année scolaire
     * @param academicYearPublicId   identifiant public de l'année
     * @param academicYearCode       code de l'année
     */
    record ResolvedTarget(
            long classInternalId,
            UUID classPublicId,
            String classCode,
            long academicYearInternalId,
            UUID academicYearPublicId,
            String academicYearCode) {
    }
}
