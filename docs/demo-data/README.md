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

**Avant usage** : remplacer le marqueur `__TEACHER_PUBLIC_ID__` par le
`publicId` d'un compte portant un rôle `TEACHER` actif (visible dans
`/administration` ou via `GET /api/v1/sessions/teachers`).

### `planning-demo.csv` — 5 créneaux valides

Simulation attendue (`POST /api/v1/planning-imports`, classe cible sans
planning publié) : `status = SIMULATED`, `totalRows = 5`, `validRows = 5`,
`errorRows = 0`, `addedRows = 5`, `confirmable = true`. Publication
(`POST …/{id}/publish`) → `versionNumber = 1`, 5 séances `course_session`
d'origine planning (`status = PLANNED`, sans motif d'exception) visibles
dans `/sessions` et via `GET /api/v1/planning/versions`.

Republier une version modifiée du même fichier (horaire changé, créneau
retiré, créneau ajouté) → `versionNumber = 2`, la version 1 passe
`SUPERSEDED`, les séances sont réutilisées / supersédées / créées en
conséquence (AC-008).

### `planning-conflicts-demo.csv` — 5 créneaux fautifs

Simulation attendue : `errorRows = 5`, `confirmable = false`. Anomalies :
`PLAN_CONFLICT_CLASS` + `PLAN_CONFLICT_TEACHER` + `PLAN_CONFLICT_ROOM`
(deux créneaux qui se chevauchent), `PLAN_TITLE_REQUIRED` (titre vide),
`PLAN_DATE_INVALID` (`32/13/2026`), `PLAN_TEACHER_NOT_ELIGIBLE`
(identifiant nul). La publication d'un tel job est refusée
(`409 PLAN_BLOCKING_ISSUES`) : c'est le comportement recherché
(prévisualisation → correction → nouvel import).
