# État courant — ESIC Connect

> **But de ce document** : donner, en une lecture courte, l'état **réel**
> du dépôt (ce qui est implémenté, testé, partiel, hors périmètre) et les
> preuves associées. Il ne contient plus la chronologie détaillée des
> tranches : celle-ci est archivée dans
> [`docs/reports/PROJECT_HISTORY.md`](reports/PROJECT_HISTORY.md).
>
> **Sources de vérité complémentaires** :
> - [`docs/reports/PROJECT_FINAL_AUDIT.md`](reports/PROJECT_FINAL_AUDIT.md)
>   — audit vérifiable de fin de tranche (checkpoint F1, 31 août 2026),
>   matrices EF-* / RG-* / AC-*, inventaire des endpoints, backlog
>   `FINAL-001..030`.
> - [`docs/reports/PROJECT_FINALIZATION_REPORT.md`](reports/PROJECT_FINALIZATION_REPORT.md)
>   — rapport du lot de finalisation F2 → F6 (produit en fin de lot).
> - `git log` — le code et les tests font foi sur ce qui est réalisé.

## Dernière mise à jour

```text
1er septembre 2026 — passe corrective probatoire G1-E / G1-F / G1-G
(branche feature/master-level-product-expansion)
```

> Cette passe corrige des réserves des blocs G1-E/F/G : isolation de
> l'échec d'audit après stockage d'une pièce jointe (CHANTIER A),
> portée réelle de la réconciliation des orphelins (B), remplaçants
> actifs dans le tableau de bord formateur (C), contexte de rôle
> multi-rôle vérifié côté serveur (D), anti-N+1 réellement prouvé (F),
> recette API rendue continue (G) et statuts documentaires réalignés.
> Détail : `docs/reports/G1_FINAL_REPORT.md` et
> `docs/reports/G1_IMPLEMENTATION_PROGRESS.md` § « Passe corrective G1-E/F/G ».

## Commit stable de référence

```text
main = e44ccb1  — Merge PR #26 (audit de finalisation F1)
Dernier commit fonctionnel : 9c5affa — import CSV contrôlé des apprenants (PR #25)
```

Toutes les tranches décrites ci-dessous sont **fusionnées sur `main`**.
Le dépôt n'a aucune branche « en cours » de référence : le lot de
finalisation se fait sur `chore/project-finalization-f2-f6`.

## Modules Spring Modulith réels (14)

> 12 modules à la finalisation F2 **+ `planning` (G1-B) + `dashboard`
> (G1-F)** = **14** (14 `package-info.java` sous
> `com.esic.connect.*` ; `ModularityTests` vert). La mention « 13 modules »
> de `G1_IMPLEMENTATION_PROGRESS.md` (fin G1-G) est erronée d'une unité.

`backend/src/main/java/com/esic/connect/` :

| Module | Rôle | Migration(s) |
|---|---|---|
| `identity` | comptes, rôles, authentification JWT, invitation/activation, administration des comptes | V1, V2, V3 |
| `organization` | site / bâtiment / salle / plage réseau CIDR | V4 |
| `academic` | année scolaire, formation, niveau, promotion, classe, affectation pédagogique + contrôle de périmètre | V5, V6 |
| `enrollment` | profil apprenant, inscription, changement de classe historisé | V7 |
| `alternation` | modèles de rythme, affectation historisée à une classe, exceptions individuelles, résolution `SCHOOL`/`COMPANY`/`UNKNOWN` | V8 |
| `planning` | import CSV de planning → simulation (0 séance, AC-007) → publication atomique versionnée (N/N+1, `SUPERSEDED`, AC-008) → séances `course_session` via le port `coursesession.PlanningSessionWriter` ; conflits formateur / classe / salle intra-fichier (G1-B) | V12, V13 |
| `coursesession` | séance (création manuelle **ou** issue d'un planning publié — G1-B), cycle `PLANNED → OPEN → CLOSED` / `CANCELLED` (G1-C), points de contrôle multiples, remplacements de formateur (G1-C.2) | V9, V10, V13, V14 |
| `attendance` | jeton d'émargement (Redis), validation, retard, présence manuelle / correction / annulation, justificatif métier **+ pièces jointes** (G1-E : port `JustificationFileStorage`, adaptateur local, validateur magic-bytes, dépôt/téléchargement `nosniff`, séquence base↔fichier avec compensation, réconciliation `@Scheduled` des `PENDING_STORAGE`, notification propriétaire), rapports + export CSV | V9, V10, V16 |
| `studentimport` | import CSV contrôlé des apprenants (lecture sécurisée, simulation, confirmation transactionnelle, purge) | V11 |
| `notification` | email d'activation via SMTP local (Mailpit) **+ centre de notifications métier persistantes** (G1-D) : table `notification`, listeners `AFTER_COMMIT` sur planning publié / séance annulée / remplaçant / justificatif examiné, idempotence `dedup_key`, API `/api/v1/me/notifications` | V15 |
| `dashboard` | tableau de bord par rôle `GET /api/v1/me/dashboard` (lecture seule, agrégats bornés) ; rôle effectif = **contexte demandé s'il est dans le JWT** sinon priorité fixe (G1-F + passe corrective D) ; carte formateur incluant les **remplaçants actifs** (passe corrective C) | — |
| `audit` | piste d'audit `audit_event` alimentée par les événements métier | V1 |
| `bootstrap` | amorçage `demo` (comptes fictifs, profil `demo` uniquement) | — |
| `shared` | types transverses, `BaseEntity`, `ApiError`, `GlobalExceptionHandler`, `ClockConfig` | — |

`ModularityTests` (Spring Modulith 1.4) est **vert** : aucune dépendance
vers un package `.internal` d'un autre module, aucun cycle.

Modules décrits dans `docs/03-architecture.md` §7 comme **architecture
cible non implémentée** : `room` (remplacé par `organization`),
`justification` (fusionné dans `attendance`, pièces jointes incluses
depuis G1-E), `claim`, `reporting` (fusionné dans `attendance`), `ai`,
`iot`. (`planning` et `dashboard` sont désormais **implémentés** — G1-B,
G1-F.)

## Migrations Flyway réelles

```text
V1  identité + audit          V8  alternance
V2  seed des 6 rôles          V9  séances + émargement
V3  invitations               V10 gestion d'assiduité + reporting
V4  organisation              V11 import CSV apprenants
V5  référentiel académique    V12 module planning (7 tables)        [G1-B]
V6  affectations pédagogiques V13 lien course_session ↔ créneau     [G1-B]
V7  profils apprenant         V14 cycle de vie séances (CANCELLED,  [G1-C]
    + inscriptions                teacher_substitution)
                              V15 table notification                [G1-D]
                              V16 justification_attachment          [G1-E]
```

Schéma **en version 16**. `spring.jpa.hibernate.ddl-auto = validate`.
Aucune donnée métier insérée par une migration. V12/V13 corrigées en
place à l'audit G1-B.1, en-tête de `V13` re-précisé au checkpoint G1-C.3
(jamais poussées ; une base ayant appliqué l'ancienne forme ne se répare
**pas** par un simple `flyway repair` — recréation ou migration
corrective explicite — voir en-tête de `V13`).

## Fonctionnalités livrées (`IMPLEMENTED_AND_TESTED`)

Sauf mention contraire, « testé » = tests automatisés passants
(`./mvnw clean test` / `npm test`), pas de démonstration manuelle
enregistrée dans le dépôt.

### Identité / accès
- Connexion email + mot de passe → JWT HS256 stateless (signature +
  `exp` + `iss` vérifiés, `401` nu). Réponse uniforme pour email
  inconnu / mauvais mot de passe / compte inactif.
- Multi-rôles ; autorités `ROLE_*` dans le JWT ; `@EnableMethodSecurity`
  + `@PreAuthorize` sur toutes les routes non publiques.
- Invitation + activation de compte (jeton `SecureRandom`, empreinte
  SHA-256 seule stockée, TTL configurable, usage unique).
- Administration des comptes : suspension / réactivation / archivage /
  attribution / retrait de rôle, avec gardes fines côté serveur
  (protection `SUPER_ADMIN`, auto-action interdite, dernier rôle actif
  protégé). Front `/administration` en lecture **et** écriture.
- Sélecteur de contexte de rôle côté front (ergonomie ; n'élargit jamais
  le JWT).

### Référentiels
- `organization` : CRUD + archivage/restauration site / bâtiment / salle,
  plages réseau CIDR IPv4/IPv6 validées (sans DNS). **Aucun écran
  Angular** — API seule.
- `academic` : CRUD + archivage année / formation / niveau / promotion /
  classe ; affectation pédagogique + `AcademicScopeGuard` (périmètre RP
  décidé côté serveur). Front `/academic` en **lecture seule**.
- `enrollment` : profil apprenant, inscription, changement de classe
  conservant l'historique ; une seule inscription active par apprenant et
  par année (contrainte SQL + isolation de la concurrence). Front
  `/students` en **lecture seule**.
- `alternation` : 4 types de rythme, `configuration_json` validé et
  canonicalisé ; affectation historisée à une classe ; exceptions
  individuelles ; résolution `SCHOOL`/`COMPANY`/`UNKNOWN`. Front
  `/alternation` en **lecture et écriture**.

### Séances & émargement
- Séance créée manuellement (motif obligatoire) **ou** issue d'un
  planning publié (G1-B, `planning_slot_public_id` renseigné) ; cycle
  strict `PLANNED → OPEN → CLOSED`, pas de réouverture ;
  `PLANNED`/`OPEN → CANCELLED` avec motif (G1-C.1) ; remplacements de
  formateur datés (G1-C.2). Pas de `PATCH`.
- Points de contrôle multiples par séance (`START` / `END` / `CUSTOM`),
  transitions concurrentes → `409`, jamais `500`.
- Jeton d'émargement **opaque** + **code court** dans Redis (TTL,
  rotation, purge à la fermeture) ; QR encode le seul jeton opaque.
  Redis indisponible → `503 ATT_TOKEN_BACKEND_UNAVAILABLE`, jamais de
  validation dégradée.
