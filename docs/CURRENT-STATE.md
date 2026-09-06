# État courant — ESIC Connect

> **But** : donner en une lecture l'état **réel** du dépôt — ce qui est
> implémenté, testé, partiel ou absent — et les preuves associées.
>
> **Ce document est la seule source de vérité sur l'avancement.** En cas
> de contradiction avec le cahier des charges, la roadmap, le backlog ou
> le README, c'est ce document qui a raison, et l'autre qui doit être
> corrigé.

## Dernière mise à jour

### 6 septembre 2026 — campagne finale (checkpoint avant fusion/déploiement), branche `feat/ui-redesign-bootstrap-material`

Suite de la campagne finale. Livré et vérifié dans cette passe, **aucune
ligne de back-end, zéro migration** (schéma inchangé V34) :

- **§2 — Tableau de bord, ajustement desktop.** Variante responsable
  uniquement : ligne 1 « Mon activité » (indicateurs) | « Mon périmètre »
  (`.dashboard__split`, `minmax(1.7fr, 1fr)`), ligne 2 « Taux d'assiduité
  par classe » (plus large) | « Séances à venir » (`.dashboard__grid--facing`).
  Bascule `@container esic-content` (état du rail), pile sous 52 rem,
  repli `@media` sans container queries. Autres rôles intacts ; sélecteurs
  E2E conservés (`.dashboard__chart`, `.dashboard__bar-value`,
  `table.dashboard__table`). `dashboard.spec.ts` 22/22.
- **§3 — Panneau compact « Profil » dans l'en-tête.** Nouveau composant
  `app-profile-menu` : déclencheur (pastille + identifiant court, pastille
  seule sous 640 px) ouvrant un `mat-menu` ancré — adresse complète,
  rôle(s), contexte d'usage actif si multi-rôles, lien vers « Sécurité du
  compte » (`/mon-compte/securite`, route existante). `mat-menu` fournit
  ouverture clic + clavier, fermeture Échap + clic extérieur, piège de
  focus, `aria-haspopup` / `aria-expanded` / `aria-controls`,
  repositionnement près des bords. Pas de bouton « copier », aucun appel
  réseau, aucune donnée sensible ; la déconnexion reste un contrôle
  distinct non dupliqué. `app-shell` : adresse et rôles retirés du
  composant et du SCSS mort. `profile-menu.spec.ts` 6/6 ; `app-shell.spec.ts`
  mis à jour (l'adresse est désormais dans le panneau).
- **§6 (repo) — allègement du contenu versionné.** `docs/audit/`,
  `artifacts/report-screenshots/` (41 captures) et
  `docs/JOURNAL-BATCH-S02A-S11.md` retirés de l'index (conservés sur
  disque, ignorés, **sauvegardés hors dépôt** dans
  `~/esic-connect-local-docs/2026-09-06/`). `.gitignore` : `/artifacts/`,
  `/docs/audit/`, `/docs/JOURNAL-*.md`. `README` : section « Documents de
  travail locaux ». Aucune dépendance CI / script vers les fichiers
  retirés (vérifié). Aucun historique réécrit.
- **§7 — paquet de déploiement minimal.** `.dockerignore` durcis
  (`backend/`, `frontend/`) + nouveau `.dockerignore` racine (garde-fou
  contre un build racine involontaire). Les Dockerfiles étaient déjà
  multi-étapes. `docker compose -f compose.prod.yaml config` : valide.

**`NOT_PERFORMED` / différé — checkpoint avant fusion et déploiement
(choix du porteur) :**

- **§4 — lot complémentaire de données pédagogiques** (BTS SIO/CIEL 1-2,
  Bachelor CDA, ESIS 1-2, CPDIA 1-2 ; 4 familles de rythmes ; 3 mois de
  planning sans conflit par classe ; sites Malakoff/Paris + salles ;
  apprenants/formateurs fictifs ; jeux d'import valide/avertissement/
  bloquant/multi-anomalies/doublons vérifiés). **Non commencé.** Chantier
  back-end + données qui exige un back-end en profil `demo` sur
  `esic_connect_demo`, la réinitialisation de cette base (§5 du mandat,
  garde `ESIC_ALLOW_DEMO_RESET`), un générateur de planning déterministe
  sans conflit et une boucle de vérification par import réel. À traiter
  en passe dédiée.
