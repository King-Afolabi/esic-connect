# G1 — Rapport final du grand lot produit

> Livrable (9) du plan `G1_IMPLEMENTATION_PLAN.md` : rapport final du
> **grand lot produit G1** (« montée en gamme fonctionnelle d'ESIC
> Connect »). Rédigé après les blocs G1-0 → G1-G **et** la passe
> corrective probatoire G1-E / G1-F / G1-G du 1er septembre 2026.
>
> Sources de vérité complémentaires :
> - `docs/reports/G1_IMPLEMENTATION_PROGRESS.md` — journal bloc par bloc ;
> - `docs/reports/G1_REQUIREMENTS_TRACEABILITY.md` — matrice EF-* / RG-* / AC-* ;
> - `docs/reports/G1_ARCHITECTURE_DECISIONS.md` — décisions `DEC-G1-*` ;
> - `docs/CURRENT-STATE.md` — état du dépôt ;
> - `git log` — le code et les tests font foi.

## 1. Métadonnées

| Élément | Valeur |
|---|---|
| Lot | G1 — grand lot produit |
| Branche | `feature/master-level-product-expansion` |
| Date du rapport | 1er septembre 2026 |
| HEAD au démarrage de la passe corrective | `55f999a` |
| Environnement de vérification | OpenJDK 21.0.12 (Homebrew), Maven 3.9.16 (wrapper), Node 24.13, npm 11.6.2, MySQL 8 + Redis 7 (Docker Compose local) |
| Poussé / PR / fusion | **Non** — aucun `push`, aucune PR, aucune fusion, aucun `--amend` |

## 2. Périmètre du lot G1

Le lot G1 lève une partie du périmètre classé `HORS_PÉRIMÈTRE_ASSUMÉ` à
la finalisation F2 :

