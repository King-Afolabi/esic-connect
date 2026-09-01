# Jeux de données de démonstration

Données **strictement fictives** (`example.test`, aucun numéro de
téléphone réel). À utiliser uniquement avec le profil `demo` et après
`scripts/seed-demo.sh` (qui crée la formation `PRG-DEMO`, la classe
`C-DEMO` et l'année `AY-DEMO` référencées ci-dessous).

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

> Résultats **attendus** (déduits du code et des tests d'intégration
> `PlanningImportIntegrationTests` / `PlanningPublicationIntegrationTests`),
> **non relevés manuellement** dans une session de démonstration.

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

> Résultats **attendus**, non relevés manuellement.

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
