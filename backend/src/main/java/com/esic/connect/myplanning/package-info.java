/**
 * Module « myplanning » (CDC §5.6/§5.7 : « voir son planning »).
 *
 * <p>Un seul endpoint, {@code GET /api/v1/me/planning} : les séances de
 * l'appelant sur son <strong>seul</strong> périmètre propre, décidé
 * côté serveur à partir du rôle effectif du JWT — jamais d'un paramètre
 * client. Comble un manque constaté en recette : le module
 * {@code coursesession} exclut {@code STUDENT} de toutes ses routes
 * ({@code CourseSessionWeb} : « STUDENT n'a aucun accès à ces routes »)
 * et un {@code TEACHER} n'avait, jusqu'ici, qu'un flux iCalendar externe
 * ({@code integration.CalendarFeedService}) pour consulter son planning
 * dans l'application elle-même.
 *
 * <ul>
 *   <li>{@code TEACHER} : ses séances (formateur principal ou remplaçant
 *       {@code ACTIVE}), via
 *       {@link com.esic.connect.coursesession.CourseSessionDirectory#findTeacherSchedule} ;</li>
 *   <li>{@code STUDENT} : les séances des classes où il a une inscription
 *       {@code ACTIVE} (mêmes classes que le tableau de bord apprenant),
 *       via {@link com.esic.connect.enrollment.EnrollmentDirectory
 *       #findActiveEnrollmentsForUserOn} puis
 *       {@link com.esic.connect.coursesession.CourseSessionDirectory#findClassSchedule}.</li>
 * </ul>
 *
 * <p>Ni l'un ni l'autre port ne réalise de contrôle d'accès de
 * l'appelant : c'est ce module qui choisit, à partir du rôle effectif
 * (jamais d'un identifiant fourni par le client), quel périmètre
 * interroger — même principe que {@code dashboard}, dont ce module
 * reprend exactement les dépendances de lecture ({@code
 * CourseSessionDirectory}, {@code EnrollmentDirectory}, {@code
 * academic.ClassGroupDirectory}, {@code identity.UserDirectory}).
 * Aucune entité JPA, aucun repository d'un autre module ; lecture seule.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Mon planning")
package com.esic.connect.myplanning;
