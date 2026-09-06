# Jeux de fichiers d'import PLANNING — vérification réelle

Générés par `scripts/seed-demo-full.py` (phase `pfixtures`) et **réellement simulés** le 2026-09-07 sur `esic_connect_demo`, classe cible `BTS-SIO-1`. Aucun n'est publié.

Colonnes : `slot_key, session_date, start_time, end_time, time_zone_id, title, teacher_public_id` (obligatoires) + `room_code`. Une seule classe et une seule année par import (portées par la requête, pas les lignes).

| Fichier | Attendu | total/valides/avert./erreurs / confirmable | anomalies (codes) |
|---|---|---|---|
| `planning-valide.csv` | 3 créneaux valides, publiable | 3/3/0/0 / confirmable | — |
| `planning-avertissement.csv` | hors plage horaire + durée inhabituelle (avert.) | 2/0/2/0 / confirmable | PLAN_DURATION_ABNORMAL, PLAN_OUTSIDE_WORKING_HOURS |
| `planning-bloquant.csv` | formateur inéligible + date illisible → non publiable | 2/0/0/2 / NON confirmable | PLAN_DATE_INVALID, PLAN_TEACHER_NOT_ELIGIBLE |
| `planning-conflits.csv` | slot_key dupliqué + chevauchement de salle | 3/0/0/3 / NON confirmable | PLAN_CONFLICT_CLASS, PLAN_CONFLICT_ROOM, PLAN_CONFLICT_TEACHER, PLAN_SLOT_KEY_DUPLICATED |

> Les `planning-*.csv` de cette série incluent les `publicId` de
> formateurs de la base `esic_connect_demo` au moment de leur génération.
> Après une recréation de base, relancer `python3
> scripts/seed-demo-full.py pfixtures` pour les rafraîchir. Les modèles
> DB-agnostiques (`planning-demo.csv`, `planning-conflicts-demo.csv` +
> `scripts/prepare-planning-demo.sh`) restent la référence portable.
