# Product Backlog — ESIC Connect

## Métadonnées

| Élément | Valeur |
|---|---|
| Projet | ESIC Connect |
| Document | Product Backlog |
| Période de référence | Du 1er mars au 31 août 2026 |
| Méthode | Scrum adapté avec tableau Kanban |
| Durée d’un sprint | Deux semaines |
| Product Owner | Monsieur BANKA |
| Scrum Master | Monsieur INOUSSA Chaabane |
| Architecte et développeur | Abubacar AFOLABI |
| Version | 1.0 |
| Date de mise à jour | 28 août 2026 |
| Statut | Backlog initial à affiner |

---

# 1. Objet

Ce document recense et priorise les besoins fonctionnels, techniques,
documentaires et sécuritaires du projet **ESIC Connect**.

Il sert de référence pour :

- la planification des sprints ;
- le suivi dans GitHub Projects ;
- la création des issues ;
- la mesure de l’avancement ;
- la préparation des démonstrations ;
- la traçabilité avec le cahier des charges ;
- la couverture des blocs RNCP 39394.

Ce document représente une **trajectoire de projet sur six mois**.

Il ne constitue pas, à lui seul, une preuve que toutes les
fonctionnalités ont été réalisées.

L’état réel de chaque élément doit être renseigné au moyen des statuts :

- `À faire` ;
- `Prêt` ;
- `En cours` ;
- `En revue` ;
- `En test` ;
- `Terminé`.

Le niveau de livraison doit être renseigné séparément :

- `À réaliser` ;
- `Implémenté` ;
- `Testé` ;
- `Démontré` ;
- `Simulé` ;
- `Conçu` ;
- `Reporté`.

---

# 2. Vision produit

## 2.1 Product Goal

> Mettre à disposition de l’ESIC une plateforme centralisée, sécurisée
> et intelligente permettant de gérer les apprenants, les plannings,
> les séances et l’assiduité, depuis l’importation des données jusqu’à
> la production de rapports, en prenant en charge les cours
> présentiels, distanciels et hybrides.

## 2.2 Parcours de valeur principal

```text
Importation des apprenants
→ Contrôle et confirmation
→ Importation du planning
→ Validation et publication
→ Création automatique des séances
→ Consultation par les acteurs
→ Ouverture de l’émargement
→ Validation des présences
→ Traitement des exceptions
→ Production des rapports
```

---

# 3. Gouvernance

## 3.1 Équipe

| Acteur | Responsabilités |
|---|---|
| Monsieur BANKA | Commanditaire, responsable pédagogique et Product Owner |
| Monsieur INOUSSA Chaabane | Scrum Master, facilitation et suivi de la méthode |
| Abubacar AFOLABI | Architecture, développement full-stack, données, IA, IoT et cybersécurité |

## 3.2 Responsabilités du Product Owner

Le Product Owner :

- porte la vision ;
- ordonne le Product Backlog ;
- définit les priorités métier ;
- clarifie les besoins ;
- valide les résultats fonctionnels ;
- accepte ou refuse les éléments présentés en revue.

## 3.3 Responsabilités du Scrum Master

Le Scrum Master :

- facilite les événements ;
- accompagne l’application de la méthode ;
- aide à identifier les blocages ;
- facilite l’amélioration continue ;
- protège la clarté du processus.

## 3.4 Responsabilités du développeur-architecte

Le développeur-architecte :

- analyse les besoins ;
- propose l’architecture ;
- développe les composants ;
- sécurise la plateforme ;
- crée les tests ;
- maintient les documents ;
- prépare les preuves ;
- présente les résultats.

---

# 4. Méthode d’estimation

## 4.1 Story points

Les éléments sont estimés avec la suite suivante :

```text
1, 2, 3, 5, 8, 13
```

Les points représentent une combinaison de :

- complexité ;
- effort ;
- incertitude ;
- risques ;
- volume de tests ;
- dépendances.

## 4.2 Interprétation

| Points | Interprétation |
|---:|---|
| 1 | Très petite modification |
| 2 | Petite fonctionnalité connue |
| 3 | Fonctionnalité simple |
| 5 | Fonctionnalité moyenne |
| 8 | Fonctionnalité complexe |
| 13 | Fonctionnalité trop importante ou incertaine à découper |

Un élément estimé à 13 points doit normalement être découpé avant son
intégration dans un sprint.

---

# 5. Priorisation MoSCoW

| Priorité | Définition |
|---|---|
| `MUST` | Indispensable au parcours principal |
| `SHOULD` | Important, mais le système peut fonctionner temporairement sans |
| `COULD` | Apporte une valeur supplémentaire |
| `WON'T` | Non retenu dans la version actuelle |
| `FUTURE` | Prévu dans une évolution ultérieure |

---

# 6. Definition of Ready

Une user story est `Prête` lorsque :

- l’acteur est identifié ;
- le besoin est compréhensible ;
- la valeur métier est précisée ;
- les critères d’acceptation existent ;
- les dépendances principales sont connues ;
- les données nécessaires sont identifiées ;
- les questions bloquantes sont résolues ;
- l’estimation est réalisée ;
- la story peut raisonnablement être terminée dans un sprint.

---

# 7. Definition of Done

Une user story est `Terminée` lorsque :

- le code compile ;
- les migrations nécessaires existent ;
- les tests prioritaires passent ;
- les autorisations sont contrôlées ;
- les erreurs sont gérées ;
- l’API est documentée ;
- la documentation reflète l’état réel ;
- aucune donnée réelle n’est utilisée ;
- aucun secret n’est présent dans Git ;
- les preuves sont disponibles ;
- le Product Owner peut vérifier le résultat.

---

# 8. Epics

