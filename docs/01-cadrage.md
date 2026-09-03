# Note de cadrage — ESIC Connect

## Métadonnées

| Élément | Valeur |
|---|---|
| Nom du projet | ESIC Connect |
| Nature | Preuve de concept d’une plateforme web et mobile intelligente et hautement sécurisée |
| Établissement concerné | ESIC |
| Porteur du projet | Abubacar AFOLABI |
| Certification préparée | RNCP 39394 — Expert en systèmes d’information et sécurité |
| Version | 2.1 |
| Date | 27 août 2026 |
| Statut | À valider |
| Durée de réalisation du prototype | Trois jours |
| Environnement initial | Développement local conteneurisé |

---

## 1. Présentation du projet

**ESIC Connect** est une plateforme centralisée, réactive et sécurisée
de planification pédagogique, de gestion des promotions, d’émargement
intelligent et de suivi de l’assiduité destinée à l’ESIC.

La plateforme doit couvrir le cycle allant de l’importation des
apprenants et des plannings jusqu’à l’enregistrement des présences et
à la production de rapports d’assiduité.

Elle sera accessible depuis :

- les navigateurs web ;
- les ordinateurs de l’établissement ;
- les tablettes ;
- les smartphones ;
- une Progressive Web App ou PWA ;
- une borne d’émargement connectée reposant sur une Raspberry Pi.

Le système prendra en charge les cours :

- en présentiel ;
- à distance pour toute une classe ;
- à distance pour certains apprenants autorisés ;
- en mode hybride.

La solution vise une expérience rapide et fluide. L’objectif cible est
d’obtenir un temps de traitement inférieur à 100 ms pour les opérations
simples servies depuis le cache, dans un environnement maîtrisé.

Cet objectif devra être vérifié par des tests. Il ne constitue pas une
garantie pour toutes les routes ou toutes les conditions de charge.

ESIC Connect assurera également :

- la centralisation des données pédagogiques ;
- la création automatique des séances depuis les plannings ;
- la communication entre les acteurs ;
- la traçabilité des actions ;
- la protection des données personnelles ;
- la détection des anomalies ;
- l’intégration d’un dispositif IoT ;
- l’assistance par intelligence artificielle ;
- l’authentification renforcée selon la sensibilité des comptes.

---

## 2. Contexte

ESIC est un établissement proposant des formations dans les domaines
du commerce et de l’informatique, notamment :

- des BTS ;
- des Bachelors ;
- des Mastère ;
- d’autres parcours pouvant être ajoutés à la plateforme.

Chaque ensemble de formations peut être placé sous la responsabilité
d’un responsable pédagogique.

Les enseignements peuvent être dispensés :

- en présentiel ;
- entièrement à distance ;
- en mode hybride ;
- à distance pour certains apprenants ne pouvant pas se déplacer.

La gestion des plannings, des comptes, des présences et des rapports
peut nécessiter l’utilisation de plusieurs fichiers ou outils. Cette
organisation peut provoquer :

- des ressaisies manuelles ;
- des doublons ;
- des erreurs de saisie ;
- des difficultés de consolidation ;
- une création manuelle des listes d’appel ;
- un manque de visibilité sur l’assiduité ;
- des retards dans la production des rapports ;
- une traçabilité insuffisante ;
- des risques de fraude à l’émargement ;
- des difficultés à gérer les remplacements ;
- des erreurs de communication avec les apprenants ;
- des comptes non activés à cause d’adresses électroniques erronées.

Le projet vise à centraliser ces processus au sein d’un système
d’information unique.

---

## 3. Problématique

> Comment concevoir une plateforme centralisée, performante, accessible
> et sécurisée permettant aux responsables pédagogiques d’importer les
> apprenants et les plannings, aux formateurs d’utiliser les séances
> programmées, aux apprenants d’émarger de manière fiable et à
> l’administration de suivre l’assiduité, tout en garantissant la
> traçabilité, la protection des données et la continuité des activités
> pédagogiques ?

---

## 4. Finalité du projet

La finalité d’ESIC Connect est de produire une preuve de concept
démontrant la faisabilité d’un système d’information capable de :

- centraliser les données pédagogiques ;
- structurer les formations, promotions et classes ;
- importer les apprenants ;
- créer et inviter automatiquement les utilisateurs ;
- suivre l’activation des comptes ;
- importer les plannings ;
- détecter les erreurs d’importation ;
- créer automatiquement les séances ;
- publier les plannings ;
- faciliter l’émargement ;
- gérer les présences, absences et retards ;
- assurer une authentification renforcée ;
- produire des rapports ;
- permettre les réclamations ;
- détecter les comportements inhabituels ;
- intégrer un dispositif IoT ;
- préparer une future intégration avec Microsoft 365 et Teams.

---

## 5. Objectifs

### 5.1 Objectif général

Concevoir et développer un prototype web responsive permettant de gérer
le cycle allant de l’importation des apprenants et des plannings jusqu’à
l’émargement et à la production des rapports d’assiduité.

### 5.2 Objectifs fonctionnels

Le système devra permettre de :

1. authentifier les utilisateurs ;
2. gérer plusieurs rôles par utilisateur ;
3. appliquer les autorisations selon les rôles et les périmètres ;
4. gérer les formations, promotions, classes et matières ;
5. affecter un responsable pédagogique à plusieurs formations ;
6. gérer les formateurs internes et externes ;
7. importer une liste d’apprenants depuis un fichier CSV ou Excel ;
8. vérifier la présence des champs obligatoires ;
9. détecter les doublons ;
10. créer les comptes des apprenants ;
11. envoyer les invitations d’activation ;
12. suivre le statut des invitations ;
13. permettre la réémission d’une invitation ;
14. importer un planning CSV ou Excel ;
15. prévisualiser et contrôler les données importées ;
16. détecter les erreurs et conflits ;
17. permettre une correction avant validation ;
18. publier le planning ;
19. créer automatiquement les séances ;
20. afficher les séances aux formateurs concernés ;
21. permettre la réaffectation d’une séance à un remplaçant ;
22. ouvrir et clôturer une séance ;
23. générer un QR code dynamique ;
24. permettre l’émargement ;
25. renforcer l’émargement par WebAuthn lorsque le terminal le permet ;
26. enregistrer les présences, absences et retards ;
27. suivre les présences en direct ;
28. corriger une présence avec justification ;
29. permettre à l’apprenant de consulter son assiduité ;
30. permettre à l’apprenant de déposer une réclamation ;
31. notifier les acteurs concernés ;
32. rechercher une formation, une classe, un apprenant ou un formateur ;
33. produire des tableaux de bord ;
34. produire des rapports par date, mois, année scolaire, classe,
    formation ou apprenant ;
