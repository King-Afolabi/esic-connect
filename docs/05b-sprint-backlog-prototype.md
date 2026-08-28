# Sprint Backlog d’accélération — Prototype ESIC Connect

## Métadonnées

| Élément | Valeur |
|---|---|
| Projet | ESIC Connect |
| Nature | Sprint intensif de consolidation du prototype |
| Durée | Trois jours |
| Product Owner | Monsieur BANKA |
| Scrum Master | Monsieur INOUSSA Chaabane |
| Développeur | Abubacar AFOLABI |
| Version | 1.0 |
| Date du document | 28 août 2026 |
| Statut | À exécuter |
| Environnement | Local conteneurisé |

---

# 1. Positionnement

Ce Sprint Backlog décrit le travail réel d’accélération nécessaire pour
produire une preuve de concept démontrable.

Il ne remplace pas la roadmap sur six mois.

Il sélectionne uniquement les éléments indispensables permettant de
démontrer le parcours principal.

---

# 2. Sprint Goal

> Produire un parcours local démontrable permettant à un responsable
> pédagogique d’importer des apprenants et un planning, à un formateur
> d’ouvrir une séance et à un apprenant d’enregistrer sa présence,
> jusqu’à la génération d’un rapport simple et audité.

---

# 3. Résultat attendu

```text
Responsable pédagogique
→ importe les apprenants
→ confirme l’import
→ importe le planning
→ publie le planning

Formateur
→ consulte la séance
→ ouvre l’émargement
→ affiche un QR ou code temporaire

Apprenant
→ s’authentifie
→ valide sa présence

Système
→ enregistre la présence
→ affiche le résultat
→ conserve l’audit
→ produit un rapport
```

---

# 4. Périmètre du sprint

## Inclus

- dépôt structuré ;
- documentation ;
- Docker Compose ;
- MySQL ;
- Redis ;
- Spring Boot ;
- Angular minimal ;
- utilisateurs ;
- rôles ;
- authentification ;
- formations ;
- classes ;
- inscriptions ;
- import CSV des apprenants ;
- import CSV du planning ;
- séances ;
- ouverture ;
- QR ou code temporaire ;
- présence ;
- rapport simple ;
- export CSV ;
- audit ;
- tests critiques ;
- démonstration.

## Optionnel

- Excel ;
- PWA ;
- WebAuthn ;
- quatre contrôles complets ;
- Raspberry Pi ;
- FastAPI ;
- SSE ;
- staging.

## Hors sprint

- Microsoft Graph ;
- Teams ;
- AWS complet ;
- NFC ;
- PDF complexe ;
- notifications push ;
- haute disponibilité ;
- production réelle.

---

# 5. Kanban

```text
Backlog
→ Prêt
→ En cours
→ En revue
→ En test
→ Terminé
```

## Limites

```text
En cours : 2
En revue : 2
En test : 2
```

Aucun élément optionnel ne doit passer en `En cours` tant qu’un élément
obligatoire critique reste bloqué.

---

# 6. Jour 1 — Socle, identité et référentiels

## Objectif du jour

Obtenir un back-end sécurisé et une base structurée permettant de créer
des utilisateurs, formations, classes et inscriptions.

## Tâches documentaires

### T-J1-001 — Vérifier les documents

- cadrage ;
- cahier des charges ;
- architecture ;
- modèle de données ;
- backlog.

**Estimation :** 1 point.

### T-J1-002 — Créer `CURRENT-STATE.md`

Contenu :

- dernier commit stable ;
- fonctions réalisées ;
- tests disponibles ;
- problèmes ;
- prochaine tâche.

**Estimation :** 1 point.

## Infrastructure

### T-J1-010 — Créer Docker Compose

Services :

- MySQL ;
- Redis ;
- Mailpit ;
- Mosquitto si possible.

**Estimation :** 3 points.

#### Critères d’acceptation

- les conteneurs démarrent ;
- MySQL est accessible au back-end ;
- Redis répond ;
- les mots de passe sont externalisés ;
- `.env.example` existe.

### T-J1-011 — Initialiser Spring Boot

Dépendances :

