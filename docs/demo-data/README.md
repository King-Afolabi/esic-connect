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