35. exporter les résultats ;
36. recevoir les événements d’une Raspberry Pi ;
37. détecter les événements inhabituels ;
38. conserver une piste d’audit des opérations sensibles.

### 5.3 Objectifs techniques

Le prototype devra :

- utiliser Java 21 et Spring Boot pour le back-end ;
- utiliser Angular et Angular Material pour le front-end ;
- être utilisable comme application web responsive ;
- préparer un fonctionnement PWA ;
- utiliser MySQL comme source principale de données ;
- utiliser Redis pour le cache et les données temporaires ;
- utiliser Python pour les fonctions d’intelligence artificielle ;
- utiliser MQTT pour l’intégration de la Raspberry Pi ;
- fonctionner localement avec Docker Compose ;
- exposer une API REST documentée ;
- intégrer des migrations de base de données ;
- intégrer des tests automatisés prioritaires ;
- permettre un futur déploiement cloud ;
- préparer une future intégration à Microsoft Graph.

### 5.4 Objectifs de performance

Les objectifs de performance sont les suivants :

- affichage rapide des plannings fréquemment consultés ;
- génération rapide des jetons d’émargement ;
- mise à jour rapide des listes de présence ;
- réduction des accès inutiles à MySQL ;
- traitement asynchrone des courriels et notifications ;
- mesure des temps de réponse des routes principales ;
- mise en cache uniquement des données compatibles avec les exigences
  de confidentialité.

### 5.5 Objectifs de sécurité

Le prototype devra :

- protéger les mots de passe avec un hachage robuste ;
- mettre en place une authentification sécurisée ;
- appliquer un contrôle d’accès par rôle ;
- accepter le cumul de plusieurs rôles ;
- limiter le responsable pédagogique à son périmètre ;
- imposer une authentification renforcée aux comptes sensibles ;
- protéger les formulaires exposés contre les robots ;
- limiter les tentatives de connexion ;
- sécuriser la récupération des mots de passe ;
- ne pas intégrer de données personnelles dans les QR codes ;
- générer des jetons temporaires et non prédictibles ;
- empêcher les utilisations multiples du même jeton ;
- journaliser les opérations sensibles ;
- protéger les secrets techniques ;
- utiliser uniquement des données fictives pour la démonstration ;
- soumettre les alertes produites par l’IA à une validation humaine.

---

## 6. Périmètre organisationnel

Le projet concerne les acteurs suivants :

- administration technique ;
- administration scolaire ;
- responsables pédagogiques ;
- formateurs internes ;
- formateurs externes ;
- formateurs remplaçants ;
- apprenants.

L’organisation fonctionnelle de référence est la suivante :

```text
ESIC
└── Formation
    └── Promotion ou année scolaire
        └── Classe ou groupe
            └── Apprenants
```

Un responsable pédagogique peut gérer plusieurs formations.

Une formation peut être attribuée à un responsable principal et, si
nécessaire, à des responsables délégués.

---

## 7. Rôles et privilèges

### 7.1 SUPER_ADMIN

Le super administrateur dispose du contrôle technique global.

Il peut :

- gérer les paramètres critiques ;
- gérer les comptes administrateurs ;
- consulter les journaux de sécurité ;
- consulter les événements techniques ;
- gérer les équipements connectés ;
- intervenir lors d’un incident majeur ;
- configurer les politiques de sécurité ;
- suspendre un compte ou un dispositif compromis ;
- superviser les intégrations externes.

Son utilisation doit être exceptionnelle et fortement auditée.

### 7.2 ADMIN

L’administrateur assure l’administration fonctionnelle globale.

Il peut :

- gérer les utilisateurs ;
- attribuer les rôles autorisés ;
- administrer les référentiels ;
- gérer les paramètres fonctionnels ;
- suivre les imports ;
- consulter les erreurs d’envoi de courriels ;
- réémettre une invitation ;
- consulter les journaux fonctionnels ;
- assister les responsables pédagogiques.

### 7.3 SCHOOL_ADMINISTRATION

L’administration scolaire peut :

- rechercher un apprenant ;
- rechercher une classe ;
- consulter les données d’assiduité ;
- gérer les justificatifs ;
- consulter les réclamations administratives ;
- produire des rapports ;
- exporter les données autorisées ;
- analyser les taux d’absentéisme.

### 7.4 PEDAGOGICAL_MANAGER

Le responsable pédagogique gère une ou plusieurs formations.

Il peut :

- gérer les promotions et classes de son périmètre ;
- importer les apprenants ;
- suivre l’activation de leurs comptes ;
- créer les comptes des formateurs externes ;
- importer les plannings ;
- prévisualiser les imports ;
- corriger les erreurs ;
- publier les plannings ;
- affecter les formateurs ;
- nommer un remplaçant ;
- consulter les statistiques ;
- traiter les réclamations pédagogiques ;
- produire les rapports de son périmètre.

Le rôle `PEDAGOGICAL_MANAGER` peut être cumulé avec `TEACHER`.

### 7.5 TEACHER

Le formateur peut :

- consulter son emploi du temps ;
- consulter les classes qui lui sont affectées ;
- consulter les séances qui lui ont été déléguées ;
- ouvrir une séance ;
- afficher le QR code ;
- suivre les présences ;
- déclarer un retard ;
- corriger un statut avec un motif ;
- clôturer la séance ;
- répondre aux réclamations liées à ses séances.

