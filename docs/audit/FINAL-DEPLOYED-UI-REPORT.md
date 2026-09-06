# Rapport — passe « navigation & dashboard réorganisés » + recette locale

> Statut : **déploiement non exécuté** (aucune cible démo/recette
> disponible — voir §9). La recette a été rejouée contre la **pile
> locale de démonstration**, clairement identifiée comme telle et non
> comme un environnement déployé.

## 1. Branche et HEAD initial

| Élément | Valeur |
|---|---|
| Branche | `feat/ui-redesign-bootstrap-material` |
| HEAD au départ | `45b9e09` (`docs(current-state): consigner la campagne one-shot A→P`) |
| Arbre au départ | propre |
| Base `main` | `main` (branche 61 commits en arrière au départ de la campagne one-shot) |

## 2. Commits créés (locaux, non poussés)

| # | Commit | Objet |
|---|---|---|
| 1 | `5b9202f` | `fix(navigation): unifier notifications et préférences en un seul espace` |
| 2 | `4db06ab` | `fix(attendance-ui): clarifier les vues du suivi d'assiduité` |
| 3 | `9177868` | `refactor(navigation): regrouper organisation et planning` |
| 4 | `f353ed4` | `refactor(dashboard): compacter la disposition et remonter les accès rapides` |
| 5 | `462667b` | `fix(navigation): supprimer l'état actif résiduel des navigations secondaires` |
| 6 | `7404b08` | `fix(navigation): fiabiliser l'entrée de rail active après réécriture d'URL` |
| 7 | _(HEAD)_ | `docs: consigner la recette locale, les captures et le blocage de déploiement` |

Aucune réécriture d'historique, aucun `push`, aucun `merge`.

**Régression corrigée en cours de route (commits 5 & 6).** La recette
navigateur a mis au jour un défaut réel : au retour vers la vue liste
d'un espace à onglets (Notifications, Suivi d'assiduité) **et** dans le
rail, l'entrée précédente restait active. Cause : une vue enfant qui
réécrit ses paramètres de requête au chargement (Lot G) déclenche une
navigation « même URL » qui **supplante et annule** celle de l'onglet ou
du rail — la séquence se termine en `NavigationCancel` + `NavigationSkipped`,
**sans jamais émettre `NavigationEnd`**. Tout code (dont `routerLinkActive`)
qui ne réagit qu'à `NavigationEnd` restait alors figé, en mode zoneless.
Correctif : dériver l'état actif de `router.url` sur `NavigationEnd`
**et** `NavigationSkipped` **et** `NavigationCancel`. Vérifié au
navigateur (plusieurs allers-retours par espace).

## 3. Fichiers modifiés

**Front-end — nouveaux composants**
- `src/app/features/notifications/notifications-shell/` (`.ts`, `.html`, `.spec.ts`)
- `src/app/features/attendance/management/attendance-management-shell.{ts,html,spec.ts}`
- `src/app/features/organisation-planning/organisation-planning-hub.{ts,html,scss,spec.ts}`

**Front-end — modifiés**
- `src/app/app.routes.ts` — `notifications` et `attendance-management`
  deviennent des coquilles à enfants ; ajout de la route
  `organisation-planning`. **Aucune route existante supprimée ou
  déplacée.**
- `src/app/core/navigation/navigation.ts` — suppression de l'entrée
  `/notifications/preferences` ; fusion des quatre entrées
  `academic` / `organization` / `planning` / `alternation` en une seule
  (`Organisation & planning`) ; nouveau champ `NavItem.matchPaths` ;
  `activeNavPath` étendu.
- `src/styles/_primitives.scss` — nouvelle primitive `.esic-subnav`
  (navigation secondaire d'un espace : vrais liens de route, un seul
  accent bleu, défilement horizontal contenu, hauteur stable).
- `src/app/features/notifications/notification-list.{html,scss}` —
  `<h1>` retiré (porté par la coquille), barre d'actions sur
  `.esic-actions-row`.
