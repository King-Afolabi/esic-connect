/**
 * Module « attendance » (branche feature/attendance-qr-demonstration).
 *
 * <p>Émargement d'une séance ouverte via un jeton dynamique opaque ou un
 * code court, tous deux émis et validés par le serveur, conservés
 * <strong>uniquement dans Redis</strong> avec une durée de vie courte
 * (jamais en base MySQL, jamais dans une URL, jamais journalisés). Le QR
 * visuel encode uniquement le jeton opaque ; le scan caméra physique est
 * hors périmètre de cette tranche — le parcours fiable est la saisie du
 * code court.
 *
 * <p>Anti-rejeu de cette tranche : jeton expiré refusé, jeton d'une
 * séance fermée refusé (purge Redis sur l'événement
 * {@link com.esic.connect.coursesession.CourseSessionChangeEvent} d'action
 * {@code CLOSED}), et un même apprenant ne peut pas créer deux présences
 * pour le même point de contrôle — garanti par la contrainte SQL
 * {@code uq_attendance_record_checkpoint_enrollment} (autorité contre la
 * concurrence, retraduite en 409, jamais en 500). L'indisponibilité de
 * Redis produit une erreur contrôlée ({@code ATT_TOKEN_BACKEND_UNAVAILABLE},
 * 503) — jamais de validation dégradée.
 *
 * <p><strong>Hors périmètre</strong> : présence manuelle, correction,
 * justificatif, calcul de demi-journée, export CSV.
 *
 * <p>Dépendances inter-modules limitées aux ports publics :
 * {@link com.esic.connect.coursesession.CourseSessionDirectory} (séance,
 * point de contrôle, contrôle d'accès de lecture / gestion),
 * {@link com.esic.connect.enrollment.EnrollmentDirectory} (inscription
 * active de l'apprenant émargeur, identité des présents, effectif
 * attendu), {@link com.esic.connect.identity.CurrentUserResolver} et
 * {@link com.esic.connect.identity.UserDirectory} (compte de l'apprenant).
 * Consomme {@link com.esic.connect.coursesession.CourseSessionChangeEvent}
 * (purge Redis à la fermeture). Publie
 * {@link com.esic.connect.attendance.AttendanceChangeEvent}, consommé par
 * {@code audit}. Les types d'implémentation résident dans
 * {@code attendance.internal}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Attendance")
package com.esic.connect.attendance;