| ID | Epic | Objectif |
|---|---|---|
| EP-01 | Pilotage et documentation | Cadrer, planifier et tracer le projet |
| EP-02 | UX et accessibilité | Concevoir des parcours simples et accessibles |
| EP-03 | Architecture et infrastructure | Construire une base technique portable |
| EP-04 | Identité et sécurité | Gérer les comptes, rôles et authentifications |
| EP-05 | Organisation pédagogique | Gérer les formations, promotions et classes |
| EP-06 | Apprenants et inscriptions | Importer et historiser les apprenants |
| EP-07 | Alternance | Gérer les rythmes école-entreprise |
| EP-08 | Planning | Importer, construire, versionner et publier |
| EP-09 | Séances et formateurs | Gérer les séances, salles et remplacements |
| EP-10 | Émargement et assiduité | Enregistrer et calculer les présences |
| EP-11 | Justificatifs et réclamations | Gérer les exceptions et échanges |
| EP-12 | Notifications | Informer les acteurs |
| EP-13 | Tableaux de bord et rapports | Produire les indicateurs d’assiduité |
| EP-14 | Intelligence artificielle | Assister les imports et détecter les anomalies |
| EP-15 | IoT | Intégrer une Raspberry Pi sécurisée |
| EP-16 | Cybersécurité | Renforcer, superviser et auditer |
| EP-17 | Qualité et tests | Valider le fonctionnement et les performances |
| EP-18 | Déploiement et exploitation | Préparer staging, production et continuité |
| EP-19 | Intégrations externes | Préparer BERRA et Microsoft 365 |
| EP-20 | Soutenance | Produire le rapport, les preuves et la présentation |

---

# 9. Backlog détaillé

## EP-01 — Pilotage et documentation

### US-001 — Formaliser le cadrage

**En tant que** porteur du projet,  
**je souhaite** formaliser le contexte, les objectifs et le périmètre,  
**afin de** disposer d’une référence commune.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S01 |
| Bloc RNCP | BC01 |
| Livrable | `docs/01-cadrage.md` |

#### Critères d’acceptation

- le contexte actuel est décrit ;
- les acteurs sont identifiés ;
- les objectifs sont définis ;
- le périmètre et les exclusions sont distingués ;
- les risques principaux sont recensés.

---

### US-002 — Rédiger le cahier des charges

**En tant que** Product Owner,  
**je souhaite** disposer d’exigences détaillées,  
**afin de** guider la conception, le développement et la recette.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S02-S03 |
| Bloc RNCP | BC01/BC02/BC03/BC04 |
| Livrable | `docs/02-cahier-des-charges.md` |

#### Critères d’acceptation

- les exigences fonctionnelles sont identifiées ;
- les exigences non fonctionnelles sont décrites ;
- les règles métier sont numérotées ;
- les critères d’acceptation principaux existent ;
- les priorités sont précisées.

---

### US-003 — Définir l’architecture

**En tant que** architecte,  
**je souhaite** définir les composants et leurs interactions,  
**afin de** construire une solution cohérente et évolutive.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S05 |
| Bloc RNCP | BC02/BC03/BC04 |
| Livrable | `docs/03-architecture.md` |

---

### US-004 — Définir le modèle de données

**En tant que** développeur,  
**je souhaite** disposer d’un modèle relationnel historisé,  
**afin de** préserver l’intégrité des données.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S05 |
| Bloc RNCP | BC02/BC03 |
| Livrable | `docs/04-modele-donnees.md` |

---

### US-005 — Maintenir la matrice RNCP

**En tant que** candidat,  
**je souhaite** relier les réalisations aux blocs RNCP,  
**afin de** démontrer la couverture des compétences.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S01-S13 |
| Bloc RNCP | BC01/BC02/BC03/BC04 |

---

### US-006 — Maintenir le journal d’utilisation de l’IA

**En tant que** candidat,  
**je souhaite** documenter l’usage des assistants IA,  
**afin de** démontrer une utilisation contrôlée et vérifiée.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 3 |
| Sprint cible | S01-S13 |
| Bloc RNCP | BC01/BC02/BC03/BC04 |

---

## EP-02 — UX et accessibilité

### US-010 — Concevoir les personas

**En tant que** concepteur,  
**je souhaite** formaliser les profils des utilisateurs,  
**afin de** concevoir des interfaces adaptées.

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 3 |
| Sprint cible | S04 |
| Bloc RNCP | BC01/BC02 |

Personas :

- responsable pédagogique ;
- formateur ;
- apprenant ;
- administration ;
- administrateur technique.

---

### US-011 — Produire les parcours utilisateurs

**En tant que** concepteur,  
**je souhaite** représenter les parcours principaux,  
**afin de** réduire les ambiguïtés avant le développement.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S04 |
| Bloc RNCP | BC02 |

---

### US-012 — Concevoir les maquettes

**En tant que** utilisateur,  
**je souhaite** disposer d’une interface simple et cohérente,  
**afin de** réaliser rapidement mes tâches.

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 8 |
| Sprint cible | S04 |
| Bloc RNCP | BC02 |

---

### US-013 — Garantir les alternatives accessibles

**En tant que** personne ne pouvant pas utiliser la caméra ou WebAuthn,  
**je souhaite** disposer d’une solution alternative,  
**afin de** pouvoir utiliser les fonctions essentielles.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S04/S11 |
| Bloc RNCP | BC01/BC02 |

---

## EP-03 — Architecture et infrastructure

### US-020 — Initialiser le dépôt

**En tant que** développeur,  
**je souhaite** disposer d’une arborescence structurée,  
**afin de** centraliser le code et les documents.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 2 |
| Sprint cible | S01 |
| Bloc RNCP | BC01/BC02 |

---

### US-021 — Conteneuriser l’infrastructure locale

**En tant que** développeur,  
**je souhaite** lancer MySQL, Redis, Mailpit et Mosquitto avec Docker,  
**afin de** disposer d’un environnement reproductible.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S06 |
| Bloc RNCP | BC03/BC04 |

---

### US-022 — Initialiser Spring Boot

**En tant que** développeur,  
**je souhaite** disposer d’un back-end modulaire,  
**afin de** implémenter les règles métier.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S06 |
| Bloc RNCP | BC02/BC03 |