| Bloc | Objet | Statut honnête |
|---|---|---|
| G1-A | Écrans Angular des API `organization` existantes | `PARTIAL` — sites / bâtiments / salles / plages CIDR livrés ; écritures `academic` / `enrollment` / affectations / invitation restent une dette (API prêtes, pas d'écran) |
| G1-B | Module `planning` : import CSV → simulation → publication versionnée → séances | `IMPLEMENTED_AND_TESTED` — `EF-PLAN-001..005/007`, `EF-SES-001`, `RG-016`, `RG-030..035` (RG-032..035 `PARTIAL` après audit G1-B.1), `AC-007`, `AC-008`. `EF-PLAN-006` (création manuelle plein calendrier) reste `HORS_PÉRIMÈTRE_ASSUMÉ` |
| G1-C | Cycle de vie avancé des séances : annulation, remplacements | `IMPLEMENTED_AND_TESTED` — `EF-SES-004`, `EF-SES-005`, `RG-12`, `RG-015` (C.1 + C.2 + C.3) |
| G1-D | Centre de notifications persistantes | `EF-NOTIF-001` `IMPLEMENTED_AND_TESTED` ; `EF-NOTIF-002` / `RG-033` `PARTIAL` (audience **formateur uniquement** ; livraison « au mieux » après commit, sans reprise — dettes G1-D-OUTBOX / G1-D-AUDIENCE) |
| G1-E | Pièces jointes des justificatifs | `IMPLEMENTED_AND_TESTED` — `EF-JUS-001`, `EF-JUS-002`, `RG-071`, `RG-072`, `CDC §21.5`. **Antivirus `NOT_IMPLEMENTED`** (`DEC-G1-E-ANTIVIRUS`). **Balayage des fichiers orphelins `NOT_IMPLEMENTED`** (voir §4-B) |
| G1-F | Tableaux de bord par rôle | `IMPLEMENTED_AND_TESTED` **par carte** (jamais un `IMPLEMENTED_FULL_SUITE_GREEN` global) — cartes manager justificatifs périmétrés / alternance `UNKNOWN` / planning actif / conflits et carte administration « audit récent » restent `PARTIAL` |
| G1-G | Recette du parcours prioritaire + documentation | Recette **API** `IMPLEMENTED_AND_TESTED` ; e2e **navigateur** `NOT_IMPLEMENTED` ; démonstration manuelle : `IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED` |

## 3. Passe corrective probatoire G1-E / G1-F / G1-G

Objet : vérifier par le code et les tests les réserves restantes,
corriger les défauts confirmés, renforcer les preuves insuffisantes,
réaligner les qualifications documentaires exagérées.

### 3.1 Anomalies confirmées et corrigées

| Réf | Anomalie confirmée | Correction | Preuve |
|---|---|---|---|
| A | `AttendanceJustificationService.uploadOwnAttachment` publie la trace d'audit `JUSTIFICATION_ATTACHMENT_STORED` **hors transaction, après** le commit `STORED` ; le listener d'audit synchrone (`@EventListener` + `REQUIRES_NEW`) qui échoue faisait **remonter un 5xx** alors que la pièce est déjà durablement stockée → **faux négatif d'API** | `try/catch (RuntimeException)` autour de la publication : l'échec est **journalisé** (WARN, sans PII) ; l'API répond `201`, la pièce reste valide et téléchargeable. La garantie exacte est décrite au §5 | `JustificationAttachmentIntegrationTests#anAuditFailureAfterTheAttachmentIsStoredStillReturns201AndKeepsThePiece` (faute d'audit injectée par un `@EventListener` de test à priorité maximale ; aucun bean de production modifié) |
| C | Tableau de bord **formateur** : `CourseSessionDirectory.findUpcomingForTeacher` ne renvoyait que les séances où l'utilisateur est **formateur principal** — les séances où il est **remplaçant `ACTIVE`** (règles G1-C.3) étaient absentes | Ajout d'un `Clock` à `DefaultCourseSessionDirectory` ; `findUpcomingForTeacher` fait désormais `taughtBy(id) OR internalId IN findActiveSubstitutedSessionIds(id, now)` en **une** requête, sans doublon | `DashboardIntegrationTests#aTeacherDashboardIncludesSessionsWhereTheyAreAnActiveSubstitute`, `…ExcludesSessionsWhereTheirSubstitutionHasEnded`, `…aTeacherWhoIsBothPrincipalAndActiveSubstituteSeesTheSessionOnce` |
| C (annexe) | `DashboardService.teacher()` filtrait « à ouvrir » par `"PLANNED".equals(s.status())` où `s.status()` est un `SessionLifecycle` → comparaison **toujours fausse**, carte « À ouvrir » **toujours vide** | `s.status() == SessionLifecycle.PLANNED` | Couvert par `aTeacher…` ci-dessus + revue |
| D | **Divergence silencieuse UI ↔ serveur** : le front propose un sélecteur de contexte de rôle (multi-rôles) mais `/me/dashboard` choisissait toujours le rôle par **priorité fixe** ; un compte RP+formateur qui sélectionne « formateur » voyait quand même le tableau de bord RP | Politique explicite : `GET /me/dashboard?context=<rôle>` ; le serveur **vérifie que le rôle est présent dans les autorités du JWT** (`403 DASHBOARD_CONTEXT_NOT_HELD` sinon — **jamais** d'élévation) ; contexte absent ⇒ priorité fixe déterministe. Le front transmet le contexte actif (uniquement s'il y a un choix réel) et recharge le tableau de bord à son changement | `DashboardIntegrationTests` (mono-rôle sans contexte ; multi-rôles sans contexte → plus haut ; multi-rôles avec chaque contexte détenu ; rôle non détenu → `403` ; `STUDENT` demandant `ADMIN` → `403`) + `dashboard.spec.ts` / `dashboard-api.service.spec.ts` (front) |
| F | **N+1 réel** dans `DefaultCourseSessionDirectory.findSessionsForClasses` : résolution des classes par `classGroupDirectory.findByPublicId(...)` **dans un `.map()`** — 1 requête par classe du périmètre. Masqué par le test existant (plafond `< 20` sur une fixture à 2 classes) | Nouveau port de lot `ClassGroupDirectory.findByPublicIds(Collection<UUID>)` (1 requête) ; `findSessionsForClasses` et `DashboardService` (nouvelle méthode `lines(...)`) résolvent tous les libellés de classe en une requête, en réutilisant les codes déjà connus du périmètre | `DashboardIntegrationTests#aPedagogicalManagerDashboardDoesNotGrowItsQueryCountWithTheNumberOfClasses` : fixture A = 1 classe / 3 séances, fixture B = 15 classes / 3 séances ; `qSmall` = 14 requêtes, `qLarge` = 14 ; assertion `qLarge − qSmall ≤ 3` (loin d'un +14). Contenu fonctionnel vérifié pour les deux tailles. `ClassGroupDirectoryTests#findByPublicIdsResolvesABatchInOneCallAndIgnoresUnknownOrNull` |
| G | La recette « bout en bout » créait un **apprenant parallèle** (`account(RoleCode.STUDENT)`) pour la seconde moitié du scénario ; l'apprenant importé (CSV) n'était **jamais utilisé** → chaîne annoncée « continue » en réalité **discontinue** ; le test était aussi présenté implicitement comme un e2e | La recette **active un apprenant réellement importé** via l'API publique `POST /account-invitations/activate` (jeton capté par un mailer de test), puis **ce même apprenant** (déjà inscrit par l'import) émarge, dépose le justificatif et sa pièce jointe. Dates construites relativement à l'horloge (aucune ne périme). Le SQL direct ne sert plus qu'à observer des invariants. Javadoc et docs qualifient la classe de « recette d'intégration API Spring », **pas** un e2e navigateur | `PriorityPathRecetteIntegrationTests#theEndToEndPriorityPathAndG1ExtensionsReplaySuccessfully` (vert) |

