/**
 * Module « planning » — import CSV, versionnement et publication d'un
 * planning de classe (docs/02-cahier-des-charges.md §13 ;
 * docs/04-modele-donnees.md §17-18 ;
 * docs/reports/G1_ARCHITECTURE_DECISIONS.md DEC-G1-001..006, 012 ;
 * EF-PLAN-001..007, EF-SES-001 ; RG-016, RG-030..RG-035 ; AC-007, AC-008 ;
 * branche feature/master-level-product-expansion, bloc G1-B).
 *
 * <p><strong>État (checkpoint G1-B / schéma + modèle)</strong> : schéma
 * {@code V12} (sept tables propres au module) + {@code V13} (lien additif
 * {@code course_session ↔ planning_entry}), entités JPA et repositories,
 * socle interne (rôles {@code @PreAuthorize} de {@link
 * com.esic.connect.planning.internal.PlanningWeb}, erreur métier
 * {@code PLAN_*} de {@link com.esic.connect.planning.internal.PlanningException}
 * + handler, codes d'anomalie, configuration typée / validée). Le parsing
 * CSV, la simulation, la publication transactionnelle, les endpoints REST
 * et les écrans Angular relèvent des checkpoints suivants.
 *
 * <p>Frontières inter-modules : uniquement par ports publics —
 * {@link com.esic.connect.identity.CurrentUserResolver} (auteur des
 * écritures), {@link com.esic.connect.identity.TeacherDirectory}
 * (résolution d'un formateur par identifiant public),
 * {@link com.esic.connect.academic.ClassGroupDirectory} (résolution
 * classe ↔ année depuis des codes fonctionnels),
 * {@link com.esic.connect.academic.AcademicScopeDirectory} (périmètre
 * pédagogique d'un {@code PEDAGOGICAL_MANAGER}). La publication appellera
 * — synchronement, dans sa transaction — le port d'écriture
 * {@link com.esic.connect.coursesession.PlanningSessionWriter} exposé par
 * {@code coursesession} (DEC-G1-001), sans partage d'entité JPA. Les
 * types d'implémentation résident dans {@code planning.internal}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Planning")
package com.esic.connect.planning;