---

### US-023 — Initialiser Angular

**En tant que** développeur,  
**je souhaite** disposer d’une interface Angular Material,  
**afin de** construire les écrans des utilisateurs.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S06 |
| Bloc RNCP | BC02 |

---

### US-024 — Gérer les profils d’environnement

**En tant que** exploitant,  
**je souhaite** séparer local, test, staging et production,  
**afin de** sécuriser et fiabiliser les déploiements.

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S06/S13 |
| Bloc RNCP | BC03 |

---

### US-025 — Documenter l’API

**En tant que** développeur,  
**je souhaite** disposer d’une documentation OpenAPI,  
**afin de** tester et comprendre les routes.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 3 |
| Sprint cible | S06-S13 |
| Bloc RNCP | BC02 |

---

## EP-04 — Identité et sécurité

### US-030 — Authentifier un utilisateur

**En tant que** membre de l’ESIC,  
**je souhaite** me connecter avec mon email et mon mot de passe,  
**afin de** consulter mon espace.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S07 |
| Bloc RNCP | BC02/BC03 |

#### Critères d’acceptation

- un compte actif peut se connecter ;
- un compte suspendu est refusé ;
- les mots de passe sont hachés ;
- les erreurs restent neutres ;
- la connexion est auditée.

---

### US-031 — Gérer plusieurs rôles

**En tant que** responsable également formateur,  
**je souhaite** changer de contexte,  
**afin de** réaliser mes différentes missions.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S07 |
| Bloc RNCP | BC02/BC03 |

---

### US-032 — Appliquer le périmètre pédagogique

**En tant que** responsable pédagogique,  
**je souhaite** consulter uniquement mes formations,  
**afin de** protéger les données des autres périmètres.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S07 |
| Bloc RNCP | BC03 |

---

### US-033 — Activer un compte par invitation

**En tant que** nouvel utilisateur,  
**je souhaite** recevoir un lien d’activation,  
**afin de** créer mon accès à la plateforme.

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S07 |
| Bloc RNCP | BC02/BC03 |

---

### US-034 — Réinitialiser un mot de passe

**En tant que** utilisateur,  
**je souhaite** récupérer mon compte de manière sécurisée,  
**afin de** retrouver l’accès à la plateforme.

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S07 |
| Bloc RNCP | BC03 |

---

### US-035 — Utiliser WebAuthn

**En tant que** utilisateur sur un terminal reconnu,  
**je souhaite** utiliser une passkey ou une confirmation locale,  
**afin de** simplifier et renforcer ma connexion.

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 13 |
| Sprint cible | S11 |
| Bloc RNCP | BC02/BC03 |
| Recommandation | Découper avant planification |

---

### US-036 — Mettre en place le MFA adaptatif

**En tant que** responsable de la sécurité,  
**je souhaite** renforcer les connexions à risque,  
**afin de** limiter les compromissions sans alourdir chaque connexion.

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 8 |
| Sprint cible | S11/S12 |
| Bloc RNCP | BC03 |

---

## EP-05 — Organisation pédagogique

### US-040 — Gérer les formations

**En tant que** responsable,  
**je souhaite** créer et archiver les formations,  
**afin de** structurer l’offre pédagogique.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 3 |
| Sprint cible | S08 |
| Bloc RNCP | BC02 |

---

### US-041 — Gérer les niveaux et promotions

**En tant que** responsable,  
**je souhaite** rattacher les niveaux à des promotions,  
**afin de** représenter les parcours annuels.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S08 |
| Bloc RNCP | BC02 |

---

### US-042 — Gérer les classes

**En tant que** responsable pédagogique,  
**je souhaite** créer une classe dans mon périmètre,  
**afin de** y inscrire les apprenants.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S08 |
| Bloc RNCP | BC02/BC03 |

---

### US-043 — Déléguer temporairement une formation

**En tant que** responsable principal,  
**je souhaite** déléguer temporairement une formation,  
**afin de** garantir la continuité de gestion.

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S08 |
| Bloc RNCP | BC01/BC02 |

---

### US-044 — Gérer les matières

**En tant que** responsable,  
**je souhaite** gérer un référentiel de matières,  
**afin de** les associer aux plannings et séances.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 3 |
| Sprint cible | S08 |
| Bloc RNCP | BC02 |

---

## EP-06 — Apprenants et inscriptions

### US-050 — Simuler l’import CSV des apprenants

**En tant que** responsable pédagogique,  
**je souhaite** analyser un fichier CSV avant de l’appliquer,  
**afin de** détecter les erreurs et les doublons.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S09 |
| Bloc RNCP | BC02/BC03 |

---

### US-051 — Confirmer l’import des apprenants

**En tant que** responsable pédagogique,  
**je souhaite** confirmer une simulation valide,  
**afin de** créer ou mettre à jour les comptes et inscriptions.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S09 |
| Bloc RNCP | BC02 |

---

### US-052 — Importer un fichier Excel multifeuille

**En tant que** responsable pédagogique,  
**je souhaite** importer plusieurs classes depuis un classeur,  
**afin de** réduire le nombre d’opérations manuelles.

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 8 |
| Sprint cible | S09 |
| Bloc RNCP | BC02 |

---

### US-053 — Conserver l’historique de classe

**En tant que** responsable,  
**je souhaite** déplacer un apprenant sans perdre son historique,  
**afin de** suivre son évolution.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S08/S09 |
| Bloc RNCP | BC02/BC03 |

---

### US-054 — Réaliser des opérations de masse

**En tant que** administration scolaire,  
**je souhaite** suspendre ou archiver plusieurs comptes,  
**afin de** traiter les départs efficacement.

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 8 |
| Sprint cible | S09 |
| Bloc RNCP | BC02/BC03 |

---

### US-055 — Gérer un apprenant provisoire

**En tant que** formateur,  
**je souhaite** enregistrer provisoirement un nouvel apprenant,  
**afin de** ne pas bloquer sa première séance.

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S11 |
| Bloc RNCP | BC02 |

