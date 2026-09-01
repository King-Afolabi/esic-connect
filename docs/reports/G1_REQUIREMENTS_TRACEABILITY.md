# G1 — Traçabilité des exigences (gel avant développement)

> **Verrou de traçabilité du grand lot produit G1**, produit au bloc
> **G1-0** avant tout code métier.
>
> Règles appliquées :
> - chaque identifiant `EF-*`, `RG-*`, `AC-*` cité est **retrouvé
>   textuellement** dans sa source (colonne « Source ») ;
> - aucun identifiant inventé ;
> - une règle nécessaire mais **non numérotée** dans les documents
>   devient une décision `DEC-G1-*` (voir
>   `docs/reports/G1_ARCHITECTURE_DECISIONS.md`) et n'est **jamais**
>   présentée comme une exigence du cahier des charges ;
> - une **présentation d'interface** retenue pour la démonstration est
>   une décision d'ergonomie, pas une exigence fonctionnelle : elle est
>   isolée dans la section « Choix d'ergonomie ».
>
> Statuts autorisés : `IMPLEMENTED_AND_TESTED`,
> `IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED`, `PARTIAL`,
> `DOCUMENTATION_ONLY`, `NOT_IMPLEMENTED`, `HORS_PÉRIMÈTRE_ASSUMÉ`,
> `BLOCKED`.

## Date

```text
31 août 2026
```

## Sources

| Code court | Fichier |
|---|---|
| CDC | `docs/02-cahier-des-charges.md` |
| CAD | `docs/01-cadrage.md` |
| ARCH | `docs/03-architecture.md` |
| MDD | `docs/04-modele-donnees.md` |
| BKL | `docs/05-product-backlog.md` |
| CS | `docs/CURRENT-STATE.md` |
| AUDIT | `docs/reports/PROJECT_FINAL_AUDIT.md` |

---

## 1. Vue d'ensemble par bloc

> **Avancement (1er septembre 2026, session autonome).** G1-A et G1-B
> sont **livrés et verts** (suites back 713/0, front 548/0). Détail par
> commit : `docs/reports/G1_IMPLEMENTATION_PROGRESS.md`.

| Bloc | Intitulé | Exigences pilotes | Décisions | Statut avant G1 | Statut après cette session |
|---|---|---|---|---|---|
| G1-A | Interfaces Angular des API existantes | EF-ROOM-001, EF-ACA-001..005, EF-USER-001..003, EF-AUTH-004 | DEC-G1-A1 | `PARTIAL` (API livrées, écrans absents ou lecture seule) | **`IMPLEMENTED_AND_TESTED`** pour `EF-ROOM-001` (écrans `organization`) ; `EF-ACA-001..005` / `EF-USER-001` / `EF-AUTH-004` = **dette G1-A** (API prêtes, pas d'écran d'écriture ; plan §3.1) |
| G1-B | Module `planning` complet | EF-PLAN-001, EF-PLAN-002, EF-PLAN-003, EF-PLAN-004, EF-PLAN-005, EF-PLAN-007, EF-SES-001, RG-016, RG-030..RG-035, AC-007, AC-008 | DEC-G1-001..006, DEC-G1-012 | `HORS_PÉRIMÈTRE_ASSUMÉ` (addendum F2) | **`IMPLEMENTED_AND_TESTED`** — module `planning`, V12/V13, simulation (T1), publication atomique + versionnement, port `PlanningSessionWriter`, écrans `/planning/**`. `EF-PLAN-003` (correction ligne à ligne) = **`PARTIAL`** (annulation + réimport, DEC-G1-003) ; `EF-PLAN-006` = `HORS_PÉRIMÈTRE_ASSUMÉ` ; `DEC-G1-006` (alternance) post-G1 |
| G1-C | Cycle de vie avancé des séances | EF-SES-004, EF-SES-005, CAD §24 RG-12 (« remplacement autorisé et audité »), CDC §43 RG-015, RG-017 | DEC-G1-004, DEC-G1-005 | `NOT_IMPLEMENTED` (séances exceptionnelles `PLANNED→OPEN→CLOSED` seulement) | `NOT_STARTED` |
| G1-D | Notifications métier persistantes | EF-NOTIF-001, EF-NOTIF-002, RG-033 | DEC-G1-007 | `PARTIAL` (email d'activation seul) | `NOT_STARTED` (l'événement `planning.PlanningPublishedEvent` existe déjà, prêt à être consommé) |
| G1-F | Tableaux de bord par rôle | CDC §25.1..25.4 (dashboards par rôle) | DEC-G1-010 | `PARTIAL` (dashboard générique unique) | `NOT_STARTED` |
| G1-E | Pièces jointes des justificatifs | EF-JUS-001, EF-JUS-002, RG-071, RG-072, RG-073, RG-075, RG-076, CDC §21.5 | DEC-G1-008, DEC-G1-009 | `PARTIAL` (justificatif métier sans fichier) | `NOT_STARTED` |
| G1-G | Recette globale, e2e, doc | CDC §46, §47 ; AC-007, AC-008, AC-017 | DEC-G1-011 | `PARTIAL` (recette API §11.8 du guide de démo) | `PARTIAL` — jeux de données `planning-demo.csv` / `planning-conflicts-demo.csv` ajoutés ; docs `CURRENT-STATE` / README / ce fichier mis à jour ; recette e2e complète + `docs/11` restent à faire |

