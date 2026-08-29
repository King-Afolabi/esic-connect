# État courant — ESIC Connect

## Dernière mise à jour

```text
29 août 2026
```

## Dernier commit stable

```text
495c2bf — feat: student profiles and historical enrollments (#10), sur main
```

## Phase actuelle

```text
Sélecteur de contexte de rôle (front-end) — branche
`feature/frontend-role-context`, PR ouverte contre `main`, NON fusionnée.
Troisième tranche verticale front-end : docs/02 §6.1 (exigence
EF-AUTH-003 « Choisir un contexte de rôle »). Le socle front-end (PR #11,
`6fa341f`) et le parcours public d'activation (PR #12, `2ff7aa8`) sont
fusionnés sur `main`. Aucun fichier back-end, migration V1–V7 ou
docs/01–04 modifié ; autorisation, CORS et endpoints back-end inchangés ;
aucune dépendance npm ajoutée.
- Nouveau `core/auth/role-context.service.ts` (`RoleContextService`,
  `providedIn: 'root'`) : contextes proposés = **uniquement les rôles
  présents dans le claim `roles` du JWT** (`AuthService.roles`), aucune
  valeur inventée. `active` = signal **en mémoire seule** (ni
  `localStorage` ni `sessionStorage` ni cookie JS ; docs/07 §6, RG-085),
  au même titre que le jeton d'accès — un rechargement le perd comme la
  session. Contexte par défaut = rôle le plus privilégié présent (ordre
  de `ROLES`, helper exporté `defaultContext`). Un `effect` réaligne le
  contexte sur la session : à tout changement de rôles, un contexte
  encore valide est conservé, sinon retour au défaut ; jeu de rôles vide
  → `null`.
- `effectiveRoles` (contexte actif seul, sinon union des rôles) ne sert
  **qu'à l'affichage et à la navigation** : `AppShell.navItems` et
  `Dashboard.quickLinks` filtrent désormais dessus via
  `visibleNavItems`. Cela ne peut que **restreindre** les entrées
  visibles, jamais élargir un droit — toute autorisation reste décidée
  par Spring Security (`roleGuard` inchangé, gardes de route toujours sur
  l'union réelle des rôles ; RG-002, RG-087).
- Nouveau composant `core/layout/role-context-menu/` (`app-role-context-menu`,
  `MatMenu`) affiché dans la barre supérieure de `AppShell`
  **uniquement si le compte cumule au moins deux rôles** (`hasChoice`).
  Accessibilité : bouton déclencheur `aria-haspopup="menu"`, items de
  menu avec `aria-current="true"` + icône `check` sur le contexte actif,
  libellés explicites. Le tableau de bord affiche le contexte actif et
  rappelle qu'il n'affecte pas les autorisations (visible seulement si
  plusieurs rôles).
- `/login`, `/activation` et `/dashboard` : comportement inchangé
  (`app.routes.ts` non modifié ; aucune route ajoutée, aucun endpoint
  créé). `AuthService`, intercepteurs, `jwt.ts`, gardes : inchangés.
- Tests front : **102** (17 nouveaux, 0 échec ; 85 → 102).
  `role-context.service.spec.ts` (12 : `defaultContext` ordre/vide ;
  contextes = rôles du JWT seuls ; 0 rôle → pas de choix, `effectiveRoles`
  vide ; 1 rôle → contexte adopté sans choix ; multi-rôles → défaut au
  plus privilégié + choix ; `select` restreint `effectiveRoles` sans
  toucher `available` ; `select` d'un rôle absent du JWT ignoré ;
  contexte encore valide conservé au changement de rôles, sinon retour au
  défaut ; aucun accès `Storage.prototype.setItem`).
  `role-context-menu.spec.ts` (3 : masqué pour un rôle unique ; contexte
  actif dans le déclencheur + `aria-haspopup` ; items = rôles détenus,
  repère `check`/`aria-current`, clic → `RoleContextService.select`).
  `app-shell.spec.ts` (+2 : pas de sélecteur pour un rôle unique ;
  sélecteur présent pour un compte multi-rôles). `dashboard.spec.ts`
  (+2 : pas de mention de contexte pour un rôle unique ; contexte actif
  affiché + « vos autorisations restent inchangées » pour un compte
  multi-rôles).
- Vérifs locales le 29 août 2026 (Node 24.13.0), depuis `frontend/` :
  `rm -rf node_modules && npm ci` → 0 vulnérabilité ;
  `npm test -- --watch=false` → 18 fichiers, 102 tests, 0 échec ;
  `npm run build` → bundle initial 437,57 kB brut / 112,73 kB transféré,
  0 alerte de budget ; `npm run lint` → « All files pass linting ».
  `package.json` / `package-lock.json` inchangés.
- Ambiguïtés documentaires (aucune règle inventée) : docs/02 §6.1 donne
  un exemple de libellés de contexte (« Gérer mes formations », « Consulter
  mes séances de formateur ») sans liste normative → une table
  `ROLE_CONTEXT_LABELS` fournit un libellé par rôle, modifiable ; le choix
  du contexte par défaut (rôle le plus privilégié) et la conservation
  d'un contexte encore valide au changement de session ne sont pas
  spécifiés → comportement retenu et testé, documenté ici.
```

### Activation de compte (front-end) — fusionnée sur `main` via PR #12 (commit `2ff7aa8`)