- **§9 — fusion** de `feat/ui-redesign-bootstrap-material` dans
  `feat/demo-readiness-e2e-ui` (base de la PR #46) : **non faite**,
  checkpoint demandé avant.
- **§10 — déploiement Raspberry Pi + Quick Tunnel Cloudflare** :
  **non fait**. Cible = Pi (choix du porteur) ; ses coordonnées SSH /
  confirmation d'accessibilité / ARM64 restent à fournir. `cloudflared`
  tourne comme conteneur dans `compose.prod.yaml` (aucune installation
  hôte requise).
- Revue visuelle pilotée des écrans §2/§3 aux points de rupture ; recette
  Playwright ; audit accessibilité outillé.

### 6 septembre 2026 — campagne finale : D-01 tranchée (même branche `feat/ui-redesign-bootstrap-material`)

**D-01 — DÉCISION PRISE : OPTION 1 (statu quo).** Validée par le porteur.
Aucun changement de code, aucun changement de règle métier, zéro
migration. Vérification menée dans le code, les tests et la
documentation :

- **Ouverture automatique autour de l'horaire : non.** Aucun `@Scheduled`
  n'ouvre un point de contrôle. Seul `START` s'ouvre à l'ouverture de la
  séance par le formateur (`CourseSessionService.open()`) ; les autres
  s'ouvrent un par un (`AttendanceCheckpointService.open()`), sans
  contrôle de l'état des autres points — plusieurs `OPEN` simultanés
  restent permis.
- **Fermeture automatique après la fenêtre : non.** `close()` est
  manuel ; la fermeture de la séance ferme les points encore ouverts.
  Aucun balayage temporel.
- **Durées réelles :** `app.attendance.token-ttl` = `PT30S` (QR dynamique
  + code court, tournés à chaque émission ; profil test `PT1H`) ;
  `app.attendance.room-qr-open-before` = `PT15M` (QR fixe de salle
  accepté de `début − 15 min` au début, refusé strictement après).
- **Point resté `OPEN` après expiration de sa fenêtre :** le statut reste
  `OPEN` jusqu'à fermeture humaine ; mais sans jeton vivant il n'accepte
  plus rien. **`OPEN` ≠ « jeton utilisable ».**
- **Jeton expiré inutilisable :** confirmé (`resolve()` → vide ; Redis
  down → `503`, jamais dégradé).
- **Jeton précédent invalidé à l'émission d'un nouveau :** confirmé
  (bascule du pointeur d'autorité Redis ; `resolve()` n'accepte que le
  jeton exactement pointé).

Conformément au mandat, **aucune fermeture automatique n'a été inventée**.
La distinction statut / jeton est documentée dans
`docs/03-architecture.md` (`DEC-D01`) et `DECISIONS_NEEDED.md` (D-01,
désormais un registre de décision prise, plus une action en attente).
Couverture de tests inchangée — `AttendanceTokenServiceTests` (20 tests)
couvre déjà expiration et rotation ; aucun test nouveau requis.

### 6 septembre 2026 (nuit) — passe « navigation & dashboard réorganisés » (même branche `feat/ui-redesign-bootstrap-material`)

Base `45b9e09`, **7 commits** (HEAD = ce commit de documentation). **Aucune ligne de
back-end, zéro migration** (schéma inchangé V34). Non fusionné, non
poussé, **non déployé**. Détail complet :
`docs/audit/FINAL-DEPLOYED-UI-REPORT.md` (22 sections) + INDEX des
captures `artifacts/report-screenshots/INDEX.md`.

- **§1 Notifications** — une seule entrée latérale « Notifications » →
  coquille `NotificationsShell` à deux vues internes en onglets
  (`centre` / `preferences`). `/notifications` et
  `/notifications/preferences` restent valides (redirection).
- **§2 Suivi d'assiduité** — coquille `AttendanceManagementShell` :
  titre unique + navigation secondaire visible `.esic-subnav`,
  « Synthèse » listée et en premier. Cinq routes enfants inchangées.
- **§3 Tableau de bord** — « Accès rapides » remonté sous la bande
  d'identité ; grille de détail à **deux colonnes** dès ≈ 52 rem de
  largeur de contenu (`@container esic-content`), une colonne en pile en
  dessous ; listes de cartes bornées en hauteur. Restructuration 2×2
  littérale par rôle **non faite** (4 variantes + sélecteurs e2e figés) —
  arbitrage assumé dans le rapport.
- **§4 Organisation & planning** — quatre entrées latérales
  (Référentiels, Organisation, Planning, Alternance) fusionnées en une ;
  nouveau hub `/organisation-planning` ; `NavItem.matchPaths` +
  `activeNavPath` étendu. **Aucune route déplacée** — `/academic`,
  `/organization`, `/planning`, `/alternation` restent adressables.
- **Correctif (commits 5 & 6)** — état actif résiduel des onglets **et**
  du rail : une vue enfant qui réécrit ses filtres dans l'URL au
  chargement (Lot G) déclenche une navigation « même URL » terminée en
  `NavigationCancel` + `NavigationSkipped`, **sans `NavigationEnd`** ;
  `routerLinkActive` (et tout code ne filtrant que `NavigationEnd`)
  restait figé en mode zoneless. Corrigé en dérivant l'état de
  `router.url` sur `NavigationEnd` / `NavigationSkipped` /
  `NavigationCancel`. **Limite restante** : `aria-current` du rail n'est
  fiable qu'après une navigation client, pas au premier chargement d'une
  URL profonde (`.active` visible OK).

**Tests** : front-end `npx ng lint` vert · `npx ng test --watch=false`
→ **102 fichiers / 838 tests / 0 échec** · `npx ng build --configuration
production` sans alerte de budget (581,71 kB / 136,29 kB gzip) · typecheck
de la suite Playwright 0 erreur. Back-end **non rejoué** (0 fichier Java,
0 migration ; dernier résultat consigné §6.5 : 1231 tests).

**Recette navigateur (pile locale `demo`, `ESIC_DEMO_TOTP_SECRET` =
valeur d'exemple)** : nouveau `tests/14-report-screenshots.spec.ts`
**7 / 7** — Notifications et Suivi d'assiduité : exactement un onglet
actif, le bon, aucun résidu après aller-retour ; hub Organisation &
planning : entrée de rail groupée surlignée, maintenue sur `/academic` et
`/planning/import`. `tests/02` (libellés de navigation mis à jour),
`03` (routes regroupées en URL directe), `07` (coquille assiduité),
`08` (notifications + dashboard), `10` (correctif sélecteur
`.shell__topbar`) : **tous verts** après purge du seau
`LOGIN_ORIGIN_LIMIT` (les enchaînements longs saturent le compteur de
connexions — 18 « échecs » d'un run combiné, tous « Trop de tentatives »,
→ 18/18 au rejeu ; couplage d'environnement connu, `§6.1`).

**`NOT_PERFORMED`** : déploiement (aucune cible démo/recette — Pi, tunnel,
URL, `.env` de prod, `CLOUDFLARE_TUNNEL_TOKEN` absents ; paquet
`compose.prod.yaml` **prêt et valide**, documenté jusqu'à la dernière
commande) ; suite Playwright complète `tests/01..13` ; axe sur les écrans
authentifiés refondus ; Lighthouse ; **lot complémentaire** (référentiels
BTS/CIEL/CDA/ESIS/CPDIA, rythmes, plannings 3 mois, jeux d'import +
validation) — chantier back-end + scripts + données différé sur décision
de cadrage.

### 6 septembre 2026 (nuit) — campagne « one-shot » Lots A→P (branche `feat/ui-redesign-bootstrap-material`)

Base `314476b`, HEAD `f167b41`, **16 commits**, 83 fichiers
(+3692/−135). **Aucune ligne de back-end, zéro migration** (schéma
inchangé V34). Non fusionné, non poussé, non déployé. Détail complet :
`docs/audit/FINAL-ONE-SHOT-REPORT.md` (23 sections) + `docs/audit/LOT-A`,
`LOT-B`, `LOT-O` + `DECISIONS_NEEDED.md` (D-01).

- **A** — expiration de session glissante pilotée par l'activité
  (`SessionActivityService`, `session-timeout-warning`), avertissement
  accessible, multi-onglets, route de retour préservée. Front-end seul.
- **C** — un seul élément de navigation latéral actif (`activeNavPath`).
- **J** — anomalies d'import apprenants : filtre `BLOCKING` retiré (ne
  matchait aucune ligne), bloquantes/non bloquantes scindées, message
  « aucune anomalie », compteur réévalué après correction.
- **I** — **règle des fenêtres d'émargement NON modifiée** : le dépôt
  autorise plusieurs points de contrôle `OPEN` (le jeton unique est la
  vraie exclusion). Conflit avec l'hypothèse du mandat consigné dans
  `DECISIONS_NEEDED.md` D-01, 3 options. UI : explique la fenêtre active.
- **H** — création manuelle d'un apprenant (`/students/nouveau`,
  ADMIN/SUPER_ADMIN) : enchaîne 3 endpoints existants, non atomique,
  reprise guidée. Vérifié absent avant écriture.
- **G** — filtres / tri / pagination persistés dans l'URL sur 5 listes
  (`list-query-params`), restauration de défilement au retour.
- **D** — raccourcis du tableau de bord en grille compacte.
- **E** — connexion responsive (portrait compact, paysage court en 2
  colonnes).
- **F** — onglets de section : `RouterLinkActive` réellement importé
  (planning ne surlignait jamais), `ariaCurrentWhenActive`, styles
  actif/hover/focus.
- **K** — éditeur de correction de planning en panneau pleine largeur
  sous le tableau (plus de chevauchement/troncature).
- **L** — indice de défilement horizontal (CSS) sur les enveloppes de
  tableau.
- **B** — `PARTIAL` : signature ESIC (segment bleu→vert) sur l'en-tête
  de page ; passe de direction artistique écran par écran **NON faite**
  (inventaire : 297 `mat-card` identiques ; `docs/audit/LOT-B`).
- **O** — revue sécurité (`docs/audit/LOT-O`) : aucun secret, pas d'XSS,
  `npm audit` 0 vuln.
- **P** — `compose.prod.yaml` durci (rotation journaux ×5, healthcheck
  frontend) + `docs/deployment/{RASPBERRY-PI,PRE-FLIGHT,ROLLBACK,SECRETS}.md`.
  Jamais monté sur Pi.

**Tests** : back-end `./mvnw clean test` → **1231 tests / 0 échec**
(143 rapports Surefire ; identique, aucun code back-end touché).
Front-end **99 fichiers / 835 tests / 0 échec** (+49 nets), lint vert,
build production sans alerte de budget. Playwright
`tests/13-accessibility-axe.spec.ts` → **20/20** : axe WCAG 2.0/2.1 A+AA
0 violation critique/sérieuse sur les écrans publics, clavier + focus,
zoom 200 %. 15 captures publiques dans `artifacts/report-screenshots/`.

**`NOT_PERFORMED`** : recette Playwright complète (`tests/01..12`),
axe + captures des écrans **authentifiés**, Lighthouse. Motif : le
back-end en cours tourne en profil `local` (aucun compte de
démonstration) ; la pile `demo` exige un basculement du back-end.
Commande dans `FINAL-ONE-SHOT-REPORT.md` §15.

```text
5 septembre 2026 — sprint 11 terminé : tableaux de bord complets,
exports Excel et PDF, attestation d'assiduité identifiable, recherche
globale, consultation et export de la piste d'audit, flux iCalendar
signé et révocable.
Backend 1209 tests, frontend 764 tests, recette navigateur S11 13/13,
tout vert. Schéma en V34. Trois modules nouveaux : `document`,
`search` (sans table) et `integration`. ModularityTests vert — 19 modules.
Dette T-03 (coût SQL par séance) LEVÉE et vérifiée par une mesure.
`EF-INT-002` et `EF-INT-003` restent `PARTIAL` : les adaptateurs
Microsoft Graph sont écrits et couverts par des tests, mais AUCUN
LOCATAIRE MICROSOFT RÉEL n'a été sollicité — dette T-16. Sans
identifiants, l'API déclare `meetingActive: false`.
Limites du sprint 10 conservées telles quelles : T-13 (aucun service de
poussée réel), T-14 (hors ligne limité à une session ouverte), T-15
(file d'actions non persistante).
```

### 5 septembre 2026 (soir) — mandat hors sprint : démo, e2e, T-18

Travail mené sur une branche dédiée (`feat/demo-readiness-e2e-ui`, base
`batch/S02A-S11` @ `4b38178`), pas encore fusionnée. Portée : rendre le
second facteur réellement franchissable en démonstration et en recette
navigateur (T-19/T-20), livrer les écrans manquants des opérations de
masse et de la détection de doublons (T-18), et exécuter l'ensemble des
validations.

- **T-19/T-20 levées** : `DemoMfaProvisioner` (nouveau port public du
  module `identity`, implémenté uniquement sous `@Profile("demo")`)
  active un facteur TOTP déterministe pour `ADMIN`/`SUPER_ADMIN` de
  démonstration, strictement optionnel (`ESIC_DEMO_TOTP_SECRET` —
  absent, comportement inchangé). `scripts/seed-demo.sh` et
  `tests/support/auth.ts` franchissent désormais le VRAI défi
  `/mfa/verify` ou `/mfa/enroll` + `/mfa/enroll/confirm`, code calculé
  localement (RFC 6238 — port Python et TypeScript, aucune nouvelle
  dépendance), avec gestion de l'anti-rejeu réel (RG-054/055).
- **Défauts réels mis au jour et corrigés en cours de route**, sans
  rapport direct avec le MFA : la limite de débit de connexion par
  origine (`LOGIN_ORIGIN_LIMIT`, défaut 60/15 min) est trop basse pour
  un run complet de la suite ; un test appelait l'API d'authentification
  directement sans jamais gérer le défi MFA ; la détection de succès
  d'un helper de test reposait sur une course DOM plutôt que sur la
  vraie réponse HTTP ; une course entre `loginAsUi` et la redirection
  asynchrone du garde de route Angular faisait échouer une connexion sur
  une fraction des exécutions ; un test du parcours prioritaire créait
  toujours une séance à horaire figé (08:00), donc dépendant de l'heure
  du jour d'exécution de la suite.
- **T-18 levée** : sélection multiple, aperçu obligatoire (RG-034) puis
  confirmation explicite pour les opérations de masse sur
  `/administration` ; nouvel écran `/administration/duplicates`
  (`ADMIN`/`SUPER_ADMIN` uniquement, plus restreint que le reste de
  `/administration`), lecture seule, aucune fusion proposée (docs/02
  §9.5).
- **Dépendance** : avis modéré sur `qs` (transitif, outillage
  Angular CLI) corrigé par `npm audit fix` non forcé — 0 vulnérabilité.
- **Chiffres mesurés** : backend 1218 tests (140 classes, +9 vs sprint
  11 — nouveaux tests `DefaultDemoMfaProvisioner` et wiring
  `DemoDataInitializer`), `ModularityTests` vert (19 modules, aucun
  nouveau module), schéma inchangé en V34. Frontend 95 fichiers / 779
  tests (+15, nouvel écran doublons et opérations de masse), lint et
  build verts. Suite Playwright complète : voir §6.1 pour le décompte
  exact après ce lot.
- **Audit visuel** (desktop 1440px et mobile 390px, 8 écrans
  représentatifs, captures réelles) : aucun débordement horizontal
  détecté, conteneur de page partagé déjà cohérent
  (`app-shell.scss` `.shell__main`, `max-width: 72rem`), bouton de
  retour explicite déjà présent sur la quasi-totalité des écrans de
  détail (~30 fichiers) — aucune réécriture de grande ampleur jugée
  nécessaire ni entreprise.
- **Non traité, hors périmètre du mandat** : T-13 (poussée, exige des
  clés VAPID), T-16 (Microsoft, exige un locataire réel), T-04/T-11
  (antivirus — configuration cible par environnement documentée dans
  `.env.example`, comportement par défaut inchangé).

### 6 septembre 2026 — continuité de session au rechargement (cookie de renouvellement)

Même branche `feat/demo-readiness-e2e-ui`. Défaut signalé par le porteur :
**recharger la page déconnecte l'utilisateur**. Le jeton d'accès ne vit
qu'en mémoire (RG-093) et rien ne le reconstruisait ; `restoreSession()`
était un no-op explicite, en attente du back-end. La stratégie était
déjà écrite au cahier (docs/02 §17.7, docs/03 §15.2, docs/08 §6) : jeton
de renouvellement rotatif en cookie `HttpOnly`. Exigence non implémentée,
pas un changement de règle.

- **Back-end (module `identity` uniquement, aucun nouveau module)** :
  - `RefreshTokenStore` — une clé Redis par session (`esic:auth:refresh:{familyId}`),
    empreinte SHA-256 du secret courant, jamais le secret. Rotation à
    chaque usage ; un cookie dont le secret ne correspond plus est traité
    comme un rejeu et **coupe toute la famille**. Deux bornes : inactivité
    glissante (`JWT_REFRESH_TOKEN_IDLE_TTL`, défaut `PT30M` — « 30 minutes
    d'inactivité » du cahier) et plafond absolu jamais repoussé
    (`JWT_REFRESH_TOKEN_ABSOLUTE_TTL`, défaut `PT12H`).
  - `RefreshCookies` — `HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth`,
    `Secure` piloté par `APP_COOKIE_SECURE` (vrai par défaut, y compris
    en profil `demo` derrière le tunnel HTTPS ; `false` seulement pour
    `local` et `test`, `localhost` restant un contexte sûr côté
    navigateur).
  - `RefreshService` + `RefreshController` — `POST /api/v1/auth/refresh`
    (**route publique** : le cookie fait foi), rotation, revérification de
    l'état du compte, réémission d'un jeton d'accès **portant les mêmes
    `amr`** que la session d'origine (EF-AUTH-015). CSRF : `SameSite=Strict`
    + réponse qui ne rend le jeton que dans son corps + aucune autorité
    ambiante par cookie sur les routes métier → pas de jeton anti-CSRF
    distinct. Tout échec → `401` nu, indistinguable (`RefreshTokenException`).
  - Révocation globale : `refresh` recharge le compte et applique la règle
    de `RevokedTokenValidator` — une famille ouverte avant
    `credentials_invalidated_at` est refusée. Changement de mot de passe,
    suspension, `logout-all` neutralisent donc aussi le renouvellement.
    `POST /auth/logout` supprime la famille et vide le cookie.
  - `GET /api/v1/auth/me` (docs/02 §30.2) — `{subject, email, roles}`,
    protégé par jeton ; sert au front à réafficher l'identité après un
    renouvellement.
  - Les 4 chemins qui délivrent un jeton (`/login` sans MFA, `/mfa/verify`,
    `/mfa/enroll/confirm` branche défi, `/webauthn/login`) posent le
    cookie. Redis indisponible **à l'émission** : la connexion réussit
    sans cookie (le jeton d'accès reste valable) ; **au renouvellement** :
    `401`, aucune session dégradée.
- **Front-end** :
  - `AuthService.restoreSession()` — enchaîne `POST /auth/refresh`
    (`withCredentials`) puis `GET /auth/me` ; sans cookie valide, démarrage
    anonyme silencieux.
  - `AuthService.refreshSession()` — renouvellement en cours de session,
    **file unique** (`shareReplay`), utilisé par l'intercepteur d'erreurs :
    un `401` métier déclenche un renouvellement puis un **rejeu unique**
    de la requête ; l'utilisateur n'est renvoyé vers `/login` que si le
    renouvellement échoue. Les routes `/v1/auth/*` sont exclues du rejeu
    (pas de boucle).
  - Aucun `localStorage`/`sessionStorage` : le cookie est `HttpOnly`, le
    jeton d'accès reste en mémoire seule.
- **T-14 reformulée** : le rechargement **en ligne** rétablit désormais
  la session. Reste non couvert : un **démarrage à froid hors ligne** (le
  cookie exige le réseau ; le jeton ne vit qu'en mémoire — RG-093).
- **Tests** : voir §6.5. Back-end `RefreshTokenIntegrationTests` (11) +
  `RefreshTokenExpiryIntegrationTests` (2, durées courtes via `properties`).
  Front-end `auth.service.spec.ts` et `api-error.interceptor.spec.ts`
  étendus (restauration OK/KO, file unique, rejeu, échec → `/login`).
  `ModularityTests` inchangé (19 modules), schéma inchangé (V34, aucune
  migration). **Recette navigateur non rejouée** — `NOT_PERFORMED` pour
  le parcours « recharger la page reste connecté » (à ajouter, §10).

### 6 septembre 2026 (après-midi) — refonte UI : socle du système de design

Branche **`feat/ui-redesign-bootstrap-material`** (base
`feat/demo-readiness-e2e-ui`, non fusionnée). Refonte **visuelle et UX**
uniquement — aucune règle métier, aucun contrôleur, aucune migration
touchés. **`PARTIAL` : socle livré, refonte écran par écran NON faite.**

**Livré dans ce lot** :
- `src/styles/_tokens.scss` — point unique de l'identité (couleurs vert /
  bleu ESIC, espacements, rayons, ombres, statuts d'assiduité) en
  variables `--esic-*`.
- `src/styles/_esic-palette.scss` — palette Material 3 générée
  (`ng generate @angular/material:m3-theme`) depuis primaire `#134E9C`,
  secondaire `#1F7A4C`, tertiaire `#B26A00`, erreur `#B3261E`.
- `src/styles/_bootstrap-bridge.scss` — Bootstrap 5.3.3 (SCSS, **sans
  JS**) : grille + conteneurs + utilitaires responsive **triés** (pas de
  composant Bootstrap, pas de couleurs/bordures/typo/ombres), variables
  réécrites sur l'échelle ESIC, 5 points de rupture.
- `src/styles/_primitives.scss` — en-tête de page, carte de contenu,
  **pastille de statut** (`.esic-status--present/late/absent/excused/company/pending`),
  badge, tableau de données, liste clé/valeur, état vide.
- `src/styles/_material-overrides.scss` + bloc `html` de `styles.scss` —
  réalignement des jetons système Material (`--mat-sys-*`,
  `--mat-button-*-container-shape`) sur ESIC : surfaces, filets, rayons
  (boutons en capsule → rayon de contrôle 4 px), élévations (2 ombres),
  arête active structurelle du menu.
- `index.html` — polices IBM Plex Sans (interface) + IBM Plex Serif
  (titres, documents), `theme-color` `#134e9c`.
- **Coquille applicative** (`app-shell`, `role-context-menu`) : barre
  supérieure blanche fine + pastille monogramme + mot-symbole ; rail de
  navigation avec arête active bleue ; identité sur une ligne ;
  compactage responsive (contexte et déconnexion en icône seule,
  jetons de rôle masqués sous 1100 px). Aucun débordement horizontal à
  390 px.
- **Rail de navigation repliable** (desktop) : bascule dépliée (16 rem,
  icône + libellé) ↔ repliée (3,5 rem, bande d'icônes). Préférence
  mémorisée par appareil (`localStorage` protégé, RG-093 — commodité
  d'affichage, jamais un jeton). Repliée : libellés dans le DOM pour les
  technologies d'assistance + infobulle au survol / focus.
  `mat-sidenav-container [autosize]` pour que le contenu se recale.
- **Vrai logo ESIC** : trouvé non suivi à la racine du dépôt
  (`logo esic 1.png`), déplacé dans `frontend/public/brand/logo-esic.png`.
- **Écrans publics d'authentification** (connexion, second facteur, mot de
  passe oublié, réinitialisation, activation) : mise en page **partagée**
  dans `src/styles/_auth.scss` (namespace `.esic-auth`) — carte unique,
  logo ESIC, titre serif, encarts d'information / d'avertissement à filet
  de couleur, ligne d'erreur à hauteur réservée. ~150 lignes de SCSS
  dupliquées par écran supprimées ; aucune règle métier touchée.

**Suite de la branche** : les étapes 5 à 14 ont été menées après ce lot —
voir les entrées datées ci-dessus (tableau de bord, pages métier,
formulaires, tableaux/listes, dialogues, responsive, icônes) et l'entrée
« étapes 12-14 + audit final » plus bas. Restent `NOT_PERFORMED` : le jeu
de données de démonstration élargi (relève du back-end, hors périmètre de
cette branche UI) et l'audit accessibilité outillé. Le **tableau de bord**
est fait (voir
« étape 5 » ci-dessous).

**Dépendance ajoutée** : `bootstrap@5.3.3` (+ `@popperjs/core` transitif,
non utilisé). `npm audit` : 0 vulnérabilité.

**Budget de bundle** : `maximumWarning` initial relevé de `500kB` à
`600kB` dans `angular.json`. Justification : la feuille de style passe à
**78,7 kB brut / 6,6 kB transféré (gzip)** avec la grille Bootstrap ;
total initial **568 kB brut / 132 kB transféré**. `maximumError` inchangé
(1 MB).

**Tests** (branche `feat/ui-redesign-bootstrap-material`, 6 septembre
2026, même environnement que §6) :

| Commande | Résultat |
|---|---|
| `cd frontend && npm run lint` | « All files pass linting » |
| `cd frontend && npm test -- --watch=false` | **95 fichiers / 786 tests / 0 échec** |
| `cd frontend && npx ng build --configuration production` | bundle produit, **aucune alerte de budget** (seuil 600 kB) |
| Revue visuelle pilotée (Chromium 1440 px et 390 px, connexion démo `responsable@example.test`) | connexion, tableau de bord, liste des séances : identité ESIC appliquée, aucun débordement horizontal, barre supérieure compacte sur mobile |

`NOT_PERFORMED` : recette Playwright (non rejouée) ; audit
accessibilité outillé ; revue sur tablette / pliable / ultralarge réels.

### 6 septembre 2026 (soir) — refonte UI : étape 6, pages métier

Même branche. **`PARTIAL` : toutes les aires métier sont alignées sur le
système de design ; l'audit visuel écran par écran des aires 2 à 9 reste
`NOT_PERFORMED` (voir plus bas).**

Neuf lots (`34472a4` → `e48fedd`), une aire à la fois, `lint` + `build`
prod + **786 tests / 0 échec** vérifiés à chaque commit :

| Lot | Aire | Écrans |
|---|---|---|
| 1 | Séances | liste, détail, formulaire |
| 2 | Apprenants | liste, fiche, import, revue d'import |
| 3 | Alternance | modèles (liste/fiche/formulaire), affectations classe, exceptions inscription, aperçu de cycle |
| 4 | Organisation + Référentiels | sites (liste/fiche/formulaire), référentiels académiques (liste/fiche) |
| 5 | Réclamations | liste, fil |
| 6 | Assiduité | mes présences ×4, gestion ×3, émargement, panneau départs anticipés |
| 7 | Planning | import, revue d'import, calendrier, versions |
| 8 | Administration + Invitations | comptes (liste/fiche), doublons, invitations, invitations non activées |
| 9 | Transverses | sécurité du compte, attestations, audit, abonnement calendrier, notifications (+ préférences), effets de bord, recherche globale, matières |

**Méthode.** Quatre primitives ajoutées à `_primitives.scss` : `.esic-filters`
(barre de recherche + actions), `.esic-note` (encart d'état, `--danger` /
`--warning`), `.esic-section` (sous-section titrée), `.esic-reveal`
(panneau dépliant). Correspondance **statut métier → tonalité** centralisée
dans `.esic-badge[data-status]` (ACTIVE, PUBLISHED, PENDING…, SUSPENDED,
ARCHIVED, FAILED, DELIVERED, REOPENED… — une seule table, les gabarits
posent la valeur brute). Chaque partiel `_*-common.scss` (ou SCSS d'écran
isolé) réécrit sur les jetons `--esic-*` **en conservant les classes
`.area__*`** : les gabarits ne changent que pour l'en-tête
(`.esic-page-header`, titre serif + filet), la pastille de statut et
l'enveloppe de table à défilement contenu. Le lien de retour est le
primitif unique `.esic-back` (lot `f606636`, 13 écrans + 4 ajoutés ici).

**Résultat mesurable.** Plus **aucune** référence `--mat-sys-*` directe ni
couleur en dur dans `src/app/**/*.scss` (hors fichiers de jetons) — toute
la couche de présentation dérive du système de design ESIC.

**Aucun `.ts`, aucune logique métier touchés.** Une seule assertion de
test ajustée (`dashboard.spec.ts`, libellé « Comptes actifs »).

`NOT_PERFORMED` : audit visuel piloté écran par écran des aires 2 à 9
(le back-end local est devenu injoignable après un incident disque plein
en cours de session — les captures des aires 1 « Séances » et du tableau
de bord ont été faites ; les autres écrans partagent les mêmes primitives
déjà vérifiées mais n'ont pas été re-capturés). Recette Playwright non
rejouée. Audit accessibilité outillé non fait.

### 6 septembre 2026 (soir) — refonte UI : étape 7, formulaires

Même branche. **`PARTIAL` : les formulaires de saisie dédiés sont alignés
sur une primitive commune ; audit visuel piloté `NOT_PERFORMED` (back-end
local toujours injoignable).**

Deux lots (`4e673a1`, `3926d4a`), `lint` + `build` prod + **786 tests /
0 échec** à chaque commit.

**Primitive `.esic-form*`** ajoutée à `_primitives.scss` : colonne bornée
(`max-width` 42 rem, `--wide` 52 rem), `.esic-form__grid` (grappe de
champs courts, `auto-fit` sur la largeur disponible), `.esic-form__row`
(champ pleine largeur), `.esic-form__group` (`<fieldset>` remis à zéro
puis redécoré, légende serif — regroupement fonctionnel, docs/02 §34.1),
`.esic-form__hint` (aide autonome, distincte de `mat-hint`),
`.esic-form__required-note` (« * » explicité une fois par formulaire —
Material appose déjà l'astérisque sur le libellé d'un champ requis),
`.esic-form__error` (erreur de **soumission**, niveau formulaire, toujours
`role="alert"` — distincte de `mat-error`), `.esic-form__actions` (pied à
filet : action primaire `mat-flat-button` + échappatoire `mat-button`).

**Appliquée** en conservant les classes `.area__*` (mêmes gabarits, à
l'en-tête / la grille / le pied près) :

| Lot | Formulaires |
|---|---|
| 1 | séance exceptionnelle, modèle de rythme (`<fieldset>` → `.esic-form__group`), affectation de rythme, exception d'inscription, site (8 champs courts → grille), ajout de matière, création de compte |
| 2 | ouverture de réclamation + réponse/transfert/décision/réouverture du fil, attestations (émettre / vérifier), abonnement calendrier, émargement (code court + QR de salle), départs anticipés (transmettre / décider) |

Les blocs `.X__form`, `.X__form-actions`, `.X__inline-error` /
`.X__form-error` des SCSS communs et isolés (`_sessions-common`,
`_alt-common`, `organization.shared`, `pattern-form`, `site-form`,
`user-list`, `subject-list`, `claim-list`, `attestations`,
`calendar-subscriptions`, `attendance-check-in`, `early-departure-panel`)
sont réduits à une **délégation** vers la primitive.

**Aucun `.ts`, aucune logique métier, aucune assertion de test touchés.**

**Non repris, signalés tels quels** : `student-import-home` et
`planning-import` (formulaires-**panneau** à gabarit propre — bordure,
fond, `padding` —, déjà sur jetons) ; `account-security` (motif « champ +
bouton en ligne » délibéré) ; les **barres de filtres** `.X__filters`
(relèvent de l'étape 8, tableaux / listes).

`NOT_PERFORMED` : audit visuel piloté (back-end local injoignable) ;
recette Playwright non rejouée ; audit accessibilité outillé non fait.

### 6 septembre 2026 (soir) — refonte UI : étapes 8 (tableaux/listes) et 9 (dialogues)

Même branche. Deux commits (`a365bda`, `8869379`), `lint` + `build` prod +
**786 tests / 0 échec**.

**Étape 8 — tableaux et listes.**
- **Barres de filtres** consolidées sur la primitive `.esic-filters` : les
  **17** formulaires `.X__filters` de l'application (séances, sites,
  alternance ×5, apprenants, comptes, réclamations, audit, assiduité ×4,
  référentiels) portent désormais aussi `esic-filters` ; les blocs
  réimplémentés à l'identique dans 9 SCSS (`_sessions-common`,
  `_alt-common`, `organization.shared`, `claim-list`, `user-list`,
  `student-list`, `academic-reference-list`, `audit-trail`,
  `my-attendance-list`) sont retirés. Ne restent que les rangées d'actions
  `.X__filter-actions`, en helper autonome (réutilisées hors filtres dans
  l'aire assiduité — corrections, décisions).
- **Défilement horizontal contenu** généralisé : les `mat-table` de
  `subject-list` et les deux de `invitation-list` — seules encore sans
  enveloppe — sont posées dans `.esic-table-wrap`. La page ne défile
  jamais horizontalement ; la table, si, dans son conteneur. Toutes les
  autres listes avaient déjà leur enveloppe (`.X__table-wrapper` ou
  `.esic-table-wrap`, étape 6).
- Tri (`matSort`) et pagination (`mat-paginator`) : composants Material,
  déjà réalignés par les jetons système (`_material-overrides.scss`) —
  rien de spécifique ajouté.

**Étape 9 — dialogues.** Constat : l'application n'ouvre **aucune fenêtre
modale** — `MatDialog` n'est utilisé nulle part. Les confirmations et
saisies contextuelles s'ouvrent **en creux dans le flux de la page**
(`role="group"` + `aria-label`), pas en superposition : aucun problème de
`z-index`, aucun piège de focus à gérer (aligné sur le mandat).
- **`.esic-reveal`** complétée dans `_primitives.scss` : `__title` (serif),
  `__text`, `__actions`, modificateur `--danger` (arête rouge pour une
  action destructrice). Commentaire d'en-tête qui fige la règle « pas de
  modal ».
- **Bandeau transitoire** (`MatSnackBar`, seule surcouche du produit) :
  stylé ESIC dans `_material-overrides.scss` — encre claire sur fond
  encré, ombre « float », arête d'accent, action « Fermer » lisible ; la
  variante erreur (`panelClass: 'app-snackbar-error'`), jusqu'ici sans
  aucun style, devient rouge ESIC et distincte du bandeau d'information.
- Confirmations `org__confirm` (archivage de site, `--danger`) et
  `plan__confirm` (publication de planning) convergées sur `.esic-reveal`,
  blocs SCSS dupliqués supprimés. Les panneaux de saisie contextuels
  (`sessions__reveal`, `alt__reveal`, `att__reveal`…) gardent leur nom —
  ils délèguent déjà au même vocabulaire de jetons (étape 6) ; un
  renommage complet serait purement cosmétique.

**Aucun `.ts`, aucune règle métier, aucune assertion de test touchés.**
`NOT_PERFORMED` : audit visuel piloté (back-end local injoignable) ;
recette Playwright non rejouée ; audit accessibilité outillé.

### 6 septembre 2026 (soir) — refonte UI : étapes 10 (responsive) et 11 (icônes)

Même branche. Deux commits (`a330fcb`, `cf26086`), `lint` + `build` prod +
**786 tests / 0 échec**.

**Étape 10 — responsive, passe de finition.** Le socle (grilles
`minmax(min(100%, Nrem), 1fr)`, compaction du rail, `100dvh`) était posé
aux étapes 3-6. Ajouts, tous par largeur / orientation, jamais par modèle
d'appareil :
- `.shell__main` devient un **conteneur** (`container: esic-content /
  inline-size`) : les requêtes `@container` des primitives (`.esic-kv`…)
  se calent sur la largeur réelle de la colonne de contenu — qui varie
  selon que le rail est déplié ou replié —, jamais sur la fenêtre.
  `.esic-auth__card` aussi.
- Pliable replié (`< 22rem`) : la barre supérieure ne garde que la
  pastille de marque, lâche le mot-symbole.
- Paysage court (`orientation: landscape` et `hauteur < 30rem`) : barre
  supérieure resserrée à 3 rem, padding de contenu réduit.
- Fenêtre courte en authentification (`hauteur < 34rem`) : la carte
  s'ancre en haut et défile, au lieu d'être rognée par le centrage.

**Étape 11 — favicon, icônes PWA, monogramme.** Fin du placeholder « E »
bleu. Toutes les icônes sont dérivées du **« E » du logo ESIC officiel**
(`public/brand/logo-esic.png`), **recadré sans déformation ni
recoloration** (`sips`), centré sur fond blanc — la présentation même du
logo sur les écrans d'authentification. La règle « ne jamais falsifier ni
déformer le logo » est respectée : ce sont les pixels authentiques du
glyphe, jamais un redessin.
- Ajoutés / régénérés : `favicon.svg` (glyphe en raster embarqué) +
  `favicon.ico` multi-tailles 16/32/48 ; `icons/icon-{192,512}.png`
  (`any`) ; `icons/icon-maskable-{192,512}.png` (glyphe dans la zone de
  sécurité centrale) ; `icons/apple-touch-icon.png` 180×180 opaque ;
  `brand/mark-esic.png` — le monogramme du bandeau (`.shell__mark`)
  abandonne la forme géométrique neutre pour le vrai « E ».
- `index.html` : `rel="icon"` SVG d'abord, `.ico` en repli ;
  `apple-touch-icon` → nouvelle image 180.
- `manifest.webmanifest` : `theme_color` `#0d47a1`→`#134e9c` (aligné sur
  `--esic-primary` et `<meta theme-color>`), `background_color`
  `#fafafa`→`#f4f6f8` (`--esic-paper`), `id: "/"`, entrée maskable 192.
- `sw.js` : `VERSION` `v1`→`v2` (rafraîchit le cache de coquille),
  `/favicon.svg` précaché.

`NOT_PERFORMED` : rendu réel des icônes dans un navigateur / à
l'installation PWA (vérifié visuellement sur les PNG générés, pas
in-situ) ; recette Playwright ; audit accessibilité outillé.

### 6 septembre 2026 (soir) — refonte UI : étapes 12-14 + audit final

Même branche. Commits `cf26086` (icônes), `<docs>` (cette entrée).

**Étape 12 — données de démonstration.** Rien à faire côté branche UI. Le
jeu de démonstration appartient au back-end (`DemoDataInitializer`,
`scripts/seed-demo.sh`, profil `demo`) ; l'enrichir ne relève pas d'une
refonte visuelle et **sortirait la PR UI de son périmètre** (« la PR UI
ne contient que la refonte »). Le back-end local est par ailleurs
injoignable depuis l'incident disque plein. `NOT_PERFORMED` — suivi
séparément.

**Étape 13 — tests.** La refonte est CSS + gabarits : **aucune logique
nouvelle à couvrir**. Sur les ~18 commits de la branche, **une seule**
assertion de test a été ajustée (`dashboard.spec.ts`, libellé « Comptes
actifs », étape 5). À chaque commit : `npm run lint` vert,
`npx ng test --watch=false` → **95 fichiers / 786 tests / 0 échec**,
`npx ng build --configuration production` sans alerte de budget. Les
sélecteurs sur lesquels s'appuie la recette Playwright ont été
**délibérément conservés** : `.dashboard__chart`, `.dashboard__bar-value`,
`table.dashboard__table caption`, `form.upload`,
`input[formcontrolname="password"]`, `button[type="submit"]`.
`NOT_PERFORMED` : recette Playwright (pile de démonstration requise,
back-end injoignable) ; contrôle accessibilité outillé (axe / Lighthouse).

**Étape 14 — documentation.** `docs/03-architecture.md` §9.7 « Système de
design » réécrit : catalogue complet des primitives, règle « aucune
fenêtre modale », `.shell__main` conteneur `@container`, stratégie
responsive largeur/orientation, dérivation des icônes depuis le « E » du
logo réel. `docs/CURRENT-STATE.md` tenu à jour à chaque étape (entrées
ci-dessus).

**Audit final (couche de présentation).**

| Contrôle | Résultat |
|---|---|
| `grep -rn "mat-sys-\|#rrggbb\|rgb(0 0 0 / …)" src/app/**/*.scss` hors `_tokens`/`_esic-palette` | **0 occurrence** — `module-placeholder.scss` (composant partagé oublié à l'étape 6) corrigé ici |
| `npm run lint` | « All files pass linting » |
| `npx ng test --watch=false` | 95 fichiers / 786 tests / 0 échec |
| `npx ng build --configuration production` | `styles.css` **87,6 kB brut / 7,9 kB gzip** ; total initial **577 kB / 134 kB gzip** ; aucune alerte de budget (seuil 600 kB) |
| `.ts` touchés | **0** (hors 1 assertion de test à l'étape 5) |
| Règles métier / contrôleurs / migrations touchés | **0** |

**Reste `NOT_PERFORMED` pour clore la branche** : revue visuelle pilotée
écran par écran (back-end local à redémarrer), recette Playwright complète,
audit accessibilité outillé (axe-core / Lighthouse) et navigation clavier
sur les écrans refondus. Ces trois contrôles conditionnent le passage de
la PR #46 de brouillon à « prête ».

### 6 septembre 2026 (soir) — refonte UI : étape 5, tableau de bord

Même branche `feat/ui-redesign-bootstrap-material`. Refonte **visuelle et
UX** du tableau de bord des quatre rôles ; aucun champ, endpoint, rôle ni
règle métier touché — `dashboard-api.service.ts` et `dashboard.models.ts`
inchangés.

- **Trois registres visuels distincts** au lieu d'une pile de cartes
  identiques : (1) *bande d'identité* — compte / identifiant / expiration
  + jetons de rôle + phrase de contexte, sur un simple filet de base,
  jamais accentuée ; (2) *bandeau d'indicateurs* — les chiffres clés en
  grand (chasse tabulaire), un seul bloc bordé divisé par des filets,
  point d'entrée du regard ; (3) *cartes de détail* — surface Material
  réalignée.
- **Le bandeau d'indicateurs réutilise le vocabulaire de couleur des
  statuts d'assiduité** (`_tokens.scss`) : chaque cellule d'assiduité
  porte le point + le filet supérieur de son statut — présences en vert,
  retards en ocre, absences en rouge, excusées en bleu, en attente en
  ocre. La couleur ne porte jamais seule l'information : libellé + point
  (docs/02 §32.5).
- **Défaut fonctionnel corrigé** : `.dashboard__card { height: 100% }` +
  `grid auto-fit` étirait toutes les cartes d'une rangée à la hauteur de
  la plus grande — d'où les panneaux « Session » / « Rôles » hauts et
  vides des captures précédentes. La grille est désormais
  `align-items: start` et la règle `height: 100%` supprimée : une carte
  courte reste courte.
- **Tableaux équivalents** (EF-REP-008) enveloppés dans `.esic-table-wrap`
  (défilement horizontal contenu, jamais le `<body>`) et portant la
  classe `.esic-table` des primitives ; l'histogramme passe aux jetons
  (piste en creux, remplissage bleu ESIC, barre fine). Le graphique et la
  table lisent toujours la **même liste** ; chaque barre garde sa valeur
  en toutes lettres.
- En-tête `.esic-page-header` (titre serif), sous-titres de section en
  serif, listes de séances en lignes compactes séparées d'un filet.
- Carte « Comptes » de l'administration **retirée** : elle dupliquait à
  l'identique le bandeau d'indicateurs. Le test
  `dashboard.spec.ts` correspondant a été ajusté au nouveau libellé
  (« Comptes actifs », « En attente d'activation »).

**Liens de retour unifiés.** Les écrans de détail portaient jusqu'ici
**cinq** implémentations distinctes du lien « Retour » (`<nav>` étiqueté
à tort « Fil d'Ariane » + glyphe `←`, `<a mat-stroked-button>` avec `←`,
`<p class="X__back">` avec `<mat-icon>`, `<a mat-button>`…). Nouveau
primitif unique `.esic-back` (`_primitives.scss`) : lien simple, icône
`arrow_back` en tête (plus de glyphe texte), placé avant l'en-tête de
page, anneau de focus clavier. Appliqué à **13 écrans** (séances,
réclamations, apprenants, imports, comptes, doublons, alternance, mes
présences) ; classes par écran (`sessions__crumbs`, `alt__back`,
`profile__back`, `account__back`, `duplicates__back`, `att__crumbs`
orphelin) et leur SCSS supprimés. Restent hors de ce lot : le lien
« Retour au tableau de bord » en pied de l'écran d'émargement
(`attendance-check-in`, à traiter à l'étape 6) et les navigations à
onglets `att__crumbs` / `plan__tabs` / `alt__tabs` (ce ne sont pas des
liens de retour).

**Budget de style par composant** : `anyComponentStyle.maximumWarning`
relevé de `4 kB` à `8 kB` et `maximumError` de `8 kB` à `12 kB` dans
`angular.json`. Le SCSS *scopé* du tableau de bord (4 variantes de rôle,
bandeau, histogramme, adaptatif) compile à **~5,3 kB** — le seuil par
défaut de 4 kB d'Angular est serré pour un écran de cette densité.
`styles.scss` global inchangé (81 kB), budget initial (600 kB) intact.

**Tests** (même environnement que §6) :

| Commande | Résultat |
|---|---|
| `cd frontend && npm run lint` | « All files pass linting » |
| `cd frontend && npx ng test --watch=false` | **95 fichiers / 786 tests / 0 échec** (dont `dashboard.spec.ts` 22/22) |
| `cd frontend && npx ng build --configuration production` | bundle produit, **aucune alerte de budget** |
| Revue visuelle pilotée (Chromium, `local` sur `esic_connect`) | tableau de bord *responsable* 1440 px et 390 px, tableau de bord *apprenant* 1440 px : hiérarchie appliquée, bandeau d'indicateurs aux couleurs de statut, cartes à hauteur naturelle, `body.scrollWidth === clientWidth` (aucun débordement) aux deux largeurs |

`NOT_PERFORMED` : tableau de bord *administration* et *formateur* en
navigateur (mêmes primitives que *responsable*, non re-capturés) ;
recette Playwright `tests/11-pilotage-restitution.spec.ts` (sélecteurs
`.dashboard__chart` / `.dashboard__bar-value` / `table.dashboard__table`
**conservés**, suite non rejouée) ; audit accessibilité outillé.

## Repère Git

| Élément | Valeur |
|---|---|
| Branche de travail | `batch/S02A-S11` (lot de sprints S2 → S11) |
| Base | `f0d02d4` sur `feature/produit-complet-v2` |
| Jalons posés | `v0.2` (S2), `v0.3` (S3), `v0.4` (S4), `v0.5` (S5), `v0.6` (S6), `v0.7` (S7), `v0.8` (S8), `v0.9` (S9), `v0.10` (S10), `v0.11` (S11) |
| Documents cadres | `docs/01-cadrage.md` v3.0, `docs/02-cahier-des-charges.md` v2.0 |

---

## 1. Couverture des exigences

Le cahier des charges v2.0 définit **142 exigences fonctionnelles**.

| Statut | Nombre | Part |
|---|---:|---:|
| `IMPLEMENTED_AND_TESTED` | 112 | 79 % |
| `PARTIAL` | 5 | 3 % |
| `NOT_IMPLEMENTED` | 25 | 18 % |

Cette répartition est **attendue** : la version 2.0 du cahier des
charges a volontairement élargi le périmètre à l'ensemble du produit
cible. Les 35 exigences non implémentées ne sont pas des régressions :
ce sont les sprints 11 à 13 de la roadmap.

Le sprint 11 fait passer **huit** exigences de `NOT_IMPLEMENTED` à
`IMPLEMENTED_AND_TESTED` — `EF-REP-004` (Excel), `EF-REP-005` (PDF),
`EF-REP-006` (attestation identifiable), `EF-REP-008` (tableau
équivalent), `EF-REP-010` (invitations non activées), `EF-USER-009`
(recherche globale), `EF-AUD-002` (consultation et export de l'audit),
`EF-INT-001` (flux iCalendar) — et clôt un partiel : `EF-REP-007`, dont
les cartes du responsable pédagogique et de l'administration étaient
incomplètes.

Deux exigences deviennent `PARTIAL` **plutôt que livrées**, et il faut
le lire comme tel :

- `EF-INT-002` (réunion Teams depuis une séance distancielle) et
  `EF-INT-003` (écriture dans un calendrier Microsoft) — le port, les
  adaptateurs Graph et l'adaptateur inactif sont écrits et testés, mais
  **aucun locataire Microsoft réel n'a été sollicité** (dette T-16).
  Sans `tenantId`, `clientId` et `clientSecret` fournis par
  l'environnement, l'adaptateur inactif répond et
  `GET /api/v1/integrations/microsoft/status` déclare
  `meetingActive: false`. Il ne simule aucune réunion.

Le sprint 10 fait passer sept exigences de `NOT_IMPLEMENTED` à
`IMPLEMENTED_AND_TESTED` — `EF-AUD-003` (outbox), `EF-OPS-005` (rejeu
manuel), `EF-NOTIF-003` (audience), `EF-NOTIF-004` (courriel),
`EF-NOTIF-006` (préférences), `EF-PWA-001` (installable), `EF-PWA-003`
(file d'actions différées) — et clôt un partiel : `EF-NOTIF-002`, dont
l'audience se limitait au formateur.

Deux exigences deviennent `PARTIAL` plutôt que livrées, et il faut le
lire comme tel :

- `EF-NOTIF-005` (poussée) — le chiffrement RFC 8291 est vérifié contre
  le vecteur de test officiel de la RFC, le cycle d'abonnement et les
  préférences sont livrés, mais **aucun service de poussée réel n'a été
  sollicité**. Sans clés VAPID, l'adaptateur inactif répond et l'API
  déclare `providerActive: false` ;
- `EF-PWA-002` (consultation hors ligne) — le service worker sert le
  planning, l'assiduité et les notifications depuis son cache quand le
  réseau tombe, **application ouverte**. Le jeton ne vivant qu'en mémoire
  (RG-093), un démarrage à froid sans réseau affiche l'écran de
  connexion : il n'y a pas de session à rétablir.

Le sprint 9 en ajoute sept et clôt un partiel : `EF-CLAIM-001` à
`EF-CLAIM-004` (réclamations), `EF-ATT-013` (départ anticipé),
`EF-ATT-014` (journal de transparence) et `EF-JUS-002`, qui passe de
`PARTIAL` à `IMPLEMENTED_AND_TESTED` — sous la réserve explicite qu'aucun
analyseur antivirus n'est actif par défaut (§3 et §8).

> **Correction de comptage.** Le tableau §1.1 et ces totaux divergeaient
> depuis plusieurs sprints : la ligne « émargement et assiduité » portait
> un partiel qui n'existait pas et sous-comptait ses absents. Les chiffres
> ci-dessus sont désormais la **somme exacte** du tableau §1.1, elle-même
> alignée sur les listes §2, §3 et §4. Le dépôt a raison, le compteur
> avait tort.

Le sprint 2 a fait passer huit exigences de `NOT_IMPLEMENTED` à
`IMPLEMENTED_AND_TESTED` : `EF-AUTH-006` à `EF-AUTH-011`, `EF-AUTH-013`
et `EF-AUTH-015`.

Le sprint 3 en a fait passer six de plus : `EF-ACA-006` (matières),
`EF-ACA-007` (groupes temporaires), `EF-USER-001` (création de compte),
`EF-USER-007` (suivi et réémission des invitations), `EF-USER-008`
(délivrabilité) et `EF-TEA-001` (formateur externe).

Le sprint 4 en ajoute cinq : `EF-IMP-003` (Excel), `EF-IMP-004`
(classeur multifeuille), `EF-IMP-006` (correction de ligne),
`EF-USER-004` (opérations de masse) et `EF-USER-005` (doublons).

Le sprint 5 clôt deux exigences restées partielles depuis l'origine :
`EF-PLAN-003` (correction ligne à ligne) et `EF-PLAN-009` (conflit de
salle contre les séances déjà publiées).

Le sprint 8 en ajoute quatre et clôt trois partiels : `EF-ORG-003` (QR
fixe de salle), `EF-ATT-007` (apprenant provisoire), `EF-ATT-008`
(contrôle de plage réseau) et `EF-ATT-010` (émargement par QR de salle) ;
`EF-ATT-003` (quatre points nommés), `EF-ATT-004` (résultat journalier) et
`EF-ATT-005` (paliers de retard) passent de `PARTIAL` à
`IMPLEMENTED_AND_TESTED`.

Le sprint 7 ajoute `EF-ENR-004` (suivi à distance individuel) — la seule
exigence du sprint 7 qui n'était pas déjà livrée : l'émargement nominal
(`EF-ATT-001/002/006/009/012/015`, `EF-SES-002/003`) l'était depuis
l'origine.

Le sprint 6 en ajoute six : `EF-PLAN-006` (calendrier interactif),
`EF-PLAN-008` (retour à une version antérieure), `EF-PLAN-010`
(avertissement d'alternance), `EF-PLAN-011` (planning Excel),
`EF-SES-007` (report) et `EF-SES-008` (demande d'annulation).

`EF-SES-009` (séance multi-classes) était **déjà implémenté** et listé à
tort comme absent : `CourseSession` porte une collection `SessionClass`
et l'API accepte `classPublicIds`. Correction faite ici — le dépôt a
raison, le document avait tort.

### 1.1 Par domaine

| Domaine | Livré | Partiel | Absent |
|---|---:|---:|---:|
| Identité et accès (15) | 15 | 0 | 0 |
| Utilisateurs (9) | 9 | 0 | 0 |
| Référentiels et organisation (13) | 12 | 0 | 1 |
| Inscriptions et imports (10) | 9 | 0 | 1 |
| Corps enseignant (5) | 4 | 1 | 0 |
| Planning (13) | 11 | 0 | 2 |
| Séances (9) | 9 | 0 | 0 |
| Émargement et assiduité (16) | 14 | 0 | 2 |
| Justificatifs et réclamations (8) | 8 | 0 | 0 |
| Notifications et mobilité (9) | 7 | 2 | 0 |
| Restitution (10) | 9 | 0 | 1 |
| IA et objets connectés (10) | 0 | 0 | 10 |
| Intégrations (4) | 1 | 2 | 1 |
| Transverse (11) | 4 | 0 | 7 |

> **Contrôle de somme** : 15+9+12+9+4+11+9+14+8+7+9+0+1+4 = **112** livrés ;
> 0+0+0+0+1+0+0+0+0+2+0+0+2+0 = **5** partiels ;
> 0+0+1+1+0+2+0+2+0+0+1+10+1+7 = **25** absents. Total **142**, identique
> au tableau §1.

---

## 2. Ce qui est livré et testé

### 2.1 Identité et accès

- `EF-AUTH-001` connexion email + mot de passe → JWT HS256 stateless
  (signature, `exp`, `iss` vérifiés). Réponse **uniforme** pour email
  inconnu, mot de passe erroné et compte inactif.
- `EF-AUTH-002` multi-rôles, autorités `ROLE_*` dans le jeton,
  `@PreAuthorize` sur toute route non publique.
- `EF-AUTH-003` sélecteur de contexte de rôle **transmis au serveur** et
  vérifié contre les autorités du jeton
  (`403 DASHBOARD_CONTEXT_NOT_HELD`). Le cumul n'élargit jamais le jeton.
- `EF-AUTH-004` invitation et activation : jeton `SecureRandom`,
  empreinte SHA-256 seule stockée, durée de vie configurable, usage
  unique.
- `EF-USER-002/003/006` suspension, réactivation, archivage, attribution
  et retrait de rôle, avec gardes fines côté serveur (protection
  `SUPER_ADMIN`, auto-action interdite, dernier rôle actif protégé).
  Écran `/administration` en lecture et écriture.
- `EF-AUTH-005` **mot de passe oublié** : réponse strictement neutre —
  adresse connue, inconnue, suspendue ou archivée produisent la même
  réponse et le même corps ; jeton `SecureRandom` à usage unique, durée de
  vie 30 minutes, empreinte SHA-256 seule stockée, une demande active par
  compte garantie en base ; une nouvelle demande révoque la précédente ;
  un mot de passe refusé par la politique ne consomme pas le jeton ; un
  compte `PENDING_ACTIVATION` devient `ACTIVE` (contrôler l'adresse vaut
  activation) ; un compte suspendu ne peut pas contourner la décision
  administrative.
- `EF-AUTH-012` **limitation de débit** : compteurs à fenêtre fixe dans
  Redis sur la connexion (par identité **et** par origine réseau), la
  demande de réinitialisation et la consommation d'un jeton. Les clés sont
  des empreintes : ni adresse électronique ni adresse IP en clair
  (RG-094). Une connexion réussie remet le seau d'identité à zéro, jamais
  celui de l'origine. Réponse `429 RATE_LIMITED` + `Retry-After`, sans
  rien révéler sur l'existence du compte. Repli permissif si Redis est
  indisponible (`DEC-S2-001`).
- `EF-AUTH-014` **déconnexion et révocation** : liste de refus Redis
  indexée par `jti` pour une session, colonne
  `user_account.credentials_invalidated_at` pour la révocation globale
  (changement de mot de passe, `logout-all`). Validées à chaque requête
  par un `OAuth2TokenValidator` contribué par le module `identity`, sans
  créer de dépendance de `shared` vers `identity` (`DEC-S2-002`).
- **Politique de mot de passe** : longueur minimale 12, liste de mots de
  passe courants canonicalisée (casse et accents neutralisés), refus d'un
  mot de passe contenant l'adresse, borne haute anti-déni de service.
  Aucune exigence de composition, aucune expiration périodique.
- `EF-AUTH-006`/`007` **passkeys WebAuthn** : options d'enregistrement
  avec défi aléatoire à usage unique (Redis), vérification d'attestation
  et d'assertion par `webauthn4j`, compteur de signature contrôlé et mis
  à jour, connexion sans mot de passe, liste et révocation individuelle
  (RG-008). Le serveur ne reçoit **qu'une clé publique et une signature** :
  aucune structure de ce module ne peut porter de donnée biométrique
  (`AC-020`). Les options d'assertion ne renvoient aucune liste de
  justificatifs — elle révélerait l'existence d'un compte.
- `EF-AUTH-008` **second facteur TOTP** (RFC 6238, HMAC-SHA1, 6 chiffres,
  pas de 30 s), vérifié contre les vecteurs officiels de la RFC. Secret
  partagé **chiffré au repos** en AES-256-GCM, jamais renvoyé après
  l'écran d'enrôlement. Anti-rejeu : le dernier pas consommé est mémorisé,
  un même code ne sert pas deux fois. Limitation de débit dédiée.
- `EF-AUTH-009` **dix codes de récupération** à usage unique, empreinte
  SHA-256 seule stockée, affichés une seule fois, régénérables — la
  régénération invalide toute la série précédente.
- `EF-AUTH-010` **authentification adaptative** : un appareil inconnu
  déclenche le second facteur ; un appareil reconnu allège la reconnexion
  d'un compte ordinaire et n'accorde **aucune dispense** à un compte
  privilégié (`DEC-S2-006`).
- `EF-AUTH-011` **anti-robot** : port `CaptchaVerifier`, adaptateur
  Cloudflare Turnstile et adaptateur local. Vérification **côté serveur**,
  systématique sur la demande de réinitialisation et l'activation,
  déclenchée après trois échecs sur la connexion (`AC-022`). Sans clé
  secrète configurée, le produit **déclare** qu'aucun contrôle n'est actif
  (`GET /api/v1/auth/captcha`) — il n'en simule pas un. Politique de repli
  `DEC-S2-004`.
- `EF-AUTH-013` **appareils de confiance** : empreinte seule en base (ni
  user-agent, ni adresse IP), confiance bornée dans le temps, liste et
  révocation par le propriétaire, `404` sur l'appareil d'autrui. Mémorisé
  **uniquement** après une authentification complète.
- `EF-AUTH-015` **réauthentification avant action critique** : le claim
  `amr` du jeton porte les moyens réellement employés ; un changement de
  rôle exige un jeton obtenu avec un facteur fort, jamais un mot de passe
  seul.
- **Politique de second facteur** (RG-007, `AC-021`) : un compte
  `SUPER_ADMIN` ou `ADMIN` n'obtient **jamais** de jeton contre son seul
  mot de passe. La connexion renvoie un défi — `VERIFY` s'il a un facteur,
  `ENROLL` sinon, l'enrôlement se faisant dans la foulée (`DEC-S2-005`).
- `EF-AUD-001` piste d'audit `audit_event` alimentée par tous les flux
  métier, **sans donnée personnelle, sans jeton, sans adresse IP** ;
  `PASSWORD_CHANGED`, `SESSIONS_REVOKED`, `MFA_*`, `PASSKEY_*` et
  `TRUSTED_DEVICE_*` publiés **après commit** (`DEC-S2-003`).

### 2.2 Référentiels et organisation

- `EF-ORG-001/002` sites, bâtiments, salles, plages réseau CIDR IPv4 et
  IPv6 validées sans résolution DNS. CRUD, archivage, restauration.
  Écrans Angular livrés.
- `EF-ACA-001..005, 008` années, formations, niveaux, promotions,
  classes, affectations pédagogiques, avec contrôle de périmètre décidé
  côté serveur (`AcademicScopeGuard`).
- `EF-ACA-009` alternance : quatre types de rythme, configuration
  validée et canonicalisée, affectation historisée, exceptions
  individuelles, résolution `SCHOOL` / `COMPANY` / `UNKNOWN`. Écran en
  lecture et écriture.

### 2.3 Population, matières et groupes

- `EF-ACA-006` **matières** : CRUD, archivage et restauration,
  rattachement à une ou plusieurs formations contrôlé formation par
  formation (`AcademicScopeGuard`), code immuable après création — il sert
  de référence dans les fichiers de planning. **Aucun champ formateur**,
  ni en base, ni dans l'API, ni dans l'écran : le cahier réserve
  l'affectation à la séance, à une période ou à une association
  classe–matière–période (docs/02 §6.4). Écran `/subjects`, lecture
  ouverte aux formateurs.
- `EF-ACA-007` **groupes temporaires** : un groupe rassemble des
  apprenants issus de **classes différentes** pour une période, sans
  jamais toucher à leur classe principale (RG-022). Membres rattachés à
  l'**inscription** et non au profil, retrait logique, périmètre
  pédagogique contrôlé côté serveur. Hébergé dans `enrollment` et non
  `academic` : l'inverse créerait un cycle entre modules (`DEC-S3-001`).
- `EF-USER-001` **création de compte** : `POST /api/v1/users` crée un
  compte `PENDING_ACTIVATION` et émet son invitation dans la foulée.
  **Aucun champ de mot de passe** — la personne choisit le sien via son
  lien (docs/02 §11.2). Adresse déjà utilisée → `409`, jamais de doublon
  (RG-001). Formulaire dans `/administration`.
- `EF-TEA-001` **formateur externe** : créé par la même route, avec une
  adresse de n'importe quel domaine. Le domaine n'est jamais un critère
  de confiance (docs/02 §12.1).
- `EF-USER-007` **suivi et réémission des invitations** :
  `GET /api/v1/account-invitations` (statut, expiration déduite de
  `expires_at`) et `POST /{id}/resend`, qui **révoque le jeton
  précédent** — sans quoi une adresse corrigée laisserait un lien valide
  dans la mauvaise boîte. Écran `/invitations`.
- `EF-USER-004` **opérations de masse** : suspension, réactivation,
  archivage et réémission groupés. **Sans `confirm: true`, rien n'est
  écrit** (RG-034) : l'appel produit le même calcul — éligibles, ignorés,
  refusés — et le rend, sans effet. L'exécution est isolée **par
  compte** et non atomique sur le lot : un compte protégé est reporté
  sans priver les autres de l'opération (`DEC-S4-002`).
- `EF-USER-005` **doublons** : comptes rapprochés par nom complet
  normalisé (accents et casse neutralisés) ou par numéro de téléphone.
  Le service **signale**, il ne fusionne ni ne supprime : la suppression
  d'un doublon reste une action humaine, exceptionnelle et doublement
  confirmée (docs/02 §9.5).
- `EF-USER-008` **délivrabilité** : table `email_delivery` tenant
  **deux axes distincts** — ce que le produit a fait
  (`QUEUED` / `SENT_TO_PROVIDER` / `PROCESSING_FAILED`) et ce que le
  fournisseur a constaté (`UNKNOWN` par défaut). « Remis au serveur de
  messagerie » n'est jamais présenté comme « délivré » (docs/02 §11.3).
  L'adresse n'est stockée ni exposée en clair : empreinte + forme masquée
  (`c…e@e…c.test`).

### 2.4 Imports

- `EF-IMP-003` **import Excel `.xlsx`** : le format binaire ancien
  (`.xls`, OLE2) est **refusé**, le cahier ne demandant que `.xlsx` et
  OLE2 ouvrant la porte aux macros. Le type réel est dérivé du contenu
  (magie ZIP) : un CSV renommé en `.xlsx` — ou l'inverse — est rejeté,
  jamais deviné. Le classeur converge vers la **même structure** que le
  CSV, afin que la validation métier ne diverge pas par format. Les
  cellules sont converties de façon prévisible : une date reste une date
  ISO, un numéro étudiant saisi comme nombre ne devient pas
  `20260001.0`, et une formule est lue par son résultat mis en cache —
  jamais recalculée.
- `EF-IMP-004` **classeur multifeuille** : toutes les feuilles non
  masquées sont lues ; une feuille dont l'en-tête diffère est
  **signalée et écartée**, jamais lue avec le mauvais mapping. Chaque
  ligne conserve sa feuille d'origine, de sorte qu'une anomalie est
  située « fichier, feuille, ligne, colonne » (docs/02 §10.7). Critère
  IMP-STU-05 vérifié : un classeur de trois feuilles rattache trois
  classes.
- `EF-IMP-006` **correction de ligne avant confirmation** : corriger une
  ligne en anomalie sans recommencer l'import. La correction rejoue
  **exactement** la validation de la simulation, recalcule la synthèse du
  travail — corriger la dernière ligne fautive le rend confirmable — et
  est tracée en append-only (qui, quand, valeur avant, valeur après). La
  liste des champs corrigeables est fermée côté serveur.

- `EF-ENR-001..003` profils apprenants, inscriptions, changement de
  classe conservant l'historique. Une seule inscription active par
  apprenant et par année, garantie par contrainte SQL et testée en
  concurrence.
- `EF-IMP-001` simulation d'import CSV **sans aucune écriture métier** :
  extension contrôlée, rejet ZIP / OLE2 / PDF / octet nul, UTF-8 strict,
  RFC 4180, séparateur auto-détecté, fichier **jamais écrit sur
  disque**, plafond `2 MiB` → `413`.
- `EF-IMP-002` confirmation transactionnelle unique : verrou
  `SELECT … FOR UPDATE`, revalidation complète, idempotence, rollback
  total sur toute exception, courriel émis **uniquement après commit**,
  numéro `ESIC-{année}-{NNNNN}` alloué atomiquement.

### 2.5 Planning

- `EF-PLAN-001/002` import CSV borné, jamais écrit sur disque
  (SHA-256 seul), simulation produisant lignes, anomalies et synthèse
  **sans créer aucune séance**.
- `EF-PLAN-004/005/007` publication **atomique** : verrou `FOR UPDATE`,
  revalidation, version N/N+1, ancienne version `SUPERSEDED`, séances
  créées ou réutilisées via le **port public**
  `coursesession.PlanningSessionWriter`. Publication concurrente
  strictement idempotente. Identité de créneau stable et déterministe.
- `EF-PLAN-003` **correction ligne à ligne** dans l'écran de revue : la
  correction rejoue l'analyse de **tout** le travail — les conflits de
  planning sont croisés, corriger une ligne peut en lever ou en créer un
  ailleurs (`DEC-S5-002`). Corriger la dernière ligne fautive rend le
  travail publiable sans réimport.
- `EF-PLAN-009` **conflit de salle contre les séances déjà publiées** :
  la séance conserve désormais son `room_code` (migration `V21`). Le
  contrôle porte sur **tout l'établissement** — une salle n'appartient
  pas à une classe (`DEC-S5-001`). Deux créneaux sans salle ne sont
  jamais en conflit ; le même créneau republié reste exclu.
- `EF-PLAN-006` **calendrier interactif** : ajout, modification,
  déplacement, suppression, duplication d'une semaine, répétition d'un
  créneau, brouillon, publication. Un créneau saisi devient une ligne d'un
  travail d'import ordinaire et chaque mutation rejoue l'analyse du lot
  entier ; la publication reste `POST /planning-imports/{id}/publish`,
  avec les mêmes conflits et le même versionnement (`DEC-S6-001`). Le
  publié et le brouillon sont présentés séparément.
- `EF-PLAN-008` **retour à une version antérieure** : crée une version
  **N+1** dont le contenu est celui de la version choisie (AC-009).
  L'historique n'est jamais effacé ; `slot_public_id` étant conservé, les
  séances existantes sont réutilisées et non recréées (RG-047). Refusé si
  un formateur n'est plus éligible ou si la version est vide
  (`DEC-S6-002`).
- `EF-PLAN-010` **avertissement d'alternance** non bloquant : un créneau
  tombant sur une période résolue `COMPANY` produit
  `PLAN_ALTERNATION_COMPANY_PERIOD` sans empêcher la publication. Sans
  rythme affecté (axe `UNKNOWN`), le produit se tait plutôt que de
  présumer.
- `EF-PLAN-011` **import Excel `.xlsx`** : type réel dérivé du contenu
  (un CSV renommé `.xlsx` est refusé), dates et heures converties de
  façon prévisible, feuilles supplémentaires signalées et non lues en
  silence — un planning porte sur une seule classe.
- Écrans `/planning/import`, `/planning/import/:jobId`,
  `/planning/calendar`, `/planning/versions`.

### 2.6 Séances et remplacements

- `EF-SES-001..006` séance issue d'un planning publié ou créée
  manuellement avec motif ; cycle strict `PLANNED → OPEN → CLOSED` sans
  réouverture ; `CANCELLED` avec motif, la séance restant consultable en
  historique ; séance supersédée inactive partout.
- `EF-SES-007` **report** d'une séance annulée : crée une séance de
  remplacement **liée** à l'originale, qui reste `CANCELLED` et
  consultable en portant `postponedToPublicId`. Une séance non annulée
  n'est pas reportable ; un second report est refusé
  (`SESSION_ALREADY_POSTPONED`).
- `EF-SES-008` **demande d'annulation** par le formateur, décidée par le
  responsable : une acceptation annule la séance dans la foulée, un refus
  la laisse `PLANNED`. Une seule demande en attente par séance ; le
  demandeur peut la retirer, l'historique la conservant en `WITHDRAWN`.
  Le formateur reçoit `403` s'il tente de décider — « il demande, il ne
  décide pas » (RG-024).
- `EF-SES-009` séance rattachée à **plusieurs classes** (RG-023) : porté
  par `SessionClass`, exposé par `classPublicIds`.
- `EF-TEA-003..005` remplacements datés : formateur principal jamais
  écrasé, une seule substitution active applicable, droits accordés au
  remplaçant **uniquement** pendant sa période, `TEACHER` exclu de la
  création.

### 2.7 Émargement

- `EF-ATT-001/009` jeton d'émargement **opaque** et code court dans
  Redis : durée de vie, rotation, purge à la fermeture **après commit**.
  Le QR n'encode que le jeton opaque, aucune donnée personnelle.
- `EF-ATT-002` validation par un `STUDENT` inscrit, anti-double présence
  par contrainte SQL ; concurrence → `200` / `409`, jamais `500`.
- `EF-ATT-006/012` présence manuelle, correction, annulation logique,
  motif obligatoire, historique append-only, verrou optimiste → `409`.
- `EF-ATT-015` suivi des présences en direct.
- `EF-ENR-004` **suivi à distance individuel** : autorisation datée,
  motivée, révocable et auditée ; sur une séance présentielle, le canal
  distant sans autorisation active est refusé
  (`403 ATT_REMOTE_NOT_AUTHORIZED`). La portée « séance / période / année »
  est un **intervalle de dates** ; une autorisation générale est réservée
  au périmètre global (`DEC-S7-002`). Une séance porte désormais sa
  modalité (`ON_SITE` / `REMOTE` / `HYBRID`) et son lien distant ; les
  canaux `REMOTE_QR` et `REMOTE_CODE` sont enregistrés distinctement.
  **Le drapeau `remote` est une déclaration, pas une preuve de
  localisation** (`DEC-S7-001`) : le contrôle de présence sur site reste
  le QR fixe de salle et la plage réseau (sprint 8). Écran : section
  « suivi à distance » de la fiche apprenant.
- `EF-ATT-003` **quatre points de contrôle nommés** (`MORNING_ARRIVAL`,
  `MORNING_BREAK_RETURN`, `AFTERNOON_ARRIVAL`, `AFTERNOON_BREAK_RETURN`),
  uniques par séance. V10 les disait « réalisables via des points
  `CUSTOM` libellés » : vrai fonctionnellement, faux structurellement —
  un calcul journalier ne peut pas se fonder sur un libellé libre.
- `EF-ATT-004` **résultat journalier** (`GET /attendance/reports/daily`) :
  `FULL_DAY`, `MORNING`, `AFTERNOON`, `PARTIAL`, `TO_CONFIRM`, `ABSENT`,
  `EXCUSED`, plus `COMPANY` (RG-028 — jamais une absence) et
  `NOT_EXPECTED`, absents de la table du cahier qui suppose une journée
  attendue. Un **retour de pause sans l'arrivée** qui le précède est une
  incohérence → `TO_CONFIRM` ; l'inverse est incomplet → `PARTIAL`.
- `EF-ATT-005` **paliers de retard** configurables : `PRESENT` jusqu'à 15
  min, `LATE` jusqu'à 30, au-delà `LATE` **plus** validation humaine
  requise. Le troisième palier ne refuse pas l'émargement : refuser
  produirait une absence là où il y a un retard constaté.
- `EF-ORG-003` **QR fixe de salle** : jeton `SecureRandom` généré par le
  serveur, unique, daté, renouvelable et révocable. Une référence saisie
  à la main serait devinable, donc sans valeur (`DEC-S8-001`). `V26`
  efface les valeurs libres héritées de V4.
- `EF-ATT-010` **émargement par QR de salle** : le corps ne porte que le
  jeton ; le serveur détermine salle, séance imminente, inscription et
  fenêtre. Refusé après le début de la séance (RG-051), sans séance
  correspondante, et sur jeton inconnu (`404`).
- `EF-ATT-008` **contrôle de plage réseau** : comparaison CIDR IPv4/IPv6
  sur les octets, sans résolution DNS, **avant** toute autre décision.
  Refus par défaut — un site sans plage déclarée n'autorise rien. L'adresse
  sert à décider puis disparaît : ni persistée, ni auditée, ni renvoyée
  (RG-094, `DEC-S8-002`).
- `EF-ATT-007` **apprenant provisoire** : signalement par le formateur
  (`UNREGISTERED_GUEST` / `PENDING_REGISTRATION`), puis régularisation
  motivée — rattachement à une inscription réelle, ou mise à l'écart.
  L'entrée n'entre dans **aucun** calcul d'assiduité tant qu'elle n'est
  pas régularisée, et le rattachement ne fabrique pas de présence
  (`DEC-S8-003`).
- Redis indisponible → `503 ATT_TOKEN_BACKEND_UNAVAILABLE` : **aucune
  validation dégradée**.

### 2.8 Justificatifs et restitution

- `EF-JUS-001/003/004` dépôt, modification tant que `PENDING`, examen,
  décision motivée, `ACCEPTED` → `ABSENT` devient `EXCUSED_ABSENCE` ;
  `TEACHER` exclu de l'examen.
- `EF-JUS-002` pièce jointe : dépôt multipart propriétaire, validation
  extension + type déclaré + **magic bytes** (type re-dérivé du contenu,
  rejet ZIP/OLE2), stockage **hors base et hors webroot**, séquence
  base ↔ fichier avec compensation, réconciliation planifiée des lignes
  `PENDING_STORAGE`, téléchargement forcé en pièce jointe avec
  `nosniff`, accès propriétaire et examinateur périmétré uniquement.
  **Analyse antivirus** : port `AttachmentMalwareScanner`, adaptateur
  ClamAV (`INSTREAM`) activable par configuration, adaptateur inactif par
  défaut qui **déclare** n'analyser rien. Verdict persisté (V30) parmi
  `NOT_SCANNED` / `CLEAN` / `INFECTED` / `UNAVAILABLE` et exposé par
  l'API : une pièce non analysée n'est **jamais** présentée comme saine.
  L'analyse a lieu avant toute écriture — un contenu reconnu malveillant
  ne touche pas le disque (`422`). Quarantaine gouvernée par
  `app.attendance.antivirus.required` (`409` sans verdict exploitable).
  **Balayage des orphelins** : passage planifié et borné qui supprime les
  contenus qu'aucune ligne active ne référence plus (`DEC-S9-005`).
- `EF-CLAIM-001..004` **réclamations** : une réclamation s'adresse à un
  **guichet** (formateur, responsable pédagogique, administration
  scolaire), jamais à une personne — c'est ce qui rend le transfert
  possible. Fil de messages append-only avec le rôle figé à l'écriture,
  décisions tenues dans une table **distincte** de la conversation
  (RG-088), transfert et refus motivés, réouverture d'un dossier clos.
  Une réclamation d'autrui répond `404`, jamais `403`. Une réclamation
  d'un apprenant sans classe active adressée à un guichet à périmètre est
  refusée à la création plutôt qu'acceptée puis invisible de tous
  (`DEC-S9-006`).
- `EF-ATT-013` **départ anticipé** : l'apprenant signale, le formateur
  accepte, refuse, ou transmet au responsable avec un avis facultatif.
  Une fois transmis, le formateur ne reprend pas la main (`403`).
  L'effet — `PARTIAL` / `EXCUSED_PARTIAL` / `TO_CONFIRM` — est **dérivé**
  du statut, jamais stocké, et ne module que les journées `PARTIAL` :
  une journée complète ne redevient pas incomplète, une absence totale
  n'est pas excusée (`DEC-S9-001`, `DEC-S9-002`).
- `EF-ATT-014` **journal de transparence** : `GET /me/attendance/transparency`
  compose à la lecture l'émargement, l'historique append-only des
  corrections, le cycle des justificatifs et celui des départs anticipés.
  Rien n'est persisté. L'apprenant est résolu depuis le **seul JWT** ; les
  acteurs y sont désignés par leur **fonction**, jamais par leur nom
  (`DEC-S9-003`, `DEC-S9-004`).
- `AC-018` **l'auteur d'une correction est exposé** — il ne l'était pas :
  la colonne existait depuis V10 sans jamais atteindre l'API. Nom et
  fonction pour le personnel, fonction seule pour l'apprenant.
- `EF-REP-001..003` rapports séance / classe / apprenant / synthèse en
  JSON paginé avec tri serveur borné, export CSV UTF-8 + BOM, séparateur
  `;`, neutralisation d'injection de formule.
- Espace apprenant `/me/attendance*` : absences **dérivées** d'un point
  de contrôle fermé, jamais persistées ; aucun accès croisé (`AC-017`).

### 2.9 Notifications et effets de bord

- `EF-AUD-003` **outbox transactionnelle** : le module `outbox` (16ᵉ
  module) porte la table `outbox_message` (V31). Un module métier écrit
  une **intention** dans sa propre transaction ; elle commite avec
  l'action, ou **disparaît avec son annulation** (`AC-027`). Un diffuseur
  la traite ensuite — drain immédiat dans l'`afterCompletion`, puis
  reprise planifiée pour ce que l'immédiat a manqué —, avec attente
  croissante plafonnée puis passage en **file d'échec** (`AC-028`).
  Les onze écouteurs d'audit n'écrivent plus eux-mêmes : ils enregistrent
  une intention, et `audit_event.outbox_key` (V32, unique) rend le
  gestionnaire rejouable sans faire compter l'audit en double.
  Seul `onLoginFailed` conserve une transaction dédiée, et c'est
  délibéré : la transaction de connexion est *toujours* annulée sur
  échec ; la rejoindre effacerait la trace de la tentative — l'essentiel
  de ce qu'un responsable sécurité cherche (`DEC-S10-001`).
- `EF-OPS-005` **rejeu manuel** : `GET /api/v1/outbox/messages`,
  `/summary` et `POST /{id}/replay`, réservés à `ADMIN` / `SUPER_ADMIN` —
  rejouer peut envoyer un courriel, ce n'est pas une lecture. Le rejeu
  est refusé (`409`) hors file d'échec, et le **contenu du message n'est
  jamais renvoyé** : l'exploitant voit ce qui a échoué et pourquoi.
  Écran `/exploitation/effets-de-bord`.
- `EF-NOTIF-002`/`003` **audience complète** : une annulation de séance
  prévient désormais le formateur, ses remplaçants, **les apprenants
  attendus** et **le responsable pédagogique du périmètre**. L'audience
  est *décrite* par l'écouteur et *résolue après commit*, sur l'état
  réellement établi (`DEC-S10-003`). Deux ports naissent de là :
  `EnrollmentDirectory.findActiveStudentUserPublicIds` et
  `PedagogicalResponsibilityDirectory` (résolution inverse du périmètre).
  Choix assumé : un **remplacement** ne réveille pas la classe — prévenir
  de chaque changement de formateur noierait ce qui compte.
- `EF-NOTIF-004` **courriel** : chaque notification met en file son
  propre envoi, avec ses tentatives et sa place dans la file d'échec. Le
  journal de délivrabilité distingue quatre états sans jamais les
  confondre — *accepté par l'adaptateur*, *remis au serveur de messagerie*
  (`SENT_TO_PROVIDER`), *en échec* (`PROCESSING_FAILED`), *délivré*
  (**jamais affirmé** : `UNKNOWN` par défaut). L'adresse n'est ni
  journalisée ni stockée en clair.
- `EF-NOTIF-006` **préférences** : réglage par catégorie et par canal
  (V33). Seules les **exceptions** sont stockées — une catégorie ajoutée
  plus tard est active pour tous, sans migration de données. Le centre de
  notifications (`IN_APP`) et la catégorie `SECURITY` ne se désactivent
  pas, refusés par le service **et** par contrainte SQL. L'écran affiche
  ces réglages cochés et verrouillés, plutôt que de laisser cliquer sur
  un interrupteur que le serveur refuserait. Écran
  `/notifications/preferences`.
- **Réclamations notifiées** (dette T-12) : dépôt, message, transfert,
  décision et réouverture préviennent les participants du fil **et le
  guichet courant** — résolu au moment de l'événement, ce qui rend le
  transfert visible des deux côtés. Ni sujet ni extrait de message n'entre
  dans la notification (§23.3).
- **Isolation par destinataire préservée** : chaque destinataire est
  écrit dans sa propre transaction, avec ses intentions de courriel et de
  poussée. Un échec n'empêche pas les autres d'être servis ; le message
  repasse malgré tout en reprise, et l'idempotence évite de notifier deux
  fois (`DEC-S10-004`).

### 2.10 Mobilité (PWA)

- `EF-PWA-001` **application installable** : manifeste, icônes (dont une
  *maskable* avec sa zone de sécurité réelle), service worker écrit à la
  main plutôt que `@angular/service-worker` — un seul service worker peut
  être enregistré par portée, et celui d'Angular ne sait ni rejouer une
  action métier ni recevoir les poussées avec le contrôle voulu
  (`DEC-S10-006`). Bouton « Installer » dans l'en-tête.
- `EF-PWA-003` **file d'actions différées** : un émargement fait hors
  ligne est mis en file et rejoué au retour du réseau. L'écran affiche
  **« en attente de confirmation »** — un état distinct du succès
  (`AC-031`, RG-063). Au rejeu, un `409` vaut succès (la présence est déjà
  enregistrée), une erreur `4xx` est une décision définitive du serveur
  **affichée avec son motif**, une panne réseau laisse l'action en
  attente. La file vit **en mémoire** : le code court est un jeton
  (RG-093) et il expire en trente secondes (`DEC-S10-005`).
- **Ce que le cache conserve, et ce qu'il ne conserve jamais** : la
  coquille applicative et une **liste fermée** de réponses `GET` d'API
  (planning, assiduité, notifications, tableau de bord). Les routes
  d'authentification et les jetons d'émargement en sont exclus, et le
  cache de données est vidé à la déconnexion — un appareil partagé ne
  garde pas les données de la personne précédente.

### 2.11 Restitution, documents et intégrations (sprint 11)

- `EF-REP-004`/`005` **exports Excel et PDF** : un paramètre `format`
  (`csv` | `xlsx` | `pdf`) sur les routes d'export existantes, résolu
  contre une **liste fermée** — un format inconnu produit un `400`
  explicite, jamais un repli silencieux sur le CSV. Les trois formats
  partagent la **même description de rapport** (module `document`) : une
  colonne ajoutée à l'un et oubliée à l'autre se lirait comme une
  différence de chiffres. Conséquence assumée : les en-têtes de colonnes
  du CSV sont désormais les **libellés français** partagés avec le PDF
  (voir §8, changement de comportement).
- **Injection de formule neutralisée au classeur comme au CSV**
  (`AC-032`) : le vecteur est le tableur qui ouvre le fichier, pas
  l'extension. Toutes les cellules `.xlsx` sont écrites en **texte**,
  jamais en formule, et vérifiées comme telles.
- Le **CSV n'a aucun préambule** : la ligne 1 est l'en-tête. Titre,
  synthèse et mentions sont portés par le classeur et le PDF, qui sont
  faits pour être lus par une personne — un CSV est un format
  d'interéchange, et tout ce qui précède l'en-tête casse les analyseurs.
- `EF-REP-005` **identité visuelle** : bandeau, établissement, nom du
  rapport, période, date de génération, auteur, identifiant et mention de
  document électronique, **sur chaque page**. Le dépôt ne contient
  **aucun fichier de logo** : l'en-tête est une signature typographique,
  pas une image — dessiner un logo inventé serait pire que de ne pas en
  mettre.
- `EF-REP-006` **attestation d'assiduité** (`AC-033`, `AC-019`) :
  identifiant `ESIC-ATT-<année>-<10 caractères aléatoires>` inscrit au
  registre `report_document` (V34), émetteur et auteur sur le document,
  mesure exprimée **en demi-journées et en journées équivalentes** —
  l'unité du cahier, pas des heures de connexion. Le **PDF n'est pas
  conservé** : il est reproductible depuis les données d'assiduité, et le
  garder dupliquerait des données personnelles dans un second
  emplacement à gouverner. Est conservée une **empreinte SHA-256**, qui
  suffit à dire si un PDF présenté est bien celui qui a été émis. La
  route de vérification ne renvoie **aucune donnée d'assiduité** :
  quiconque détient le papier ne doit pas pouvoir en apprendre davantage
  que ce que le papier porte.
- `EF-REP-007` **cartes complètes** : le responsable pédagogique voit
  taux d'assiduité, retards, absences non justifiées, taux par classe,
  justificatifs en attente **de son périmètre**, réclamations ouvertes de
  son guichet et comptes non activés de ses classes ; l'administration
  voit taux global, **comparaison des formations**, volume et délai
  médian de traitement des justificatifs, invitations expirées, exports
  récents et dernières opérations auditées. Le délai médian vaut
  **`null` et non `0`** quand rien n'a été traité : « aucun dossier
  traité » et « traité en zéro heure » ne doivent pas s'écrire pareil.
  Le taux d'un groupe est recalculé depuis les **totaux**, jamais comme
  moyenne des taux — une classe de six pèserait autant qu'une classe de
  trente.
- **Ce que la carte du responsable ne montre pas, et pourquoi** : une
  « séance sans formateur » n'existe pas en base
  (`course_session.teacher_user_id` est `NOT NULL` depuis V9). Le cas est
  traité à l'import de planning, où il produit un avertissement
  (`EF-PLAN-009`). Une tuile afficherait éternellement zéro et laisserait
  croire à un contrôle qui n'aurait pas lieu ; une mention le dit.
- `EF-REP-008` **tableau équivalent** : chaque histogramme est doublé
  d'un `<table>` avec `caption`, et **chaque barre porte sa valeur en
  toutes lettres**. Graphique et table lisent la même liste : ils ne
  peuvent pas diverger. La couleur n'est jamais seule porteuse de
  l'information (docs/02 §22.6, §32.5).
- `EF-REP-010` **invitations non activées** : comptes en attente,
  dernière relance, expiration, jours d'attente. L'adresse n'apparaît que
  **masquée** (`c…e@e…c.test`) — ce rapport sert à relancer, pas à
  produire un annuaire exportable qui circulerait ensuite par courriel.
- `EF-USER-009` **recherche globale** : apprenants, formateurs, classes,
  formations, salles et séances. **Chaque module cherche dans sa propre
  donnée** (lui seul sait ce qu'est un code de classe) ; le module
  `search` ne fait qu'assembler. Le périmètre est relu du contexte de
  sécurité, jamais reçu en paramètre. Deux protections : les jokers
  `%` et `_` saisis sont **échappés** (sans quoi `%` ramènerait tout le
  référentiel), et un fragment de moins de deux caractères est refusé —
  c'est une énumération, pas une recherche. **L'adresse électronique
  n'est jamais un critère** : la chercher permettrait de confirmer
  l'existence d'un compte à partir d'une adresse devinée.
- `EF-AUD-002` **consultation et export de l'audit** : filtres fermés
  (période, action, catégorie, type de ressource, résultat, acteur,
  corrélation), pagination bornée, export CSV / Excel / PDF. **Aucune
  route d'écriture ni de suppression n'existe** — « l'audit ne peut être
  modifié ni effacé » (§23.4). Les colonnes JSON
  (`old_values_json`, `new_values_json`, `metadata_json`) **ne sortent
  pas de la base** : leur contenu dépend du module émetteur et n'est pas
  gouverné pour l'affichage. Réservé à `ADMIN` / `SUPER_ADMIN` ;
  l'apprenant dispose de son journal de transparence (`EF-ATT-014`), qui
  est la bonne granularité pour lui. Un export tronqué **le dit**.
- `EF-INT-001` **flux iCalendar** (`AC-034`) : abonnement par personne,
  signé, révocable. La route du flux est **volontairement non
  authentifiée** — Outlook, Google et Apple ne savent pas porter un jeton
  d'accès, ils rappellent une URL. Le secret est donc le jeton porté par
  l'URL : 32 octets aléatoires, **jamais stockés en clair** (empreinte
  SHA-256 seule), comparés en temps constant. Un jeton faux et une clé
  inconnue produisent la **même** réponse. La révocation est une **date**
  et non une suppression : un agenda continuera d'appeler l'URL pendant
  des semaines, et il faut pouvoir répondre `410 Gone` plutôt que
  « inconnu ». Le flux ne contient que le planning de la personne, et
  inclut les séances **annulées** — les retirer laisserait le cours dans
  son agenda.
- Le jeton d'abonnement est renvoyé **une seule fois**, à la création, et
  ne peut pas être réaffiché ; l'écran le dit avant de le montrer.

---

### 2.12 Transverse

- En-têtes durcis : `nosniff`, `X-Frame-Options: DENY`, anti-cache,
  CSP, `Referrer-Policy: no-referrer`.
- CORS restrictif piloté par `APP_ALLOWED_ORIGINS`, jamais `*`,
  `allowCredentials=false`.
- Erreur d'appel client → `400 VALIDATION_ERROR`, jamais `500`.
- Front Angular 21 zoneless / standalone / Material ; jeton et contexte
  de rôle **en mémoire seule**, aucun `localStorage` ni
  `sessionStorage`, asserté par test.
- Lien d'évitement présent sur les pages applicatives **et** publiques.
- Matrices `*SecurityTests` (`401` / `403` / `200`) par module ;
  concurrence testée sur inscriptions, affectations, émargement,
  corrections, confirmations d'import, publications de planning.

---

## 3. Partiels

| Exigence | Ce qui existe | Ce qui manque |
|---|---|---|
| `EF-TEA-002` | API d'affectation pédagogique livrée | aucun écran d'affectation classe–matière–période |
| `EF-NOTIF-005` | abonnement par appareil, révocation, préférences, chiffrement **RFC 8291 vérifié contre le vecteur de test officiel de la RFC**, signature VAPID RFC 8292, adaptateur HTTP écrit | **aucun service de poussée réel sollicité** : sans clés VAPID, l'adaptateur inactif répond et l'API déclare `providerActive: false` |
| `EF-PWA-002` | service worker servant planning, assiduité et notifications depuis son cache quand le réseau tombe, **application ouverte** ; bandeau « hors ligne » | pas de consultation après un **démarrage à froid** sans réseau : le jeton ne vit qu'en mémoire (RG-093), il n'y a pas de session à rétablir |
| `EF-INT-002` | port `MeetingProvider`, adaptateur Microsoft Graph (flux « identifiants client », `POST /users/{id}/onlineMeetings`), adaptateur inactif, route d'état déclarée | **aucun locataire Microsoft réel sollicité** (T-16) : sans `tenantId`/`clientId`/`clientSecret`, l'adaptateur inactif répond et l'API déclare `meetingActive: false` |
| `EF-INT-003` | port `ExternalCalendarWriter`, adaptateur Graph (`/users/{id}/events`), adaptateur inactif | idem T-16 ; priorité `COULD` |

> La ligne `EF-REP-007` a disparu de ce tableau : les cartes du
> responsable pédagogique et de l'administration sont complètes (§2.11),
> et le coût SQL par séance est levé et **mesuré** (T-03, §6.3).
>
> La ligne « Audit transactionnel » avait disparu au sprint 10 : les onze
> écouteurs d'audit passent par l'outbox (§2.9). Ce n'était pas une
> exigence mais une dette — T-02, levée.

---

## 4. Non implémenté

Aucune ligne de code. Ce sont les sprints à venir — voir
`docs/06-roadmap-six-mois.md`.

| Bloc | Exigences | Nombre | Sprint |
|---|---|---:|---|
| Détection des conflits et incohérences de salle hors planning | `EF-ORG-004` | 1 | 6 |
| Mapping d'import assisté par l'IA | `EF-IMP-005` | 1 | 12 |
| Planning PDF texte et mapping assisté par l'IA | `EF-PLAN-012`, `013` | 2 | 12 |
| Confirmation locale d'un émargement par WebAuthn ; borne connectée | `EF-ATT-011`, `016` | 2 | 8, 12 |
| Rapport des anomalies d'émargement | `EF-REP-009` | 1 | 12 |
| Service d'IA complet | `EF-AI-001..005` | 5 | 12 |
| Objets connectés | `EF-IOT-001..005` | 5 | 12 |
| Fournisseur de courriel réel | `EF-INT-004` | 1 | 13 |
| RGPD et exploitation | `EF-RGPD-001..003`, `EF-OPS-001..004` | 7 | 13 |

Total : **25**, soit exactement le compte de §1. Sortent de ce tableau au
sprint 11 : `EF-USER-009`, `EF-AUD-002`, `EF-REP-004`, `005`, `006`,
`008`, `010` et `EF-INT-001` (livrés, §2.11) ; `EF-INT-002` et
`EF-INT-003` passent en `PARTIAL` (§3) et ne sont donc plus comptés ici.

**Vérifications de terrain** (5 septembre 2026, après sprint 11) : aucune
occurrence de `MQTT` dans `backend/src/main` ou `frontend/src`. Aucun
module `ai` ni `iot`. Les modules `claim`, `outbox`, `document`,
`integration` et `search` **existent**. Une bibliothèque PDF est
désormais présente — Apache PDFBox 3.0.8, ajoutée au sprint 11 pour
`EF-REP-005` et `EF-REP-006` ; l'affirmation contraire du sprint 10
n'est plus vraie et est corrigée ici. Un service worker est livré
(`frontend/public/sw.js`) et présent dans le bundle de production.
`clamav` reste un service de **profil optionnel** : il n'est pas démarré
par `docker compose up -d` seul — il a été démarré et éprouvé au
sprint 10 (§6.2), et **n'a pas été relancé au sprint 11** : aucun
parcours du sprint 11 ne touche aux pièces jointes.

---

## 5. Architecture réelle

### 5.1 Modules Spring Modulith — 19

`ModularityTests` **vert** : aucune dépendance vers l'interne d'un autre
module, aucun cycle.

| Module | Rôle | Migrations |
|---|---|---|
| `identity` | comptes, rôles, JWT, invitation, administration, mot de passe oublié, révocation, second facteur, passkeys, appareils de confiance | V1, V2, V3, V17, V18 |
| `organization` | site, bâtiment, salle, plage réseau, QR fixe de salle | V4, V26 |
| `academic` | année, formation, niveau, promotion, classe, affectation, matières | V5, V6, V19 |
| `enrollment` | profil apprenant, inscription, changement de classe, groupes temporaires, suivi à distance | V7, V19, V24 |
| `alternation` | rythmes, affectations, exceptions, résolution | V8 |
| `planning` | import CSV et Excel, simulation, conflits, correction de ligne, calendrier interactif, publication versionnée, retour arrière | V12, V13, V23 |
| `coursesession` | séances, cycle de vie, points de contrôle nommés, remplacements, salle, modalité, report, demandes d'annulation | V9, V10, V13, V14, V21, V22, V24, V25 |
| `attendance` | jetons, validation, QR de salle, corrections, apprenants provisoires, justificatifs et leur analyse antivirus, départ anticipé, journal de transparence, rapports, résultat journalier, exports multiformats, attestations | V9, V10, V16, V26, V27, V29, V30, V34 |
| `studentimport` | import CSV et Excel des apprenants, correction de ligne | V11, V20 |
| `notification` | centre de notifications persistant, audience serveur, courriel, préférences, abonnements et chiffrement de poussée, délivrabilité | V15, V19, V33 |
| `dashboard` | tableau de bord par rôle | — |
| `claim` | réclamations : guichets, fil de messages, transfert, décision, réouverture | V28 |
| `outbox` | file transactionnelle des effets de bord : publication, diffusion, reprise, file d'échec, rejeu manuel | V31 |
| `document` | production des documents de restitution : CSV, classeur `.xlsx`, PDF paginé avec identité visuelle et identité de document | — |
| `search` | recherche globale dans le périmètre de l'appelant — **aucune table** : assemble les ports des modules qui détiennent la donnée | — |
| `integration` | flux iCalendar signé et révocable ; ports Microsoft Graph (réunion Teams, calendrier) et leurs adaptateurs inactifs | V34 |
| `audit` | piste d'audit, écrite par l'outbox ; consultation et export | V1, V32 |
| `bootstrap` | amorçage du profil `demo` | — |
| `shared` | types transverses, gestion d'erreurs, horloge | — |

Modules du cahier des charges **non encore créés** : `ai`, `iot`.
`reporting` reste fusionné dans `attendance` — les rapports vivent avec
la donnée qu'ils agrègent ; le module `document`, lui, ne connaît aucun
métier et ne fait que restituer ce qu'on lui donne. `integration` est
créé au sprint 11.

Le module `document` **ne dépend d'aucun module métier** : il reçoit un
titre, des faits, un en-tête et des lignes déjà rendues en texte. Ce sont
`attendance`, `audit` et `identity` qui dépendent de lui. Le module
`search` **ne détient aucune donnée** : il lit les ports publics de
`enrollment`, `identity`, `academic`, `organization` et `coursesession`,
comme `dashboard`. `ModularityTests` reste vert.

Le module `outbox` **ne dépend d'aucun module métier** : il route un
`messageType` vers l'`OutboxHandler` que le module compétent publie. Ce
sont les autres qui dépendent de lui. `ModularityTests` reste vert.

### 5.2 Migrations Flyway — schéma en V34

**64 tables métier** (compté sur la base `esic_test` :
`information_schema`, hors `flyway_schema_history`), `ddl-auto = validate`,
aucune donnée métier insérée par une migration. Les deux tables ajoutées
au sprint 11 sont `report_document` et `calendar_subscription` (V34).
Le chiffre annoncé au sprint 9 — « 58 » — était inexact : le dépôt a
raison, le document avait tort. `V17` ajoute `password_reset_token` et la colonne
`user_account.credentials_invalidated_at` ; `V18` ajoute
`mfa_credential`, `mfa_recovery_code`, `webauthn_credential` et
`trusted_device` ; `V19` ajoute `subject`, `subject_program`,
`student_group`, `student_group_member` et `email_delivery` ; `V20`
ajoute `student_import_row_correction`, la colonne
`student_import_row.sheet_name` et **remplace** l'unicité
`(job, ligne)` par `(job, feuille, ligne)` ; `V21` ajoute
`course_session.room_code` ; `V22` ajoute le lien de report et
`session_cancellation_request` ; `V23` assouplit la contrainte
`file_size_bytes > 0` en `>= 0`, un planning construit au calendrier
n'ayant pas de fichier ; `V24` ajoute `course_session.attendance_mode` et
`remote_link`, la table `remote_attendance_authorization`, et remplace la
contrainte `chk_attendance_record_source` de V10 pour accepter les canaux
distants ; `V25` ajoute les quatre types de point de contrôle nommés,
élargit `checkpoint_type` (`AFTERNOON_BREAK_RETURN` fait 21 caractères) et
impose l'unicité d'un type nommé par séance ; `V26` transforme
`room.static_qr_reference` en jeton serveur unique et daté et ajoute le
canal `ROOM_STATIC_QR` ; `V27` crée `session_guest_attendance` ; `V28`
crée `claim`, `claim_message` et `claim_event` — messages et décisions
dans deux tables distinctes, l'historique d'un dossier transféré devant
rester lisible ; `V29` crée `early_departure`, **sans** colonne d'effet
(il se déduit du statut) ; `V30` ajoute à `justification_attachment` le
verdict d'analyse antivirus, sa date et sa signature, par défaut
`NOT_SCANNED` — marquer `CLEAN` rétroactivement des pièces jamais
analysées serait une affirmation que rien ne fonde.

>
> `V31` crée `outbox_message` — une seule table pour tous les effets de
> bord, le cahier (§25.1) ne décrivant qu'un flux : action → outbox →
> diffuseur → fournisseur. `message_type` route vers le gestionnaire du
> module concerné, sans que `outbox` connaisse aucun d'eux. Les statuts
> `FAILED` et `DEAD` sont volontairement distincts : une tentative qui
> sera reprise n'est pas un effet de bord abandonné qui attend une
> décision humaine, et les confondre masquerait la file d'échec.
>
> `V32` ajoute `audit_event.outbox_key`, **nullable** et unique : les
> traces antérieures n'ont jamais transité par l'outbox et ne peuvent pas
> se voir attribuer une clé rétroactivement ; MySQL n'applique pas
> l'unicité aux valeurs `NULL`.
>
> `V33` crée `notification_preference` — une ligne par **exception** au
> défaut, jamais par combinaison, pour qu'une catégorie ajoutée plus tard
> soit active sans migration de données — et `push_subscription`, dont
> l'unicité porte sur l'**empreinte** de la terminaison : celle-ci
> contient un jeton propre à l'appareil et n'a pas à être indexée en
> clair. La révocation y est une date et non une suppression, sans quoi
> une page restée ouverte recréerait aussitôt l'abonnement retiré.
>
> `V34` crée `report_document` et `calendar_subscription`.
>
> `report_document` est le **registre** sans lequel « identifiant
> vérifiable » (§22.4) n'a aucun sens : sans lui, la référence imprimée
> sur le papier n'est qu'une décoration que personne ne peut confronter à
> quoi que ce soit. Le **contenu du PDF n'y est pas stocké** — il est
> reproductible depuis les données d'assiduité, et le conserver
> dupliquerait des données personnelles dans un second emplacement avec
> sa propre durée de conservation à gouverner. Est conservée une
> empreinte SHA-256 du document remis. `document_id` porte une part
> **aléatoire** : une séquence devinable permettrait de fabriquer une
> référence plausible.
>
> `calendar_subscription` ne stocke **pas le jeton** mais son empreinte
> SHA-256 : un agenda externe ne sait pas s'authentifier, le secret est
> donc l'URL, et une fuite de la base ne doit pas rendre les plannings
> lisibles. `feed_key` est une référence publique distincte, présente
> dans l'URL à côté du jeton, qui permet de retrouver la ligne d'un seul
> index. La révocation est une **date** : l'agenda continuera d'appeler
> l'URL pendant des semaines, et il faut pouvoir répondre « révoqué »
> plutôt que « inconnu ».

> **Règle absolue** : une migration appliquée n'est **jamais** modifiée,
> pas même un commentaire — cela invalide sa somme de contrôle et casse
> toute base existante. Les corrections passent par une nouvelle
> migration.
>
> Les migrations V10 à V16 citent en commentaire des chemins de rapports
> supprimés le 3 septembre 2026 (`docs/reports/…`). Ces références sont
> **conservées telles quelles** pour cette raison. Les décisions
> correspondantes sont reprises dans `docs/03-architecture.md` sous les
> mêmes identifiants `DEC-G1-*`.

---

## 6. Résultats de tests

Mesurés sur ce dépôt, branche `sprint/S11-pilotage-restitution`,
5 septembre 2026. Environnement : OpenJDK 21.0.12, Node 24.13.0,
npm 11.6.2, MySQL 8.4, Redis 7.4 en Docker Compose.

| Commande | Résultat |
|---|---|
| `cd backend && ./mvnw clean test` | **140 classes / 1209 tests / 0 échec / 0 erreur** — `BUILD SUCCESS`, `ModularityTests` vert (19 modules), schéma V34 |
| `cd frontend && npm test -- --watch=false` | **94 fichiers / 764 tests / 0 échec** |
| `cd frontend && npm run lint` | « All files pass linting » |
| `cd frontend && npm run build` | bundle produit, aucune alerte de budget ; `manifest.webmanifest`, `sw.js` et les icônes présents dans la sortie |
| `cd frontend && ./node_modules/.bin/tsc -p ../tsconfig.json --noEmit` | contrôle de type de la suite Playwright — aucune erreur |
| Chaîne de migrations sur base **vierge** | `V1` … `V34` appliquées dans l'ordre sur une base `esic_v34_check` créée pour l'occasion : **64 tables**, dont `report_document` et `calendar_subscription`. Base supprimée après contrôle |

**Antivirus non rejoué au sprint 11.** Les 8 tests exigeant un `clamd`
réel restent **ignorés** sans `ESIC_CLAMAV_REAL=1` ; ils n'ont pas été
relancés, aucun parcours du sprint 11 ne touchant aux pièces jointes. La
preuve du sprint 10 (§6.2) reste valable et n'est pas rejouée ici.

Les tests portant le tag `perf` sont exclus par défaut
(`./mvnw test -Pperf` pour les exécuter).

**Piège d'exécution** : la suite back-end exige les variables du `.env`.
Sans `set -a && source ../.env && set +a`, Flyway échoue avec
`Access denied for user '${MYSQL_USER}'` et toute la suite tombe en
erreur. Ce n'est pas un défaut du produit.

**Piège de compilation** : `./mvnw -o test-compile` peut répondre
« BUILD SUCCESS » alors que `./mvnw -o clean test-compile` échoue — le
plugin ne recompile pas les tests quand seules les sources principales ont
changé. L'extension Java de l'éditeur peut en outre laisser dans
`target/` une classe portant `Unresolved compilation problem`, qui se
manifeste au moment de l'exécution et non de la compilation. **Ne jamais
conclure d'un `test-compile` incrémental** : seul `clean` fait foi.

**Défaut corrigé au sprint 10 — dépendance à l'heure de la journée.**
`RoomQrAttendanceIntegrationTests` publie un créneau de planning à
`Instant.now()`, parce que c'est l'horloge du serveur qui décide si le QR
fixe est encore recevable. Avec la fenêtre de travail par défaut
(08:00–19:00 UTC), toute exécution en soirée produisait une anomalie
bloquante et sept tests échouaient — pour une raison sans aucun rapport
avec ce qu'ils vérifient. **Le défaut a été reproduit à l'identique sur
le tag `v0.9`** : il est antérieur à ce lot. La classe élargit désormais
la fenêtre et la durée minimale *pour elle-même*, et borne la fin du
créneau à la fin du jour UTC — une ligne de planning ne sait pas exprimer
une séance qui franchit minuit. Les deux règles restent vérifiées là où
c'est leur objet, dans les tests du module `planning`.

**Pool de connexions porté de 4 à 6 en test.** L'outbox draine la file
dans l'`afterCompletion` de la transaction métier : à cet instant, la
connexion de cette transaction n'est pas encore relâchée — Spring ne la
rend qu'après avoir déclenché les synchronisations — tandis que le
diffuseur en ouvre une seconde. Un même fil de requête détient donc deux
connexions au lieu d'une.

**Défaut corrigé au sprint 11 — une méthode HTTP inattendue produisait
un `500`.** En vérifiant que la piste d'audit n'offre aucune route
d'écriture (`EF-AUD-002`, docs/02 §23.4), le test a constaté que
`POST /api/v1/audit-events` répondait **`500 INTERNAL_ERROR`** au lieu de
`405`. La cause n'était pas propre à l'audit : le
`GlobalExceptionHandler` n'avait pas de gestionnaire pour
`HttpRequestMethodNotSupportedException`, et le rattrapage générique
`Exception` transformait **toute** méthode HTTP incorrecte, sur
**n'importe quelle route de l'API**, en panne serveur — ce que
docs/02 §30.1 interdit explicitement (« une erreur d'appel du client
produit un `400` explicite, jamais un `500` »). Un `405` est désormais
renvoyé, avec l'en-tête `Allow` exigé par la RFC 9110 §15.5.6.

**Défaut d'instabilité corrigé au sprint 11 — des tests affirmaient une
livraison synchrone que l'outbox ne promet pas.** Trois assertions
(`OutboxIntegrationTests` × 2, `PriorityPathRecetteIntegrationTests`)
vérifiaient l'existence d'une trace ou d'une notification
**immédiatement** après le commit métier. Or le drain qui suit le commit
est délibérément *best effort*
(`DefaultOutboxPublisher.scheduleDrain` → `drainQuietly`) : sous la
charge de la suite complète — six connexions partagées, dont deux
détenues par le même fil pendant l'`afterCompletion` — il peut ne pas
obtenir de connexion, et la ligne attend la reprise planifiée. **C'est la
garantie de l'outbox, pas un défaut** : l'effet de bord n'est jamais
perdu, il peut être différé (dette T-01 levée au sprint 10). Ces tests
passaient isolément et échouaient en suite complète.

Correction : `OutboxIntegrationTests` force un drain avant d'assertir —
ce qu'il vérifie reste l'idempotence, pas le délai ; la recette
prioritaire ramène `app.outbox.poll-interval` à 500 ms pour elle-même et
attend, dans une limite de 20 secondes, que la notification apparaisse.
Aucune des deux ne touche à l'encapsulation du module `outbox` :
`OutboxDispatcher` reste *package-private*.

**Changement de comportement assumé — en-têtes des exports CSV.** Les
trois formats d'un même rapport partagent désormais une seule
description (module `document`), donc les mêmes libellés de colonnes :
`session_id;titre;debut` devient
`Identifiant de séance;Titre;Début`. Des en-têtes techniques dans un PDF
officiel seraient illisibles, et deux jeux d'en-têtes finiraient par
diverger. Le CSV **n'a en revanche aucun préambule** : sa ligne 1 reste
l'en-tête, parce que tout ce qui la précède casse les analyseurs. Le test
`AttendanceIntegrationTests
.csvExportIsUtf8WithBomAndNeutralizesFormulaInjection` a été mis à jour
en conséquence.

**Couplage connu entre classes de test** : les compteurs de limitation
indexés sur l'*origine* réseau vivent dans Redis et sont partagés par
toute la suite — toutes les classes se connectent depuis `127.0.0.1`.
`AuthRateLimitIntegrationTests` et `CaptchaIntegrationTests` les remettent
donc à zéro dans un `@BeforeEach`. Sans cela, elles échoueraient pour une
raison sans rapport avec ce qu'elles vérifient, dès que le nombre total de
connexions de la suite augmente.

### 6.1 Recette navigateur

| Indicateur | Valeur |
|---|---|
| Commande | `npm run test:e2e` (pile démarrée, `ESIC_DEMO_PASSWORD` exporté) |
| Fichiers / tests | 10 / 149 |
| Navigateurs | chromium exécuté ; firefox, webkit et mobile configurés, non exécutés par défaut |
| Intégration continue | `.github/workflows/e2e.yml`, déclenchement manuel |

La suite ne couvre pas les fonctions qui n'ont pas d'écran : scan caméra,
exports Excel et PDF, écrans d'écriture `academic` et `enrollment`.
**Aucun test n'est écrit contre un écran qui n'existe pas.**

Les écrans livrés au sprint 9 — réclamations, départ anticipé, journal de
transparence — ont un écran mais **ne sont pas** dans la recette
navigateur : ils sont couverts par des tests de composant Angular.
`NOT_PERFORMED` pour ces parcours.

Les écrans livrés au sprint 2 — vérification en deux étapes, sécurité du
compte — sont couverts par des tests de composant Angular, **pas encore**
par la recette navigateur : `NOT_PERFORMED` pour ces parcours.

### Recette navigateur du sprint 11 — exécutée

| Élément | Valeur |
|---|---|
| Fichier | `tests/11-pilotage-restitution.spec.ts` |
| Commande | `npx playwright test --project=chromium tests/11-pilotage-restitution.spec.ts` |
| Pile | back-end profil `demo` sur `esic_connect_demo` (port 8080), `ng serve` (port 4200), MySQL / Redis / Mailpit en Docker |
| Résultat | **13 / 13 passés en 8,4 s** |

Parcours réellement rejoués dans Chromium : recherche globale, refus de
la recherche à un apprenant, fragment trop court refusé sans appel
serveur ; écran d'attestation (émission, vérification, registre), refus
d'un identifiant inconnu, refus de l'écran à un formateur ; abonnement
iCalendar **de bout en bout** — création, lecture du flux **réellement
servi hors session applicative** (`BEGIN:VCALENDAR`, `PRODID` ESIC),
révocation, `410 INT_FEED_REVOKED` ensuite, `404` sur jeton faux ; refus
de la piste d'audit au responsable et au formateur ; invitations non
activées sans adresse en clair ; **tableau équivalent** du graphique du
tableau de bord, avec la valeur portée en toutes lettres par chaque
barre.

**Ce que cette recette ne couvre pas, et pourquoi.** La *consultation*
de la piste d'audit est réservée à `ADMIN` / `SUPER_ADMIN`, et ces
comptes n'obtiennent aucun jeton contre leur seul mot de passe depuis le
sprint 2 (RG-007, `DEC-S2-005`) : la connexion renvoie un défi de second
facteur, que le support e2e ne sait pas franchir. Écrire un test qui le
contournerait donnerait une fausse preuve. Elle reste couverte côté
serveur (`AuditQueryIntegrationTests`, 6 tests) et côté écran
(`audit-trail.spec.ts`, 8 tests). **`NOT_PERFORMED` en navigateur.**

**Deux défauts de la suite navigateur, corrigés ici.**

1. **Toute la recette navigateur était cassée depuis le sprint 2.**
   `tests/support/auth.ts` cliquait
   `getByRole('button', { name: 'Se connecter' })` sans `exact` ; l'écran
   de connexion porte aussi, depuis les passkeys (`EF-AUTH-007`), un
   bouton « Se connecter avec une clé d'accès » que ce libellé apparie
   également. Playwright échouait en *strict mode violation* **dès
   l'authentification**, donc sur les 10 fichiers existants comme sur le
   nouveau. Corrigé par `exact: true`. Les 10 fichiers antérieurs
   n'ont pas été relancés dans ce sprint : leur état reste celui de
   l'audit du 3 septembre.
2. **`scripts/seed-demo.sh` ne fonctionne plus** : il se connecte en
   `ADMIN` et attend un jeton, alors que la politique de second facteur
   du sprint 2 renvoie un défi. Il échoue sur
   « Échec de connexion ADMIN (HTTP 200) ». Non corrigé au sprint 11 —
   hors périmètre, et le jeu de démonstration existant a suffi. Dette
   **T-19**.

**Deux couplages d'environnement à connaître avant de relancer.**

- **Limitation de débit.** Chaque exécution complète du fichier ouvre
  13 sessions depuis `127.0.0.1`, contre un seau d'origine de 60 par
  fenêtre de 15 minutes (`LOGIN_ORIGIN_LIMIT`). Au-delà de quatre
  exécutions rapprochées, la connexion est refusée et les tests
  échouent **pour une raison sans rapport avec ce qu'ils vérifient**.
  Remise à zéro : supprimer les clés Redis `esic:rate-limit:login-*`.
- **Compilation à la demande d'`ng serve`.** La toute première
  navigation vers un écran jamais compilé peut dépasser le délai du
  premier test. Précharger les routes visées (un simple `curl` sur
  chacune) avant de lancer la suite suffit ; c'est ce qui a été fait
  pour le run 13/13 ci-dessus.

**La recette navigateur n'a pas été relancée au sprint 10** : les écrans
livrés — préférences de notification, file d'échec des effets de bord,
état « en attente de confirmation » de l'émargement hors ligne — sont
couverts par des tests de composant Angular. `NOT_PERFORMED` pour ces
parcours. L'installation de la PWA et la réception d'une notification
poussée ne sont **pas** vérifiées en navigateur : elles supposent un
contexte sûr (HTTPS ou `localhost`) et, pour la poussée, un service
réel — voir §3 et §8.

---

### 6.2 Analyse antivirus contre un `clamd` réel — dettes T-04 et T-11

Exécutée le 5 septembre 2026. C'est ce qui manquait pour affirmer que
l'analyse antivirus fonctionne : jusqu'ici, seuls des doubles pilotés
étaient exercés.

| Élément | Valeur constatée |
|---|---|
| Démon | `ClamAV 1.4.6/28108/Sun Aug 30 06:27:10 2026` |
| Démarrage | `docker compose --profile antivirus up -d clamav`, conteneur `healthy`, `clamdscan --ping 1` → `PONG` |
| Plateforme | image publiée pour `linux/amd64` uniquement ; `compose.yaml` fixe `platform: linux/amd64` (émulation sur Apple Silicon) |
| `ClamAvRealDaemonIntegrationTests` | 3 tests, 0 échec — fichier sain → `CLEAN` ; EICAR → `INFECTED` (`Eicar-Test-Signature`) ; 4 Mio analysés sans erreur de cadrage |
| `JustificationAttachmentRealAntivirusIntegrationTests` | 5 tests, 0 échec — dépôt sain accepté et téléchargeable, adaptateur câblé vérifié, API annonçant `active: true` |
| `ClamAvMalwareScannerTests` | 10 tests contre un **vrai serveur TCP** parlant `INSTREAM` : cadrage `zINSTREAM\0`, blocs préfixés en gros-boutiste, bloc de fin, contenu restitué à l'identique, découpage au-delà d'un bloc |
| Contrôle manuel | `docker exec esic-connect-clamav clamdscan /tmp/eicar.txt` → `Eicar-Test-Signature FOUND` |

**Indisponibilité, délais, taille** — vérifiés et jamais confondus avec
« sain » : démon injoignable, démon muet (délai réellement appliqué),
coupure en cours d'échange et `INSTREAM size limit exceeded` produisent
tous `UNAVAILABLE`, jamais `CLEAN`.

**Ce que ces tests n'établissent pas.** La signature EICAR est
**ancrée au fichier entier**, par construction : enveloppée dans un PDF,
elle n'est plus reconnue — vérifié contre le démon réel. Il n'existe donc
pas de fichier à la fois structurellement valide et détecté par EICAR :
la détection est prouvée au niveau du port, et la réaction du produit à
un verdict `INFECTED` (`422`, aucune écriture disque) au niveau de l'API,
avec un double. C'est une propriété de la chaîne d'essai, pas une lacune
du produit — et le rappel que l'antivirus ne remplace pas les contrôles
structurels, qui s'appliquent **avant** lui.

### 6.3 Coût SQL du tableau de bord — dette T-03 levée

C'est ce qui manquait pour affirmer que la dette T-03 est traitée :
jusqu'ici le coût n'était pas mesuré, seulement supposé.

**Mesure.** `DashboardCardsIntegrationTests
.theManagerDashboardCostDoesNotGrowWithTheNumberOfSessions` compte les
`PreparedStatement` (statistiques Hibernate) d'un appel
`GET /api/v1/me/dashboard` d'un responsable pédagogique, avec 3 puis
9 séances dans son périmètre, et échoue si six séances de plus coûtent
six requêtes de plus.

| État du code | Requêtes, 3 séances | Requêtes, 9 séances | Écart |
|---|---:|---:|---:|
| Avant correction | 21 124 | 21 221 | **+97** (≈ 16 / séance) |
| Alternance mémorisée | 21 775 | 21 822 | **+47** (≈ 8 / séance) |
| Comptage de justificatifs borné | 2 508 | 2 514 | **+6** (1 / séance) |
| Rattachements de classes chargés en bloc | — | — | **0** ✅ |

> Les valeurs absolues sont cumulées sur la classe de test : seul l'écart
> entre deux appels identiques a un sens ici, et c'est lui que le test
> vérifie.

**Deux tests antérieurs documentaient cette dette et ont dû être
inversés** — ils affirmaient la croissance au lieu de la corriger :

| Test | Avant | Après |
|---|---|---|
| `DashboardIntegrationTests.…GrowsLinearlyWithTheNumberOfSessions…` | assertait `qMany > qFew` | renommé `…DoesNotGrowItsQueryCountWithTheNumberOfSessions` : 1 séance → **1640** requêtes, 10 séances → **1640** |
| `DashboardIntegrationTests.…DoesNotGrowItsQueryCountWithTheNumberOfClasses` | croissance ≤ 3 pour +14 classes ; garde-fou absolu `< 25` | croissance ≤ 3 tenue ; **garde-fou absolu retiré et expliqué** — la carte calcule désormais l'assiduité du périmètre (EF-REP-007), c'est un travail supplémentaire réel, pas une régression |

**Sept causes distinctes, toutes réelles :**

1. **une requête de points de contrôle par séance** — la conversion
   unitaire `toRef` en émettait une par séance ; `toRefs` les charge en
   bloc ;
2. **une résolution de contexte d'alternance par (apprenant × séance)** —
   le contexte dépend de l'inscription et du **jour**, jamais de la
   séance : deux cours du même après-midi donnent forcément la même
   réponse. Mémorisé par `(inscription, jour)` ;
3. **un comptage de justificatifs qui recalculait toute l'assiduité de la
   base** — `countPendingJustificationsInScope()` passait par la synthèse
   complète, avec des bornes ouvertes, pour obtenir un seul nombre. C'est
   ce qui expliquait les ~21 000 requêtes de base, et un coût qui
   grandissait à **chaque séance jamais créée**. La fenêtre est désormais
   obligatoire et le compte direct ;
4. **une requête de périmètre par (séance × classe)** —
   `AcademicScopeDirectory.isClassInScope(uuid)` interroge la base à
   chaque appel, et il était appelé dans la boucle sur les séances. Le
   périmètre est maintenant résolu une fois, puis interrogé en mémoire ;
5. **une requête de rattachement de classes par séance** —
   `CourseSession.classes` est une collection `LAZY` ; elle est
   désormais initialisée pour tout le lot par un `join fetch` ;
6. **une requête d'effectif par classe** — `classReport` interrogeait
   `findActiveRosterForClasses(Set.of(uneClasse))` classe par classe ;
   un seul appel couvre désormais tout le périmètre, groupé en mémoire ;
7. **deux requêtes par apprenant** — `DefaultEnrollmentDirectory.roster`
   résolvait le nom (`identity`) et le code de classe (`academic`) une
   inscription à la fois. Un nouveau port
   `UserDirectory.findNames(Collection)` et `findByPublicIds` les
   chargent en bloc. `internalIdsOf` faisait de même, une classe à la
   fois : il utilise désormais `findByPublicIds`.

Les points 2, 3, 4, 6 et 7 profitent aussi aux **rapports**
(`EF-REP-001` à `EF-REP-003`), qui partagent ce code, et le point 7 à
tout appelant de l'effectif d'une classe.

---

### 6.4 Résultats après le mandat démo/e2e/T-18 (5 septembre 2026, soir)

Mesurés sur la branche `feat/demo-readiness-e2e-ui` (non fusionnée),
même environnement que §6.

| Commande | Résultat |
|---|---|
| `cd backend && ./mvnw clean test` | **140 classes / 1218 tests / 0 échec / 0 erreur** — `BUILD SUCCESS`, `ModularityTests` vert (19 modules), schéma inchangé V34 |
| `cd frontend && npm test -- --watch=false` | **95 fichiers / 779 tests / 0 échec** |
| `cd frontend && npm run lint` | « All files pass linting » |
| `cd frontend && npm run build` | bundle produit, aucune alerte de budget |
| `npm audit` (frontend) | **0 vulnérabilité** (`qs` corrigé, `npm audit fix` non forcé) |
| `npm run test:e2e` | **13 fichiers / 167 tests / 0 échec / 0 ignoré — 31,3 min** |

Quatre exécutions complètes de la suite Playwright ont été nécessaires
pour atteindre ce résultat, chacune ayant mis au jour une cause réelle
distincte (jamais contournée, toujours corrigée) : limite de débit de
connexion par origine trop basse pour 162+ connexions consécutives
(`LOGIN_ORIGIN_LIMIT`, variable de session uniquement, jamais committée) ;
un helper de test API oubliant le second facteur (`tests/support/api.ts`) ;
une détection de succès MFA fondée sur une course DOM plutôt que la
vraie réponse HTTP ; une séance de démonstration créée à un horaire
figé dans un test antérieur à ce mandat ; une course entre `loginAsUi`
et la redirection asynchrone du garde de route. Le dernier passage,
sur une base `esic_connect_demo` remise à zéro juste avant, est
**entièrement vert** : voir le rapport HTML (`test-results/html-report/`)
et les traces (`test-results/artifacts/`, purgées à chaque nouvelle
exécution locale).

---

### 6.5 Renouvellement de session (6 septembre 2026)

Même environnement que §6. Aucune migration, aucun nouveau module.

| Commande | Résultat |
|---|---|
| `cd backend && ./mvnw clean test` | **142 classes / 1231 tests / 0 échec / 0 erreur** — `BUILD SUCCESS`, `ModularityTests` vert (19 modules), schéma inchangé V34 ; +13 vs §6.4 (`RefreshTokenIntegrationTests` 11, `RefreshTokenExpiryIntegrationTests` 2) |
| `cd frontend && npm test -- --watch=false` | **95 fichiers / 786 tests / 0 échec** (+7 vs §6.4 : `restoreSession` OK/KO, `refreshSession` file unique / échec, intercepteur `401` → renouvellement → rejeu / échec → `/login`) |
| `cd frontend && npm run lint` | « All files pass linting » |
| `cd frontend && npm run build` | bundle produit, aucune alerte de budget |

**Ce qui reste `NOT_PERFORMED`** : le parcours « recharger la page reste
connecté » n'a **pas** été rejoué dans un vrai navigateur. Il est couvert
par `RefreshTokenIntegrationTests` (bout en bout HTTP, cookie réellement
posé, rotaté, rejoué, révoqué) et par les tests de composant Angular
(`auth.service.spec.ts`, `api-error.interceptor.spec.ts`). Un scénario
Playwright est à ajouter (§10, priorité 6).

**Test d'expiration — durées réelles.** `RefreshTokenExpiryIntegrationTests`
force `idle-ttl` et `absolute-ttl` à `PT3S` via
`@SpringBootTest(properties = …)` et attend réellement (~3,5 s puis
~5,5 s). Il vérifie deux règles distinctes : l'inactivité **glisse** à
chaque usage, mais le plafond absolu ne bouge pas — un renouvellement à
mi-parcours ne repousse pas l'échéance absolue.

**Rejeu de vérification (6 septembre 2026, après-midi).** Toute la suite
ré-exécutée avant fermeture du lot :

| Commande | Résultat |
|---|---|
| `cd backend && ./mvnw clean test` | 143 rapports Surefire — 141 classes exécutées + 2 ignorées faute de `clamd` réel (`ESIC_CLAMAV_REAL` absent) — **1231 tests / 0 échec / 0 erreur**, `BUILD SUCCESS` |
| `cd frontend && npm run lint` | « All files pass linting » |
| `cd frontend && npm test -- --watch=false` | **95 fichiers / 786 tests / 0 échec** |
| `cd frontend && npx ng build --configuration production` | bundle produit, aucune alerte de budget |
| `frontend/node_modules/.bin/tsc -p tsconfig.json --noEmit` | contrôle de type de la suite Playwright — 0 erreur |
| `docker build ./backend` | image produite (dont l'étape `mkdir -p /data/uploads/justifications`) |
| `docker build ./frontend` | image produite (`npm install`, `ng build`, `nginx.conf` copié) |
| `docker compose -f compose.prod.yaml config` | valide ; `REDIS_PASSWORD` n'est plus interpolé dans la chaîne de la sonde Redis (voir ci-dessous) |

**Correctif `compose.prod.yaml` — sonde Redis.** La sonde utilisait
`redis-cli -a "${REDIS_PASSWORD}"` : Compose interpolait le mot de passe
directement dans la définition du service (visible dans
`docker compose config`). Rétabli en `$${REDIS_PASSWORD}` (étendu par le
shell **dans** le conteneur), avec un bloc `environment: REDIS_PASSWORD`
sur le seul service `redis` pour que la variable y soit résolue. Le mot
de passe ne figure plus que là où il est indispensable
(`--requirepass` de Redis, connexion du back-end).

**Non rejoué dans ce lot** : suite Playwright complète (167 tests, ~30 min,
exige la pile de démonstration — dernier passage vert en §6.4) ; montée
de la pile `compose.prod.yaml` en conditions réelles avec Quick Tunnel
(les deux images se construisent, la composition valide ; aucune
exécution live). `NOT_PERFORMED`.

---

## 7. Démonstration

| Nature | Statut |
|---|---|
| Recette d'intégration API du parcours prioritaire | `IMPLEMENTED_AND_TESTED` |
| Parcours prioritaire rejoué dans un vrai navigateur (2 apprenants, création → ouverture → QR et code court → émargement → anti-rejeu → clôture → isolation `AC-017`) | `IMPLEMENTED_AND_TESTED` |
| Parcours du sprint 11 rejoués dans un vrai navigateur contre la pile démarrée (recherche, attestation, abonnement iCalendar de bout en bout, refus de la piste d'audit, invitations, tableau équivalent) — **13 / 13**, §6.1 | `IMPLEMENTED_AND_TESTED` |
| ~~Écrans réservés à `ADMIN` / `SUPER_ADMIN` en navigateur~~ — T-20 levée hors sprint (5 septembre 2026) : le second facteur est réellement franchi (`tests/support/auth.ts`) | `IMPLEMENTED_AND_TESTED` — suite complète 167/167, §6.4 |
| Démonstration **manuelle** de bout en bout par un humain | **`NOT_PERFORMED`** — un navigateur piloté par script n'en est pas une |
| Déploiement | **`NOT_PERFORMED`** — aucune instance, aucune URL |

---

## 8. Dettes et risques

| Réf | Dette | Effet |
|---|---|---|
| ~~T-01~~ | **levée au sprint 10** — outbox transactionnelle avec drain immédiat, reprise planifiée et file d'échec | une panne du diffuseur ne perd plus la notification ; ce qui échoue durablement devient **visible** dans `/exploitation/effets-de-bord` |
| ~~T-02~~ | **levée au sprint 10** — les onze écouteurs d'audit passent par l'outbox | une trace ne peut plus manquer sans annuler l'action, ni subsister derrière une action annulée |
| ~~T-12~~ | **levée au sprint 10** — les réclamations notifient participants et guichet courant | — |
| ~~T-04~~ | **levée au sprint 10** — `clamd` réel démarré et éprouvé (ClamAV 1.4.6, base 28108) ; voir §6.2 | l'analyse reste **inactive par défaut** : sans le profil `antivirus`, les pièces sont marquées `NOT_SCANNED`, l'API et l'écran l'annoncent, et « garanti sans logiciel malveillant » ne doit jamais être écrit |
| ~~T-11~~ | **levée au sprint 10** — protocole `INSTREAM` vérifié contre un vrai serveur TCP **et** contre un `clamd` réel | — |
| ~~T-03~~ | **levée au sprint 11** — quatre sources de coût par séance supprimées et **mesurées** (§6.3) | le coût du tableau de bord ne suit plus le nombre de séances affichées |
| T-05 | rétention des pièces supprimées `À_DÉFINIR` | politique RGPD à arrêter avant tout usage réel |
| T-06 | pièces jointes sur système de fichiers local | non persistant sur un hébergement éphémère |
| ~~T-10~~ | **renumérotée T-18** — non traitée au sprint 11, voir ci-dessous | — |
| T-07 | cérémonie WebAuthn complète non rejouée en test | la vérification cryptographique repose sur la bibliothèque ; les tests couvrent contrat, défi, isolation et absence de donnée biométrique |
| T-08 | Turnstile jamais vérifié contre le service réel | aucune clé secrète dans le dépôt ; sans clé, le produit **déclare** qu'aucun contrôle n'est actif |
| T-09 | passkeys inutilisables hors `localhost` sans domaine ni HTTPS | contrainte du standard WebAuthn, pas du produit |
| **T-13** | **aucun service de poussée réel sollicité** (sprint 10) | le chiffrement RFC 8291 est vérifié contre le vecteur officiel de la RFC et la signature VAPID est implémentée, mais aucun message n'a jamais atteint un navigateur. Sans clés VAPID, l'API déclare `providerActive: false` — elle ne simule aucun envoi |
| **T-14** | **démarrage à froid hors ligne toujours non couvert** (sprint 10 ; **rechargement en ligne levé le 6 septembre 2026**) | un rechargement **avec réseau** rétablit désormais la session via le cookie de renouvellement `HttpOnly` (voir la mise à jour du 6 septembre). Reste non couvert : ouvrir l'application **sans réseau** — le cookie exige le serveur, et le jeton d'accès ne vit qu'en mémoire (RG-093). C'est un arbitrage, pas un oubli |
| **T-15** | **file d'actions différées non persistante** (sprint 10) | elle ne survit pas à un rechargement de page. Le code court étant un jeton (RG-093) et expirant en 30 s, la persister n'apporterait qu'un rejeu de codes périmés |
| **T-16** | **aucun locataire Microsoft réel sollicité** (sprint 11) | `EF-INT-002` et `EF-INT-003` restent `PARTIAL`. Le port, l'adaptateur Graph (jeton d'application mis en cache, `onlineMeetings`, `events`) et l'adaptateur inactif sont écrits et couverts par des tests, mais **aucun appel n'a jamais atteint Microsoft**. Sans identifiants, `GET /api/v1/integrations/microsoft/status` déclare `meetingActive: false` — le produit ne simule aucune réunion |
| **T-17** | **aucun fichier de logo dans le dépôt** (sprint 11) | l'en-tête des documents PDF est une **signature typographique** (bandeau, établissement, produit), pas une image. Dessiner un logo inventé serait pire que de ne pas en mettre. L'insertion d'un logo fourni par l'établissement se réduit à un `PDImageXObject` dans `PdfDocumentWriter.header` |
| ~~T-18~~ | **levée hors sprint (5 septembre 2026, mandat démo/e2e)** — écrans livrés : sélection multiple, aperçu obligatoire puis confirmation explicite pour les opérations de masse (`user-list`), nouvel écran `/administration/duplicates` (lecture seule, aucune fusion) | `DemoMfaProvisioner`, tests unitaires (80/80 module administration) et Playwright (`tests/12-bulk-and-duplicates.spec.ts`, 5/5) à l'appui |
| ~~T-19~~ | **levée hors sprint (5 septembre 2026)** — `scripts/seed-demo.sh` franchit le vrai second facteur (`/mfa/verify` ou `/mfa/enroll`+`/mfa/enroll/confirm`) via un secret TOTP déterministe **strictement optionnel** (`ESIC_DEMO_TOTP_SECRET` → `DemoMfaProvisioner`, actif uniquement sous le profil `demo`) | validé contre un back-end réel, base `esic_connect_demo` remise à zéro, y compris en ré-exécution immédiate (retry anti-rejeu RG-054/055 observé) |
| ~~T-20~~ | **levée hors sprint (5 septembre 2026)** — `tests/support/auth.ts` franchit réellement le second facteur pour `ADMIN`/`SUPER_ADMIN` (code TOTP calculé localement, décision sur la vraie réponse HTTP) | a mis au jour et corrigé au passage deux défauts réels sans rapport avec le MFA : une course entre `loginAsUi` et la redirection asynchrone du garde de route, et un test du parcours prioritaire à horaire de séance figé (§8, notes de validation) |
| — | base `esic_test` **recréée** au sprint 8 ; `esic_connect` (local) reste à recréer — `./scripts/db-reset.sh esic_connect` non exécuté | — |

---

## 9. Infrastructure

`docker compose up -d` démarre `mysql` (8.4), `redis` (7.4), `mailpit`
et `mosquitto`. Les trois premiers passent `healthy` ; **Mosquitto n'a
pas de sonde et aucun code back-end ne le consomme.**

`clamav` (1.4.6) appartient au **profil optionnel** `antivirus` : il ne
démarre pas avec `docker compose up -d` seul, mais avec
`docker compose --profile antivirus up -d clamav`. Il a été démarré et
éprouvé au sprint 10 (§6.2). L'image n'étant publiée que pour
`linux/amd64`, `compose.yaml` fixe `platform: linux/amd64` — sans quoi le
démarrage échoue sur un poste Apple Silicon. Procédure d'exploitation
complète : `docs/11-guide-deploiement.md` §7.

Quatre bases distinctes : `esic_connect` (local), `esic_connect_demo`
(démonstration), `esic_test` (tests), `esic_connect_ci` (intégration
continue). Le profil `test` lit `MYSQL_TEST_DATABASE`.

---

## 10. Prochaines priorités

1. **Exécuter `./scripts/db-reset.sh esic_connect`** — l'outillage est
   livré, l'exécution ne l'est pas. `esic_test` a été recréée au sprint 8 ;
   la base locale ne l'est toujours pas.
2. **Sprint 12 — intelligence et objets connectés** : service d'IA
   (`EF-AI-001..005`), borne connectée MQTT (`EF-IOT-001..005`), rapport
   des anomalies (`EF-REP-009`), mapping d'import et planning PDF texte
   assistés (`EF-IMP-005`, `EF-PLAN-012/013`), confirmation d'émargement
   par WebAuthn (`EF-ATT-011`).
3. ~~Livrer les écrans des opérations de masse et des doublons~~ (T-18) :
   **fait hors sprint le 5 septembre 2026** (soir) — voir « Dernière mise
   à jour » et §6.4. Reste à fusionner `feat/demo-readiness-e2e-ui` dans
   `batch/S02A-S11`.
4. **Éprouver les intégrations Microsoft** (T-16) : enregistrer une
   application dans un locataire Entra ID, injecter `tenantId`,
   `clientId` et `clientSecret` par l'environnement, et vérifier qu'une
   réunion Teams est réellement créée avant de présenter `EF-INT-002`
   comme livré.
5. **Éprouver la poussée** (T-13) : générer une paire VAPID hors dépôt,
   l'injecter par l'environnement, et vérifier qu'un navigateur reçoit
   réellement une notification avant de présenter `EF-NOTIF-005` comme
   livré.
6. **Étendre la recette navigateur** aux écrans des sprints 9 et 10, au
   parcours d'installation de la PWA sur un contexte HTTPS, et à un
   scénario **« recharger la page reste connecté »** (renouvellement de
   session du 6 septembre — couvert par des tests d'intégration et de
   composant, pas encore en navigateur). Les parcours du sprint 11 ont,
   eux, une suite écrite (`tests/11-pilotage-restitution.spec.ts`) — voir
   §6.1 pour son état d'exécution.
7. **Obtenir un fichier de logo de l'établissement** (T-17) pour les
   documents PDF officiels.
8. **Arrêter la politique de rétention des pièces jointes** (T-05) avant
   tout usage sur données réelles.
9. **Écran du résultat journalier** (`EF-ATT-004`) : l'API
   `GET /attendance/reports/daily` est livrée et testée depuis le
   sprint 8, aucun écran ne l'expose.

---

## 11. Règle de mise à jour

Ne jamais déclarer :

- `IMPLEMENTED_AND_TESTED` sans commande exécutée et reproductible ;
- démontré sans vérification manuelle enregistrée ;
- déployé sans URL ni preuve ;
- fonctionnel au seul motif que le code existe.

Ce document est mis à jour **à chaque livraison**, dans le même commit
que le code qu'il décrit.
