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
| 5 | _(à compléter)_ | `test(e2e): captures du rapport + recette locale des zones réorganisées` |
| 6 | _(à compléter)_ | `docs(deployment): consigner la recette locale et le blocage de déploiement` |

Aucune réécriture d'historique, aucun `push`, aucun `merge`.

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
  vues internes en onglets (`Notifications` / `Préférences`).
  `routerLinkActive` + `ariaCurrentWhenActive` ; `{ exact: true }` sur
  l'onglet liste pour qu'il ne reste pas actif sur `/preferences`.
- Rétrocompatibilité : `/notifications` et `/notifications/preferences`
  restent les chemins des enfants — favoris et liens intacts.
- `activeNavPath('/notifications/preferences')` → `/notifications` :
  une seule entrée latérale active sur les deux vues.

## 5. Suivi d'assiduité (Lot §2)

- Problème : cinq vues reliées par des liens texte séparés par « · »,
  sans état actif ; « Synthèse » absente du strip une fois quittée.
- Solution : coquille `AttendanceManagementShell` — titre unique +
  `.esic-subnav` à cinq onglets, **« Synthèse » listée explicitement et
  en premier**, `routerLinkActive` + `ariaCurrentWhenActive`.
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
| `npx ng test --watch=false` | **102 fichiers / 840 tests / 0 échec** (base : 99 / 835) |
| `npx ng build --configuration production` | bundle produit, **aucune alerte de budget** — total initial 581,64 kB / 136,25 kB gzip (seuil 600 kB) ; `styles.css` 90,51 kB / 8,42 kB gzip |
| `frontend/node_modules/.bin/tsc -p tsconfig.json --noEmit` | contrôle de type de la suite Playwright — 0 erreur |

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

## 12. Recette locale après changements (exacts)

> Pile **locale** : back-end `:8080` profil `demo` sur `esic_connect_demo`,
> `ng serve` `:4200`, MySQL / Redis / Mailpit en Docker (déjà debout).
> `ESIC_DEMO_TOTP_SECRET=JBSWY3DPEHPK3PXP` (valeur d'exemple de
> `.env.example`, alignée sur le facteur du back-end en cours).

_(résultats détaillés — §13)_

## 13. Résultat Playwright

_(à compléter à la fin de l'exécution des lots ciblés)_

## 14. Résultat axe

_(à compléter)_

## 15. Résultat Lighthouse

_(à compléter — `NOT_PERFORMED` si non installé/non exécutable ici)_

## 16. Erreurs console / réseau

_(à compléter)_

## 17. Inventaire des captures

_(à compléter — `artifacts/report-screenshots/`, `INDEX.md` mis à jour)_

## 18. Régressions rencontrées et corrections

_(à compléter)_

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
  complet dans cette passe (couverture unitaire large : 840 tests).
- `main` très en retard sur la branche (fusion à décider par le porteur).
- Dettes héritées inchangées : T-05, T-06, T-13..T-17, T-19/T-20
  (le secret TOTP de démonstration reste hors dépôt).

## 21. État de D-01

**Inchangé.** La règle métier des fenêtres d'émargement n'est pas
touchée par cette passe. L'explication d'interface ajoutée lors de la
campagne one-shot (Lot I) est conservée. `DECISIONS_NEEDED.md` D-01
reste ouvert, en attente d'arbitrage du porteur (trois options).

## 22. `git status` final

_(à compléter)_