### 3.2 Réserves inspectées et **infirmées** (aucun défaut / pas de correction de code)

| Réf | Réserve | Constat |
|---|---|---|
| A (formulation) | « audit après commit » dans la doc G1-E serait inexacte | Pour le **chemin d'upload**, `uploadOwnAttachment` n'est pas transactionnel et la trace est publiée **après** le retour de `store()` (donc après le commit `STORED`) : la formulation est exacte. Seul l'**isolement** de son échec manquait (corrigé — réserve A) |
| B | Cas « ligne `PENDING_STORAGE` avec fichier » / « sans fichier » non couverts par la réconciliation | **Déjà couverts** : `reconcileOne` → `STORED` si fichier valide, `DELETED` si absent, suppression + `DELETED` si incohérent. Tests existants `reconciliationPromotesAnAgedPendingRow…` / `…DropsAnAgedPendingRow…` |
| B | Cas « ligne `STORED` avec fichier absent » balayé | Non balayé, mais **non reproductible dans la séquence** (le fichier est vérifié présent + empreinte avant `STORED`). Non traité, documenté |

### 3.3 Réserves non traitées — classées explicitement

| Réf | Capacité | Statut | Motif |
|---|---|---|---|
| B | Balayage des **fichiers orphelins** (ligne `DELETED`, fichier subsistant après échec best-effort de suppression ; ou fichier sans aucune ligne) | **`NOT_IMPLEMENTED`** | Un scan de répertoire *sûr* (protection liens symboliques + traversée + TOCTOU, refus de supprimer sur supposition fragile, détection avant suppression) est disproportionné pour cette passe corrective. Risque et surface trop élevés. Test de figure de la portée ajouté (`reconciliationDoesNotSweepAFileOrphanedByADeletedRow`). Stratégie future : job dédié borné, journalisation d'abord, quarantaine avant purge |
| E | Cartes manager « justificatifs en attente **périmétrés** » / « alternance `UNKNOWN` » / « planning actif » / « conflits récents » ; carte administration « dernières opérations d'audit » | **`PARTIAL`** | Aucun de ces agrégats ne dispose d'un **port borné existant** ; la jointure justificatif → inscription → classe traverse 3 modules sans port. Le tableau de bord renvoie une `note` honnête renvoyant vers « Suivi d'assiduité » / « Planning ». Non inventé |
| G | e2e **navigateur** (Playwright / autre) | **`NOT_IMPLEMENTED`** | Étude de faisabilité : aucune dépendance `playwright` / `puppeteer` / `cypress` dans `frontend/package.json`, rien dans `node_modules`, pas de cache `~/.cache/ms-playwright`, pas de script `e2e`. Une installation impose le téléchargement de navigateurs (~300-500 Mo) non fiable en environnement non interactif + un backend + un dev-server + la base. Critère « risque / coût disproportionné » de `DEC-G1-011`. Repli : la recette API |
| G | **Démonstration manuelle** du parcours (UI) | **`IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED`** | Aucune manipulation manuelle consignée dans le dépôt |

