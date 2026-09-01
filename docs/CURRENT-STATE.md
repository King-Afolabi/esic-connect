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
31 août 2026 — checkpoint de finalisation F2 (vérité documentaire)
```

## Commit stable de référence

```text
main = e44ccb1  — Merge PR #26 (audit de finalisation F1)
Dernier commit fonctionnel : 9c5affa — import CSV contrôlé des apprenants (PR #25)
```

Toutes les tranches décrites ci-dessous sont **fusionnées sur `main`**.
Le dépôt n'a aucune branche « en cours » de référence : le lot de
finalisation se fait sur `chore/project-finalization-f2-f6`.

## Modules Spring Modulith réels (12)

`backend/src/main/java/com/esic/connect/` :

| Module | Rôle | Migration(s) |
|---|---|---|
| `identity` | comptes, rôles, authentification JWT, invitation/activation, administration des comptes | V1, V2, V3 |
| `organization` | site / bâtiment / salle / plage réseau CIDR | V4 |
| `academic` | année scolaire, formation, niveau, promotion, classe, affectation pédagogique + contrôle de périmètre | V5, V6 |
| `enrollment` | profil apprenant, inscription, changement de classe historisé | V7 |
| `alternation` | modèles de rythme, affectation historisée à une classe, exceptions individuelles, résolution `SCHOOL`/`COMPANY`/`UNKNOWN` | V8 |
| `coursesession` | séance **exceptionnelle** (création manuelle), cycle `PLANNED → OPEN → CLOSED`, points de contrôle multiples | V9, V10 |
| `attendance` | jeton d'émargement (Redis), validation, retard, présence manuelle / correction / annulation, justificatif métier sans fichier, rapports + export CSV | V9, V10 |
| `studentimport` | import CSV contrôlé des apprenants (lecture sécurisée, simulation, confirmation transactionnelle, purge) | V11 |
| `notification` | email d'activation via SMTP local (Mailpit), envoi asynchrone après commit | — |
| `audit` | piste d'audit `audit_event` alimentée par les événements métier | V1 |
| `bootstrap` | amorçage `demo` (comptes fictifs, profil `demo` uniquement) | — |
| `shared` | types transverses, `BaseEntity`, `ApiError`, `GlobalExceptionHandler`, `ClockConfig` | — |

`ModularityTests` (Spring Modulith 1.4) est **vert** : aucune dépendance
vers un package `.internal` d'un autre module, aucun cycle.

Modules décrits dans `docs/03-architecture.md` §7 comme **architecture
cible non implémentée** : `planning`, `room` (remplacé par
`organization`), `justification` (fusionné dans `attendance`, sans
fichier), `claim`, `reporting` (fusionné dans `attendance`), `ai`, `iot`.

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
```

Schéma **en version 14**. `spring.jpa.hibernate.ddl-auto = validate`.
Aucune donnée métier insérée par une migration. V12/V13 corrigées en
place à l'audit G1-B.1 (jamais poussées — cf. en-tête de `V13`).

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
- Séance **exceptionnelle** créée manuellement (motif obligatoire),
  cycle strict `PLANNED → OPEN → CLOSED`, pas de réouverture, pas de
  `PATCH` / annulation / remplaçant.
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
- Justificatif **métier sans pièce jointe** : dépôt / modification tant
  que `PENDING` / examen ; `ACCEPTED` → `ABSENT → EXCUSED_ABSENCE` ;
  `TEACHER` exclu de l'examen.
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
| Alternance ↔ assiduité | contexte résolu, consommé par le reporting | pas de module `planning` : les « demi-journées attendues » reposent sur des séances exceptionnelles saisies à la main |
| Justificatif (EF-JUS-001/002) | métadonnée métier + cycle d'examen | aucune pièce jointe (docs/02 §21) |
| Notifications (EF-NOTIF-001/002) | email d'activation seulement | in-app, email métier (planning, remplacement…), push PWA, file persistante / DLQ |
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
`EF-SES-005` (remplaçant) = **G1-C.2, en cours**.

Le reste de la liste ci-dessous (`HORS_PÉRIMÈTRE_ASSUMÉ` de la
finalisation F2) **reste d'actualité** tant que les blocs G1-C.2 à G1-G
ne sont pas livrés.

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
- Séances : ~~annulation (EF-SES-004)~~ → **livrée au bloc G1-C.1** ;
  affectation d'un remplaçant (EF-SES-005) — **en cours (G1-C.2)** ;
  `PATCH` d'une séance manuelle `PLANNED` — non livré (G1-C.2 ou différé).
- QR fixe de salle + contrôle réseau CIDR (référentiel `site_network_range`
  présent, non consommé) — EF-ROOM-002, EF-ATT-008.
- Scan caméra mobile (code court uniquement).
- WebAuthn / passkeys, MFA TOTP, Cloudflare Turnstile / anti-bot.
- Réclamations / messagerie (EF-CLAIM-001/002), départ anticipé,
  justificatif avec pièce jointe, import Excel `.xlsx` / multifeuille,
  groupes temporaires.
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
