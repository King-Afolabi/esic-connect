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
    auth/       AuthService (session en mémoire), décodage JWT (affichage seul)
    guards/     authGuard, guestGuard, roleGuard(...)
    http/       intercepteurs (jeton porteur, erreurs API)
    layout/     coquille authentifiée (barre + navigation)
    models/     types (rôles, session, erreurs API)
    navigation/ matrice de navigation dérivée des @PreAuthorize back-end
    notifications/
  shared/       composants réutilisables
  features/
    auth/login/ écran de connexion
    dashboard/  premier écran authentifié
    errors/     403 / 404
```

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