Le formateur ne crée pas librement les séances du planning.

### 7.6 STUDENT

L’apprenant peut :

- activer son compte ;
- consulter son planning ;
- recevoir une notification ;
- ouvrir directement la séance concernée ;
- scanner le QR code ;
- effectuer une vérification locale lorsque WebAuthn est disponible ;
- consulter son historique ;
- consulter son taux d’assiduité ;
- signaler une erreur ;
- déposer un justificatif ;
- envoyer une réclamation.

---

## 8. Gestion des utilisateurs et des promotions

### 8.1 Importation des apprenants

Le responsable pédagogique pourra importer les apprenants avec un
fichier :

- CSV ;
- Excel au format `.xlsx`.

Le modèle minimal contiendra :

```text
last_name
first_name
email
phone
formation_code
class_code
academic_year
```

Les champs obligatoires seront :

- nom ;
- prénom ;
- adresse électronique ;
- code de formation ;
- code de classe ;
- année scolaire.

Le téléphone sera facultatif.

### 8.2 Contrôles à l’importation

Le système vérifiera :

- la présence des colonnes obligatoires ;
- la présence des valeurs obligatoires ;
- la syntaxe des adresses électroniques ;
- l’existence de la formation ;
- l’existence de la classe ;
- l’appartenance de la classe à la formation ;
- les doublons dans le fichier ;
- les doublons avec les comptes existants ;
- les lignes en conflit ;
- le périmètre du responsable pédagogique.

### 8.3 Cycle d’activation du compte

Les statuts d’un compte seront :

- `PENDING_ACTIVATION` ;
- `ACTIVE` ;
- `SUSPENDED` ;
- `LOCKED` ;
- `ARCHIVED`.

Le parcours sera :

1. importation de l’apprenant ;
2. validation des informations ;
3. création du compte en attente ;
4. génération d’un jeton d’activation ;
5. envoi d’une invitation ;
6. définition du mot de passe ;
7. activation du compte ;
8. journalisation du résultat.

### 8.4 Suivi des courriels

Les statuts de traitement internes seront :

- `QUEUED` ;
- `SENT_TO_PROVIDER` ;
- `PROCESSING_FAILED`.

Lorsque le prestataire le permet, les statuts de délivrabilité seront :

- `DELIVERED` ;
- `BOUNCED` ;
- `REJECTED` ;
- `COMPLAINED` ;
- `UNKNOWN`.

L’interface devra permettre :

- de consulter le statut ;
- de consulter la date de la dernière tentative ;
- de consulter un motif d’erreur non sensible ;
- de corriger l’adresse ;
- de réémettre l’invitation ;
- d’auditer les actions.

Un message ne sera pas considéré comme livré uniquement parce qu’il a
été transmis au serveur de messagerie.

---

## 9. Gestion des formateurs externes et remplaçants

### 9.1 Formateurs externes

Un formateur externe peut être créé sans adresse institutionnelle ESIC.

Les informations prévues sont :

- nom ;
- prénom ;
- adresse électronique ;
- téléphone facultatif ;
- organisme facultatif ;
- matières concernées ;
- date éventuelle de début d’intervention ;
- date éventuelle de fin d’intervention.

Le compte sera activé avec une invitation sécurisée.

Le domaine de l’adresse électronique ne sera pas utilisé comme seul
critère de confiance.

### 9.2 Remplacements

Une séance pourra être réaffectée à un formateur remplaçant.

La réaffectation contiendra :

- la séance ;
- le formateur initial ;
- le remplaçant ;
- l’auteur de la modification ;
- le motif ;
- la date de la modification ;
- la période de validité ;
- le statut de notification.

Le remplaçant recevra uniquement les autorisations nécessaires aux
séances concernées.

---

## 10. Gestion des plannings

### 10.1 Responsabilité

Le responsable pédagogique est le propriétaire fonctionnel du planning
de son périmètre.

Il peut :

- importer un planning ;
- enregistrer un brouillon ;
- consulter les anomalies ;
- corriger les lignes ;
- valider le contenu ;
- publier le planning ;
- créer les séances ;
- republier une version corrigée.

### 10.2 Formats

Les formats sont priorisés ainsi :

1. CSV obligatoire ;
2. Excel `.xlsx` souhaité ;
3. PDF texte expérimental ;
4. PDF scanné hors périmètre.

### 10.3 Modèle minimal

```text
formation_code
class_code
course_code
course_name
teacher_email
session_date
start_time
end_time
room
attendance_mode
remote_link
```

### 10.4 Cycle d’importation

```text
Téléversement
    ↓
Contrôle du type de fichier
    ↓
Analyse des colonnes
    ↓
Normalisation
    ↓
Validation métier
    ↓
Détection des conflits
    ↓
Prévisualisation
    ↓
Correction
    ↓
Confirmation humaine
    ↓
Création ou mise à jour des séances
    ↓
Publication
```

### 10.5 Assistance par intelligence artificielle

L’intelligence artificielle pourra :

- suggérer la correspondance entre les colonnes ;
- reconnaître différents noms d’en-tête ;
- normaliser les formats de date ;
- rapprocher un nom de formateur d’un compte existant ;
- détecter des valeurs inhabituelles ;
- identifier des doublons probables ;
- signaler des chevauchements ;
- proposer le mode de participation ;
- produire une synthèse des erreurs.

L’IA ne publiera jamais directement un planning. Une confirmation
humaine restera obligatoire.

---

## 11. Gestion des séances

### 11.1 Création

Les séances seront créées à partir d’un planning validé et publié.

Une séance contiendra :

- une formation ;
- une classe ;
- une matière ;
- un formateur principal ;
- éventuellement un remplaçant ;
- une date ;
- une heure de début ;
- une heure de fin ;
- une salle ;
- un mode de participation ;
- éventuellement un lien distant ;
- un statut.

### 11.2 Statuts

- `DRAFT` ;
- `PLANNED` ;
- `OPEN` ;
- `CLOSED` ;
- `CANCELLED`.

### 11.3 Modes de participation