```text
Parcours public `/activation?token=<jeton>`, fusionné sur `main`
(commit `2ff7aa8`). Le socle front-end (PR #11) est fusionné sur `main`
(commit `6fa341f`). Aucun fichier back-end, migration V1–V7 ou docs/01–04
modifié ; autorisation et CORS back-end inchangés.
- Route `/activation` — **publique, sans aucune garde** (ni `authGuard`,
  ni `roleGuard`, ni `guestGuard`) : le jeton d'invitation fait foi,
  indépendamment d'une éventuelle session en mémoire (docs silencieux sur
  le cas d'un utilisateur déjà connecté activant un autre compte).
  N'apparaît pas dans la navigation authentifiée. Les routes `/login`,
  `/dashboard`, placeholders, gardes et navigation existantes sont
  inchangées.
- Endpoints consommés **exactement** (contrat existant, rien d'inventé) :
  * `GET /api/v1/account-invitations/validate?token=<jeton>` (jeton en
    paramètre de requête) → toujours `200` avec `{ "valid": boolean }`
    (aucune donnée personnelle, aucun motif) ;
  * `POST /api/v1/account-invitations/activate`, corps
    `{ "token": string, "password": string }` → succès `204 No Content`,
    corps vide, **aucun identifiant de session renvoyé**.
- Traitement du jeton : lu une seule fois depuis `?token=` via
  `ActivatedRoute.snapshot`, puis retiré de la barre d'adresse dès
  `NavigationEnd` par `Location.replaceState` (pas de rechargement, pas
  d'entrée d'historique) ; conservé uniquement dans un champ privé du
  composant (effacé à la destruction) ; jamais journalisé, affiché, mis
  en `localStorage` / `sessionStorage` / IndexedDB, ajouté à une autre
  URL, ni envoyé comme jeton porteur.
- Formulaire de mot de passe (Angular reactive form) : champ `password`
  seul — le contrat back-end est `{ token, password }` avec
  `@Size(min = 12, max = 200)`, sans règle de complexité ni champ de
  confirmation ; aucune de ces contraintes n'est renforcée côté client
  au-delà de `required` + `minLength(12)` + `maxLength(200)`. Bascule
  afficher/masquer accessible (bouton, `aria-label` explicite,
  `aria-pressed`), `autocomplete="new-password"`. Envoi désactivé tant
  que le formulaire est invalide ou en cours ; double envoi empêché ;
  champs marqués « touchés » à une soumission invalide ; mot de passe
  effacé du formulaire après succès, après échec terminal, et à la
  destruction du composant.
- États de l'interface (dérivés des codes back-end réels) :
  `validating` → `form` (invitation valide) → `success` (lien vers
  `/login`, **aucune connexion automatique**, aucun JWT fabriqué) ;
  `invalid-link` (jeton absent / illisible, `{ valid: false }`, ou
  `400 INVITATION_INVALID` à l'envoi) — **état terminal unique** : le
  back-end renvoie un seul code pour un lien inconnu / expiré / révoqué /
  déjà utilisé, aucune distinction n'est inventée ; `validation-error`
  (réseau ou `5xx` pendant la validation, bouton « Réessayer ») ;
  `submitting` ; message d'erreur en ligne pour `400 VALIDATION_ERROR`
  (« 12 à 200 caractères », formulaire conservé), pour le réseau
  (statut 0) et pour un `5xx` (message générique sûr, renvoi possible).
  Aucune trace serveur, message d'exception, requête SQL, valeur de jeton
  ni détail de compte n'est affiché.
- Intercepteurs ajustés (plus petit changement sûr) : `authTokenInterceptor`
  et `apiErrorInterceptor` excluent tous deux
  `/account-invitations/validate` et `/account-invitations/activate`
  (`isPublicInvitationRequest`) → aucun en-tête `Authorization` sur ces
  appels publics, et un `401` / `5xx` venant d'eux ne purge jamais la
  session en mémoire ni ne déclenche le bandeau global. Le `POST
  /account-invitations` protégé (émission) reçoit toujours le jeton
  porteur.
- Stratégie de session inchangée : jeton d'accès en mémoire uniquement,
  ni `localStorage` ni `sessionStorage` ni cookie JS (docs/07 §6,
  RG-085) ; un rechargement de page perd la session et renvoie vers
  `/login` ; une vraie session persistante exige le futur cookie
  `HttpOnly` + refresh token côté back-end. Le jeton d'invitation est
  distinct du jeton d'accès.
- Accessibilité : `<main>` sémantique, labels associés, `role="status"`
  pour la validation asynchrone, `role="alert"` pour les échecs, focus
  visible, navigation clavier, aucun indicateur d'état par la seule
  couleur, libellés de boutons explicites, page responsive cohérente avec
  l'écran de connexion.
- Tests front : **69 → 85** (16 nouveaux, 0 échec). `AccountActivationApiService`
  (méthode / chemin / placement du jeton en paramètre ; corps
  `{ token, password }` ; `204` géré ; aucun `Authorization`),
  `authTokenInterceptor` (aucun bearer sur validate/activate ; bearer
  conservé sur l'émission protégée), `apiErrorInterceptor` (un `401` /
  `5xx` public d'activation ne purge pas la session et ne notifie pas ;
  l'erreur est toujours relayée), `app.routes` (`/activation` déclarée
  sans garde ; joignable anonyme et authentifié), `AccountActivation`
  via `RouterTestingHarness` (navigation réelle) : jeton absent → état
  terminal sans requête ; jeton lu et retiré de l'URL visible ;
  formulaire pour invitation valide avec `autocomplete="new-password"` ;
  `{ valid: false }` → terminal ; « Réessayer » après échec de
  validation ; règles 12/200 ; bouton désactivé si invalide ; charge
  utile exacte + double envoi empêché ; succès + lien `/login` + aucune
  connexion + mot de passe effacé ; `INVITATION_INVALID` terminal ;
  `VALIDATION_ERROR` en ligne ; échec réseau récupérable ; jeton absent
  du DOM rendu et de tout stockage navigateur. `makeJwt` déplacé de
  `jwt.spec.ts` vers `jwt.testing.ts` (plus aucun spec n'en importe un
  autre ; `*.testing.ts` exclu du build).
- Aucune dépendance ajoutée (`@angular/material` fournit déjà le
  `progress-spinner`) : `package.json` et `package-lock.json` inchangés.
  Vérifs locales le 29 août 2026 (Node 24.13.0), depuis `frontend/` :
  `rm -rf node_modules && npm ci` → 0 vulnérabilité ;
  `npm test -- --watch=false` → 16 fichiers, 85 tests, 0 échec ;
  `npm run build` → bundle initial 410,57 kB brut / 106,54 kB transféré,
  0 alerte de budget ; `npm run lint` → « All files pass linting ».
- Ambiguïtés documentaires signalées (aucune règle inventée) : aucun
  document n'impose de champ de **confirmation** du mot de passe pour
  l'activation (docs/02 §8.3 étape 6 = simple « définition du mot de
  passe ») → champ omis ; l'accès invité à `/activation` est laissé sans
  garde car le jeton d'invitation est l'autorité de cet endpoint public
  (docs silencieux) ; le back-end renvoyant un unique `INVITATION_INVALID`,
  l'interface ne distingue pas expiré / consommé / révoqué.

Socle front-end Angular — **fusionné sur `main` via PR #11**
(commit `6fa341f`). Première tranche verticale authentifiée : connexion →
tableau de bord (rapport d'un **état de session local** établi après
connexion réussie).
- Application créée avec `ng new` sous `frontend/` (docs/03 §9.1) :
  Angular **21.2** (paquets framework / CLI / build résolus en 21.2.22 ;
  Material + CDK en 21.2.14 — même ligne mineure 21.2, versionnement
  propre à Angular Components ; runner de tests `@angular/build:unit-test`
  = Vitest + jsdom ; application **zoneless** par défaut), Node.js 24.13,
  npm 11. `package.json` déclare une politique de version cohérente
  (`^21.2.x` pour tous les paquets `@angular/*`). Composants standalone,
  TypeScript strict, formulaires réactifs, signaux, routes de
  fonctionnalités en lazy loading, control flow natif (`@if` / `@for`).
- Dépendances ajoutées, toutes first-party : `@angular/material` +
  `@angular/cdk` (Angular Material explicitement requis par docs/02 §48.1,
  docs/01 §5.3, US-023, T-J1-040) ; `angular-eslint` (+ `eslint`,
  `typescript-eslint`) en dev pour `npm run lint`. Pas de NgRx, pas de
  Tailwind/Bootstrap, pas de SSR ni service worker.
- Routes implémentées :
  * `/login` — écran de connexion (`guestGuard`), formulaire réactif
    email + mot de passe, validations alignées sur `LoginRequest`
    (`@NotBlank @Email` / `@NotBlank`), message d'échec unique et
    générique (aucune énumération de comptes), bouton désactivé +
    barre de progression pendant l'appel ;
  * `/dashboard` — premier écran authentifié (`authGuard`) : rapporte un
    **état de session local** (établi après connexion réussie ; le
    tableau de bord ne prétend PAS avoir revérifié le jeton porteur via
    un second appel d'API authentifié), affiche le compte (email saisi),
    l'identifiant public (claim `sub`), l'échéance du jeton et les rôles ;
    carte « accès rapides » n'exposant que des écrans livrés (donc vide
    tant qu'il n'y en a pas d'autre que le tableau de bord) ; état vide
    si aucun rôle ;
  * `/administration` — `roleGuard(['ADMIN','SUPER_ADMIN'])`, écran
    d'attente (placeholder) — périmètre aligné sur `UserAccountController` ;
  * `/students` — `roleGuard(['ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION'])`,
    placeholder — périmètre aligné sur `EnrollmentWeb.MANAGE_ROLES` ;
  * `/forbidden` (403) et `**` (404).
  Les deux routes placeholder sont **masquées de la navigation principale
  et des accès rapides** (`NavItem.placeholder`), mais restent
  directement adressables et gardées par rôle — un rôle non autorisé est
  toujours redirigé vers `/forbidden`. La navigation principale ne
  présente que les écrans réellement utilisables (aujourd'hui : le seul
  tableau de bord). Les routes authentifiées sont enfants d'une coquille
  `AppShell` (barre Material + navigation latérale responsive, repères
  `<nav>` / `<main>`, lien d'évitement, `aria-current`, email + rôles +
  déconnexion).
- Authentification / session :
  * `POST /api/v1/auth/login` consommé tel quel (réponse
    `{ accessToken, tokenType, expiresInSeconds }`) ; aucun endpoint
    `/auth/me` ni `/auth/logout` n'existe côté back-end — la déconnexion
    est purement locale, l'identité affichée vient de l'email saisi et des
    claims du JWT ;
  * **stockage du jeton en mémoire uniquement** (signal `AuthService`),
    ni `localStorage` ni `sessionStorage` ni cookie JS — conforme à
    docs/07 §6 et RG-085. Conséquence assumée : un rechargement de page
    perd la session et renvoie vers `/login`. `AuthService.restoreSession()`
    est le point d'ancrage d'un futur cookie `HttpOnly` + refresh token
    (stratégie cible docs/03 §15.2, docs/07 §6), non exposé par le
    back-end à ce jour ;
  * le décodage du JWT (rôles, `sub`, `exp`) est **non vérifié** et ne
    sert qu'à l'affichage et au filtrage de la navigation ; toute
    autorisation réelle reste décidée par Spring Security (consigne du
    lot ; docs/07 §7) ;
  * intercepteur de jeton porteur (en-tête `Authorization` sur les appels
    `/api`, jamais journalisé) ; intercepteur d'erreurs : `401` non-login
    → purge de session + redirection `/login?reason=expired` ; `0` / `5xx`
    → bandeau générique (aucune trace serveur exposée) ; `4xx` laissé au
    composant ; `normalizeHttpError` conserve le `code` métier
    (`ApiError`, docs/03 §10.3).
- Infra HTTP : URL de base d'API **relative** (`/api`) via
  `src/environments/environment*.ts` ; `ng serve` proxifie `/api` vers
  `http://localhost:8080` (`proxy.conf.json`) → aucune requête
  cross-origin, **aucune modification de la configuration CORS du
  back-end** (inexistante à ce jour) nécessaire en local. Un déploiement
  cross-origin devra définir l'URL absolue ici ET activer une
  configuration CORS Spring (documenté dans `environment.ts`).
- Accessibilité : labels de formulaire associés (Material), navigation
  clavier, focus visible, repères sémantiques, messages de validation
  `aria-live` / `role="alert"`, état de soumission communiqué
  (`aria-busy`), CSS responsive sans framework additionnel, aucun secret
  en paramètre d'URL.
- Tests front (69, Vitest) : `AuthService` (login succès/échec, session
  en mémoire, `restoreSession` sans persistance, `logout`,
  `handleUnauthorized`, `hasAnyRole`), décodage JWT, `normalizeHttpError`
  (préservation du code métier, masquage des 5xx, réseau, non-HTTP),
  intercepteur jeton porteur, intercepteur d'erreurs (401 / 5xx / 4xx),
  `authGuard` / `guestGuard` / `roleGuard`, matrice de navigation
  (placeholders jamais rendus quel que soit le rôle ; mapping
  rôle → route conservé pour la traçabilité), câblage réel des gardes sur
  `app.routes` (routes placeholder toujours déclarées et gardées ;
  TEACHER → `/forbidden` sur route ADMIN, SCHOOL_ADMINISTRATION →
  `/students` mais pas `/administration`, invité → `/login`), `Login`
  (rendu, état de soumission, message générique), `Dashboard` (rapport
  d'état de session **local**, aucune allégation d'appel d'API
  revérifié, rôles, état vide, absence des placeholders dans les accès
  rapides), `AppShell` (navigation limitée aux écrans livrés, placeholders
  absents même pour un ADMIN, déconnexion), `App`.
- CI : nouveau workflow `.github/workflows/frontend-ci.yml` (lint + tests
  + build de production, déclenché sur `frontend/**`). `backend-ci.yml`
  inchangé.
- Commandes de vérification front (voir plus bas) : `npm ci`,
  `npm test -- --watch=false`, `npm run build`, `npm run lint` — tous
  exécutés avec succès en local le 29 août 2026.
- Limites connues : pas de restauration de session au rechargement — un
  rechargement de page perd la session et renvoie vers `/login` ; une
  vraie session persistante exige le futur cookie `HttpOnly` + refresh
  token côté back-end ; l'écran `/activation` exige un `?token=` valide
  dans le lien (aucun renvoi d'invitation en libre-service dans la SPA) ;
  pas de contexte de rôle sélectionnable (docs/02 §6.1) ;
  `/administration` et `/students` sont des routes gardées sans contenu
  métier, volontairement masquées de la navigation ; PWA, notifications,
  SSE non abordés.
- Correction de revue (2ᵉ commit sur la PR) : formulation du tableau de
  bord rendue exacte (« session locale » au lieu de « appel d'API
  authentifié fonctionne ») ; routes placeholder retirées de la
  navigation visible tout en restant gardées et adressables ; alignement
  des versions `@angular/*` sur la ligne 21.2 ; `package-lock.json`
  régénéré ; 64 → 69 tests.

Inscriptions historiques — fusionné sur `main` via PR #10 (commit
`495c2bf`) — module `enrollment` + migration V7 `student_profile` /
`enrollment`
(schéma en version 7, appliquée et vérifiée). Couvre le profil apprenant
et l'inscription d'un apprenant
dans une classe pour une année scolaire, avec conservation de
l'historique lors d'un changement de classe (docs/02 §7.6, §13 ;
docs/04 §11.1, §13 ; RG-006, RG-012, RG-022, RG-023 ; AC-006 ;
T-J1-032 / US-053). N'aborde ni l'import CSV des apprenants, ni les
rythmes d'alternance, ni les apprenants provisoires, ni Angular.
- Entités `enrollment.internal.StudentProfile` (`user_id` = valeur
  technique via port `identity.UserDirectory`, unique ; `student_number`
  unique ; `work_study`, `birth_date`, `company_name` ; statut
  ACTIVE/ARCHIVED — seul ACTIVE produit dans ce lot) et
  `enrollment.internal.Enrollment` (`student_profile` = relation
  intra-module ; `class_group_id` / `academic_year_id` = valeurs
  techniques via nouveau port `academic.ClassGroupDirectory` ;
  `previous_enrollment_id` auto-référence ; `start_date` / `end_date`
  en `LocalDate` bornes inclusives ; `enrollment_source`
  MANUAL/CLASS_TRANSFER ; statut
  PENDING/ACTIVE/COMPLETED/TRANSFERRED/WITHDRAWN/SUSPENDED/ARCHIVED —
  ACTIVE/TRANSFERRED/COMPLETED/WITHDRAWN pilotés). Aucun DELETE
  physique ; rattachements, `start_date`, `enrollment_source` et
  `previous_enrollment_id` immuables.
- Règle RG-012 / docs/04 §13.3 : au plus une inscription ACTIVE par
  apprenant et par année scolaire — pré-contrôle applicatif
  (`ENR_ACTIVE_ENROLLMENT_EXISTS`, 409) doublé par la contrainte SQL
  `uq_enrollment_active_per_year` (deux colonnes générées VIRTUAL
  portant `student_profile_id` / `academic_year_id` uniquement pour une
  ligne ACTIVE ; une clôture libère immédiatement le créneau). Course
  concurrente :
  * création de profil (`StudentProfileService.create`) et inscription
    (`EnrollmentService.enroll`) — non transactionnelles ; l'INSERT est
    isolé dans le bean proxifié `EnrollmentPersister`
    (`@Transactional(REQUIRES_NEW)`, même approche que
    `academic.internal.AssignmentPersister`). La
    `DataIntegrityViolationException` est reçue **hors** de toute
    transaction en échec et retraduite en 409 sur place, uniquement pour
    `uq_student_profile_user` / `uq_student_profile_student_number`
    (profil) ou `uq_enrollment_active_per_year` (inscription) ; toute
    autre violation est relancée telle quelle (500 via le gestionnaire
    global). Jamais de `catch (Exception)`.
  * changement de classe (`EnrollmentService.transfer`) — reste
    `@Transactional` : l'UPDATE de clôture et l'INSERT de la nouvelle
    inscription sont atomiques, et l'INSERT doit voir dans la même
    transaction le créneau libéré. La course résiduelle ne peut donc pas
    être captée dans le service (transaction déjà rollback-only) : elle
    est retraduite après l'annulation faite par le proxy, par
    `EnrollmentExceptionHandler`, en 409 ciblé sur la seule contrainte
    `uq_enrollment_active_per_year`.
- Changement de classe (`POST /api/v1/enrollments/{id}/transfer`,
  docs/04 §13.2) : l'inscription courante ACTIVE est clôturée en
  TRANSFERRED (`end_date` = date effective, borne **inclusive**, ≥ sa
  `start_date`), l'UPDATE est flushé d'abord (colonnes générées → NULL,
  créneau libéré), puis une nouvelle inscription ACTIVE est créée
  (`start_date` = date effective **+ 1 jour** — bornes inclusives, aucun
  chevauchement de période ; docs/04 §13.2 ne fixe pas de valeur de
  `start_date`, la non-superposition découle des bornes inclusives et de
  l'unicité d'une inscription active §13.3 —, `enrollment_source` =
  CLASS_TRANSFER, `previous_enrollment_id` = ancienne). Vers une autre
  année : contrôle explicite d'absence d'inscription ACTIVE avant
  écriture. Deux événements d'audit (`ENROLLMENT_TRANSFERRED` sur
  l'ancienne, `ENROLLMENT_CREATED` sur la nouvelle). L'ancienne reste
  consultable (AC-006).
- Clôture (`POST /api/v1/enrollments/{id}/close`) : `status`
  (`COMPLETED` | `WITHDRAWN`, `@Pattern` + garde service), `reason`
  obligatoire, `effectiveDate` par défaut aujourd'hui (horloge
  injectée), ≥ `start_date`. Audit `ENROLLMENT_CLOSED`.
- Nouveau port public `academic.ClassGroupDirectory` (impl
  `academic.internal.DefaultClassGroupDirectory`, confinée) :
  `ClassGroupRef(internalId, publicId, code, programPublicId,
  programCode, academicYearInternalId, academicYearPublicId,
  academicYearCode, openForEnrollment)` — `openForEnrollment` faux dès
  qu'un maillon de la chaîne (classe, promotion, formation, année
  scolaire) est archivé ; l'inscription sous une chaîne archivée est
  refusée (409 `ENR_ARCHIVED_PARENT`). N'expose ni `ClassGroup`, ni
  repository. `ModularityTests` reste vert (module `enrollment` →
  `identity`, `academic`, `shared` ; publie vers `audit`).
- Routes (toutes réservées `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`
  — cahier §6.4, §10.1 ; `PEDAGOGICAL_MANAGER` exclu tant qu'un port de
  périmètre pédagogique public n'existe pas ; `TEACHER`/`STUDENT` sans
  accès — la consultation de son propre historique par l'apprenant
  relève d'un lot ultérieur) :
  * `GET/POST /api/v1/student-profiles`, `GET …/{publicId}` — filtres
    `q` (numéro étudiant), `status`, `user` ; tri liste blanche
    `studentNumber`/`createdAt` (sinon 400 `ENR_INVALID_SORT`) ;
    pagination max 100 ;
  * `GET/POST /api/v1/enrollments`, `GET …/{publicId}`,
    `POST …/{id}/transfer`, `POST …/{id}/close` — filtres `student`
    (profil), `classGroup`, `status` ; tri liste blanche
    `startDate`/`endDate`/`createdAt` ; pagination max 100.
  Aucun `PATCH`, aucun `DELETE`, aucune route nichée. DTO sans
  identifiant SQL interne (`id`, `userId`, `studentProfileId`,
  `classGroupId`, `academicYearId`) ni colonne auteur.
- Éligibilité de la cible d'un profil : compte existant, non archivé,
  porteur d'un rôle actif `STUDENT` (via `identity.UserDirectory`) —
  sinon 422 `ENR_USER_NOT_ELIGIBLE`. Un seul profil par compte
  (`ENR_PROFILE_EXISTS`), numéro étudiant unique
  (`ENR_DUPLICATE_STUDENT_NUMBER`).
- Audit : `enrollment.EnrollmentChangeEvent` (module racine, enums
  `EnrollmentResourceType` STUDENT_PROFILE/ENROLLMENT,
  `EnrollmentChangeAction` CREATED/TRANSFERRED/CLOSED) → nouveau
  `audit.internal.EnrollmentAuditListener` (catégorie `ENROLLMENT`,
  transaction REQUIRES_NEW), actions `STUDENT_PROFILE_CREATED` /
  `ENROLLMENT_CREATED` / `_TRANSFERRED` / `_CLOSED`, motif non sensible
  (`class=<code>;year=<code>` ou `class=<code>;status=<statut>`), jamais
  de numéro étudiant, de nom ni d'adresse.
- Horloge : bean `java.time.Clock` (`shared.config.ClockConfig`) injecté
  dans `EnrollmentService` (`start_date` / `effectiveDate` par défaut).
- Config test : `application-test.yml` plafonne désormais le pool
  HikariCP (`maximum-pool-size: 6`, `minimum-idle: 1`). Chaque classe
  `@SpringBootTest` déclarant sa propre `@TestConfiguration` imbriquée,
  Spring met en cache un contexte (et un pool) par classe ; avec le pool
  par défaut (10) et 16 classes `@SpringBootTest`, MySQL 8
  (`max_connections` = 151) saturait (« Too many connections »). Aucun
  test métier modifié.
- docs/03 et docs/04 non modifiés. Aucune donnée fictive en V7.
- Tests ajoutés (57) — voir la section de vérification.

Périmètre pédagogique — fusionné sur main via PR #9 (commit `52d38ce`) —
table `pedagogical_assignment` via migration V6 ; schéma en version 6,
appliquée et vérifiée. Affecte un responsable
pédagogique à une formation (RG-004, RG-010, RG-011) et branche le
contrôle d'accès par périmètre sur TOUT le référentiel académique
(formation, niveau, promotion, classe). Ne traite ni les inscriptions,
ni les matières, ni Angular.
- Entité `academic.internal.PedagogicalAssignment` : relation
  intra-module vers `Program` ; `manager_user_id` et `delegated_by_id`
  = valeurs techniques (FK SQL vers `user_account`, aucune relation JPA
  inter-module, résolues via le nouveau port `identity.UserDirectory`).
  Enums `PedagogicalAssignmentRole` (PRIMARY_MANAGER | DELEGATE) et
  `PedagogicalAssignmentStatus` (ACTIVE | CLOSED). Validité en
  **`LocalDate` / `DATE`** (jour civil, bornes inclusives), colonnes
  `reason` (motif d'affectation) et `close_reason` (motif de clôture).
  Conventions techniques complètes ; aucun DELETE physique ;
  rattachements, rôle et `valid_from` immuables, seule la clôture fait
  évoluer l'entité.
- Modèle : un seul PRIMARY_MANAGER ACTIF par formation via colonne
  générée `active_primary_key` (UNIQUE) + pré-contrôle applicatif
  (409 `ACAD_PRIMARY_MANAGER_EXISTS`). Gestion de la course entre deux
  créations : `saveAndFlush` isolé dans un bean dédié `AssignmentPersister`
  (`@Transactional(propagation = REQUIRES_NEW)`) — la transaction
  d'insertion échoue et est annulée sans contaminer l'appelant, qui n'est
  **pas** transactionnel (`PedagogicalAssignmentService.create` : lectures
  en transactions implicites, insertion déléguée, audit dans sa propre
  transaction). La `DataIntegrityViolationException` est donc capturée
  **hors** de toute transaction en échec et n'est retraduite en 409 que
  si la contrainte violée est `uq_pedagogical_assignment_active_primary`
  (recherche du nom de contrainte Hibernate **et** du message SQL, avec
  sémantique de doublon) — **toute autre** violation (FK, `CHECK`,
  `NOT NULL`, longueur, unicité de `public_id`...) est relancée
  telle quelle, jamais mappée sur ce code. DELEGATE multiples et
  chevauchements autorisés,
  toujours sur toute la formation. La période détermine l'accès
  effectif ; le créneau du PRIMARY_MANAGER n'est libéré que par une
  clôture explicite (status=CLOSED), même période expirée. `CHECK
  (valid_until IS NULL OR valid_until >= valid_from)`. Cible : doit
  exister, ne pas être archivée, porter un rôle actif PEDAGOGICAL_MANAGER
  — sinon **422 `ACAD_TARGET_NOT_ELIGIBLE`**. Création refusée sous une
  formation archivée (409 `ACAD_ARCHIVED_PARENT`).
- Routes `/api/v1/pedagogical-assignments` (réservées ADMIN/SUPER_ADMIN) :
  GET liste — filtres **`program`, `user`, `type`, `status`, `activeOn`**
  (`activeOn` en `LocalDate`, validité inclusive `validFrom <= activeOn
  <= validUntil`), tri liste blanche stricte `validFrom`/`validUntil`/
  `createdAt` (sinon 400 `ACAD_INVALID_SORT`), pagination max 100 ; GET
  détail ; POST création (`type` validé `@Pattern`) ; POST `{id}/close`
  — corps `{reason (obligatoire), effectiveDate? (LocalDate, défaut
  aujourd'hui)}`, exige `effectiveDate >= validFrom` sinon 400
  `ACAD_ASSIGNMENT_DATE_INVALID`, persiste `validUntil = effectiveDate`.
  Aucune route nichée, aucun PATCH, aucun DELETE. DTO sans id SQL ni
  `programId`/`managerUserId`/`delegatedById`.
- Contrôle de périmètre centralisé — nouveau `AcademicScopeGuard`
  (unique point de décision, lit le contexte Spring Security, jamais un
  paramètre client) :
  * accès **global** (aucun filtrage) = autorité `ROLE_ADMIN`,
    `ROLE_SUPER_ADMIN` **ou `ROLE_SCHOOL_ADMINISTRATION`** ; un
    PEDAGOGICAL_MANAGER cumulé avec l'un de ces rôles est donc global,
    cumulé seulement avec TEACHER il reste limité ;
  * sinon, lecture des listes `programs`/`programs/{id}/levels`/
    `promotions`/`class-groups` filtrée aux formations du périmètre
    effectif (sous-requête `IN` sur l'ensemble des `program_id` visibles,
    affectation ACTIVE dont la période couvre le jour courant) ;
  * détail et toute opération create/update/archive/restore hors
    périmètre → **403 `ACAD_FORBIDDEN`** (formation, niveau, promotion,
    classe).
  Écriture ouverte au PEDAGOGICAL_MANAGER via `SCOPED_WRITE_ROLES`
  (update/archive/restore de la formation + toutes les écritures niveau/
  promotion/classe), puis restreinte au périmètre par le service. La
  **création d'une formation** reste réservée à ADMIN/SUPER_ADMIN
  (`WRITE_ROLES`). AcademicYear reste global et inchangé.
- Codes d'erreur alignés : `ACAD_FORBIDDEN`, `ACAD_ASSIGNMENT_NOT_FOUND`,
  `ACAD_TARGET_NOT_ELIGIBLE`, `ACAD_PRIMARY_MANAGER_EXISTS`,
  `ACAD_ASSIGNMENT_ALREADY_CLOSED`, `ACAD_ASSIGNMENT_DATE_INVALID`
  (+ `ACAD_INVALID_ASSIGNMENT_ROLE` défensif).
- Horloge : `java.time.Clock` injectable (bean `shared.config.ClockConfig`,
  `@ConditionalOnMissingBean` pour permettre une horloge figée en test).
  `AcademicScopeGuard` et `PedagogicalAssignmentService` l'utilisent
  (`LocalDate.now(clock)`) au lieu de `LocalDate.now()` — dates de
  validité par défaut et décision de périmètre testables avec
  `Clock.fixed(...)`.
- Port `identity.UserDirectory` (impl `identity.internal.
  DefaultUserDirectory`, confinée) : `UserRef(internalId, publicId,
  archived, activeRoles)` — types standard uniquement, n'expose ni
  `UserAccount`, ni repository. Complète `CurrentUserResolver`.
  `ModularityTests` reste vert.
- Audit : `AcademicChangeEvent` étendu (type `PEDAGOGICAL_ASSIGNMENT`,
  action `CLOSED`) → `audit.internal.AcademicAuditListener` inchangé,
  produit `PEDAGOGICAL_ASSIGNMENT_CREATED` / `_CLOSED` (catégorie
  ACADEMIC, motif non sensible `program=<code>;type=<type>`, jamais de
  donnée personnelle).
- docs/03 et docs/04 non modifiés. Aucune affectation fictive en V6.
- Tests ajoutés / mis à jour (49) :
  `PedagogicalAssignmentServiceTests` (16, Mockito — persister mocké,
  horloge figée ; dont **traduction d'une collision `active_primary` en
  409** avec message SQL réaliste, **relance inchangée d'une violation
  FK sans objet**, `validFrom` par défaut lu sur l'horloge injectée,
  clôture avant `validFrom`, clôture par défaut sur l'horloge injectée) ;
  `AcademicScopeGuardTests` (7, Mockito + `SecurityContextHolder` +
  `Clock.fixed` — global admin/super-admin/school-admin, manager+teacher
  limité et **requêtes de périmètre datées par l'horloge injectée**,
  appelant non résolu → rien de visible, `requireProgramInScope` OK /
  403, contexte anonyme non global) ;
  `PedagogicalAssignmentConstraintsTests` (11, @DataJpaTest — unicité
  `active_primary` (autre formation acceptée), DELEGATE non limités,
  créneau libéré par clôture, `CHECK` période + validité un seul jour
  acceptée, `public_id` unique, FK `RESTRICT` `program`/`manager`/
  `delegated_by` via `org.hibernate.exception.ConstraintViolationException`
  précise, **`isActivePrimaryUniqueViolation` reconnaît une vraie
  exception de collision et rejette une violation `public_id`**) ;
  `PedagogicalScopeIntegrationTests` (4, @SpringBootTest — scope sur
  formation/niveau/promotion/classe, manager+teacher limité,
  manager+administrator global, school-admin lecture globale sans gestion
  d'affectations) ;
  `PedagogicalAssignmentIntegrationTests` (11, @SpringBootTest — cycle
  create/list/close + audit, filtres `activeOn` (dates inclusives) et
  `type`, clôture par défaut à aujourd'hui, clôture avant `validFrom`
  refusée, éligibilité de la cible → 422, doublon PRIMARY_MANAGER → 409,
  **deux créations concurrentes (pool 2 threads) → exactement un 201 et
  un 409**, `type` invalide → 400, tri hors liste blanche, matrice
  401/403/200). `AcademicServiceTests` : constructeurs des 4 services
  académiques (+`AcademicScopeGuard` mock).

Référentiel académique minimal — fusionné sur main via PR #8 (commit
`a27b761`) — nouveau module `academic` + migration V5
`academic_year` / `program` / `program_level` / `promotion` / `class_group`
(schéma en version 5, appliqué et vérifié). Couvre la hiérarchie
formation → promotion → classe/groupe (docs/04 §12) ; n'aborde ni les
inscriptions, ni les matières, ni les responsabilités pédagogiques, ni
Angular.
- `academic_year` et `program_level` inclus uniquement comme référentiels
  support des FK de `promotion` (academic_year_id) et `class_group`
  (program_level_id). Conventions techniques complètes (public_id,
  created_at/by, updated_at/by, version, status, archived_at/by,
  archive_reason). Écarts documentés vs docs/04 §12 : `program_level`
  reçoit public_id/horodatage/version/archivage (absents du tableau
  §12.3) ; `promotion` reçoit start_date/end_date optionnelles pour la
  validation de période ; les colonnes external_source/external_id de
  §12.2/§12.5 ne sont pas reprises (pas de synchronisation externe).
- CRUD + archivage logique + restauration pour les cinq entités. Aucun
  DELETE physique. `code` immuable après création ; tous les
  rattachements parents (program, academic_year, program_level,
  promotion, site) immuables.
- Consultation paginée (max 100, défaut 20) + filtres : status, q
  (code+name, LIKE échappé) ; promotions filtrables par program /
  academicYear ; classes par promotion / programLevel / site ; niveaux
  listés sous /programs/{id}/levels. Tri liste blanche (sinon 400
  ACAD_INVALID_SORT). Consultation par public_id. Routes exclusivement
  en public_id : `/api/v1/academic-years`, `/api/v1/programs`,
  `/api/v1/programs/{id}/levels` + `/api/v1/program-levels/{id}`,
  `/api/v1/promotions`, `/api/v1/class-groups`.
- Règles métier vérifiées : end_date > start_date (année, + CHECK SQL) ;
  période de promotion, si renseignée, strictement incluse dans celle de
  l'année (ACAD_PROMOTION_PERIOD_OUT_OF_YEAR) ; modification de la période
  d'une année refusée si elle exclurait une promotion existante à période
  renseignée (ACAD_ACADEMIC_YEAR_PERIOD_CONFLICT, deux `exists` ciblés,
  aucun chargement de liste) ; le program_level d'une classe doit
  appartenir à la même formation que sa promotion
  (ACAD_PROGRAM_LEVEL_MISMATCH), revérifié aussi à la restauration ;
  création refusée sous un parent archivé (ACAD_ARCHIVED_PARENT) ;
  archivage refusé tant qu'il reste des enfants actifs
  (ACAD_HAS_ACTIVE_CHILDREN : niveaux/promotions pour une formation,
  promotions pour une année, classes pour niveau/promotion) ;
  restauration d'une classe refusée si un maillon de la chaîne est
  archivé — promotion, sa formation, son année, le niveau, la formation
  du niveau — ou si le site est absent/archivé ; restauration d'une
  promotion refusée si sa formation ou son année est archivée.
  `capacity > 0` (class_group, + CHECK SQL).
- Unicités testées : academic_year.code (global), program.code (global),
  (program_id, code) pour program_level, (program_id, academic_year_id,
  code) pour promotion, (promotion_id, code) pour class_group, tous les
  public_id ; FK RESTRICT vérifiées (program→promotion,
  promotion→class_group).
- Rattachement au site : `class_group.site_id` est une valeur technique
  (FK SQL `fk_class_group_site` vers `site.id`), jamais une relation JPA
  inter-module. Résolu via un nouveau port public minimal
  `organization.SiteDirectory` (impl `organization.internal.
  DefaultSiteDirectory`) qui n'expose que `SiteRef(internalId, publicId,
  archived)` — ni `Site`, ni `SiteRepository`, ni `organization.internal`.
  Le module `academic` n'importe jamais `organization.internal`.
  `ModularityTests` reste vert.
- Autorisations @PreAuthorize : lecture =
  ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION/PEDAGOGICAL_MANAGER ; écriture
  = ADMIN/SUPER_ADMIN uniquement (écriture PEDAGOGICAL_MANAGER reportée
  au périmètre pédagogique T-J1-023). TEACHER et STUDENT exclus (401/403
  testés).
- DTO sans identifiant SQL interne ni colonne auteur ; erreurs via
  ApiError commun (AcademicExceptionHandler, codes ACAD_*).
- Audit : événement applicatif `academic.AcademicChangeEvent` (module
  racine) → `audit/internal.AcademicAuditListener` (catégorie ACADEMIC,
  transaction REQUIRES_NEW), actions
  ACADEMIC_YEAR_/PROGRAM_/PROGRAM_LEVEL_/PROMOTION_/CLASS_GROUP_ +
  CREATED/UPDATED/ARCHIVED/RESTORED, motif non sensible (code), jamais de
  donnée personnelle.
- Aucune formation, promotion ni classe fictive insérée en V5.

Référentiel organisationnel — fusionné sur main via PR #7 (commit
`085c2f9`) — nouveau module `organization` +
migration V4 `site` / `building` / `room` / `site_network_range` (schéma
en version 4, appliqué et vérifié). Ce module élargit et remplace le
module `room` prévu par l'architecture (docs/03 §7.6).
- Hiérarchie site → bâtiment → salle. Conventions techniques complètes
  (public_id, created_at/by, updated_at/by, version, status, archived_at/by,
  archive_reason) ; `site_network_range` reçoit en plus public_id,
  updated_at et version.
- CRUD + archivage logique + restauration (site/bâtiment/salle) ;
  plages réseau = création + activation/désactivation. Aucun DELETE
  physique. `code` immuable après création ; rattachement au site immuable.
- Consultation paginée (max 100, défaut 20) + filtres (status, q, site,
  building, active) + tri liste blanche. Consultation par public_id.
  Routes exclusivement en public_id.
- Règles : refus building/room sous parent archivé ; room.site =
  building.site imposé ; archivage d'un site/bâtiment refusé tant qu'il
  reste des enfants actifs ; unicité site.code (global), (site,code) pour
  building et room, (site,cidr) active pour les plages.
- Validations : fuseau IANA via ZoneId, code pays ISO 3166-1 alpha-2,
  CIDR IPv4 et IPv6 réellement validé (préfixes bornés 0..32 / 0..128,
  sans résolution DNS).
- Autorisations @PreAuthorize : lecture site/bâtiment/salle =
  ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION/PEDAGOGICAL_MANAGER ; écriture
  = ADMIN/SUPER_ADMIN ; site_network_range = SUPER_ADMIN pour TOUTES les
  opérations, consultation comprise.
- DTO sans identifiant SQL interne ni colonne auteur ; erreurs via
  ApiError commun (OrganizationExceptionHandler).
- Audit : événement applicatif `organization.OrganizationChangeEvent`
  (module racine) → `audit/internal.OrganizationAuditListener`
  (catégorie ORGANIZATION, transaction REQUIRES_NEW), actions
  SITE_/BUILDING_/ROOM_/SITE_NETWORK_RANGE_ + CREATED/UPDATED/ARCHIVED/
  RESTORED/ACTIVATED/DEACTIVATED, motif non sensible (code, cidr), jamais
  de donnée personnelle ni d'IP.
- Port public minimal ajouté au module `identity` :
  `identity.CurrentUserResolver` (résout l'id interne depuis le subject
  public du JWT) ; implémentation `DefaultCurrentUserResolver` confinée à
  `identity.internal`. N'expose ni UserAccount, ni repository, ni autre
  classe interne. `ModularityTests` reste vert.
- Aucun site fictif ni donnée métier insérés en V4.

Administration minimale des comptes et des rôles (fusionnée sur main via
PR #6) — n'avait ajouté aucune migration (colonnes suspended_*/archived_at/
user_role.* déjà présentes en V1) :
- GET /api/v1/users (ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION) : liste
  paginée (taille max 100, défaut 20), filtres status / role (affectation
  active) / q (email+prénom+nom, normalisé, borné à 100 car., LIKE
  échappé), tri restreint à createdAt/lastLoginAt/email/lastName ;
