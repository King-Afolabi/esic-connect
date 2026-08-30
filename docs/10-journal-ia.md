# Journal d’utilisation de l’intelligence artificielle

## Règles

- ne pas transmettre de données réelles ;
- ne pas transmettre de secrets ;
- vérifier chaque résultat ;
- conserver les prompts importants ;
- distinguer aide, décision et validation ;
- ne pas présenter une production IA comme preuve sans contrôle.

## Registre

| Date | Outil | Tâche | Données | Résultat | Vérification | Décision |
|---|---|---|---|---|---|---|
| 28/08/2026 | ChatGPT | Cadrage | Besoins exprimés | Document MD | Relecture humaine | Retenu avec corrections |
| 28/08/2026 | ChatGPT | Cahier des charges | Besoins fictifs | Exigences | Relecture humaine | Retenu |
| 28/08/2026 | ChatGPT | Architecture | Cahier des charges | Architecture | Relecture humaine | Retenu |
| 28/08/2026 | ChatGPT | Modèle de données | Architecture | MCD/MLD | Relecture humaine | Retenu |
| 30/08/2026 | Claude Code | Correction : idempotence du provisionnement des comptes de démonstration sur base MySQL persistante (`DefaultDemoAccountProvisioner`) | Code back-end, aucun secret | Resynchronisation du mot de passe et du statut `ACTIVE` des 4 comptes fictifs à chaque amorçage `demo`, hachage réécrit seulement si nécessaire | `./mvnw clean test` → 502 tests, 0 échec ; 3 tests ajoutés (resync, idempotence fonctionnelle, garde `@Profile("demo")`) | Retenu |
| 30/08/2026 | Claude Code | Grande tranche V10 : gestion de l'assiduité et reporting (migration `V10`, N points de contrôle par séance, jeton d'émargement par point de contrôle, présence manuelle / correction / annulation logique avec historique append-only, justificatif métier **sans fichier**, calcul de demi-journées, rapports séance / classe / apprenant + synthèse, export CSV avec neutralisation d'injection de formule, écrans Angular `/sessions` enrichi + `/my-attendance` + `/attendance-management`) | Code back-end + front-end, **données fictives uniquement**, aucun secret, aucune donnée personnelle réelle | Rapport de conception `docs/reports/ATTENDANCE_MANAGEMENT_DESIGN.md` (décisions figées, 6 divergences documentées vs docs/02 & docs/04) ; migration additive V1–V9 inchangées ; frontières Spring Modulith respectées (nouveaux ports `alternation.AlternationDirectory`, extensions de `CourseSessionDirectory` / `EnrollmentDirectory`) | Back-end `./mvnw clean test` → **548 tests, 0 échec** (502 → 532 → 545 → 548 au fil des passes de revue PR #22), `ModularityTests` vert, V10 appliquée sur MySQL 8.4 ; front-end `npm test` → **454 tests, 0 échec** (416 → 444 → 451 → 454), `npm run lint` OK, `npm run build` 482,24 kB (< seuil 500 kB) ; démonstration locale relevée en statuts HTTP | Retenu ; limites documentées (contexte d'alternance `UNKNOWN` non compté comme absence ; candidats à la saisie manuelle exposés via `GET /api/v1/sessions/{id}/attendance/candidates` et bornés à l'inscription valable le jour de la séance (2ᵉ passe PR #22) ; `TEACHER` hors rapports agrégés ; pas de test e2e Angular → Spring Boot) |
| 30/08/2026 | Claude Code | Conception (Checkpoint 0) de l'import CSV contrôlé des apprenants — EF-IMP-001 / EF-IMP-002, US-050 / US-051, RG-020 à RG-024, AC-004 à AC-006, TI-001 à TI-012 : nouveau module `studentimport`, migration additive `V11` (`student_import_job` / `_job_issue` / `_row` / `_row_issue`), téléversement multipart `.csv` non conservé, simulation sans écriture métier, confirmation transactionnelle idempotente avec re-validation, ports `identity.StudentAccountProvisioner` / `enrollment.StudentEnrollmentProvisioner` / extension `academic.ClassGroupDirectory`, écrans Angular `/students/import` | Documentation uniquement (aucun code) ; références docs/01 §8, docs/02 §10, docs/04 §16, docs/07 §9-10, docs/08 §9 ; **données fictives**, aucun secret | Rapport `docs/reports/STUDENT_CSV_IMPORT_DESIGN.md` (décisions arrêtées, 12 ambiguïtés listées à valider, 9 divergences assumées vs docs, découpage en 11 checkpoints) | Aucune commande exécutée (checkpoint de conception) ; frontières Spring Modulith vérifiées sur les ports existants ; relecture humaine attendue avant CP1 | En attente de validation — implémentation à suivre sur la même branche |

## Modèle d’entrée

```markdown
## [DATE] — [OUTIL]

### Objectif

[Description]

### Données envoyées

- [Données]
- Classification : fictive/anonymisée/publique

### Prompt résumé

[Résumé]

### Résultat

[Résultat]

### Vérifications

- [Test]
- [Source]
- [Relecture]

### Décision

- Accepté
- Corrigé
- Rejeté

### Limites

[Limites]
```

## Responsabilité

Les assistants IA :

- proposent ;
- accélèrent ;
- expliquent ;
- génèrent des brouillons.

Le candidat :

- choisit ;
- vérifie ;
- teste ;
- assume les décisions ;
- explique le résultat au jury.