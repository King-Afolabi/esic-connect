# Lot O — Revue sécurité et qualité

Revue des changements de cette campagne (base `314476b` → HEAD). Toutes
les modifications sont **front-end + documentation** ; aucune ligne de
back-end, aucune migration.

| Point de contrôle | Constat |
|---|---|
| **Aucun secret commité** | `git diff 314476b..HEAD` (hors `.png`) scanné : aucune occurrence de mot de passe / clé / jeton en dur. `.env` non modifié, non suivi. Le repli `ESIC_DEMO_TOTP_SECRET=JBSWY3DPEHPK3PXP` n'a été **exporté que dans le shell** pour un essai e2e, jamais écrit dans un fichier suivi (`tests/13` ne le référence pas). |
| **Profil `demo` non activable par erreur en production** | Inchangé. `DemoDataInitializer`, `DefaultDemoMfaProvisioner`, `DefaultDemoAccountProvisioner` restent `@Profile("demo")`. `compose.prod.yaml` et `PRE-FLIGHT.md` rappellent : `demo` pour la recette seulement. |
| **Permissions serveur des nouvelles actions** | Lot H (création manuelle d'apprenant) n'ajoute **aucun endpoint** : il enchaîne `POST /users` (`@PreAuthorize(ADMIN_ROLES)`), `POST /student-profiles` et `POST /enrollments` (`EnrollmentWeb.MANAGE_ROLES`), tous inchangés. La route `/students/nouveau` porte `roleGuard(['ADMIN','SUPER_ADMIN'])` — masquage ergonomique ; Spring Security reste l'autorité (un `403` est rendu « accès refusé »). |
| **CSRF** | Aucun nouvel appel muté par cookie. Lot A : `refreshSession` réutilise `POST /auth/refresh` existant (cookie `SameSite=Strict`, `Path=/api/v1/auth` — protection décrite dans CURRENT-STATE 6 sept.). Lot G : `router.navigate` pour l'URL — aucun appel serveur. Lot H : appels porteur (`Authorization: Bearer`), pas d'autorité ambiante par cookie. |
| **XSS dans le détail des anomalies** | Lot J / Lot K : `issue.message`, `issue.columnName`, `receivedValue`, `suggestedValue` rendus par interpolation Angular `{{ }}` (échappement automatique). Aucun `[innerHTML]`. Les formulaires de correction lient `[value]=` (pas `innerHTML`). Le fichier de test `students-xss.csv` couvre déjà la neutralisation côté import. |
| **Limitation de débit compatible e2e sans affaiblir la production** | Non touchée. `LOGIN_ORIGIN_LIMIT` reste piloté par l'environnement (60/15 min par défaut). `tests/13` ouvre l'écran de connexion sans jamais soumettre d'identifiants → aucun impact sur le seau de connexion. |
| **Journaux sans mot de passe / OTP / cookie / jeton** | `SessionActivityService` ne journalise rien. `tests/13` : `console.error` n'imprime qu'un résumé de violations axe (id, impact, nombre de nœuds) — aucune donnée personnelle. Aucun `console.log` résiduel dans les specs (vérifié). |
| **Pas de donnée personnelle excessive dans les captures** | 15 captures = écrans **publics** (connexion, mot de passe oublié), aucun contenu personnel. Les captures authentifiées ne sont pas produites dans cette passe (voir rapport final). |
| **Migrations uniquement si nécessaires** | **Zéro migration ajoutée.** Schéma inchangé en V34. |
| **Compatibilité ARM64 (Raspberry Pi)** | Modifications front-end neutres vis-à-vis de l'architecture. `@axe-core/playwright` est une **dépendance de développement** (jamais dans l'image de production). `compose.prod.yaml` / Dockerfiles : images de base multi-arch, aucune ligne `platform: linux/amd64`. |
| **Consommation mémoire / espace disque** | Front : bundle initial 578–580 kB (≈ +2 kB depuis la base, sous le seuil de 600 kB). `styles.css` ≈ 89 kB. Rotation des journaux Docker ajoutée (`x-logging`, 10 Mo × 3 par conteneur) — protège la carte SD. |
| **Dépendances vulnérables** | `npm audit` (racine + `frontend`, toutes dépendances) : **0 vulnérabilité**. `@axe-core/playwright` + `axe-core` ajoutés en `devDependencies`. |
| **État Git** | Propre. `.env` non suivi. `test-results/artifacts/` gitignoré. |

## Points ouverts (hérités, non introduits ici)

- `DECISIONS_NEEDED.md` D-01 : ordre/exclusivité des fenêtres
  d'émargement — décision produit en attente (Lot I).
- Dettes T-05, T-06, T-13..T-17 de `docs/CURRENT-STATE.md` : inchangées.
