# Rapport final — campagne « one-shot » (Lots A → P)

## 1. Branche et commit de départ

- Branche : `feat/ui-redesign-bootstrap-material`
- Commit de départ : `314476b` (« feat(ui): étapes 12-14 + audit final »),
  arbre propre, 57 commits d'avance sur `main`.

## 2. HEAD final

`0deba76` — `docs(deployment): runbook Raspberry Pi + revue sécurité (Lots O, P)`.
14 commits ajoutés. Aucun `push`, `merge` ni déploiement.

## 3. Commits créés (ordre chronologique)

| # | Commit | Lot |
|---|---|---|
| 1 | `e191312` fix(auth): renouveler la session sur activité significative | A |
| 2 | `3fb0188` fix(navigation): garantir un seul élément latéral actif | C |
| 3 | `c0df26c` fix(import): rendre les anomalies apprenants lisibles et cohérentes | J |
| 4 | `93f5aed` fix(attendance): clarifier la fenêtre d'émargement active | I |
| 5 | `75297be` feat(students): permettre la création manuelle d'un apprenant | H |
| 6 | `9f106be` test(e2e): conserver filtres, tri et pagination au retour | G |
| 7 | `ba038c5` fix(dashboard): raccourcis en grille compacte | D |
| 8 | `5d4d588` fix(responsive): adapter la connexion aux orientations mobiles | E |
| 9 | `509ffab` fix(ui): rendre les onglets de section lisibles et cohérents | F |
| 10 | `829b58a` fix(planning): empêcher les superpositions pendant la correction | K |
| 11 | `957641e` fix(responsive): indice de défilement horizontal des tableaux | L |
| 12 | `6713f0a` refactor(ui): signature ESIC discrète sur l'en-tête de page | B |
| 13 | `04b727d` test(a11y): audit axe et captures des écrans publics | M, N |
| 14 | `0deba76` docs(deployment): runbook Raspberry Pi + revue sécurité | O, P |

## 4. Fichiers modifiés

**83 fichiers, +3692 / −135** (hors captures PNG). Répartition :

- **Front-end** — TypeScript, HTML, SCSS de : `core/auth` (nouveau
  `session-activity.service` + `session-timeout-warning` + `list-query-params`),
  `core/navigation`, `core/layout/app-shell`, `features/dashboard`,
  `features/students` (nouveau `student-create`), `features/students/import`,
  `features/planning`, `features/sessions`, `features/alternation` (7 gabarits),
  `features/academic`, `features/administration`, `features/organization`,
  `features/notifications`, `styles/` (`_primitives`, `_auth`,
  `_material-overrides`), `environments/`.
- **Tests** — 1 fichier e2e (`tests/13-accessibility-axe.spec.ts`), spécs
  unitaires étendues sur ~10 composants + 2 nouveaux fichiers de spéc
  (`session-activity.service.spec`, `session-timeout-warning.spec`,
  `student-create.spec`, `list-query-params.spec`).
- **Exploitation** — `compose.prod.yaml` (rotation des journaux,
  healthcheck frontend), `package.json` / `package-lock.json`
  (`@axe-core/playwright` en devDependency).
- **Documentation** — `DECISIONS_NEEDED.md`, `docs/audit/LOT-A`,
  `LOT-B`, `LOT-O`, ce rapport ; `docs/deployment/RASPBERRY-PI.md`,
  `PRE-FLIGHT.md`, `ROLLBACK.md`, `SECRETS.md`.

Aucune ligne de back-end Java. **Zéro migration** (schéma inchangé, V34).

## 5. Comportement du timeout d'inactivité — avant / après

**Source réelle** : le jeton d'accès JWT vit `JWT_ACCESS_TOKEN_TTL_SECONDS`
= **900 s (15 min)** (`backend/.../application.yml`). Le cookie de
renouvellement `HttpOnly` (inactivité glissante `PT30M`, plafond absolu
`PT12H`) était déjà correct côté back-end.