- Web ;
- Security ;
- JPA ;
- Validation ;
- MySQL ;
- Redis ;
- Flyway ;
- Actuator ;
- OpenAPI ;
- Tests.

**Estimation :** 3 points.

### T-J1-012 — Créer les migrations minimales

Tables :

- utilisateur ;
- rôle ;
- rôle utilisateur ;
- formation ;
- niveau ;
- année scolaire ;
- promotion ;
- classe ;
- profil apprenant ;
- profil formateur ;
- inscription ;
- audit.

**Estimation :** 8 points.

## Identité

### T-J1-020 — Créer les comptes de démonstration

- super administrateur ;
- administrateur ;
- responsable pédagogique ;
- formateur ;
- apprenant.

**Estimation :** 2 points.

### T-J1-021 — Implémenter la connexion

- email ;
- mot de passe ;
- hachage ;
- cookie sécurisé ;
- `/auth/login` ;
- `/auth/me` ;
- `/auth/logout`.

**Estimation :** 8 points.

### T-J1-022 — Implémenter les rôles

- contrôles de routes ;
- contrôles de services ;
- tests `401` ;
- tests `403`.

**Estimation :** 5 points.

### T-J1-023 — Implémenter le périmètre pédagogique

Le responsable ne consulte que ses formations.

**Estimation :** 5 points.

## Référentiels

### T-J1-030 — Gérer les formations

**Estimation :** 3 points.

### T-J1-031 — Gérer les classes

**Estimation :** 3 points.

### T-J1-032 — Gérer les inscriptions historiques

**Estimation :** 5 points.

### T-J1-033 — Ajouter les rythmes d’alternance minimaux

Rythmes :

- trois jours/deux jours ;
- une semaine sur quatre ;
- deux semaines sur quatre.

Pour la démonstration, une configuration peut être chargée par données
de référence.

**Estimation :** 5 points.

## Front-end

### T-J1-040 — Initialiser Angular Material

**Estimation :** 3 points.

### T-J1-041 — Créer l’écran de connexion

**Estimation :** 3 points.

### T-J1-042 — Créer la structure de navigation

Menus selon le rôle.

**Estimation :** 3 points.

## Tests du jour

- démarrage ;
- migrations ;
- connexion réussie ;
- connexion refusée ;
- accès hors rôle ;
- accès hors périmètre ;
- changement de classe sans perte d’historique.

## Définition de fin du jour

Le jour 1 est réussi si :

- l’application démarre ;
- les comptes fictifs fonctionnent ;
- le responsable accède à son périmètre ;
- une formation et une classe existent ;
- un apprenant possède une inscription ;
- les tests critiques passent.

---

# 7. Jour 2 — Imports, planning et séances

## Objectif du jour

Permettre au responsable d’intégrer les apprenants et le planning, puis
de créer les séances.

## Import des apprenants

### T-J2-001 — Définir le modèle CSV

Colonnes minimales :

```text
student_number
last_name
first_name
email
formation_code
class_code
academic_year
work_study
work_study_pattern
```

**Estimation :** 1 point.

### T-J2-002 — Créer l’analyse CSV

- lecture ;
- normalisation ;
- validation ;
- doublons ;
- utilisateurs existants ;
- erreurs.

**Estimation :** 8 points.

### T-J2-003 — Créer la simulation

Aucune modification définitive.

**Estimation :** 5 points.

### T-J2-004 — Créer la confirmation

- création ;
- mise à jour ;
- changement de classe ;
- bilan ;
- audit.

**Estimation :** 8 points.

### T-J2-005 — Créer l’écran Angular d’import

- sélection du fichier ;
- simulation ;
- tableau des résultats ;
- confirmation.

**Estimation :** 8 points.

## Import du planning

### T-J2-010 — Définir le modèle CSV planning

```text
academic_year
formation_code
class_code
session_date
start_time
end_time
course_code
course_name
teacher_email
room_code
attendance_mode
remote_link
```

**Estimation :** 1 point.

### T-J2-011 — Créer la simulation du planning

- validation ;
- erreurs ;
- formateur ;
- matière ;
- conflits simples.

**Estimation :** 8 points.

