package com.esic.connect.planning.internal;

/**
 * Périmètres d'autorisation des routes du module {@code planning}
 * (docs/03-architecture.md). Expressions SpEL
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

    /**
     * Convertit un identifiant public reçu en {@link java.util.UUID}.
     *
     * <p>Un identifiant mal formé ne désigne aucune ressource : il produit
     * le même refus qu'un identifiant inconnu, plutôt qu'une erreur de
     * format qui distinguerait les deux cas.
     */
    static java.util.UUID parseUuid(String value, PlanningException.Kind notFound) {
        try {
            return java.util.UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            throw new PlanningException(notFound);
        }
    }

    /** Sujet ({@code sub}) du JWT de l'appelant, ou {@code null}. */
    static String subject(org.springframework.security.oauth2.jwt.Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }
}