---

## 2. G1-A — Interfaces Angular des API existantes

| ID | Source | Résumé fidèle | Statut avant G1 | Critère d'acceptation G1 | Preuve code attendue | Preuve test attendue | Démo attendue |
|---|---|---|---|---|---|---|---|
| EF-ROOM-001 | CDC §44 (`EF-ROOM-001 \| Gérer les salles \| MUST`) | Gérer les salles | `PARTIAL` (module `organization` livré, **aucun écran Angular**, CS « API seule ») | Écrans Angular CRUD + archivage/restauration site / bâtiment / salle et plages réseau CIDR, alignés sur les rôles serveur | `frontend/src/app/features/organization/**`, service API typé | `*.spec.ts` service + composants ; garde de rôle ; `403`/`409` rendus ; axe-core sur un formulaire | Parcours « créer un site → un bâtiment → une salle » |
| EF-ACA-001 | CDC §44 | Gérer les formations | `PARTIAL` (front `/academic` en **lecture seule**, CS) | Écrans création / modification / archivage / restauration des formations | `frontend/src/app/features/academic/**` (formulaires) | `*.spec.ts` ; garde ; `403` périmètre RP | Créer puis archiver une formation |
| EF-ACA-002 | CDC §44 | Gérer les niveaux | `PARTIAL` (lecture seule) | Idem, niveaux | idem | idem | idem |
| EF-ACA-003 | CDC §44 | Gérer les promotions | `PARTIAL` (lecture seule) | Idem, promotions | idem | idem | idem |
| EF-ACA-004 | CDC §44 | Gérer les classes | `PARTIAL` (lecture seule) | Idem, classes | idem | idem | idem |
| EF-ACA-005 | CDC §44 | Gérer les années scolaires | `PARTIAL` (lecture seule) | Idem, années scolaires | idem | idem | idem |
| EF-USER-001 | CDC §44 (`EF-USER-001 \| Créer un utilisateur \| MUST`) | Créer un utilisateur | `PARTIAL` (CS : « pas d'endpoint `POST /users` de création `PENDING_ACTIVATION` ») | **Si** un endpoint d'émission d'invitation réel existe : écran d'émission ; **sinon** limite conservée et documentée (pas de faux endpoint) | selon audit endpoint (voir DEC-G1-A1) | test de contrat si endpoint ; sinon N/A | Émettre une invitation depuis `/administration` |
| EF-USER-002 | CDC §44 | Suspendre un utilisateur | `IMPLEMENTED_AND_TESTED` (CS : front `/administration` R/W) | Aucune régression | — | suite existante | — |
| EF-USER-003 | CDC §44 (`SHOULD`) | Archiver un utilisateur | `IMPLEMENTED_AND_TESTED` (CS) | Aucune régression | — | suite existante | — |
| EF-AUTH-004 | CDC §44 (`Activer un compte par invitation \| SHOULD`) | Activer un compte par invitation | `PARTIAL` (activation livrée ; émission d'invitation sans écran — CS mise à jour F5) | Écran d'émission d'invitation **uniquement si** endpoint réel ; état visible ; relance si endpoint réel | selon audit | contrat + garde | Émettre / relancer une invitation |

Périmètre de rôles : chaque écran reprend **à l'identique** le
`@PreAuthorize` du contrôleur cible (audit dans
`G1_IMPLEMENTATION_PLAN.md`). Le garde Angular masque la navigation ;
Spring Security reste l'autorité.

---

## 3. G1-B — Module `planning`

| ID | Source | Résumé fidèle | Statut avant G1 | Critère d'acceptation G1 | Preuve code attendue | Preuve test attendue | Démo attendue |
|---|---|---|---|---|---|---|---|
| EF-PLAN-001 | CDC §44 (`EF-PLAN-001 \| Importer un planning CSV \| MUST`) | Importer un planning CSV | `HORS_PÉRIMÈTRE_ASSUMÉ` (CDC §4.5.1, addendum F2) | `POST /api/v1/planning-imports` accepte un CSV `.csv` borné, jamais persisté (SHA-256 seul), crée un job `SIMULATED` | module `com.esic.connect.planning`, migration `V12`, `PlanningImportController` | tests parseur + `CsvFileGuard` (ZIP/OLE/PDF rejetés) + sécurité `401/403` | Téléverser `docs/demo-data/planning-demo.csv` |
| EF-PLAN-002 | CDC §44 (`EF-PLAN-002 \| Prévisualiser le planning \| MUST`) | Prévisualiser le planning | `HORS_PÉRIMÈTRE_ASSUMÉ` | La simulation produit lignes + anomalies + synthèse **sans** créer de séance / version / entrée active (invariant T1) | `PlanningSimulationService` | test « simulation sans écriture métier » ; erreurs de référence (année/classe/formateur/salle) ; conflits ; alternance | Écran `/planning/import/:jobId` |
| EF-PLAN-003 | CDC §44 (`EF-PLAN-003 \| Corriger les lignes \| SHOULD`) | Corriger les lignes | `HORS_PÉRIMÈTRE_ASSUMÉ` | **Soit** correction ligne à ligne complète et sûre (valeur d'origine conservée, revalidation globale), **soit** annulation + réimport documenté comme réduction (DEC-G1-003) | `PlanningRowCorrectionService` **ou** `/cancel` + réimport | test correction + revalidation des conflits, **ou** test annulation | Corriger une ligne en conflit ou réimporter |
| EF-PLAN-004 | CDC §44 (`EF-PLAN-004 \| Publier le planning \| MUST`) | Publier le planning | `HORS_PÉRIMÈTRE_ASSUMÉ` (EF-SES-001, AC-007 liés) | `POST /api/v1/planning-imports/{id}/publish` : transaction tout-ou-rien, verrou `FOR UPDATE`, revalidation, version N/N+1, séances créées via port public, idempotent, concurrence → `409`/idempotent, jamais `500` | `PlanningPublicationService`, port `coursesession.PlanningSessionWriter` (DEC-G1-001) | tests rollback total (T3), idempotence, concurrence (2 publications), audit `AFTER_COMMIT` | Publier → séances visibles |
| EF-PLAN-005 | CDC §44 (`EF-PLAN-005 \| Versionner le planning \| SHOULD`) | Versionner le planning | `HORS_PÉRIMÈTRE_ASSUMÉ` | Table `planning_version` (`DRAFT`/`PUBLISHED`/`SUPERSEDED`), N+1 à chaque republication, ancienne version `SUPERSEDED`, aucune suppression physique | entité `PlanningVersion` + repo | test versionnement + « séances `OPEN`/`CLOSED` inchangées » + comparaison | `/planning/versions` |
| EF-PLAN-007 | CDC §44 (`EF-PLAN-007 \| Conserver trois versions \| SHOULD`) | Conserver trois versions | `HORS_PÉRIMÈTRE_ASSUMÉ` | Aucune purge automatique des versions ; ≥ 3 consultables | contrainte : pas de `DELETE` de version | test « 4 publications → 4 versions consultables » | Historique `/planning/versions` |
| EF-SES-001 | CDC §44 (`EF-SES-001 \| Créer des séances depuis le planning \| MUST`) ; CDC §43 RG-016 | Créer des séances depuis le planning | `HORS_PÉRIMÈTRE_ASSUMÉ` (CDC §4.5.1) | La publication crée / réutilise / supersède des `course_session` via un **port public** de `coursesession` (aucune entité JPA partagée) ; séance d'origine planning = `planning_entry_public_id` renseigné (discriminant `V13`, cf. DEC-G1-001), `exception_reason` alors nulle ; lien `planning_entry ↔ session_public_id` | `coursesession.PlanningSessionWriter` + `DefaultPlanningSessionWriter` | test création + réutilisation + supersession ; `ModularityTests` vert | Séance issue du planning dans `/sessions` |
| EF-PLAN-006 | CDC §44 (`EF-PLAN-006 \| Créer un planning dans l'interface \| SHOULD`) | Créer un planning dans l'interface | `HORS_PÉRIMÈTRE_ASSUMÉ` | **Hors périmètre G1** : la création manuelle plein calendrier (`US-073`, estimation 13, « à découper ») n'est pas dans G1-B. La création manuelle de séance exceptionnelle existe déjà (`coursesession`). | — | — | `HORS_PÉRIMÈTRE_ASSUMÉ` (documenté) |

### Règles de gestion `planning` (CDC §43)

| ID | Source | Texte (résumé fidèle) | Critère d'acceptation G1 |
|---|---|---|---|
| RG-016 | CDC §43 (`RG-016 : une séance normale provient d'un planning publié.`) | Séance normale ⇐ planning publié | Toute séance d'origine planning (`planning_entry_public_id` non nul) porte une référence à une `planning_entry` d'une version `PUBLISHED` ; les séances manuelles restent l'exception documentée (addendum F2) |
| RG-030 | CDC §43 (`RG-030 : le responsable pédagogique publie son planning.`) | Le RP publie son planning | `PEDAGOGICAL_MANAGER` peut publier **dans son périmètre uniquement** (`AcademicScopeDirectory`) |
| RG-031 | CDC §43 (`RG-031 : le formateur ne publie pas le planning.`) | Le `TEACHER` ne publie pas | Aucune route de publication ouverte à `TEACHER` ; test `403` |
| RG-032 | CDC §43 (`RG-032 : trois versions sont conservées.`) | ≥ 3 versions conservées | cf. EF-PLAN-007 |
| RG-033 | CDC §43 (`RG-033 : une modification publiée génère une notification.`) | Modification publiée ⇒ notification | Événement `PlanningPublishedEvent` → notifications (G1-D) ; testé en G1-D |
| RG-034 | CDC §43 (`RG-034 : un conflit bloquant interdit la publication.`) | Conflit bloquant ⇒ publication refusée | Job avec ≥ 1 ligne `ERROR` non publiable → `409` métier |
| RG-035 | CDC §43 (`RG-035 : une salle peut être affectée après l'import.`) | Salle affectable après import | `room_code` facultatif ; entrée sans salle publiable |

### Critères d'acceptation `planning` (CDC §45)

| ID | Source | Texte | Test G1 |
|---|---|---|---|
| AC-007 | CDC §45 (`AC-007 — Import planning` : « Un planning valide doit produire des séances uniquement après confirmation et publication. ») | Séances **seulement** après confirmation + publication | Test : simulation ⇒ 0 séance ; publication ⇒ N séances |
| AC-008 | CDC §45 (`AC-008 — Versionnement` : « Une modification d'un planning publié doit créer une nouvelle version. ») | Modification d'un planning publié ⇒ nouvelle version | Test : republication d'un job modifié ⇒ `version_number` = N+1, ancienne `SUPERSEDED` |

---

## 3bis. Réévaluation individuelle après l'audit correctif G1-B.1 (1er septembre 2026)

> **Pourquoi.** La ligne du §1 déclarait en bloc « `EF-PLAN-001..005,
> EF-PLAN-007, EF-SES-001, RG-016, RG-030..RG-035, AC-007, AC-008 =
> IMPLEMENTED_AND_TESTED ». L'audit G1-B.1 a réévalué **chaque
> identifiant** et corrige cette déclaration trop optimiste. La colonne
> « Déclaré initialement (G1-B) » conserve la mention d'origine ; la
> colonne « Statut corrigé (audit G1-B.1) » fait foi.

| ID | Déclaré initialement (G1-B) | Statut corrigé (audit G1-B.1) | Justification vérifiée dans le code |
|---|---|---|---|
| EF-PLAN-001 | `IMPLEMENTED_AND_TESTED` | **`IMPLEMENTED_AND_TESTED`** | `PlanningImportController` + `PlanningCsvGuard` + `PlanningCsvParser` ; `PlanningImportIntegrationTests` (upload `.csv`, rejet non-CSV → `415`, colonne manquante → `400`, sécurité `401/403`). |
| EF-PLAN-002 | `IMPLEMENTED_AND_TESTED` | **`IMPLEMENTED_AND_TESTED`** | `PlanningSimulationService` invariant T1 (aucune écriture métier) ; lignes + anomalies + synthèse ; conflits intra-fichier **et** vs séances déjà publiées (audit G1-B.1). |
| EF-PLAN-003 | `PARTIAL` | **`PARTIAL`** (inchangé) | Pas de correction ligne à ligne : un fichier fautif se corrige et se re-téléverse (`/cancel` + réimport, DEC-G1-003). `PlanningQueryService.cancel` idempotent, testé. |
| EF-PLAN-004 | `IMPLEMENTED_AND_TESTED` | **`IMPLEMENTED_AND_TESTED`** (renforcé) | Transaction atomique + `FOR UPDATE` ; **concurrence idempotente durcie** : le perdant d'une course sur le même job renvoie `alreadyPublished=true` (jamais `FAILED`) — `entityManager.refresh` + repli `alreadyPublishedResult` dans l'orchestrateur ; test `concurrentPublishOfSameJobIsStrictlyIdempotent` (assertions exactes : 1 version, 1 séance, job `PUBLISHED`, `publishedVersionPublicId` non nul, pas de `PLAN_PUBLICATION_FAILED`). Rollback + `FAILED` prouvé de façon déterministe (`PlanningPublicationFailureIntegrationTests`, faux `PlanningSessionWriter` `@Primary`). |
| EF-PLAN-005 | `IMPLEMENTED_AND_TESTED` | **`IMPLEMENTED_AND_TESTED`** | `PlanningVersion` `DRAFT/PUBLISHED/SUPERSEDED`, N/N+1, `replacedByVersion` ; `PlanningPublicationIntegrationTests#republicationOfAModifiedImportCreatesVersionTwoAndSupersedesVersionOne`. |
| EF-PLAN-006 | `HORS_PÉRIMÈTRE_ASSUMÉ` | **`HORS_PÉRIMÈTRE_ASSUMÉ`** (inchangé) | Création manuelle plein calendrier non implémentée (US-073). |
| EF-PLAN-007 / RG-032 | `IMPLEMENTED_AND_TESTED` | **`PARTIAL`** | Aucune purge de `planning_version` (rétention garantie structurellement : aucun chemin `DELETE`), mais **aucun test dédié « ≥ 3 versions consultables »** — les tests d'intégration couvrent 2 versions. Gap mineur, tracé pour G1-G. |
| EF-SES-001 / RG-016 | `IMPLEMENTED_AND_TESTED` | **`IMPLEMENTED_AND_TESTED`** (identité corrigée) | Port `PlanningSessionWriter` (création / réutilisation / supersession). **Identité de créneau corrigée à l'audit** : `course_session.planning_slot_public_id` (renommée depuis `planning_entry_public_id`, nom trompeur) porte l'identité *stable* du créneau (`planning_entry.slot_public_id` déterministe), jamais un `planning_entry.public_id`. Test `PlanningSlotIdentityIntegrationTests` (identité stable inter-versions ; `public_id` de ligne distinct ; deux plannings ⇒ identités distinctes ; aucun `entryPublicId` ambigu dans les DTO). |
| RG-030 | `IMPLEMENTED_AND_TESTED` | **`IMPLEMENTED_AND_TESTED`** | `AcademicScopeDirectory.isClassInScope` re-vérifié à la publication ; `403 PLAN_SCOPE_FORBIDDEN` testé. |
| RG-031 | `IMPLEMENTED_AND_TESTED` | **`IMPLEMENTED_AND_TESTED`** | `@PreAuthorize(PlanningWeb.MANAGE_ROLES)` sans `TEACHER` ; `publishIsForbiddenForTeacherAndStudent`. |
| RG-033 | `IMPLEMENTED_AND_TESTED` | **`PARTIAL`** | `PlanningPublishedEvent` publié en transaction et consommé après commit par `audit` **uniquement** ; **aucune notification persistante** destinée à un utilisateur (module `notification` = G1-D, `NOT_STARTED`). La règle « génère une notification » n'est pas satisfaite côté destinataire. |
| RG-034 | `IMPLEMENTED_AND_TESTED` | **`PARTIAL`** (gap réduit) | Conflit bloquant intra-fichier (formateur / classe / **salle**) + hors plage horaire : OK. **Ajout audit G1-B.1** : conflit **formateur** et **classe** contre des séances *déjà publiées* (port `CourseSessionDirectory.findOperationalSessionWindows`, exclusion du même créneau republié) — testé. **Gap restant** : conflit **salle** contre des séances déjà publiées — non fait, le module `coursesession` ne porte pas de `room_code` (limite documentée, DEC-G1-005). |
| RG-035 | `IMPLEMENTED_AND_TESTED` | **`PARTIAL`** | `planning_entry.room_code` = simple `VARCHAR` facultatif, propagé jusqu'à la séance ; **aucune action / aucun écran d'affectation de salle après l'import**, aucun lien vers `organization.room`. Une entrée sans salle est publiable (couvert). |
| AC-007 | `IMPLEMENTED_AND_TESTED` | **`IMPLEMENTED_AND_TESTED`** | `publishesAVersionAndCreatesPlanningSessionsOnlyAfterConfirmation` + assertion « simulation ⇒ 0 séance » dans plusieurs tests. |
| AC-008 | `IMPLEMENTED_AND_TESTED` | **`IMPLEMENTED_AND_TESTED`** pour le versionnement ; **`PARTIAL`** pour le devenir des séances | Version N+1 + ancienne `SUPERSEDED` : testé. Une séance retirée d'une version reste `PLANNED` + `superseded_by_scheduling = true` ; elle est désormais traitée comme **inactive** par une garde centralisée (`CourseSession.isOperational()` appliquée dans `DefaultCourseSessionDirectory`, `CourseSessionService.require/list`, specs) — GET / ouverture / jeton / rapports la rejettent, seul l'historique planning la montre (tests dans `PlanningPublicationIntegrationTests#supersededSessionIsInactiveButRemainsInPlanningVersionHistory`). L'état `CANCELLED` propre arrive en **G1-C** ; la migration G1-C fera basculer ces séances futures `PLANNED` supersédées vers `CANCELLED`. |

### G1-A — statut de bloc corrigé

`G1-A` reste **globalement `PARTIAL`** : seul `EF-ROOM-001` (écrans
`organization`) est `IMPLEMENTED_AND_TESTED`. `EF-ACA-001..005`,
`EF-USER-001`, `EF-AUTH-004` (écrans d'écriture académique, inscriptions,
affectation d'un responsable pédagogique, émission d'invitation) restent
**`NOT_IMPLEMENTED`** — API livrées, aucune UI. Aucune UI ne simule un
endpoint absent.

---

## 4. G1-C — Cycle de vie avancé des séances

| ID | Source | Résumé fidèle | Statut avant G1 | Critère d'acceptation G1 | Preuve code | Preuve test | Démo |
|---|---|---|---|---|---|---|---|
| EF-SES-004 | CDC §44 (`EF-SES-004 \| Annuler une séance \| SHOULD`) | Annuler une séance | ~~`NOT_IMPLEMENTED`~~ → **`IMPLEMENTED_AND_TESTED`** (G1-C.1, 1er sept. 2026) | `POST /api/v1/sessions/{id}/cancel {reason}` : `PLANNED`/`OPEN` → `CANCELLED` (motif obligatoire, `400` sinon) ; `CLOSED`/déjà `CANCELLED` → `409` ; points de contrôle non terminaux → `CANCELLED` ; jetons Redis purgés ; **aucune absence dérivée** (garde `operational()`) ; audit `SESSION_CANCELLED` ; `403` `STUDENT`/`SCHOOL_ADMINISTRATION` ; course concurrente ouvrir/annuler → `409`, jamais `500` | `CourseSessionService.cancel`, migration **`V14`** | `CourseSessionIntegrationTests` **+4** (transitions, motif, rôles, concurrence) | Annuler une séance exceptionnelle |
| EF-SES-005 | CDC §44 (`EF-SES-005 \| Affecter un remplaçant \| SHOULD`) | Affecter un remplaçant | `IN_PROGRESS` (G1-C.2) — table `teacher_substitution` créée en `V14`, code non écrit | `POST /api/v1/sessions/{id}/substitutions` : compte actif rôle `TEACHER`, formateur initial conservé (`original_teacher_user_id`), période de validité, exception historisée, une seule `ACTIVE` applicable, notification après commit (G1-D) | `teacher_substitution` (MDD §18.3, `V14` — **livré**), `SubstitutionService` (à écrire) | tests éligibilité + chevauchement + accès substitut + concurrence + audit | Désigner un remplaçant |
| CAD §24 RG-12 | `docs/01-cadrage.md` §24 (`RG-12 : un remplacement est autorisé et audité.`) | Remplacement autorisé + audité | Toute substitution écrit un `audit_event` catégorie `COURSE_SESSION` |
| CDC §43 RG-015 | `docs/02` §43 (`RG-015 : une séance peut posséder un remplaçant autorisé.`) | Séance ⇒ remplaçant possible | Colonne de remplaçant sur `course_session` (nom exact décidé à l'implémentation), renseignée par la substitution |
| CDC §43 RG-017 | `docs/02` §43 (`RG-017 : une séance exceptionnelle exige un motif.`) | Séance exceptionnelle ⇒ motif | Déjà en place : `course_session.exception_reason` `NOT NULL` sur toute séance manuelle ; non régressé (devient nullable pour les séances d'origine planning, cf. DEC-G1-001) |
| — | CDC §15.1 (« Une séance exceptionnelle peut être créée par un responsable pédagogique ») + CS (« pas de `PATCH` ») | Modifier une séance exceptionnelle `PLANNED` | `PATCH /api/v1/sessions/{id}` limité aux séances **d'origine manuelle** (`planning_entry_public_id IS NULL`, cf. DEC-G1-001) **et** `PLANNED` ; séance issue d'un planning non modifiable structurellement (DEC-G1-004) ; verrou optimiste → `409` | `CourseSessionService.update` | tests `PLANNED` vs `OPEN`/`CLOSED` + planning vs manuel + concurrence |

> `EF-SES-002` (ouvrir) et `EF-SES-003` (clôturer) sont déjà
> `IMPLEMENTED_AND_TESTED` (CS) : aucune régression attendue.
>
> **Deux numérotations `RG-*`** (correctif G1-0.1) : `docs/01-cadrage.md`
> §24 numérote `RG-01..RG-30` ; `docs/02-cahier-des-charges.md` §43
> numérote `RG-001..RG-088` (+ un groupe `RG-010..RG-017`). Le même
> numéro n'a **pas** le même sens : `CDC §43 RG-012` = « un apprenant
> appartient à une seule classe principale active », alors que « un
> remplacement est autorisé et audité » est `CAD §24 RG-12`. Ne jamais
> déduire le sens d'une règle de son seul numéro.

---

## 5. G1-D — Notifications métier persistantes

| ID | Source | Résumé fidèle | Statut avant G1 | Critère d'acceptation G1 | Preuve code | Preuve test | Démo |
|---|---|---|---|---|---|---|---|
| EF-NOTIF-001 | CDC §44 (`EF-NOTIF-001 \| Afficher des notifications internes \| SHOULD`) | Afficher des notifications internes | `PARTIAL` (CS : « email d'activation seulement ») | Table `notification` (migration dédiée) ; `GET /api/v1/me/notifications` (paginé, borné, par utilisateur), `unread-count`, `read`, `read-all` ; front cloche + badge + liste | module `notification` étendu, migration `V15` | tests sécurité (isolation destinataire), pagination, contenu sans PII/jeton | Cloche → liste → marquer lu |
| EF-NOTIF-002 | CDC §44 (`EF-NOTIF-002 \| Notifier les modifications \| SHOULD`) | Notifier les modifications | `PARTIAL` | Événements planning publié / séance modifiée / séance annulée / remplaçant affecté / invitation émise / justificatif accepté-refusé / import apprenant appliqué → notifications persistées **après commit**, transaction indépendante, idempotentes ; un échec de notification ne rollback pas le métier | listeners `@TransactionalEventListener(AFTER_COMMIT)` + clé d'idempotence | tests « after commit », « rollback métier ⇒ pas de notification », idempotence, destinataires dérivés serveur | Publier un planning ⇒ notification apprenant |
| RG-033 | CDC §43 (`RG-033 : une modification publiée génère une notification.`) | Modification publiée ⇒ notification | cf. EF-NOTIF-002, test dédié planning |

---

## 6. G1-F — Tableaux de bord par rôle

Source : CDC §25 « Tableaux de bord et graphiques » — §25.1 responsable
pédagogique, §25.2 administration, §25.3 formateur, §25.4 apprenant.
Aucune de ces listes n'est numérotée `EF-*` : chaque carte est reliée à
une donnée réelle et arrêtée dans **DEC-G1-010**.

| Rôle | Source | Cartes (résumé) | Statut avant G1 | Critère d'acceptation G1 |
|---|---|---|---|---|
| `SUPER_ADMIN` / `ADMIN` | CDC §25.2 + §25.1 | comptes actifs / suspendus / activation en attente, imports récents, séances du jour, justificatifs en attente, planning actif, dernières opérations d'audit **sans PII** | `PARTIAL` (dashboard générique) | endpoint typé par contexte, borné, sécurisé par rôle, testé sans N+1 mesurable |
| `SCHOOL_ADMINISTRATION` | CDC §25.2 | effectifs, inscriptions, séances à venir, justificatifs, imports | `PARTIAL` | idem |
| `PEDAGOGICAL_MANAGER` | CDC §25.1 | classes du périmètre, planning actif, séances, taux d'assiduité, anomalies `UNKNOWN`, justificatifs | `PARTIAL` | idem + **périmètre serveur** (`AcademicScopeDirectory`) |
| `TEACHER` | CDC §25.3 | prochaine séance, séances du jour, à ouvrir, remplacements, présences à compléter | `PARTIAL` | idem, `TEACHER` = ses séances |
| `STUDENT` | CDC §25.4 | prochain cours, planning semaine, présences, retard/absence à justifier, notifications | `PARTIAL` | idem, `STUDENT` = ses données (AC-017) |

| ID | Source | Texte | Rôle dans G1-F |
|---|---|---|---|
| AC-017 | CDC §45 (`AC-017 — Sécurité` : « Un étudiant ne doit jamais consulter le rapport d'un autre étudiant. ») | Cloisonnement apprenant | Le dashboard `STUDENT` n'expose que les données de l'appelant ; test d'accès croisé |

---

## 7. G1-E — Pièces jointes des justificatifs

| ID | Source | Résumé fidèle | Statut avant G1 | Critère d'acceptation G1 | Preuve code | Preuve test | Démo |
|---|---|---|---|---|---|---|---|
| EF-JUS-001 | CDC §44 (`EF-JUS-001 \| Déposer un justificatif \| SHOULD`) | Déposer un justificatif | `PARTIAL` (CS : « justificatif **métier sans fichier** ») | Upload PDF/JPEG/PNG borné, contrôle extension + MIME + magic bytes, nom neutralisé, SHA-256, stockage **hors webroot** via port `JustificationFileStorage`, métadonnées en MySQL, aucun contenu en base | migration `V16`, entité `JustificationAttachment`, port de stockage + implémentation locale | tests PDF/JPEG/PNG OK, extension trompeuse rejetée, magic bytes, taille, traversal, en-têtes `Content-Disposition: attachment` + `nosniff` | Déposer un PDF fictif |
| EF-JUS-002 | CDC §44 (`EF-JUS-002 \| Valider ou refuser \| SHOULD`) | Valider ou refuser | `IMPLEMENTED_AND_TESTED` (cycle d'examen métier, CS) — sans fichier | L'examinateur voit et télécharge la pièce jointe dans son périmètre ; justificatif traité non modifiable | `JustificationAttachmentController` (téléchargement) | tests accès (étudiant propriétaire / examinateur périmètre / autre `403`) | Examiner un justificatif avec pièce |
| RG-071 | CDC §43 (`RG-071 : la taille maximale est de 5 Mo.`) | Taille max 5 Mo | `JUSTIFICATION_MAX_FILE_BYTES` par défaut 5 MiB → `413` au-delà |
| RG-072 | CDC §43 (`RG-072 : les formats acceptés sont JPEG, PNG et PDF.`) | Formats JPEG / PNG / PDF | Tout autre type rejeté (magic bytes) |
| RG-073 | CDC §43 (`RG-073 : un refus exige un motif.`) | Refus ⇒ motif | déjà en place (cycle d'examen) ; non régressé |
| RG-075 | CDC §43 (`RG-075 : un justificatif accepté produit EXCUSED.`) | Accepté ⇒ `EXCUSED` | déjà en place (`ABSENT → EXCUSED_ABSENCE`) ; non régressé |
| RG-076 | CDC §43 (`RG-076 : un justificatif n'efface pas l'historique de l'absence.`) | Historique conservé | append-only ; non régressé |
| — | CDC §21.5 « Sécurité des fichiers » (vérifier extension / MIME / taille, nom interne, hors répertoire public, pas d'exécution, anti-traversal) | Durcissement fichier | tests dédiés (voir ci-dessus) |
| AC-014 | CDC §45 (`AC-014 — Justificatif` : « Un justificatif accepté doit transformer `ABSENT` en `EXCUSED`. ») | Transition d'assiduité | non régressé par l'ajout de fichier |

---

## 8. G1-G — Recette globale, e2e, documentation

| ID | Source | Rôle dans G1-G |
|---|---|---|
| CDC §46 « Tests » | CDC | La suite unitaire / intégration / sécurité / concurrence de chaque bloc est agrégée ; commandes et totaux consignés |
| CDC §47 « Recette » | CDC | Le scénario de recette principal (§47.2) est rejoué **avec le planning** (import → simulation → publication → séance → émargement → correction → audit → rapport → export) |
| AC-007, AC-008 | CDC §45 | Rejoués de bout en bout (API + e2e si Playwright) |
| AC-017 | CDC §45 | Rejoué : accès croisé apprenant refusé |
| DEC-G1-011 | `G1_ARCHITECTURE_DECISIONS.md` | Décision e2e (Playwright vs démonstration API automatisée) |

---

## 9. Choix d'ergonomie (décisions, pas des exigences)

Ces points sont des **choix de présentation** retenus pour la
démonstration jury. Ils ne figurent pas comme exigences numérotées et
sont tracés comme décisions dans `G1_ARCHITECTURE_DECISIONS.md`.

| Réf | Choix | Justification |
|---|---|---|
| DEC-G1-A1 | Réutiliser les composants partagés existants (`shared/components`) — sélecteurs asynchrones, dialogue de confirmation, gestion d'erreur — plutôt qu'un générateur CRUD générique | cohérence, testabilité, lisibilité pour le jury |
| DEC-G1-B-UI | Vue « semaine » du planning en CSS grid / Angular simple, **sans** bibliothèque calendrier lourde | budget de bundle (< 500 kB), pas d'exigence de vue calendaire riche |
| DEC-G1-D-UI | Cloche + badge de compteur non lus dans l'`app-shell` | motif d'interface usuel, non normatif |
| DEC-G1-F-UI | Cartes de tableau de bord + listes courtes cliquables | CDC §25 décrit des contenus, pas une maquette |

---

## 10. Exigences explicitement hors du périmètre G1

Restent `HORS_PÉRIMÈTRE_ASSUMÉ` (inchangé, cf. §5 du prompt du lot et
`docs/CURRENT-STATE.md`) :

- EF-ROOM-002, EF-ATT-008 (QR fixe + contrôle réseau d'émargement) ;
- EF-IMP-003, EF-IMP-004 (Excel `.xlsx` / multifeuille) — le planning
  G1-B est **CSV uniquement** ;
- EF-AI-001, EF-AI-002, EF-AI-003 (mapping intelligent, score) — le
  planning G1-B n'a **pas** d'assistant IA (CDC §13.10 = cible) ;
- EF-REP-004 (export Excel), export PDF, mise en page « officielle » ;
- EF-AUTH-005 (mot de passe oublié), EF-AUTH-006/007 (WebAuthn / passkey),
  EF-AUTH-008 (MFA), Turnstile ;
- EF-IOT-001/002, réclamations (EF-CLAIM-001/002), départ anticipé,
  scan caméra, PWA/push.
