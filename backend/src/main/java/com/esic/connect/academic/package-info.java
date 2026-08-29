/**
 * Module « academic » (docs/03-architecture.md §7.2).
 *
 * <p>Référentiel académique minimal : formations ({@code program}),
 * niveaux ({@code program_level}), années scolaires
 * ({@code academic_year}), promotions ({@code promotion}) et
 * classes/groupes ({@code class_group}) — hiérarchie
 * formation → promotion → classe (docs/04-modele-donnees.md §12). Ne
 * couvre ni les inscriptions, ni les matières : ces domaines relèvent
 * d'autres modules.
 *
 * <p>Le module couvre aussi le périmètre pédagogique
 * ({@code pedagogical_assignment}, RG-004/RG-010/RG-011) : affectation
 * d'un responsable pédagogique à une formation et contrôle d'accès
 * centralisé ({@link com.esic.connect.academic.internal.AcademicScopeGuard})
 * limitant, pour un {@code PEDAGOGICAL_MANAGER} sans rôle global,
 * la lecture et l'écriture de la formation, du niveau, de la promotion et
 * de la classe à son périmètre effectif.
 *
 * <p>Dépendances inter-modules limitées aux ports publics : les ports
 * {@link com.esic.connect.identity.CurrentUserResolver} (auteur des
 * écritures) et {@link com.esic.connect.identity.UserDirectory} (cible
 * d'une affectation de responsable pédagogique), et le port
 * {@link com.esic.connect.organization.SiteDirectory} (rattachement d'une
 * classe à un site, sans partage d'entité JPA).
 * Publie {@link com.esic.connect.academic.AcademicChangeEvent}, consommé
 * par le module {@code audit}. Les types d'implémentation résident dans
 * {@code academic.internal} et ne sont pas visibles des autres modules.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Academic")
package com.esic.connect.academic;
