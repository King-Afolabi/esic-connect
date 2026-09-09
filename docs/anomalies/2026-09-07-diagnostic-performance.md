# Diagnostic de performance — rapport avant correction (Phase 2.4)

| Élément | Valeur |
|---|---|
| Date | 7 septembre 2026 |
| Portée | tableau de bord (`GET /api/v1/me/dashboard`), synthèse d'assiduité (`/attendance-management/*`) |
| Environnements observés | **production** (Raspberry Pi, `esic_connect_demo`) ; **local** non rejoué (back-end local injoignable, cf. `CURRENT-STATE.md` entrées du 6 sept.) ; **démonstration** = la Pi |

---

## 0. Ce qui a pu être mesuré, et ce qui n'a pas pu l'être

**Mesuré (Phase 2.2 / 2.3)** :

- ressources Pi au repos : `free -h` → 260 Mio libres hors cache, swap
  323 Mio **déjà** consommés ; `load` 0,15 ; disque 28 % ; 5/5 conteneurs
  `healthy`, 0 redémarrage ; `docker stats` CPU < 1,2 %.
- volumétrie complète (`2026-09-07-volumetrie-et-purge.md`) : 17,28 Mio,
  aucune table volumineuse, index du chemin chaud présents.
- lecture du code : [`DashboardService.java`](../../backend/src/main/java/com/esic/connect/dashboard/internal/DashboardService.java),
  routes `attendance-management`, `AttendanceReportService` /
  `AttendanceReportSort`.

**Non mesuré, et pourquoi** :

- `EXPLAIN ANALYZE` sur les requêtes réellement lentes : nécessiterait
  d'activer la journalisation SQL (Hibernate `show_sql` / `p6spy`) sur la
  **production**, puis de la retirer — le mandat l'interdit
  explicitement (« Ne laisse pas une journalisation SQL verbeuse en
  production après le diagnostic »). Aucune fenêtre de test isolée n'était
  disponible (back-end local down).
- chronométrage des endpoints **authentifiés** `me/dashboard` et
  `attendance-management/summary` : la connexion `ADMIN` /
  `PEDAGOGICAL_MANAGER` exige un second facteur TOTP (RG-007) ; le
  scripter contre la prod sans support e2e donnerait une preuve fragile.
  Le `slow query log` MySQL n'est pas configuré sur la Pi.

En conséquence, les causes ci-dessous sont **étayées par la lecture du
code et par un défaut déjà consigné dans `CURRENT-STATE.md`**, pas par un
plan d'exécution SQL. Les corrections sont donc **spécifiées, non
appliquées** dans cette passe (voir §4).

---

## 1. ANO-PERF-001 — tableau de bord

### Cause probable A — trois agrégats d'assiduité 30 j dans une requête HTTP

`DashboardService.manager()` (l. 168-224) et `.administration()`
(l. 261-301) appellent, dans la même requête :

1. `attendanceDashboard.classDigests(from, now)` — demi-journées
   attendues / présentes / absentes / excusées + retards, **par classe**,
   sur `REPORTING_WINDOW = 30 jours` ;
2. `attendanceDashboard.countPendingJustificationsInScope(from, now)` ;
3. `attendanceDashboard.justificationThroughput(from, now)` (admin).

**Appel / requête concerné** : `GET /api/v1/me/dashboard` →
`AttendanceDashboardDirectory.classDigests` (impl. `DashboardCardsService`
côté module `attendance`).

**Mesure avant correction** : non disponible (voir §0). Élément objectif :
`CURRENT-STATE.md` (7 sept., « campagne finale ») documente que ce même
*rollup* **compte des demi-journées « attendues » sur chaque jour
d'alternance `SCHOOL` même sans séance publiée** → le coût croît avec la
**fenêtre** (30 j) × le nombre d'inscriptions, indépendamment du nombre
réel de séances. C'est aussi la cause du « 0 % » affiché (dénominateur
gonflé).

**Solution retenue (spécifiée)** :

- borner l'agrégat aux **séances réellement attendues** de la fenêtre
  (jointure sur `course_session` publiées) au lieu d'itérer le calendrier
  d'alternance ;
- calculer les demi-journées attendues / présentes **en SQL agrégé**
  (`GROUP BY class`), pas en Java ligne à ligne ;
- une seule requête pour les trois besoins de la carte quand c'est
  possible, ou trois requêtes **bornées et indexées** ;
- conserver `REPORTING_WINDOW` comme seule fenêtre ; rejeter toute borne
  ouverte.