---

## EP-07 — Alternance

### US-060 — Gérer le rythme trois jours/deux jours

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S08 |
| Bloc RNCP | BC02 |

### US-061 — Gérer une semaine sur quatre

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S08 |
| Bloc RNCP | BC02 |

### US-062 — Gérer deux semaines sur quatre

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S08 |
| Bloc RNCP | BC02 |

### US-063 — Gérer les exceptions

**En tant que** responsable,  
**je souhaite** déclarer une présence exceptionnelle à l’école,  
**afin de** adapter le calendrier sans produire une fausse absence.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S08/S10 |
| Bloc RNCP | BC02 |

---

## EP-08 — Planning

### US-070 — Simuler l’import CSV du planning

**En tant que** responsable pédagogique,  
**je souhaite** prévisualiser un planning,  
**afin de** corriger les erreurs avant publication.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S09 |
| Bloc RNCP | BC02 |

---

### US-071 — Publier le planning

**En tant que** responsable pédagogique,  
**je souhaite** publier une version validée,  
**afin de** créer les séances et informer les acteurs.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S09/S10 |
| Bloc RNCP | BC02/BC03 |

---

### US-072 — Versionner le planning

**En tant que** responsable,  
**je souhaite** conserver les versions précédentes,  
**afin de** comprendre les modifications et revenir en arrière.

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S10 |
| Bloc RNCP | BC02/BC03 |

---

### US-073 — Créer le planning dans l’interface

**En tant que** responsable,  
**je souhaite** ajouter et modifier les créneaux dans un calendrier,  
**afin de** ne pas dépendre uniquement des imports.

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 13 |
| Sprint cible | S10 |
| Bloc RNCP | BC02 |
| Recommandation | Découper |

---

### US-074 — Détecter les conflits

**En tant que** responsable,  
**je souhaite** détecter les conflits de salle, classe et formateur,  
**afin de** publier un planning cohérent.

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S10 |
| Bloc RNCP | BC02 |

---

## EP-09 — Séances et formateurs

### US-080 — Créer les séances depuis le planning

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S10 |
| Bloc RNCP | BC02 |

### US-081 — Consulter les séances du formateur

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S10 |
| Bloc RNCP | BC02 |

### US-082 — Réaffecter une séance

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S10 |
| Bloc RNCP | BC02 |

### US-083 — Demander et valider une annulation

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S10 |
| Bloc RNCP | BC02 |

### US-084 — Gérer les salles

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S08/S10 |
| Bloc RNCP | BC02/BC03 |

### US-085 — Gérer le distanciel individuel

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S10 |
| Bloc RNCP | BC02 |

---

## EP-10 — Émargement et assiduité

