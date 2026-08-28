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
Conception documentaire terminée — Initialisation du code à commencer
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
| Dépôt Git | NON INITIALISÉ (aucun `.git`) |
| Docker Compose | TESTED |
| Spring Boot | TODO |
| Angular | TODO |
| MySQL | TESTED (healthy, auth root et `esic_app` vérifiée) |
| Redis | TESTED (healthy, auth vérifiée) |
| Flyway | TODO |
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
Initialiser le dépôt, Docker Compose et Spring Boot.
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

## Règle de mise à jour

Ce document doit refléter le dépôt.

Ne jamais déclarer :

- TESTÉ sans commande exécutée ;
- DÉMONTRÉ sans vérification manuelle ;
- DÉPLOYÉ sans URL ou preuve ;
- FONCTIONNEL uniquement parce que le code existe.