- GET /api/v1/users/{public_id} : détail + historique complet des rôles ;
- POST …/{public_id}/suspend | /restore (ACTIVE↔SUSPENDED, motif
  obligatoire) — SCHOOL_ADMINISTRATION autorisé ;
- POST …/{public_id}/archive (ADMIN/SUPER_ADMIN) : statut ARCHIVED,
  clôture de tous les user_role actifs dans la même transaction
  (historique conservé), irréversible dans ce lot ;
- POST …/{public_id}/roles et …/roles/{roleCode}/revoke
  (ADMIN/SUPER_ADMIN) : attribution = nouvelle ligne user_role ; retrait =
  clôture (active=false, valid_until), jamais de suppression ; retrait du
  dernier rôle actif refusé.
DTO exposant uniquement public_id (jamais id SQL, password_hash, jeton).
Contrôles sensibles doublés dans le service (au-delà de @PreAuthorize) :
un compte ou le rôle SUPER_ADMIN n'est administrable que par un
SUPER_ADMIN ; auto-suspension / auto-archivage / retrait de son propre
rôle interdits. Audit ACCOUNT_SUSPENDED / ACCOUNT_REACTIVATED /
ACCOUNT_ARCHIVED / ROLE_ASSIGNED / ROLE_REVOKED (module audit, motif
seul, sans donnée sensible). PEDAGOGICAL_MANAGER exclu tant que le
périmètre pédagogique n'existe pas. Aucune suppression physique. Toujours
aucun MFA, WebAuthn, refresh token ni réinitialisation de mot de passe.

