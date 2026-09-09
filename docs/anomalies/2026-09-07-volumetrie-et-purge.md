# Volumétrie & candidats à la purge — 7 septembre 2026

| Élément | Valeur |
|---|---|
| Source | production Raspberry Pi `king_a@192.168.1.83`, base `esic_connect_demo` (données **fictives**, profil `demo`) |
| Relevé | 7 septembre 2026, ~22 h 15, lecture seule (`SELECT` sur `information_schema` + comptages agrégés) |
| Confidentialité | aucun nom, aucune adresse, aucun jeton, aucune donnée personnelle dans ce document — uniquement des tailles et des comptes |

> **Rappel du mandat.** La demande initiale « vider la base » est
> **écartée** : destructive, interdite. Ce document établit la volumétrie
> et **identifie** des candidats à une purge éventuelle. **Aucune donnée
> n'a été supprimée.** Une purge ne pourra avoir lieu que si elle est
> couverte par une politique de conservation précise (`docs/02` §33,
> `docs/08-securite-rgpd.md`), prévisualisée, bornée, sauvegardée et
> auditée.

---

## 1. Taille de la base

**Total : 17,28 Mio.** La base n'est pas volumineuse ; les lenteurs
constatées (ANO-PERF-001/002) **ne sont pas un problème de volume de
données** — voir `2026-09-07-performance-ux.md` (cause algorithmique +
mémoire de la Pi).

## 2. Tables par taille (25 premières)

| Table | Lignes (approx.) | Taille (Mio) | Index secondaires |
|---|---:|---:|---:|
| `outbox_message` | 4 454 | 4,52 | 5 |
| `audit_event` | 4 338 | 3,88 | 7 |
| `attendance_record` | 2 822 | 1,36 | 9 |
| `attendance_correction` | 2 807 | 0,83 | 4 |
| `course_session` | 489 | 0,33 | 14 |
| `attendance_checkpoint` | 631 | 0,30 | 8 |
| `planning_entry` | 488 | 0,30 | 8 |
| `enrollment` | 381 | 0,23 | 10 |
| `planning_import_row` | 488 | 0,20 | 4 |
| `notification` | 216 | 0,19 | 5 |
| `user_account` | 218 | 0,17 | — |
| `account_invitation` | 210 | 0,17 | 8 |
| `email_delivery` | 426 | 0,17 | 4 |
| `room` | 16 | 0,16 | — |
| `class_group` | 8 | 0,16 | — |
| `student_profile` | 192 | 0,16 | — |
| `planning_import_job` | 13 | 0,14 | — |
| `class_work_study_pattern` | 9 | 0,14 | — |
| `pedagogical_assignment` | 6 | 0,14 | — |
| `promotion` | 6 | 0,14 | — |
| `student_import_row` | 195 | 0,13 | — |
| `session_class` | 449 | 0,13 | — |
| `program_level` | 10 | 0,13 | — |
| `building` | 2 | 0,13 | — |

Comptages de contrôle (indépendants des estimations `table_rows`) :
`attendance_record` = 3 025 · `audit_event` = 4 546 · `course_session` =
489 · `outbox_message` = 4 771 (`SENT` = 4 771, aucun autre statut).

## 3. Index — inventaire du chemin chaud

Aucun index manifestement manquant sur les tables sollicitées par le
tableau de bord et la synthèse :

- `attendance_record` : `idx_attendance_record_checkpoint`,
  `idx_attendance_record_enrollment`, `idx_attendance_record_status`,
  unique `(attendance_checkpoint_id, enrollment_id)`, FK indexées.
- `course_session` : `idx_course_session_window (starts_at, ends_at)`,
  `idx_course_session_room_window (room_code, starts_at)`,
  `idx_course_session_teacher`, `idx_course_session_status`,
  `idx_course_session_cancelled (status, cancelled_at)`.
- `attendance_checkpoint` : `idx_attendance_checkpoint_session`,
  `idx_attendance_checkpoint_status`, unique
  `(course_session_id, display_order)`, unique nommé `(course_session_id)`.

