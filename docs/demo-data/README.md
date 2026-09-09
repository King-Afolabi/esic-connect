# Jeux de données de démonstration

Données **strictement fictives** (`example.test`, aucun numéro de
téléphone réel). À utiliser uniquement avec le profil `demo` et après
`scripts/seed-demo.sh` (qui crée la formation `PRG-DEMO`, la classe
`C-DEMO` et l'année `AY-DEMO` référencées ci-dessous).

## Amorçage complet — `scripts/seed-demo-full.py`

Depuis le 7 septembre 2026, `scripts/seed-demo-full.py` construit un jeu
**complet** par-dessus `seed-demo.sh`, entièrement par les API REST
réelles (second facteur ADMIN franchi via TOTP, jamais contourné) :

- **année** `AY-2026` (2026-2027), **5 formations** (BTS SIO, BTS CIEL,
  Bachelor CDA, Mastère ESIS, Mastère CPDIA), leurs niveaux, promotions
  et **9 classes** (initiaux + alternants dans la même classe) ;
- **sites** Malakoff (étages 1, 2, 5) et Paris (rez-de-chaussée),
  bâtiments, **16 salles** ; plages réseau si un compte `SUPER_ADMIN`
  est utilisé ;
- **4 familles de rythmes d'alternance** + affectation aux 9 classes
  (BTS 1 : 3 j école / 2 j entreprise, lun.–mer. ; BTS 2 : 2 sem. école
  sur 4, lun.–jeu. ; Mastères : 1 sem. école sur 4 ; Bachelor CDA :
  1 sem. école sur 4 — **hypothèse provisoire documentée**, cursus 1 an) ;
- **18 formateurs** fictifs **actifs** (invitation + activation via
  Mailpit — parcours réel `EF-AUTH-004`), 2 par classe ;
- **~190 apprenants** importés par le **CSV réel** (simulation +
  confirmation), répartis dans les 9 classes ; ils restent
  `PENDING_ACTIVATION` — ce qui alimente aussi `EF-REP-010` (invitations
  non activées) ;
- **~3 mois de planning par classe** (24 août → 30 novembre 2026)
  publiés par l'**import CSV de planning réel** : déterministe et
  **sans conflit** (salle et binôme de formateurs dédiés par classe,
  une séance par demi-journée, rythmes respectés), soit ~490 séances ;
- **scénarios d'assiduité** : ouverture de séances, points de contrôle
  nommés, émargement manuel (présent ≈ 80 %, retard ≈ 12 %,
  absent ≈ 8 %), clôture ;
- **jeux de fichiers d'import** valide / avertissement / bloquant /
  multi-anomalies / doublons, pour l'import apprenants **et** l'import
  planning, écrits ici et **réellement simulés** — verdicts consignés
  dans `IMPORT-FIXTURES-REPORT.md` et `PLANNING-FIXTURES-REPORT.md`.

```bash
# base demo remise à zéro (ESIC_ALLOW_DEMO_RESET=true attendu par le porteur)
ESIC_ALLOW_DEMO_RESET=true \
  bash scripts/db-reset.sh esic_connect_demo --profile demo --yes --keep-running
# puis, back-end demo joignable sur :8080, Mailpit sur :8025 :
API_BASE=http://localhost:8080 \
  ESIC_DEMO_PASSWORD=... ESIC_DEMO_TOTP_SECRET=... \
  python3 scripts/seed-demo-full.py            # toutes les phases
python3 scripts/seed-demo-full.py check        # contrôle de l'état
```

Le script est **idempotent** : relancé, il retrouve les ressources par
leur `code` (409 toléré) et n'écrit pas de doublon. Tous les `publicId`
sont régénérés à chaque recréation de base : le script les résout à
l'exécution, aucun fichier ne les fige.

> **Après toute recréation de base** (`./scripts/db-reset.sh`), tous les
> `publicId` sont régénérés : `public_id` est un `UUID.randomUUID()`
> attribué au `@PrePersist`. Il faut donc **relancer**
> `scripts/seed-demo.sh` puis `scripts/prepare-planning-demo.sh` — les
> fichiers déjà produits dans `build/demo-data/` référencent un formateur
> qui n'existe plus et l'import échouerait. La recette e2e, elle, résout
> cet identifiant **à l'exécution** (`tests/support/api.ts`) et n'a rien à
> régénérer.

## `apprenants-demo.csv`

Fichier d'import CSV des apprenants (schéma minimal `docs/01` §8.1 :
`last_name,first_name,email,phone,formation_code,class_code,academic_year`).
11 lignes de données. Résultat **réellement observé** à la simulation
(back-end en profil `demo`, après `scripts/seed-demo.sh`) :

