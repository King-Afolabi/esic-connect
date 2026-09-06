# Jeux de fichiers d'import apprenants — vérification réelle

Générés par `scripts/seed-demo-full.py` (phase `fixtures`) et **réellement importés** (simulation) le 2026-09-07 sur la base `esic_connect_demo`. Seul le fichier valide est confirmé ; les autres sont laissés en simulation.

Colonnes du modèle : `last_name, first_name, email, formation_code, class_code, academic_year` (obligatoires) + `phone, student_number, birth_date, work_study, company_name` (optionnelles). `level_code`, `promotion_code`, `work_study_pattern` sont **ignorées avec un avertissement** (`IMP_COLUMN_IGNORED`).

| Fichier | Attendu | `status` / confirmable | total/valides/erreurs/bloquantes/avert. | anomalies globales |
|---|---|---|---|---|
| `import-apprenants-valide.csv` | confirmable, 2 créations | SIMULATED / confirmable | 2/2/0/0/0 | — |
| `import-apprenants-avertissement.csv` | confirmable + avert. colonne ignorée | SIMULATED / confirmable | 1/1/0/0/0 | IMP_COLUMN_IGNORED |
| `import-apprenants-bloquant.csv` | 1 ligne en erreur (formation inconnue) → non confirmable | SIMULATED / NON confirmable | 2/1/1/0/0 | — |
| `import-apprenants-multi-anomalies.csv` | plusieurs anomalies (nom, e-mail, date, classe) | SIMULATED / NON confirmable | 2/0/2/0/0 | — |
| `import-apprenants-doublons.csv` | doublon intra-fichier signalé (avert.) | SIMULATED / confirmable | 2/0/0/0/2 | — |
