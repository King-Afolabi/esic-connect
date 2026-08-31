# Notes de performance — ESIC Connect (FINAL-009)

| Élément | Valeur |
|---|---|
| Checkpoint | F3 — qualité / outillage |
| Date de mesure | 31 août 2026 |
| Statut FINAL-009 | `PARTIAL` → **mesure indicative reproductible ajoutée** ; **pas** de garantie contractuelle de latence, pas de seuil CI |

## Nature de ces chiffres

Ce sont des **mesures indicatives**, prises sur **une** machine de
développement, avec l'infrastructure locale (`compose.yaml`). Elles ne
constituent **pas** :

- une garantie de tenue de charge ;
- un SLA ;
- une preuve que la cible « < 100 ms » du cadrage (`docs/01` §1,
  `docs/02` §38.1 NFR-PERF-01) est atteinte pour toutes les routes.

Le cadrage lui-même précise que l'objectif « < 100 ms » vise *« les
opérations simples servies depuis le cache, dans un environnement
maîtrisé »* et *« devra être vérifié par des tests. Il ne constitue pas
une garantie pour toutes les routes ou toutes les conditions de
charge. »*

## Comment reproduire

Les mesures sont portées par deux tests JUnit **taggés `perf`**, donc
**exclus** de `./mvnw test` (voir `backend/pom.xml`, propriétés
`test.groups` / `test.excludedGroups`). Elles ne sont **jamais**
exécutées en CI et n'affirment aucune borne stricte (garde-fou large
uniquement, pour détecter une régression catastrophique).

```bash
cd backend
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"   # adapter
export PATH="$JAVA_HOME/bin:$PATH"
set -a && source ../.env && set +a          # MySQL / Redis / JWT
docker compose -f ../compose.yaml up -d     # infra locale

./mvnw -Pperf test \
  -Dtest='StudentImportSimulationPerfTests,AttendanceTokenPerfTests'
```

Chaque test affiche une ligne `[PERF] ...` sur la sortie standard
(min / p50 / p95 / max en millisecondes).

- `StudentImportSimulationPerfTests` — 3 itérations de chauffe puis 15
  mesures de `StudentImportSimulationService.simulate(...)` sur un CSV de
  **100 apprenants valides** (phase 1 : parsing + normalisation +
  validation + résolution des ports + persistance des seules tables
  `student_import_*`, **aucune écriture métier** — invariant T1).
- `AttendanceTokenPerfTests` — 5 itérations de chauffe puis 50 mesures de
  `AttendanceTokenService.issue(...)` contre le Redis réel : lecture du
  pointeur courant, tirage `SecureRandom` (32 octets), écriture des 3
  clés, suppression de l'ancien couple. À partir de la 2ᵉ itération,
  chaque appel est une **rotation** (le couple précédent existe).

## Contexte machine (run du 31 août 2026)

| Élément | Valeur |
|---|---|
| Machine | Apple M2, 8 cœurs, 16 Go RAM |
| OS | macOS 26.6.2 (arm64) |
| JDK | OpenJDK / Temurin 21 |
| Base | MySQL 8.4 (conteneur `compose.yaml`), sur `localhost` |
| Cache | Redis 7.4-alpine (conteneur `compose.yaml`), sur `localhost` |
| Charge | mono-utilisateur, aucune contention externe |

## Résultats mesurés

### TP-004 / NFR-PERF-03 — simulation d'import de 100 apprenants

`StudentImportSimulationService.simulate(...)`, 15 itérations, 3 chauffes :

| Métrique | Valeur |
|---|---|
| min | **603,6 ms** |
| p50 (médiane) | **636,5 ms** |
| p95 | **696,5 ms** |
| max | **696,5 ms** |

Lecture : l'analyse d'un fichier de 100 apprenants est de l'ordre de
**0,6 s** sur cette machine — « dans un délai acceptable » au sens de
NFR-PERF-03, mais **loin de la barre des 100 ms** (attendue, cette
opération n'est pas « simple servie depuis le cache » : elle fait 100
résolutions de classe/année/compte via les ports et 100+ `INSERT` dans
les tables d'import). L'affirmation antérieure « < 1 s » de
`CURRENT-STATE.md` est **cohérente** avec cette mesure ; elle est
désormais **reproductible**.

### TP-001 / TP-002 approché — génération / rotation d'un jeton d'émargement

`AttendanceTokenService.issue(...)`, 50 itérations, 5 chauffes :

| Métrique | Valeur |
|---|---|
| min | **2,39 ms** |
| p50 (médiane) | **2,88 ms** |
| p95 | **3,28 ms** |
| max | **3,32 ms** |

Lecture : la génération + rotation d'un jeton d'émargement est de l'ordre
de **3 ms** (SecureRandom + ~4 aller-retours Redis locaux). C'est
**très en dessous de la cible 100 ms** — le coût dominant serait, en
production, la latence réseau vers Redis, non mesurée ici.

## Ce qui n'est PAS mesuré (dette assumée)

- lecture d'un planning en cache (`TP-001`) — **il n'y a pas de cache de
  planning** (Redis ne sert qu'aux jetons d'émargement) ;
- latence de `POST /api/v1/attendance/validate` de bout en bout
  (`TP-003`) — seule la concurrence fonctionnelle est testée
  (`AttendanceIntegrationTests`) ;
- 20 scans simultanés sans doublon avec mesure de latence (`TP-006`) —
  la correction fonctionnelle est testée, pas le temps ;
- génération d'un rapport mensuel (`TP-005`) ;
- toute mesure sous charge, multi-utilisateurs, ou en environnement
  déployé.