`summary = { total: 11, valid: 7, warning: 2, error: 2, blocking: 0,
plannedCreate: 9, plannedNoop: 2 }` → job **`SIMULATED`**,
**`confirmable = false`**.

| Lignes de données | Contenu | Statut de ligne | Anomalie |
|---|---|---|---|
| 1, 2, 4–8 (7 lignes) | apprenants nouveaux, valides, classe `C-DEMO` | `VALID` | `IMP_STUDENT_NUMBER_WILL_BE_GENERATED` (info) |
| 3 **et** 9 | même e-mail `ines.kowalski.demo@example.test`, informations identiques | `WARNING` (les deux) | `IMP_EMAIL_DUPLICATE_IN_FILE` — **avertissement**, pas une erreur ; les lignes restent `CREATE_ACCOUNT_AND_ENROLL` (dédupliquées à la confirmation) |
| 10 | adresse e-mail invalide (`pas-une-adresse-email`) | `ERROR` | `IMP_EMAIL_INVALID` — action `NONE` |
| 11 | `class_code` inexistant (`C-INEXISTANTE`) | `ERROR` | `IMP_CLASS_UNKNOWN` — conflit métier, action `NONE` |

Un job avec au moins une ligne en **erreur** est **non confirmable**
tant que les lignes fautives ne sont pas retirées : c'est le
comportement recherché pour la démonstration (prévisualisation →
correction → confirmation). Un job avec seulement des **avertissements**
reste confirmable.

Pour montrer une **confirmation réussie**, importer une version limitée
aux **8 premières lignes de données** :

```bash
head -n 9 docs/demo-data/apprenants-demo.csv > /tmp/apprenants-demo-ok.csv
```

(Le même fichier réduit peut être écrit dans le répertoire non versionné
de démonstration : `head -n 9 docs/demo-data/apprenants-demo.csv >
build/demo-data/apprenants-valides-demo.csv`.)

→ `summary = { total: 8, valid: 8, error: 0, plannedCreate: 8 }`,
`confirmable = true` ; à la confirmation : `appliedSummary = { created: 8,
invited: 8, ignored: 0 }` (8 e-mails visibles dans Mailpit) ;
reconfirmation → `200` + `alreadyApplied: true`.

Le téléphone est facultatif : quelques valeurs `06000000xx` fictives,
le reste vide.

## `planning-demo.csv` et `planning-conflicts-demo.csv` (bloc G1-B)

Fichiers d'import CSV d'un **planning de classe** (module `planning`,
schéma G1 : `slot_key` obligatoire — `DEC-G1-002` — et `teacher_public_id`
plutôt que `teacher_email` — `DEC-G1-B`). Une seule classe par import :
elle est choisie dans l'écran `/planning/import` (pas dans le fichier).

Les modèles suivis par Git portent le marqueur `__TEACHER_PUBLIC_ID__` :
**aucun identifiant réel n'est versionné**.

### Préparer les fichiers (script reproductible)

