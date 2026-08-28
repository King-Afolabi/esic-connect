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
Authentification locale email/mot de passe créée (branche
feature/authentication-foundation, non fusionnée, non committée) :
POST /api/v1/auth/login, JWT HS256 stateless, réponse publique uniforme
en cas d'échec. Aucun MFA, WebAuthn, refresh token, inscription
publique ni réinitialisation de mot de passe pour le moment.
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
| Flyway | TESTED (V1 tables identité/audit, V2 seed des 6 rôles — migrations appliquées et vérifiées) |
| Authentification | TESTED (`POST /api/v1/auth/login` : email/mot de passe, JWT HS256 stateless, `last_login_at`, audit succès/échec ; réponse publique uniforme vérifiée pour email inconnu/mauvais mot de passe/compte non actif ; routes protégées refusent sans jeton ; MFA/WebAuthn/refresh token non implémentés) |
| Rôles | TESTED (persistance `role`/`user_role` : 6 rôles système, unicité d'affectation active, réattribution après clôture — pas encore de service métier ni d'API) |
| Référentiels | TODO |
| Import apprenants | TODO |
| Import planning | TODO |
| Séances | TODO |
| Émargement | TODO |
| Rapports | TODO |
| Audit | TESTED (persistance `audit_event` : acteur nullable après suppression du compte, snapshot conservé — pas encore d'écriture depuis un service métier réel) |
| FastAPI | TODO |
| MQTT | TODO |
| Raspberry Pi | TODO |
| WebAuthn | TODO |
| Staging | TODO |

## Prochaine priorité

```text
Créer les référentiels pédagogiques (formation, classe, inscription) et
le contrôle d'accès par périmètre pédagogique, sur la base du socle
d'authentification existant — voir
docs/05b-sprint-backlog-prototype.md T-J1-022, T-J1-023, T-J1-030 à
T-J1-032. /auth/logout et la révocation de session restent à évaluer
(jeton stateless sans état serveur pour l'instant).
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

## Règle de mise à jour

Ce document doit refléter le dépôt.

Ne jamais déclarer :

- TESTÉ sans commande exécutée ;
- DÉMONTRÉ sans vérification manuelle ;
- DÉPLOYÉ sans URL ou preuve ;
- FONCTIONNEL uniquement parce que le code existe.