- `PRESENTIAL` ;
- `REMOTE` ;
- `HYBRID`.

Pour une séance hybride, certains apprenants pourront être autorisés
individuellement à participer à distance.

---

## 12. Émargement intelligent

### 12.1 Parcours principal

1. Le formateur consulte ses séances.
2. Il ouvre la séance concernée.
3. Le serveur génère un jeton temporaire.
4. Le jeton est enregistré dans Redis avec une expiration.
5. Il est affiché sous forme de QR code.
6. L’apprenant ouvre ESIC Connect.
7. Il scanne le QR code.
8. Le système contrôle l’authentification et l’inscription.
9. Le terminal réalise, si disponible, une vérification WebAuthn.
10. Le serveur contrôle le jeton, la séance et les doublons.
11. La présence est enregistrée dans MySQL.
12. Le formateur voit la liste actualisée.
13. Les événements sensibles sont audités.

### 12.2 QR code dynamique

Le QR code ne contiendra aucune donnée personnelle directement
exploitable.

Il contiendra un jeton :

- aléatoire ;
- temporaire ;
- non prédictible ;
- associé à une séance ;
- signé ou vérifiable ;
- révocable ;
- protégé contre le rejeu ;
- soumis à une durée de validité limitée.

### 12.3 Vérification locale avec WebAuthn

La plateforme pourra utiliser WebAuthn ou les passkeys pour demander
une vérification locale par :

- empreinte digitale ;
- reconnaissance faciale du terminal ;
- code PIN ;
- mécanisme de déverrouillage de l’appareil.

ESIC Connect ne recevra et ne stockera ni empreinte digitale ni modèle
facial. La plateforme recevra une preuve cryptographique produite par
l’authentificateur du terminal.

Cette fonction sera présentée comme une confirmation locale renforcée,
et non comme une preuve absolue de l’identité physique de la personne
tenant le téléphone.

Une solution de secours sera prévue pour :

- les appareils incompatibles ;
- les utilisateurs sans biométrie ;
- les besoins d’accessibilité ;
- la perte ou le changement de terminal.

### 12.4 Solutions de secours

- saisie d’un code temporaire ;
- validation manuelle par le formateur ;
- borne Raspberry Pi ;
- badge NFC en évolution.

---

## 13. Gestion de l’assiduité

### 13.1 Statuts

- `PRESENT` ;
- `ABSENT` ;
- `LATE` ;
- `PARTIAL` ;
- `EXCUSED` ;
- `TO_CONFIRM`.

### 13.2 Correction

Toute correction contiendra :

- l’ancienne valeur ;
- la nouvelle valeur ;
- le motif ;
- l’auteur ;
- la date et l’heure ;
- l’origine de la modification.

### 13.3 Prévention des fraudes

Le système pourra vérifier :

- l’expiration du jeton ;
- les doublons ;
- le nombre de tentatives ;
- les utilisations simultanées ;
- l’utilisation du même appareil par plusieurs comptes ;
- les événements provenant d’une borne non reconnue ;
- l’ouverture effective de la séance ;
- l’appartenance de l’apprenant à la classe ;
- la présence d’un remplacement autorisé.

Les anomalies ne déclencheront pas automatiquement une sanction.

---

## 14. Communication, notifications et réclamations

### 14.1 Notifications

Le système pourra produire des notifications :

- dans l’application ;
- sous forme de notifications push PWA ;
- par courrier électronique.

Événements concernés :

- invitation ;
- publication du planning ;
- modification d’une séance ;
- remplacement d’un formateur ;
- ouverture prochaine d’une séance ;
- disponibilité de l’émargement ;
- confirmation de présence ;
- mise à jour d’une réclamation ;
- traitement d’un justificatif.

### 14.2 Centre de notifications

Chaque notification comportera :

- un titre ;
- un message ;
- un type ;
- une date ;
- un état lu ou non lu ;
- un lien vers l’élément concerné.

### 14.3 Réclamations

L’apprenant pourra adresser une réclamation :

1. au formateur d’une séance ;
2. au responsable pédagogique ;
3. à l’administration scolaire.

Une réclamation comprendra :

- une catégorie ;
- un destinataire fonctionnel ;
- un objet ;
- un message ;
- une pièce jointe facultative ;
- un statut ;
- une priorité ;
- un historique.

### 14.4 Statuts des réclamations

- `OPEN` ;
- `IN_PROGRESS` ;
- `WAITING_FOR_STUDENT` ;
- `RESOLVED` ;
- `CLOSED` ;
- `REJECTED`.

---

## 15. Rapports et tableaux de bord

### 15.1 Filtres

- formation ;
- promotion ;
- classe ;
- apprenant ;
- formateur ;
- matière ;
- date ;
- semaine ;
- mois ;
- année scolaire ;
- statut d’assiduité ;
- mode de participation.

### 15.2 Rapports

- rapport journalier d’une classe ;
- rapport hebdomadaire d’une classe ;
- rapport mensuel d’une classe ;
- rapport annuel d’une classe ;
- rapport individuel d’un apprenant ;
- rapport par formation ;
- rapport par matière ;
- rapport par formateur ;
- rapport des anomalies ;
- rapport des réclamations ;
- rapport des invitations non activées.

### 15.3 Formats

Le prototype priorisera :

1. l’affichage dans l’interface ;
2. l’export CSV ;
3. l’impression depuis le navigateur ;
4. le PDF si le délai le permet.

### 15.4 Indicateurs

- taux de présence ;
- taux d’absence ;
- nombre de retards ;
- heures de cours prévues ;
- heures suivies ;
- évolution mensuelle ;
- classes les plus touchées ;
- apprenants pouvant nécessiter un accompagnement ;
- taux d’activation des comptes ;
- taux d’échec des invitations ;
- temps moyen de traitement des réclamations ;
- nombre d’anomalies détectées.

---

## 16. Sécurité par conception

### 16.1 Mots de passe

Les mots de passe devront être :

- hachés avec Argon2id ou BCrypt ;
- soumis à une longueur minimale ;
- protégés contre les tentatives répétées ;
- réinitialisables avec un jeton temporaire ;
- absents des journaux.