**Risques** : le chiffre d'assiduité du tableau de bord **changera**
(il est faux aujourd'hui — 0 %) ; à accompagner d'un test qui fige le
nouveau calcul et d'une note dans `CURRENT-STATE.md`. Risque de
divergence avec le rapport journalier canonique (`EF-ATT-004`) → le test
doit comparer les deux.

**Mesure cible** : `GET /me/dashboard` < 2 s sur la Pi ; nombre de
`PreparedStatement` **constant** entre 3 et 12 séances (garde-fou
`DashboardCardsIntegrationTests`, à étendre à la fenêtre) ; `docs/02`
NFR-PERF-08 (« aucune requête proportionnelle au nombre d'éléments
affichés »).

### Cause B — résilience front-end absente

`Dashboard` (`dashboard.ts`, 139 l.) charge la réponse en **un** appel ;
un échec/timeout bascule **tout** l'écran en « Une erreur est survenue ».

**Solution retenue (spécifiée)** : découper l'affichage par carte, chaque
carte gérant `loading / empty / success / error` indépendamment ; bouton
« Réessayer » ; afficher l'identifiant de corrélation de l'erreur si
présent ; timeout UX explicite (≠ masquer le défaut serveur) ; ne pas
attendre toutes les sections pour afficher les premières.

**Risque** : suppose que l'API expose des sous-ressources ou que la
réponse actuelle soit rendue tolérante aux champs manquants — à cadrer
avec la correction back-end.

---

## 2. ANO-PERF-002 — synthèse d'assiduité

**Cause probable** : mêmes agrégats non bornés partagés avec le tableau
de bord (`CURRENT-STATE.md` §6.3 : ce code a déjà coûté ~21 000 requêtes
avant la correction T-03) ; plus, à vérifier : N+1 de résolution de
libellés par ligne de rapport, absence de pagination (`Slice`), tri
(`AttendanceReportSort`) sur colonne éventuellement non couverte.

**Appel concerné** : routes `/api/v1/attendance/reports/*` +
`GET /attendance-management/summary`.

**Mesure avant** : non disponible (voir §0).

**Solution retenue (spécifiée)** :

- période de synthèse **obligatoirement bornée** (rejet `400` si absente
  ou > N mois) ; jamais « toutes les années » ;
- pagination `Slice` (pas de `COUNT` exact quand inutile), `size`
  plafonnée ;
- agrégats en SQL ; libellés résolus **en lot** (`findByPublicIds`),
  jamais par ligne ;
- front : chaque section de la coquille synthèse a son propre état ; une
  section en erreur n'empêche pas les autres ; annulation des requêtes
  obsolètes (`switchMap`).

**Mesure cible** : `docs/02` NFR-PERF-05 (rapport mensuel de classe
< 2 s) ; coût requêtes borné vs. nombre d'apprenants affichés.

---

## 3. Ce qui a été écarté comme cause

| Piste | Écartée parce que |
|---|---|
| Volume de données | 17,28 Mio total ; `attendance_record` 3 025 lignes. Négligeable. |
| Index manquant | chemin chaud déjà indexé (`2026-07…volumetrie` §3). Pas de gain rapide sans `EXPLAIN`. |
| Saturation CPU / disque Pi | CPU < 2 %, disque 28 %, `load` 0,15 au repos. |
| Cache Redis absent | Redis `healthy` ; le cahier autorise un cache d'agrégats (`docs/02` §24) mais un cache **aveugle** est explicitement proscrit par le mandat — à n'ajouter qu'après la correction algorithmique, si la mesure le justifie. |
| Boucle de redémarrage conteneur | 0 redémarrage, 2 h d'uptime backend/frontend. |

---

## 4. Statut des corrections

| Correction | Statut | Raison |
|---|---|---|
| Borne d'agrégat + calcul SQL (PERF-001/002 back-end) | **`DECLARED`** — spécifiée, non appliquée | Exige `EXPLAIN ANALYZE` sur la Pi et un cycle build + sauvegarde + déploiement back-end vérifiable, hors de portée d'une passe sans fenêtre de test isolée. Appliquer une migration/optimisation à l'aveugle violerait le mandat. |
| Résilience par carte + `switchMap` + « Réessayer » (front) | **`DECLARED`** — spécifiée | Dépend de la forme de la réponse après correction back-end ; livrer la seule couche front masquerait le vrai défaut. |
| Cache Redis d'agrégats contextualisé | **`DECLARED`** conditionnel | À n'ajouter qu'après mesure post-correction algorithmique. |

> Ces trois points sont portés au **product backlog** et à
> `CURRENT-STATE.md` comme dette de performance ouverte, avec la présente
> analyse pour point de départ.