## 4. Garanties transactionnelles exactes (pièces jointes)

Séquence de dépôt (`JustificationAttachmentStore`, non transactionnelle,
avec compensation) :

1. validation stricte (extension + type déclaré + magic bytes → type
   re-dérivé + rejet ZIP/OLE2 + cohérence + taille) — échec ⇒ **0 ligne,
   0 fichier** ;
2. `newStorageKey()` ; SHA-256 des octets ;
3. insertion `PENDING_STORAGE` (`REQUIRES_NEW`) — committée **avant**
   l'écriture du fichier ;
4. `store(key, upload)` — échec ⇒ **compensation immédiate** : ligne
   `PENDING_STORAGE` → `DELETED` (créneau d'unicité libéré), aucun
   fichier ;
5. vérification `sha256` + `sizeBytes` réellement écrits — divergence ⇒
   `storage.delete` + `markDeleted` + `503` ;
6. bascule `STORED` (`REQUIRES_NEW`) — échec ⇒ ligne `PENDING_STORAGE` +
   fichier **conservés** ; l'API répond `503 ATT_ATTACHMENT_STORAGE_FAILED`
   (pas de faux succès) ; la **réconciliation** `@Scheduled` bornée
   promeut la ligne (`STORED` si fichier valide) ou la déclasse
   (`DELETED` si absente / incohérente) ;
7. **trace d'audit** publiée **après** le retour de `store()` (donc après
   le commit `STORED`), **hors transaction**. **Son échec n'annule pas le
   dépôt** (réserve A) : `201` rendu, pièce durable, échec **journalisé**.
   L'absence de trace est une **dette d'audit assumée** (le listener
   n'est pas rejoué) — cohérente avec les 8 autres listeners d'audit
   synchrones du projet (voir G1-C.3). Une reprise durable (outbox
   d'audit) reste à planifier globalement.

Notification du propriétaire à l'examen : événement publié **dans** la
transaction de `review`, consommé `AFTER_COMMIT` — rollback de l'examen
⇒ 0 notification.

## 5. Politique multi-rôle finale (tableau de bord)

- Le front (`RoleContextService`) propose un contexte de rôle **seulement**
  si le compte cumule ≥ 2 rôles ; il transmet ce contexte à
  `GET /api/v1/me/dashboard?context=<rôle>` et **recharge** le tableau de
  bord à chaque changement.
- Le serveur (`DashboardService.resolveRole`) :
  - `context` fourni ⇒ le rôle **doit** figurer dans le claim `roles` du
    JWT, sinon `403 DASHBOARD_CONTEXT_NOT_HELD`. Le rôle est ensuite mappé
    vers son tableau de bord (`SUPER_ADMIN`/`ADMIN`/`SCHOOL_ADMINISTRATION`
    → `ADMINISTRATION` ; `PEDAGOGICAL_MANAGER` ; `TEACHER` ; `STUDENT`).
    **Aucune élévation possible** : le rôle demandé est déjà détenu.
  - `context` absent ⇒ priorité fixe déterministe
    `SUPER_ADMIN > ADMIN > SCHOOL_ADMINISTRATION > PEDAGOGICAL_MANAGER >
    TEACHER > STUDENT` ; aucun rôle exploitable ⇒ `403 DASHBOARD_NO_ROLE`.
- Le contexte ne pilote **que** le rôle affiché ; il n'élargit jamais le
  JWT ni les autorisations (revalidées à chaque appel par Spring
  Security).

## 6. Cartes de tableau de bord réellement disponibles

| Rôle effectif | Cartes livrées (`IMPLEMENTED_AND_TESTED`) | Cartes `PARTIAL` (note honnête renvoyée) |
|---|---|---|
| `STUDENT` | présences (present / late / absent / excused), justificatifs en attente / refusés (ses seules données, AC-017) ; cours des 7 jours | — |
| `TEACHER` | prochaine séance / séances à venir (7 j) **y compris comme remplaçant actif** ; séances `PLANNED` déjà commencées « à ouvrir » | — |
| `PEDAGOGICAL_MANAGER` | nombre de classes + codes (périmètre serveur) ; séances à venir (7 j) du périmètre | justificatifs en attente périmétrés ; alternance `UNKNOWN` ; planning actif ; conflits récents |
| `ADMINISTRATION` (`SUPER_ADMIN` / `ADMIN` / `SCHOOL_ADMINISTRATION`) | comptes par statut ; justificatifs en attente (global) ; imports récents ; séances du jour | dernières opérations d'audit (non exposées) |

## 7. Preuve anti-N+1 (tableau de bord manager)

| Fixture | Classes | Séances | Requêtes SQL Hibernate |
|---|---:|---:|---:|
| A (petite) | 1 | 3 | 14 |
| B (grande) | 15 | 3 | 14 |

Croissance mesurée : **0** (14 → 14). Avant le correctif : qSmall = 14,
qLarge = **28** (une requête `findByPublicId` par classe). Assertion du
test : `qLarge − qSmall ≤ 3` (croissance bornée, indépendante du nombre
de classes) **et** `qLarge < 25` (garde-fou absolu). Le contenu
fonctionnel (`classCount`, codes de classe des séances, tous dans le
périmètre) est vérifié pour **les deux** tailles. Correctif : port de lot
`ClassGroupDirectory.findByPublicIds` + résolution groupée des libellés.
Coût per-séance restant (`checkpoints` + `session_class` par séance dans
`coursesession`) : borné (≤ 10 séances affichées), identique entre les
deux fixtures, documenté.

## 8. Nature exacte de la recette automatisée

`PriorityPathRecetteIntegrationTests` = **recette d'intégration API
Spring** (`@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate`, appels
HTTP réels). **Ce n'est pas** un e2e navigateur. Chaîne continue : un
**seul** apprenant, créé et inscrit par l'import CSV, activé via l'API
publique, réutilisé pour l'émargement, le justificatif et sa pièce
jointe. Toutes les actions métier passent par l'API ; le SQL direct ne
sert qu'aux invariants (`accountCount`, `notificationCount`, comptes de
séances de planning).

## 9. Statut Playwright / e2e navigateur

`NOT_IMPLEMENTED` — voir §3.3 (étude de faisabilité). Le grand lot G1
reste `IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED`, jamais `DEMONSTRATED`.

## 10. Nombre réel de modules Spring Modulith

**14** : `identity`, `organization`, `academic`, `enrollment`,
`alternation`, `planning`, `coursesession`, `attendance`, `studentimport`,
`notification`, `dashboard`, `audit`, `bootstrap`, `shared`
(14 `package-info.java` sous `com.esic.connect.*`). `ModularityTests`
vert. La mention « 13 modules » en fin de `G1_IMPLEMENTATION_PROGRESS.md`
(§ « Totaux consolidés G1-G ») est **erronée d'une unité** (elle oublie
que `planning` est aussi devenu réel au bloc G1-B).