> **Avancement (tranche V10, branche `feature/attendance-management-and-reporting`,
> non fusionnée).** Livré et testé : plusieurs points de contrôle par
> séance et jeton d'émargement par point de contrôle (US-090/US-091
> étendues) ; calcul des retards ; présence manuelle et correction avec
> historique append-only et motif obligatoire ; justificatif **métier
> sans fichier** (dépôt / modification / examen — `ABSENT →
> EXCUSED_ABSENCE` à l'acceptation) ; calcul de demi-journées (contexte
> d'alternance `COMPANY` exclu, `UNKNOWN` signalé) ; rapports séance /
> classe / apprenant + synthèse et **export CSV** (neutralisation
> d'injection de formule) ; écrans Angular `/sessions` enrichi,
> `/my-attendance`, `/attendance-management`. Non livré ici : QR fixe de
> salle (US-092), contrôle réseau, WebAuthn, scan caméra, mise en page
> officielle des rapports, pièce jointe de justificatif.

### US-090 — Ouvrir et clôturer une séance

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S11 |
| Bloc RNCP | BC02 |

### US-091 — Générer le QR dynamique

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S11 |
| Bloc RNCP | BC02/BC03 |

### US-092 — Gérer le QR fixe de salle

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 8 |
| Sprint cible | S11 |
| Bloc RNCP | BC02/BC03/BC04 |

### US-093 — Valider une présence

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S11 |
| Bloc RNCP | BC02/BC03 |

### US-094 — Gérer les quatre points de contrôle

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S11 |
| Bloc RNCP | BC02 |

### US-095 — Calculer les demi-journées

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S11 |
| Bloc RNCP | BC02 |

### US-096 — Gérer les retards

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S11 |
| Bloc RNCP | BC02 |

### US-097 — Enregistrer une présence manuelle

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S11 |
| Bloc RNCP | BC02/BC03 |

### US-098 — Actualiser les présences avec SSE

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S11 |
| Bloc RNCP | BC02 |

---

## EP-11 — Justificatifs et réclamations

### US-100 — Déposer un justificatif

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S12 |
| Bloc RNCP | BC02/BC03 |

### US-101 — Traiter un justificatif

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S12 |
| Bloc RNCP | BC02 |

### US-102 — Créer une réclamation

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S12 |
| Bloc RNCP | BC02 |

### US-103 — Échanger dans une réclamation

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S12 |
| Bloc RNCP | BC02 |

### US-104 — Transférer ou rouvrir une réclamation

| Champ | Valeur |
|---|---|
| Priorité | COULD |
| Estimation | 5 |
| Sprint cible | S12 |
| Bloc RNCP | BC02 |

---

## EP-12 — Notifications

### US-110 — Créer un centre de notifications

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S12 |
| Bloc RNCP | BC02 |

### US-111 — Notifier les changements de planning

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S10/S12 |
| Bloc RNCP | BC02 |

### US-112 — Envoyer les invitations avec Mailpit

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S07/S12 |
| Bloc RNCP | BC02/BC03 |

### US-113 — Mettre en place les notifications push

| Champ | Valeur |
|---|---|
| Priorité | COULD |
| Estimation | 8 |
| Sprint cible | S12 |
| Bloc RNCP | BC02 |

---

## EP-13 — Tableaux de bord et rapports

### US-120 — Afficher le tableau de bord du responsable

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S12 |
| Bloc RNCP | BC02 |

### US-121 — Produire le rapport journalier d’une classe

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S12 |
| Bloc RNCP | BC02 |

### US-122 — Produire le rapport mensuel

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S12 |
| Bloc RNCP | BC02 |

### US-123 — Produire le rapport individuel

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S12 |
| Bloc RNCP | BC02/BC03 |

### US-124 — Exporter en CSV

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 3 |
| Sprint cible | S12 |
| Bloc RNCP | BC02 |

### US-125 — Exporter en Excel

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S12 |
| Bloc RNCP | BC02 |

### US-126 — Générer un PDF ESIC

| Champ | Valeur |
|---|---|
| Priorité | COULD |
| Estimation | 5 |
| Sprint cible | S12/S13 |
| Bloc RNCP | BC02 |

---

## EP-14 — Intelligence artificielle

### US-130 — Proposer le mapping des colonnes

**En tant que** responsable,  
**je souhaite** recevoir une proposition de correspondance,  
**afin de** faciliter l’import d’un fichier hétérogène.

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 8 |
| Sprint cible | S12 |
| Bloc RNCP | BC02 |

### US-131 — Afficher un score de confiance

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S12 |
| Bloc RNCP | BC02 |

### US-132 — Valider humainement une suggestion

| Champ | Valeur |
|---|---|
| Priorité | MUST pour l’usage IA |
| Estimation | 3 |
| Sprint cible | S12 |
| Bloc RNCP | BC02/BC03 |

### US-133 — Détecter une anomalie de présence

| Champ | Valeur |
|---|---|
| Priorité | COULD |
| Estimation | 8 |
| Sprint cible | S12 |
| Bloc RNCP | BC03 |

---

## EP-15 — IoT

### US-140 — Enregistrer une Raspberry Pi

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S12 |
| Bloc RNCP | BC04 |

### US-141 — Publier un heartbeat MQTT

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 3 |
| Sprint cible | S12 |
| Bloc RNCP | BC04 |

### US-142 — Transmettre un événement d’émargement

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 8 |
| Sprint cible | S12 |
| Bloc RNCP | BC04 |

### US-143 — Détecter le rejeu d’un événement

| Champ | Valeur |
|---|---|
| Priorité | MUST pour la démonstration IoT sécurisée |
| Estimation | 5 |
| Sprint cible | S12 |
| Bloc RNCP | BC03/BC04 |

### US-144 — Mettre en file un événement hors ligne

| Champ | Valeur |
|---|---|
| Priorité | COULD |
| Estimation | 5 |
| Sprint cible | S12 |
| Bloc RNCP | BC04 |

---

## EP-16 — Cybersécurité

### US-150 — Auditer les actions critiques

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S07-S13 |
| Bloc RNCP | BC03 |

### US-151 — Limiter les tentatives avec Redis

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S11/S12 |
| Bloc RNCP | BC03 |

### US-152 — Protéger les fichiers

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S12 |
| Bloc RNCP | BC03 |

### US-153 — Intégrer Turnstile

| Champ | Valeur |
|---|---|
| Priorité | COULD |
| Estimation | 5 |
| Sprint cible | S12 |
| Bloc RNCP | BC03 |

### US-154 — Réaliser une analyse de risques

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S03/S06/S13 |
| Bloc RNCP | BC01/BC03 |

### US-155 — Documenter la réponse à incident

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S13 |
| Bloc RNCP | BC03 |

---

## EP-17 — Qualité et tests

### US-160 — Tester le modèle de données

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S06-S13 |
| Bloc RNCP | BC02/BC03 |

### US-161 — Tester les autorisations

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S07-S13 |
| Bloc RNCP | BC03 |

### US-162 — Tester les imports

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S09 |
| Bloc RNCP | BC02 |

### US-163 — Tester l’émargement concurrent

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S11 |
| Bloc RNCP | BC02/BC03 |

### US-164 — Mesurer les performances

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S12/S13 |
| Bloc RNCP | BC03 |

### US-165 — Réaliser la recette

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S13 |
| Bloc RNCP | BC01/BC02/BC03/BC04 |

---

## EP-18 — Déploiement et exploitation

### US-170 — Déployer localement avec Docker Compose

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S06/S13 |
| Bloc RNCP | BC03 |

### US-171 — Créer un environnement de staging

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 8 |
| Sprint cible | S13 |
| Bloc RNCP | BC03 |

### US-172 — Sauvegarder et restaurer MySQL

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S13 |
| Bloc RNCP | BC03 |

### US-173 — Superviser les composants

| Champ | Valeur |
|---|---|
| Priorité | SHOULD |
| Estimation | 5 |
| Sprint cible | S12/S13 |
| Bloc RNCP | BC03 |

### US-174 — Documenter l’architecture AWS cible

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S05/S13 |
| Bloc RNCP | BC03/BC04 |

---

## EP-19 — Intégrations externes

### US-180 — Préparer le mapping BERRA

| Champ | Valeur |
|---|---|
| Priorité | FUTURE |
| Estimation | 8 |
| Sprint cible | Après le MVP |
| Bloc RNCP | BC02/BC03 |

### US-181 — Préparer Microsoft Graph

| Champ | Valeur |
|---|---|
| Priorité | FUTURE |
| Estimation | 8 |
| Sprint cible | Après le MVP |
| Bloc RNCP | BC02 |

### US-182 — Synchroniser les réunions Teams

| Champ | Valeur |
|---|---|
| Priorité | FUTURE |
| Estimation | 13 |
| Sprint cible | Après le MVP |
| Bloc RNCP | BC02 |

---

## EP-20 — Soutenance

### US-190 — Rédiger le rapport

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 13 |
| Sprint cible | S01-S13 |
| Bloc RNCP | Tous |
| Recommandation | Remplissage progressif |

### US-191 — Construire la présentation

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S04-S13 |
| Bloc RNCP | Tous |

### US-192 — Préparer la démonstration

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 8 |
| Sprint cible | S13 |
| Bloc RNCP | Tous |

### US-193 — Enregistrer une vidéo de secours

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 3 |
| Sprint cible | S13 |
| Bloc RNCP | Tous |

### US-194 — Préparer les questions du jury

| Champ | Valeur |
|---|---|
| Priorité | MUST |
| Estimation | 5 |
| Sprint cible | S13 |
| Bloc RNCP | Tous |

---

# 9bis. Grand lot produit G1 — montée en gamme fonctionnelle (31 août 2026)

> Items du **grand lot produit G1**. Ils font évoluer le prototype vers
> une application métier riche. Traçabilité complète :
> `docs/reports/G1_REQUIREMENTS_TRACEABILITY.md` ; décisions :
> `docs/reports/G1_ARCHITECTURE_DECISIONS.md` ; plan :
> `docs/reports/G1_IMPLEMENTATION_PLAN.md` ; avancement :
> `docs/reports/G1_IMPLEMENTATION_PROGRESS.md`.
>
> Statut initial (avant travaux) ci-dessous. Il est mis à jour **bloc par
> bloc**, uniquement sur preuve (code présent + test exécuté + résultat
> consigné).
>
> **Avancement (1er septembre 2026).** **G1-A**, **G1-B**, **G1-C**
> (C.1–C.3) et **G1-D** (notifications persistantes, audience formateur)
> sont livrés et verts (back `./mvnw clean test` → **743 tests, 0 échec**
> — 3 fuseaux ; front `npm test` → **570 tests, 0 échec** ;
> `lint`/`build`/`audit` verts). **G1-E, G1-F, G1-G** : non démarrés.
> Détail par checkpoint et commande :
> `docs/reports/G1_IMPLEMENTATION_PROGRESS.md`.

## G1-A — Interfaces Angular des API administratives existantes

| Champ | Valeur |
|---|---|
| Exigences liées | EF-ROOM-001, EF-ACA-001..005, EF-USER-001..003, EF-AUTH-004 (CDC §44) |
| Épics concernés | EP-03, EP-05, EP-06 |
| Priorité | MUST |
| Valeur métier | rendre utilisables les fonctions back-end déjà livrées mais absentes de l'IHM (organisation, référentiels académiques en écriture, affectations pédagogiques, profils / inscriptions / transferts, émission d'invitation) |
| Risques | duplication de CRUD, dérive du périmètre de rôle par rapport au `@PreAuthorize` serveur, régression de navigation |
| Critères d'acceptation | chaque écran reprend à l'identique les rôles du contrôleur ; états `400/401/403/404/409/5xx` gérés ; axe-core sur ≥ 1 formulaire ; aucune régression des 475 tests front |
| Définition de fini | services + composants + routes + gardes testés ; `npm test` / `lint` / `build` / `audit` verts ; doc de traçabilité mise à jour |
| Statut initial | `PARTIAL` (API livrées, écrans absents ou en lecture seule) |
| **Statut (01/09/2026)** | **`IMPLEMENTED_AND_TESTED` pour `EF-ROOM-001`** (écrans `organization` livrés, +48 tests front) ; `EF-ACA-001..005` / `EF-USER-001` / `EF-AUTH-004` = **dette G1-A** (API prêtes, audit rôles figé au plan §3.1, aucune UI d'écriture) |
| Preuves attendues | `frontend/src/app/features/organization/**`, formulaires `academic`, `*.spec.ts`, capture du parcours |

## G1-B — Module `planning` (import CSV → simulation → publication versionnée → séances)

| Champ | Valeur |
|---|---|
| Exigences liées | EF-PLAN-001, EF-PLAN-002, EF-PLAN-003, EF-PLAN-004, EF-PLAN-005, EF-PLAN-007, EF-SES-001 ; RG-016, RG-030..RG-035 ; AC-007, AC-008 (CDC §43–§45) ; US-070, US-071, US-072, US-074, US-080 |
| Épics concernés | EP-08, EP-09 |
| Priorité | MUST |
| Valeur métier | livrer le chaînon principal du parcours prioritaire de `CLAUDE.md`, aujourd'hui `HORS_PÉRIMÈTRE_ASSUMÉ` (addendum F2) |
| Risques | publication partielle, duplication de séances, conflit concurrent, migration défectueuse, couplage `planning` ↔ `coursesession` (cf. `docs/06-risques.md` R-G1-01..R-G1-06) |
| Critères d'acceptation | AC-007 (séances uniquement après publication), AC-008 (modification ⇒ nouvelle version) ; simulation sans écriture métier ; publication transactionnelle tout-ou-rien, idempotente, `409` sur conflit métier, jamais `500` ; `ModularityTests` vert |
| Définition de fini | migrations `V12` (tables planning) + `V13` (lien `course_session ↔ planning_entry`, discriminant d'origine, `exception_reason` nullable) ; module + port `coursesession.PlanningSessionWriter` (UUID publics, aucune clé SQL) ; identité de créneau = `slot_key` (DEC-G1-002, repli `REMOVED`+`ADDED`) ; publication atomique + `FAILED` en `REQUIRES_NEW` séparé (DEC-G1-003) ; endpoints terminés ; écrans `/planning/**` + `/my-planning` ; suite back (parseur, simulation, conflits, alternance, publication, rollback, idempotence, concurrence, versionnement, sécurité, audit) + front + axe-core ; docs |
| Statut initial | `HORS_PÉRIMÈTRE_ASSUMÉ` → cible `IMPLEMENTED_AND_TESTED` |
| **Statut (01/09/2026)** | **`IMPLEMENTED_AND_TESTED`** — module `com.esic.connect.planning`, `V12`+`V13`, simulation (T1), publication atomique + versionnement, port `PlanningSessionWriter`, écrans `/planning/**` ; back +20 tests (693→713), front +25 (523→548). `EF-PLAN-003` = `PARTIAL` (annulation + réimport, `DEC-G1-003`) ; `EF-PLAN-006` reste `HORS_PÉRIMÈTRE_ASSUMÉ` ; `DEC-G1-006` (alternance) + conflit vs séances déjà publiées = post-G1 |
| Preuves attendues | `com.esic.connect.planning`, `V12`+`V13`, tests nommés, `docs/demo-data/planning-demo.csv` |

## G1-C — Cycle de vie avancé des séances

| Champ | Valeur |
|---|---|
| Exigences liées | EF-SES-004, EF-SES-005 ; CAD §24 RG-12 (« remplacement autorisé et audité »), CDC §43 RG-015, RG-017 ; CDC §15.1 (modification d'une séance exceptionnelle) — cf. note « deux numérotations RG » dans `G1_REQUIREMENTS_TRACEABILITY.md` §4 |
| Épics concernés | EP-09 |
| Priorité | SHOULD |
| Valeur métier | modifier / annuler une séance exceptionnelle `PLANNED`, désigner un remplaçant, tracer l'historique |
| Risques | modification d'une séance `OPEN`/`CLOSED`, absence dérivée d'une séance annulée, conflit concurrent |
| Critères d'acceptation | `OPEN`/`CLOSED` non modifiables ; `CANCELLED` non ouvrable, sans jeton, sans absence dérivée ; motif obligatoire ; audité ; `409` métier ; séance issue d'un planning non modifiable structurellement (nouvelle version requise) |
| Définition de fini | `V14` (`teacher_substitution` ; `session_cancellation_request` si workflow retenu) ; endpoints `PATCH`/`cancel`/`substitute`/`history` ; `PATCH` limité aux séances d'origine manuelle (`planning_entry_public_id IS NULL`) `PLANNED` ; suite back (transitions, concurrence, sécurité, audit, planning vs manuel) + front |
| Statut initial | `NOT_IMPLEMENTED` |
| Preuves attendues | `CourseSessionService.update/cancel`, `SubstitutionService`, tests |
| **Statut (01/09/2026)** | **`IMPLEMENTED_FULL_SUITE_GREEN`** — **G1-C.1** annulation (`V14`, `POST /sessions/{id}/cancel`), **G1-C.2** remplacements (`teacher_substitution`, `GET/POST …/substitutions`, `…/{id}/end`, `AccessGuard` étendu), **G1-C.3** audit correctif : séance `CANCELLED` **consultable** (`GET` historique, gardes `isHistoricallyReadable` vs `isOperational`), remplaçant `ACTIVE` **visible en liste** + `MANAGE`, période de remplacement devant **chevaucher la séance** (± 60 min, `422 SESSION_SUBSTITUTION_OUTSIDE_SESSION`), audit `coursesession` + purge Redis **`AFTER_COMMIT`** (rollback ⇒ 0 audit, testé). `EF-SES-004`, `EF-SES-005`, `CAD §24 RG-12`, `CDC §43 RG-015` → `IMPLEMENTED_AND_TESTED`. `PATCH /sessions/{id}` d'une séance manuelle `PLANNED` : **non livré** (non requis). Back +6 tests G1-C.3 (729→735, 3 fuseaux) ; front +2 (557→559). Détail : `G1_IMPLEMENTATION_PROGRESS.md` §§ G1-C.1/C.2/« Audit G1-C.3 » |

## G1-D — Centre de notifications métier persistantes

| Champ | Valeur |
|---|---|
| Exigences liées | EF-NOTIF-001, EF-NOTIF-002 ; RG-033 (CDC §43–§44) ; CDC §14, §23 |
| Épics concernés | EP-12 |
| Priorité | SHOULD |
| Valeur métier | informer les acteurs des événements (planning publié, séance modifiée / annulée, remplaçant, invitation, justificatif, import appliqué) dans un centre consultable |
| Risques | perte d'événement, notification dupliquée, contenu sensible en base, rollback métier provoqué par un échec de notification |
| Critères d'acceptation | notifications persistées **après commit**, transaction indépendante, idempotentes (`dedup_key`) ; un échec de notification ne rollback pas le métier ; destinataires dérivés serveur ; aucun jeton / PII / IP / chemin / secret ; isolation (AC-017) |
| Définition de fini | `V15` (`notification`) ; listener `AFTER_COMMIT` / `REQUIRES_NEW` (motif du seul `StudentImportAuditListener`) ; endpoints `/me/notifications*` ; cloche + badge front ; suite back (after-commit, rollback métier, idempotence, destinataires, sécurité) + front |
| Statut initial | `PARTIAL` (email d'activation seul) |
| Preuves attendues | `com.esic.connect.notification` étendu, `V15`, tests |
| **Statut (01/09/2026)** | **`IMPLEMENTED_FULL_SUITE_GREEN`** — `V15` (table `notification`, `dedup_key` UNIQUE) ; `NotificationListener` (`AFTER_COMMIT`) sur `PlanningPublishedEvent` + `CourseSessionChangeEvent(CANCELLED / SUBSTITUTION_ADDED / SUBSTITUTION_ENDED)` ; `NotificationWriter` → `NotificationRowWriter` (`REQUIRES_NEW` **par ligne**) ; idempotence `dedup_key` (SHA-256, `eventId` / `versionPublicId`) ; 4 endpoints `/api/v1/me/notifications` (liste paginée bornée, `unread-count`, `{id}/read` idempotent, `read-all`), isolation par destinataire (`404` sur une notif d'autrui), `NOTIF_*` ; front cloche `mat-badge` (`app-shell`) + centre `/notifications`. Destinataires = **formateurs** (principal + remplaçants `ACTIVE`) ; **apprenants / responsables pédagogiques = prolongement documenté** (nouveaux ports `enrollment` / `academic`). Pas de préférences, pas de push, pas de purge (dettes documentées). `EF-NOTIF-001` → `IMPLEMENTED_AND_TESTED` ; `EF-NOTIF-002` / `RG-033` → `IMPLEMENTED_AND_TESTED` (audience formateur). Back +8 tests (735→743, 3 fuseaux) ; front +11 (559→570). Détail : `G1_IMPLEMENTATION_PROGRESS.md` § « G1-D » |

## G1-F — Tableaux de bord par rôle

| Champ | Valeur |
|---|---|
| Exigences liées | CDC §25.1–§25.4 (contenus par rôle) ; AC-017 (cloisonnement apprenant) |
| Épics concernés | EP-13 |
| Priorité | SHOULD |
| Valeur métier | remplacer le tableau de bord générique par des vues métier utiles à la démonstration jury |
| Risques | N+1 Hibernate, métrique non reliée à une donnée réelle, fuite de périmètre |
| Critères d'acceptation | chaque carte reliée à une requête agrégat bornée nommée dans le plan ; `readOnly` ; périmètre serveur ; absence de N+1 vérifiée sur ≥ 1 endpoint ; `401/403` |
| Définition de fini | `GET /api/v1/me/dashboard` typé par rôle ; front cartes + listes ; suite back (par rôle, périmètre, bornes, vide, N+1) + front + axe-core |
| Statut initial | `PARTIAL` (dashboard générique unique) |
| Preuves attendues | endpoint + repositories de projection, `features/dashboard/**`, tests |

## G1-E — Pièces jointes sécurisées des justificatifs

| Champ | Valeur |
|---|---|
| Exigences liées | EF-JUS-001, EF-JUS-002 ; RG-071, RG-072, RG-073, RG-075, RG-076 (CDC §43) ; CDC §21.5 ; AC-014 |
| Épics concernés | EP-11 |
| Priorité | SHOULD |
| Valeur métier | joindre une preuve (PDF/JPEG/PNG) à un justificatif métier, avec stockage sûr et téléchargement contrôlé |
| Risques | traversal, fichier malveillant / polyglotte, stockage sensible, incohérence base / fichier, volume disque |
| Critères d'acceptation | contrôle extension + MIME + magic bytes + taille (`413` > 5 Mo) ; nom neutralisé ; stockage **hors webroot** via port abstrait ; contenu jamais en base ; téléchargement `attachment` + `nosniff`, MIME re-dérivé ; accès = propriétaire / examinateur périmètre, sinon `403` ; compensation base ↔ fichier documentée et testée |
| Définition de fini | `V16` (`justification_attachment`) ; port `JustificationFileStorage` + implémentation locale ; endpoints upload / liste / download / suppression logique ; suite back (formats, extension trompeuse, magic bytes, taille, traversal, accès croisé, en-têtes, rollback, nettoyage, audit) + front |
| Statut initial | `PARTIAL` (justificatif métier sans fichier) |
| Preuves attendues | `JustificationAttachment*`, `V16`, tests, `docs/demo-data/*` fictifs |

## G1-G — Recette globale, tests e2e et documentation finale

| Champ | Valeur |
|---|---|
| Exigences liées | CDC §46, §47 ; AC-007, AC-008, AC-017 |
| Épics concernés | EP-17 |
| Priorité | MUST |
| Valeur métier | prouver le parcours complet et aligner toute la documentation sur l'état réel |
| Risques | e2e instable, dépendance e2e vulnérable, documentation en avance sur le code |
| Critères d'acceptation | parcours bout en bout exécuté (e2e Playwright **ou** démonstration API automatisée, statut honnête) ; totaux de tests re-mesurés et consignés ; README / CURRENT-STATE / docs 01–12 / matrices alignés ; addendum daté aux rapports historiques **sans réécriture** |
| Définition de fini | `docs/demo-data/planning-demo.csv` + `planning-conflicts-demo.csv` + fichiers justificatifs fictifs ; seed idempotent étendu ; `G1_FINAL_REPORT.md` ; commits `docs(demo)` puis `docs(g1)` rapport |
| Statut initial | `PARTIAL` (recette API partielle, §11.8 du guide de démo) |
| Preuves attendues | `frontend/e2e/**` ou script API, `docs/reports/G1_FINAL_REPORT.md` |

---

# 10. Vue synthétique des priorités

## MUST

- cadrage ;
- cahier des charges ;
- architecture ;
- modèle de données ;
- authentification ;
- rôles ;
- périmètres ;
- formations ;
- classes ;
- inscriptions historiques ;
- trois rythmes d’alternance ;
- import CSV des apprenants ;
- import CSV du planning ;
- publication ;
- séances ;
- QR dynamique ;
- présences ;
- quatre contrôles ;
- calcul des demi-journées ;
- rapports ;
- export CSV ;
- audit ;
- tests ;
- Docker Compose ;
- documentation ;
- démonstration.

## SHOULD

- Excel multifeuille ;
- activation par email ;
- récupération de compte ;
- WebAuthn ;
- délégations ;
- remplacements ;
- QR fixe ;
- contrôle réseau ;
- PWA ;
- justificatifs ;
- réclamations ;
- notifications ;
- export Excel ;
- assistance IA ;
- Raspberry Pi ;
- staging.

## COULD

- notifications push ;
- Turnstile ;
- Isolation Forest ;
- PDF ;
- DLQ ;
- fonctionnement IoT hors ligne ;
- tableaux de bord avancés.

## FUTURE

- BERRA ;
- Microsoft Graph ;
- Teams ;
- AWS complet ;
- NFC ;
- haute disponibilité.

---

# 11. Règle de transparence

Les états réels doivent toujours être distingués :

| État | Signification |
|---|---|
| Conçu | Décrit dans les documents |
| Implémenté | Code présent |
| Testé | Tests exécutés avec succès |
| Démontré | Vérification manuelle réussie |
| Simulé | Comportement présenté sans intégration réelle |
| Reporté | Non réalisé dans la version actuelle |

Aucune date, réunion, validation ou mesure ne doit être présentée comme
réelle sans preuve disponible.