### 16.2 Réinitialisation

Le parcours sera :

1. saisie de l’adresse électronique ;
2. réponse neutre ;
3. génération d’un jeton limité dans le temps ;
4. envoi du lien ;
5. vérification ;
6. définition du nouveau mot de passe ;
7. invalidation du jeton ;
8. révocation éventuelle des sessions ;
9. journalisation.

### 16.3 Authentification multifacteur

Le MFA TOTP pourra être :

- obligatoire pour `SUPER_ADMIN` ;
- obligatoire ou fortement recommandé pour `ADMIN` ;
- obligatoire ou recommandé pour `PEDAGOGICAL_MANAGER` ;
- facultatif pour les autres rôles.

Le système prévoira :

- l’enrôlement ;
- la confirmation ;
- les codes de récupération ;
- la révocation ;
- l’audit des changements.

### 16.4 Protection contre les robots

La solution privilégiée est Cloudflare Turnstile.

Elle pourra protéger :

- la connexion après comportement suspect ;
- la demande de réinitialisation ;
- l’activation du compte ;
- les formulaires publics.

La validation du jeton anti-bot sera effectuée côté serveur.

### 16.5 Limitation des tentatives

Redis pourra limiter :

- les connexions répétées ;
- les demandes de réinitialisation ;
- les validations de QR code ;
- les réémissions de courriels ;
- les créations automatisées de réclamations ;
- les appels sensibles de l’API.

### 16.6 Sessions et jetons

Le système prévoira :

- des jetons d’accès de courte durée ;
- un renouvellement sécurisé ;
- la rotation et la révocation ;
- l’invalidation après changement de mot de passe ;
- un stockage limitant les risques XSS ;
- une politique CORS restrictive ;
- une protection CSRF adaptée à l’architecture.

---

## 17. Cache et performance

### 17.1 Utilisation de Redis

Redis pourra contenir :

- les jetons d’émargement ;
- les jetons d’activation ;
- les données de limitation des requêtes ;
- les informations temporaires de session ;
- les plannings fréquemment consultés ;
- certains droits calculés ;
- les compteurs ;
- les événements temporaires ;
- les résultats de tableaux de bord coûteux.

### 17.2 Principes

Les données mises en cache respecteront :

- une durée de vie définie ;
- des clés tenant compte du périmètre d’autorisation ;
- une invalidation après modification ;
- la minimisation des données sensibles ;
- l’interdiction de contourner les autorisations ;
- une mesure du bénéfice réel.

### 17.3 Indicateurs de performance

Le prototype mesurera si possible :

- le temps de réponse sans cache ;
- le temps de réponse avec cache ;
- le taux de succès du cache ;
- le temps de génération d’un jeton ;
- le temps de validation d’un émargement ;
- le temps de chargement d’un planning.

L’objectif inférieur à 100 ms sera évalué sur des routes simples dans
l’environnement local de démonstration.

---

## 18. Messagerie électronique asynchrone

### 18.1 Flux cible

```text
Action métier
    ↓
Création d’un événement
    ↓
File de messages
    ↓
Service d’envoi
    ↓
Prestataire de messagerie
    ↓
Retour de statut
    ↓
Mise à jour de la traçabilité
```

### 18.2 Gestion des échecs

Après plusieurs tentatives infructueuses, le message pourra être placé
dans une Dead Letter Queue.

Les données de suivi seront :

- destinataire ;
- type de message ;
- nombre de tentatives ;
- dernière erreur ;
- prochaine tentative ;
- statut ;
- dates de création et de traitement.

### 18.3 Prototype local

Le prototype pourra utiliser :

- un serveur SMTP de développement ;
- une boîte de réception locale de test ;
- une table de messages en attente ;
- un traitement planifié ;
- une simulation contrôlée des statuts.

La file dédiée et la DLQ complète pourront rester dans l’architecture
cible si elles ne sont pas implémentées.

---

## 19. Intelligence artificielle

### 19.1 Assistance à l’importation

L’IA pourra assister :

- la reconnaissance des colonnes ;
- la normalisation ;
- la détection des incohérences ;
- la proposition de correspondances ;
- la synthèse des erreurs.

### 19.2 Détection d’anomalies

Le service Python pourra produire :

- un score de 0 à 1 ;
- un niveau `LOW`, `MEDIUM` ou `HIGH` ;
- une liste de raisons ;
- une recommandation de vérification humaine.

Les facteurs pourront inclure :

- QR code expiré ;
- tentatives répétées ;
- utilisation simultanée ;
- comportement inhabituel d’un dispositif ;
- appareil associé à plusieurs comptes ;
- horaire anormal ;
- durée de présence incohérente.

### 19.3 Prévention de l’absentéisme

En perspective, la plateforme pourra identifier :

- les absences répétées ;
- l’évolution du taux d’assiduité ;
- les retards fréquents ;
- la participation partielle ;
- une rupture soudaine par rapport aux habitudes.

### 19.4 Limites

L’intelligence artificielle ne devra pas :

- prononcer une sanction ;
- supprimer une présence ;
- refuser automatiquement un justificatif ;
- publier seule un planning ;
- transmettre une donnée à un acteur non autorisé ;
- utiliser des données réelles dans un service non approuvé.

---

## 20. Intégration IoT

### 20.1 Raspberry Pi

La Raspberry Pi pourra :

- posséder un identifiant unique ;
- publier un signal de vie ;
- simuler ou lire un badge ;
- envoyer un événement d’émargement ;
- recevoir un accusé de réception ;
- transmettre de la télémétrie ;
- stocker temporairement les événements en cas de coupure.

### 20.2 Communication

La communication reposera sur MQTT.

Les événements pourront contenir :

- l’identifiant du dispositif ;
- l’identifiant de la séance ;
- l’identifiant pseudonymisé du badge ;
- l’horodatage ;
- un identifiant unique d’événement ;
- un numéro de séquence ;
- le type d’événement ;
- une preuve d’authenticité.

