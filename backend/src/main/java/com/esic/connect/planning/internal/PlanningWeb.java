package com.esic.connect.planning.internal;

/**
 * Périmètres d'autorisation des routes du module {@code planning}
 * (docs/reports/G1_IMPLEMENTATION_PLAN.md §4.6). Expressions SpEL
 * réutilisées par {@code @PreAuthorize}.
 *
 * <ul>
 *   <li>{@link #MANAGE_ROLES} : importer / simuler / revalider / annuler
 *       / publier un planning. {@code CDC §13.1} désigne le
 *       {@code PEDAGOGICAL_MANAGER} comme propriétaire du planning ;
 *       l'ouverture aux 3 rôles administratifs est une décision
 *       d'architecture {@code DEC-G1-B} (cohérence avec l'import
 *       apprenant). {@code RG-031} : le {@code TEACHER} ne publie
 *       jamais.</li>
 *   <li>{@link #READ_ROLES} : consulter les versions d'un planning
 *       (silence documentaire ; aligné sur {@code MANAGE_ROLES}).</li>
 * </ul>
 *
 * <p>Pour un {@code PEDAGOGICAL_MANAGER}, le périmètre effectif (classe
 * dans son périmètre) est décidé côté serveur via
 * {@link com.esic.connect.academic.AcademicScopeDirectory}, jamais d'un
 * paramètre client.
 */
final class PlanningWeb {

    static final String MANAGE_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER')";
    static final String READ_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER','TEACHER','STUDENT')";

    private PlanningWeb() {
    }

    /** Sujet ({@code sub}) du JWT de l'appelant, ou {@code null}. */
    static String subject(org.springframework.security.oauth2.jwt.Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }
}
