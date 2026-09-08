# Registre d'anomalies — performance & UX

| Élément | Valeur |
|---|---|
| Date d'ouverture | 7 septembre 2026 |
| Auteur | mandat performance/UX (session Claude Code) |
| Branche | `feat/demo-readiness-e2e-ui` |
| Base de mesure | production Raspberry Pi `king_a@192.168.1.83` (`compose.prod.yaml`, Quick Tunnel), commit `bf6c3a9` déployé ; MySQL 8.4 / Redis 7.4 conteneurisés |
| Jeu de données observé | `esic_connect_demo` — 218 comptes, 192 profils apprenants, 8 classes, 16 salles, 489 séances, 3025 émargements, 2807 corrections, 4546 événements d'audit, 4454 lignes d'outbox (toutes `SENT`) |

> **Convention de statut** : `OPEN` (constatée, non corrigée) · `IN_PROGRESS`
> (correction commencée) · `FIXED_LOCAL` (corrigée et testée hors production) ·
> `DEPLOYED` (corrigée en production, preuve à l'appui) · `DECLARED` (analysée
> et spécifiée, correction non commencée dans cette passe).

---

## Contexte de mesure production (Phase 2.2)

Relevé le 7 septembre 2026 sur la Pi, au repos (aucun utilisateur) :

```
uptime         : load average 0.15, 0.30, 0.43
free -h        : total 3.7Gi / used 1.3Gi / free 260Mi / buff-cache 2.3Gi / available 2.4Gi
                 swap total 2.0Gi / used 323Mi
df -h /        : 58G total, 16G utilisés (28 %)
docker ps      : backend/frontend/mysql/redis/cloudflared — 5/5 healthy, 0 restart
docker stats   : CPU < 1,2 % sur tous les conteneurs au repos
```

`docker stats` ne rapporte pas la mémoire par conteneur sur ce noyau
(cgroup v2, colonnes à `0B`) — la mémoire est donc lue au niveau hôte.
**La marge mémoire réelle est faible** : 260 Mio libres hors cache, 323 Mio
de swap déjà consommés au repos. Une requête agrégée qui matérialise
plusieurs milliers de lignes côté JVM sur cette machine pousse vers le
swap et l'I/O carte SD — cause plausible des délais et des échecs
intermittents décrits en ANO-PERF-001/002.

---

## ANO-PERF-001 — Tableau de bord lent ou bloqué (« Mon activité »)