**Conséquence.** Il n'y a pas de migration d'index « gain rapide »
défendable sans mesure `EXPLAIN ANALYZE` sur une requête réellement
lente. Ajouter des index à l'aveugle serait contraire au mandat
(« Ne remplace pas le diagnostic par un cache aveugle »). La correction
d'ANO-PERF-001/002 est **algorithmique** (borne de fenêtre + agrégat SQL
au lieu d'un calcul Java sur des plages de dates), pas indicielle.

## 4. Candidats à la purge — analyse

| Classe de donnée | Compte | Politique de conservation | Verdict |
|---|---:|---|---|
| `outbox_message` au statut `SENT` **de plus de 30 jours** | **0** | `docs/02` §33 : « Jetons et données Redis : durée de vie propre » ; **aucune rétention explicite pour l'outbox `SENT`** | **Rien à purger.** Toutes les lignes (`SENT`) datent du **jour même** (amorçage/activité du 7 sept.). Sans politique chiffrée, on ne purge pas. Candidat à formaliser : « outbox `SENT` conservée N jours » (proposition : 30 j). |
| `account_invitation` **expirées** (`expires_at < NOW()`, non `ACCEPTED`) | **0** | `docs/02` RG-005 : expiration après 1 mois ; pas de purge définie | **Rien à purger.** 194 `PENDING` toutes encore valides, 18 `ACCEPTED`. |
| `password_reset_token` **expirés** | **0** | `docs/02` §17.8 : jeton à usage unique, 30 min ; nettoyage attendu | **Rien à purger.** |
| Sessions expirées | n/a | jetons d'accès en mémoire ; renouvellement en cookie + Redis avec TTL propre | **Sans objet** — pas de table de session persistante. |
| Données Redis échues | n/a | TTL par usage (`docs/02` §24) | **Géré par Redis lui-même** (expiration native). Rien à faire côté SQL. |
| `attendance_correction` (2 807 pour 3 025 présences) | 2 807 | historique **append-only** (`docs/02` §16.14, RG-060) — **jamais supprimé** | **Interdit de purger.** Volume dû au jeu de démonstration (≈ 1 correction par présence à l'amorçage). Bruit de démo, pas une donnée à détruire. |
| `audit_event` | 4 546 | `docs/02` §33 : **3 ans actifs**, puis archivage ; « ne peut être modifié ni effacé » (§23.4) | **Interdit de purger.** Toutes les traces ont < 1 jour. |
| `notification` masquées | — | `docs/02` §21.5 : « masquer sans effacer l'audit métier » | **Non purgé** — aucune politique de suppression des notifications lues/masquées. |
| Fichiers de justificatifs orphelins | volume vide en démo (2 entrées tar) | `docs/02` §19.5 : balayage périodique des orphelins **déjà implémenté** (`DEC-S9-005`) | **Rien à faire manuellement** — la tâche planifiée s'en charge. |
| `planning_import_row` de jobs abandonnés | 488 (13 jobs) | pas de politique ; lignes de travail d'import | **Non purgé** — volume négligeable, pas de politique, risque de casser un job en revue. |
| Journaux techniques | conteneurs | `docs/02` §33 : 30 jours ; `compose.prod.yaml` : `json-file` `max-size 10m` × 3 | **Déjà borné** par la rotation Docker. |

## 5. Conclusion

**Aucune purge exécutée. Aucune purge exécutable dans cette passe** :
aucune des classes de données volumineuses n'a de politique de
conservation technique suffisamment précise **et** de lignes réellement
échues. Le mandat l'impose explicitement dans ce cas : « documenter les
candidats seulement ».

### Actions de suivi recommandées (hors passe)

1. **Formaliser** dans `docs/08-securite-rgpd.md` une rétention pour
   `outbox_message` au statut `SENT` / `DEAD` (proposition : `SENT`
   purgeable après 30 j ; `DEAD` conservé jusqu'à décision humaine).
2. **Implémenter** ensuite une tâche de purge **bornée, prévisualisée,
   auditée** (comptage avant/après, `LIMIT`, dry-run), testée hors
   production, sur le modèle du balayage des orphelins existant.
3. Ne jamais purger `attendance_record`, `attendance_correction`,
   `enrollment`, `user_account`, `audit_event` actif, ni l'historique
   pédagogique — hors politique validée par la direction et le référent
   RGPD.