### T-J2-012 — Créer la publication

- version ;
- séances ;
- audit ;
- invalidation du cache.

**Estimation :** 8 points.

### T-J2-013 — Créer l’écran d’import du planning

**Estimation :** 8 points.

## Séances

### T-J2-020 — Afficher les séances du formateur

**Estimation :** 5 points.

### T-J2-021 — Ouvrir et clôturer une séance

**Estimation :** 5 points.

### T-J2-022 — Préparer les points de contrôle

Au minimum :

- arrivée du matin ;
- arrivée de l’après-midi.

Les quatre types doivent être présents dans le modèle, même si la
démonstration n’utilise que deux points.

**Estimation :** 5 points.

## Tests du jour

- fichier valide ;
- colonne absente ;
- email invalide ;
- doublon ;
- changement de classe ;
- planning valide ;
- formateur inconnu ;
- horaire invalide ;
- publication ;
- création des séances ;
- accès du formateur.

## Définition de fin du jour

Le jour 2 est réussi si :

- un responsable importe une classe ;
- les erreurs sont prévisualisées ;
- l’import est confirmé ;
- un planning est publié ;
- une séance est visible par le formateur.

---

# 8. Jour 3 — Émargement, rapport et démonstration

## Objectif du jour

Terminer le parcours fonctionnel et produire les preuves de soutenance.

## Émargement

### T-J3-001 — Générer un jeton Redis

- aléatoire ;
- temporaire ;
- lié à la séance ;
- lié au point de contrôle ;
- expiration.

**Estimation :** 5 points.

### T-J3-002 — Générer le QR ou code court

**Estimation :** 3 points.

### T-J3-003 — Valider la présence

- authentification ;
- inscription ;
- séance ouverte ;
- jeton valide ;
- absence de doublon ;
- statut ;
- audit.

**Estimation :** 8 points.

### T-J3-004 — Gérer les retards

- 0 à 15 minutes : présent ;
- 16 à 30 minutes : retard ;
- après 30 minutes : manuel.

**Estimation :** 3 points.

### T-J3-005 — Afficher les présences du formateur

Actualisation simple ou SSE selon le temps.

**Estimation :** 5 points.

### T-J3-006 — Corriger une présence

- motif obligatoire ;
- ancienne valeur ;
- nouvelle valeur ;
- audit.

**Estimation :** 5 points.

## Rapports

### T-J3-010 — Produire le rapport d’une classe

- date ;
- apprenants ;
- statut ;
- retard ;
- canal.

**Estimation :** 5 points.

### T-J3-011 — Produire le rapport individuel

**Estimation :** 5 points.

### T-J3-012 — Exporter en CSV

**Estimation :** 3 points.

## Technologies avancées

### T-J3-020 — Démontrer une assistance IA

Version minimale :

- reconnaître quelques synonymes de colonnes ;
- proposer un mapping ;
- afficher un score ;
- demander une confirmation.

**Estimation :** 5 points.

### T-J3-021 — Démontrer MQTT

Version minimale :

- simulateur ou Raspberry Pi ;
- heartbeat ;
- événement ;
- détection du doublon.

**Estimation :** 5 points.

### T-J3-022 — Démontrer WebAuthn

À réaliser uniquement si le parcours principal est stable.

Sinon :

- produire une preuve technique séparée ;
- classer la fonction comme `Simulée` ou `Conçue`.

**Estimation :** 8 points.

## Qualité

### T-J3-030 — Exécuter les tests critiques

**Estimation :** 5 points.

### T-J3-031 — Vérifier les secrets

**Estimation :** 2 points.

### T-J3-032 — Tester la sauvegarde/restauration

**Estimation :** 3 points.

## Soutenance

### T-J3-040 — Mettre à jour le rapport

**Estimation :** 5 points.

### T-J3-041 — Finaliser la présentation

**Estimation :** 5 points.

### T-J3-042 — Préparer la matrice RNCP

**Estimation :** 3 points.

### T-J3-043 — Enregistrer la vidéo de secours

**Estimation :** 3 points.

### T-J3-044 — Répéter la démonstration

**Estimation :** 3 points.

## Définition de fin du jour