| Champ | Contenu |
|---|---|
| **Identifiant** | ANO-PERF-001 |
| **Titre** | Le tableau de bord (`GET /api/v1/me/dashboard`) est lent, puis échoue par intermittence sur la Pi |
| **Écran concerné** | `/dashboard` — cartes « Mon activité » et « Mon périmètre » (rôles `PEDAGOGICAL_MANAGER` et administration) |
| **Étapes de reproduction** | 1. Se connecter comme responsable pédagogique ou administration sur l'instance Pi. 2. Ouvrir `/dashboard`. 3. Observer le temps d'affichage des cartes et recharger plusieurs fois. |
| **Résultat actuel** | Chargement long (plusieurs secondes) ; certains chargements ne terminent pas ; l'écran finit par afficher « Une erreur est survenue ». Le comportement s'est dégradé avec la volumétrie de séances/émargements. |
| **Résultat attendu** | Cartes affichées en < 2 s. Chaque carte gère indépendamment chargement / vide / succès / erreur. Une carte en échec ne bloque pas les autres. Bouton « Réessayer » + identifiant de corrélation si disponible. Aucun spinner infini. |
| **Sévérité** | Majeure — écran d'accueil de deux rôles, inutilisable par intermittence en production. |
| **Hypothèses** | (a) `DashboardService.manager()` / `administration()` appellent `attendanceDashboard.classDigests(from, now)` sur une fenêtre de 30 jours pour **toutes** les classes du périmètre, plus `countPendingJustificationsInScope(from, now)` et `justificationThroughput(from, now)` — trois agrégats d'assiduité dans la même requête HTTP. (b) Défaut pré-existant déjà consigné dans `CURRENT-STATE.md` (7 sept., entrée « campagne finale ») : le *rollup* du tableau de bord compte des demi-journées « attendues » sur **chaque jour d'alternance SCHOOL même sans séance publiée**, ce qui fait itérer le calcul sur des plages de dates × inscriptions au lieu des seules séances réellement attendues → dénominateur gonflé **et** coût proportionnel à la fenêtre. (c) Front-end : le composant `Dashboard` charge la réponse en un seul appel et bascule tout l'écran en état d'erreur si cet appel échoue ou expire (pas de résilience par carte). (d) Pression mémoire/swap de la Pi (voir contexte) amplifiant (a)+(b). |
| **Preuve** | Lecture de [`DashboardService.java`](../../backend/src/main/java/com/esic/connect/dashboard/internal/DashboardService.java) lignes 168-224 (`manager`) et 261-301 (`administration`) : trois appels d'agrégat + `attendanceDashboard.classDigests` par carte. `CURRENT-STATE.md` documente explicitement le dénominateur gonflé du rollup comme « non corrigé, relève de `DashboardCardsService` ». Volumétrie DB relevée le 7 sept. (ci-dessus). Contexte mémoire Pi ci-dessus. |
| **Statut** | `DECLARED` — voir Phase 3 (rapport avant correction). Correction runtime backend **non déployée dans cette passe** : elle exige une mesure `EXPLAIN ANALYZE` sur la Pi et un cycle build+backup+deploy que la présente passe ne peut pas conduire de façon vérifiable sans risque production. Résilience front-end : `DECLARED` avec spec. |
| **Tests de non-régression attendus** | Backend : test comptant les `PreparedStatement` d'un `GET /me/dashboard` responsable avec 3 puis 12 séances — l'écart doit rester nul (garde-fou déjà en place `DashboardCardsIntegrationTests`, à étendre à la fenêtre de synthèse). Test : la fenêtre d'agrégat d'assiduité est bornée à `REPORTING_WINDOW` et n'itère jamais au-delà. Front-end : test de composant `dashboard.spec.ts` — une carte qui rejette laisse les autres rendues ; bouton « Réessayer » présent ; pas d'état d'erreur global. |

---

## ANO-PERF-002 — Synthèse d'assiduité lente ou incomplète