```bash
# Back-end démarré en profil demo + scripts/seed-demo.sh déjà passé
API_BASE=http://localhost:8080 ESIC_DEMO_PASSWORD=… \
  ./scripts/prepare-planning-demo.sh
# ou, si l'on connaît déjà le publicId du formateur fictif :
./scripts/prepare-planning-demo.sh <teacher-public-id>
```

- résout le `publicId` du compte `formateur@example.test` (le même que
  `scripts/seed-demo.sh`), ou l'accepte en argument / via
  `TEACHER_PUBLIC_ID` ;
- **valide que c'est un UUID** ;
- écrit les copies substituées dans `build/demo-data/` (non versionné —
  personnalisable par `OUT_DIR`) ; **ne modifie jamais** les modèles de
  `docs/demo-data/` ;
- affiche les chemins générés.

Vérification automatisée : `bash scripts/test/test-prepare-planning-demo.sh`
(sans back-end : UUID en argument + faux `curl`).

### `planning-demo.csv` — 5 créneaux valides

> Résultats **relevés par appels API** le 2 septembre 2026 sur un
> back-end `demo` (base `esic_connect_demo`, Flyway V16), et cohérents
> avec `PlanningImportIntegrationTests` /
> `PlanningPublicationIntegrationTests`. Relevé **par API**, pas par une
> manipulation d'interface.

Upload → simulation (`POST /api/v1/planning-imports`, sur une classe
**sans planning publié**, sur une plage horaire **libre** pour le
formateur) : `status = SIMULATED`, `totalRows = 5`, `validRows = 5`,
`errorRows = 0`, `addedRows = 5`, `confirmable = true`.

Publication (`POST …/{id}/publish`) → `versionNumber = 1`,
`alreadyPublished = false`, 5 séances `course_session` d'origine planning
(`status = PLANNED`, sans motif d'exception, `planningSlotPublicId`
renseigné) visibles dans `/sessions` et via
`GET /api/v1/planning/versions`. Repartir la même publication →
`alreadyPublished = true`, aucune séance en plus (idempotence).

Republier une version modifiée du même fichier (horaire changé, créneau
retiré, créneau ajouté) → `versionNumber = 2`, la version 1 passe
`SUPERSEDED` (`replacedByVersionPublicId` renseigné), les séances sont
réutilisées (même `slotPublicId`) / supersédées (filtrées de `/sessions`)
/ créées en conséquence (AC-008).

### `planning-conflicts-demo.csv` — 5 créneaux fautifs

> Refus de publication **relevé par API** le 2 septembre 2026 :
> `POST …/{id}/publish` → `409 PLAN_BLOCKING_ISSUES`. Le détail des
> anomalies ci-dessous reste déduit du code et des tests.

Simulation : `errorRows = 5`, `confirmable = false`. Anomalies **intra-fichier** :
`PLAN_CONFLICT_CLASS` + `PLAN_CONFLICT_TEACHER` + `PLAN_CONFLICT_ROOM`
(deux créneaux qui se chevauchent), `PLAN_TITLE_REQUIRED` (titre vide),
`PLAN_DATE_INVALID` (`32/13/2026`), `PLAN_TEACHER_NOT_ELIGIBLE`
(identifiant nul). La publication d'un tel job est refusée
(`409 PLAN_BLOCKING_ISSUES`).

**Conflit avec une séance déjà publiée** (audit G1-B.1) : réimporter,
sur une classe qui a **déjà un planning publié**, un fichier dont un
**nouveau** `slot_key` chevauche une séance publiée existante (même
formateur ou même classe) → la ligne passe `ERROR` avec
`PLAN_CONFLICT_TEACHER` / `PLAN_CONFLICT_CLASS`. Le **même** `slot_key`
republié n'est jamais signalé contre lui-même. La salle n'est pas
vérifiée contre les séances existantes (le module `coursesession` ne
porte pas de `room_code` — limite documentée dans
`G1_REQUIREMENTS_TRACEABILITY.md`).