Le sprint est réussi si le parcours suivant fonctionne :

```text
Connexion responsable
→ Import des apprenants
→ Import du planning
→ Publication
→ Connexion formateur
→ Ouverture de séance
→ QR/code
→ Connexion apprenant
→ Présence
→ Affichage formateur
→ Rapport
→ Export
```

---

# 9. Ordre strict des priorités

## Priorité 1

- base ;
- authentification ;
- rôles ;
- formations ;
- classes ;
- inscriptions.

## Priorité 2

- import apprenants ;
- import planning ;
- publication ;
- séances.

## Priorité 3

- ouverture ;
- QR ;
- présence ;
- rapport ;
- audit.

## Priorité 4

- tests ;
- documents ;
- présentation ;
- vidéo.

## Priorité 5

- IA ;
- IoT ;
- WebAuthn ;
- SSE ;
- PWA.

Une priorité inférieure ne doit pas bloquer une priorité supérieure.

---

# 10. Points de contrôle quotidiens

À la fin de chaque journée :

1. exécuter les tests ;
2. créer un commit ;
3. mettre à jour `CURRENT-STATE.md` ;
4. mettre à jour le backlog ;
5. mettre à jour le rapport ;
6. ajouter les captures ;
7. inscrire l’usage de l’IA ;
8. identifier les blocages ;
9. décider les priorités du lendemain.

---

# 11. Daily Scrum adapté

Chaque point quotidien répond à :

```text
Qu’ai-je terminé depuis le dernier point ?
Que vais-je terminer ensuite ?
Quel obstacle menace l’objectif du sprint ?
```

Durée cible :

```text
15 minutes maximum
```

Le Daily Scrum doit produire un plan d’action, pas un long compte rendu.

---

# 12. Sprint Review

À la fin des trois jours, présenter :

- le parcours réellement fonctionnel ;
- les tests ;
- les limites ;
- les éléments simulés ;
- les éléments reportés ;
- les décisions pour la suite.

---

# 13. Sprint Retrospective

Répondre à quatre questions :

1. Qu’est-ce qui a bien fonctionné ?
2. Qu’est-ce qui a ralenti le travail ?
3. Quelles erreurs faut-il éviter ?
4. Quelle amélioration appliquer au prochain cycle ?

---

# 14. Risques du sprint

| Risque | Réponse immédiate |
|---|---|
| Authentification bloquée | Simplifier le flux sans supprimer la sécurité essentielle |
| Import trop complexe | CSV strict avant Excel |
| Front-end en retard | Tester via Swagger puis créer les écrans essentiels |
| QR dynamique instable | Utiliser un code court comme solution de secours |
| WebAuthn bloque | Le démontrer séparément |
| Raspberry Pi bloque | Utiliser un script MQTT |
| IA bloque | Utiliser règles et similarité simples |
| Rapport en retard | Documenter chaque soir |
| Démo instable | Vidéo et données préchargées |
| Claude modifie trop de fichiers | Une tâche ciblée par prompt |

---

# 15. Commande de clôture Claude Code

À utiliser à la fin de chaque journée :

```text
Lis CLAUDE.md et docs/CURRENT-STATE.md.

Effectue la clôture de la journée sans inventer de résultat.

1. Inspecte le dépôt.
2. Exécute les tests disponibles.
3. Liste les fonctions réellement implémentées.
4. Distingue implémenté, testé, démontré, simulé et reporté.
5. Mets à jour docs/CURRENT-STATE.md.
6. Mets à jour le Sprint Backlog.
7. Mets à jour la matrice RNCP.
8. Mets à jour le rapport uniquement avec des faits vérifiables.
9. Liste les fichiers modifiés.
10. Donne les trois prochaines priorités.
```

---

# 16. Transparence du sprint

Les trois jours constituent un sprint intensif de prototypage et de
consolidation.

Le suivi doit rester honnête :

- une fonction codée n’est pas forcément testée ;
- une fonction testée n’est pas forcément démontrée ;
- une architecture conçue n’est pas déployée ;
- une simulation n’est pas une intégration réelle ;
- une roadmap n’est pas un historique de réalisation.
