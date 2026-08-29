# ESIC Connect — Front-end Angular

Interface web (PWA à venir) d'ESIC Connect. Consomme l'API REST Spring Boot
sous `/api/v1` (docs/03-architecture.md §9–10).

## Prérequis

- Node.js 24 (testé avec 24.13.0)
- npm 11
- Back-end lancé sur `http://localhost:8080` (voir `../backend`) pour un usage réel

## Démarrer

```bash
npm ci
npm start            # ng serve sur http://localhost:4200
```

`ng serve` proxifie `/api` vers `http://localhost:8080` (`proxy.conf.json`),
donc aucune configuration CORS n'est nécessaire en local.

## Commandes qualité

```bash
npm test -- --watch=false   # Vitest + jsdom (builder @angular/build:unit-test)
npm run build               # build de production (dossier dist/)
npm run lint                # ESLint (angular-eslint)
```

## Structure

```
src/app/
  core/         infrastructure transverse
    auth/       AuthService (session en mémoire), décodage JWT (affichage seul),
                RoleContextService (contexte de rôle, mémoire seule)
    guards/     authGuard, guestGuard, roleGuard(...)
    http/       intercepteurs (jeton porteur, erreurs API)
    layout/     coquille authentifiée (barre + navigation)
    models/     types (rôles, session, erreurs API)
    navigation/ matrice de navigation dérivée des @PreAuthorize back-end
    notifications/
  shared/       composants réutilisables
  features/
    auth/login/          écran de connexion
    account-activation/  parcours public `/activation?token=…` (validation + définition du mot de passe)
    dashboard/           premier écran authentifié
    students/            liste des apprenants + fiche + historique d'inscriptions
    errors/              403 / 404
```

## Apprenants (`/students`)

Écran réservé, côté serveur, à `ADMIN` / `SUPER_ADMIN` /
`SCHOOL_ADMINISTRATION` (`EnrollmentWeb.MANAGE_ROLES`). Le `roleGuard` de
la route reprend ce périmètre pour masquer la navigation ; il ne
remplace pas Spring Security — un `403` renvoyé par l'API est rendu comme
un état « accès refusé » explicite.

- `/students` — liste paginée des profils apprenants
  (`GET /api/v1/student-profiles`). Recherche, filtre, tri et pagination
  reflètent **exactement** l'API : recherche `q` sur le seul
  **numéro étudiant** (le nom n'est pas interrogeable), filtre `status`
  (`ACTIVE` / `ARCHIVED`), tri sur `studentNumber` ou `createdAt`,
  pagination bornée à 100.
- `/students/:publicId` — fiche apprenant
  (`GET /api/v1/student-profiles/{publicId}`) + historique des
  inscriptions (`GET /api/v1/enrollments?student={publicId}&sort=startDate,desc`).
  L'identité civile (nom, e-mail) est complétée de façon **facultative**
  par `GET /api/v1/users/{userPublicId}` — le profil apprenant n'exposant
  que `userPublicId` ; l'échec de cet appel n'empêche pas l'affichage.

Aucun endpoint ni champ n'est inventé. Les états chargement, vide,
erreur (avec « Réessayer »), accès refusé et succès sont couverts. Aucune
donnée n'est écrite dans `localStorage` / `sessionStorage`.

## Activation de compte

`/activation?token=<jeton>` est une route **publique sans garde** atteinte
via le lien d'invitation du back-end. Le jeton est lu une fois depuis la
query string puis retiré de la barre d'adresse (`Location.replaceState`,
sans rechargement), gardé en mémoire du composant, jamais journalisé ni
stocké, et transmis uniquement à
`GET /api/v1/account-invitations/validate` et
`POST /api/v1/account-invitations/activate` (jamais en jeton porteur).
L'activation réussie (`204`) ne connecte pas : elle affiche un succès et
un lien vers `/login`.

## Authentification / session

- `POST /api/v1/auth/login` renvoie un bearer JWT (`{ accessToken, tokenType, expiresInSeconds }`).
- Le jeton est conservé **en mémoire uniquement** (aucun `localStorage`,
  `sessionStorage` ni cookie écrit en JS) — docs/07-securite-rgpd.md §6, RG-085.
- Conséquence : un rechargement de page perd la session et renvoie vers la
  connexion. `AuthService.restoreSession()` est le point d'ancrage d'un futur
  cookie `HttpOnly` + refresh token (stratégie cible docs/03 §15.2), non
  encore exposé par le back-end.
- Le contenu du JWT (rôles, sujet) ne sert qu'à l'affichage et au filtrage de
  la navigation. Toute autorisation réelle est décidée par Spring Security.

## Contexte d'utilisation (rôle)

Conforme à docs/02-cahier-des-charges.md §6.1 (exigence EF-AUTH-003).

- `RoleContextService` propose comme contextes **uniquement les rôles
  réellement présents** dans le claim `roles` du JWT ; aucune valeur
  inventée.
- Le contexte actif est un signal **en mémoire seule** (ni `localStorage`
  ni `sessionStorage`), au même titre que le jeton. Un rechargement de
  page le perd, comme la session. Contexte par défaut = le rôle le plus
  privilégié présent (ordre de `ROLES`).
- Il ne pilote que l'affichage et la navigation : `effectiveRoles` réduit
  les entrées visibles au seul rôle choisi. Il **ne remplace jamais** le
  contrôle d'accès Spring Security, qui revalide chaque appel à partir du
  JWT — sélectionner un contexte n'accorde ni ne retire aucun droit.
- Le sélecteur (`app-role-context-menu`, barre supérieure de `AppShell`)
  n'apparaît que si le compte cumule au moins deux rôles. `/login`,
  `/activation` et `/dashboard` sont inchangés.
