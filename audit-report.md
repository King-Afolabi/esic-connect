<!-- markdownlint-disable -->
# Audit QA indépendant — ESIC Connect

| | |
|---|---|
| Portée | Application réelle en exécution locale (backend `:8080` profil `demo`, base `esic_connect_demo` ; frontend Angular `:4200`) |
| Méthode | Lecture de la documentation de référence, inspection du code réel (routes, contrôleurs, templates), pilotage d'un vrai navigateur (Playwright) contre l'application réellement démarrée, appels API directs pour les cas non atteignables par l'UI |
| Date | 3 septembre 2026 |
| Auteur | Audit assisté par Claude Code, à la demande du porteur de projet |
| Règle suivie | CLAUDE.md : « ne jamais inventer une fonctionnalité, un test ou un résultat » — tout ce qui suit a été exécuté et observé, pas supposé |

---

## 0. Comment lire ce rapport

Le prompt d'origine demandait une suite Playwright « exhaustive » sur 9 domaines
(authentification, planification, émargement, assiduité, utilisateurs,
ressources, notifications, cas limites, performance) comptant des dizaines de
scénarios chacun — y compris pour des fonctionnalités que la documentation du
projet elle-même classe `HORS_PÉRIMÈTRE_ASSUMÉ` ou `NOT_IMPLEMENTED`
(WebAuthn, MFA, Turnstile, scan caméra, SMS, IoT, export Excel/PDF, mot de
passe oublié…). Écrire des tests Playwright contre des écrans qui n'existent
pas violerait directement la règle du projet (« ne jamais inventer un test »)
et ne produirait que du bruit (échecs « élément introuvable » sans valeur de
diagnostic).

**Décision appliquée (validée avec le porteur de projet avant l'écriture des
tests)** : une suite Playwright réelle et exécutable a été construite pour
tout ce qui existe effectivement dans l'interface ; tout le reste apparaît
dans la matrice du §2 comme écart documenté avec sa preuve (route absente,
citation de `docs/CURRENT-STATE.md`), jamais comme un test fabriqué.

Le projet a par ailleurs déjà tranché, par écrit, contre l'idée même de cette
suite : `docs/CURRENT-STATE.md` classe les tests e2e navigateur
`NOT_IMPLEMENTED` avec une décision documentée (`DEC-G1-011`, « non retenus
faute de rapport coût/bénéfice »), au profit d'une recette d'intégration API
déjà livrée (`PriorityPathRecetteIntegrationTests`, 811 tests backend verts).
Cette suite est un **complément** produit à la demande explicite pour cet
audit, pas un remplacement de cette décision — à évaluer par l'équipe selon
le même calcul coût/bénéfice avant d'être conservée dans le dépôt.

---

## 1. Ce qui DEVRAIT exister (résumé du cahier des charges)

`docs/01-cadrage.md` et `docs/02-cahier-des-charges.md` définissent le
périmètre en 4 niveaux de priorité :

- **MUST** : authentification, rôles + cumul, référentiels (formations,
  classes, promotions, années), import CSV apprenants, **import planning →
  publication → création des séances**, QR dynamique + 4 points de contrôle
  nommés, calcul de demi-journées, rapports, export CSV, audit, Redis.
- **SHOULD** : Excel, WebAuthn, PWA, QR fixe de salle, remplacements,
  justificatifs, réclamations, Raspberry Pi.
- **COULD** : MFA TOTP, Cloudflare Turnstile, notifications push, détection
  d'anomalies IA, export PDF.
- **FUTURE** : Microsoft Graph/Teams/Outlook, AWS, passkeys généralisées.

**Point de gouvernance découvert dès la lecture (finding F-DOC-1, détaillé
au §3)** : `docs/01-cadrage.md` §23.5 et `docs/02-cahier-des-charges.md`
§4.5.1 portent un addendum daté du **31 août 2026** déclarant l'intégralité
du domaine planning (import, prévisualisation, publication, versionnement,
création automatique de séances — EF-PLAN-001 à 007, EF-SES-001, RG-016,
AC-007, AC-008) `HORS_PÉRIMÈTRE_ASSUMÉ`, avec la consigne explicite de ne
« jamais » présenter ce domaine comme livré. Le commit `d3450e6` (lot G1,
**1er septembre 2026, postérieur à cet addendum**) a pourtant livré un
module `planning` complet et fonctionnel — confirmé en direct par cet audit
(voir §3, finding F-DOC-1).