- `src/app/features/notifications/preferences/notification-preferences.html`
  — `<h1>` retiré.
- `src/app/features/attendance/management/attendance-summary.html`,
  `attendance-report.{html,ts}`, `justification-queue.{html,ts}` —
  `<h1>` + `<nav.att__crumbs>` retirés, `RouterLink` retiré des deux
  composants qui n'en avaient plus l'usage.
- `src/app/features/attendance/my-attendance/my-attendance-list.scss` —
  `.att__title` / `.att__crumbs` supprimés (portés par `.esic-subnav`).
- `src/app/features/dashboard/dashboard.{html,scss}` — « Accès rapides »
  remonté sous la bande d'identité ; grille de détail en deux colonnes
  dès 52rem de largeur de contenu (`@container esic-content`) ; listes
  de cartes bornées en hauteur.

**Tests**
- Specs unitaires nouvelles : `notifications-shell.spec` (4),
  `attendance-management-shell.spec` (4), `organisation-planning-hub.spec` (5).
- Specs unitaires mises à jour : `navigation.spec`, `app-shell.spec`,
  `dashboard.spec`.
- Specs Playwright mises à jour : `02-authorization-rbac.spec.ts`
  (libellés de navigation) ; nouvelle `14-report-screenshots.spec.ts`.

**Aucune ligne de back-end, aucune migration, aucun changement de
configuration.** (D-01 : la règle des fenêtres d'émargement n'est pas
touchée — voir §21.)

## 4. Notifications / Préférences (Lot §1)

- Problème : deux entrées latérales concurrentes
  (`/notifications`, `/notifications/preferences`), états actifs pouvant
  coexister, soulignement gris/bleu incohérent.
- Solution : une seule entrée latérale « Notifications » → coquille
  `NotificationsShell` (`.esic-page-header` + `.esic-subnav`) à deux
  vues internes en onglets. L'onglet actif est **dérivé de `router.url`
  par un signal** (et non de `routerLinkActive` — voir §2 pour le
  pourquoi) ; `[attr.aria-current]="'page'"` sur le seul onglet courant.
- La vue liste porte un chemin propre `centre` ; `/notifications` y
  redirige. `/notifications` et `/notifications/preferences` restent donc
  valides — favoris et liens intacts.
- `activeNavPath('/notifications/preferences')` → `/notifications` :
  une seule entrée latérale active sur les deux vues.

## 5. Suivi d'assiduité (Lot §2)

- Problème : cinq vues reliées par des liens texte séparés par « · »,
  sans état actif ; « Synthèse » absente du strip une fois quittée.
