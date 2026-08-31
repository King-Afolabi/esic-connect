/**
 * Module « studentimport » — import CSV contrôlé des apprenants
 * (docs/02-cahier-des-charges.md §10 ; docs/04-modele-donnees.md §16 ;
 * docs/reports/STUDENT_CSV_IMPORT_DESIGN.md ; EF-IMP-001 / EF-IMP-002 ;
 * US-050 / US-051 ; RG-020 à RG-024).
 *
 * <p><strong>État (checkpoint CP2)</strong> : schéma {@code V11} (quatre
 * tables techniques temporaires {@code student_import_job} /
 * {@code student_import_job_issue} / {@code student_import_row} /
 * {@code student_import_row_issue} + séquence
 * {@code student_number_sequence}, entités JPA et repositories) et
 * <em>socle interne</em> du module : rôles {@code @PreAuthorize}
 * ({@code StudentImportWeb}), erreur métier {@code IMP_*}
 * ({@code StudentImportException} + handler), codes d'anomalie
 * ({@code StudentImportIssueCodes}) et configuration typée / validée
 * ({@code StudentImportProperties}, préfixe {@code app.import.student}).
 * Le parsing CSV, la simulation, la confirmation transactionnelle, les
 * ports inter-modules, les endpoints REST et les écrans Angular relèvent
 * des checkpoints suivants et n'existent pas encore.
 *
 * <p>Les données métier créées lors d'une future confirmation (comptes,
 * profils apprenants, inscriptions, invitations) ne dépendent d'aucune
 * table {@code student_import_*} : la suppression d'un import (avant
 * confirmation ou à la purge) ne détruit jamais de donnée métier.
 * {@code requested_by_id} / {@code confirmed_by_id} sont de simples
 * valeurs techniques (clés étrangères SQL {@code RESTRICT} vers
 * {@code user_account}) : aucun partage d'entité JPA avec les autres
 * modules. Les types d'implémentation résident dans
 * {@code studentimport.internal}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Student import")
package com.esic.connect.studentimport;
