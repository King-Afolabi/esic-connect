/**
 * Module « academic » (docs/03-architecture.md §7.2).
 *
 * <p>Référentiel académique minimal : formations ({@code program}),
 * niveaux ({@code program_level}), années scolaires
 * ({@code academic_year}), promotions ({@code promotion}) et
 * classes/groupes ({@code class_group}) — hiérarchie
 * formation → promotion → classe (docs/04-modele-donnees.md §12). Ne
 * couvre ni les inscriptions, ni les matières, ni les responsabilités
 * pédagogiques (périmètre) : ces domaines relèvent d'autres modules.
 *
 * <p>Dépendances inter-modules limitées aux ports publics : le port
 * {@link com.esic.connect.identity.CurrentUserResolver} (auteur des
 * écritures) et le port {@link com.esic.connect.organization.SiteDirectory}
 * (rattachement d'une classe à un site, sans partage d'entité JPA).
 * Publie {@link com.esic.connect.academic.AcademicChangeEvent}, consommé
 * par le module {@code audit}. Les types d'implémentation résident dans
 * {@code academic.internal} et ne sont pas visibles des autres modules.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Academic")
package com.esic.connect.academic;
