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
| G1-D | Notifications métier persistantes | EF-NOTIF-001, EF-NOTIF-002, RG-033 | DEC-G1-007 | `PARTIAL` (email d'activation seul) | **EF-NOTIF-001 `IMPLEMENTED_AND_TESTED`** ; **EF-NOTIF-002 / RG-033 `PARTIAL`** (audit G1-D.1) — module `notification` étendu (`V15`), listeners `AFTER_COMMIT` sur planning publié / séance annulée / remplaçant (**+ fin de remplacement**, G1-D.1), idempotence `dedup_key`, isolation par destinataire, 4 endpoints `/api/v1/me/notifications`, cloche + centre Angular (liens en liste blanche par rôle). Audience **formateur uniquement** ; apprenants / RP = dette G1-D-AUDIENCE ; livraison **best effort** sans reprise = dette G1-D-OUTBOX. Voir §5bis + §5ter. |
| G1-F | Tableaux de bord par rôle | CDC §25.1..25.4 (dashboards par rôle) | DEC-G1-010, DEC-G1-F | `PARTIAL` (dashboard générique unique) | **`IMPLEMENTED_AND_TESTED`** — module `dashboard`, `GET /api/v1/me/dashboard` typé par rôle (rôle effectif décidé serveur, priorité fixe), agrégats bornés via ports publics, périmètre serveur (`STUDENT` = ses données AC-017, `TEACHER` = ses séances, RP = `AcademicScopeDirectory`), anti-N+1 testé (manager). Cartes `PARTIAL` : justificatifs périmétrés RP / alternance `UNKNOWN`, dernières opérations d'audit, planning actif, conflits (non exposées). Front : cartes par rôle sous « Mon activité ». Voir §6bis. |
| G1-E | Pièces jointes des justificatifs | EF-JUS-001, EF-JUS-002, RG-071, RG-072, RG-073, RG-075, RG-076, CDC §21.5 | DEC-G1-008, DEC-G1-009 | `PARTIAL` (justificatif métier sans fichier) | **`IMPLEMENTED_AND_TESTED`** — dépôt owner + endpoints + séquence base/fichier avec compensation + réconciliation `@Scheduled` + téléchargement sécurisé (owner + examinateur) + notification au propriétaire + écrans. `V16` consommée. **Antivirus `NOT_IMPLEMENTED`** (`DEC-G1-E-ANTIVIRUS`) ; rétention pièces `À_DÉFINIR` (`R-G1-30`) ; pas de remplacement direct (retrait puis redépôt). Voir §7ter. |
| G1-G | Recette globale, e2e, doc | CDC §46, §47 ; AC-007, AC-008, AC-017 | DEC-G1-011 | `PARTIAL` (recette API §11.8 du guide de démo) | **`IMPLEMENTED_AND_TESTED`** pour la recette **API** de bout en bout (`PriorityPathRecetteIntegrationTests`, parcours prioritaire + extensions G1) ; **e2e navigateur `PARTIAL`** (Playwright non ajouté, repli API livré) ; **aucune démonstration manuelle** ⇒ G1 global `IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED`. Voir §8bis. |

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
| EF-SES-005 | CDC §44 (`EF-SES-005 \| Affecter un remplaçant \| SHOULD`) | Affecter un remplaçant | ~~`NOT_IMPLEMENTED`~~ → **`IMPLEMENTED_AND_TESTED`** (G1-C.2, 1er sept. 2026) | `POST /api/v1/sessions/{id}/substitutions` (`201` ; `CREATE_ROLES`, `TEACHER` exclu) : remplaçant = `TEACHER` actif éligible ≠ principal ; **formateur principal jamais écrasé** (`original_teacher_user_id` figé) ; période de validité ; une seule `ACTIVE` applicable (verrou de ligne séance + contrôle de chevauchement) ; `GET …/substitutions` (historique `ACTIVE`+`ENDED`) ; `POST …/{id}/end` (`204`) ; `AccessGuard` : substitut `ACTIVE` couvrant maintenant ⇒ `MANAGE` ; audit `SESSION_SUBSTITUTION_ADDED` / `…_ENDED` | `TeacherSubstitution` + `SubstitutionService` + `CourseSessionAccessGuard` étendu ; `teacher_substitution` (`V14`) | `CourseSessionIntegrationTests` **+6** (principal conservé, substitut autorisé/expiré, inéligible, chevauchement, fin/double fin, rôles, concurrence sans `5xx`) | Désigner un remplaçant |
| CAD §24 RG-12 / CDC §43 RG-015 | remplacement autorisé et audité / séance ⇒ remplaçant possible | **`IMPLEMENTED_AND_TESTED`** (G1-C.2) | Toute substitution écrit `SESSION_SUBSTITUTION_ADDED` / `…_ENDED` dans `audit_event` ; `course_session` porte un ou plusieurs remplacements via `teacher_substitution` (le principal reste `teacher_user_id`, non modifié) | cf. ci-dessus | cf. ci-dessus | — |
| CAD §24 RG-12 | `docs/01-cadrage.md` §24 (`RG-12 : un remplacement est autorisé et audité.`) | Remplacement autorisé + audité | Toute substitution écrit un `audit_event` catégorie `COURSE_SESSION` |
| CDC §43 RG-015 | `docs/02` §43 (`RG-015 : une séance peut posséder un remplaçant autorisé.`) | Séance ⇒ remplaçant possible | Colonne de remplaçant sur `course_session` (nom exact décidé à l'implémentation), renseignée par la substitution |
| CDC §43 RG-017 | `docs/02` §43 (`RG-017 : une séance exceptionnelle exige un motif.`) | Séance exceptionnelle ⇒ motif | Déjà en place : `course_session.exception_reason` `NOT NULL` sur toute séance manuelle ; non régressé (devient nullable pour les séances d'origine planning, cf. DEC-G1-001) |
| — | CDC §15.1 (« Une séance exceptionnelle peut être créée par un responsable pédagogique ») + CS (« pas de `PATCH` ») | Modifier une séance exceptionnelle `PLANNED` | `PATCH /api/v1/sessions/{id}` limité aux séances **d'origine manuelle** (`planning_entry_public_id IS NULL`, cf. DEC-G1-001) **et** `PLANNED` ; séance issue d'un planning non modifiable structurellement (DEC-G1-004) ; verrou optimiste → `409` | `CourseSessionService.update` | tests `PLANNED` vs `OPEN`/`CLOSED` + planning vs manuel + concurrence |

### 4bis. Consolidation au checkpoint G1-C.3 (1er septembre 2026)

| ID | Statut G1-C.1/C.2 | Statut G1-C.3 | Ce que G1-C.3 a prouvé en plus |
|---|---|---|---|
| EF-SES-004 | `IMPLEMENTED_AND_TESTED` | **`IMPLEMENTED_AND_TESTED`** (consolidé) | séance `CANCELLED` **consultable** par `GET /sessions/{id}` (statut / motif / date / points terminaux, sans champ SQL) et par rechargement ; garde `isHistoricallyReadable()` distincte de `isOperational()` ; audit `SESSION_CANCELLED` écrit **after-commit** (rollback ⇒ **0** ligne, test à faute injectée) ; purge Redis after-commit |
| EF-SES-005 | `IMPLEMENTED_AND_TESTED` | **`IMPLEMENTED_AND_TESTED`** (consolidé) | le remplaçant `ACTIVE` **voit la séance dans `GET /sessions` (liste)** et la gère (`MANAGE`), sans N+1 ; futur / expiré / terminé ⇒ aucun droit ; période **doit chevaucher la séance** (± 60 min) sinon `422 SESSION_SUBSTITUTION_OUTSIDE_SESSION` ; audit `…_ADDED` / `…_ENDED` after-commit |
| CAD §24 RG-12 / CDC §43 RG-015 | `IMPLEMENTED_AND_TESTED` | **`IMPLEMENTED_AND_TESTED`** | « autorisé **et audité** » : l'audit est désormais **garanti** (jamais committé sans commit métier — `@TransactionalEventListener(AFTER_COMMIT)` + `CourseSessionAuditWriter` `REQUIRES_NEW`) |
| Bloc **G1-C** | `IMPLEMENTED_FULL_SUITE_GREEN` | **`IMPLEMENTED_FULL_SUITE_GREEN`** — accès historiques, droits du remplaçant, périodes et audit post-commit **prouvés** | — |

Suites : back **735/0** (3 fuseaux, Flyway `V1→V14` rejoué sur vierge) ;
front **559/0**. Nouveaux codes : `SESSION_SUBSTITUTION_OUTSIDE_SESSION`
(`422`). Détail : `G1_IMPLEMENTATION_PROGRESS.md` § « Audit G1-C.3 ».

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

### 5bis. Statut après livraison G1-D (1er septembre 2026)

| ID | Statut avant G1-D | Statut après G1-D | Justification vérifiée dans le code |
|---|---|---|---|
| EF-NOTIF-001 | `PARTIAL` | **`IMPLEMENTED_AND_TESTED`** | Table `notification` (`V15`) ; `NotificationController` : `GET /api/v1/me/notifications` (paginé, `size` borné à 100, tri `createdAt DESC, id DESC`), `…/unread-count`, `…/{id}/read` (idempotent), `…/read-all` ; front cloche `mat-badge` (`app-shell`) + centre `/notifications` (filtre, marquage, `loading`/`empty`/`error`, pagination). `NotificationIntegrationTests` (8) : isolation par destinataire, `404` (pas `403`) sur une notif d'autrui, `read-all` borné, `400` filtre invalide, `401` sans jeton, DTO sans identifiant SQL, corps sans motif nominatif. |
| EF-NOTIF-002 | `PARTIAL` | **`IMPLEMENTED_AND_TESTED`** pour les événements **planning publié / séance annulée / remplaçant affecté / remplacement terminé** ; **`PARTIAL`** pour « invitation émise / justificatif accepté-refusé / import apprenant appliqué » (non branchés en G1-D — non requis numériquement) | `NotificationListener` (`@TransactionalEventListener(AFTER_COMMIT)`) sur `PlanningPublishedEvent` + `CourseSessionChangeEvent(CANCELLED / SUBSTITUTION_ADDED / SUBSTITUTION_ENDED)` ; `NotificationWriter` → `NotificationRowWriter` (`REQUIRES_NEW` par ligne) ; idempotence `dedup_key` UNIQUE. Tests : après commit (event via `TransactionTemplate`), **rollback ⇒ 0 notification**, idempotence (2 écritures ⇒ 1 ligne), compte archivé exclu, `open`/`close` ⇒ 0 notification. |
| RG-033 | `PARTIAL` (audit G1-B.1) | **`IMPLEMENTED_AND_TESTED`** pour l'audience **formateur** ; audience **apprenant / RP** = prolongement documenté | `PlanningPublishedEvent` → `PLANNING_PUBLISHED` pour les formateurs des séances créées / mises à jour (`aCommittedPlanningPublishedEventNotifiesTheAffectedTeachers` : le formateur d'une séance non concernée ne reçoit rien). |

**Destinataires — décision de périmètre G1-D.** Les documents (CDC
§18 / §23) demandent apprenants et responsables pédagogiques « si
requis » — non numériquement exigé. G1-D livre l'audience **formateur**
(principal + remplaçants `ACTIVE`), directement identifiée par les
événements G1-B / G1-C. Étendre aux apprenants et RP demande de nouveaux
ports `enrollment` / `academic` : tracé comme prolongement de G1-D dans
`G1_IMPLEMENTATION_PROGRESS.md`.

### 5ter. Reclassement après audit G1-D.1 (1er septembre 2026)

L'audit G1-D.1 revient sur l'optimisme du §5bis pour `EF-NOTIF-002` et
`RG-033`. Constat vérifié : **aucun document ne numérote l'audience**
de ces événements — CDC §14, §23, §44 listent des **événements**, jamais
des **rôles destinataires**. La colonne « Démo » du §5 (« Publier un
planning ⇒ notification apprenant ») est une **aspiration d'ergonomie**,
pas une exigence. Or CDC §13.9 (« notifier les apprenants lorsque
nécessaire ») et §23.2 (annulation = notification prioritaire) montrent
qu'une séance annulée / modifiée **concerne les apprenants** : ne
notifier que les formateurs est donc un **manque assumé**, pas un choix
neutre.

| ID | Statut G1-D (§5bis) | Statut G1-D.1 | Justification |
|---|---|---|---|
| EF-NOTIF-001 | `IMPLEMENTED_AND_TESTED` | **`IMPLEMENTED_AND_TESTED`** | centre in-app complet et testé ; inchangé |
| EF-NOTIF-002 | `IMPLEMENTED_AND_TESTED` (audience formateur) | **`PARTIAL`** | événements planning publié / annulation / remplacement **+ fin de remplacement** (G1-D.1) livrés et testés pour l'audience **formateur** (principal + remplaçants `ACTIVE` + remplaçant tout juste terminé via `CourseSessionChangeEvent.affectedUserPublicIds`) ; **audience apprenants / RP manquante** (dette G1-D-AUDIENCE) ; livraison **best effort** après commit, **sans reprise** (dette G1-D-OUTBOX) |
| RG-033 | `IMPLEMENTED_AND_TESTED` (audience formateur) | **`PARTIAL`** | « une modification publiée génère une notification » : satisfaite pour les **formateurs** des séances impactées, pas pour les **apprenants** de la classe |
| AC-017 (isolation destinataire) | `IMPLEMENTED_AND_TESTED` | **`IMPLEMENTED_AND_TESTED`** | `listUnreadCountReadAndReadAllAreScopedToTheCaller` ; `404` (pas `403`) sur une notif d'autrui ; inchangé |
| Préférences de notification | non tracé | **`NOT_IMPLEMENTED`** (non exigé) | DEC-G1-007 : `notification_preference` conditionnée à une exigence réelle d'opt-in / par type / par canal — **non établie** |
| Rétention / purge des notifications | non tracé | **`À_DÉFINIR`** | aucune durée documentaire (MDD §23.1 ne fixe rien) ; risque `R-G1-30` (`docs/06`), `docs/07` §14 |
| Garantie de livraison / reprise | implicite | **`PARTIAL`** | `AFTER_COMMIT` en mémoire, pas d'outbox : perte possible sur crash JVM entre commit et écriture ; idempotence garantit l'absence de doublon si une reprise est ajoutée (dette G1-D-OUTBOX) |

**Ce qui reste `IMPLEMENTED_AND_TESTED` en G1-D.1** : persistance des
notifications, **absence de duplication** (`dedup_key` UNIQUE + tests de
rejeu / concurrence), **livraison après commit** (rollback ⇒ 0
notification), **isolation par destinataire** (AC-017), **échec d'un
destinataire n'empêche pas les autres** (G1-D.1), **liens front en liste
blanche par rôle** (G1-D.1), **compteur non sondé hors session
authentifiée** (G1-D.1).

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

### 6bis. Statut après livraison G1-F (1er septembre 2026)

> **Mis à jour par la passe corrective G1-E/F/G — voir §8ter.** La limite
> « remplaçant non inclus » (CDC §25.3) est **levée** ; DEC-G1-010 gagne
> une preuve anti-N+1 renforcée (1 vs 15 classes) + un N+1 réel corrigé ;
> le bloc n'est plus qualifié `IMPLEMENTED_FULL_SUITE_GREEN` (par carte).

Module `dashboard` livré. Commits `feat(dashboard): exposer les agrégats
périmétrés par rôle` + `feat(frontend): ajouter les tableaux de bord par
rôle`. Détail + matrice rôle × carte :
`G1_IMPLEMENTATION_PROGRESS.md` § « G1-F ».

| Élément | Statut | Justification vérifiée dans le code / les tests |
|---|---|---|
| CDC §25.1 (RP) | **`IMPLEMENTED_AND_TESTED`** (partiel) | classes du périmètre + séances à venir périmétrées (`AcademicScopeDirectory` → `findByInternalIds` en 1 requête, anti-N+1 testé). **`PARTIAL`** : justificatifs périmétrés / alternance `UNKNOWN` (renvoi vers « Suivi d'assiduité »). |
| CDC §25.2 (administration) | **`IMPLEMENTED_AND_TESTED`** (partiel) | comptes par statut, justificatifs en attente global, imports récents, séances du jour. **`PARTIAL`** : dernières opérations d'audit (non exposées — éviter la divulgation d'`audit_event`). |
| CDC §25.3 (formateur) | **`IMPLEMENTED_AND_TESTED`** | prochaine séance / séances à venir / à ouvrir (`findUpcomingForTeacher`, UUID public). Limite : séances où il n'est que **remplaçant** non incluses. |
| CDC §25.4 (apprenant) | **`IMPLEMENTED_AND_TESTED`** | prochain cours / semaine + présences / retards / absences / justificatifs — **ses seules données** (AC-017, `aStudentGetsOnlyTheStudentSectionWithTheirOwnData`). |
| AC-017 (cloisonnement) | **`IMPLEMENTED_AND_TESTED`** | `studentDigest(userPublicId)` + inscriptions actives de l'appelant uniquement ; DTO sans identifiant SQL ni e-mail. |
| DEC-G1-010 (endpoint typé, agrégats bornés, sans N+1) | **`IMPLEMENTED_AND_TESTED`** | `GET /api/v1/me/dashboard`, `readOnly`, `COUNT` / `GROUP BY` / `Pageable`, compteur Hibernate `< 20` requêtes pour le dashboard manager. `V17` non créée (aucun index de perf justifié). |

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

### 7bis. Statut après checkpoint 1 de G1-E (1er septembre 2026)

Bloc `IN_PROGRESS` — checkpoint « schéma + modèle + stockage » livré.

| ID | Statut | Justification vérifiée dans le code |
|---|---|---|
| EF-JUS-001 | **`PARTIAL`** (socle livré) | `V16` `justification_attachment` (métadonnées seules) ; port public `attendance.JustificationFileStorage` + `LocalFilesystemJustificationFileStorage` (clé opaque dispersée, déplacement atomique, **taille appliquée pendant le flux**, SHA-256 pendant l'écriture, anti-traversal, hors webroot) ; `JustificationFileSafetyValidator` (extension + type déclaré + **magic bytes** → type re-dérivé ; rejet ZIP/OLE2 ; cohérence extension↔contenu ; nom assaini). Tests : `JustificationFileSafetyValidatorTests` (13), `LocalFilesystemJustificationFileStorageTests` (8), `JustificationAttachmentSchemaIntegrationTests` (2). **Manque** : endpoint de dépôt multipart + séquence base/fichier avec compensation (DEC-G1-009), tâche de réconciliation. |
| EF-JUS-002 | `IMPLEMENTED_AND_TESTED` (cycle d'examen, sans fichier) | inchangé ; le **téléchargement sécurisé** de la pièce par l'examinateur arrive au checkpoint endpoints. |
| RG-071 | `PARTIAL` | `app.attendance.justification-max-file-bytes` (défaut `5242880`), rejet `TOO_LARGE` **pendant le flux** (testé au niveau du stockage) ; `413` HTTP au checkpoint endpoints. |
| RG-072 | **`IMPLEMENTED_AND_TESTED`** (validateur) | liste blanche `.pdf`/`.jpg`/`.jpeg`/`.png` + magic bytes `%PDF-` / `FF D8 FF` / PNG ; tout autre contenu → `CONTENT_NOT_RECOGNISED` ; ZIP/OLE2 → `ARCHIVE_REJECTED` ; `.png` portant un PDF → `EXTENSION_CONTENT_MISMATCH`. |
| RG-073 / RG-075 / RG-076 / AC-014 | `IMPLEMENTED_AND_TESTED` | inchangés, non régressés. |
| CDC §21.5 (durcissement fichier) | `PARTIAL` | extension + type + magic bytes + taille + nom interne + hors répertoire public + anti-traversal **livrés et testés** ; `Content-Disposition: attachment` + `nosniff` = checkpoint endpoints ; **antivirus `NOT_IMPLEMENTED`** (`DEC-G1-E-ANTIVIRUS` — abstraction seule, jamais « garanti sans malware »). |

### 7ter. Statut après les checkpoints 2-4 de G1-E (1er septembre 2026)

Bloc **`IMPLEMENTED_FULL_SUITE_GREEN`** — dépôt, endpoints, réconciliation,
notification et écrans livrés. Commits `1835532` + `5d5f451`. Détail :
`G1_IMPLEMENTATION_PROGRESS.md` § « Checkpoints 2-4 ».

| ID | Statut après 2-4 | Justification vérifiée dans le code / les tests |
|---|---|---|
| EF-JUS-001 | **`IMPLEMENTED_AND_TESTED`** | `JustificationAttachmentStore` (validation → `newStorageKey` → `PENDING_STORAGE` `REQUIRES_NEW` → `store(key, upload)` → vérif SHA-256/taille → `STORED` `REQUIRES_NEW`) + compensation (échec `store` ⇒ `markDeleted` immédiat, 0 fichier ; échec `markStored` ⇒ `PENDING_STORAGE` + fichier, récupérés par la réconciliation `@Scheduled` bornée) ; endpoints `POST/GET/DELETE /api/v1/me/attendance/justifications/{id}/attachment`. `JustificationAttachmentIntegrationTests` (15 : nominal, unicité `409`, concurrence 1 active, retrait+redépôt, dépôt sur justificatif examiné `409`, compensation, réconciliation ×3). |
| EF-JUS-002 | **`IMPLEMENTED_AND_TESTED`** (consolidé) | `GET [/download] /api/v1/attendance/justifications/{id}/attachment` pour l'examinateur (`REVIEW_LIST_ROLES`) ; hors périmètre (autre `STUDENT`, `TEACHER` sans périmètre, RP hors périmètre) → **`404`**, jamais `403`. Testé. |
| RG-071 | **`IMPLEMENTED_AND_TESTED`** | rejet `413` **avant écriture** (`JustificationAttachmentValidationException.TOO_LARGE`) + limite pendant le flux (adaptateur) + enveloppe servlet multipart portée à 6 Mo (`ATT_ATTACHMENT_TOO_LARGE`). `rejectsAnOversizedFileWith413AndPersistsNothing`. |
| RG-072 | **`IMPLEMENTED_AND_TESTED`** | inchangé (validateur magic bytes) ; `415` HTTP mappé (`rejectsAWrongTypeWith415AndPersistsNothing`). |
| RG-073 / RG-075 / RG-076 / AC-014 | **`IMPLEMENTED_AND_TESTED`** | non régressés. |
| CDC §21.5 | **`IMPLEMENTED_AND_TESTED`** sauf **antivirus `NOT_IMPLEMENTED`** | `Content-Disposition: attachment` (+ `filename*`) + `X-Content-Type-Options: nosniff` + type **re-dérivé** + `Content-Length` + `Cache-Control: no-store`, pas de `Range` ; répertoire hors webroot, `toRealPath`, refus des liens symboliques, anti-traversal. Antivirus = `DEC-G1-E-ANTIVIRUS` (abstraction seule ; jamais « garanti sans malware »). |
| EF-NOTIF-002 (audience justificatif) | **`IMPLEMENTED_AND_TESTED`** pour l'examen d'un justificatif | `JustificationReviewedEvent` → notification au **propriétaire** (destinataire unique porté par l'événement ; `dedup_key` = `justificationPublicId` ; corps neutre, jamais le motif de refus) ; rollback de l'examen ⇒ 0 notification (`AFTER_COMMIT`). `acceptingAJustificationNotifiesTheOwnerExactlyOnceAndRejectingToo`. L'audience apprenants/RP des événements **planning / séance** reste la dette **G1-D-AUDIENCE** (inchangé). |

**Rétention** des fichiers / lignes `DELETED` : `À_DÉFINIR` (aucune durée
documentaire ; `R-G1-30`, `docs/07` §14). La réconciliation ne traite que
le **technique** (`PENDING_STORAGE` orphelins), pas le métier.

---

## 8. G1-G — Recette globale, e2e, documentation

| ID | Source | Rôle dans G1-G |
|---|---|---|
| CDC §46 « Tests » | CDC | La suite unitaire / intégration / sécurité / concurrence de chaque bloc est agrégée ; commandes et totaux consignés |
| CDC §47 « Recette » | CDC | Le scénario de recette principal (§47.2) est rejoué **avec le planning** (import → simulation → publication → séance → émargement → correction → audit → rapport → export) |
| AC-007, AC-008 | CDC §45 | Rejoués de bout en bout (API + e2e si Playwright) |
| AC-017 | CDC §45 | Rejoué : accès croisé apprenant refusé |
| DEC-G1-011 | `G1_ARCHITECTURE_DECISIONS.md` | Décision e2e (Playwright vs démonstration API automatisée) |

### 8bis. Statut après livraison G1-G (1er septembre 2026)

| Élément | Statut | Justification vérifiée dans le code / les tests |
|---|---|---|
| CDC §47.2 (recette bout en bout **avec planning**) | **`IMPLEMENTED_AND_TESTED`** (API) | `PriorityPathRecetteIntegrationTests#theEndToEndPriorityPathAndG1ExtensionsReplaySuccessfully` : référentiel → import apprenants CSV (simulation → confirmation → 3 comptes) → import planning CSV (**AC-007** : simulation ⇒ 0 séance) → publication (version 1, 2 séances) → le formateur consulte / ouvre / émet un jeton → un apprenant actif inscrit émarge → rapport classe + **export CSV** (`text/csv`) → annulation d'une séance → **notification** `SESSION_CANCELLED` du formateur → **remplacement** de formateur → justificatif + **pièce jointe** → acceptation → **notification** `JUSTIFICATION_ACCEPTED` du propriétaire + téléchargement de la pièce par l'examinateur → **tableaux de bord** `ADMINISTRATION` / `TEACHER` / `STUDENT`. |
| AC-007 | **`IMPLEMENTED_AND_TESTED`** (rejoué) | assertion « après simulation, `planning_slot_public_id` séances = 0 » puis « après publication = 2 ». |
| AC-008 | `IMPLEMENTED_AND_TESTED` (couvert par `PlanningPublicationIntegrationTests`) | non re-rejoué dans la recette (une seule publication) — versionnement N/N+1 testé au bloc G1-B. |
| AC-017 | `IMPLEMENTED_AND_TESTED` | couvert par `DashboardIntegrationTests` (dashboard `STUDENT` = ses données) + `Attendance*` / `Justification*` (accès croisé → `404`). |
| DEC-G1-011 (e2e) | **`PARTIAL`** — e2e **navigateur non livré** | **Playwright n'est pas ajouté** : dépendance lourde + téléchargement de navigateur non fiable dans l'environnement (critère « risque / coût disproportionné » de `DEC-G1-011`). **Repli livré** : `PriorityPathRecetteIntegrationTests` (`@SpringBootTest`, appels HTTP réels de bout en bout). Le e2e **navigateur** reste `PARTIAL`. Aucune démonstration **manuelle** n'a été exécutée ⇒ le grand lot G1 est `IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED`, jamais `DEMONSTRATED`. |
| CDC §46 (agrégation) | **`IMPLEMENTED_AND_TESTED`** | totaux finaux consignés dans `G1_IMPLEMENTATION_PROGRESS.md` § « G1-G ». |

### 8ter. Reclassement après la passe corrective G1-E / G1-F / G1-G (1er septembre 2026)

Voir `G1_IMPLEMENTATION_PROGRESS.md` § « Passe corrective G1-E/F/G » et
`G1_FINAL_REPORT.md`.

| Élément | Avant | Après | Justification vérifiée |
|---|---|---|---|
| G1-E — échec d'audit après stockage | (non tracé — faux négatif d'API) | **`IMPLEMENTED_AND_TESTED`** (isolé) | `uploadOwnAttachment` isole l'échec de la trace après le commit `STORED` : `201`, pièce durable, échec journalisé. Dette : trace **non rejouée** (assumée, cohérente avec les 8 listeners d'audit synchrones). Test `anAuditFailureAfterTheAttachmentIsStoredStillReturns201AndKeepsThePiece`. |
| G1-E — balayage des fichiers orphelins | (hedge « limite assumée ») | **`NOT_IMPLEMENTED`** (explicite) | La réconciliation ne traite QUE les `PENDING_STORAGE`. Scan de répertoire sûr disproportionné. Test de figure de la portée `reconciliationDoesNotSweepAFileOrphanedByADeletedRow`. |
| CDC §25.3 (dashboard formateur) | `IMPLEMENTED_AND_TESTED` **avec limite** « remplaçant non inclus » | **`IMPLEMENTED_AND_TESTED`** (limite levée) | `findUpcomingForTeacher` inclut les séances où l'utilisateur est remplaçant `ACTIVE` couvrant l'instant courant, en 1 requête, sans doublon. Corrige aussi le filtre « à ouvrir » (`== SessionLifecycle.PLANNED`). |
| EF-AUTH-003 ↔ dashboard (contexte de rôle) | contexte **ergonomique**, non transmis (divergence UI ↔ serveur) | **`IMPLEMENTED_AND_TESTED`** — politique explicite | `?context=<rôle>` vérifié contre les autorités du JWT (`403 DASHBOARD_CONTEXT_NOT_HELD` sinon — jamais d'élévation) ; absent ⇒ priorité fixe. Front transmet + recharge. Tests back + front. |
| DEC-G1-010 (dashboard sans N+1) | `IMPLEMENTED_AND_TESTED` (test à plafond `< 20` sur 2 classes) | **`IMPLEMENTED_AND_TESTED`** — preuve renforcée + N+1 réel corrigé | `findSessionsForClasses` résolvait les classes par `findByPublicId` en boucle. Port de lot `ClassGroupDirectory.findByPublicIds` + résolution groupée. Preuve : 1 vs 15 classes, `qLarge − qSmall ≤ 3`. |
| G1-F — statut de bloc | `IMPLEMENTED_FULL_SUITE_GREEN` (global) | **`IMPLEMENTED_AND_TESTED` par carte** ; 4 cartes manager + « audit récent » administration = **`PARTIAL`** | Aucun port agrégé borné pour ces cartes ; `note` honnête renvoyée. `IMPLEMENTED_FULL_SUITE_GREEN` n'est pas un synonyme de complétude produit. |
| CDC §47.2 (recette) — continuité | « un apprenant actif inscrit émarge » (compte parallèle) | **`IMPLEMENTED_AND_TESTED`** — chaîne **continue** | L'apprenant qui émarge et dépose le justificatif est celui **importé** puis **activé** via l'API publique (`/account-invitations/activate`). Dates relatives à l'horloge. |
| DEC-G1-011 (e2e) | **`PARTIAL`** | **`NOT_IMPLEMENTED`** (navigateur) ; recette **API** `IMPLEMENTED_AND_TESTED` | Étude de faisabilité : pas de Playwright/puppeteer/cypress, pas de navigateur, pas de script `e2e`. Repli API renforcé. Démonstration manuelle : `IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED`. |
| Décompte de modules | « 13 modules » (fin G1-G) | **14 modules** | 14 `package-info.java` ; `planning` (G1-B) + `dashboard` (G1-F) tous deux réels. |

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
