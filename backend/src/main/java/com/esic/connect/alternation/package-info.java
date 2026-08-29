/**
 * Module « alternation » (docs/03-architecture.md §7.4).
 *
 * <p>Rythmes d'alternance école / entreprise : modèles réutilisables de
 * rythme ({@code work_study_pattern}), affectation historisée d'un rythme
 * à une classe ({@code class_work_study_pattern}, docs/04 §14.2) et
 * exceptions individuelles de calendrier ({@code student_schedule_exception},
 * docs/04 §14.3). Le module résout, pour une classe (ou une inscription)
 * et une date, le contexte attendu {@code SCHOOL} / {@code COMPANY} /
 * {@code UNKNOWN} — une période en entreprise n'est jamais une absence
 * (docs/02 §8.4).
 *
 * <p><strong>Hors périmètre de ce lot</strong> : aucun calcul d'assiduité,
 * aucune dépendance aux modules {@code planning}, {@code coursesession} ou
 * {@code attendance} (inexistants). La résolution effective d'une
 * inscription applique uniquement la priorité <em>structurelle</em> d'une
 * exception individuelle sur le rythme de classe.
 *
 * <p>Dépendances inter-modules limitées aux ports publics :
 * {@link com.esic.connect.identity.CurrentUserResolver} (auteur des
 * écritures), {@link com.esic.connect.academic.ClassGroupDirectory}
 * (résolution d'une classe et de son année scolaire),
 * {@link com.esic.connect.academic.AcademicScopeDirectory} (contrôle du
 * périmètre pédagogique d'un {@code PEDAGOGICAL_MANAGER}, sans importer la
 * logique de sécurité interne d'{@code academic}) et
 * {@link com.esic.connect.enrollment.EnrollmentDirectory} (résolution
 * d'une inscription pour y rattacher une exception). {@code class_group_id}
 * et {@code enrollment_id} sont de simples valeurs techniques (clés
 * étrangères SQL). Publie
 * {@link com.esic.connect.alternation.AlternationChangeEvent}, consommé
 * par le module {@code audit}. Les types d'implémentation résident dans
 * {@code alternation.internal} et ne sont pas visibles des autres modules.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Alternation")
package com.esic.connect.alternation;