Flux d'invitation et d'activation de compte (fusionné sur main via PR #4) :
- POST /api/v1/account-invitations (protégé ADMIN/SUPER_ADMIN/
  PEDAGOGICAL_MANAGER/SCHOOL_ADMINISTRATION) : émission réservée aux
  comptes PENDING_ACTIVATION, attribution du rôle demandé via user_role,
  jeton SecureRandom 32 octets Base64URL, empreinte SHA-256 seule stockée,
  TTL configurable (défaut P30D, strictement positif), révocation des
  invitations PENDING antérieures, email d'activation via Mailpit ;
- GET /api/v1/account-invitations/validate (public) : réponse générique
  {"valid": bool} — aucune donnée personnelle, réponse identique pour
  jeton inconnu/expiré/révoqué/accepté ;
- POST /api/v1/account-invitations/activate (public) : mot de passe encodé
  (BCrypt), statut ACTIVE, email_verified_at, invitation ACCEPTED à usage
  unique.
Audit ACCOUNT_INVITATION_ISSUED / ACCOUNT_ACTIVATED (module audit, sans
jeton).

Dette technique : l'email d'activation est envoyé de façon synchrone
après commit ; en cas d'échec l'invitation est conservée et seule une
erreur technique est journalisée (ni jeton, ni email, ni lien). Il
n'existe pas encore de file persistante ni de reprise garantie
(docs/03-architecture.md §18, cahier §23.3).
```

## Documents

| Document | Statut |
|---|---|
| Cadrage | CONÇU |
| Cahier des charges | CONÇU |
| Architecture | CONÇU |
| Modèle de données | CONÇU |
| Product Backlog | CONÇU |
| Roadmap | CONÇU |
| Sprint Backlog | CONÇU |
| Diagrammes | CONÇU |
| Risques | CONÇU |
| Sécurité/RGPD | CONÇU |
| Tests/recette | CONÇU |
| Matrice RNCP | CONÇU |
| Journal IA | INITIALISÉ |

## Implémentation

| Fonctionnalité | Statut |
|---|---|
| Dépôt Git | INITIALISÉ (`main`, remote `origin` GitHub) |
| Docker Compose | TESTED |
| Spring Boot | TESTED (socle : démarrage du contexte, `mvn test` exécuté avec succès — aucune route ni entité métier) |
| Angular | IMPLEMENTED (socle `frontend/` fusionné via PR #11 = commit `6fa341f` ; activation de compte fusionnée via PR #12 = commit `2ff7aa8` ; sélecteur de contexte de rôle (docs/02 §6.1, EF-AUTH-003) sur branche `feature/frontend-role-context`, PR ouverte non fusionnée — Angular 21.2 (framework/CLI 21.2.22, Material/CDK 21.2.14) / Node 24, zoneless, standalone, Angular Material ; routes `/login`, `/activation` (publique, sans garde), `/dashboard`, `/administration`, `/students`, `/forbidden`, `**` — `/administration` et `/students` gardées par rôle mais masquées de la navigation ; `authGuard` / `guestGuard` / `roleGuard` ; intercepteurs jeton porteur + erreurs, endpoints publics d'activation exclus (pas de bearer, pas de purge de session) ; jeton d'accès **en mémoire uniquement** (docs/07 §6, RG-085) ; jeton d'invitation lu depuis `?token=` puis retiré de l'URL (`Location.replaceState`), jamais journalisé / affiché / stocké / envoyé en bearer ; activation `POST …/activate` → `204`, **aucune connexion automatique** ; le tableau de bord rapporte un état de session **local** (pas de second appel d'API vérifié) ; `RoleContextService` (contexte de rôle en mémoire seule, rôles du seul JWT, affichage/navigation uniquement, aucun effet sur Spring Security) + `app-role-context-menu` visible seulement si ≥ 2 rôles ; 102 tests Vitest verts, `npm ci` / `npm run build` / `npm run lint` verts en local le 29 août 2026. Non démontré de bout en bout avec le back-end en marche ; pas de restauration de session au rechargement) |
| MySQL | TESTED (healthy, auth root et `esic_app` vérifiée) |
| Redis | TESTED (healthy, auth vérifiée) |
| Flyway | TESTED (V1 tables identité/audit, V2 seed des 6 rôles, V3 table `account_invitation`, V4 tables `site`/`building`/`room`/`site_network_range`, V5 tables `academic_year`/`program`/`program_level`/`promotion`/`class_group`, V6 table `pedagogical_assignment`, V7 tables `student_profile`/`enrollment` — migrations appliquées et vérifiées, schéma en version 7) |
| Authentification | TESTED (`POST /api/v1/auth/login` : email/mot de passe, JWT HS256 stateless, `last_login_at`, audit succès/échec ; réponse publique uniforme vérifiée pour email inconnu/mauvais mot de passe/compte non actif ; routes protégées refusent sans jeton ; MFA/WebAuthn/refresh token non implémentés) |
| Rôles | TESTED (persistance `role`/`user_role` : 6 rôles système, unicité d'affectation active, réattribution après clôture ; attribués via `user_role` à l'émission d'une invitation ; API d'attribution / retrait dédiée — voir « Gestion des comptes / rôles ») |
| Gestion des comptes / rôles | TESTED (`GET /api/v1/users` paginé/filtré/trié, `GET /api/v1/users/{public_id}`, `POST …/{public_id}/suspend`·`/restore`·`/archive`·`/roles`·`/roles/{roleCode}/revoke` ; `@PreAuthorize` + contrôles sensibles dans `UserManagementService` (protection SUPER_ADMIN, auto-action interdite, dernier rôle actif protégé) ; archivage = clôture transactionnelle des rôles actifs, ARCHIVED irréversible ; DTO sans id SQL / `password_hash` / jeton ; audit `ACCOUNT_SUSPENDED`/`ACCOUNT_REACTIVATED`/`ACCOUNT_ARCHIVED`/`ROLE_ASSIGNED`/`ROLE_REVOKED` ; aucune migration V4 ; `PEDAGOGICAL_MANAGER` exclu jusqu'au périmètre pédagogique) |
| Invitation / activation | TESTED (`POST /api/v1/account-invitations` protégé par rôle, `GET …/validate` et `POST …/activate` publics ; migration V3 `account_invitation` ; jeton SecureRandom 32 o Base64URL, empreinte SHA-256 unique stockée, TTL configurable strictement positif, révocation des invitations PENDING antérieures, jeton à usage unique ; validation publique strictement générique ; email d'activation via Mailpit ; audit `ACCOUNT_INVITATION_ISSUED`/`ACCOUNT_ACTIVATED` sans jeton) |
| Notification (email) | TESTED (module `notification` : écouteur `AFTER_COMMIT` sur `AccountInvitationIssuedEvent`, envoi SMTP `SimpleMailMessage` via Mailpit ; échec d'envoi avalé, invitation conservée, log sans jeton/email/lien ; pas de file persistante — dette technique) |
| Périmètre pédagogique (pedagogical_assignment) | TESTED (module `academic`, migration V6 réécrite ; entité `PedagogicalAssignment` reliant un responsable (`manager_user_id`) + `delegated_by_id` — valeurs techniques via port `identity.UserDirectory` — à une formation ; rôles PRIMARY_MANAGER/DELEGATE, statut ACTIVE/CLOSED ; validité en `LocalDate`/`DATE` bornes inclusives, colonnes `reason`/`close_reason` ; un seul PRIMARY_MANAGER actif par formation (colonne générée `active_primary_key` + pré-contrôle 409 + collision de contrainte retraduite en 409, jamais 500), DELEGATE multiples ; `CHECK (valid_until IS NULL OR valid_until >= valid_from)`, créneau libéré uniquement par clôture explicite ; cible = compte existant, non archivé, rôle actif PEDAGOGICAL_MANAGER sinon 422 `ACAD_TARGET_NOT_ELIGIBLE` ; routes `/api/v1/pedagogical-assignments` GET liste (filtres `program`/`user`/`type`/`status`/`activeOn` inclusif, tri liste blanche stricte `validFrom`/`validUntil`/`createdAt`) + GET détail + POST création + POST `{id}/close` (`reason` obligatoire, `effectiveDate` défaut aujourd'hui, `>= validFrom` sinon 400 `ACAD_ASSIGNMENT_DATE_INVALID`, persiste `validUntil`), réservées ADMIN/SUPER_ADMIN, aucun PATCH/DELETE/route nichée ; contrôle de périmètre centralisé (`AcademicScopeGuard`) sur formation + niveau + promotion + classe : listes filtrées, détail/écriture hors périmètre → 403 `ACAD_FORBIDDEN` ; accès global = `ROLE_ADMIN`/`ROLE_SUPER_ADMIN`/`ROLE_SCHOOL_ADMINISTRATION` (déduit des autorités Spring Security), écriture ouverte au PEDAGOGICAL_MANAGER dans son périmètre via `SCOPED_WRITE_ROLES`, création de formation toujours ADMIN/SUPER_ADMIN, AcademicYear inchangé ; DTO sans id SQL ; audit `PEDAGOGICAL_ASSIGNMENT_CREATED`/`_CLOSED` catégorie ACADEMIC). |
| Référentiels pédagogiques (formation/niveau/année/promotion/classe) | TESTED (module `academic`, migration V5 ; CRUD + archivage/restauration des 5 entités, aucun DELETE physique ; hiérarchie formation → promotion → classe/groupe ; routes en public_id sous `/api/v1/academic-years`, `/api/v1/programs`, `/api/v1/programs/{id}/levels` + `/api/v1/program-levels/{id}`, `/api/v1/promotions`, `/api/v1/class-groups` ; pagination max 100 + tri liste blanche ; unicités academic_year.code / program.code / (program,code) / (program,academicYear,code) / (promotion,code) ; période année (end>start), période promotion incluse dans l'année, program_level d'une classe = même formation que sa promotion, refus parent archivé, archivage bloqué si enfants actifs, code + rattachements immuables ; `class_group.site_id` = valeur technique via port public `organization.SiteDirectory` (aucun import de `organization.internal`, aucune relation JPA inter-module) ; `@PreAuthorize` lecture ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION/PEDAGOGICAL_MANAGER, écriture ADMIN/SUPER_ADMIN ; DTO sans id SQL ; audit `ACADEMIC_YEAR_*`/`PROGRAM_*`/`PROGRAM_LEVEL_*`/`PROMOTION_*`/`CLASS_GROUP_*` catégorie ACADEMIC). Inscriptions, apprenants, formateurs, matières : hors périmètre de ce lot. |
| Référentiel organisationnel (site/bâtiment/salle/plage réseau) | TESTED (module `organization`, migration V4 ; CRUD + archivage/restauration site·bâtiment·salle, création + activation/désactivation plages réseau, aucun DELETE physique ; routes en public_id sous `/api/v1/sites`, `/api/v1/buildings/{id}`, `/api/v1/rooms/{id}`, `/api/v1/network-ranges/{id}` ; pagination max 100 + tri liste blanche ; unicités site.code / (site,code) / (site,cidr) active ; refus parent archivé, room.site=building.site, archivage bloqué si enfants actifs, code immuable ; ZoneId + ISO 3166-1 + CIDR IPv4/IPv6 validés ; `@PreAuthorize` lecture ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION/PEDAGOGICAL_MANAGER, écriture ADMIN/SUPER_ADMIN, plages réseau SUPER_ADMIN pour toute opération ; DTO sans id SQL ; audit `SITE_*`/`BUILDING_*`/`ROOM_*`/`SITE_NETWORK_RANGE_*` catégorie ORGANIZATION ; port public `identity.CurrentUserResolver` pour l'auteur des écritures) |
| Inscriptions historiques (student_profile / enrollment) | TESTED (module `enrollment`, migration V7 ; `StudentProfile` (`user_id` valeur technique via `identity.UserDirectory`, unique ; `student_number` unique ; statut ACTIVE/ARCHIVED) et `Enrollment` (rattachements `class_group_id`/`academic_year_id` = valeurs techniques via nouveau port `academic.ClassGroupDirectory` ; `previous_enrollment_id` auto-référence ; `enrollment_source` MANUAL/CLASS_TRANSFER ; statuts docs/04 §13.1) ; **au plus une inscription ACTIVE par apprenant et par année scolaire** (RG-012 / docs/04 §13.3) : pré-contrôle applicatif + contrainte SQL `uq_enrollment_active_per_year` (colonnes générées) + isolation de la collision concurrente (bean `EnrollmentPersister` `@Transactional(REQUIRES_NEW)` pour `create`/`enroll` — retraduction hors transaction en échec ; `EnrollmentExceptionHandler` pour `transfer` — dont l'INSERT doit voir la clôture dans la même transaction), retraduite en 409 ciblé, jamais 500 ; changement de classe `POST …/{id}/transfer` clôturant l'ancienne inscription en TRANSFERRED (`end_date` inclusif, historique conservé — AC-006) et créant la nouvelle liée débutant `end_date` + 1 jour (aucun chevauchement de période) ; clôture `POST …/{id}/close` (COMPLETED/WITHDRAWN, motif obligatoire) ; `CHECK (end_date IS NULL OR end_date >= start_date)` ; routes en public_id sous `/api/v1/student-profiles` et `/api/v1/enrollments` (GET liste filtres + tri liste blanche + pagination max 100, GET détail, POST) réservées ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION (PEDAGOGICAL_MANAGER exclu tant qu'un port de périmètre pédagogique public n'existe pas ; TEACHER/STUDENT sans accès), aucun PATCH/DELETE/route nichée ; horloge `java.time.Clock` injectée ; DTO sans id SQL ; audit `STUDENT_PROFILE_CREATED`/`ENROLLMENT_CREATED`/`_TRANSFERRED`/`_CLOSED` catégorie `ENROLLMENT`. Import CSV, rythmes d'alternance, apprenants provisoires : hors périmètre de ce lot. |
| Import apprenants | TODO |
| Import planning | TODO |
| Séances | TODO |
| Émargement | TODO |
| Rapports | TODO |
| Audit | TESTED (persistance `audit_event` + écriture depuis flux métier réels : connexion réussie/refusée, émission d'invitation, activation de compte, suspension/réactivation/archivage d'un compte, attribution/retrait d'un rôle, changements du référentiel organisationnel — catégorie `ORGANIZATION` — et changements du référentiel académique — année/formation/niveau/promotion/classe **et affectations de responsable pédagogique (`PEDAGOGICAL_ASSIGNMENT_CREATED`/`_CLOSED`)**, catégorie `ACADEMIC` — **et changements du module inscriptions — `STUDENT_PROFILE_CREATED` / `ENROLLMENT_CREATED` / `_TRANSFERRED` / `_CLOSED`, catégorie `ENROLLMENT`** — jamais de jeton, de donnée sensible ni d'IP ; pour les actions d'administration, le compte/la ressource concernée est portée par `resource_public_id`, l'acteur par `actor_user_id`) |
| FastAPI | TODO |
| MQTT | TODO |
| Raspberry Pi | TODO |
| WebAuthn | TODO |
| CI (GitHub Actions) | IMPLEMENTED (`.github/workflows/backend-ci.yml` : déclenché sur PR vers `main` et push sur `main` ; job unique `ubuntu-latest`, `permissions: contents: read`, `timeout-minutes: 20`, concurrence avec annulation des exécutions obsolètes ; Java 21 Temurin + cache Maven ; services `mysql:8.4` et `redis:7.4-alpine` (mot de passe via `command: redis-server --requirepass`) avec identifiants dédiés CI non sensibles ; exécute `./mvnw --batch-mode test` depuis `backend/` ; aucun usage de `.env`, aucun SMTP réel. Non encore exécuté sur GitHub — statut à confirmer au premier run) |
| Staging | TODO |

## Prochaine priorité

```text
Les référentiels organisationnel (module `organization`, V4), académique
minimal (module `academic`, V5), le périmètre pédagogique (module
`academic`, V6) et les inscriptions historiques (module `enrollment`,
V7 : `student_profile` + `enrollment` + changement de classe conservant
l'historique) sont en place. Le socle front-end Angular est fusionné sur
`main` (PR #11, commit `6fa341f`) : connexion + tableau de bord (état de
session local) + gardes de route par rôle. Le parcours public
d'activation de compte (`/activation`,
`GET/POST /api/v1/account-invitations/validate|activate`) est fusionné
sur `main` (PR #12, commit `2ff7aa8`). Le sélecteur de contexte de rôle
(docs/02 §6.1, EF-AUTH-003) est implémenté sur
`feature/frontend-role-context` (PR ouverte, non fusionnée).
Prochaines étapes :
- front-end : premiers écrans de liste (formations, apprenants) une fois
  la PR de contexte de rôle fusionnée ;
- rythmes d'alternance minimaux (T-J1-033 / US-060 à 062) : module
  `alternation` — `work_study_pattern`, `class_work_study_pattern`,
  `student_schedule_exception` (docs/04 §14) ;
- import CSV des apprenants (T-J2-001 à 004 / US-050, US-051) : simulation
  puis confirmation, s'appuyant sur `student-profiles` et `enrollments` ;
- créer un port de périmètre pédagogique public afin d'ouvrir la gestion
  des profils / inscriptions au PEDAGOGICAL_MANAGER dans son périmètre
  (aujourd'hui ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION uniquement) ;
- exposer éventuellement une route de consultation de ses propres
  affectations pour le PEDAGOGICAL_MANAGER ;
- affectation d'un responsable pédagogique principal à une formation au
  fil de l'eau depuis les écrans d'administration.
/auth/logout et la révocation de session restent à évaluer (jeton
stateless sans état serveur pour l'instant).

Dettes techniques à traiter ultérieurement :
- file persistante + reprise garantie pour les emails d'activation
  (actuellement envoi synchrone après commit, échec seulement journalisé) ;
- purge / expiration explicite des invitations `PENDING` périmées ;
- création de comptes `PENDING_ACTIVATION` par API (l'émission cible
  aujourd'hui un compte déjà existant, créé par fixture ou futur import ;
  vaut aussi pour la création d'un `student_profile`, qui exige un
  compte `STUDENT` préexistant) ;
- consultation par l'apprenant de son propre profil et de son historique
  d'inscriptions (routes réservées à l'administration dans ce lot) ;
- génération locale d'un numéro étudiant `ESIC-{ANNEE}-{SEQUENCE}` quand
  il est absent (docs/04 §3.5 ; aujourd'hui le numéro est obligatoire
  dans la requête) ;
- suite de tests : chaque classe `@SpringBootTest` porte sa propre
  `@TestConfiguration` imbriquée → un contexte Spring (et un pool
  HikariCP) mis en cache par classe ; le pool de test est plafonné
  (`application-test.yml`, `maximum-pool-size: 6`) pour rester sous
  `max_connections` de MySQL. Une `@TestConfiguration` partagée ou des
  Testcontainers dédiés seraient préférables à terme ;
- incohérences docs à corriger : docs/03 §6.4 (dépendances du module
  `academic` : ajouter `organization` et la publication vers `audit` ;
  ajouter le module `enrollment` → `identity`, `academic`, publication
  vers `audit`, et le port `academic.ClassGroupDirectory`) ;
  docs/04 §12.3 (colonnes techniques de `program_level`).
```

## Blocages

```text
Aucun blocage technique vérifié pour le moment.
```

## Commandes de démarrage

```text
docker compose config    # valider la syntaxe et les variables
docker compose up -d     # démarrer mysql, redis, mailpit, mosquitto
docker compose ps        # vérifier l'état des conteneurs
```

Infrastructure vérifiée le 28 août 2026 : MySQL et Redis en état
`healthy` (authentification testée), Mailpit `healthy`, Mosquitto
démarré (pas de healthcheck configuré). Nécessite un fichier `.env`
local non versionné (voir `.env.example`).

Front-end (dossier `frontend/`, Node.js 24) :

```text
cd frontend
npm ci
npm start                    # ng serve — http://localhost:4200, proxifie /api vers :8080
npm test -- --watch=false    # Vitest + jsdom (builder @angular/build:unit-test)
npm run build                # build de production dans dist/
npm run lint                 # angular-eslint
```

```text
cd backend
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
set -a && source ../.env && set +a   # variables MySQL/Redis requises
./mvnw test                          # exécute EsicConnectApplicationTests + ModularityTests
```

Socle back-end vérifié le 28 août 2026 (Java 21.0.12.1, Maven 3.9.16,
Spring Boot 3.5.16, Spring Modulith 1.4.12) : `./mvnw test` →
`BUILD SUCCESS`, 2 tests exécutés (chargement du contexte + vérification
de la structure modulaire), 0 échec. Aucune route métier, aucune entité
JPA, aucune authentification réelle. Sécurité : seules
`/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**` et
`/swagger-ui.html` sont ouvertes ; toutes les autres routes exigent une
authentification (non implémentée). Travaux réalisés sur la branche
`feature/spring-boot-foundation`, non fusionnée et non committée (fusionnée
depuis sur `main` via la PR #1).

Socle identité + audit vérifié le 28 août 2026, sur la branche
`feature/identity-foundation` (non fusionnée, non committée) : `./mvnw test`
(mêmes commandes ci-dessus) → `BUILD SUCCESS`, 9 tests exécutés (rôles
seedés, unicité email, unicité public_id, suppression d'un utilisateur
référencé refusée par `RESTRICT`, unicité d'une affectation active +
réattribution possible après clôture, acteur d'audit mis à `NULL` après
suppression du compte avec conservation du snapshot, chargement du
contexte, structure modulaire), 0 échec, exécuté deux fois pour
vérifier la stabilité. Migrations Flyway `V1` (tables `user_account`,
`role`, `user_role`, `audit_event`) et `V2` (6 rôles système) appliquées
sur la base locale. Aucune donnée de démonstration, aucun compte
administrateur, aucun JWT, aucun contrôleur d'authentification.

Authentification vérifiée le 28 août 2026, sur la branche
`feature/authentication-foundation` (non fusionnée, non committée) :
`./mvnw test` (mêmes commandes ci-dessus) → `BUILD SUCCESS`, 24 tests
exécutés (15 nouveaux : adaptateur `UserDetailsService`, service
d'authentification unitaire — y compris qu'un échec de journalisation
d'audit ne modifie ni ne masque jamais le résultat réel —, connexion
réussie de bout en bout avec jeton décodable et audit committé, réponse
publique strictement identique pour email inconnu/mauvais mot de
passe/compte non actif, aucune fuite de l'email brut d'un compte
inconnu dans l'audit, rejet sans jeton d'une route protégée, refus d'un
jeton correctement signé mais à l'émetteur (`iss`) incorrect (401 nu,
sans détail de validation exposé), routes `/actuator/health` et
`/v3/api-docs` toujours publiques), 0 échec, exécuté deux fois pour
vérifier la stabilité. Aucune migration `V3` : le schéma `V1`
suffisait. `JwtDecoder` vérifie désormais explicitement l'émetteur
(`JwtValidators.createDefaultWithIssuer`) et `AuthenticationService`
refuse de démarrer si `JWT_ACCESS_TOKEN_TTL_SECONDS` n'est pas
strictement positif. Un `AuthenticationEntryPoint` dédié a dû remplacer
celui par défaut de Resource Server, qui exposait le motif technique du
refus (ex. « the iss claim is not valid ») dans l'en-tête
`WWW-Authenticate` — désormais un 401 nu. **`JWT_SECRET` est requis**
(≥ 32 octets, aucune valeur par défaut) : à ajouter manuellement à
votre `.env` local
(voir `.env.example`) pour lancer l'application hors des tests — les
tests utilisent un secret dédié dans `application-test.yml`, jamais
`.env`.

Invitation / activation vérifiée le 28 août 2026, sur la branche
`feature/account-invitation` (non fusionnée, non committée) : `./mvnw test`
(mêmes commandes ci-dessus) → `BUILD SUCCESS`, **50 tests** exécutés
(26 nouveaux), 0 échec, exécuté deux fois pour vérifier la stabilité.
Nouveaux tests : `InvitationTokenServiceTests` (SecureRandom ≥ 32 o,
Base64URL sans padding, SHA-256 hex déterministe + vecteur
`SHA-256("abc")`), `AccountInvitationServiceTests` (TTL non positif
refusé au démarrage, émission limitée à `PENDING_ACTIVATION`, rôle
inconnu/inactif refusé, révocation des invitations PENDING antérieures
avec `flush` avant insert, empreinte stockée jamais égale au jeton,
activation encodant le mot de passe, jetons inconnu/expiré/accepté →
même erreur générique, `validate` = booléen seul),
`InvitationEmailListenerTests` (transmission au mailer, échec avalé sans
propagation), `AccountInvitationIntegrationTests` (émission protégée →
email capturé par un mailer enregistreur → `validate` public générique →
activation → connexion avec le nouveau mot de passe → jeton à usage
unique refusé en 400 `INVITATION_INVALID` → audit
`ACCOUNT_INVITATION_ISSUED`/`ACCOUNT_ACTIVATED` ; réémission révoquant le
jeton précédent), `AccountInvitationSecurityTests` (émission : 401 sans
jeton, 403 rôle `STUDENT` ; `validate` public : uniquement `{"valid":
bool}`, réponse identique pour tout jeton invalide ; `activate` jeton
inconnu → 400 générique sans fuite).

Migration Flyway `V3` (`account_invitation` : `public_id`, `version`,
`token_hash` UNIQUE, `active_invitation_key` générée → une seule
invitation `PENDING` par compte, FK `RESTRICT`) appliquée sur la base
locale (schéma en version 3). `pom.xml` : ajout de
`spring-boot-starter-mail`. `SecurityConfig` : `@EnableMethodSecurity` +
`/api/v1/account-invitations/validate` et `/activate` publics.
`GlobalExceptionHandler` : `AccessDeniedException` → 403 neutre (sinon
masqué en 500 par le catch-all). Module `shared` déclaré `OPEN`
(noyau technique : `ApiError` consommé hors module). Nouveau module
`notification`. `management.health.mail.enabled=false` **uniquement**
dans les profils `local` et `test` (pas globalement). Nouvelles
variables dans `.env.example` (`MAIL_HOST`, `MAIL_PORT`, `APP_MAIL_FROM`,
`APP_ACTIVATION_BASE_URL`, `INVITATION_TOKEN_TTL`) ; `.env`, `compose.yaml`,
`V1` et `V2` inchangés. TTL par défaut `P30D`, configurable via
`INVITATION_TOKEN_TTL`, refus de démarrage si ≤ 0.

Gestion des comptes / rôles vérifiée le 28 août 2026, sur la branche
`feature/user-management` (non fusionnée, non committée) : `./mvnw test`
(mêmes commandes ci-dessus, `JAVA_HOME` OpenJDK 21) → `BUILD SUCCESS`,
**98 tests** exécutés (48 nouveaux), 0 échec, exécuté deux fois pour
vérifier la stabilité (dont `ModularityTests` : frontières de modules
respectées). Nouveaux tests : `UserManagementServiceTests` (35 —
transitions ACTIVE↔SUSPENDED, un compte SUSPENDED ne peut pas se
réactiver lui-même, archivage clôturant les rôles actifs sans
suppression, protection d'un compte `SUPER_ADMIN` (y compris pour une
réactivation par `SCHOOL_ADMINISTRATION` et pour l'attribution/retrait
de *n'importe quel* rôle par un `ADMIN`), `SUPER_ADMIN` interdit de
s'auto-suspendre / s'auto-archiver / retirer son propre rôle, dernier
rôle actif protégé, rôle inconnu, tri hors liste blanche, direction de
tri invalide (`email,wrong`) refusée au lieu d'un ASC silencieux, filtre
invalide, taille de page bornée à 100 / défaut 20),
`UserManagementIntegrationTests` (5 — liste paginée/filtrée/triée sans
`id` ni `password_hash`, détail par `public_id` + 404, suspension →
connexion refusée → réactivation → connexion rétablie, archivage
bloquant la connexion et refusant la réactivation (409), attribution
puis retrait de rôle conservant l'historique, dernier rôle protégé,
audit écrit), `UserManagementSecurityTests` (8 — 401 anonyme, 403
`STUDENT`/`TEACHER`, `SCHOOL_ADMINISTRATION` peut suspendre mais pas
archiver ni gérer les rôles, `ADMIN` ne peut pas archiver un
`SUPER_ADMIN` ni attribuer le rôle `SUPER_ADMIN`, auto-suspension
refusée (409), `public_id` inconnu → 404).

Aucune migration `V4` : `user_account` (`suspended_at`/`suspended_by_id`/
`suspension_reason`/`archived_at`/`updated_by_id`) et `user_role`
(`valid_until`/`active`/`assigned_by_id`/`assignment_reason`) portaient
déjà les colonnes nécessaires depuis `V1`. Fichiers back-end ajoutés
dans `identity.internal` (`UserAccountController`, `UserManagementService`,
`UserAdminSpecifications`, `UserManagementException(+Handler)`, DTO
`UserSummaryResponse`/`UserDetailResponse`/`RoleAssignmentResponse`/
`PageResponse`, requêtes `AccountActionRequest`/`AssignRoleRequest`) ;
modifiés : `UserAccount` (méthodes `suspend`/`reactivate`/`archive` +
getters), `UserRole` (`close` + getters), `UserAccountRepository`
(`JpaSpecificationExecutor`), `UserRoleRepository` (requêtes de rôles
actifs), `identity.AccountLifecycleAction` (+5 actions),
`audit.internal.AuditEvent` (`setResourcePublicId`),
`AccountLifecycleAuditListener`. `.env`, `compose.yaml`, `V1`–`V3`,
`SecurityConfig` et le workflow CI inchangés. Aucun commit, aucun push.

Référentiel organisationnel vérifié le 28 août 2026, sur la branche
`feature/organization-foundation` (depuis fusionnée sur main via PR #7) :
`./mvnw test` (mêmes commandes ci-dessus, `JAVA_HOME` OpenJDK 21) →
`BUILD SUCCESS`, **164 tests** exécutés (66 nouveaux), 0 échec, exécuté
deux fois pour vérifier la stabilité (dont `ModularityTests` : nouveau
module `organization`, frontières respectées, aucun cycle). Nouveaux
tests : `CidrValidatorTests` (32 — littéraux IPv4/IPv6 valides, préfixes
hors bornes, octets > 255, noms d'hôte refusés, absence de résolution
DNS), `OrganizationServiceTests` (11, Mockito — fuseau inconnu, code
pays non ISO, code dupliqué, archivage bloqué par enfants actifs, tri
hors liste blanche / direction invalide, bâtiment d'un autre site,
création sous site archivé, CIDR invalide, doublon de plage active,
publication d'événement), `OrganizationConstraintsTests` (8, `@DataJpaTest`
— unicité `site.code`, `(site,code)` bâtiment libre entre sites, unicité
`(site,code)` salle, `public_id` unique, FK `RESTRICT` site→bâtiment et
bâtiment→salle, unicité de la plage réseau active, créneau libéré après
désactivation), `OrganizationIntegrationTests` (8, `@SpringBootTest`
`RANDOM_PORT` — cycle complet site/bâtiment/salle + archivage en cascade
contrôlée + restauration + audit `SITE_*`/`BUILDING_*`/`ROOM_*`, DTO sans
`id`/`siteId`/`createdById`, archivage refusé avec enfants actifs,
création sous parent archivé refusée, `room.site` ≠ `building.site`
refusé, unicité/fuseau/pays validés, pagination bornée à 100, tri
inconnu 400, CRUD plage réseau IPv4 + IPv6 par SUPER_ADMIN avec audit),
`OrganizationSecurityTests` (7 — 401 anonyme, 403 `STUDENT`/`TEACHER`,
`PEDAGOGICAL_MANAGER` lit mais n'écrit pas, `SCHOOL_ADMINISTRATION` lit
les sites, plages réseau réservées à `SUPER_ADMIN` y compris en lecture,
`ADMIN` et `SCHOOL_ADMINISTRATION` reçoivent 403).

Migration Flyway `V4` (`site`, `building`, `room`, `site_network_range` :
`public_id` unique, FK `RESTRICT` vers `user_account` pour les colonnes
auteur et vers le parent hiérarchique, `version`, colonnes générées
`active_range_key`, `CHECK (capacity > 0)`) appliquée sur la base locale
(schéma en version 4). Fichiers back-end ajoutés : nouveau module
`com.esic.connect.organization` (package racine : `OrganizationChangeEvent`
+ enums `OrganizationResourceType`/`OrganizationChangeAction` ;
`organization.internal` : entités `Site`/`Building`/`Room`/
`SiteNetworkRange` + `OrganizationStatus`, 4 repositories, 4 services,
4 contrôleurs, DTO de réponse et records de requête, `CidrValidator`,
`SiteFieldValidator`, `OrganizationQuerySupport`, `OrganizationSpecifications`,
`OrganizationChangePublisher`, `OrganizationException(+Handler)`,
`PageResponse` local) ; `identity.CurrentUserResolver` (port public) +
`identity.internal.DefaultCurrentUserResolver` (implémentation) ;
`audit.internal.OrganizationAuditListener`. `.env`, `compose.yaml`,
`V1`–`V3`, `SecurityConfig`, `pom.xml` et le workflow CI inchangés.
Aucun site fictif ni donnée métier insérés. Aucun commit, aucun push.

Référentiel académique minimal vérifié le 29 août 2026, sur la branche
`feature/academic-foundation` (depuis fusionnée sur main via PR #8),
après la passe corrective : `./mvnw clean test` (`JAVA_HOME` OpenJDK 21,
`set -a && source ../.env`) → `BUILD SUCCESS`, **214 tests** exécutés
(50 nouveaux : 32 du premier lot + 18 correctifs), 0 échec, exécuté trois
fois pour vérifier la stabilité (dont `ModularityTests` : nouveau module
`academic`, frontières respectées, aucun cycle). Tests académiques :
`AcademicServiceTests` (22, Mockito — période inversée, code dupliqué, tri
hors liste blanche / direction invalide, archivage bloqué par enfants
actifs (formation via niveau **et** via promotion seule, niveau via
classe, promotion via classe), type de formation inconnu, période de
promotion hors année, promotion sous formation archivée, niveau d'une
autre formation, site inconnu, modification d'année excluant une
promotion existante, restauration de promotion refusée si formation ou
année archivée / réussie et auditée si parents actifs, restauration de
classe refusée si année ou formation-du-programme archivée, si site
archivé, si site absent), `AcademicConstraintsTests` (13, `@DataJpaTest`
— unicités `academic_year.code` / `program.code` / `public_id` /
`(program,code)` / `(program,academicYear,code)` / `(promotion,code)`,
FK `RESTRICT` formation→promotion, promotion→classe, année→promotion,
niveau→classe et site→classe (DELETE natif du site refusé), `CHECK`
période `academic_year` et `CHECK` capacité `class_group` ; le site
requis par les FK est inséré en SQL natif),
`AcademicIntegrationTests` (9, `@SpringBootTest` `RANDOM_PORT` — cycle
complet année→formation→niveau→promotion→classe rattachée à un site,
DTO sans `id`/`siteId`/`programId`, archivage en cascade contrôlée +
restauration complète (année, formation, niveau, promotion, classe) +
audit `…_RESTORED` inclus, archivage refusé avec enfants actifs (409),
niveau d'une autre formation refusé (400), période de promotion hors
année refusée (400), période d'année inversée refusée (400), code de
formation dupliqué (409), création de classe sous promotion archivée
refusée (409), restauration de classe refusée sous année archivée (409),
modification d'année excluant une promotion existante refusée (409) puis
acceptée si la période l'englobe, pagination bornée à 100, tri inconnu
400), `AcademicSecurityTests` (6 — 401 anonyme, 403 `STUDENT`/`TEACHER`,
`SCHOOL_ADMINISTRATION` et `PEDAGOGICAL_MANAGER` lisent mais n'écrivent
pas (403), `ADMIN` crée (201)).

Passe corrective (module `academic` + tests uniquement) :
`ClassGroupService.restore` vérifie désormais toute la chaîne de
rattachement (promotion, sa formation, son année, le niveau, la formation
du niveau, le site présent et actif) et revérifie l'invariant
niveau↔formation ; `AcademicYearService.update` refuse une période qui
exclurait une promotion existante à période renseignée (nouveau code
`ACAD_ACADEMIC_YEAR_PERIOD_CONFLICT`, requêtes `exists` ciblées) ;
`ClassGroupService.resolveSitePublicId` lève une erreur métier contrôlée
au lieu de renvoyer `null` silencieusement, sans exposer d'identifiant
SQL.

Migration Flyway `V5` (`academic_year`, `program`, `program_level`,
`promotion`, `class_group` : `public_id` unique, FK `RESTRICT` vers
`user_account` pour les colonnes auteur et vers le parent hiérarchique,
FK `RESTRICT` `class_group.site_id` → `site.id`, `version`,
`CHECK (end_date > start_date)` année, `CHECK (end_date IS NULL OR
start_date IS NULL OR end_date > start_date)` promotion,
`CHECK (capacity IS NULL OR capacity > 0)`) appliquée sur la base locale
(schéma en version 5). Fichiers back-end ajoutés : nouveau module
`com.esic.connect.academic` (package racine : `AcademicChangeEvent` +
enums `AcademicResourceType`/`AcademicChangeAction` ; `academic.internal` :
entités `AcademicYear`/`Program`/`ProgramLevel`/`Promotion`/`ClassGroup`
+ `AcademicStatus`/`ProgramType`, 5 repositories, 5 services,
5 contrôleurs, DTO de réponse et records de requête, `AcademicWeb`,
`AcademicQuerySupport`, `AcademicSpecifications`, `AcademicChangePublisher`,
`AcademicException(+Handler)`, `PageResponse` local) ;
`organization.SiteDirectory` (port public) +
`organization.internal.DefaultSiteDirectory` (implémentation) ;
`audit.internal.AcademicAuditListener`. `.env`, `compose.yaml`, `V1`–`V4`,
`SecurityConfig`, `pom.xml` et le workflow CI inchangés. Aucune formation,
promotion ni classe fictive insérée. Aucun commit, aucun push.

Périmètre pédagogique vérifié le 29 août 2026, sur la branche
`feature/pedagogical-scope` (depuis fusionnée sur main via PR #9), après
**deux** passes correctives (revues « NOT READY TO COMMIT ») — la seconde portant
sur l'isolation transactionnelle de la collision `PRIMARY_MANAGER` et
l'injection de `Clock` :
`./mvnw clean test` (`JAVA_HOME` OpenJDK 21,
`set -a && source ../.env`) → `BUILD SUCCESS`, **263 tests** exécutés,
0 échec, 0 erreur, 0 ignoré, **exécuté deux fois** de suite après tous
les changements code + docs (résultats identiques), dont `ModularityTests`
vert (ports `identity.UserDirectory`, bean `shared.config.ClockConfig`,
table `pedagogical_assignment` et bean `AssignmentPersister` dans le
module `academic` — frontières respectées, aucun cycle).

État Flyway local : `V6` inchangée depuis la première passe corrective
(déjà réécrite + réappliquée alors ; table `pedagogical_assignment` +
ligne d'historique `version = "6"` supprimées puis recréées). `V1`–`V5`
jamais touchées ; aucun `flyway clean`. Cette passe-ci ne modifie pas de
migration.

Isolation de la collision concurrente : `PedagogicalAssignmentService`
n'est plus `@Transactional` au niveau classe ; `create` n'ouvre aucune
transaction et délègue l'`INSERT` à `AssignmentPersister.persist`
(`@Transactional(propagation = REQUIRES_NEW)`). Une violation de
`uq_pedagogical_assignment_active_primary` annule cette transaction
interne, puis `create` reçoit la `DataIntegrityViolationException` hors
de toute transaction en échec et ne la mappe sur
`ACAD_PRIMARY_MANAGER_EXISTS` (409) que si le nom de contrainte / le
message SQL désigne bien cette contrainte-là ; toute autre violation
(FK, `CHECK`, `NOT NULL`, longueur, `uq_pedagogical_assignment_public_id`)
est relancée intacte. `close` reste `@Transactional`.

Horloge : bean `java.time.Clock` (`shared.config.ClockConfig`,
`@ConditionalOnMissingBean`) injecté dans `AcademicScopeGuard` et
`PedagogicalAssignmentService` ; tous les `LocalDate.now()` deviennent
`LocalDate.now(clock)`.

Tests ajoutés / mis à jour :
`PedagogicalAssignmentServiceTests` (16, Mockito — persister mocké,
`Clock.fixed(2026-06-15)` : formation inconnue / archivée, `type`
invalide, cible non éligible → `ASSIGNMENT_TARGET_NOT_ELIGIBLE`,
`validUntil < validFrom`, deuxième PRIMARY_MANAGER refusé par
pré-contrôle, **collision `active_primary` (message SQL réaliste)
retraduite en `PRIMARY_MANAGER_ALREADY_ASSIGNED` sans publier
d'événement**, **violation FK sans objet relancée à l'identique**,
DELEGATE autorisé + `reason` trimé + `delegatedById`, `validFrom` par
défaut = date de l'horloge injectée, clôture déjà clôturée, clôture
avant `validFrom`, clôture par défaut = date de l'horloge injectée + événement,
tri hors liste blanche) ;
`AcademicScopeGuardTests` (7, Mockito + `SecurityContextHolder` +
`Clock.fixed` — accès global admin / super-admin / school-admin, cumul
manager+teacher limité avec **requêtes de périmètre datées par l'horloge
injectée**, appelant non résolu → rien de visible,
`requireProgramInScope` OK vs 403 `OUT_OF_SCOPE`, contexte anonyme non
global) ;
`PedagogicalAssignmentConstraintsTests` (11, `@DataJpaTest` — unicité
`active_primary` (autre formation acceptée), DELEGATE non limités,
créneau libéré après clôture, `CHECK` de période + validité d'un seul
jour acceptée, `public_id` unique, FK `RESTRICT` `program` /
`manager_user_id` / `delegated_by_id` via
`org.hibernate.exception.ConstraintViolationException` précise,
**`PedagogicalAssignmentService.isActivePrimaryUniqueViolation`
reconnaît une vraie exception de collision et rejette une violation
`public_id`**) ;
`PedagogicalScopeIntegrationTests` (4, `@SpringBootTest` `RANDOM_PORT` —
scope descendant sur formation / niveau / promotion / classe : listes
filtrées et 403 `ACAD_FORBIDDEN` en lecture détail comme en création /
modification / archivage-restauration hors périmètre ;
`PEDAGOGICAL_MANAGER + TEACHER` reste limité ; `PEDAGOGICAL_MANAGER +
ADMIN` est global ; `SCHOOL_ADMINISTRATION` lit globalement mais ne gère
pas les affectations (403)) ;
`PedagogicalAssignmentIntegrationTests` (11, `@SpringBootTest`
`RANDOM_PORT` — cycle create/list/close + audit
`PEDAGOGICAL_ASSIGNMENT_CREATED` / `_CLOSED` ; filtres `activeOn` (bornes
`LocalDate` inclusives : `2026-09-01` et `2026-09-30` → 1, `2026-08-31`
et `2026-10-01` → 0) et `type` ; clôture par défaut à aujourd'hui,
clôture `effectiveDate < validFrom` → 400 `ACAD_ASSIGNMENT_DATE_INVALID` ;
cible inconnue / non responsable → 422 `ACAD_TARGET_NOT_ELIGIBLE` ;
doublon PRIMARY_MANAGER → 409 `ACAD_PRIMARY_MANAGER_EXISTS` ; **deux
créations concurrentes (pool 2 threads) → exactement un 201 et un 409** ;
`type` invalide → 400 ; tri hors liste blanche → 400 `ACAD_INVALID_SORT` ;
matrice 401 anonyme / 403 STUDENT·TEACHER·SCHOOL_ADMINISTRATION·
PEDAGOGICAL_MANAGER / 200 ADMIN).
`AcademicServiceTests` : les quatre fabriques de services
(`Program`/`ProgramLevel`/`Promotion`/`ClassGroup`) reçoivent un mock
`AcademicScopeGuard` ; les appels `ProgramService.archive` reviennent à
la signature à trois arguments.

Migration Flyway `V6` réécrite (`pedagogical_assignment` : `public_id`
unique, `valid_from`/`valid_until` en `DATE`, `reason` /
`close_reason` / `delegated_by_id`, FK `RESTRICT` vers `program.id` et
vers `user_account.id` pour `manager_user_id`, `delegated_by_id` et les
colonnes auteur, `version`, colonne générée `active_primary_key`
(VIRTUAL) + `UNIQUE`, `CHECK (valid_until IS NULL OR valid_until >=
valid_from)`). Fichiers back-end ajoutés : `academic.internal`
(`PedagogicalAssignment`, `PedagogicalAssignmentRole`,
`PedagogicalAssignmentStatus`, `PedagogicalAssignmentRepository`,
`PedagogicalAssignmentService`, `PedagogicalAssignmentController`,
`PedagogicalAssignmentRequests`, `PedagogicalAssignmentResponse`,
`AcademicScopeGuard`, `AssignmentPersister`) ; `identity.UserDirectory`
(port public) + `identity.internal.DefaultUserDirectory` ;
`shared.config.ClockConfig` (bean `Clock`). Fichiers modifiés :
`academic.AcademicResourceType` (+`PEDAGOGICAL_ASSIGNMENT`),
`academic.AcademicChangeAction` (+`CLOSED`), `academic.package-info`,
`academic.internal` (`AcademicException`(+`Handler`) — codes alignés
`ACAD_FORBIDDEN` / `ACAD_ASSIGNMENT_NOT_FOUND` / `ACAD_TARGET_NOT_ELIGIBLE`
/ `ACAD_PRIMARY_MANAGER_EXISTS` / `ACAD_ASSIGNMENT_ALREADY_CLOSED` /
`ACAD_ASSIGNMENT_DATE_INVALID` ; `AcademicSpecifications` — specs `IN`
de périmètre + `assignmentHasType` / `assignmentActiveOn` ; `AcademicWeb`
— `SCOPED_WRITE_ROLES` / `ASSIGNMENT_ROLES` ; `AcademicScopeGuard` et
`PedagogicalAssignmentService` — `Clock` injectée + isolation de
l'`INSERT` via `AssignmentPersister` ; les cinq services académiques
(hors `AcademicYear`) et leurs contrôleurs — branchement du
`AcademicScopeGuard`). `.env`, `compose.yaml`, `V1`–`V6`,
`SecurityConfig`, `pom.xml`, `docs/03`, `docs/04` et le workflow CI
inchangés. Aucune affectation fictive insérée. Aucun commit, aucun push.

Inscriptions historiques vérifiées le 29 août 2026, sur la branche
`feature/enrollment-history` — depuis **fusionnée sur `main` via PR #10**
(commit `495c2bf`). Après la passe corrective de revue de PR #10 (sémantique
de date du changement de classe + isolation transactionnelle des
collisions concurrentes) : `./mvnw clean test` (`JAVA_HOME` OpenJDK 21,
`set -a && source ../.env && set +a`) → `BUILD SUCCESS`, **320 tests**
exécutés (57 nouveaux ; 263 → 320), 0 échec, 0 erreur, 0 ignoré,
**exécuté deux fois** de suite après tous les changements code + docs
(résultats identiques), dont `ModularityTests` vert (nouveau module
`enrollment` → `identity`, `academic`, `shared` ; publie
`EnrollmentChangeEvent` vers `audit` ; nouveau port
`academic.ClassGroupDirectory` ; frontières respectées, aucun cycle).

État Flyway local : nouvelle migration `V7`
(`student_profile` / `enrollment`), appliquée et vérifiée ; schéma en
version 7. `V1`–`V6` jamais touchées ; aucun `flyway clean`.

`enrollment` — deux entités : `StudentProfile` (`user_id` valeur
technique, unique ; `student_number` unique ; `birth_date`,
`work_study`, `company_name` ; statut ACTIVE/ARCHIVED) et `Enrollment`
(`student_profile` intra-module ; `class_group_id` / `academic_year_id`
valeurs techniques via `ClassGroupDirectory` ; `previous_enrollment_id`
auto-référence FK `RESTRICT` ; `start_date`/`end_date` en `LocalDate`,
`CHECK (end_date IS NULL OR end_date >= start_date)` ;
`enrollment_source` MANUAL/CLASS_TRANSFER ; statuts docs/04 §13.1).
Unicité d'une inscription ACTIVE par (apprenant, année) : deux colonnes
générées `VIRTUAL` (`active_student_key` / `active_year_key`, valorisées
seulement pour une ligne ACTIVE) + `UNIQUE (active_student_key,
active_year_key)` ; pré-contrôle applicatif `ENR_ACTIVE_ENROLLMENT_EXISTS`
(409). Isolation des collisions concurrentes :
- `StudentProfileService.create` et `EnrollmentService.enroll` ne sont
  **pas** `@Transactional` ; l'INSERT est isolé dans le bean proxifié
  `EnrollmentPersister` (`@Transactional(REQUIRES_NEW)`, même approche
  que `academic.internal.AssignmentPersister`). La
  `DataIntegrityViolationException` est reçue **hors** de toute
  transaction en échec et retraduite en 409 sur place, uniquement pour
  `uq_student_profile_user` / `uq_student_profile_student_number`
  (profil) ou `uq_enrollment_active_per_year` (inscription) ; toute
  autre violation d'intégrité est relancée telle quelle. Jamais de
  `catch (Exception)`.
- `EnrollmentService.transfer` reste `@Transactional` : l'INSERT de la
  nouvelle inscription doit voir, dans la même transaction, le créneau
  libéré par la clôture ; il ne peut donc pas capter la collision
  localement (transaction déjà rollback-only). La course résiduelle est
  retraduite après l'annulation faite par le proxy, par
  `EnrollmentExceptionHandler`, en 409 ciblé sur la seule contrainte
  `uq_enrollment_active_per_year` ; toute autre violation relancée
  (500 via le gestionnaire global).

Changement de classe (`transfer`) : `@Transactional` unique — clôture de
l'inscription courante en TRANSFERRED (`end_date` = date effective,
borne **inclusive**, ≥ `start_date`), `saveAndFlush` de l'UPDATE d'abord
(colonnes générées → NULL), puis création de la nouvelle inscription
ACTIVE avec `start_date` = date effective **+ 1 jour** (bornes
inclusives → aucun chevauchement de période ; docs/04 §13.2 ne fixe pas
de `start_date`, la non-superposition découle des bornes inclusives et
de l'unicité d'une inscription active §13.3) — `enrollment_source` =
CLASS_TRANSFER, `previous_enrollment_id`, `change_reason` ; vers une
autre année scolaire, contrôle explicite d'absence d'inscription ACTIVE
avant écriture. `close` : COMPLETED / WITHDRAWN (`@Pattern` + garde
service), motif obligatoire, `effectiveDate` par défaut =
`LocalDate.now(clock)` ≥ `start_date`.

Nouveau port public `academic.ClassGroupDirectory` (impl
`academic.internal.DefaultClassGroupDirectory`, `@Component` confiné) :
`ClassGroupRef(internalId, publicId, code, programPublicId, programCode,
academicYearInternalId, academicYearPublicId, academicYearCode,
openForEnrollment)` — `openForEnrollment` vrai seulement si la classe,
sa promotion, sa formation et son année scolaire sont toutes ACTIVE
(sinon inscription refusée en 409 `ENR_ARCHIVED_PARENT`). N'expose ni
`ClassGroup`, ni repository.

Horloge : `EnrollmentService` reçoit le bean `java.time.Clock`
(`shared.config.ClockConfig`) — `start_date` et `effectiveDate` par
défaut lus dessus, testables avec `Clock.fixed(...)`.

Config test : `application-test.yml` plafonne le pool HikariCP
(`spring.datasource.hikari.maximum-pool-size: 6`, `minimum-idle: 1`).
Motif : chaque classe `@SpringBootTest` déclare sa propre
`@TestConfiguration` imbriquée → Spring met en cache un contexte (et un
pool) par classe ; avec le pool par défaut (10) et 16 classes
`@SpringBootTest`, MySQL 8 (`max_connections` = 151 par défaut, y compris
en CI) était saturé (« Too many connections »). Aucun test métier
existant modifié.

Tests ajoutés (57) :
`EnrollmentServiceTests` (20, Mockito, `Clock.fixed(2026-06-15)` :
profil inconnu / archivé, classe inconnue / chaîne archivée, unicité
année (pré-contrôle), `start_date` par défaut = horloge et fournie,
**collision `uq_enrollment_active_per_year` du persister retraduite en
409** et **violation d'intégrité sans objet relancée à l'identique**,
`transfer` sur inscription non active / même classe / `effectiveDate` <
`start_date` / inscription ACTIVE dans l'année cible / cas nominal —
ancienne → TRANSFERRED + `end_date` inclusif, **nouvelle ACTIVE
débutant `end_date` + 1 jour, sans chevauchement** + deux événements —,
**`transfer` avec `effectiveDate` explicite : `end_date` = effectiveDate
et nouvelle `start_date` = effectiveDate + 1**, `close` non active /
statut invalide / date invalide / COMPLETED nominal + événement, tri
hors liste blanche) ;
`StudentProfileServiceTests` (10, Mockito — compte inconnu / archivé /
sans rôle `STUDENT`, numéro dupliqué, profil déjà existant, création +
`companyName` trimé + événement, **collisions concurrentes
`uq_student_profile_user` / `uq_student_profile_student_number` du
persister retraduites en 409** et **violation `public_id` relancée à
l'identique**, tri hors liste blanche) ;
`EnrollmentConstraintsTests` (12, `@DataJpaTest` — deuxième inscription
ACTIVE même année rejetée, collision reconnue par
`EnrollmentPersistence.isActiveEnrollmentUniqueViolation` et violation
`public_id` **non** reconnue, clôture qui libère le créneau, année
distincte acceptée, unicités `user_id` / `student_number` /
`public_id`, `CHECK` de période, FK `RESTRICT` `student_profile` /
`class_group` / `previous_enrollment_id` via
`org.hibernate.exception.ConstraintViolationException` ; chaîne
académique insérée en SQL natif) ;
`ClassGroupDirectoryTests` (3, `@SpringBootTest` — résolution
publicId → codes formation / année + `openForEnrollment`, faux après
archivage de la classe, identifiant inconnu / `null` → `Optional.empty`) ;
`EnrollmentIntegrationTests` (9, `@SpringBootTest` `RANDOM_PORT` — cycle
profil → inscription → changement de classe (ancienne consultable en
TRANSFERRED + **nouvelle `start_date` = `end_date` de l'ancienne + 1
jour, aucun chevauchement**) → clôture, audit `STUDENT_PROFILE_CREATED`
/ `ENROLLMENT_CREATED` / `_TRANSFERRED` / `_CLOSED` ; doublon
d'inscription ACTIVE → 409 ; **deux créations concurrentes (pool 2
threads) → exactement un 201 et un 409** ; transfert vers la même
classe → 400 `ENR_SAME_CLASS` ; profil sur compte non `STUDENT` → 422 ;
numéro étudiant dupliqué → 409 ; inscription sous classe archivée → 409
`ENR_ARCHIVED_PARENT` ; profil inconnu → 404 ; tri hors liste blanche →
400 `ENR_INVALID_SORT` ; DTO sans identifiant SQL) ;
`EnrollmentSecurityTests` (3, `@SpringBootTest` — 401 anonyme ;
`STUDENT` / `TEACHER` / `PEDAGOGICAL_MANAGER` → 403 en lecture comme en
écriture ; `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION` → 200 en
lecture).

Fichiers back-end ajoutés : migration `V7` ; nouveau module
`com.esic.connect.enrollment` (package racine : `EnrollmentChangeEvent`
+ enums `EnrollmentResourceType` / `EnrollmentChangeAction`,
`package-info` ; `enrollment.internal` : `StudentProfile`(+`Status`),
`Enrollment`(+`EnrollmentStatus`, `EnrollmentSource`), leurs
repositories, `StudentProfileService` / `EnrollmentService`,
`StudentProfileController` / `EnrollmentController`, `*Requests` /
`*Response`, `EnrollmentChangePublisher`, `EnrollmentException`(+
`Handler`), `EnrollmentPersistence`, `EnrollmentPersister`
(`@Transactional(REQUIRES_NEW)`), `EnrollmentWeb`,
`EnrollmentQuerySupport`, `EnrollmentSpecifications`, `PageResponse`
local) ; `academic.ClassGroupDirectory` (port public) +
`academic.internal.DefaultClassGroupDirectory` (implémentation) ;
`audit.internal.EnrollmentAuditListener`. Fichiers modifiés :
`academic/package-info.java` (mention du port `ClassGroupDirectory`) ;
`src/test/resources/application-test.yml` (pool HikariCP plafonné).
`.env`, `compose.yaml`, `V1`–`V6`, `SecurityConfig`, `pom.xml`,
`docs/01`–`docs/04` et le workflow CI inchangés. Aucun profil, aucune
inscription fictive insérés. **PR #10 fusionnée sur `main`** (commit
`495c2bf`).

---

## Socle front-end Angular — 29 août 2026 (corrigé après revue de PR #11)

Branche `feature/frontend-foundation`, **fusionnée sur `main` via PR #11**
(commit `6fa341f`). Aucun fichier back-end modifié : `docs/01`–`docs/04`,
migrations `V1`–`V7`, `SecurityConfig`, `backend/**` et `backend-ci.yml`
inchangés. Autorisation et CORS back-end inchangés.

Application créée avec `ng new` sous `frontend/`. **Angular 21.2**,
politique de version cohérente dans `package.json` (`^21.2.x` pour tous
les paquets `@angular/*`). Versions résolues : `@angular/{core, common,
compiler, compiler-cli, forms, platform-browser, router, cli, build}` =
**21.2.22** ; `@angular/material` + `@angular/cdk` = **21.2.14**. Le
décalage de patch entre le framework (21.2.22) et Material/CDK (21.2.14)
est normal : Angular Components suit sa propre cadence de patch et
21.2.14 est son dernier patch de la ligne **21.2**, compatible avec le
framework 21.2.22. **Node.js 24.13.0**, npm 11.6.2. Application
*zoneless* par défaut, composants standalone, TypeScript strict,
formulaires réactifs, signaux, routes de fonctionnalités en lazy
loading, control flow natif. Dépendances first-party ajoutées :
`@angular/material` + `@angular/cdk` (Material explicitement requis —
docs/02 §48.1, docs/01 §5.3, US-023, T-J1-040) ; `angular-eslint` (dev)
pour `npm run lint`. `package-lock.json` régénéré via `npm install`,
`npm ci` vérifié depuis un `node_modules` vide.

Routes : `/login` (guestGuard), `/dashboard` (authGuard, 1re tranche
verticale authentifiée), `/administration`
(`roleGuard(['ADMIN','SUPER_ADMIN'])`), `/students`
(`roleGuard(['ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION'])`),
`/forbidden` (403), `**` (404). `/administration` et `/students` sont des
écrans d'attente (placeholder) : **masqués de la navigation principale
et des accès rapides** (`NavItem.placeholder`, `visibleNavItems` les
exclut), mais toujours déclarés, directement adressables et gardés par
rôle — un rôle non autorisé est redirigé vers `/forbidden`, un rôle
autorisé atteint l'écran « À venir ». La navigation principale ne
présente que les écrans livrés (aujourd'hui : le seul `/dashboard`).
Routes authentifiées enfants d'une coquille `AppShell` (barre Material +
navigation latérale responsive, `<nav>`/`<main>`, lien d'évitement,
`aria-current`).

Tableau de bord : rapporte un **état de session local** établi après une
connexion réussie. Il n'effectue **pas** de second appel d'API
authentifié et ne prétend pas avoir revérifié le jeton porteur via un
autre endpoint back-end (aucun `/auth/me` n'existe et aucun n'a été
ajouté). Il affiche l'email saisi, le claim `sub`, l'échéance du jeton,
les rôles, et une carte « accès rapides » limitée aux écrans livrés.

Authentification : `POST /api/v1/auth/login` consommé tel quel — c'est
cette requête qui prouve l'authentification. Pas d'endpoint `/auth/me`
ni `/auth/logout` côté back-end → déconnexion locale, identité affichée
= email saisi + claims JWT. **Stockage du jeton en mémoire uniquement**
(signal `AuthService`), ni `localStorage` ni `sessionStorage` ni
IndexedDB ni cookie JS (docs/07 §6, RG-085). Rechargement de page =
perte de session et retour à `/login` ; une vraie session persistante
exige le futur cookie `HttpOnly` + refresh token côté back-end.
`AuthService.restoreSession()` reste le point d'ancrage de ce futur flux
(aucun faux endpoint de refresh ni de current-user ajouté). Décodage JWT
non vérifié, affichage et navigation uniquement — autorisation réelle =
Spring Security. Intercepteurs : jeton porteur (`Authorization` sur
`/api`, jamais journalisé) ; erreurs (`401` non-login → purge +
`/login?reason=expired`, `0`/`5xx` → bandeau générique, `4xx` →
composant ; `normalizeHttpError` conserve le `code` métier `ApiError`
docs/03 §10.3).

Infra HTTP : URL d'API **relative** (`/api`) via
`src/environments/environment*.ts` ; `ng serve` proxifie `/api` vers
`http://localhost:8080` (`proxy.conf.json`) → aucune requête
cross-origin, **CORS back-end non modifié** (inexistant, non requis en
local). Déploiement cross-origin ultérieur : URL absolue + CORS Spring
(documenté dans `environment.ts`).

Vérifications exécutées avec succès en local le 29 août 2026 (Node
24.13.0), depuis `frontend/` :

```text
rm -rf node_modules && npm ci   # 582 paquets, 0 vulnérabilité
npm test -- --watch=false        # 14 fichiers, 69 tests, 0 échec (Vitest + jsdom)
npm run build                    # bundle initial 409 kB brut / 106 kB transféré, 0 alerte de budget
npm run lint                     # angular-eslint, « All files pass linting »
```

`cd backend && ./mvnw clean test` non ré-exécuté : aucun fichier
back-end modifié.

CI : `.github/workflows/frontend-ci.yml` (lint + tests + build sur
`frontend/**`).

Limites connues :
- pas de restauration de session au rechargement — un rechargement de
  page perd la session et renvoie vers `/login` ; une vraie session
  persistante exige le futur cookie `HttpOnly` + refresh token côté
  back-end ;
- sélecteur de contexte de rôle (docs/02 §6.1) livré à part sur
  `feature/frontend-role-context` (voir « Phase actuelle ») ;
- `/administration` et `/students` sont des routes gardées sans contenu
  métier, volontairement masquées de la navigation ;
- PWA, notifications, SSE non abordés ;
- tests front en TestBed/Vitest uniquement, pas de tests e2e Angular →
  Spring Boot.

---

## Activation de compte (front-end) — 29 août 2026

Branche `feature/frontend-account-activation` (créée depuis `main` à
`6fa341f`), **fusionnée sur `main` via PR #12** (commit `2ff7aa8`). Aucun
fichier back-end modifié : `docs/01`–`docs/04`, migrations `V1`–`V7`,
`SecurityConfig`, `backend/**`, `backend-ci.yml`, autorisation et CORS
back-end inchangés. Aucune dépendance ajoutée → `package.json` /
`package-lock.json` inchangés.

Parcours public `/activation` atteint via le lien d'invitation généré
par le back-end (`JavaMailSenderInvitationMailer` :
`${app.activation.base-url}?token=<jeton URL-encodé>`).

Contrat back-end consommé **tel quel** (`AccountInvitationController`,
`ActivateAccountRequest`, `InvitationValidationResponse`,
`InvitationExceptionHandler`) — rien inventé :
- `GET /api/v1/account-invitations/validate` — **public** (SecurityConfig
  `PUBLIC_PATHS`), jeton en **paramètre de requête** `token` ; toujours
  `200` avec `{ "valid": boolean }` (aucun code d'erreur, aucune donnée
  personnelle) ;
- `POST /api/v1/account-invitations/activate` — **public** ; corps
  `{ "token": string, "password": string }` ; succès `204 No Content`,
  corps vide, **aucun identifiant de session** ; erreurs :
  `400 VALIDATION_ERROR` (`@NotBlank` / `@Size(min = 12, max = 200)` sur
  `token` / `password`, via `GlobalExceptionHandler`) et
  `400 INVITATION_INVALID` (message « Lien d'activation invalide ou
  expire. ») — **code unique** pour jeton inconnu / expiré / révoqué /
  déjà consommé / cible non `PENDING_ACTIVATION`. Les autres `Kind`
  (`TARGET_NOT_FOUND` 404, `TARGET_NOT_PENDING` 409, `ROLE_INVALID` 422)
  ne sont atteignables que depuis l'émission protégée, jamais `/activate`.

Contraintes de mot de passe côté client, alignées exactement :
`required` + `minLength(12)` + `maxLength(200)`. Pas de règle de
complexité (absente du DTO), **pas de champ de confirmation** (absent du
contrat et des docs — docs/02 §8.3 étape 6 = simple « définition du mot
de passe »). Bascule afficher/masquer accessible ; `autocomplete="new-password"`.

Fichiers ajoutés : `frontend/src/app/features/account-activation/`
(`account-activation.models.ts`, `account-activation-api.service.ts`
(+ `.spec`), `account-activation.ts` / `.html` / `.scss` (+ `.spec`)),
`frontend/src/app/core/auth/jwt.testing.ts` (extraction de `makeJwt`
hors de `jwt.spec.ts`).
Fichiers modifiés : `app.routes.ts` (route `/activation` publique sans
garde), `core/http/auth-token.interceptor.ts` +
`core/http/api-error.interceptor.ts` (helper `isPublicInvitationRequest`
excluant `/account-invitations/validate|activate` — pas de bearer, pas
de purge de session sur `401`, pas de bandeau sur `5xx`),
`jwt.spec.ts` + `auth.service.spec.ts` (import depuis `jwt.testing`),
`app.routes.spec.ts` / `auth-token.interceptor.spec.ts` /
`api-error.interceptor.spec.ts` (tests ajoutés),
`tsconfig.spec.json` / `tsconfig.app.json` (`*.testing.ts`).

Vérifications exécutées avec succès en local le 29 août 2026 (Node
24.13.0), depuis `frontend/` :

```text
rm -rf node_modules && npm ci   # 0 vulnérabilité
npm test -- --watch=false        # 16 fichiers, 85 tests, 0 échec (Vitest + jsdom)
npm run build                    # bundle initial 410,57 kB brut / 106,54 kB transféré, 0 alerte de budget
npm run lint                     # angular-eslint, « All files pass linting »
```

`cd backend && ./mvnw clean test` non ré-exécuté : aucun fichier
back-end modifié (CI back-end `backend-ci.yml` inchangée).

Limites connues (activation) :
- l'écran exige un `?token=` valide dans le lien : pas de renvoi
  d'invitation en libre-service dans la SPA ;
- le back-end renvoyant un unique `INVITATION_INVALID`, l'interface
  affiche un seul état terminal « lien invalide ou expiré » (pas d'écran
  distinct expiré / consommé / révoqué — choix délibéré) ;
- pas de champ de confirmation du mot de passe (hors contrat back-end) ;
- l'activation ne connecte pas automatiquement (le `204` ne renvoie
  aucun identifiant) : écran de succès + lien explicite vers `/login`.

## Règle de mise à jour

Ce document doit refléter le dépôt.

Ne jamais déclarer :

- TESTÉ sans commande exécutée ;
- DÉMONTRÉ sans vérification manuelle ;
- DÉPLOYÉ sans URL ou preuve ;
- FONCTIONNEL uniquement parce que le code existe.