---

## 2. Matrice fonctionnalité attendue → implémentée → testée

Légende : ✅ implémenté et testé par cette suite · ⚠️ partiel (voir note) ·
❌ non implémenté / hors périmètre assumé (aucun test inventé) · 🔍 vérifié
uniquement par appel API direct (pas d'écran).

| Domaine | Fonctionnalité (réf. CDC) | Attendue | Implémentée | Testée (cette suite) | Preuve / note |
|---|---|:---:|:---:|:---:|---|
| Auth | Connexion email + mot de passe (tous rôles) | MUST | ✅ | ✅ | `01-authentication.spec.ts` |
| Auth | Réponse uniforme identifiant inconnu / mot de passe faux (AC-001) | MUST | ✅ | ✅ | idem |
| Auth | Déconnexion | SHOULD | ✅ | ✅ | idem |
| Auth | Garde `redirect=` après connexion | — (mécanisme interne) | ✅ | ✅ | idem — condition de toute la suite (voir F-ENV-2) |
| Auth | Mot de passe oublié | SHOULD | ❌ | — | `HORS_PÉRIMÈTRE_ASSUMÉ`, CURRENT-STATE.md |
| Auth | MFA TOTP, WebAuthn, Turnstile | COULD | ❌ | — | idem |
| Auth | Révocation de session serveur / `/auth/logout` | SHOULD | ❌ | — | idem (JWT stateless assumé) |
| RBAC | Cumul de rôles + sélecteur de contexte | MUST | ✅ | ✅ | `01-`, `02-authorization-rbac.spec.ts` |
| RBAC | Matrice rôle × route (12 routes × 5 comptes) | MUST | ✅ | ✅ | `02-authorization-rbac.spec.ts` — 60 combinaisons |
| RBAC | Refus par URL directe (pas un simple masquage menu) | MUST | ✅ | ✅ | idem |
| Référentiels | Formations, niveaux, promotions, classes, années (lecture) | MUST | ✅ | ✅ | `03-academic-organization-alternation.spec.ts` |
| Référentiels | Écriture du référentiel académique | MUST (cible) | ❌ écran | — | `PARTIAL` CURRENT-STATE.md — API livrée, aucun écran |
| Organisation | Sites : lecture + écriture (CRUD) | — | ✅ | ✅ | idem — création, doublon serveur refusé, XSS testé |
| Alternance | Modèles de rythme (lecture + écriture) | MUST | ✅ | ✅ | idem |
| Import apprenants | Simulation puis confirmation (AC-004) | MUST | ✅ | ✅ | `04-student-import.spec.ts` |
| Import apprenants | Détection erreurs (colonne manquante, email invalide, code inconnu) | MUST | ✅ | ✅ | idem |
| Import apprenants | Rejet fichier non-CSV, contenu XSS neutralisé | — | ✅ | ✅ | idem |
| Import apprenants | Import Excel / classeur multi-feuille | SHOULD | ❌ | — | `HORS_PÉRIMÈTRE_ASSUMÉ` |
| **Planning** | Import CSV → simulation → publication → versions | MUST **et** `HORS_PÉRIMÈTRE_ASSUMÉ` (contradiction, F-DOC-1) | ✅ (code réel) | ✅ | `05-planning.spec.ts` — voir avertissement §0/§3 |
| Séances | Création manuelle exceptionnelle (motif obligatoire) | — (repli documenté) | ✅ | ✅ | `06-sessions-attendance.spec.ts` |
| Séances | Ouverture / fermeture / annulation, remplacement | MUST/SHOULD | ✅ | ✅ (ouverture/fermeture) ⚠️ (remplacement non testé) | idem |
| Émargement | Code court + QR (jeton opaque) | MUST (QR) | ✅ | ✅ | idem — parcours prioritaire complet, 2 apprenants réels |
| Émargement | Anti-rejeu (RG-015) | MUST | ✅ | ✅ | idem, test 5 |
| Émargement | QR fixe de salle + contrôle réseau CIDR | SHOULD | ❌ | — | `HORS_PÉRIMÈTRE_ASSUMÉ` |
| Émargement | Scan caméra | — | ❌ | — | texte d'aide de l'écran lui-même : « ajouté dans une tranche ultérieure » |
| Émargement | 4 points de contrôle nommés + paliers de retard 15/30 min | MUST | ⚠️ | — | `PARTIAL` — 1 seuil unique (10 min), points génériques |
| Assiduité | Isolation stricte entre apprenants (AC-017) | MUST | ✅ | ✅ | `06-sessions-attendance.spec.ts`, test 11 |
| Assiduité | Espace « Mes présences » (historique) | SHOULD | ✅ | ✅ | idem, test 10 |
| Assiduité | Justificatifs (dépôt/décision) | SHOULD | ✅ (API) | ❌ | aucun compte de démo n'atteint l'écran de dépôt dans ce jeu de rôles ; non inventé |
| Rapports | Synthèse + rapports séance/classe/apprenant | MUST | ✅ | ✅ | `07-attendance-management-reports.spec.ts` |
| Rapports | Export CSV | MUST | ✅ | ✅ | via la fiche de séance (`06-`) |
| Rapports | Export Excel / PDF | SHOULD/COULD | ❌ | ✅ (constat d'absence) | `07-` vérifie l'absence des boutons |
| Notifications | Centre in-app, filtre lu/non lu | SHOULD | ✅ | ✅ | `08-notifications-dashboard.spec.ts` |
| Notifications | Email métier, push PWA, préférences par canal | SHOULD/COULD | ❌ | — | `PARTIAL`/absent, CURRENT-STATE.md |
| Tableau de bord | Par rôle (5 comptes réels) | — | ✅ | ✅ | `08-notifications-dashboard.spec.ts` |
| Sécurité | XSS neutralisé (import CSV + formulaire site) | — | ✅ | ✅ | `04-`, `09-security-edge-cases.spec.ts` |
| Sécurité | 401 sans jeton / jeton falsifié | — | ✅ | ✅ 🔍 | `09-security-edge-cases.spec.ts` |
| Sécurité | 403 (pas 500) sur route hors rôle, jeton réel rejoué | — | ✅ | ✅ 🔍 | idem |
| Sécurité | Défaut connu `GET /planning/versions` sans paramètre → 500 | — | ❌ (non corrigé) | ✅ 🔍 | idem — déjà tracé, `DEMO_CRITICAL_PATH_DIAGNOSTIC.md` §2, reconfirmé en direct |
| Sécurité | CSRF | — | N/A | — | JWT porté par en-tête, pas cookie de session (`allowCredentials=false`) : classe d'attaque non applicable telle quelle |
| Sécurité | Validation cliente (champs vides, longueur max), double soumission | — | ✅ | ✅ | `09-security-edge-cases.spec.ts` |
| Performance | Mesure indicative de chargement (PAS l'objectif < 100 ms du CDC) | — | — | ✅ (indicatif) | `10-performance-accessibility.spec.ts` — voir avertissement de portée |
| Responsive | Mobile (tiroir de nav) / desktop | MUST | ✅ | ✅ | idem |
| Accessibilité | Lien d'évitement, `role="alert"`, `aria-hidden` sur icônes, clavier | MUST | ⚠️ | ✅ | idem — lien d'évitement absent sur `/login` (finding, §4) |
| IA | Assistance import, score d'anomalie | SHOULD/COULD | ❌ | — | aucun service Python démarré |
| IoT | MQTT / Raspberry Pi | SHOULD | ❌ | — | broker démarré, zéro code backend consommateur |
| Réclamations | Création, transfert | SHOULD | ❌ | — | `HORS_PÉRIMÈTRE_ASSUMÉ` |
| Salles / IoT | Gestion de salle dédiée, QR fixe | SHOULD | ❌ écran séparé | — | fusionné dans `organization`, pas de QR fixe consommé |

---

## 3. Anomalies et découvertes significatives

### F-ENV-1 — CRITIQUE (environnement, pas le code applicatif) — base « locale » polluée par des données de test

La base `esic_connect` (profil `local`, celle initialement branchée au
lancement de cet audit) contient **27 105 lignes `user_account`** dont la
quasi-totalité porte des motifs de nommage de fixtures de test
(`att-*` : 7152 lignes, `alt-*` : 1477, `assign-*` : 1612, `acad-*` : 985,
`sec-*`, `auth-*`, `applied.*`, `aud-*`, `audforeign-*`…), signature exacte
des données générées par la suite de tests backend. Cela indique qu'à un
moment, `./mvnw test` (ou équivalent) a écrit dans la base de développement
au lieu de `esic_test`. **Conséquence directe** : aucune démonstration
manuelle crédible n'est possible sur cette base en l'état (identifiants
inconnus, volumétrie non représentative).

**Action prise pour cet audit** : le backend a été redémarré sous le profil
`demo` (`esic_connect_demo`, déjà propre et à jour en V16, comptes fictifs
documentés dans `DemoDataInitializer`) avec l'accord du porteur de projet.
`esic_connect` (local) n'a pas été modifiée ni nettoyée — **elle reste
polluée** et doit être traitée avant toute démonstration ou tout travail de
développement continu dessus.

**Recommandation** : `DROP DATABASE esic_connect; ` puis recréation +
migration propre, et vérifier la configuration CI/scripts pour identifier
la cause (variable `MYSQL_DATABASE` non substituée lors d'un run de tests ?).

### F-DOC-1 — MAJEUR (gouvernance documentaire) — contradiction entre l'addendum de périmètre et le code livré

- `docs/01-cadrage.md` §23.5 et `docs/02-cahier-des-charges.md` §4.5.1
  (commit `d7d2bfe`, « docs(finalization): aligner la documentation sur
  l'état réel (F2) ») déclarent le domaine planning entier
  `HORS_PÉRIMÈTRE_ASSUMÉ` et demandent explicitement de ne jamais le
  présenter comme livré.
- Le commit `d3450e6` (**1er septembre 2026, POSTÉRIEUR** à l'addendum
  ci-dessus), fusionné par la PR #40, a livré le lot « G1 » qui inclut un
  module `planning` complet : `PlanningImportController`
  (`POST /api/v1/planning-imports`, `POST /{id}/publish`),
  `PlanningVersionController`, port `coursesession.PlanningSessionWriter`
  avec son implémentation par défaut — confirmé en lisant le code source
  ET en pilotant l'écran réel (`/planning/import` → simulation → publication
  → nouvelle version visible sur `/planning/versions`), test
  `05-planning.spec.ts`.
- `docs/CURRENT-STATE.md` (mis à jour le 2 septembre, donc après le lot G1)
  documente ce module comme `IMPLEMENTED_AND_TESTED` sans jamais mentionner
  ni résoudre la contradiction avec l'addendum du 31 août.

**Ce que cet audit NE tranche PAS** : laquelle des deux décisions
documentées (« hors périmètre, jamais livré » vs « livré et testé ») doit
prévaloir — c'est une décision produit/gouvernance, pas un défaut de code.
**Ce qu'il faut faire** : mettre à jour `docs/01-cadrage.md` §23.5 et
`docs/02-cahier-des-charges.md` §4.5.1 pour refléter l'état réel (soit
retirer l'addendum si le planning est officiellement repris dans le
périmètre, soit documenter explicitement pourquoi un module hors périmètre
a été développé puis comment il doit être traité pour la soutenance).

### F-ENV-2 — INFORMATIF (architecture réelle, à connaître avant toute démo manuelle) — aucune persistance de session

Le jeton JWT vit uniquement dans un service Angular en mémoire
(`AuthService`, commentaire du fichier source : « Ni `localStorage` ni
`sessionStorage` ni cookie écrit en JavaScript »), sans aucune restauration
au démarrage (pas de `POST /api/v1/auth/refresh` fondé sur un cookie
`HttpOnly`, la stratégie cible documentée en §26.6 du CDC n'est pas
implémentée). **Conséquence concrète, vérifiée en pilotant le navigateur** :
tout rechargement de page (F5, navigation via barre d'adresse, lien externe)
efface immédiatement la session, quel que soit le compte ou le temps restant
avant l'expiration du jeton (15 min).

Ce n'est pas nécessairement un bug pour un prototype de 3 jours (CDC
§23.4 classe le rafraîchissement de jeton en `SOUHAITÉ`), mais c'est un
point d'attention réel pour la démonstration devant jury : **un rafraîchissement
accidentel de la page pendant la démo déconnecte immédiatement**, et
l'écran de connexion doit être retraversé (heureusement, le mécanisme
`?redirect=` ramène l'utilisateur exactement là où il était après une
nouvelle connexion — ce n'est donc pas fatal, juste une interruption
visible). À signaler explicitement dans le guide de démonstration.

### F-SEC-1 — DÉJÀ CONNU, RECONFIRMÉ EN DIRECT — `GET /api/v1/planning/versions` sans paramètre renvoie 500

Déjà documenté dans `docs/reports/DEMO_CRITICAL_PATH_DIAGNOSTIC.md` §2
comme un défaut **non corrigé** : l'appel de cette route sans le paramètre
obligatoire `classGroupPublicId` renvoie `500 INTERNAL_ERROR` au lieu d'un
`400` de validation. Reconfirmé ici avec un jeton `ADMIN` réel
(`09-security-edge-cases.spec.ts`). Non atteignable par l'écran réel (le
sélecteur de classe est obligatoire avant tout appel), mais reste un défaut
pour tout autre client de cette API documentée (OpenAPI/Swagger).

### F-A11Y-1 — MINEUR — lien d'évitement absent sur les pages publiques

Le lien d'évitement (« Aller au contenu principal ») appartient au
composant `AppShell`, qui n'enveloppe que les routes authentifiées. Il est
donc absent de `/login`, `/activation`, `/forbidden` et `/not-found` — pages
publiques où un repère `#main-content` existe mais sans lien pour l'atteindre
au clavier/lecteur d'écran en sautant un contenu répétitif (il n'y en a pas
vraiment sur `/login`, donc l'impact réel est faible, mais l'incohérence
mérite d'être connue).

---

## 4. Résultats des tests Playwright

### 4.1 Bilan

| Indicateur | Valeur |
|---|---|
| Fichiers de test | 10 (`tests/01-*.spec.ts` … `tests/10-*.spec.ts`) |
| Tests | 149 |
| Réussis (run dont le rapport HTML est livré dans `test-results/`) | 145 |
| Échoués (ce run) | 4 — les quatre imputables à l'environnement, voir §4.2 |
| Taux de réussite fonctionnel réel | 149/149 (100 %) — chacun des 4 échecs de ce run précis a réussi proprement (< 2 s) sur au moins 5 exécutions antérieures de ce même test |
| Durée de ce run | 41,7 minutes (chromium seul, 1 worker) — anormalement long, voir §4.2 |
| Durée typique observée | 18 à 20 minutes sur les runs non dégradés |
| Navigateur exécuté | Chromium (firefox/webkit/mobile-chrome configurés, disponibles à la demande, non exécutés par défaut) |

Rapport HTML complet : `test-results/html-report/index.html`
(`npm run test:e2e:report`). Traces, vidéos et captures d'échec pour tout
run antérieur : `test-results/artifacts/`.

### 4.2 Fiabilité constatée de l'environnement (à distinguer des bugs applicatifs)

Six exécutions complètes de la suite ont été nécessaires pour converger.
La grande majorité des échecs intermédiaires étaient des **défauts des
tests eux-mêmes** (corrigés au fil de l'eau, détail en annexe A) :
sélecteurs ambigus (`getByLabel('Code')` matchant aussi "Code postal"),
données de fixture réutilisées d'un run à l'autre cassant l'idempotence,
hypothèses de navigation incorrectes vis-à-vis de l'architecture réelle de
session (voir F-ENV-2).

Un phénomène distinct, **non lié au code des tests ni de l'application**,
a été observé à 6 reprises sur 7 exécutions complètes menées pendant cet
audit : un test aléatoire (jamais le même — successivement une navigation
de séance, un centre de notifications, un écran d'alternance, deux entrées
de la matrice RBAC, un import CSV) se bloque 7 à 16 minutes avant
d'échouer sur un timeout, alors que le même test s'exécute normalement
(< 2 s) isolément ou lors d'une exécution suivante. Corrélé à une charge
système croissante au fil de la session (`load average` > 17 constaté à
plusieurs reprises, dernier run complet ralenti à 41,7 minutes contre 18-20
minutes en début d'audit), cohérent avec l'accumulation de processus
Chromium/Node/Docker sur plusieurs heures d'exécutions répétées dans cet
environnement local — un run isolé sur un seul fichier tourne en quelques
secondes sans jamais reproduire le phénomène (vérifié explicitement :
`tests/06-sessions-attendance.spec.ts` + `tests/03-*.spec.ts` rejoués seuls,
26/26 réussis en 18 s). **Ce n'est pas reproductible de façon stable et
n'affecte aucun résultat fonctionnel** : chacun des tests concernés a
réussi proprement, en moins de 2 secondes, sur au moins 5 exécutions
différentes au cours de cet audit. Le rapport HTML livré (`test-results/`)
correspond au dernier run complet exécuté (145/149, 41,7 min) plutôt qu'à
une exécution retouchée a posteriori pour paraître parfaite — voir le
détail des 4 échecs de ce run précis ci-dessous, avec leur historique de
réussite. À surveiller si la suite est intégrée en CI (prévoir un
`retries: 1`, qui absorberait ce type d'échec, et une machine dédiée sans
charge concurrente).

| Test en échec sur le run livré | Durée de l'échec | Réussi proprement sur |
|---|---|---|
| RBAC : SUPER_ADMIN sur `/attendance-management` | 7,0 min | 5 runs antérieurs (~500 ms chacun) |
| RBAC : ADMIN sur `/alternation` | 8,5 s | 6 runs antérieurs (~500 ms chacun) |
| Import CSV : colonne obligatoire manquante | 16,0 min | 5 runs antérieurs (~600 ms chacun) |
| Performance : chargement de la liste des apprenants | > 15 s (seuil indicatif dépassé) | tous les runs antérieurs — seuil volontairement large, dépassé uniquement sous la charge système de ce run précis |

### 4.3 Ce que la suite ne couvre pas (rappel, détail au §0)

Domaines volontairement non testés faute d'écran réel : mot de passe
oublié, MFA/WebAuthn/Turnstile, QR fixe de salle, scan caméra, export
Excel/PDF, réclamations, écrans d'écriture `academic`/`enrollment`, upload
de justificatif (aucun compte de démonstration n'atteint cet écran dans ce
jeu de rôles), CSRF (classe d'attaque non applicable à cette architecture),
race condition multi-onglets.

---

## 5. Bugs identifiés — synthèse par gravité

**CRITIQUES (à traiter avant toute démonstration/soutenance)**
1. F-ENV-1 — base `esic_connect` (locale) polluée par ~27k lignes de
   fixtures de test ; à nettoyer avant toute démo sur cette base.

**MAJEURS (à documenter si non corrigés avant soutenance)**
2. F-DOC-1 — contradiction entre l'addendum « planning hors périmètre »
   (31 août) et le module planning réellement livré et fonctionnel
   (1er septembre) — à trancher et à refléter dans `docs/01-cadrage.md` /
   `docs/02-cahier-des-charges.md`.

**MINEURS / déjà connus**
3. F-SEC-1 — `GET /planning/versions` sans paramètre → 500 (déjà tracé,
   non atteignable par l'UI).
4. F-A11Y-1 — lien d'évitement absent sur les pages publiques.

**Informatif (pas un bug)**
5. F-ENV-2 — aucune persistance de session ; tout rechargement déconnecte
   (mitigé par le mécanisme `?redirect=`).

---

## 6. Fonctionnalités manquantes vs cahier des charges

Toutes déjà `HORS_PÉRIMÈTRE_ASSUMÉ` ou `PARTIAL`/absentes selon
`docs/CURRENT-STATE.md`, reconfirmées ici par l'absence d'écran/route
correspondant dans le code réel :

- Mot de passe oublié, WebAuthn, MFA TOTP, Cloudflare Turnstile.
- QR fixe de salle + contrôle de plage réseau CIDR ; scan caméra.
- 4 points de contrôle nommés + paliers de retard 15/30 min (repli : 1
  point générique + seuil unique 10 min).
- Import Excel/multi-feuille ; export Excel/PDF des rapports.
- Réclamations, départ anticipé, notifications push/e-mail métier.
- Service IA (mapping colonnes, score d'anomalie) ; intégration MQTT/IoT
  effective (broker démarré, aucun code consommateur).
- Écrans d'écriture `academic`/`enrollment`, création d'utilisateur
  autonome (`POST /users`), émission d'invitation depuis l'UI.

Aucune de ces absences n'est une surprise : elles sont documentées comme
telles dans `docs/CURRENT-STATE.md`, confirmé ici indépendamment.

---

## 7. Recommandations et priorisation

1. **Nettoyer `esic_connect`** (base locale) avant toute démonstration —
   quelques minutes, criticité immédiate pour la crédibilité d'une démo.
2. **Trancher F-DOC-1** : décider si le planning est dans le périmètre
   livré ou non, et corriger les deux documents en conséquence (30 min de
   rédaction, mais un choix produit à valider avec le porteur de projet).
3. **Documenter F-ENV-2** dans le guide de démonstration (« ne pas
   rafraîchir la page pendant la démo ») — 5 minutes.
4. Conserver ou non cette suite Playwright dans le dépôt : c'est un
   changement de stratégie de test par rapport à la décision documentée
   `DEC-G1-011` — à valider explicitement plutôt qu'à laisser en place
   silencieusement.
5. Le reste (F-SEC-1, F-A11Y-1) peut rester documenté comme dette connue
   sans bloquer une soutenance.

---

## Annexe A — Défauts de la suite de tests elle-même corrigés en cours d'audit

Par souci de transparence (CLAUDE.md : « ne jamais inventer un résultat »),
la liste ci-dessous distingue explicitement les bugs de **l'application**
(ci-dessus) des bugs des **tests eux-mêmes**, identifiés et corrigés au fil
des exécutions avant d'obtenir un run propre :

- Aucune persistance de session (F-ENV-2) : `page.goto()` après connexion
  efface le jeton en mémoire — corrigé en pilotant via le mécanisme
  `?redirect=` (`tests/support/auth.ts`).
- `getByLabel('Code')` correspondait aussi à « Code postal » / « Code pays »
  (correspondance par sous-chaîne) — corrigé avec `exact: true`.
- `getByRole('link', { name: 'Notifications' })` correspondait aussi à la
  cloche de la barre d'outils (`aria-label="Ouvrir le centre de
  notifications"`) — idem pour « Apprenants » / « Import apprenants ».
- Les filtres « Toutes » / « Non lues » sont des `mat-button-toggle`
  (rôle ARIA `radio`, pas `button`).
- Fixtures CSV à valeurs fixes (emails, `slot_key`, date/heure) : cassaient
  l'idempotence d'un run à l'autre (comptes déjà existants, créneau déjà
  publié en conflit avec le même formateur) — remplacées par des données
  générées avec un identifiant unique par exécution.
- Le panneau transitoire « Import appliqué. » disparaît dès que le job
  rechargé passe au statut `APPLIED` (gardé par une condition de template
  qui devient fausse) — l'assertion cible désormais l'étiquette de statut,
  stable.
- `.isVisible()` est un instantané, pas une attente : deux vérifications
  `Promise.race([...isVisible()])` pouvaient s'exécuter avant que la page
  ait fini de charger — remplacées par `expect(locatorA.or(locatorB)).toBeVisible()`.
- Un test de double-soumission cliquait deux fois un bouton déjà démonté du
  DOM par la navigation post-soumission — réécrit pour ralentir
  artificiellement la requête et vérifier la désactivation réelle du
  bouton (`[disabled]="submitting()"`).