### 20.3 Sécurité

La borne intégrera :

- une identité unique ;
- une authentification ;
- un chiffrement des communications ;
- une liste de dispositifs autorisés ;
- une protection contre le rejeu ;
- une journalisation ;
- un stockage local minimal ;
- une file locale en cas de perte réseau ;
- une reprise contrôlée après reconnexion.

---

## 21. Fonctionnalités différenciantes et perspectives

### 21.1 Carte de séance intelligente

Une carte unique pourra regrouper :

- la séance ;
- la salle ;
- le lien distant ;
- le formateur ;
- l’état de l’émargement ;
- les messages ;
- les documents ;
- les changements récents.

### 21.2 Mode hors ligne

L’application pourra permettre :

- la consultation du planning récent hors ligne ;
- la mise en file d’une action ;
- la synchronisation après reconnexion ;
- la gestion des conflits.

Une présence hors ligne ne sera pas définitivement validée sans contrôle
serveur.

### 21.3 Connexion sans mot de passe

Les passkeys pourront devenir le moyen principal de connexion afin de
réduire :

- le risque d’hameçonnage ;
- les mots de passe oubliés ;
- la dépendance aux codes SMS.

### 21.4 Calendrier unifié

Le planning pourra être synchronisé avec :

- Microsoft Outlook ;
- Microsoft Teams ;
- Google Calendar ;
- un flux iCalendar.

### 21.5 Détection des conflits

Le système pourra signaler :

- un formateur affecté à deux séances ;
- une salle utilisée simultanément ;
- une classe affectée à plusieurs cours ;
- une séance hors des horaires autorisés ;
- une durée inhabituelle.

### 21.6 Tableau de bord de qualité pédagogique

Le système pourra corréler :

- assiduité ;
- modalités de cours ;
- changements de planning ;
- taux de réclamation ;
- incidents techniques ;
- activation des comptes.

### 21.7 Parcours de secours accessible

Un apprenant ne pouvant utiliser la caméra ou WebAuthn disposera d’une
alternative contrôlée.

### 21.8 Journal de transparence étudiant

L’apprenant pourra consulter :

- la date d’enregistrement de sa présence ;
- le canal utilisé ;
- les modifications effectuées ;
- l’auteur d’une correction ;
- la justification ;
- les réclamations associées.

---

## 22. Architecture technique

### 22.1 Composants

- Back-end : Java 21, Spring Boot 3.5.x et Maven ;
- Sécurité : Spring Security, JWT, TOTP et WebAuthn ;
- Accès aux données : Spring Data JPA ;
- Migrations : Flyway ;
- Base principale : MySQL 8 ;
- Cache et données temporaires : Redis 7 ;
- Interface : Angular et Angular Material ;
- Mobile : PWA en priorité ;
- Service IA : Python, FastAPI et scikit-learn ;
- Communication IoT : MQTT ;
- Équipement : Raspberry Pi ;
- Messagerie locale : serveur SMTP de développement ;
- Conteneurisation : Docker Compose ;
- Documentation API : OpenAPI/Swagger ;
- Supervision : Spring Boot Actuator et journaux structurés.

### 22.2 Architecture logique

```text
Angular / PWA
      |
      | HTTPS
      v
Spring Boot API
   |      |       |          |
   v      v       v          v
 MySQL  Redis  Service IA  Service mail
                    ^
                    |
              Événements métier

Raspberry Pi
      |
     MQTT
      |
      v
Broker MQTT
      |
      v
Spring Boot API
```

---

## 23. Périmètre du prototype de trois jours

### 23.1 Obligatoire

- dépôt Git structuré ;
- documentation de cadrage ;
- cahier des charges ;
- authentification ;
- gestion des rôles ;
- cumul des rôles ;
- utilisateurs fictifs ;
- formations et classes ;
- import CSV des apprenants ;
- statut d’activation ;
- import CSV du planning ;
- validation de l’import ;
- création des séances ;
- consultation du planning ;
- ouverture d’une séance ;
- QR code temporaire ;
- enregistrement d’une présence ;
- tableau des présences ;
- rapport simple ;
- export CSV ;
- piste d’audit ;
- utilisation de Redis ;
- tests prioritaires ;
- démonstration locale.

### 23.2 Souhaité

- import Excel ;
- envoi local d’invitations ;
- suivi simulé de la délivrabilité ;
- mot de passe oublié ;
- gestion d’un remplacement ;
- PWA installable ;
- notifications internes ;
- réclamations simples ;
- connexion de la Raspberry Pi ;
- score d’anomalie.

### 23.3 Expérimental ou simulé

- WebAuthn ;
- MFA TOTP ;
- Cloudflare Turnstile ;
- notifications push réelles ;
- reconnaissance intelligente des colonnes ;
- modèle Isolation Forest ;
- import PDF structuré ;
- file de messages et DLQ complète ;
- synchronisation Microsoft Graph.

### 23.4 Hors périmètre

- reconnaissance faciale centralisée ;
- stockage de données biométriques ;
- géolocalisation permanente ;
- reconnaissance universelle de fichiers PDF ;
- application iOS publiée ;
- déploiement général à l’ESIC ;
- haute disponibilité réelle ;
- Kubernetes ;
- intégration complète à Teams ;
- décision disciplinaire automatisée.

### 23.5 Addendum de finalisation F2 (31 août 2026) — réduction de périmètre — **CADUC**

> **Statut : caduc depuis le 3 septembre 2026.** Section conservée pour la
> traçabilité de la décision, jamais comme description de l'état courant.
> Elle est remplacée par le § 23.6.

Décision prise le 31 août 2026 au checkpoint de finalisation F2
(commit `d7d2bfe`) : l'import du planning, sa prévisualisation, sa
publication, son versionnement et la création automatique des séances
depuis un planning étaient déclarés **non implémentés**, et les exigences
`EF-PLAN-001` à `EF-PLAN-007`, `EF-SES-001`, `RG-016`, `AC-007` et
`AC-008` classées `HORS_PÉRIMÈTRE_ASSUMÉ`.

