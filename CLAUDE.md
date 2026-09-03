# CLAUDE.md — ESIC Connect

> Ce fichier était stocké avec un markdown **échappé** (`\#`, `\-`,
> `\*\*`) et enveloppé dans une clôture de code : il ne s'affichait pas
> comme du markdown et ses trois références obligatoires étaient
> illisibles. Corrigé le 3 septembre 2026, en même temps que
> `docs/01-cadrage.md` et `docs/02-cahier-des-charges.md`, qui souffraient
> du même défaut. **Ne jamais recoller ces fichiers depuis un éditeur
> enrichi.**

## Références

Lire avant toute tâche métier ou technique :

- @docs/01-cadrage.md
- @docs/02-cahier-des-charges.md
- @docs/CURRENT-STATE.md

Le cahier des charges définit les exigences.
Le code et les tests définissent ce qui est réellement réalisé.

## Objectif prioritaire

```text
Import apprenants
→ Import planning
→ Publication
→ Création des séances
→ Ouverture par le formateur
→ Émargement
→ Rapport
```

Le parcours ci-dessus est **entièrement livré** depuis le lot G1
(PR #40) et rejoué de bout en bout dans un vrai navigateur
(`tests/`, `audit-report.md`). L'addendum du 31 août 2026 qui déclarait le
planning hors périmètre est **caduc** : voir `docs/01-cadrage.md` §23.6.

## Stack

**Réellement utilisé :**

- Java 21, Spring Boot 3.5, Maven, Spring Modulith 1.4 (14 modules)
- Angular 21.2 (standalone, zoneless, signaux), Angular Material
- MySQL 8 (Flyway V1 → V16), Redis 7 (jetons d'émargement uniquement)
- Docker Compose (mysql, redis, mailpit, mosquitto)
- Playwright / Chromium pour la recette e2e navigateur

**Conçu mais sans aucune ligne de code — ne jamais présenter comme livré :**

- Python / FastAPI (service IA)
- Raspberry Pi 4, MQTT (le broker Mosquitto démarre, rien ne le consomme)
- PWA installable / offline

## Règles

- Utiliser uniquement des données fictives.
- Ne jamais enregistrer de secret dans Git.
- Ne jamais inventer une fonctionnalité, un test ou un résultat.
- Respecter strictement le cahier des charges.
- Demander confirmation avant de modifier une règle métier.
- Commencer par les exigences `MUST`.
- Écrire et exécuter les tests.
- Contrôler les autorisations côté Spring Boot.
- Ne pas supprimer les historiques.
- Ne pas utiliser `localStorage` pour les jetons sensibles.
- Ne pas créer de microservices Java, de MongoDB ou de Kubernetes.
- Ne pas commencer par AWS.
- Ne pas réécrire entièrement un document pour une modification mineure.
- Ne figer dans le dépôt aucun identifiant régénéré à la recréation de la
  base (`public_id` est un `UUID.randomUUID()`) : le résoudre à
  l'exécution.
- Ne jamais écrire de mot de passe, même « de démonstration » ou « de
  repli », dans un fichier suivi par Git.

## Méthode

Pour chaque tâche :

1. Lire uniquement les fichiers utiles.
2. Examiner le code existant.
3. Proposer un plan court.
4. Implémenter une seule fonctionnalité.
5. Écrire et exécuter les tests.
6. Mettre à jour `docs/CURRENT-STATE.md`.
7. Indiquer les fichiers modifiés, les tests et les limites.

## Statuts

Vocabulaire **unique** du dépôt, à reprendre tel quel (le référentiel
complet est en tête de `README.md` et dans `docs/CURRENT-STATE.md`) :

| Statut | Signification |
|---|---|
| `IMPLEMENTED_AND_TESTED` | code livré **et** couvert par des tests automatisés passants |
| `PARTIAL` | une partie seulement de l'exigence est livrée — jamais à présenter comme complète |
| `NOT_IMPLEMENTED` | aucun code ; limite explicitement assumée |
| `HORS_PÉRIMÈTRE_ASSUMÉ` | exclusion décidée et documentée pour cette livraison |
| `NOT_PERFORMED` | action jamais exécutée (démonstration manuelle, déploiement) |
| `À_DÉFINIR` | décision non prise (ex. rétention RGPD) |

Ne jamais confondre **implémenté**, **testé automatiquement**, **vérifié
manuellement** et **démontré**. Un navigateur piloté par un script n'est
pas une démonstration manuelle.

## Définition de terminé

Une tâche est terminée lorsque :

- le code compile ;
- les tests passent (`./scripts/verify-all.sh` enchaîne back, front,
  contrôle de type e2e, scripts de démonstration et diagnostic de base) ;
- la sécurité est contrôlée ;
- la documentation reflète la réalité ;
- les commandes de vérification sont fournies.

## Commandes utiles

```bash
./scripts/verify-all.sh            # tout ce qui doit être vert
./scripts/db-doctor.sh             # diagnostic base (code 2 = polluée)
./scripts/db-reset.sh <base>       # sauvegarde → recréation → Flyway → contrôle
npm run test:e2e                   # recette navigateur (pile démarrée requise)
```

Installation, remise à zéro, déploiement et verrous restants :
`docs/13-guide-deploiement.md`.
