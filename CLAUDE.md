# CLAUDE.md — ESIC Connect

## Ce qu'est ce produit

**ESIC Connect** est une plateforme complète de gestion pédagogique,
d'émargement et de suivi de l'assiduité pour l'ESIC. Ce n'est pas une
maquette ni une preuve de concept : la cible est une application
déployée et exploitée.

## Références

À lire avant toute tâche métier, d'architecture ou de documentation :

- @docs/01-cadrage.md — vision, objectifs, acteurs, exclusions
- @docs/02-cahier-des-charges.md — exigences, règles, critères d'acceptation
- @docs/CURRENT-STATE.md — état réel de l'implémentation

Pour une tâche purement technique, lire seulement `CURRENT-STATE.md` et
les fichiers concernés.

**Le cahier des charges définit ce qui doit exister. Le code et les
tests définissent ce qui existe.** En cas de contradiction entre un
document et le dépôt, le dépôt a raison, et le document doit être
corrigé.

## Chaîne de valeur

```text
Référentiels
→ Import des apprenants → Invitation → Activation
→ Import ou construction du planning → Contrôle → Publication
→ Création des séances → Affectation → Remplacement
→ Ouverture → Émargement → Points de contrôle
→ Assiduité → Justificatifs → Réclamations
→ Tableaux de bord → Rapports → Exports → Attestations
```

## Pile technique

**Utilisé :**

- Java 21, Spring Boot 3.5, Maven, Spring Modulith (monolithe modulaire)
- Angular 21 (standalone, zoneless, signaux), Angular Material
- MySQL 8 (source de vérité, Flyway), Redis 7 (temporaire uniquement)
- Docker Compose : mysql, redis, mailpit, mosquitto
- Playwright / Chromium pour la recette navigateur

**Prévu et à construire — ne jamais présenter comme livré avant de
l'avoir écrit et testé :**

- Python / FastAPI (service d'IA)
- MQTT / Raspberry Pi (borne d'émargement) + simulateur logiciel
- PWA installable, hors ligne, notifications push
- WebAuthn, MFA TOTP, Turnstile
- Exports Excel et PDF, attestations
- Microsoft Graph, Teams, flux iCalendar

L'état exact de chacune de ces briques est dans `docs/CURRENT-STATE.md`.

## Règles impératives

- Utiliser uniquement des données fictives.
- Ne jamais placer un secret dans Git — même « de test », même « de
  repli », même « de démonstration ».
- Ne jamais inventer une fonctionnalité, un test ou un résultat.
- Ne jamais déclarer terminé sans preuve exécutée.
- Ne pas confondre implémenté, testé automatiquement, vérifié
  manuellement et démontré. Un navigateur piloté par un script n'est pas
  une démonstration manuelle.
- Demander confirmation avant de modifier une règle de gestion.
- Contrôler les autorisations côté serveur, jamais dans l'affichage
  Angular seul.
- Ne pas stocker de jeton sensible dans `localStorage`.
- Ne figer aucun identifiant régénéré à la recréation de la base
  (`public_id` est un `UUID.randomUUID()`) : le résoudre à l'exécution.
- Ne pas créer de microservices Java, de MongoDB ni de Kubernetes.
- Ne pas réécrire entièrement un document pour une modification mineure.
- Aucune entité JPA partagée entre modules : communication par port
  public ou par événement.
- Tout effet de bord externe passe par l'outbox transactionnelle.

## Méthode

Pour chaque tâche :

1. Lire uniquement les fichiers utiles.
2. Examiner le code existant avant d'en écrire.
3. Proposer un plan court.
4. Implémenter une capacité à la fois.
5. Écrire les tests **avec** le code, et les exécuter.
6. Mettre à jour `docs/CURRENT-STATE.md`.
7. Indiquer les fichiers modifiés, les tests exécutés et les limites
   restantes.

## Statuts

Vocabulaire unique du dépôt :

| Statut | Signification |
|---|---|
| `IMPLEMENTED_AND_TESTED` | code livré **et** couvert par des tests automatisés passants |
| `PARTIAL` | une partie seulement est livrée — jamais présentée comme complète |
| `NOT_IMPLEMENTED` | aucun code ; limite explicitement assumée |
| `NOT_PERFORMED` | action jamais exécutée (démonstration manuelle, déploiement) |
| `À_DÉFINIR` | décision non prise |

## Définition de terminé

- le code compile ;
- les tests passent (`./scripts/verify-all.sh`) ;
- les autorisations sont testées, refus compris ;
- la sécurité est contrôlée ;
- l'accessibilité est vérifiée ;
- la documentation reflète la réalité ;
- les commandes de vérification sont fournies.

## Commandes

```bash
./scripts/verify-all.sh            # tout ce qui doit être vert
./scripts/db-doctor.sh             # diagnostic base (code 2 = polluée)
./scripts/db-reset.sh <base>       # sauvegarde → recréation → Flyway → contrôle
npm run test:e2e                   # recette navigateur (pile démarrée requise)
```

Installation, exploitation et déploiement : `docs/11-guide-deploiement.md`.
Ce que le porteur doit préparer côté comptes et matériel :
`docs/12-prerequis-externes.md`.
