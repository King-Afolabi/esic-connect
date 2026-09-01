/**
 * Module « dashboard » (bloc G1-F ; CDC §25 ; DEC-G1-010).
 *
 * <p>Un seul endpoint : {@code GET /api/v1/me/dashboard}. Renvoie un DTO
 * <strong>typé par le rôle effectif</strong> de l'appelant, déterminé
 * <strong>côté serveur</strong> à partir des rôles du JWT selon une
 * priorité fixe (jamais d'un paramètre client). Le module ne porte
 * aucune règle métier : il assemble des <strong>agrégats bornés</strong>
 * lus via les ports publics des autres modules
 * ({@code identity.AccountStatsDirectory},
 * {@code attendance.AttendanceDashboardDirectory},
 * {@code studentimport.StudentImportDashboardDirectory},
 * {@code coursesession.CourseSessionDirectory},
 * {@code enrollment.EnrollmentDirectory},
 * {@code academic.AcademicScopeDirectory} / {@code ClassGroupDirectory}).
 * Aucune entité JPA, aucun repository d'un autre module ; lecture seule.
 *
 * <p>Périmètre : {@code STUDENT} = ses seules données (AC-017) ;
 * {@code TEACHER} = ses séances ; {@code PEDAGOGICAL_MANAGER} = son
 * périmètre pédagogique ({@code AcademicScopeDirectory}) ;
 * administration = agrégats globaux autorisés, sans PII.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Dashboard")
package com.esic.connect.dashboard;