## 11. Résultats de vérification (passe corrective)

### Back-end — `./mvnw clean test` (JDK 21)

| Environnement | Résultat |
|---|---|
| défaut | **`Tests run: 809, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS** |
| `TZ=UTC` | **`Tests run: 809, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS** |
| `TZ=Europe/Paris` | **`Tests run: 809, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS** |
| `ModularityTests` | **vert** (14 modules) |
| Flyway `V1 → V16` sur base fraîche `esic_test`, `ddl-auto=validate` | **`Successfully applied 16 migrations … now at version v16`** puis `ddl-auto=validate` OK — BUILD SUCCESS (69 tests ciblés, 0 échec) |

Tests ajoutés par la passe corrective : +9 net (800 → 809)
(`JustificationAttachmentIntegrationTests` +2 ; `DashboardIntegrationTests`
+3 remplaçant −1 ancien test N+1, net +6 ;
`ClassGroupDirectoryTests` +1). Aucun test supprimé, aucun `@Disabled`,
aucune assertion affaiblie.

### Front-end

| Commande | Résultat |
|---|---|
| `npm test -- --watch=false` | **71 fichiers / 600 tests / 0 échec** (Vitest ; +4 : `dashboard-api.service.spec.ts` +2, `dashboard.spec.ts` +2 dont 1 modifié) |
| `npm run lint` | « All files pass linting » |
| `npm run build` | initial **484,52 kB** brut / 122,07 kB transféré — 0 alerte de budget |
| `npm audit --audit-level=high` | **0 vulnérabilité** |

Tests front ajoutés : `dashboard-api.service.spec.ts` +2,
`dashboard.spec.ts` +2 (1 modifié).

## 12. Dettes résiduelles (inchangées ou nouvellement explicitées)

- **Audit** : 8 des 9 listeners d'audit restent des `@EventListener`
  synchrones `REQUIRES_NEW` (migration globale `AFTER_COMMIT` + outbox à
  planifier). L'échec d'audit après stockage d'une pièce est **isolé**
  mais **non rejoué** (réserve A).
- **Pièces jointes** : antivirus `NOT_IMPLEMENTED` ; balayage des
  fichiers orphelins `NOT_IMPLEMENTED` ; rétention des lignes / fichiers
  `DELETED` `À_DÉFINIR` (`R-G1-30`, `docs/07` §14) ; pas de remplacement
  direct d'une pièce (retrait + redépôt).
- **Notifications** : audience **formateur uniquement** (dette
  G1-D-AUDIENCE : apprenants / RP) ; livraison « au mieux » après commit
  **sans reprise** (dette G1-D-OUTBOX) ; pas d'email métier / push PWA /
  préférences / purge.
- **Tableau de bord manager** : 4 cartes `PARTIAL` (pas de port agrégé
  borné) ; carte administration « audit récent » non exposée ; pas de
  cache Redis (`DEC-G1-010`).
- **G1-A** : écrans d'écriture `academic` / `enrollment` / affectations /
  émission d'invitation `NOT_IMPLEMENTED` (API prêtes).
- **`coursesession`** : coût per-séance borné (checkpoints +
  `session_class`) non regroupé — acceptable (≤ 10 séances affichées).
- **e2e navigateur** `NOT_IMPLEMENTED` ; démonstration manuelle non
  consignée.

## 13. Confirmation

Aucun `push`, aucune PR, aucune fusion, aucun `commit --amend`, aucune
réécriture d'historique. Aucune migration Flyway existante modifiée
(aucune migration ajoutée : le schéma reste en **V16**). Modularité
Spring Modulith préservée (`ModularityTests` vert).
