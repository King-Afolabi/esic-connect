package com.esic.connect.enrollment.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Résout le périmètre de <strong>consultation</strong> des apprenants
 * pour l'appelant courant, sur la liste des profils apprenants
 * ({@code GET /api/v1/student-profiles}) et des inscriptions
 * ({@code GET /api/v1/enrollments}).
 *
 * <p>La décision n'est jamais prise à partir d'un paramètre client :
 * l'accès global vient des autorités Spring Security (via
 * {@link AcademicScopeDirectory}), le périmètre pédagogique vient des
 * affectations de responsabilité, le périmètre d'enseignement vient des
 * séances réellement confiées au formateur (via
 * {@link CourseSessionDirectory}).
 *
 * <ul>
 *   <li>{@code ADMIN} / {@code SUPER_ADMIN} / {@code SCHOOL_ADMINISTRATION}
 *       — accès global : {@link Optional#empty()}, aucun filtre ;</li>
 *   <li>{@code PEDAGOGICAL_MANAGER} — les classes des formations dont il
 *       répond au jour courant ;</li>
 *   <li>{@code TEACHER} — les classes rattachées à ses séances
 *       (formateur principal ou remplaçant {@code ACTIVE}) ;</li>
 *   <li>cumul de rôles — l'<strong>union</strong> des deux ensembles, sans
 *       jamais élargir au-delà ;</li>
 *   <li>ensemble vide — l'appelant ne voit aucun apprenant (page vide),
 *       jamais tous.</li>
 * </ul>
 *
 * Cahier §5.5 (« contrôle d'accès par périmètre »), §18.2-18.3 (une
 * ressource hors périmètre renvoie {@code 404}, le cumul de rôles
 * n'élargit jamais un périmètre).
 */
@Component
class RosterScopeResolver {

    private final AcademicScopeDirectory academicScope;
    private final CourseSessionDirectory courseSessions;
    private final ClassGroupDirectory classGroups;

    RosterScopeResolver(AcademicScopeDirectory academicScope,
                        CourseSessionDirectory courseSessions,
                        ClassGroupDirectory classGroups) {
        this.academicScope = academicScope;
        this.courseSessions = courseSessions;
        this.classGroups = classGroups;
    }

    /**
     * Identifiants internes des classes visibles par l'appelant.
     *
     * @param callerSubject sujet du JWT ({@code user_account.public_id}) —
     *                      utilisé pour le périmètre d'enseignement ;
     *                      {@code null} accepté (aucun apport formateur)
     * @return {@link Optional#empty()} si accès global (aucun filtre) ;
     *         sinon l'ensemble — éventuellement vide — des identifiants
     *         internes de classes visibles
     */
    Optional<Set<Long>> visibleClassGroupInternalIds(String callerSubject) {
        Optional<Set<Long>> managed = academicScope.visibleClassGroupIds();
        if (managed.isEmpty()) {
            return Optional.empty();
        }
        Set<Long> union = new HashSet<>(managed.get());
        addTaughtClasses(callerSubject, union);
        return Optional.of(union);
    }

    private void addTaughtClasses(String callerSubject, Set<Long> target) {
        if (callerSubject == null || callerSubject.isBlank()) {
            return;
        }
        UUID subject;
        try {
            subject = UUID.fromString(callerSubject.trim());
        } catch (IllegalArgumentException notAUuid) {
            return;
        }
        Set<UUID> taughtPublicIds = courseSessions.findTaughtClassGroupPublicIds(subject);
        if (taughtPublicIds.isEmpty()) {
            return;
        }
        classGroups.findByPublicIds(taughtPublicIds)
                .forEach(ref -> target.add(ref.internalId()));
    }
}
