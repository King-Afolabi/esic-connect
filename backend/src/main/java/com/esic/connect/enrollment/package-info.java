/**
 * Module « enrollment » (docs/03-architecture.md §7.3).
 *
 * <p>Inscriptions historiques ({@code enrollment}) reliant un apprenant —
 * un compte {@code user_account} porteur du rôle actif {@code STUDENT},
 * seule source de vérité du statut apprenant — à une classe pour une
 * année scolaire, avec conservation de l'historique lors d'un changement
 * de classe (docs/02-cahier-des-charges.md §7.6, §13 ;
 * docs/04-modele-donnees.md §13 ; RG-006, RG-012, RG-022, RG-023 ;
 * AC-006). Il n'existe plus de table {@code student_profile} (refonte
 * 2026-09, supprimée) : le numéro étudiant et la date de naissance sont
 * des colonnes de {@code user_account} (module {@code identity}) ; la
 * situation d'alternance ({@code work_study} / {@code company_name}) est
 * portée par {@code enrollment} elle-même. Ne couvre ni l'import CSV des
 * apprenants, ni les rythmes d'alternance, ni les apprenants provisoires,
 * ni Angular : ces domaines relèvent d'autres lots.
 *
 * <p>Règle centrale (docs/04 §13.3, RG-012) : un apprenant possède au
 * maximum une inscription {@code ACTIVE} pour une même année scolaire —
 * garantie par un pré-contrôle applicatif et par la contrainte SQL
 * {@code uq_enrollment_active_per_year} (colonnes générées). Un changement
 * de classe clôture l'inscription courante ({@code TRANSFERRED},
 * {@code end_date} renseigné, {@code previous_enrollment_id} sur la
 * nouvelle) sans jamais supprimer de ligne (docs/04 §13.2, §13.4).
 *
 * <p>Dépendances inter-modules limitées aux ports publics :
 * {@link com.esic.connect.identity.CurrentUserResolver} (auteur des
 * écritures), {@link com.esic.connect.identity.UserDirectory} (compte
 * cible d'une inscription, identité civile, numéro étudiant),
 * {@link com.esic.connect.academic.ClassGroupDirectory} (résolution de la
 * classe et de son année scolaire, sans partage d'entité JPA),
 * {@link com.esic.connect.academic.AcademicScopeDirectory} et
 * {@link com.esic.connect.coursesession.CourseSessionDirectory}
 * (périmètre de <em>consultation</em> des apprenants : un
 * {@code PEDAGOGICAL_MANAGER} ne voit que les classes de ses formations,
 * un {@code TEACHER} que les classes de ses séances — voir
 * {@code RosterScopeResolver}).
 * {@code enrollment.user_id}, {@code enrollment.class_group_id} et
 * {@code enrollment.academic_year_id} sont de simples valeurs techniques
 * (clés étrangères SQL). Publie
 * {@link com.esic.connect.enrollment.EnrollmentChangeEvent}, consommé par
 * le module {@code audit}, et expose les ports
 * {@link com.esic.connect.enrollment.EnrollmentDirectory} (consommé par
 * {@code alternation} et {@code attendance} pour rattacher une donnée à
 * une inscription ou un compte apprenant) et
 * {@link com.esic.connect.enrollment.StudentEnrollmentProvisioner}
 * (consommé par {@code studentimport} pour créer/transférer des
 * inscriptions <em>dans la transaction unique</em> de la confirmation
 * d'import — écriture directe, sans {@code EnrollmentPersister}, sans
 * événement ; le numéro étudiant / la date de naissance de l'import
 * passent, eux, par {@code identity.StudentAccountProvisioner}).
 * Aucun partage d'entité JPA. Les types d'implémentation résident dans
 * {@code enrollment.internal} et ne sont pas visibles des autres modules.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Enrollment")
package com.esic.connect.enrollment;