| | Avant | Après |
|---|---|---|
| Renouvellement | **réactif seulement** : au premier `401` d'un appel métier | **proactif** : sur activité significative récente (`pointerdown` / `keydown` / `submit` / navigation interne — **jamais** le survol souris), quand le jeton approche de son terme, throttlé (≤ 1 / `minRenewIntervalMs`), verrou `localStorage` inter-onglets |
| Utilisateur qui lit / saisit / navigue sans appel API | session tombe à 15 min, sans préavis ; si le trou dépasse 30 min, perte de la saisie | session **glisse indéfiniment** tant qu'il y a une activité toutes les ≤ 5 min, jusqu'au plafond absolu de 12 h |
| Préavis | aucun | **avertissement accessible** (`role="alertdialog"`, `aria-modal`, focus déplacé puis rendu, piège de focus, `Échap` = « continuer », `prefers-reduced-motion`) ~2 min avant, avec « Continuer la session » / « Se déconnecter » |
| Multi-onglets | non coordonné | `BroadcastChannel('esic-session')` : renouvellement / fin propagés |
| Route au retour | perdue (`/login?reason=expired`) | **préservée** : `/login?reason=expired&redirect=<route>`, sans boucle |
| Plafond absolu | back-end seul | respecté, le client cesse de renouveler à l'approche et bascule en fin de session |

Durées : toutes dans `frontend/src/environments/environment.ts` →
`session` (documentées dans `docs/audit/LOT-A-session-timeout.md`).
Back-end inchangé (MFA, CSRF, rotation, expiration serveur, révocation
intacts). Choix assumé : **pas** de rotation du cookie à chaque appel API
(incompatible avec la détection de rejeu de famille sous concurrence) —
le cahier l'autorise (« si l'architecture de sécurité le permet »).

Tests : `session-activity.service.spec` (12), `session-timeout-warning.spec`
(6), `auth.service.spec` (+3).

## 6. Décisions de sécurité

Détail : `docs/audit/LOT-O-securite.md`. En résumé : aucun secret
commité ; profil `demo` toujours `@Profile`-gardé ; Lot H n'ajoute aucun
endpoint (les 3 routes enchaînées gardent leur `@PreAuthorize`) ; route
`/students/nouveau` gardée `ADMIN`/`SUPER_ADMIN` ; anomalies rendues par
interpolation Angular (pas d'XSS, pas de `[innerHTML]`) ; `npm audit` 0
vulnérabilité (racine + front) ; bundle +2 kB sous le seuil de 600 kB ;
rotation des journaux Docker ajoutée ; `@axe-core/playwright` en
**devDependency** (jamais dans l'image de production).

## 7. Transformations visuelles

| Lot | Changement |
|---|---|
| B | Signature ESIC : segment 2 px bleu → vert (couleurs de la marque) sur le filet de l'en-tête de page, ~30 écrans. **Passe de direction artistique écran par écran NON faite** — inventaire et raison dans `docs/audit/LOT-B-identite-visuelle.md`. |
| C | Un seul élément de navigation actif (`activeNavPath`, correspondance segment la plus profonde) ; arête active en `--esic-blue-700` (plus foncée). |
| D | « Accès rapides » : grille de tuiles compactes adaptative au lieu d'une pile verticale copiant le rail. |
| E | Connexion : portrait compact (logo 11rem → 8rem, marges réduites) ; paysage court en **deux colonnes** (identité à gauche, formulaire à droite). |
| F | Onglets de section : `RouterLinkActive` réellement importé (planning ne surlignait jamais), `ariaCurrentWhenActive` partout, style actif / hover / focus-visible. |
| K | Éditeur de correction de planning : **panneau pleine largeur sous le tableau** (borné en hauteur, défilement interne) au lieu d'une cellule étroite qui débordait. |
| L | Ombre discrète de défilement horizontal sur les enveloppes de tableau (CSS pur). |

`prefers-reduced-motion` respecté partout où du mouvement a été ajouté
(avertissement de session, tuiles de raccourcis).

## 8. Routes testées

- **Unitaire (Vitest)** : suite complète **99 fichiers / 835 tests / 0
  échec** à chaque commit (lint vert, build production sans alerte de
  budget). Couvre la logique de tous les lots : session glissante,
  `activeNavPath` (chaque route du menu), anomalies d'import (aucune /
  bloquante / non bloquante / correction), création d'apprenant
  (enchaînement + échecs partiels), restauration d'URL (student-list,
  session-list) + helper `list-query-params`, grille de raccourcis,
  onglet actif d'`academic-reference-detail`, panneau de correction
  planning, note « un seul point de contrôle » de `session-detail`.
