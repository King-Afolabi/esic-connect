/**
 * Module « coursesession » (branche feature/attendance-qr-demonstration).
 *
 * <p>Séances exceptionnelles : une séance est créée manuellement (sans
 * module planning), qualifiée d'exceptionnelle, avec un motif obligatoire
 * ({@code exception_reason}). Elle référence directement un compte
 * formateur ({@code teacher_user_id}, rôle {@code TEACHER} actif), cible
 * au moins une classe ({@code session_class}) et suit le cycle de vie
 * {@code PLANNED → OPEN → CLOSED} (sans réouverture). Chaque séance porte
 * un unique point de contrôle d'émargement ({@code attendance_checkpoint},
 * ouvert / fermé avec la séance) — limite explicite de cette tranche.
 *
 * <p><strong>Hors périmètre</strong> : planning récurrent, remplacement,
 * annulation, modification structurante après ouverture, plusieurs points
 * de contrôle, calcul d'assiduité. Le module ne persiste ni jeton QR ni
 * code court (Redis, module {@code attendance}).
 *
 * <p>Dépendances inter-modules limitées aux ports publics :
 * {@link com.esic.connect.identity.CurrentUserResolver} (auteur des
 * écritures et identité de l'appelant),
 * {@link com.esic.connect.identity.TeacherDirectory} (comptes formateurs
 * éligibles), {@link com.esic.connect.academic.ClassGroupDirectory}
 * (existence / activité d'une classe et code fonctionnel) et
 * {@link com.esic.connect.academic.AcademicScopeDirectory} (périmètre
 * pédagogique d'un {@code PEDAGOGICAL_MANAGER}). Publie
 * {@link com.esic.connect.coursesession.CourseSessionChangeEvent},
 * consommé par {@code audit} et par {@code attendance} (purge Redis à la
 * fermeture). Expose le port
 * {@link com.esic.connect.coursesession.CourseSessionDirectory}, consommé
 * par {@code attendance} pour résoudre une séance, son point de contrôle
 * et le contrôle d'accès, sans partage d'entité JPA. Les types
 * d'implémentation résident dans {@code coursesession.internal}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Course session")
package com.esic.connect.coursesession;
