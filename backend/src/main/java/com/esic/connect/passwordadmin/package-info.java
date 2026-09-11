/**
 * Module « passwordadmin » — réinitialisation du mot de passe d'un tiers
 * par une personne habilitée (docs/02 §17.8 complété).
 *
 * <p>Un seul endpoint, {@code POST /api/v1/users/{publicId}/password-reset}
 * : déclenche pour le compte visé le même parcours que « mot de passe
 * oublié » ({@link com.esic.connect.identity.AdminPasswordResetDirectory}),
 * sans jamais transmettre ni afficher le mot de passe lui-même. L'appelant
 * ne choisit jamais le nouveau mot de passe — il ouvre seulement, pour
 * autrui, la même porte que la personne s'ouvrirait elle-même depuis
 * l'écran « mot de passe oublié ».
 *
 * <p><strong>Hiérarchie des rôles</strong> (RG-009 — protéger un rôle plus
 * élevé d'une action décidée par un rôle moindre) :
 * <ul>
 *   <li>{@code SUPER_ADMIN} — n'importe quel compte, sans restriction ;</li>
 *   <li>{@code ADMIN} / {@code SCHOOL_ADMINISTRATION} — tout compte dont
 *       aucun rôle actif n'est {@code SUPER_ADMIN} ni {@code ADMIN} (donc
 *       {@code PEDAGOGICAL_MANAGER}, {@code TEACHER}, {@code STUDENT}) ;</li>
 *   <li>{@code PEDAGOGICAL_MANAGER} — uniquement {@code TEACHER} et
 *       {@code STUDENT}, et seulement dans son périmètre effectif : la
 *       classe d'inscription active de l'apprenant, ou une classe
 *       réellement enseignée par le formateur, doit relever d'une
 *       formation dont il répond au jour courant (même décision que
 *       {@link com.esic.connect.academic.AcademicScopeDirectory}, déjà
 *       utilisée par {@code enrollment.RosterScopeResolver} pour le même
 *       calcul de périmètre).</li>
 * </ul>
 * Un compte hors de portée renvoie {@code 403} — jamais un contournement
 * silencieux.
 *
 * <p>Dépendances inter-modules limitées aux ports publics :
 * {@link com.esic.connect.identity.UserDirectory} (rôles actifs du
 * compte visé), {@link com.esic.connect.identity.AdminPasswordResetDirectory}
 * (déclenchement effectif), {@link com.esic.connect.academic.AcademicScopeDirectory}
 * (périmètre pédagogique de l'appelant), {@link com.esic.connect.academic.ClassGroupDirectory}
 * (résolution interne des classes), {@link com.esic.connect.enrollment.EnrollmentDirectory}
 * (classe d'inscription active d'un apprenant visé) et
 * {@link com.esic.connect.coursesession.CourseSessionDirectory} (classes
 * réellement enseignées par un formateur visé) — mêmes ports, pour le même
 * calcul de périmètre, que {@code enrollment.internal.RosterScopeResolver}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Réinitialisation de mot de passe (admin)")
package com.esic.connect.passwordadmin;
