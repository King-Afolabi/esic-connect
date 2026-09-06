# Lot A — Expiration de session et inactivité

## Source réelle du comportement « déconnexion à 15 minutes »

| Mécanisme | Valeur | Fichier |
|---|---|---|
| Jeton d'accès (JWT, en mémoire seule) | **900 s (15 min)** | `backend/.../application.yml` → `JWT_ACCESS_TOKEN_TTL_SECONDS` |
| Cookie de renouvellement `HttpOnly` — inactivité glissante | `PT30M` | `JWT_REFRESH_TOKEN_IDLE_TTL` |
| Cookie de renouvellement — plafond absolu | `PT12H` | `JWT_REFRESH_TOKEN_ABSOLUTE_TTL` |

Le back-end (commit `d4eea44`) implémente déjà un modèle correct : cookie
rotatif, inactivité glissante, plafond absolu, détection de rejeu de
famille. **Ce qui manquait était côté client** :

- aucun renouvellement **proactif** : le jeton d'accès n'était renouvelé
  qu'au premier `401` d'un appel métier (`api-error.interceptor.ts`) ;
- aucun **avertissement** avant l'expiration ;
- la route en cours était **perdue** à l'expiration
  (`handleUnauthorized` renvoyait vers `/login` sans `?redirect=`).

Conséquence : un utilisateur qui lisait, saisissait ou naviguait sans
déclencher d'appel voyait sa session tomber à 15 min ; si le trou
dépassait aussi les 30 min d'inactivité du cookie, le renouvellement
réactif échouait et il perdait sa saisie.

## Correctif

Renouvellement **piloté par l'activité significative**, sans toucher au
back-end (MFA, CSRF, rotation, expiration serveur, révocation intacts).

- `SessionActivityService` (`core/auth/session-activity.service.ts`),
  armé par la coquille applicative (présente uniquement sous session
  ouverte), désarmé à sa destruction.
- **Activité significative** : `pointerdown`, `keydown`, `submit`,
  `NavigationEnd` du routeur. **Pas** le survol souris ni le défilement.
- **Anti-rafale** : une activité n'est retenue qu'une fois par
  `activityThrottleMs`.
- **Renouvellement proactif** : sur un tick, si activité récente **et**
  jeton à moins de `renewLeadMs` de son terme **et** dernier
  renouvellement il y a plus de `minRenewIntervalMs` **et** le plafond
  absolu ne serait pas dépassé → `POST /api/v1/auth/refresh`. Verrou
  `localStorage` inter-onglets (`esic-session-renew-lock`, TTL 8 s,
  horodatage — pas un jeton) pour ne pas rejouer le cookie en parallèle
  et déclencher la détection de rejeu.
- **Avertissement** : `SessionTimeoutWarning`
  (`core/layout/session-timeout-warning/`) — `role="alertdialog"`,
  `aria-modal`, libellé et description liés, focus déplacé puis rendu,
  piège de focus, `Échap` = « Continuer », `prefers-reduced-motion`
  respecté. Boutons « Continuer la session » / « Se déconnecter ».
- **Plafond absolu** : appliqué par le back-end ; le client cesse de
  renouveler quand le plafond miroir `absoluteMaxMs` est atteint et
  bascule en fin de session.
- **Multi-onglets** : `BroadcastChannel('esic-session')`. Un
  renouvellement diffuse `renewed` (les autres onglets lèvent leur
  avertissement) ; une fin de session diffuse `ended` (les autres
  terminent aussi).
- **Route de retour** : `AuthService.expireSession()` renvoie vers
  `/login?reason=expired&redirect=<route courante>` (sauf si déjà sur
  `/login`). Le composant `Login` honore `?redirect=` via `safeTarget()`
  (chemin interne uniquement) — pas de boucle de redirection.

## Durées retenues et configuration

Toutes dans `frontend/src/environments/environment.ts` → `session` (mêmes
valeurs en développement), en millisecondes :

| Clé | Défaut | Rôle |
|---|---|---|
| `pollIntervalMs` | 20 000 | fréquence du contrôle d'échéance |
| `activityThrottleMs` | 60 000 | fenêtre anti-rafale d'enregistrement d'activité |
| `activityWindowMs` | 300 000 | ancienneté max d'une activité pour être « actif » |
| `renewLeadMs` | 300 000 | renouvellement proactif sous ce reste de jeton |
| `minRenewIntervalMs` | 240 000 | intervalle minimal entre deux renouvellements proactifs |
| `warningLeadMs` | 120 000 | avertissement affiché sous ce reste, sans activité |
| `absoluteMaxMs` | 43 200 000 | miroir d'affichage du plafond absolu back-end (PT12H) |

Back-end : `JWT_ACCESS_TOKEN_TTL_SECONDS`, `JWT_REFRESH_TOKEN_IDLE_TTL`,
`JWT_REFRESH_TOKEN_ABSOLUTE_TTL` (variables d'environnement,
`application.yml`). Pour éprouver rapidement le parcours, réduire
`JWT_ACCESS_TOKEN_TTL_SECONDS` et `warningLeadMs`.

## Choix d'architecture assumés

- **Pas de renouvellement à chaque appel API réussi.** La rotation du
  cookie à chaque requête est incompatible avec la détection de rejeu de
  famille du back-end sous concurrence, et coûteuse (écriture Redis par
  requête). Le renouvellement piloté par l'activité, throttlé, couvre le
  besoin ; le cahier l'autorise (« si l'architecture de sécurité le
  permet »).
- **Préservation des brouillons** : seule la route de retour est
  préservée à ce stade. La préservation générique des brouillons de
  formulaire non sensibles est une amélioration distincte, non traitée
  ici (pas de faux correctif).

## Tests

- `session-activity.service.spec.ts` (12) — renouvellement proactif sur
  activité, expiration effective sans activité, avertissement affiché,
  renouvellement throttlé (un seul refresh, pas un par tick), plafond
  absolu respecté, `ended`/`renewed` multi-onglets, arrêt propre,
  « Continuer » (succès et échec du cookie), navigation = activité.
- `session-timeout-warning.spec.ts` (6) — rien rendu au repos ;
  `alertdialog` accessible (labels, `aria-modal`) ; action primaire =
  « continuer » ; action secondaire = déconnexion ; `Échap` = continuer ;
  actions désactivées pendant le renouvellement.
- `auth.service.spec.ts` — `expireSession` préserve `?redirect`, ne
  boucle pas sur `/login`, no-op sans session.
- Suite complète : **97 fichiers / 807 tests / 0 échec**, lint vert,
  build production sans alerte de budget.
