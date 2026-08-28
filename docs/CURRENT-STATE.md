# État courant — ESIC Connect

## Dernière mise à jour

```text
28 août 2026
```

## Dernier commit stable

```text
À renseigner
```

## Phase actuelle

```text
Flux d'invitation et d'activation de compte créé (branche
feature/account-invitation, non fusionnée, non committée) :
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
jeton). Toujours aucun MFA, WebAuthn, refresh token ni réinitialisation
de mot de passe.

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
| Angular | TODO |
| MySQL | TESTED (healthy, auth root et `esic_app` vérifiée) |
| Redis | TESTED (healthy, auth vérifiée) |
| Flyway | TESTED (V1 tables identité/audit, V2 seed des 6 rôles, V3 table `account_invitation` — migrations appliquées et vérifiées, schéma en version 3) |
| Authentification | TESTED (`POST /api/v1/auth/login` : email/mot de passe, JWT HS256 stateless, `last_login_at`, audit succès/échec ; réponse publique uniforme vérifiée pour email inconnu/mauvais mot de passe/compte non actif ; routes protégées refusent sans jeton ; MFA/WebAuthn/refresh token non implémentés) |
| Rôles | TESTED (persistance `role`/`user_role` : 6 rôles système, unicité d'affectation active, réattribution après clôture ; désormais attribués via `user_role` lors de l'émission d'une invitation — pas encore d'API de gestion dédiée) |
| Invitation / activation | TESTED (`POST /api/v1/account-invitations` protégé par rôle, `GET …/validate` et `POST …/activate` publics ; migration V3 `account_invitation` ; jeton SecureRandom 32 o Base64URL, empreinte SHA-256 unique stockée, TTL configurable strictement positif, révocation des invitations PENDING antérieures, jeton à usage unique ; validation publique strictement générique ; email d'activation via Mailpit ; audit `ACCOUNT_INVITATION_ISSUED`/`ACCOUNT_ACTIVATED` sans jeton) |
| Notification (email) | TESTED (module `notification` : écouteur `AFTER_COMMIT` sur `AccountInvitationIssuedEvent`, envoi SMTP `SimpleMailMessage` via Mailpit ; échec d'envoi avalé, invitation conservée, log sans jeton/email/lien ; pas de file persistante — dette technique) |
| Référentiels | TODO |
| Import apprenants | TODO |
| Import planning | TODO |
| Séances | TODO |
| Émargement | TODO |
| Rapports | TODO |
| Audit | TESTED (persistance `audit_event` + écriture depuis flux métier réels : connexion réussie/refusée, émission d'invitation, activation de compte — jamais de jeton ni de donnée sensible) |
| FastAPI | TODO |
| MQTT | TODO |
| Raspberry Pi | TODO |
| WebAuthn | TODO |
| CI (GitHub Actions) | IMPLEMENTED (`.github/workflows/backend-ci.yml` : déclenché sur PR vers `main` et push sur `main` ; job unique `ubuntu-latest`, `permissions: contents: read`, `timeout-minutes: 20`, concurrence avec annulation des exécutions obsolètes ; Java 21 Temurin + cache Maven ; services `mysql:8.4` et `redis:7.4-alpine` (mot de passe via `command: redis-server --requirepass`) avec identifiants dédiés CI non sensibles ; exécute `./mvnw --batch-mode test` depuis `backend/` ; aucun usage de `.env`, aucun SMTP réel. Non encore exécuté sur GitHub — statut à confirmer au premier run) |
| Staging | TODO |

## Prochaine priorité

```text
Créer les référentiels pédagogiques (formation, classe, inscription) et
le contrôle d'accès par périmètre pédagogique, sur la base du socle
d'authentification existant — voir
docs/05b-sprint-backlog-prototype.md T-J1-022, T-J1-023, T-J1-030 à
T-J1-032. /auth/logout et la révocation de session restent à évaluer
(jeton stateless sans état serveur pour l'instant).

Dettes techniques à traiter ultérieurement :
- file persistante + reprise garantie pour les emails d'activation
  (actuellement envoi synchrone après commit, échec seulement journalisé) ;
- purge / expiration explicite des invitations `PENDING` périmées ;
- création de comptes `PENDING_ACTIVATION` par API (l'émission cible
  aujourd'hui un compte déjà existant, créé par fixture ou futur import).
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

## Règle de mise à jour

Ce document doit refléter le dépôt.

Ne jamais déclarer :

- TESTÉ sans commande exécutée ;
- DÉMONTRÉ sans vérification manuelle ;
- DÉPLOYÉ sans URL ou preuve ;
- FONCTIONNEL uniquement parce que le code existe.