### 23.6 Addendum de reprise de périmètre G1 (3 septembre 2026) — le planning est dans le périmètre livré

**Fait constaté.** Le lot produit G1, fusionné sur `main` le
1er septembre 2026 par la PR #40 (commit `d3450e6`), donc **après**
l'addendum F2 ci-dessus, a livré un module `planning` complet :
migrations Flyway `V12` / `V13`, `PlanningImportController`
(`POST /api/v1/planning-imports`, `POST /{id}/publish`),
`PlanningVersionController`, port `coursesession.PlanningSessionWriter`,
écrans Angular `/planning/import`, `/planning/import/:jobId` et
`/planning/versions`.

**Vérification indépendante.** L'audit QA du 3 septembre 2026 a piloté un
navigateur réel contre l'application démarrée et a exécuté le parcours
import CSV → simulation → publication atomique → nouvelle version visible
(`tests/05-planning.spec.ts`). Source : `audit-report.md`, § 2 et
finding F-DOC-1.

**Décision du porteur de projet (3 septembre 2026).** Le domaine planning
est **repris dans le périmètre livré** ; l'addendum F2 est annulé sur ce
point. En conséquence :

- `EF-PLAN-001` à `EF-PLAN-005`, `EF-PLAN-007`, `EF-SES-001`, `RG-016`,
  `AC-007` et `AC-008` sont `IMPLEMENTED_AND_TESTED` ;