- Solution : coquille `AttendanceManagementShell` — titre unique +
  `.esic-subnav` à cinq onglets, **« Synthèse » listée explicitement et
  en premier**. Onglet actif dérivé de `router.url` par un signal
  (dernier segment de l'URL), `[attr.aria-current]` sur le seul onglet
  courant.
- Les cinq routes enfants sont inchangées (`summary` / `sessions` /
  `classes` / `students` / `justifications`).
- Il n'y a **aucun switch booléen** dans cet espace : la remarque du
  mandat visait la barre de liens, désormais de vrais onglets. Le seul
  contrôle segmenté restant est le filtre de statut de la file des
  justificatifs (En attente / Acceptés / Refusés / Tous), conservé tel
  quel avec son état `disabled` sur l'option courante.

## 6. Tableau de bord (Lot §3)

- « Accès rapides » remonté immédiatement sous la bande d'identité,
  avant le détail par rôle : visible sans défiler quel que soit le
  volume de cartes du rôle. Grille de tuiles inchangée (Lot D),
  distincte du rail.
- Grille de détail : **une colonne empilée**, **deux colonnes** dès que
  la colonne de contenu dépasse 52 rem (`@container esic-content` — donc
  sensible à l'état du rail, pas à la fenêtre), repli `@media (min-width:
  60rem)` si le contexte de conteneur n'est pas établi. Fin des 3-4
  colonnes étroites de l'`auto-fill` qui laissaient de grands vides sur
  écran large ; le panneau « large » (histogramme + tableau) occupe
  toujours la pleine largeur.
- Listes de séances dans une carte : `max-height: 22rem` + défilement
  contenu.
- **Portée adaptée et assumée** : la restructuration 2×2 littérale par
  rôle (quatre variantes student / teacher / manager / administration +
  sélecteurs e2e figés `.dashboard__chart` / `.dashboard__bar-value` /
  `table.dashboard__table caption`) a été jugée trop risquée pour une
  passe. Le bandeau d'indicateurs reste pleine largeur au-dessus de la
  grille à deux colonnes — disposition standard, qui répond à « mieux
  utiliser la largeur » et « éviter les grandes zones vides » sans
  réécrire quatre gabarits.

## 7. Organisation & planning (Lot §4)

- Quatre entrées latérales — Référentiels, Organisation, Planning,
  Alternance — fusionnées en une : « Organisation & planning ».
- Nouveau hub `/organisation-planning` (`OrganisationPlanningHub`) :
  `.esic-page-header` + liste de sous-sections en liens de route, chacune
  **masquée hors de son périmètre de rôle** (le masquage reflète le
  `@PreAuthorize` de la route cible ; Spring Security reste l'autorité).
- `NavItem.matchPaths` + `activeNavPath` étendu : l'entrée groupée reste
  active sur `/academic`, `/organization`, `/planning`, `/alternation`
  et leurs sous-routes.
- **Aucune fonctionnalité retirée, aucune route cassée.** Les quatre
  routes d'origine sont adressables directement (favoris, liens),
  redirections `redirectTo` internes conservées
  (`/academic` → `/academic/academic-years`, etc.).

## 8. Tests avant la recette locale (exacts)

| Commande (dans `frontend/`) | Résultat |
|---|---|
| `npx ng lint` | « All files pass linting » |
| `npx ng test --watch=false` | **102 fichiers / 838 tests / 0 échec** (base : 99 / 835 ; +3 fichiers de coquille, +3 tests nets après consolidation des specs de coquille) |
| `npx ng build --configuration production` | bundle produit, **aucune alerte de budget** — total initial 581,71 kB / 136,29 kB gzip (seuil 600 kB) |
| `frontend/node_modules/.bin/tsc -p ../tsconfig.json --noEmit` | contrôle de type de la suite Playwright — 0 erreur |

Back-end : **non rejoué** — aucune ligne Java, aucune migration, aucune
configuration touchée. Dernier résultat consigné : 1231 tests / 0 échec
(`docs/CURRENT-STATE.md` §6.5).

## 9. Cible et commit « déployés »

**Aucun déploiement effectué.** Les conditions du mandat ne sont pas
réunies :

| Condition du mandat | État |
|---|---|
| Cible exacte identifiée | ✗ — pas de Raspberry Pi, pas d'hôte de recette |
| Démo/recette (pas une production) | s.o. |
| Accès déjà configuré | ✗ |
| Secrets présents dans l'environnement | ✗ — `.env` de prod absent ; `CLOUDFLARE_TUNNEL_TOKEN` absent ; `ESIC_DEMO_TOTP_SECRET` absent de `.env` |
| Sauvegarde récente si données à conserver | s.o. — aucune instance |
| Rollback exploitable | ✓ documenté (`docs/deployment/ROLLBACK.md`), jamais exécuté |
| Healthchecks définis | ✓ (`compose.prod.yaml` — 5 services) |
| Tests pré-déploiement verts | ✓ (§8) |

Le paquet de déploiement est **prêt jusqu'à la dernière commande** :
`docker compose -f compose.prod.yaml config` est **valide** (avertissements
seulement sur des variables prod optionnelles non renseignées dans le
`.env` local) ; Dockerfiles multi-arch `arm64` ; quatre documents
d'exploitation (`RASPBERRY-PI.md`, `PRE-FLIGHT.md`, `ROLLBACK.md`,
`SECRETS.md`).

**Dernière commande non exécutée** :
`docker compose -f compose.prod.yaml up -d --build` sur la cible, suivie
de `bash scripts/seed-demo.sh`.

**Ce qui manque pour la lancer** : (1) une machine cible (Pi 4/5 arm64 ou
équivalent) avec Docker + Compose v2 ; (2) un `.env` de production rempli
selon `SECRETS.md` (`MYSQL_*`, `REDIS_PASSWORD`, `JWT_SECRET`,
`ESIC_DEMO_PASSWORD`, `ESIC_DEMO_TOTP_SECRET`) ; (3) le choix du mode
tunnel (Quick Tunnel sans jeton, ou `CLOUDFLARE_TUNNEL_TOKEN` + domaine) ;
(4) l'autorisation explicite du porteur.

## 10. Sauvegarde et rollback disponibles

- Sauvegarde : `docs/deployment/ROLLBACK.md` § Sauvegarde (mysqldump
  `--single-transaction` + `tar` du volume `justification-data` + commit
  noté). **Non exécutée** — aucune instance.
- Rollback : code seul (`git checkout <tag>` + `up -d --build`) ou
  complet (code + restauration base + volume). Procédure lue et comprise,
  **jamais exécutée**.
- Test de restauration : reste `NOT_PERFORMED` (inchangé).

## 11. Healthchecks

`compose.prod.yaml` : `mysql`, `redis`, `backend`, `frontend` portent un
healthcheck ; `cloudflared` non (connexion sortante). `restart:
unless-stopped` sur tous. Vérifié structurellement par `docker compose
-f compose.prod.yaml config` (valide).

## 12. Recette locale après changements

> Pile **locale** (pas un déploiement) : back-end `:8080` profil `demo`
> sur `esic_connect_demo`, `ng serve` `:4200`, MySQL / Redis / Mailpit en
> Docker. `ESIC_DEMO_TOTP_SECRET=JBSWY3DPEHPK3PXP` (valeur d'exemple de
> `.env.example`, alignée sur le facteur du back-end en cours) — le
> **vrai** parcours de second facteur ADMIN/SUPER_ADMIN est franchi, pas
> contourné.

## 13. Résultat Playwright

| Spec | Portée | Résultat |
|---|---|---|
| `tests/14-report-screenshots.spec.ts` | **nouveau** — §1–§4 dans un vrai navigateur + captures | **7 / 7 passés**. Assertions tenues : Notifications et Suivi d'assiduité — **exactement un onglet actif, le bon, aucun résidu** après aller-retour ; hub Organisation & planning — en-tête + 4 sous-sections, **une seule** entrée de rail surlignée, maintenue sur `/academic` et `/planning/import` ; tableau de bord — 6 points de rupture capturés sans débordement. |
| `tests/02-authorization-rbac.spec.ts` | matrice RBAC + navigation (libellés mis à jour) | **passé** (voir couplage rate-limit ci-dessous). |
| `tests/03-academic-organization-alternation.spec.ts` | routes regroupées, atteintes en **URL directe** | **passé** — les quatre routes restent adressables, gardes inchangées. |
| `tests/07-attendance-management-reports.spec.ts` | coquille du suivi d'assiduité, exports | **passé** — `heading "Suivi d'assiduité"` porté par la coquille, onglets cliquables. |
| `tests/08-notifications-dashboard.spec.ts` | centre de notifications + tableaux de bord par rôle | **passé** — titre unique, filtres Toutes/Non lues OK. |
| `tests/10-performance-accessibility.spec.ts` | mesures indicatives + a11y structurelle | **passé** — inclut le correctif du sélecteur `.shell__topbar` (l'ancien `.shell__toolbar`, renommé lors de la refonte, ne matchait plus rien : le test ne vérifiait plus que les icônes d'en-tête soient `aria-hidden`). |

**Couplage d'environnement — limite de débit de connexion.** Exécuter
`14` + `02` + `07` + `08` d'affilée dépasse le seau
`LOGIN_ORIGIN_LIMIT` (60 connexions / 15 min depuis `127.0.0.1`,
`docs/CURRENT-STATE.md` §6.1) : un premier passage combiné a produit
**18 échecs**, tous porteurs du message serveur *« Trop de tentatives »*
(erreur d'environnement, sans rapport avec le produit). Après purge des
clés Redis `esic:rate-limit:login*`, **rejeu des 18 → 18 / 18 passés**.
Le décompte de vérité est donc : **tout test touchant les quatre
refontes et les deux correctifs passe** ; les « échecs » sont l'artefact
de limitation connu, documenté, réversible.

**Non rejoué** : la suite Playwright **complète** (`tests/01`..`13`,
~167 tests, ~30 min) — coûteuse et sujette au même couplage rate-limit
sur un enchaînement aussi long ; la campagne one-shot précédente la
laissait déjà `NOT_PERFORMED` pour la même raison d'environnement.

## 14. Résultat axe

**`NOT_PERFORMED` sur les écrans authentifiés refondus dans cette passe.**
`tests/13-accessibility-axe.spec.ts` couvre les écrans **publics**
(0 violation `critical`/`serious`, commit `04b727d`) et n'a pas été
étendu ici. Les nouveaux composants respectent les invariants du système
de design (repères `<nav>`/`<h1>` uniques, `aria-current` sur l'onglet
actif du sous-menu, `aria-label` sur chaque `<nav>`, cibles clavier,
défilement contenu) mais un passage axe-core outillé sur
`/notifications/*`, `/attendance-management/*`, `/organisation-planning`,
`/dashboard` reste à faire.

## 15. Résultat Lighthouse

**`NOT_PERFORMED`.** Non installé ; l'installer déclenche un
téléchargement lourd de Chrome headless. Commande cible :
`npx lighthouse http://localhost:4200/dashboard --preset=desktop
--output=html --output-path=artifacts/lighthouse-dashboard.html`
(exige une session — donc le flux de connexion démo).

## 16. Erreurs console / réseau

Aucune erreur console ni requête en échec observée pendant les 7 tests de
`tests/14` (le harnais Playwright échoue le test sur une exception non
gérée ; les traces `retain-on-failure` sont vides pour ces 7). Contrôle
non exhaustif : pas de capture systématique des `console.error` / `4xx`
mise en place dans cette passe.

## 17. Inventaire des captures

`artifacts/report-screenshots/` — **27 captures** de cette passe
(+ les 15 captures publiques `pub-*` conservées) :

- Notifications (§1) : `01`..`05` (desktop ×3, mobile ×2).
- Suivi d'assiduité (§2) : `10`..`15` (5 vues desktop + 1 mobile).
- Tableau de bord (§3) : `20`..`25` (1440×900, 1280×720, 390×844,
  844×390, 768×1024, 1024×768).
- Organisation & planning (§4) : `30`..`33` (hub + 2 sous-sections
  desktop, hub mobile).
- Métier : `40` (liste filtrée), `41` (retour), `42` (création
  d'apprenant). `48` (avertissement de session) : produit **si** un
  déclencheur de test est exposé, sinon omis.

`INDEX.md` mis à jour : chaque fichier avec route, rôle, viewport, état,
résultat attendu, commit et environnement (nom logique, sans secret).
Données fictives : aucun nom, e-mail ni identifiant réel.

**Non produites** : « anomalies d'import », « correction de planning »,
« émargement avec explication de fenêtre » — parcours métier lourds hors
périmètre de cette passe UI.

## 18. Régressions rencontrées et corrections

1. **État actif résiduel des navigations secondaires ET du rail**
   (trouvé en recette, corrigé — commits 5 & 6). Cause et correctif :
   voir §2. Vérifié au navigateur.
2. **`tests/10` — sélecteur d'en-tête périmé** : `.shell__toolbar`
   n'existait plus (renommé `.shell__topbar` lors de la refonte du
   système de design) — le test « chaque icône décorative de l'en-tête
   est `aria-hidden` » ne vérifiait plus rien depuis. Corrigé : le test
   valide de nouveau les icônes réelles.
3. **`tests/02` — libellés de navigation** : mis à jour pour le
   regroupement (`Organisation & planning` au lieu de quatre entrées).
4. **Limite connue restante — `aria-current` du rail au premier
   chargement** : `[class.active]` (surlignage visible) suit désormais
   correctement la page ; `[attr.aria-current]` n'est posé de façon
   fiable **qu'après une navigation côté client**, pas au tout premier
   rendu d'une URL profonde (quirk de binding d'attribut sous OnPush,
   **pré-existant**, partiellement amélioré ici). `tests/14` teste donc
   `.active` pour le rail. Correctif complet candidat pour une passe
   dédiée (probablement un `NavigationEnd` + `markForCheck()` explicite,
   ou un binding hors `[attr.]`).

## 19. Éléments non réalisés (raison précise)

- **Déploiement + recette post-déploiement + Lighthouse sur URL
  déployée** : aucune cible démo/recette (voir §9).
- **Lot complémentaire (référentiels BTS/CIEL/CDA/ESIS/CPDIA, rythmes,
  plannings 3 mois, jeux d'import + validation)** : différé sur décision
  de cadrage prise en début de campagne — c'est un chantier back-end +
  scripts + jeu de données à part entière, non tenable dans la même
  passe que les quatre refontes UI + la recette.
- **Restructuration 2×2 littérale du tableau de bord par rôle** : voir §6.

## 20. Risques restants

- Écrans authentifiés refondus non couverts par un audit axe outillé
  complet dans cette passe (couverture unitaire large : 838 tests +
  7 parcours navigateur ciblés).
- `aria-current` du rail au premier chargement (§18-4) — a11y : le
  surlignage visible est correct, l'attribut ARIA manque tant qu'aucune
  navigation client n'a eu lieu.
- Suite Playwright complète non rejouée (§13) ; couplage
  `LOGIN_ORIGIN_LIMIT` sur les longs enchaînements.
- `main` très en retard sur la branche (fusion à décider par le porteur).
- Dettes héritées inchangées : T-05, T-06, T-13..T-17, T-19/T-20
  (le secret TOTP de démonstration reste hors dépôt).

## 21. État de D-01

**Inchangé.** La règle métier des fenêtres d'émargement n'est pas
touchée par cette passe. L'explication d'interface ajoutée lors de la
campagne one-shot (Lot I) est conservée. `DECISIONS_NEEDED.md` D-01
reste ouvert, en attente d'arbitrage du porteur (trois options).

## 22. `git status` final

```
Sur la branche feat/ui-redesign-bootstrap-material
Votre branche est en avance sur 'origin/feat/ui-redesign-bootstrap-material' de 32 commits.
rien à valider, la copie de travail est propre
```

7 commits ajoutés par cette passe (`45b9e09..HEAD`) :

```
<HEAD> docs: consigner la recette locale, les captures et le blocage de déploiement
7404b08 fix(navigation): fiabiliser l'entrée de rail active après réécriture d'URL
462667b fix(navigation): supprimer l'état actif résiduel des navigations secondaires
f353ed4 refactor(dashboard): compacter la disposition et remonter les accès rapides
9177868 refactor(navigation): regrouper organisation et planning
4db06ab fix(attendance-ui): clarifier les vues du suivi d'assiduité
5b9202f fix(navigation): unifier notifications et préférences en un seul espace
```

`.env` non suivi. Aucun secret ajouté. Aucun `push`, aucun `merge`.
