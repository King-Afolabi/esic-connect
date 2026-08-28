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
Socle Spring Boot initialisé (branche feature/spring-boot-foundation,
non fusionnée, non committée) — aucune entité métier ni authentification
réelle pour le moment
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
| Flyway | TESTED (connexion + création de `flyway_schema_history` vérifiées ; aucune migration métier écrite) |
| Authentification | TODO |
| Rôles | TODO |
| Référentiels | TODO |
| Import apprenants | TODO |
| Import planning | TODO |
| Séances | TODO |
| Émargement | TODO |
| Rapports | TODO |
| Audit | TODO |
| FastAPI | TODO |
| MQTT | TODO |
| Raspberry Pi | TODO |
| WebAuthn | TODO |
| Staging | TODO |

## Prochaine priorité

```text
Créer les migrations Flyway des tables de référence (utilisateur, rôle,
formation, classe, inscription) puis implémenter l'authentification
(module identity) — voir docs/05b-sprint-backlog-prototype.md T-J1-012
et T-J1-021.
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
`feature/spring-boot-foundation`, non fusionnée et non committée.

## Règle de mise à jour

Ce document doit refléter le dépôt.

Ne jamais déclarer :

- TESTÉ sans commande exécutée ;
- DÉMONTRÉ sans vérification manuelle ;
- DÉPLOYÉ sans URL ou preuve ;
- FONCTIONNEL uniquement parce que le code existe.