- **Accessibilité + captures (Playwright)** :
  `tests/13-accessibility-axe.spec.ts` — `/login`,
  `/login?reason=expired`, `/mot-de-passe-oublie` : **axe WCAG 2.0/2.1
  A+AA, 0 violation `critical`/`serious`** ; navigation clavier + focus
  visible ; aucun débordement au zoom 200 %. **20 / 20 verts.**

## 9. Création manuelle d'un apprenant (Lot H)

**Vérifié absent** avant écriture : le service `students` était en
lecture seule pour les profils, `/students` n'offrait aucune action de
création — seul l'import CSV permettait d'ajouter un apprenant.

Livré : route `/students/nouveau` (garde `ADMIN` / `SUPER_ADMIN` :
`POST /api/v1/users` l'exige côté serveur), formulaire sur la primitive
`.esic-form`, qui enchaîne **trois routes existantes** —
`POST /users` (compte `PENDING_ACTIVATION` + invitation, aucun mot de
passe), `POST /student-profiles`, `POST /enrollments`. Validation client
alignée sur les contraintes Bean Validation du back-end.

L'enchaînement **n'est pas atomique** (assumé, non masqué) : un échec
partiel conserve les identifiants acquis et **reprend à l'étape fautive**
sans rien recréer, en expliquant l'état. Une atomicité stricte
demanderait un endpoint d'orchestration back-end — signalé comme
évolution, pas simulé.

Bouton « Ajouter un apprenant » sur `/students`, visible pour
`ADMIN`/`SUPER_ADMIN` seulement. Tests : `student-create.spec` (5) —
enchaînement ordonné + navigation ; formulaire invalide → aucune
requête ; doublon d'adresse (étape 1, arrêt) ; échec étape 2 → compte
non recréé à la reprise ; échec étape 3 → ni compte ni profil recréés.

## 10. Règle finale des fenêtres d'émargement (Lot I) et sa source

**La règle métier n'a PAS été modifiée.** L'examen du dépôt montre que
l'hypothèse du mandat (exclusivité stricte, ordre imposé) **n'est pas**
la règle actuelle :

- chaque point de contrôle a son cycle propre `PLANNED → OPEN → CLOSED` ;
  `AttendanceCheckpointService.open()` n'exige pas que les autres soient
  fermés ;
- `CourseSessionService` ouvre le point `START` **d'office** à
  l'ouverture de séance, et `AttendanceIntegrationTests` ouvre `END`
  alors que `START` est encore `OPEN` — sans échec attendu ;
- la vraie exclusion est le **jeton** : `AttendanceTokenService` ne tient
  qu'un pointeur d'autorité par séance ; émettre un code pour un autre
  point de contrôle invalide le précédent ;
- `docs/02 §16.3` + `EF-ATT-004` traitent l'incohérence de séquence par
  `TO_CONFIRM`, pas par un refus à l'ouverture ;
  `DailyAttendanceIntegrationTests` encode ce modèle (4 points ouverts
  sans fermeture pour produire `PARTIAL` / `TO_CONFIRM`).

Imposer l'exclusivité côté serveur contredirait le cahier et casserait
des tests verts — interdit par les règles du dépôt (« le dépôt a
raison », « demander confirmation avant de modifier une règle de
gestion »). Le conflit et **trois options** sont consignés dans
`DECISIONS_NEEDED.md` (D-01) pour arbitrage du porteur.

Changement livré, **sans toucher la règle** : `session-detail` explique,
quand plusieurs points de contrôle sont `OPEN`, qu'un seul accepte les
émargements et qu'afficher un code pour un autre ferme le précédent ; le
code affiché indique à quel point de contrôle il appartient.

## 11. Anomalies d'import corrigées (Lot J)

Le back-end était correct ; l'écran de revue confondait trois mesures
distinctes (`summary.warning`/`error` = **lignes**, `summary.blocking` =
anomalies de **fichier**) et laissait des états trompeurs :

- filtre de gravité des lignes : `BLOCKING` retiré (`ROW_ISSUE_SEVERITIES`)
  — il ne peut structurellement matcher aucune ligne, le proposer
  renvoyait toujours zéro résultat ;
- anomalies globales scindées **bloquantes** (panneau d'erreur) /
  **non bloquantes** (panneau d'information), colonne concernée affichée ;
- carte « Anomalies bloquantes » ajoutée ; cartes de lignes relibellées
  « Lignes en avertissement / en erreur » ;
- message explicite **quand il n'y a aucune anomalie** ;
- chaque ligne indique quoi faire (« ne sera pas appliquée tant qu'une
  erreur subsiste — corrigez-la ci-dessous » / « non bloquantes :
  appliquée telle quelle ») ;
- après correction : message de succès + rechargement synthèse **et**
  lignes (compteur réévalué).

Tests : `student-import-review.spec` (+5).

## 12. Correction du planning (Lot K)

L'éditeur de correction d'une ligne était rendu **dans** une cellule de
tableau : pile de `mat-form-field` comprimée dans une colonne étroite
d'un tableau à défilement horizontal — la ligne enflait, passait sous la
cellule voisine, le contenu était tronqué.

Après : **panneau pleine largeur sous le tableau**
(`editingCorrectionRow` computed), grille de champs adaptative, largeur
bornée à 60 rem, hauteur bornée (`min(65vh, 34rem)`) avec défilement
interne ; la cellule ne garde qu'un bouton bascule (`aria-expanded`) ;
ligne éditée mise en évidence ; focus déplacé à l'ouverture, rendu au
bouton à la fermeture ; `Échap` ferme. Tests :
`planning-import-review.spec` (+1, vérifie que le panneau est un frère du
conteneur de tableau, jamais un descendant du `<table>`).

## 13. Tests back-end exacts

`cd backend && ./mvnw clean test` (variables du `.env` sourcées, base
`esic_test`) — **lancé pendant cette campagne ; résultat en cours de
consignation** (voir la mise à jour de `docs/CURRENT-STATE.md` du même
commit). **Aucune ligne de back-end n'a été modifiée** : le résultat
attendu est identique à la dernière exécution consignée
(`docs/CURRENT-STATE.md` §6.5 : 143 rapports Surefire, 1231 tests, 0
échec). Si un écart apparaît, il est indépendant des changements de
cette campagne.

## 14. Tests front-end exacts

`cd frontend`, à chaque commit :

| Commande | Résultat |
|---|---|
| `npx ng lint` | « All files pass linting » |
| `npx ng test --watch=false` | **99 fichiers / 835 tests / 0 échec** (base de départ : 95 / 786) |
| `npx ng build --configuration production` | bundle produit, **aucune alerte de budget** (initial ~578 kB / seuil 600 kB ; `styles.css` ~89 kB) |

+49 tests unitaires nets sur la campagne (session ×18, navigation ×6,
import ×5, création apprenant ×5, planning ×1, sessions ×1, dashboard ×1,
academic ×1, list-query-params ×8, +3 auth.service, etc.).

## 15. Résultat Playwright exact

- **`tests/13-accessibility-axe.spec.ts` : 20 / 20 verts** (écrans
  publics — axe, clavier, zoom 200 %, 15 captures).
- **Recette Playwright complète (`tests/01`..`tests/12`, 167 tests) :
  `NOT_PERFORMED` dans cette passe.** Motif : le back-end en cours sur
  `:8080` tourne en profil `local` (`.env` → `SPRING_PROFILES_ACTIVE=local`),
  qui **n'amorce aucun compte de démonstration** (`DemoDataInitializer`
  est `@Profile("demo")`). Un essai de `tests/01-authentication.spec.ts`
  l'a confirmé : 7 passés (tests négatifs), 6 échoués (connexion aux
  comptes démo inexistants). Rejouer la suite complète exige de **basculer
  le back-end en profil `demo`** sur `esic_connect_demo`, ce qui
  entrerait en conflit avec l'instance `local` en cours d'utilisation.
  Commande pour la rejouer, une fois la pile `demo` debout :

  ```bash
  set -a && source .env && set +a
  export ESIC_DEMO_TOTP_SECRET=<base32 fictif>
  npm run test:e2e
  ```

  La **suite unitaire front-end (835 tests)** couvre la logique de chaque
  lot et sert de filet de régression pour cette passe.

## 16. Résultat axe / Lighthouse

- **axe-core** (`@axe-core/playwright`, WCAG 2.0/2.1 A + AA) : **0
  violation `critical` ni `serious`** sur `/login`,
  `/login?reason=expired`, `/mot-de-passe-oublie`.
  **Limite documentée** : un audit automatisé ne couvre pas tout (ordre
  de lecture, pertinence des libellés, pièges de focus complexes,
  contraste des états au survol). Les écrans authentifiés n'ont **pas**
  été audités (même motif que la recette e2e).
- **Lighthouse** : `NOT_PERFORMED`. Non installé localement ; l'installer
  déclenche un téléchargement lourd. Le mandat l'autorise (« si
  disponible / quand l'environnement le permet »). Commande cible :
  `npx lighthouse http://localhost:4200/login --preset=desktop
  --output=html --output-path=artifacts/lighthouse-login.html`.

## 17. Liste et emplacement des captures

`artifacts/report-screenshots/` — **15 captures** produites (écrans
publics × 5 viewports) :

```
pub-login-{desktop-1440x900,mobile-portrait-390x844,mobile-landscape-844x390,tablet-portrait-768x1024,tablet-landscape-1024x768}.png
pub-login-expired-{… mêmes 5 viewports}.png
pub-forgot-password-{… mêmes 5 viewports}.png
```

Vérifié visuellement : Lot E — deux colonnes en paysage court (logo à
gauche, formulaire à droite) ; portrait compact sans débordement. Lot B
— signature bleu/vert visible sur l'en-tête (écrans publics n'ont pas
d'`.esic-page-header` ; visible sur les écrans applicatifs).

`INDEX.md` : `artifacts/report-screenshots/INDEX.md`.

**Captures des écrans authentifiés** (tableau de bord, navigation,
notifications, onglets, listes filtrées + retour, création d'apprenant,
émargement, anomalies d'import, correction de planning, tableaux,
avertissement de session) : **NON produites** — même dépendance à la
pile `demo` que la recette e2e.

Rapport HTML Playwright : `test-results/html-report/`. Traces sur échec :
`test-results/artifacts/` (gitignoré).

## 18. État des dépendances

- `npm audit` (racine + `frontend`, toutes dépendances) : **0
  vulnérabilité**.
- Ajout : `@axe-core/playwright@^4.13.0` + `axe-core@^4.13.0` en
  `devDependencies`.
- Aucune dépendance de production ajoutée ou modifiée.

## 19. Préparation Raspberry Pi

`docs/deployment/` : `RASPBERRY-PI.md` (prérequis, config, démarrage,
amorçage démo, URL Quick Tunnel, runbook complet), `PRE-FLIGHT.md`
(checklist), `ROLLBACK.md` (sauvegarde mysqldump + tar volume ;
rollback code seul ; rollback complet ; test de restauration),
`SECRETS.md`.

`compose.prod.yaml` durci : rotation des journaux (`x-logging`, 10 Mo × 3
par conteneur) sur les 5 services ; healthcheck ajouté au `frontend`
(les 4 autres en avaient déjà) ; `docker compose config` valide. Images
de base déjà multi-arch `arm64` (Temurin 21, node:24-alpine,
nginx:alpine, mysql:8.4, redis:7.4-alpine, cloudflared) ; **aucune ligne
`platform: linux/amd64`** dans la composition de production.
`restart: unless-stopped` sur tous les services ; volumes persistants
`mysql-data` / `justification-data`.

**Non exécuté** : `docker build ./backend` / `./frontend` sur une Pi
réelle ; montée de la pile `compose.prod.yaml` ; test de restauration.

## 20. Déploiement effectué ou non, avec raison exacte

**NON déployé.** Conditions du mandat non réunies :

- aucun environnement cible explicitement configuré comme
  démonstration / recette (pas de Pi, pas d'URL, pas d'accès) ;
- aucune sauvegarde validée d'une éventuelle instance existante ;
- recette Playwright complète non rejouée (voir §15) ;
- `ESIC_DEMO_TOTP_SECRET` absent de `.env` ;
- `CLOUDFLARE_TUNNEL_TOKEN` absent (mode Quick Tunnel possible sans, mais
  aucune cible pour l'exécuter).

Le paquet est **prêt** : `compose.prod.yaml` valide et durci, Dockerfiles
multi-arch, `.env.prod.example` complet, 4 documents d'exploitation +
runbook.

## 21. Risques restants

- **B — direction artistique écran par écran non faite** : le sentiment
  « générique » n'est que partiellement traité (signature + inventaire).
  297 `mat-card` identiques subsistent.
- **M / N — écrans authentifiés non audités ni capturés**, recette
  Playwright complète non rejouée : risque de régression e2e non couvert
  par l'automatisation (la suite unitaire de 835 tests l'atténue).
- **I — règle des fenêtres d'émargement** : décision produit en attente
  (`DECISIONS_NEEDED.md` D-01).
- **H — création d'apprenant non atomique** : un échec réseau entre
  l'étape 2 et l'étape 3 laisse un compte + profil sans inscription ; la
  reprise est guidée mais manuelle.
- **P — jamais monté sur une Pi réelle** ; test de restauration
  `NOT_PERFORMED`.
- **Back-end** : suite de tests relancée par prudence (aucun code
  back-end touché) — résultat à consigner dans `CURRENT-STATE.md`.
- Dettes héritées inchangées : T-05, T-06, T-13..T-17.

## 22. Décisions humaines indispensables

1. **`DECISIONS_NEEDED.md` D-01** — ordre / exclusivité des fenêtres
   d'émargement : choisir l'option 1 (statu quo + interface, déjà en
   place), 2 (garde `END`), ou 3 (ordre strict + réécriture des tests).
2. **Lot B** — mandater (ou non) une passe de direction artistique
   écran par écran, avec revue visuelle dédiée.
3. **Recette e2e / captures authentifiées / Lighthouse** — autoriser le
   basculement du back-end local en profil `demo` (arrêt de l'instance
   `local`, `db-reset.sh esic_connect_demo`) pour rejouer la suite.
4. **Fusion** — la branche `feat/ui-redesign-bootstrap-material` reste
   locale (14 commits d'avance sur son origine) ; décider de la fusion
   dans `batch/S02A-S11` puis `main`.
5. **Déploiement Pi** — fournir la cible (matériel, accès), les secrets
   manquants (`ESIC_DEMO_TOTP_SECRET`), et l'autorisation explicite.

## 23. Git status final

```
Sur la branche feat/ui-redesign-bootstrap-material
Votre branche est en avance sur 'origin/feat/ui-redesign-bootstrap-material' de 14 commits.
rien à valider, la copie de travail est propre
```

`.env` non suivi. `test-results/artifacts/` gitignoré. Aucun secret
ajouté.