| Champ | Contenu |
|---|---|
| **Identifiant** | ANO-PERF-002 |
| **Titre** | La synthèse du suivi d'assiduité (`/attendance-management/summary`) est lente ; les blocs situés sous la synthèse ne s'affichent pas |
| **Écran concerné** | `/attendance-management/summary` (coquille `AttendanceManagementShell`), et les rapports par séance / classe / apprenant sous `/attendance-management/*` |
| **Étapes de reproduction** | 1. Se connecter comme responsable pédagogique ou administration. 2. Ouvrir `/attendance-management/summary`. 3. Observer : la synthèse tourne longtemps ; les graphiques/listes sous la synthèse restent vides ; erreur finale possible. |
| **Résultat actuel** | Chargement long ; sections sous la synthèse non rendues ; erreur finale par intermittence. |
| **Résultat attendu** | Synthèse et sections affichées progressivement, chacune avec son propre état chargement/vide/succès/erreur. Périodes de synthèse **bornées** (pas de plage de dates ouverte, pas de calcul sur toutes les années). Rapport paginé (`Slice` quand le total exact n'est pas nécessaire). Cible `docs/02` : rapport mensuel de classe < 2 s ; coût SQL borné ; aucune requête proportionnelle au nombre d'éléments affichés. |
| **Sévérité** | Majeure. |
| **Hypothèses** | (a) Agrégation d'assiduité non bornée en période (plage par défaut trop large ou ouverte) et/ou calculée en mémoire Java après un chargement large plutôt qu'en SQL. (b) N+1 sur la résolution des libellés (classe, apprenant, séance) par ligne de rapport. (c) Absence de pagination : la liste complète est chargée puis tronquée à l'affichage. (d) Tri sur colonne non indexée. (e) `COUNT(*)` répété pour la pagination sur de gros ensembles. (f) Amplification par la pression mémoire/swap de la Pi. |
| **Preuve** | Route `attendance-management` → `AttendanceReportService` / `AttendanceReports` / `AttendanceReportSort` (présents dans `backend/src/main/java/com/esic/connect/attendance/internal/`). `AttendanceReportSort` suggère un tri applicatif à vérifier contre les index existants. `CURRENT-STATE.md` §6.3 documente que le même code d'agrégat partagé (`countPendingJustificationsInScope`, throughput) a déjà été une source de coût à ~21 000 requêtes avant correction T-03 — le risque de récurrence sur la synthèse est réel. |
| **Statut** | `DECLARED` — diagnostic Phase 2 conduit sur le code ; `EXPLAIN ANALYZE` sur la Pi et correction runtime **non conduits dans cette passe** (même raison qu'ANO-PERF-001). Résilience/pagination front-end : `DECLARED` avec spec. |
| **Tests de non-régression attendus** | Backend : test comptant les requêtes d'un rapport de classe avec 10 puis 40 apprenants — écart borné. Test : la période de synthèse est rejetée si non bornée ou > N mois. Test : réponse paginée (`page`, `size`) respectée, `size` plafonnée. Front-end : chaque section de la coquille synthèse a son propre `resource`/état ; une section en erreur n'empêche pas les autres. |

---

## ANO-USER-001 — Doublons visibles mais non traitables

| Champ | Contenu |
|---|---|
| **Identifiant** | ANO-USER-001 |
| **Titre** | L'écran des doublons détectés est en lecture seule : aucune résolution possible |
| **Écran concerné** | `/administration/duplicates` (`DuplicateList`) — rôles `ADMIN` / `SUPER_ADMIN` |
| **Étapes de reproduction** | 1. Se connecter comme `ADMIN`. 2. Ouvrir `/administration/duplicates`. 3. Constater qu'aucune case à cocher, comparaison ni action de résolution n'est disponible. |
| **Résultat actuel** | Les groupes de doublons sont listés ; l'administrateur ne peut ni sélectionner, ni comparer, ni résoudre. Décision produit historique (T-18, `docs/02` §9.5) : détection seule, **aucune fusion**. |
| **Résultat attendu** | Sélection par cases à cocher ; sélection **exacte de deux** enregistrements ; action « Comparer » ; aperçu côte à côte des champs et des dépendances ; **simulation** (dry-run) des conflits et des rattachements ; blocage explicite des cas ambigus. La **fusion destructrice reste hors périmètre de cette passe** (règle de gestion — confirmée avec le porteur : livrer sélection + comparaison + simulation uniquement). |
| **Sévérité** | Moyenne — fonctionnalité annoncée incomplète ; pas de perte de données, pas de blocage d'un autre parcours. |
| **Hypothèses** | Le back-end expose `GET /api/v1/users` `.../duplicates` (détection, EF-USER-005) mais aucune route de comparaison/simulation. Le composant `DuplicateList` n'a pas de modèle de sélection. |
| **Preuve** | [`app.routes.ts`](../../frontend/src/app/app.routes.ts) lignes 374-386 : route `duplicates` → `DuplicateList`, garde `['ADMIN','SUPER_ADMIN']`. `CURRENT-STATE.md` T-18 : « nouvel écran `/administration/duplicates` … lecture seule, aucune fusion proposée (docs/02 §9.5) ». |
| **Statut** | `DECLARED` — parcours sélection/comparaison/simulation spécifié (Phase 4). Implémentation back-end + front-end : `DECLARED`, non livrée dans cette passe (nouvelle capacité métier, exige sa propre passe testée). |
| **Tests de non-régression attendus** | Sélection limitée à deux ; « Comparer » désactivé hors de deux sélectionnés ; endpoint de simulation en lecture seule (aucune écriture) ; cas ambigu → simulation `BLOCKED` avec motif ; autorisations `401`/`403` ; deux simulations concurrentes sans effet. |

---

## ANO-UX-001 — Onglet actif sans indicateur

| Champ | Contenu |
|---|---|
| **Identifiant** | ANO-UX-001 |
| **Titre** | Les groupes d'onglets n'indiquent pas visuellement ni sémantiquement l'onglet actif |
| **Écran concerné** | Référentiels académiques (`/academic/*` — Années scolaires, Formations, Promotions, Classes), coquille Suivi d'assiduité (`.esic-subnav`), coquille Notifications, et tout groupe d'onglets comparable |
| **Étapes de reproduction** | 1. Ouvrir `/academic/programs`. 2. Observer la barre d'onglets : l'onglet courant n'a pas de soulignement/ink bar net ; parcours clavier et `aria-selected`/`aria-current` à vérifier. 3. Rafraîchir : vérifier que l'onglet est conservé (il l'est, via l'URL). |
| **Résultat actuel** | Indicateur d'onglet actif absent ou trop discret ; état actif porté surtout par la couleur ; `aria` à compléter. |
| **Résultat attendu** | Soulignement/ink bar visible et contrasté ; état actif **pas uniquement** par la couleur ; `aria-current="page"` (nav) ou `aria-selected` (tablist) ; focus visible ; navigation clavier ; URL synchronisée (déjà le cas) ; onglet conservé au rafraîchissement (déjà le cas). |
| **Sévérité** | Mineure à moyenne — accessibilité (WCAG 2.1 AA visé, `docs/02` §32.5) et orientation. |
| **Hypothèses** | Les sous-navigations sont des listes de `routerLink` stylées à la main plutôt que `mat-tab-nav-bar`. |
| **Preuve / constat après inspection** | **En grande partie déjà satisfait.** La primitive `.esic-subnav` (`src/styles/_primitives.scss` l. 76-127) porte : trait d'accent 2 px sur `.esic-subnav__link--active` **et** sur `[aria-current='page']` (l'information n'est donc pas que la couleur), `:focus-visible` avec anneau, liens `<a routerLink>` (parcours clavier natif). La barre d'onglets `.academic__tabs` (`academic-reference-list.html` l. 3-14 / `.scss` l. 22-53) fait de même : `routerLinkActive` → `.academic__tab--active` (couleur + `border-bottom-color`), `[attr.aria-current]="tabLink.isActive ? 'page' : null"`, `:focus-visible`. L'URL porte l'onglet (`/academic/programs` etc.) → conservé au rafraîchissement. Le shell principal pose déjà `aria-current` (`app-shell.html`). |
| **Statut** | `FIXED_LOCAL` (partiel) — le socle d'accessibilité des onglets **préexistait** et répond aux critères (trait visible, `aria-current`, focus, clavier, persistance par URL). La seule livraison de cette passe : la nouvelle sous-navigation `.esic-subnav` de la page « Invitations » (deux vues : suivi / non activées) réutilise cette primitive, avec `routerLinkActive` + `ariaCurrentWhenActive="page"`. Testée, déployable en rebuild frontend. Aucune régression introduite. |
| **Reste `DECLARED`** | Convergence cosmétique de `.academic__tabs` (bloc bespoke) vers la primitive `.esic-subnav` — pas un défaut d'accessibilité, une dette de duplication. |
| **Tests de non-régression attendus** | `navigation.spec.ts` / specs de composant : l'entrée active porte `aria-current="page"` ; une seule active ; trait d'accent présent ; focus visible. Nouvelle sous-nav Invitations : `invitation-list.spec.ts` / `pending-invitation-report.spec.ts` rendent les deux liens et un seul actif selon l'URL. |

---

## ANO-UX-002 — Tables trop longues (défilement de toute la page, entête perdue)

| Champ | Contenu |
|---|---|
| **Identifiant** | ANO-UX-002 |
| **Titre** | Les tables volumineuses font défiler toute la page ; l'entête disparaît, les actions deviennent difficiles à atteindre |
| **Écran concerné** | Séances, Salles d'un site, Apprenants, Comptes, Audit, Réclamations, Invitations, Justificatifs, Présences, rapports volumineux, doublons |
| **Étapes de reproduction** | 1. Ouvrir `/sessions` (489 séances en démo) ou la fiche d'un site à 16 salles. 2. Faire défiler : l'entête `<thead>` sort du viewport ; le pied de page / la pagination et les actions de ligne exigent de longs défilements. |
| **Résultat actuel** | Entête de colonnes non figée ; l'utilisateur perd le repère des colonnes et des actions ; le défilement porte sur toute la page. |
| **Résultat attendu** | Conteneur de table à hauteur bornée et responsive ; défilement **vertical interne** ; entête `sticky` (`matHeaderRowDef sticky`) ; défilement horizontal interne sur petits écrans ; pagination visible ; focus clavier conservé ; le défilement principal de la page reste possible pour atteindre les autres sections ; pas de hauteur fixe imposée aux petites tables (activation au-delà d'un seuil ou via variante). |
| **Sévérité** | Moyenne — ergonomie, pas de perte de donnée. |
| **Hypothèses** | Les `mat-table` sont enveloppées dans `.esic-table-wrap` (défilement **horizontal** contenu, étape 8 de la refonte UI) mais sans hauteur bornée ni entête `sticky`. |
| **Preuve** | `CURRENT-STATE.md`, entrée « refonte UI étapes 8 et 9 » : « `.esic-table-wrap` (défilement horizontal contenu, jamais le `<body>`) » — le défilement **vertical** interne et le `sticky` ne sont pas mentionnés. |
| **Correction livrée (8 septembre 2026)** | Variante partagée **`.esic-table-wrap--tall`** ajoutée à `src/styles/_primitives.scss` : `max-height: clamp(20rem, 62vh, 46rem)` (jamais `height` → une petite table reste courte ; surchargeable par `--esic-table-max-h`), `overflow-y/x: auto`, `overscroll-behavior: contain`, bordure + fond. Entête figée pour **les deux familles de tableaux du projet** : `mat-table` (fond opaque + `z-index` sur `.mat-mdc-header-row` / `th.mat-mdc-header-cell`, à combiner avec `*matHeaderRowDef="…; sticky: true"`) **et** tableau HTML natif (`thead th { position: sticky; top: 0 }`). Le défilement est **interne à l'enveloppe** : la coquille et le `<body>` restent librement défilables. Appliquée à : **Séances** (`session-list`), **Apprenants** (`student-list`), **Comptes** (`user-list`), **Réclamations** (`claim-list`), **Invitations** (les 2 tables), **Justificatifs** (`justification-queue`), **Mes présences** (`my-attendance-list`), **Audit** (`audit-trail`, natif), **Doublons** (`duplicate-list`, natif). Pagination laissée **hors** de l'enveloppe (reste visible). |
| **Statut** | `FIXED_LOCAL` → visé `DEPLOYED`. Tests : `session-list.spec.ts` (+1, famille `mat-table`), `audit-trail.spec.ts` (+1, famille native) ; suite frontend **859 tests / 0 échec** ; `ng build` prod sans alerte de budget. |
| **Reste `DECLARED`** | Rapports volumineux de la coquille « Suivi d'assiduité » (`attendance-report`) et tables imbriquées éventuelles — mêmes une ligne de patch mais non appliquées ici faute de vérification visuelle par point de rupture. |
| **Tests de non-régression attendus** | ✅ Enveloppe des tables longues porte `.esic-table-wrap--tall` ; entête `mat-mdc-header-row` / `thead th` présente dans l'enveloppe ; `mat-paginator` hors de l'enveloppe. Vérification manuelle restante : rendu `sticky` réel au défilement + impression + dialogues (jsdom ne met pas en page). |

---

## ANO-UX-003 — Page de détail d'un site trop longue

| Champ | Contenu |
|---|---|
| **Identifiant** | ANO-UX-003 |
| **Titre** | Le détail d'un site empile informations, bâtiments, salles (et plages réseau) sur une seule page très longue |
| **Écran concerné** | `/organization/sites/:publicId` (`SiteDetail`) — gabarit de 505 lignes |
| **Étapes de reproduction** | 1. Ouvrir la fiche d'un site à plusieurs bâtiments et 16 salles. 2. Faire défiler : informations du site, puis liste des bâtiments, puis liste des salles, puis (SUPER_ADMIN) plages réseau — tout empilé. |
| **Résultat actuel** | Défilement excessif ; accès difficile aux salles et au QR ; pas de retour/breadcrumb explicite en tête. |
| **Résultat attendu** | En-tête compact : retour/breadcrumb vers la liste des sites, nom + code + statut, actions Modifier/Archiver selon droits, informations essentielles. En dessous, **onglets** : 1. Informations · 2. Bâtiments · 3. Salles · 4. Plages réseau (si rôle). Chaque onglet : table à hauteur bornée + entête sticky + filtre/recherche si volumétrie + pagination ; onglet conservé au retour. « Afficher QR » disponible sur chaque ligne de salle. |
| **Sévérité** | Moyenne. |
| **Hypothèses** | `SiteDetail` rend toutes les sections dans un seul flux vertical ; pas de `mat-tab-group` ni de sous-navigation. |
| **Preuve** | `wc -l site-detail.html` → 505 lignes ; `site-detail.ts` → 600 lignes. `app.routes.ts` ligne 577-581 : route unique `sites/:publicId` → `SiteDetail`, sans enfants d'onglets. |
| **Statut** | `DECLARED` — structure cible spécifiée (Phase 7). Réécriture : `DECLARED`, non livrée dans cette passe (dépend du pattern de table de la Phase 6 et d'une revue visuelle par point de rupture). |
| **Tests de non-régression attendus** | Test de composant : la fiche rend 4 onglets, un seul actif, `aria-selected` ; l'onglet Plages réseau n'apparaît que pour `SUPER_ADMIN` ; l'onglet actif est restauré après navigation ; « Afficher QR » présent sur chaque ligne de salle ; retour vers `/organization/sites` en tête. |

---

## ANO-QR-001 — Impression du QR incorrecte (le shell applicatif est imprimé)

| Champ | Contenu |
|---|---|
| **Identifiant** | ANO-QR-001 |
| **Titre** | L'aperçu d'impression de l'affiche QR de salle inclut le menu latéral, la barre supérieure et le fond applicatif |
| **Écran concerné** | `/organization/sites/:publicId/rooms/:roomId/qr-poster` (`RoomQrPoster`) → action « Imprimer l'affiche » |
| **Étapes de reproduction** | 1. Se connecter comme `ADMIN`. 2. Fiche d'un site → colonne « QR fixe » → « Afficher » → « Imprimer l'affiche ». 3. Observer l'aperçu d'impression du navigateur. |
| **Résultat actuel** | Le rail de navigation, la topbar (marque, profil, cloche, bouton de déconnexion) et le fond de la coquille Angular apparaissent dans l'aperçu. Voir capture jointe au mandat. |
| **Résultat attendu** | Une seule affiche A4 portrait ; aucun menu, aucune topbar, aucun fond applicatif, aucune URL de navigation parasite ; QR net et entier ; logo net ; code + nom de salle ; site / bâtiment / étage ; les quatre instructions non coupées ; date d'émission ; **référence support masquée** (jamais le jeton complet en clair). |
| **Sévérité** | Moyenne — livrable imprimé de mauvaise qualité, mais fonction accessoire. |
| **Hypothèses** | **Cause démontrée** : la route `qr-poster` est déclarée **comme enfant du groupe `path: ''` porté par `AppShell`** (`app.routes.ts` ligne 193 → … → 589). Le composant est donc rendu à l'intérieur de `<mat-sidenav-container>` + `<header class="shell__topbar">`. `window.print()` imprime alors tout le document, shell compris. Le SCSS `@media print` de `room-qr-poster.scss` masque des éléments locaux mais **ne peut pas masquer le shell**, qui est son ancêtre dans le DOM. |
| **Preuve** | `app.routes.ts` : le bloc `path: 'organization'` (ligne 557) est dans `children:` de `path: ''` (ligne 198) dont le `loadComponent` est `AppShell` (ligne 196-197). `app-shell.html` : `<router-outlet />` est à l'intérieur de `<mat-sidenav-content>`. |
| **Statut** | `IN_PROGRESS` — corrigé dans cette passe : la route `qr-poster` est **sortie du sous-arbre `AppShell`** (route sœur de `login`/`activation`, hors coquille), gardée par les mêmes rôles ; `@media print` strict conservé en défense en profondeur. Frontend-only → déployable en rebuild frontend. |
| **Tests de non-régression attendus** | Test de composant : le DOM de la page poster ne contient ni `.shell__rail`, ni `.shell__topbar`, ni `mat-sidenav`. Test : le jeton complet n'apparaît pas dans le DOM rendu (référence masquée uniquement). Test : `Renouveler` n'est jamais appelé au montage ni à l'impression. E2E/capture (déclaré) : `tests/15-room-static-qr.spec.ts` — l'aperçu ne contient que l'affiche. Contrôle manuel Chrome/Chromium. |

---

## ANO-NAV-001 — Menu latéral redondant et trop long

| Champ | Contenu |
|---|---|
| **Identifiant** | ANO-NAV-001 |
| **Titre** | La navigation latérale a des entrées racines redondantes ; elle est trop longue |
| **Écran concerné** | Coquille applicative (`AppShell`) — `NAV_ITEMS` de `navigation.ts` |
| **Étapes de reproduction** | 1. Se connecter comme `ADMIN`. 2. Observer le rail : « Apprenants » **et** « Import apprenants » en racine ; « Invitations » **et** « Invitations non activées » en racine ; ~20 entrées au total. |
| **Résultat actuel** | Deux entrées racines pour les apprenants (liste / import) ; deux pour les invitations (suivi / non activées) ; rail long. |
| **Résultat attendu** | « Import apprenants » rattaché à « Apprenants » (section/sous-entrée ou onglet de la page Apprenants) ; « Invitations non activées » rattaché à « Invitations » (onglet/sous-navigation : Toutes · En attente / non activées · Échecs si supporté) ; nombre d'entrées racines réduit ; **autorisations préservées** (les rôles de chaque entrée restent ceux de l'API) ; **liens profonds préservés** (`/students/import`, `/invitations/non-activees` restent adressables — redirections/alias conservés). |
| **Sévérité** | Mineure — ergonomie. |
| **Hypothèses** | `NAV_ITEMS` liste `/students`, `/students/import`, `/invitations`, `/invitations/non-activees` comme entrées racines distinctes. Le regroupement « Organisation & planning » (déjà en place) fournit le modèle : entrée hub + `matchPaths`. |
| **Preuve** | [`navigation.ts`](../../frontend/src/app/core/navigation/navigation.ts) lignes 57-73 (Apprenants + Import apprenants), 187-194 (Invitations non activées), 228-235 (Invitations). Le mécanisme `matchPaths` + `activeNavPath` existe déjà (lignes 30, 283-306). |
| **Statut** | `IN_PROGRESS` — corrigé dans cette passe : « Import apprenants » retiré des entrées racines et absorbé par « Apprenants » (via `matchPaths` + accès depuis la page Apprenants) ; « Invitations non activées » absorbée par « Invitations » (`matchPaths`). Routes `/students/import` et `/invitations/non-activees` **inchangées** (liens profonds préservés). Frontend-only → déployable en rebuild frontend. |
| **Tests de non-régression attendus** | `navigation.spec.ts` : `/students/import` rend « Apprenants » actif ; `/invitations/non-activees` rend « Invitations » actif ; `visibleNavItems` ne retourne plus d'entrée racine « Import apprenants » ni « Invitations non activées » ; les rôles des entrées conservées sont inchangés. Test : navigation directe vers `/students/import` et `/invitations/non-activees` fonctionne (routes conservées). |

---

## Récapitulatif

| ID | Sévérité | Statut à l'issue de la passe | Livré (déployé) |
|---|---|---|---|
| ANO-PERF-001 | Majeure | `DECLARED` (diagnostic + spec) | Non — correction runtime backend hors passe |
| ANO-PERF-002 | Majeure | `DECLARED` (diagnostic + spec) | Non — correction runtime backend hors passe |
| ANO-USER-001 | Moyenne | `DECLARED` (spec sélection/comparaison/simulation) | Non — nouvelle capacité, passe dédiée |
| ANO-UX-001 | Moyenne | socle préexistant conforme ; sous-nav Invitations livrée | Oui (frontend) — reste : convergence cosmétique `.academic__tabs` |
| ANO-UX-002 | Moyenne | `FIXED_LOCAL` → `DEPLOYED` — primitive `.esic-table-wrap--tall` + 9 écrans | Oui (frontend) — reste : `attendance-report` |
| ANO-UX-003 | Moyenne | `DECLARED` (structure cible spécifiée) | Non — dépend d'ANO-UX-002 |
| ANO-QR-001 | Moyenne | `IN_PROGRESS` → visé `DEPLOYED` | Oui si tests verts (frontend) |
| ANO-NAV-001 | Mineure | `IN_PROGRESS` → visé `DEPLOYED` | Oui si tests verts (frontend) |

> **Note de méthode.** Conformément à `CLAUDE.md` (« Ne jamais déclarer
> terminé sans preuve exécutée », « Ne pas confondre implémenté, testé
> automatiquement, vérifié manuellement et démontré ») et aux règles du
> mandat (« Ne déclare aucun résultat non vérifié »), les anomalies dont
> la correction n'a pas pu être **construite, testée et déployée de façon
> vérifiable dans cette passe** restent `DECLARED` : le diagnostic est
> réel, la solution est spécifiée, mais elle n'est pas présentée comme
> livrée. Les corrections front-end isolées (ANO-QR-001, ANO-NAV-001,
> ANO-UX-001) sont construites et testées localement puis déployées par
> reconstruction de la seule image `frontend` (aucune migration, aucun
> rebuild back-end).