- Validation par un `STUDENT` inscrit ; anti-double présence par
  contrainte SQL (concurrence → `200` / `409` / `0×500`).
- Classement `PRESENT` / `LATE` (seuil unique `PT10M`).
- Présence manuelle / correction / annulation logique, motif
  obligatoire, historique append-only, verrou optimiste → `409`.
- Front `/sessions` (R/W), `/attendance` (émargement `STUDENT`),
  `/my-attendance` (`STUDENT`).

### Assiduité / reporting
- Justificatif : dépôt / modification tant que `PENDING` / examen ;
  `ACCEPTED` → `ABSENT → EXCUSED_ABSENCE` ; `TEACHER` exclu de l'examen.
  **Pièce jointe livrée** (G1-E — voir « Fonctionnalités partielles » pour
  les limites : antivirus et balayage d'orphelins `NOT_IMPLEMENTED`).
- Espace apprenant `/me/attendance*` : absences **dérivées** d'un point
  de contrôle fermé, jamais persistées ; aucun accès croisé (AC-017).
- Calcul de demi-journées : contexte d'alternance `COMPANY` exclu du
  dénominateur, `UNKNOWN` non satisfait compté à part.
- Rapports séance / classe / apprenant / synthèse (JSON paginé, tri
  serveur borné → `400 ATT_REPORT_INVALID_SORT`).
- Export CSV (UTF-8 + BOM, `;`, neutralisation d'injection de formule).
- Front `/attendance-management` (4 sous-rapports + file des
  justificatifs).

### Import CSV des apprenants
- Lecture sécurisée : extension `.csv`, rejet ZIP/OLE2/PDF/octet nul,
  UTF-8 strict, RFC 4180 maison, séparateur `,`/`;` auto-détecté ;
  fichier **jamais écrit sur disque** (empreinte SHA-256 seule) ;
  `2 MiB` max → `413 IMP_FILE_TOO_LARGE`.
- **Simulation** sans aucune écriture métier (invariant T1).
- **Confirmation** transactionnelle unique : verrou `SELECT … FOR
  UPDATE`, re-validation complète, idempotence `APPLIED`, rollback total
  sur toute exception (T3), e-mail seulement `AFTER_COMMIT` (T4),
  génération atomique du numéro `ESIC-{annéeDébut}-{NNNNN}`.
- Audit `AFTER_COMMIT` + `REQUIRES_NEW` (aucune trace si rollback, T5) ;
  purge `@Scheduled`.
- 6 endpoints `/api/v1/student-imports` ; décision fine de périmètre
  `PEDAGOGICAL_MANAGER` = ses propres jobs.
- Front `/students/import` (R/W).

### Transverse
- Audit `audit_event` alimenté par tous les flux métier ; sans PII, sans
  jeton, sans IP.
- Piste de vérification : matrices de sécurité `*SecurityTests`
  (`401` / `403` / `200`) par module ; concurrence testée (inscriptions,
  affectations, émargement, corrections, confirmations d'import) ;
  invariants transactionnels T1–T6 de l'import.
- Front Angular 21.2 zoneless / standalone / Material ; JWT et contexte
  de rôle **en mémoire seule** (aucun `localStorage` / `sessionStorage`,
  asserté) ; build de production sous le budget de 500 kB.

## Fonctionnalités partielles (`PARTIAL`)

| Sujet | Ce qui existe | Ce qui manque |
|---|---|---|
| Points de contrôle (EF-ATT-003) | N points de contrôle par séance | les 4 types nommés (`MORNING_ARRIVAL`…) et le calcul journée/demi-journée strict du cahier ne sont pas modélisés tels quels |
| Retards (EF-ATT-005) | seuil unique `PT10M` → `LATE` | paliers 15 / 30 min, validation manuelle automatique après 30 min |
| Alternance ↔ assiduité | contexte résolu, consommé par le reporting ; module `planning` livré (G1-B) : les séances peuvent venir d'un planning publié | pas d'avertissement d'alternance sur un créneau jour-entreprise à la publication (DEC-G1-006) ; le calcul « demi-journées attendues » ne croise pas encore le rythme d'alternance de façon systématique |
| Justificatif avec pièce jointe (EF-JUS-001/002) → **`IMPLEMENTED_AND_TESTED`** | dépôt multipart propriétaire, validation extension+MIME+magic-bytes, `V16` métadonnées, contenu hors base / hors webroot, séquence base↔fichier avec compensation, réconciliation `@Scheduled` des `PENDING_STORAGE`, téléchargement `Content-Disposition: attachment`+`nosniff` (propriétaire + examinateur périmétré), notification propriétaire à l'examen. **Échec d'audit après un stockage réussi : isolé** (201 rendu, pièce durable, trace non rejouée — passe corrective A) | **antivirus `NOT_IMPLEMENTED`** (`DEC-G1-E-ANTIVIRUS`) ; **balayage des fichiers orphelins `NOT_IMPLEMENTED`** — la réconciliation ne traite QUE les lignes `PENDING_STORAGE`, un fichier laissé par un retrait dont la suppression best-effort a échoué (ligne `DELETED`) n'est pas balayé (passe corrective B) ; remplacement direct d'une pièce (retrait puis redépôt) ; rétention `DELETED` `À_DÉFINIR` (`R-G1-30`) |
| Notifications (EF-NOTIF-001 `IMPLEMENTED_AND_TESTED` ; EF-NOTIF-002 / RG-033 `PARTIAL`) | email d'activation **+ centre in-app persistant** (G1-D + G1-D.1) : planning publié / séance annulée / remplaçant affecté / **remplacement terminé** → notifications after-commit pour les **formateurs** (principal + remplaçants `ACTIVE` + remplaçant tout juste terminé) ; idempotence `dedup_key` ; isolation par destinataire ; API `/api/v1/me/notifications` + cloche + centre Angular (liens en liste blanche par rôle) | notifications aux **apprenants / responsables pédagogiques** (dette G1-D-AUDIENCE), garantie de livraison / reprise (best effort après commit — dette G1-D-OUTBOX), email métier, push PWA, préférences par type, file persistante / DLQ, purge / rétention (`À_DÉFINIR`) |
| Tableau de bord par rôle (G1-F, CDC §25) | `GET /api/v1/me/dashboard` typé par rôle, périmètre serveur ; carte formateur **avec remplaçants actifs** (C) ; anti-N+1 **prouvé** (1 vs 15 classes — F) ; contexte de rôle multi-rôle **vérifié côté serveur** (403 si non détenu — D) | cartes manager **`PARTIAL`** : justificatifs en attente périmétrés, alternance `UNKNOWN`, planning actif, conflits récents (pas de port agrégé borné — dette G1-F) ; carte administration : dernières opérations d'audit non exposées ; pas de cache Redis |
| Rapports « officiels » (docs/02 §24.5) | calcul demi-journées + export CSV | mise en page (logo ESIC, PDF, identifiant de document), export Excel |
| OpenAPI | `/v3/api-docs` + `/swagger-ui` au runtime | pas d'`openapi.json` versionné (voir F3) |
| Redis | jetons d'émargement uniquement | cache de planning, rate-limiting, droits calculés |
| Actuator / supervision | `/actuator/health` (`show-details: never`) | métriques, logs structurés JSON |
| Rétention / purge | purge planifiée de l'import CSV | audit, invitations `PENDING` échues, présences (voir F3 pour la doc de politique) |
| Performance (TP-001..006) | concurrence fonctionnelle testée | mesures de latence reproductibles (voir F3) |
| Accessibilité (docs/08 §16) | structure sémantique, labels, `role="alert"`, clavier (revendiqués par composant) | audit outillé — voir F3 |
| EF-USER-001 | création via invitation / fixtures | pas d'endpoint `POST /users` de création `PENDING_ACTIVATION` |

## Mise à jour G1 — livraison des blocs G1-A et G1-B (1er septembre 2026)

> Le **grand lot produit G1** (branche
> `feature/master-level-product-expansion`) lève une partie du périmètre
> classé `HORS_PÉRIMÈTRE_ASSUMÉ` à la finalisation F2. Suivi détaillé,
> commandes et résultats exacts :
> [`docs/reports/G1_IMPLEMENTATION_PROGRESS.md`](reports/G1_IMPLEMENTATION_PROGRESS.md).

- **G1-A — référentiel organisationnel Angular** (`IMPLEMENTED_FULL_SUITE_GREEN`,
  commit `feat(frontend): exposer les parcours administratifs existants`).
  Le module back-end `organization` (V4) avait ses endpoints mais **aucun
  écran** : livré de bout en bout — sites (liste / création / modification
  / archivage), bâtiments et salles (création + liste + archivage depuis la
  fiche d'un site), plages réseau CIDR pour un contexte `SUPER_ADMIN`.
  `EF-ROOM-001` → `IMPLEMENTED_AND_TESTED` (écrans). Les écritures
  `academic` / `enrollment` / affectations / émission d'invitation restent
  une **dette de G1-A** (API prêtes, aucune UI ne simule un endpoint
  absent — cf. `G1_IMPLEMENTATION_PLAN.md` §3.1).
- **G1-B — module `planning` complet** (`IMPLEMENTED_FULL_SUITE_GREEN`,
  commits `feat(planning): créer le schéma et le modèle du module planning`,
  `feat(planning): simuler les imports CSV de planning`,
  `feat(planning): publier des plannings versionnés en séances`,
  `feat(frontend): ajouter le parcours planning`). Nouveau module Spring
  Modulith `planning` + migrations **V12** (7 tables) et **V13** (lien
  additif `course_session ↔ planning_entry`). Parcours livré et testé :
  **import CSV → simulation (aucune séance créée — invariant T1, AC-007)
  → revue des lignes / anomalies → publication atomique → versionnement
  N/N+1 (ancienne version `SUPERSEDED`, AC-008) → séances `course_session`
  d'origine planning créées / réutilisées / supersédées via le port
  public `coursesession.PlanningSessionWriter` (DEC-G1-001, aucun partage
  d'entité JPA)**. Détection de conflit formateur / classe / salle +
  hors plage horaire intra-fichier (DEC-G1-005). Endpoints
  `POST /api/v1/planning-imports`, `GET .../{id}`, `.../{id}/rows`,
  `.../{id}/publish`, `.../{id}/cancel`,
  `GET /api/v1/planning/versions(/{id})`. Écrans Angular
  `/planning/import`, `/planning/import/:jobId`, `/planning/versions`.
  → **`EF-PLAN-001..005`, `EF-PLAN-007`, `EF-SES-001`, `RG-016`,
  `RG-030..RG-035`, `AC-007`, `AC-008` : `IMPLEMENTED_AND_TESTED`**.
  Restent hors G1-B : `EF-PLAN-006` (création manuelle plein calendrier,
  `HORS_PÉRIMÈTRE_ASSUMÉ`), les avertissements d'alternance sur un créneau
  jour-entreprise (DEC-G1-006), le conflit avec des séances déjà publiées
  hors du fichier courant (post-G1).
- **État des suites** après G1-B :
  `cd backend && ./mvnw clean test` → **713 tests, 0 échec, 0 erreur**
  (schéma V13, `ModularityTests` vert) ;
  `cd frontend && npm test -- --watch=false` → **66 fichiers / 548 tests /
  0 échec** ; `npm run lint` OK ; `npm run build` **484,68 kB** brut
  (0 alerte de budget) ; `npm audit --audit-level=high` → 0 vulnérabilité.
- Jeux de données de démonstration ajoutés :
  `docs/demo-data/planning-demo.csv` et `planning-conflicts-demo.csv`
  (fictifs, résultats attendus décrits dans `docs/demo-data/README.md`).

### Audit correctif G1-B.1 (1er septembre 2026)

Voir `docs/reports/G1_IMPLEMENTATION_PROGRESS.md` § « Audit G1-B.1 » et
`docs/reports/G1_REQUIREMENTS_TRACEABILITY.md` §3bis. Principaux effets :
- identité de créneau corrigée : `course_session.planning_entry_public_id`
  (nom trompeur) → **`planning_slot_public_id`** (identité *stable*
  déterministe) ; `planning_entry.slot_public_id` ajouté ; V12/V13
  corrigées en place (jamais poussées) ;
- publication concurrente **strictement idempotente** (le perdant renvoie
  `alreadyPublished=true`, jamais `FAILED`) ; test rollback + `FAILED`
  **déterministe** ;
- garde centralisée `CourseSession.isOperational()` : une séance
  supersédée est **inactive** partout (liste, résolution d'émargement,
  ouverture, jeton, rapports) ; seul l'historique de planning la montre ;
- conflit **formateur / classe** vs séances *déjà publiées* détecté à la
  simulation (port `CourseSessionDirectory.findOperationalSessionWindows`) ;
  conflit **salle** vs existant non couvert (`coursesession` sans
  `room_code` — documenté) ;
- exigences reclassées : `EF-PLAN-007`/`RG-032`/`RG-033`/`RG-034`/`RG-035`
  → **`PARTIAL`** ; `AC-008` versionnement OK, devenir des séances
  `PARTIAL` ; G1-A **bloc** = `PARTIAL` ;
- suites **719/0** back (3 fuseaux) / **550/0** front.

### Bloc G1-C.1 — annulation des séances (1er septembre 2026)

`IMPLEMENTED_FULL_SUITE_GREEN`. Migration **V14** :
`course_session` gagne `cancellation_reason` / `cancelled_at` /
`cancelled_by_id` + `CHECK` étendu ; table `teacher_substitution` créée
(consommée en G1-C.2). `SessionLifecycle.CANCELLED` ;
`POST /api/v1/sessions/{id}/cancel {reason}` (`204` ; `MANAGE_ROLES` ;
motif obligatoire → `400` ; `CLOSED`/déjà `CANCELLED` → `409` ;
transitions strictes, pas d'idempotence). Effets : points de contrôle
non terminaux → `CANCELLED`, jetons Redis purgés (événement `CANCELLED`),
**aucune absence dérivée** (garde `operational()`), audit
`SESSION_CANCELLED` (motif hors événement). Course concurrente
ouvrir/annuler → `409` via `@ExceptionHandler(OptimisticLockingFailureException)`,
jamais `500`. Front `/sessions/:publicId` : bouton « Annuler la séance »
+ confirmation avec motif. `EF-SES-004` → **`IMPLEMENTED_AND_TESTED`**.
Suites : back **723/0**, front **554/0**.

### Bloc G1-C.2 — remplacements de formateur (1er septembre 2026)

`IMPLEMENTED_FULL_SUITE_GREEN`. Table `teacher_substitution` (V14)
consommée : entité `TeacherSubstitution` + `SubstitutionService` +
endpoints `GET/POST /api/v1/sessions/{id}/substitutions` et
`POST …/{substitutionId}/end`. Le **formateur principal n'est jamais
écrasé** (`original_teacher_user_id` figé). Contrôles : remplaçant
`TEACHER` actif ≠ principal, période valide + motif obligatoire, **une
seule substitution `ACTIVE` applicable** (verrou de ligne sur la séance
+ contrôle de chevauchement), séance `CLOSED` non substituable,
`CANCELLED`/supersédée → `404`. `CourseSessionAccessGuard` étendu : un
remplaçant dont une substitution `ACTIVE` couvre l'instant courant
obtient `MANAGE` ; un remplaçant expiré ou terminé n'a aucun droit ;
décision **serveur**, la lecture n'est pas élargie. `POST` réservé à
`CREATE_ROLES` (`TEACHER` exclu — « ne valide pas lui-même son
remplacement »). Audit `SESSION_SUBSTITUTION_ADDED` / `…_ENDED`. Front
`/sessions/:publicId` : section « Remplacements ». Pas d'endpoint
`/history` dédié. `EF-SES-005` / `CAD §24 RG-12` / `CDC §43 RG-015` →
**`IMPLEMENTED_AND_TESTED`**. Suites : back **729/0** (3 fuseaux),
front **557/0**.

### Bloc G1-C.3 — audit correctif (1er septembre 2026)

`IMPLEMENTED_FULL_SUITE_GREEN`. Checkpoint correctif de G1-C avant G1-D.

- **Lecture historique d'une séance `CANCELLED`** : `GET /api/v1/sessions/{id}`
  la renvoie désormais aux rôles autorisés (`status = CANCELLED`, motif,
  `cancelledAt`, `openedAt` conservé, `closedAt = null`, formateur
  principal, points de contrôle terminaux, **aucun identifiant SQL**).
  Gardes explicites : `isHistoricallyReadable()` (`= !superseded` — la
  `CANCELLED` passe, la supersédée par le planning est masquée) vs
  `isOperational()` (mutations). `GET …/checkpoints` et `…/substitutions`
  restent lisibles pour une `CANCELLED`. `open` / jeton / points de
  contrôle → `404`. La séance reste absente de la liste opérationnelle.
  Le front recharge l'état persisté après annulation (plus de patch
  local ; F5 stable).
- **Remplaçant actif visible en liste** : `GET /sessions` inclut, pour un
  formateur, les séances où il est remplaçant `ACTIVE` couvrant l'instant
  courant (une requête bornée, pas de N+1). Bandeau front « Vous
  intervenez comme remplaçant ». Remplaçant futur / expiré / terminé :
  aucun droit ; `SCHOOL_ADMINISTRATION` : aucun droit de gestion
  supplémentaire.
- **Période d'un remplacement vs séance** : doit **chevaucher réellement**
  la séance, marge ≤ 60 min avant le début / après la fin. Nouveau code
  `422 SESSION_SUBSTITUTION_OUTSIDE_SESSION` (distinct de
  `400 SESSION_SUBSTITUTION_PERIOD_INVALID` = période malformée).
- **Audit `coursesession` après commit uniquement** :
  `CourseSessionAuditListener` migré vers
  `@TransactionalEventListener(AFTER_COMMIT)` + délégation à
  `CourseSessionAuditWriter` (`REQUIRES_NEW`). Une transaction métier qui
  rollbacke ne laisse **aucune** ligne d'audit de succès (test dédié avec
  faute injectée, sans modifier de bean de production). Les 8 autres
  listeners d'audit restent en dette assumée.
- **Purge Redis après commit** : `CourseSessionCloseListener` et
  `AttendanceCheckpointCloseListener` migrés vers `AFTER_COMMIT` — la
  purge des jetons n'a lieu qu'après commit réussi (un rollback laisse la
  séance `OPEN` et ses jetons intacts).
- **Correction documentaire Flyway** : un simple `flyway repair` ne peut
  **pas** corriger une base ayant appliqué l'ancienne forme de V12/V13
  (repair ne modifie pas le schéma) — recréation ou migration corrective
  explicite requise. Décision initiale conservée, correction datée
  ajoutée à l'en-tête de `V13`, ce fichier, `G1_IMPLEMENTATION_PROGRESS.md`
  et `docs/10-journal-ia.md`.

Suites : back **729 → 735/0** (3 fuseaux ; Flyway `V1→V14` rejoué sur
`esic_test` vierge), front **557 → 559/0**, `lint` / `build` (484,68 kB) /
`audit` verts. `EF-SES-004`, `EF-SES-005`, `CAD §24 RG-12`, `CDC §43
RG-015` → **`IMPLEMENTED_AND_TESTED`** (consolidés). Détail :
[`G1_IMPLEMENTATION_PROGRESS.md`](reports/G1_IMPLEMENTATION_PROGRESS.md)
§ « Audit G1-C.3 ».

**G1-C est terminé et consolidé** (C.1 + C.2 + C.3). Reste non livré :
`PATCH /sessions/{id}` d'une séance manuelle `PLANNED` (non requis).

### Bloc G1-D — centre de notifications persistantes (1er septembre 2026)

`IMPLEMENTED_FULL_SUITE_GREEN`. Migration **`V15`** : table `notification`
(`recipient_user_id` FK `RESTRICT`, `type`, `title`, `body` **neutre**,
`resource_type` / `resource_public_id`, `status ∈ {UNREAD,READ,ARCHIVED}`,
`dedup_key CHAR(64) UNIQUE`, `CHECK ((status='UNREAD') = (read_at IS NULL))`,
index `(recipient, status, created_at)`).

- **Livraison après commit** : `NotificationListener`
  (`@TransactionalEventListener(AFTER_COMMIT)`) consomme
  `planning.PlanningPublishedEvent` et
  `coursesession.CourseSessionChangeEvent` (`CANCELLED` /
  `SUBSTITUTION_ADDED` / `SUBSTITUTION_ENDED`). Une transaction métier qui
  rollbacke ⇒ **aucune** notification (testé). `open` / `close` d'une
  séance ⇒ aucune notification.
- **Idempotence** : `NotificationWriter` (orchestration) →
  `NotificationRowWriter` (`REQUIRES_NEW` **par ligne**) ; `dedup_key` =
  `SHA-256(type | resourcePublicId | recipientUserId | eventKey)` avec
  `eventKey` = `CourseSessionChangeEvent.eventId` (nouveau champ `UUID`,
  additif) ou `versionPublicId`. Un rejeu du listener ⇒ **1** ligne.
- **Destinataires dérivés serveur** = **formateurs** (principal +
  remplaçants `ACTIVE`), via deux nouvelles méthodes 100 % UUID publics
  de `CourseSessionDirectory` (chargement groupé, pas de N+1). Un compte
  **archivé** n'est jamais destinataire. Apprenants / responsables
  pédagogiques : **prolongement documenté** (nouveaux ports
  `enrollment` / `academic` requis).
- **API** `/api/v1/me/notifications` (`@PreAuthorize("isAuthenticated()")`,
  isolation par destinataire côté service) : liste paginée (tri
  `createdAt DESC, id DESC`, `size ≤ 100`), `unread-count`, `{id}/read`
  (idempotent ; `404` — pas `403` — sur une notif d'autrui), `read-all`
  (borné au destinataire). `NotificationExceptionHandler` : `NOTIF_NOT_FOUND`
  (`404`), `NOTIF_INVALID_STATUS` (`400`), `NOTIF_UNAUTHENTICATED` (`401`).
- **Front** : cloche `mat-badge` dans l'`app-shell` (rafraîchie init /
  navigation / 60 s), route `/notifications` (tout rôle) — centre avec
  filtre Toutes / Non lues, marquage lu / tout lu, `loading` / `empty` /
  `error` + reprise, pagination, lien vers la séance / les versions.
- **Limites** : audience formateur uniquement ; pas de préférences, pas
  de push PWA / email métier, pas de purge (dettes documentées) ;
  `SESSION_SUBSTITUTION_ENDED` ne notifie pas le remplaçant tout juste
  terminé.

Suites : back **735 → 743/0** (3 fuseaux ; Flyway `V1→V15` rejoué sur
`esic_test` vierge), front **559 → 570/0**, `lint` / `build`
(484,81 kB) / `audit` verts. Détail :
[`G1_IMPLEMENTATION_PROGRESS.md`](reports/G1_IMPLEMENTATION_PROGRESS.md)
§ « G1-D ».

### Audit correctif G1-D.1 (1er septembre 2026)

`IMPLEMENTED_FULL_SUITE_GREEN`. Voir
[`G1_IMPLEMENTATION_PROGRESS.md`](reports/G1_IMPLEMENTATION_PROGRESS.md)
§ « Audit G1-D.1 » et
[`G1_REQUIREMENTS_TRACEABILITY.md`](reports/G1_REQUIREMENTS_TRACEABILITY.md)
§5ter.

- **`SESSION_SUBSTITUTION_ENDED` notifie le remplaçant tout juste
  terminé** : `CourseSessionChangeEvent` porte un champ additif
  `affectedUserPublicIds` (UUID publics, jamais de clé SQL / entité JPA) ;
  `SubstitutionService` y place l'UUID public du remplaçant concerné
  (`ADDED` et `ENDED`) ; `NotificationListener` l'ajoute aux
  destinataires. Tests : principal + remplaçant terminé notifiés
  exactement une fois ; deux remplacements successifs → chacun ne reçoit
  que sa propre fin ; fin concurrente → `{204, 409}`, jamais `5xx`, une
  seule notification.
- **Frontière transactionnelle par ligne durcie** : `NotificationRowWriter`
  ne rattrape plus l'exception de persistance dans sa transaction
  `REQUIRES_NEW` (elle devenait `rollback-only` → `UnexpectedRollbackException`
  qui interrompait les destinataires suivants) ; `NotificationWriter`
  décide **par destinataire** (doublon `dedup_key` ⇒ succès idempotent ;
  autre erreur ⇒ journalisée sans PII, suivant traité). Test : échec
  d'un destinataire ⇒ les autres notifiés ; échec **complet** du writer
  après commit ⇒ annulation `204`, séance `CANCELLED` persistée, 0
  notification.
- **Liens du centre de notifications en liste blanche par rôle** :
  `notificationLink(n, roles)` — lien `/sessions/:id` seulement si le
  rôle couvre `CourseSessionWeb.READ_ROLES`, `/planning/versions` seulement
  si `PlanningWeb.MANAGE_ROLES` ; sinon **aucun lien**, corps toujours
  lisible. Aucun `targetPath` serveur, aucune URL libre.
- **Compteur de la cloche** : aucun sondage hors session authentifiée
  (compteur remis à 0), garde « un seul sondage à la fois ».
- **Reclassement honnête** : `EF-NOTIF-001` → `IMPLEMENTED_AND_TESTED` ;
  **`EF-NOTIF-002` / `RG-033` → `PARTIAL`** (audience **formateur
  uniquement** ; livraison « au mieux » après commit **sans reprise**).
  Dettes : **G1-D-OUTBOX** (outbox transactionnelle), **G1-D-AUDIENCE**
  (apprenants / RP), rétention `À_DÉFINIR` (`R-G1-30`, `docs/07` §14).
  Préférences : non exigées, non ajoutées.

Suites : back **743 → 749/0** (`Notification*` +6, 3 fuseaux ; Flyway
`V1→V15` rejoué sur `esic_test` vierge), front **570 → 574/0**, `lint` /
`build` (484,81 kB) / `audit` verts.

### Bloc G1-E — pièces jointes des justificatifs : checkpoint 1 (1er septembre 2026)

Checkpoint « schéma + modèle + stockage » (socle ; **complété par les
checkpoints 2-4 ci-dessous**). Détail :
[`G1_IMPLEMENTATION_PROGRESS.md`](reports/G1_IMPLEMENTATION_PROGRESS.md)
§ « G1-E ».

- Migration **`V16`** : table `justification_attachment` — **métadonnées
  uniquement** (`storage_key` opaque unique jamais dérivée du nom
  client, `content_type` re-dérivé des magic bytes ∈
  {`application/pdf`,`image/jpeg`,`image/png`}, `size_bytes`, `sha256`,
  `status ∈ {PENDING_STORAGE,STORED,DELETED}`, une seule pièce active
  par justificatif via colonne générée). Le **contenu n'est jamais en
  base**.
- Port public `com.esic.connect.attendance.JustificationFileStorage`
  (le métier ne dépend jamais de `java.nio.file`) + adaptateur
  `LocalFilesystemJustificationFileStorage` : clé dispersée `aa/bb/<uuid>`,
  écriture temporaire + **déplacement atomique**, taille appliquée
  **pendant le flux**, SHA-256 calculé pendant l'écriture, **garde
  anti-traversal**, répertoire configurable **hors webroot**
  (`app.attendance.justification-storage-path`).
- `JustificationFileSafetyValidator` (pur) : extension + type déclaré +
  **magic bytes** (`%PDF-` / JPEG / PNG) → **type re-dérivé du contenu**,
  rejet ZIP / OLE2, cohérence extension ↔ contenu, nom assaini.
- **Antivirus : `NOT_IMPLEMENTED`** — aucun moteur dans l'architecture ;
  contrôle structurel seul, jamais « garanti sans malware »
  (`DEC-G1-E-ANTIVIRUS`).
- **Aucun endpoint, aucun écran** à ce checkpoint. Restent : service de
  dépôt + compensation base/fichier (DEC-G1-009), tâche de
  réconciliation, endpoints multipart + téléchargement (`nosniff` +
  `Content-Disposition: attachment`), audit / notifications, upload
  Angular.
- Checkpoint 1 : back **749 → 772/0** (Flyway `V1→V16` sur `esic_test`
  vierge, `ModularityTests` vert).

### Bloc G1-E — pièces jointes des justificatifs : livraison complète (checkpoints 2-4, 1er septembre 2026)

`IMPLEMENTED_AND_TESTED`. Commits `1835532` + `5d5f451`.
Dépôt multipart propriétaire (`POST/GET/DELETE
/api/v1/me/attendance/justifications/{id}/attachment`), téléchargement
propriétaire **et** examinateur périmétré (`Content-Disposition:
attachment` + `nosniff` + type re-dérivé ; hors périmètre → `404`),
séquence base↔fichier avec compensation (`JustificationAttachmentStore`),
réconciliation `@Scheduled` bornée des `PENDING_STORAGE`, notification
`AFTER_COMMIT` du propriétaire à l'examen. `EF-JUS-001` /
`RG-071` / `CDC §21.5` → `IMPLEMENTED_AND_TESTED` (antivirus excepté).

### Bloc G1-F — tableaux de bord par rôle (1er septembre 2026)

`IMPLEMENTED_AND_TESTED` par carte (jamais un `IMPLEMENTED_FULL_SUITE_GREEN`
global). Nouveau module `dashboard` ; `GET /api/v1/me/dashboard` typé par
rôle, lecture seule, agrégats `COUNT`/`GROUP BY`/`Pageable`, DTO sans
identifiant SQL ni e-mail, périmètre RP décidé serveur
(`AcademicScopeDirectory`), `STUDENT` = ses seules données (AC-017).
Cartes `PARTIAL` documentées (manager : justificatifs périmétrés,
alternance `UNKNOWN`, planning actif, conflits ; administration : audit
récent). Front `/dashboard` section « Mon activité ».

### Bloc G1-G — recette API du parcours prioritaire (1er septembre 2026)

`IMPLEMENTED_AND_TESTED` (recette **API**, pas navigateur) ;
`IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED` (aucune manipulation manuelle
consignée). `PriorityPathRecetteIntegrationTests` (`@SpringBootTest`,
`TestRestTemplate`) rejoue référentiel → import apprenants → **activation
d'un apprenant réellement importé** → import planning (AC-007) →
publication → émargement de **ce même apprenant** → rapport + export CSV →
annulation + notification → remplacement → justificatif + pièce jointe →
acceptation + notification → tableaux de bord. e2e **navigateur** :
`NOT_IMPLEMENTED` (Playwright absent, aucun navigateur, coût
disproportionné — `DEC-G1-011`).

### Passe corrective probatoire G1-E / G1-F / G1-G (1er septembre 2026)

Branche `feature/master-level-product-expansion`. Voir
[`G1_FINAL_REPORT.md`](reports/G1_FINAL_REPORT.md) et
`G1_IMPLEMENTATION_PROGRESS.md` § « Passe corrective G1-E/F/G ».

- **A** — `AttendanceJustificationService.uploadOwnAttachment` : l'échec
  de la trace d'audit **après** un stockage réussi (fichier + ligne
  `STORED` committés) est **isolé** → l'API répond `201`, la pièce reste
  durable et téléchargeable, l'échec est journalisé (dette d'audit
  assumée, non rejouée). Test d'intégration avec faute d'audit injectée.
- **B** — la réconciliation ne traite **que** les lignes
  `PENDING_STORAGE` ; balayage des fichiers orphelins (ligne `DELETED`,
  fichier subsistant) = **`NOT_IMPLEMENTED`** (scan de répertoire sûr
  jugé disproportionné : liens symboliques / traversée / TOCTOU /
  propriété de fichier incertaine). Test de figure de la portée.
- **C** — `CourseSessionDirectory.findUpcomingForTeacher` inclut
  désormais les séances où l'utilisateur est **remplaçant `ACTIVE`
  couvrant l'instant courant** (mêmes règles que `GET /sessions`,
  G1-C.3), en une requête, sans doublon. Corrige aussi le filtre « à
  ouvrir » (`SessionLifecycle.PLANNED` au lieu d'une comparaison de
  chaîne toujours fausse).
- **D** — `GET /api/v1/me/dashboard?context=<rôle>` : le contexte demandé
  est **vérifié contre les autorités du JWT** — rôle non détenu →
  `403 DASHBOARD_CONTEXT_NOT_HELD`, jamais d'élévation ; absent →
  priorité fixe déterministe. Le front (compte multi-rôles) transmet le
  contexte actif et recharge à son changement.
- **F** — N+1 réel corrigé : `findSessionsForClasses` résolvait les
  classes par `findByPublicId` **dans une boucle**. Nouveau port de lot
  `ClassGroupDirectory.findByPublicIds` (1 requête) + résolution groupée
  des libellés dans `DashboardService`. Preuve : test comparatif
  **1 classe vs 15 classes** — le nombre de requêtes ne croît pas avec
  le nombre de classes.
- **H** — statuts et décompte de modules réalignés (14 modules).

## Hors périmètre assumé (`HORS_PÉRIMÈTRE_ASSUMÉ`)

Décidé pour cette livraison (prototype), assumé et documenté — jamais
présenté comme livré. Détail dans le README (§ « Périmètre non livré »),
`docs/reports/PROJECT_FINAL_AUDIT.md` §7.4 et l'addendum de finalisation
des `docs/01` et `docs/02`.

- ~~**Import du planning + publication + création des séances depuis un
  planning** — EF-PLAN-001..007, EF-SES-001, RG-016, AC-007, AC-008~~ →
  **livré au bloc G1-B** (voir « Mise à jour G1 » ci-dessus).
  `EF-PLAN-006` (création manuelle plein calendrier) reste
  `HORS_PÉRIMÈTRE_ASSUMÉ`.
- Séances : ~~annulation (EF-SES-004)~~ → **livrée (G1-C.1)** ;
  ~~affectation d'un remplaçant (EF-SES-005)~~ → **livrée (G1-C.2)** ;
  `PATCH` d'une séance manuelle `PLANNED` — non livré (non requis).
- QR fixe de salle + contrôle réseau CIDR (référentiel `site_network_range`
  présent, non consommé) — EF-ROOM-002, EF-ATT-008.
- Scan caméra mobile (code court uniquement).
- WebAuthn / passkeys, MFA TOTP, Cloudflare Turnstile / anti-bot.
- Réclamations / messagerie (EF-CLAIM-001/002), départ anticipé,
  import Excel `.xlsx` / multifeuille, groupes temporaires.
  (~~justificatif avec pièce jointe~~ → **livré (G1-E)**, hors antivirus
  et balayage d'orphelins — `NOT_IMPLEMENTED`.)
- Service IA (FastAPI, mapping de colonnes, score d'anomalie),
  IoT / MQTT / Raspberry Pi (broker Mosquitto démarré, aucun code).
- PWA installable / offline / push.
- Mot de passe oublié / réinitialisation, `/auth/logout` + révocation de
  session (JWT stateless assumé).
- Déploiement cloud AWS / staging / HTTPS / HA.
- Sauvegarde / restauration outillée et testée.

## Résultats du dernier audit (run F1 — 31 août 2026)

OpenJDK 21, MySQL 8.4, Redis 7, Node 24.

| Commande | Résultat |
|---|---|
| `cd backend && ./mvnw clean test` | **BUILD SUCCESS — 682 tests, 0 échec, 0 erreur, 0 ignoré** (80 classes, `ModularityTests` vert, schéma V11) |
| `cd frontend && npm test -- --watch=false` | **53 fichiers, 471 tests, 0 échec** (Vitest + jsdom) |
| `cd frontend && npm run lint` | « All files pass linting » |
| `cd frontend && npm run build` | initial **483,26 kB** brut / 122,84 kB transféré — 0 alerte de budget ; **2 avertissements** `NG8107` / `NG8102` (`session-detail.html`), non bloquants |
| `cd frontend && npm audit` | **0 vulnérabilité** (dev + prod) |

Ces chiffres **remplacent** tous les totaux intermédiaires (548 / 567 /
454 / 502 …) qui subsistent dans l'archive et dans d'anciennes versions
de `docs/09-matrice-rncp.md`. Le lot F2 → F6 re-mesure et consigne les
chiffres à jour dans `docs/reports/PROJECT_FINALIZATION_REPORT.md`.

**Mise à jour F3 (31 août 2026)** — après le checkpoint F3 :
- back-end `./mvnw clean test` → **682 tests, 0 échec** (inchangé — les 2
  tests de mesure de performance ajoutés portent le tag JUnit `perf`,
  **exclus** du run par défaut ; `./mvnw test -Pperf` les exécute) ;
- front-end `npm test` → **475 tests, 0 échec** (471 → 475 : +2 fichiers
  d'accessibilité `*.a11y.spec.ts` avec `axe-core`) ; `npm run lint` OK ;
  `npm run build` **483,26 kB** — **les 2 avertissements `NG8107` /
  `NG8102` de `session-detail.html` sont corrigés** ; `npm audit` → 0
  vulnérabilité (`axe-core` ajouté en `devDependencies`) ;
- mesures indicatives reproductibles : `docs/reports/PERF_NOTES.md` ;
- décision Testcontainers : `docs/reports/TEST_ISOLATION_DECISION.md` ;
- politique de rétention réelle vs cible : `docs/07-securite-rgpd.md` §14.

**Mise à jour F5 (31 août 2026)** — durcissement HTTP :
- **CORS restrictif implémenté** (`SecurityConfig.corsConfigurationSource`,
  piloté par `APP_ALLOWED_ORIGINS`, jamais `*`, `allowCredentials=false`,
  méthodes et en-têtes limités) — `NOT_IMPLEMENTED` → `IMPLEMENTED_AND_TESTED` ;
- **`Content-Security-Policy` + `Referrer-Policy: no-referrer`** ajoutées
  explicitement (`script-src 'self'`, pas d'`unsafe-eval` ; `style-src`
  et `img-src data:` tolérants pour Swagger UI, documenté) ;
- test d'intégration `HttpSecurityHeadersIntegrationTests` (4) : en-têtes
  par défaut Spring Security (`nosniff`, `X-Frame-Options: DENY`,
  anti-cache) + CSP + `Referrer-Policy` présents ; HSTS **non exigé** en
  HTTP ; CORS accepté depuis une origine listée, **rejeté (403) sinon** ;
- javadoc `SecurityConfig` corrigée (26 contrôleurs, `@PreAuthorize` par
  module) ;
- **rate-limiting `/auth/login` : `NOT_IMPLEMENTED` — dette assumée**
  (`docs/07` §5) : un limiteur *fail-safe*, sans énumération de comptes,
  testé, dépasse le périmètre de ce lot ; refus déjà uniforme + BCrypt ;
- **écrans manquants** (gestion des salles, affectation d'un responsable
  pédagogique, écritures `academic` / `enrollment`, émission
  d'invitation) : `NOT_IMPLEMENTED` — endpoints API livrés, **dette
  assumée** (F1 §3.2) ;
- back-end `./mvnw clean test` re-vérifié après F5 (voir
  `docs/reports/PROJECT_FINALIZATION_REPORT.md`).

**Mise à jour F6 (31 août 2026)** — démonstration jury :
- **5ᵉ compte de démonstration** `responsable@example.test`
  (`PEDAGOGICAL_MANAGER` + `TEACHER`) → sélecteur de contexte de rôle
  (EF-AUTH-003) enfin **démontrable** ; `DemoDataInitializer` passe de 4
  à 5 comptes (test `DemoDataInitializerTests` mis à jour) ;
  `scripts/seed-demo.sh` affecte ce compte à `PRG-DEMO` (idempotent) ;
- jeu de données `docs/demo-data/apprenants-demo.csv` (fictif,
  `example.test`) + `docs/demo-data/README.md` (résultats de simulation
  **réellement observés**) ;
- `docs/12-guide-utilisateur.md` (nouveau) : parcours par rôle
  (`SUPER_ADMIN` / `ADMIN` / `SCHOOL_ADMINISTRATION` /
  `PEDAGOGICAL_MANAGER` / `TEACHER` / `STUDENT`), écrans visibles,
  limites, erreurs attendues, endpoints sans écran, hors périmètre ;
- `docs/11-guide-demonstration.md` : scénario bout en bout §11
  (import CSV → séance → émargement → rapport → export + sélecteur de
  contexte), **checklist jury §12**, **matrice fonctionnalité × preuve
  §13** ; **parcours API du §11 exécuté en direct** (§11.8, statuts HTTP
  relevés) ; la démonstration **UI** de bout en bout reste
  `IMPLEMENTED_NOT_MANUALLY_DEMONSTRATED` ;
- back-end `./mvnw clean test` re-vérifié après F6 (total dans
  `docs/reports/PROJECT_FINALIZATION_REPORT.md`).

**Passe corrective probatoire G1-E / G1-F / G1-G (1er septembre 2026)** —
OpenJDK 21.0.12, MySQL 8, Redis 7, Node 24.13 :

| Commande | Résultat |
|---|---|
| `cd backend && ./mvnw clean test` (défaut) | **809 tests, 0 échec, 0 erreur, 0 ignoré — BUILD SUCCESS** (800 → 809 : A +1, B +1, C +3, D +3, F `ClassGroupDirectoryTests` +1 ; le test N+1 comparatif remplace l'ancien) |
| `TZ=UTC ./mvnw clean test` | **809 / 0 — BUILD SUCCESS** |
| `TZ=Europe/Paris ./mvnw clean test` | **809 / 0 — BUILD SUCCESS** |
| `ModularityTests` | **vert** (14 modules) |
| Flyway `V1 → V16` sur `esic_test` recréée vierge + `ddl-auto=validate` | **`Successfully applied 16 migrations … v16`** puis validation OK — BUILD SUCCESS |
| `cd frontend && npm test -- --watch=false` | **71 fichiers / 600 tests / 0 échec** (596 → 600 : `dashboard-api.service.spec.ts` +2, `dashboard.spec.ts` +2 dont 1 modifié) |
| `npm run lint` | « All files pass linting » |
| `npm run build` | initial **484,52 kB** — 0 alerte de budget |
| `npm audit --audit-level=high` | **0 vulnérabilité** |
| Anti-N+1 dashboard manager (compteur Hibernate) | 1 classe → **14** requêtes ; 15 classes → **14** (croissance **0** ; avant correctif : 14 → 28) |

Aucun `@Disabled` / `@Ignore` / `it.skip` / `.only(` ajouté ; aucun test
supprimé ; aucune assertion affaiblie ; `.env` inchangé ; aucune
migration Flyway ajoutée (schéma en **V16**). Détail :
[`docs/reports/G1_FINAL_REPORT.md`](reports/G1_FINAL_REPORT.md).

## Infrastructure

`docker compose up -d` démarre `mysql` (8.4), `redis` (7.4), `mailpit`,
`mosquitto`. Les trois premiers passent `healthy` ; Mosquitto n'a pas de
sonde. Nécessite un `.env` local non versionné (`cp .env.example .env`,
puis renseigner `JWT_SECRET` ≥ 32 octets et, pour le profil `demo`,
`ESIC_DEMO_PASSWORD` ≥ 12 caractères). Voir `README.md`.

## Prochaine étape

Le lot de finalisation **F2 → F6** (branche
`chore/project-finalization-f2-f6`) :

- **F2** (ce commit) — aligner la documentation sur `main`.
- **F3** — preuves de qualité : avertissements de template, tests
  d'en-têtes de sécurité, accessibilité outillée minimale, mesures de
  performance reproductibles, politique de rétention documentée.
- **F4** — CI : Dependabot, `dependency-review-action`, `npm audit`,
  analyse Maven ; revue des permissions de workflow.
- **F5** — durcissement : CORS piloté par `APP_ALLOWED_ORIGINS`, CSP +
  `Referrer-Policy` + test d'en-têtes, éventuel rate-limiting,
  javadoc `SecurityConfig`. **Le module `planning` n'est pas
  implémenté** (reste `HORS_PÉRIMÈTRE_ASSUMÉ`).
- **F6** — démonstration : jeu de données CSV fictif, scénario jury,
  compte démo multi-rôles, `docs/12-guide-utilisateur.md`.

Au-delà de la finalisation, la première vraie priorité produit reste
l'**import du planning** (module `planning`).

## Règle de mise à jour

Ce document doit rester **court** et refléter le dépôt. Ne jamais
déclarer :

- `TESTED` sans commande exécutée ;
- `DEMONSTRATED` / démontré sans vérification manuelle enregistrée ;
- `DEPLOYED` sans URL ou preuve ;
- `FONCTIONNEL` seulement parce que le code existe.

La chronologie détaillée va dans `docs/reports/PROJECT_HISTORY.md`, pas
ici.