- `EF-PLAN-006` (création manuelle d'un planning plein calendrier) reste
  `HORS_PÉRIMÈTRE_ASSUMÉ` ;
- `EF-PLAN-003` (correction ligne à ligne dans l'écran de revue) reste
  `PARTIAL` : le repli livré est l'annulation du job puis le réimport
  (`DEC-G1-003`) ;
- le versionnement reste `PARTIAL` : le conflit **salle** contre les
  séances déjà publiées n'est pas détecté et il n'existe pas de retour à
  une version antérieure.

Le parcours prioritaire de `CLAUDE.md` (« Import planning → Publication →
Création des séances ») est donc **complet** dans ce prototype, au niveau
de détail précisé ci-dessus. État par capacité : `docs/CURRENT-STATE.md`.
Traçabilité de la contradiction et de sa résolution : `audit-report.md`,
finding F-DOC-1.

---

## 24. Règles de gestion principales

- **RG-01** : un utilisateur peut posséder plusieurs rôles.
- **RG-02** : les autorisations effectives correspondent à l’union
  contrôlée de ses rôles.
- **RG-03** : le super administrateur est réservé aux opérations
  techniques sensibles.
- **RG-04** : un responsable pédagogique ne consulte que son périmètre.
- **RG-05** : une classe appartient à une formation et à une année.
- **RG-06** : le responsable peut importer les apprenants de son
  périmètre.
- **RG-07** : l’adresse électronique d’un compte actif est unique.
- **RG-08** : une invitation expire après une durée définie.
- **RG-09** : un formateur externe suit un parcours d’activation sécurisé.
- **RG-10** : une séance provient d’un planning publié.
- **RG-11** : un formateur ne crée pas librement une séance.
- **RG-12** : un remplacement est autorisé et audité.
- **RG-13** : seul le formateur affecté ou son remplaçant ouvre la séance.
- **RG-14** : un apprenant émarge uniquement pour une séance autorisée.
- **RG-15** : une présence est unique par séance et par apprenant.
- **RG-16** : le jeton possède une durée de validité limitée.
- **RG-17** : un jeton expiré ou révoqué est refusé.
- **RG-18** : une correction manuelle exige un motif.
- **RG-19** : toute correction est auditée.
- **RG-20** : un fichier invalide ne crée aucune donnée avant confirmation.
- **RG-21** : les conflits de planning sont signalés.
- **RG-22** : les résultats IA sont soumis à validation humaine.
- **RG-23** : les rapports respectent le périmètre de l’utilisateur.
- **RG-24** : aucune donnée biométrique brute n’est stockée.
- **RG-25** : WebAuthn dispose d’un parcours alternatif.
- **RG-26** : la délivrabilité dépend du retour du prestataire.
- **RG-27** : les opérations externes sont traitées de façon asynchrone.
- **RG-28** : le cache ne contourne jamais les autorisations.
- **RG-29** : une réclamation conserve son historique.
- **RG-30** : la démonstration utilise uniquement des données fictives.

---

## 25. Contraintes

### 25.1 Temps

Le prototype doit être produit en trois jours.

Le parcours prioritaire est :

```text
Import des apprenants
→ Import du planning
→ Publication
→ Création des séances
→ Ouverture par le formateur
→ Émargement
→ Rapport
```

### 25.2 Technique

- développement local ;
- environnement conteneurisé ;
- VS Code ;
- Claude Code ;
- Spring Boot ;
- Angular ;
- MySQL ;
- Redis ;
- Python ;
- Raspberry Pi ;
- déploiement cloud différé.

### 25.3 Confidentialité

- données fictives ;
- minimisation des informations ;
- limitation des droits ;
- aucun secret dans Git ;
- protection des pièces jointes ;
- pseudonymisation pour l’IA ;
- aucune donnée biométrique du terminal stockée.

### 25.4 Accessibilité

Les fonctions essentielles disposeront d’alternatives :

- code au lieu du scan ;
- mécanisme sécurisé lorsque WebAuthn est indisponible ;
- validation par le formateur ;
- navigation au clavier ;
- contrastes adaptés ;
- erreurs compréhensibles ;
- compatibilité avec les technologies d’assistance.

---

## 26. Risques

| Risque | Probabilité | Impact | Atténuation |
|---|---:|---:|---|
| Périmètre trop large | Élevée | Élevé | Prioriser le parcours principal |
| Erreur d’importation | Moyenne | Élevé | Prévisualisation et confirmation |
| Adresses électroniques invalides | Élevée | Moyen | Validation, suivi et réémission |
| Mauvaise interprétation du statut mail | Moyenne | Moyen | Séparer envoi et délivrabilité |
| Import PDF trop complexe | Élevée | Moyen | Prioriser CSV et Excel |
| Attaques automatisées | Élevée | Élevé | Turnstile et limitation |
| Attaque par force brute | Élevée | Élevé | Limitation, verrouillage et MFA |
| Prêt du téléphone | Moyenne | Élevé | WebAuthn, audit et alertes |
| WebAuthn indisponible | Moyenne | Moyen | Parcours de secours |
| Erreur sur les rôles cumulés | Moyenne | Élevé | Tests d’autorisation |
| Fuite par le cache | Faible | Élevé | Clés contextualisées |
| Cache obsolète | Moyenne | Moyen | TTL et invalidation |
| Démonstration instable | Moyenne | Élevé | Vidéo et environnement local |
| Erreur produite par l’IA | Moyenne | Élevé | Contrôle humain |
| Défaillance Raspberry Pi | Moyenne | Moyen | Simulateur MQTT |
| Formateur externe non activé | Moyenne | Moyen | Relance et réaffectation |
| Remplacement non communiqué | Moyenne | Élevé | Notification et audit |
| Messagerie indisponible | Moyenne | Moyen | File d’attente et nouvelle tentative |
| Utilisation accidentelle de données réelles | Faible | Élevé | Données synthétiques |
| Objectif de 100 ms non atteint | Moyenne | Faible | Mesure et optimisation ciblée |

---

## 27. Indicateurs de réussite

Le prototype sera considéré comme réussi si :

1. un utilisateur peut se connecter selon ses rôles ;
2. un utilisateur peut cumuler deux rôles ;
3. les droits respectent le périmètre ;
4. un responsable peut importer une liste d’apprenants ;
5. les erreurs sont affichées avant confirmation ;
6. les comptes sont créés en attente d’activation ;
7. les invitations sont tracées ;
8. un planning CSV peut être importé ;
9. les séances sont créées depuis le planning ;
10. le formateur consulte ses séances ;
11. un remplaçant peut être affecté ;
12. une séance peut être ouverte ;
13. un QR code temporaire peut être généré ;
14. un apprenant peut émarger ;
15. la présence est immédiatement visible ;
16. une correction est auditée ;
17. un rapport de classe ou d’apprenant est produit ;
18. un export CSV est disponible ;
19. les principales routes sont testées ;
20. le parcours principal est démontrable localement.

---

## 28. Livrables

- note de cadrage ;
- cahier des charges ;
- backlog priorisé ;
- architecture fonctionnelle ;
- architecture technique ;
- modèle de données ;
- registre des risques ;
- analyse de sécurité ;
- analyse RGPD ;
- matrice de traçabilité RNCP ;
- prototype fonctionnel ;
- code source ;
- migrations ;
- données fictives ;
- tests ;
- documentation API ;
- scripts Docker Compose ;
- guide d’installation ;
- guide d’utilisation ;
- guide de démonstration ;
- rapport de soutenance ;
- présentation ;
- journal d’utilisation de l’intelligence artificielle ;
- vidéo de démonstration de secours.

---

## 29. Hypothèses

- les informations internes inconnues sont signalées comme étant à valider ;
- un modèle de fichier peut être imposé aux responsables ;
- les utilisateurs disposent d’une adresse électronique ;
- certains formateurs utilisent une adresse externe ;
- le prototype emploie des comptes fictifs ;
- la Raspberry Pi accède au réseau local ;
- les fonctions externes peuvent être simulées ;
- l’architecture cible peut être documentée sans être déployée ;
- la soutenance accepte une preuve de concept clairement identifiée.

---

## 30. Traçabilité RNCP 39394

### Bloc 1 — Pilotage stratégique

- cadrage ;
- analyse des besoins ;
- périmètre ;
- priorités ;
- planning ;
- risques ;
- indicateurs ;
- gouvernance ;
- conduite du changement ;
- stratégie d’évolution.

### Bloc 2 — Développement et technologies avancées

- application Angular ;
- API Spring Boot ;
- import CSV et Excel ;
- MySQL ;
- Redis ;
- WebAuthn ;
- tableaux de bord ;
- rapports ;
- service Python ;
- tests ;
- documentation API.

### Bloc 3 — Infrastructure et cybersécurité

- Docker Compose ;
- authentification ;
- autorisations ;
- MFA ;
- anti-bot ;
- limitation des requêtes ;
- cache sécurisé ;
- audit ;
- supervision ;
- sécurisation des API ;
- traitement des incidents ;
- détection des anomalies.

### Bloc 4 — IoT sécurisé et IA

- Raspberry Pi ;
- MQTT ;
- identité du dispositif ;
- télémétrie ;
- mode dégradé ;
- détection d’événements anormaux ;
- intégration au système d’information.

---

## 31. Décision de lancement

Le projet peut être lancé sous réserve :

- de conserver un périmètre strict pendant les trois jours ;
- de réaliser d’abord le parcours principal ;
- de retenir CSV comme format obligatoire ;
- de considérer Excel comme un objectif secondaire ;
- de considérer PDF comme expérimental ;
- de traiter WebAuthn, MFA et anti-bot selon le temps disponible ;
- de distinguer les fonctions réalisées, simulées, conçues et hors
  périmètre ;
- de vérifier humainement les productions des assistants IA.
```

## Référence minimale à placer dans `CLAUDE.md`

Ne recopie pas tout le cadrage dans `CLAUDE.md`. Ajoute simplement :

```markdown
## Documents de référence

Avant toute analyse fonctionnelle ou modification du périmètre, consulter :

- `docs/01-cadrage.md` : vision, objectifs, acteurs, périmètre et contraintes ;
- `docs/02-cahier-des-charges.md` : exigences fonctionnelles et techniques ;
- `docs/CURRENT-STATE.md` : état réel de l’implémentation.

Ne lire intégralement `docs/01-cadrage.md` que si la tâche concerne
le métier, le périmètre, les rôles, l’architecture ou la documentation.

Le code et les tests constituent la source de vérité concernant les
fonctionnalités réellement réalisées.
