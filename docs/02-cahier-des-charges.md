# Cahier des charges fonctionnel et technique — ESIC Connect

## Métadonnées

| Élément | Valeur |
|---|---|
| Produit | ESIC Connect |
| Type | Cahier des charges fonctionnel et technique |
| Établissement | ESIC |
| Porteur | Abubacar AFOLABI |
| Version | **2.0** |
| Date | 3 septembre 2026 |
| Statut | Référence active |
| Remplace | version 1.0 du 27 août 2026 et ses addendums F2 et G1 |
| Document de cadrage | `docs/01-cadrage.md` v3.0 |
| Horizon | version 1.0 du produit, 13 sprints de deux semaines |

---

# 0. Ce que cette version change

La version 1.0 décrivait les exigences d'une **preuve de concept de
trois jours**. Elle classait la majorité des domaines en « souhaité »,
« expérimental » ou « hors périmètre », et deux addendums successifs
avaient d'abord retiré puis réintégré le domaine planning.

Cette version 2.0 décrit les exigences d'une **application complète**.

| Ancienne notion | Devient |
|---|---|
| §4.2 périmètre obligatoire du prototype | supprimé |
| §4.3 périmètre souhaité | exigences `MUST` ou `SHOULD` de la v1.0 |
| §4.4 périmètre expérimental | exigences `MUST` ou `SHOULD` de la v1.0 |
| §4.5.1 addendum F2 (planning exclu) | supprimé |
| §4.5.2 addendum G1 (planning réintégré) | supprimé — absorbé dans le corps du document |
| §4.5 hors périmètre | §3 exclusions de conception, réduites |
| Priorité `FUTURE` | supprimée — tout ce qui est décrit est à réaliser |

**Toutes les exigences de ce document sont à réaliser.** La priorité
`MUST` / `SHOULD` / `COULD` ordonne le travail dans le temps ; elle ne
désigne plus ce qui sera abandonné.

---

# 1. Objet

Ce cahier des charges décrit les exigences fonctionnelles, techniques,
sécuritaires et réglementaires d'ESIC Connect. Il constitue la référence
pour :

- définir le produit attendu ;
- décrire les parcours utilisateurs ;
- fixer les règles de gestion ;
- fixer les critères d'acceptation ;
- organiser le backlog, le développement et la recette ;
- distinguer ce qui est réalisé de ce qui reste à faire.

Il se lit avec : `docs/01-cadrage.md`, `docs/03-architecture.md`,
`docs/04-modele-donnees.md`, `docs/05-product-backlog.md`,
`docs/07-risques.md`, `docs/08-securite-rgpd.md`,
`docs/09-strategie-tests.md`, `docs/CURRENT-STATE.md`.

---

# 2. Périmètre fonctionnel

Le produit couvre les domaines suivants, **tous inclus dans la version
1.0** :

| # | Domaine | Contenu |
|---|---|---|
| D01 | Identité et accès | authentification, passkeys, MFA, anti-robot, limitation, sessions, appareils de confiance |
| D02 | Utilisateurs | cycle de vie des comptes, rôles, opérations de masse, doublons |
| D03 | Référentiels pédagogiques | années, formations, niveaux, promotions, classes, matières |
| D04 | Organisation physique | sites, bâtiments, salles, équipements, plages réseau |
| D05 | Inscriptions | profils apprenants, inscriptions historisées, changements de classe |
| D06 | Alternance | rythmes, affectations, exceptions individuelles et collectives |
| D07 | Import de population | CSV, Excel, classeur multifeuille, simulation, confirmation |
| D08 | Invitations | jetons, envoi, délivrabilité, réémission, activation |
| D09 | Corps enseignant | formateurs internes, externes, affectations, remplacements |
| D10 | Planning | import CSV/Excel/PDF texte, assistant IA, correction, conflits, versions, publication |
| D11 | Séances | création, cycle de vie, annulation, report, remplacement |
| D12 | Émargement | cinq canaux, jetons, points de contrôle, anti-rejeu |
| D13 | Assiduité | statuts, demi-journées, retards, corrections auditées |
| D14 | Justificatifs | dépôt, pièces jointes, examen, décision, effets |
| D15 | Réclamations | conversation, transfert, cycle de vie, historique |
| D16 | Notifications | in-app, email, push PWA, préférences, centre |
| D17 | Restitution | tableaux de bord, rapports, exports CSV/Excel/PDF, attestations |
| D18 | Intelligence artificielle | assistance à l'import, anomalies, prévention du décrochage |
| D19 | Objets connectés | borne MQTT, identité, télémétrie, mode dégradé, simulateur |
| D20 | Intégrations | Microsoft Graph, Teams, iCalendar, Google Calendar, SMTP |
| D21 | Mobilité | PWA installable, hors ligne, file d'actions différées |
| D22 | Sécurité et audit | autorisations, piste d'audit, incidents, durcissement |
| D23 | Conformité | RGPD, conservation, purge, droits des personnes |
| D24 | Exploitation | santé, métriques, journaux, sauvegarde, restauration, déploiement |

---

# 3. Exclusions de conception

Seules ces exclusions sont définitives. Elles sont motivées dans
`docs/01-cadrage.md` §19.

- reconnaissance faciale centralisée ;
- stockage de données biométriques brutes ;
- géolocalisation permanente ;
- reconnaissance de PDF scanné (le PDF **texte** est pris en charge) ;
- décision disciplinaire automatisée ;
- suppression automatique d'un apprenant ;
- remplacement de Microsoft Teams comme outil de visioconférence ;
- application iOS native publiée sur l'App Store ;
- Kubernetes et architecture en microservices.

Tout le reste est **dans le périmètre**.

---

# 4. Terminologie

| Terme | Définition |
|---|---|
| Formation | parcours pédagogique — BTS, Bachelor, Mastère |
| Niveau | échelon dans un cursus — BTS 1, Master 2 |
| Promotion | cohorte rattachée à une formation et à une année |
| Classe | groupe principal d'appartenance d'un apprenant sur une période |
| Inscription | association historisée apprenant / classe / période |
| Rythme | modèle d'alternance école / entreprise |
| Créneau | ligne de planning : classe, date, horaire, cours, formateur, salle |
| Séance | occurrence datée d'un cours, issue d'un créneau publié ou créée exceptionnellement |
| Point de contrôle | moment auquel une présence doit être confirmée |
| Demi-journée | unité de mesure de l'assiduité — matin ou après-midi |
| Publication | mise à disposition d'une version validée du planning, créant les séances |
| Émargement | action de confirmer une présence |
| Canal | moyen par lequel une présence a été enregistrée |
| Justificatif | document expliquant une absence ou un retard |
| Réclamation | demande adressée par un utilisateur à un acteur compétent |
| Attestation | document d'assiduité produit par le système, identifiable |
| Passkey | justificatif d'identité WebAuthn lié à un appareil |
| Outbox | table d'effets de bord garantissant l'exactement-une-fois après commit |
| Borne | dispositif d'émargement en salle communiquant en MQTT |
| Audit | historique inaltérable des opérations significatives |

---

# 5. Acteurs et rôles

## 5.1 Principes

Un utilisateur peut posséder plusieurs rôles. Les autorisations sont
contrôlées à quatre niveaux : **route**, **service métier**,
**ressource**, **périmètre pédagogique**.

Le cumul de rôles ne permet jamais de contourner une restriction de
périmètre. Un utilisateur multi-rôles choisit un **contexte d'usage**
dans l'interface ; ce contexte est transmis au serveur et vérifié contre
les autorités réellement détenues.

## 5.2 `SUPER_ADMIN`

Contrôle technique global. Peut : gérer les comptes administrateurs,
configurer les paramètres critiques et les politiques de sécurité,
consulter les journaux de sécurité, gérer les dispositifs connectés et
les révoquer, gérer les plages réseau autorisées, révoquer des sessions,
suspendre un compte compromis, superviser les intégrations, lancer les
procédures de maintenance, consulter les résultats de sauvegarde,
supprimer un doublon après contrôle.

Contraintes : compte distinct du compte quotidien ; MFA **obligatoire** ;
actions fortement auditées ; jamais utilisé pour les tâches courantes ;
aucune suppression définitive sans double confirmation.

## 5.3 `ADMIN`

Administration fonctionnelle. Peut : gérer les utilisateurs, attribuer
les rôles non critiques, administrer tous les référentiels, gérer les
années scolaires, suivre les imports, traiter les doublons, consulter et
relancer les invitations, gérer les paramètres fonctionnels, consulter
l'audit fonctionnel, assister les responsables pédagogiques.

Ne peut pas : modifier les secrets techniques, effacer l'audit, créer un
super administrateur.

## 5.4 `SCHOOL_ADMINISTRATION`

Peut : importer des apprenants, rechercher un apprenant ou une classe,
consulter les présences, gérer les justificatifs, intervenir dans les
réclamations, produire tous les rapports, générer les attestations,
exporter les données autorisées, consulter les statistiques globales,
suspendre ou archiver un compte selon la procédure.

## 5.5 `PEDAGOGICAL_MANAGER`

Propriétaire fonctionnel de son périmètre. Peut : gérer ses formations,
promotions et classes ; importer des apprenants ; émettre et suivre les
invitations ; créer les comptes de formateurs externes ; importer,
construire, corriger, versionner et publier un planning ; affecter un
formateur ; nommer un remplaçant ; annuler ou reporter une séance ;
créer une séance exceptionnelle ; gérer les liens distanciels ; traiter
justificatifs et réclamations ; autoriser un suivi à distance ;
consulter tableaux de bord et rapports de son périmètre ; autoriser le
téléchargement du rapport par un apprenant.

Ne peut pas : sortir de son périmètre, supprimer définitivement un
utilisateur, supprimer un historique pédagogique, modifier les
paramètres techniques.

## 5.6 `TEACHER`

Peut : consulter son planning et ses séances, y compris celles qui lui
sont déléguées ; demander une annulation ; proposer un remplaçant ;
ouvrir et clôturer une séance ; afficher le QR dynamique et le code
court ; suivre les présences en direct ; enregistrer une présence
manuelle motivée ; ajouter un apprenant provisoire ; saisir un motif de
retard ; autoriser exceptionnellement un émargement tardif ; enregistrer
un départ anticipé ; joindre un justificatif transmis en classe ;
corriger une présence pendant la période autorisée ; répondre aux
réclamations liées à ses séances.

Ne peut pas : créer librement une séance de planning, publier un
planning, valider son propre remplacement, supprimer une présence,
modifier une présence ancienne sans autorisation.

## 5.7 `STUDENT`

Peut : activer son compte ; enregistrer et gérer ses passkeys ; consulter
son planning et s'y abonner depuis un agenda externe ; recevoir les
notifications et régler ses préférences ; scanner un QR ou saisir un
code court ; confirmer sa présence ; consulter son historique et son
taux d'assiduité ; consulter le journal de transparence de ses
présences ; déposer un justificatif ; créer une réclamation et suivre
ses réponses ; télécharger son rapport lorsque cette fonction est
autorisée ; exercer ses droits RGPD.

Ne peut pas : consulter la donnée d'un autre apprenant, modifier
directement une présence, émarger hors d'une séance autorisée, utiliser
un jeton expiré.

---

# 6. Référentiels pédagogiques

## 6.1 Formations

| Champ | Obligatoire | Description |
|---|---:|---|
| Identifiant public | Oui | UUID non prédictible |
| Code | Oui | code unique lisible |
| Nom | Oui | intitulé |
| Type | Oui | BTS, Bachelor, Mastère, autre |
| Description | Non | présentation |
| Statut | Oui | actif, inactif, archivé |
| Responsable principal | Oui | un `PEDAGOGICAL_MANAGER` |
| Responsables délégués | Non | plusieurs possibles |
| Horodatages | Oui | création, modification |

## 6.2 Niveaux, années, promotions

Les niveaux sont configurables (BTS 1, Bachelor 3, Master 2…).

Une **année scolaire** porte un nom, une date de début, une date de fin,
un statut et une éventuelle période d'archivage. Elle n'est pas limitée
à une convention figée.

Une **promotion** rattache une cohorte à une formation et à une année.

## 6.3 Classes

Une classe porte : code unique dans son contexte, nom, formation,
niveau, promotion, année, responsable pédagogique, capacité, rythme
d'alternance, statut, liste d'inscriptions.

Un apprenant n'appartient qu'à **une seule classe principale active**
par période, mais conserve toutes ses inscriptions historiques. Lors
d'un changement de classe : l'ancienne inscription est clôturée,
l'historique conservé, la nouvelle créée, aucune donnée écrasée, et
l'opération auditée.

## 6.4 Matières

Une matière porte un code, un nom, un volume horaire indicatif et un
rattachement possible à une ou plusieurs formations. Une matière n'a
**pas** de formateur unique global : l'affectation se fait au niveau de
la séance, d'une période, ou d'une association classe–matière–période.

## 6.5 Cours communs et groupes

Une séance peut concerner **plusieurs classes**. Le système distingue la
classe principale de l'apprenant, les classes concernées par une séance,
et la liste réelle des participants attendus.

Les **groupes temporaires** (langues, options, projets) sont pris en
charge : un groupe rassemble des apprenants issus d'une ou plusieurs
classes pour une période, et peut être la cible d'un créneau de
planning.

---

# 7. Organisation physique

## 7.1 Sites, bâtiments, salles

Une salle porte : code, nom, bâtiment, étage, capacité, équipements,
état, QR fixe, identifiant de borne connectée éventuelle.

Un site porte une ou plusieurs **plages réseau** au format CIDR IPv4 ou
IPv6, utilisées pour vérifier qu'une requête d'émargement par QR fixe
provient bien de l'établissement.

## 7.2 Affectation et conflits

Une salle peut être connue à l'import, affectée plus tard, modifiée
avant la séance, ou laissée provisoirement indéterminée.

Le système signale : deux séances simultanées dans la même salle, une
capacité insuffisante, une salle inactive ou absente, une incohérence
entre la modalité et la salle (par exemple une salle affectée à une
séance entièrement distancielle).

---

# 8. Alternance

## 8.1 Objectif

Distinguer une **absence réelle** d'une journée sans cours, d'une
journée en entreprise, d'une semaine hors établissement prévue, ou d'une
exception imposant une présence.

## 8.2 Rythmes pris en charge

| Rythme | Description |
|---|---|
| A | trois jours à l'école, deux jours en entreprise, jours configurables |
| B | une semaine à l'école sur quatre |
| C | deux semaines à l'école sur quatre |
| Personnalisé | calendrier défini par le responsable pédagogique |

Un rythme se définit au niveau de la classe, avec exception possible au
niveau d'une période, d'un groupe ou d'un individu.

## 8.3 Règles

- une période en entreprise n'est **jamais** comptée comme une absence ;
- seules les séances publiées créent une attente de présence ;
- une séance exceptionnelle prime sur la règle d'alternance ;
- toute exception est datée, motivée et auditée ;
- le calcul d'assiduité se fonde sur les séances **réellement attendues**
  pour l'apprenant considéré ;
- la publication d'un planning **avertit** lorsqu'un créneau tombe sur
  une période résolue en entreprise pour la classe visée.

---

# 9. Utilisateurs

## 9.1 Données

Communes : identifiant public, nom, prénom, adresse électronique,
téléphone (facultatif), statut, rôles, horodatages.

Apprenant : numéro étudiant, date de naissance (facultative), statut
d'alternance, entreprise (facultative), classe active, historique des
inscriptions, dates d'entrée et de sortie, autorisation de suivi à
distance, autorisation de téléchargement du rapport.

Formateur : organisme éventuel, matières, période d'intervention,
caractère interne ou externe.

## 9.2 Adresse électronique

Unique par utilisateur, vérifiée, utilisée comme identifiant de
connexion. Une même adresse accompagne l'apprenant du BTS au Mastère :
un changement de classe ou d'année ne crée jamais un nouveau compte.

## 9.3 Statuts de compte

`PENDING_ACTIVATION`, `ACTIVE`, `SUSPENDED`, `LOCKED`, `ARCHIVED`.

Un départ d'établissement suspend ou archive le compte : il n'est pas
supprimé, la connexion est refusée, l'historique est conservé, la
réactivation reste possible par une action autorisée et auditée.

## 9.4 Opérations de masse

Suspension, archivage, déplacement, activation et relance groupées.
Avant exécution, le système affiche le nombre d'utilisateurs concernés,
les conséquences, les erreurs, les éléments ignorés, et demande
confirmation.

## 9.5 Suppression

La suppression fonctionnelle est remplacée par l'archivage. La
suppression définitive est réservée aux doublons avérés, aux données de
démonstration et aux demandes validées ; elle est exceptionnelle,
doublement confirmée, auditée, et impossible si elle détruit un
historique requis.

---

# 10. Import de la population

## 10.1 Acteurs et formats

Autorisés : `ADMIN`, `SCHOOL_ADMINISTRATION`, `PEDAGOGICAL_MANAGER`
(limité à son périmètre).

Formats : **CSV** et **Excel `.xlsx`**, y compris **classeur
multifeuille**.

## 10.2 Volume

Au minimum 500 apprenants par import, plusieurs imports successifs,
plusieurs classes dans un classeur.

## 10.3 Colonnes de référence

```text
student_number
last_name
first_name
email
phone
birth_date
formation_code
level_code
promotion_code
class_code
academic_year
work_study
work_study_pattern
company_name
```

## 10.4 Classeur multifeuille

Une feuille peut correspondre à une classe. La correspondance est
déterminée par le nom de la feuille, une colonne `class_code`, une
sélection manuelle, ou une suggestion de l'assistant d'importation.
**Aucune affectation n'est appliquée sans confirmation humaine.**

## 10.5 Deux phases obligatoires

**Simulation** — lecture, normalisation, validation, détection des
doublons intra-fichier et contre l'existant, calcul des changements,
affichage des anomalies. **Aucune écriture métier.**

**Application** — après confirmation : création, mise à jour,
changement de classe, invitation, rapport d'importation, audit. Une
transaction unique ; toute exception annule l'ensemble.

## 10.6 Utilisateur existant

Si l'adresse ou le numéro étudiant existe : aucun doublon n'est créé, le
compte existant est affiché, une mise à jour est proposée, la classe
actuelle et la classe cible sont montrées, la confirmation est demandée,
l'ancienne inscription est clôturée si nécessaire, la nouvelle est
créée, l'historique est conservé.

## 10.7 Erreurs

Chaque anomalie indique : fichier, feuille, ligne, colonne, valeur
reçue, motif, correction attendue, gravité (`INFO`, `WARNING`, `ERROR`,
`BLOCKING`). Une anomalie `BLOCKING` empêche la confirmation.

## 10.8 Assistance IA

L'assistant propose une correspondance de colonnes avec un score de
confiance, reconnaît les synonymes d'en-tête, normalise les formats, et
signale les résultats incertains. Une proposition de confiance faible
n'est jamais appliquée sans confirmation.

## 10.9 Critères d'acceptation

**IMP-STU-01** — 500 apprenants valides : toutes les lignes analysées,
aucun compte créé avant confirmation.
**IMP-STU-02** — apprenant déjà présent dans une nouvelle classe : mise à
jour proposée, aucun doublon.
**IMP-STU-03** — ligne invalide : ligne, colonne et raison affichées.
**IMP-STU-04** — après confirmation : bilan des créations, mises à jour,
déplacements, erreurs et lignes ignorées.
**IMP-STU-05** — classeur de trois feuilles : trois classes correctement
rattachées après confirmation du mapping.

---

# 11. Invitation et activation

## 11.1 Cycle

Création du compte en `PENDING_ACTIVATION` → génération d'un jeton
aléatoire (empreinte seule stockée) → durée de validité d'un mois →
préparation du courriel → envoi asynchrone → définition du mot de passe
→ proposition d'enregistrement d'une passkey → activation →
journalisation.

## 11.2 Contenu du message

Identité de la plateforme, motif de l'invitation, établissement, lien
temporaire, date d'expiration, procédure en cas d'erreur, mentions de
sécurité. Aucun mot de passe n'est transmis par courriel.

## 11.3 Suivi

Statuts internes : `QUEUED`, `SENT_TO_PROVIDER`, `PROCESSING_FAILED`.
Statuts de délivrabilité, lorsque le fournisseur les remonte :
`DELIVERED`, `BOUNCED`, `REJECTED`, `COMPLAINED`, `UNKNOWN`.

Un message n'est jamais considéré comme délivré du seul fait de sa
remise au serveur de messagerie.

L'interface permet de consulter le statut, la date de dernière
tentative, un motif d'erreur non sensible, de corriger l'adresse, de
révoquer l'ancien jeton, d'en générer un nouveau, de relancer l'envoi,
et d'auditer chaque action.

---

# 12. Corps enseignant

## 12.1 Formateurs externes

Créés sans adresse institutionnelle. Champs : nom, prénom, adresse
électronique, téléphone, organisme, matières, dates d'intervention. Le
domaine de l'adresse n'est **jamais** utilisé comme seul critère de
confiance. Activation par invitation sécurisée.

## 12.2 Affectation

Un formateur enseigne plusieurs matières, dans plusieurs classes, à
différentes dates, pour plusieurs formations. L'affectation se fait au
niveau de la séance, d'une période, ou d'une association
classe–matière–période.

## 12.3 Remplacement

Le responsable pédagogique désigne un remplaçant, sélectionne les
séances, saisit un motif, définit une période et choisit si les
apprenants sont notifiés. Le formateur initial peut **proposer** un
remplaçant, demander son remplacement ou une annulation, mais ne valide
jamais lui-même.

Une substitution porte : séance ou période, formateur initial,
remplaçant, auteur, motif, dates de validité, statut, état de
notification. Le formateur principal n'est jamais écrasé. Le remplaçant
obtient les droits de gestion **uniquement** pendant sa période.

Le formateur initial et le remplaçant sont **toujours** notifiés.

---

# 13. Planning

## 13.1 Responsabilité

Le responsable pédagogique est propriétaire du planning de son
périmètre. Il importe, construit, enregistre un brouillon, consulte les
anomalies, corrige, valide, publie, republie une version corrigée et
peut revenir à une version antérieure.

## 13.2 Sources et formats

| Format | Statut |
|---|---|
| CSV | pris en charge |
| Excel `.xlsx`, multifeuille | pris en charge |
| PDF **texte** structuré | pris en charge, avec revue obligatoire |
| Construction directe dans le calendrier | prise en charge |
| PDF scanné (image) | exclu — voir §3 |

## 13.3 Colonnes de référence

```text
academic_year
formation_code
promotion_code
class_code
group_code
session_date
half_day
start_time
end_time
course_code
course_name
teacher_email
room_code
attendance_mode
remote_link
work_study_exception
notes
```

## 13.4 Cycle d'importation

```text
Téléversement
    ↓ contrôle du type réel du fichier (magic bytes)
Analyse des colonnes  ←  assistance IA (score de confiance)
    ↓
Normalisation
    ↓
Validation métier
    ↓
Détection des conflits
    ↓
Prévisualisation
    ↓
Correction ligne à ligne
    ↓
Confirmation humaine
    ↓
Publication atomique versionnée
    ↓
Création ou mise à jour des séances
    ↓
Notification des acteurs concernés
```

Le fichier téléversé n'est **jamais écrit sur disque** ; seule son
empreinte est conservée pour la traçabilité.

## 13.5 Conflits détectés

Intra-fichier **et** contre les séances déjà publiées :

- un formateur affecté à deux créneaux simultanés ;
- une classe affectée à deux cours simultanés ;
- une **salle** occupée simultanément ;
- un créneau hors des plages horaires autorisées ;
- une durée inhabituelle ;
- une capacité de salle insuffisante ;
- un créneau tombant sur une période d'alternance en entreprise
  (avertissement, non bloquant) ;
- une séance sans formateur affecté (avertissement).

Un conflit **bloquant** interdit la publication.

## 13.6 Correction ligne à ligne

L'écran de revue permet de corriger une ligne en anomalie sans
recommencer l'import : modification de la valeur, revalidation immédiate
de la ligne et du lot, journalisation de la correction avec son auteur.
L'annulation du travail d'import puis le réimport restent possibles.

## 13.7 Construction directe

Le responsable pédagogique construit un planning dans un calendrier
interactif : ajout d'un créneau, modification, déplacement, duplication
d'une semaine, répétition d'une séance, application d'un rythme
d'alternance, enregistrement d'un brouillon, publication. Les mêmes
contrôles de conflit s'appliquent.

## 13.8 Statuts

`DRAFT`, `VALIDATING`, `READY_TO_PUBLISH`, `PUBLISHED`, `SUPERSEDED`,
`ARCHIVED`, `REJECTED`.

## 13.9 Versionnement

Chaque version porte : numéro, auteur, date, motif, nombre de
changements, statut, version précédente. Au minimum **trois versions**
sont conservées. Le responsable peut **revenir à une version
antérieure** : l'opération crée une nouvelle version dont le contenu est
celui de la version choisie, et n'efface jamais l'historique.

La publication est **atomique** : verrou sur le planning, revalidation
complète, création de la version N+1, passage de la version N en
`SUPERSEDED`, création ou mise à jour des séances. Une publication
concurrente est strictement idempotente.

L'identité d'un créneau est **stable et déterministe** : une republication
retrouve la même séance plutôt que d'en créer une seconde.

## 13.10 Modification d'une séance publiée

Toute modification crée une nouvelle version, identifie les champs
modifiés, notifie le formateur et, si nécessaire, les apprenants, met à
jour les calendriers synchronisés, invalide le cache et est auditée.

---

# 14. Séances

## 14.1 Création

Une séance **normale** provient d'un planning publié. Une séance
**exceptionnelle** est créée par un responsable pédagogique avec classe
ou groupe, matière, formateur, date, horaires, salle ou lien, motif et
type d'exception.

## 14.2 Statuts

`DRAFT`, `PLANNED`, `OPEN`, `CLOSED`, `CANCELLED`, `POSTPONED`.

Le cycle est strict : `PLANNED → OPEN → CLOSED`, sans réouverture.
`PLANNED` ou `OPEN → CANCELLED` avec motif. Une séance annulée reste
consultable en historique. Une séance supersédée est inactive partout.

## 14.3 Horaires de référence

```text
Matin      : 09:00 → 12:30
Après-midi : 13:30 → 17:00
Vendredi   : fin habituelle 16:00
```

Ces plages sont **configurables** par l'établissement.

## 14.4 Annulation et report

Le responsable pédagogique annule ; le formateur demande une annulation
(`REQUESTED`, `APPROVED`, `REJECTED`, `CANCELLED`). Une annulation
porte auteur, demandeur, motif, date, décision, notifications et
commentaire. Une séance annulée n'est pas reportée automatiquement : le
responsable définit une nouvelle date, ce qui crée une séance liée à
l'originale.

---

# 15. Modalités d'enseignement

## 15.1 Présentiel

L'apprenant est attendu sur site. Il utilise le QR fixe de salle avant
le début, le QR dynamique du formateur ensuite, ou une validation
manuelle exceptionnelle.

## 15.2 Distanciel collectif

La séance est déclarée à distance pour toute la classe. Le lien est
saisi manuellement, partagé dans l'application, ou **créé
automatiquement dans Teams** lorsque l'intégration Microsoft est active.

## 15.3 Distanciel individuel

Un apprenant peut être autorisé à distance alors que sa classe est en
présentiel. L'autorisation vaut pour une séance, une période ou l'année,
et porte apprenant, auteur, motif, période, statut et date de décision.
Sans autorisation, le canal distant est refusé ; le formateur peut
signaler l'exception et le responsable régulariser.

## 15.4 Hybride

Une même séance mélange participants sur site et à distance. Le **canal
de présence** est enregistré :

`ROOM_STATIC_QR`, `TEACHER_DYNAMIC_QR`, `REMOTE_QR`, `REMOTE_CODE`,
`TEACHER_MANUAL`, `PEDAGOGICAL_MANUAL`, `IOT_TERMINAL`.

---

# 16. Émargement

## 16.1 Principes

L'émargement doit être rapide, sécurisé, accessible, traçable,
compatible avec les cours hybrides, résistant au rejeu, et toujours
doublé d'un contrôle humain possible.

## 16.2 Points de contrôle journaliers

Quatre points de contrôle nommés :

| Type | Moment |
|---|---|
| `MORNING_ARRIVAL` | arrivée du matin |
| `MORNING_BREAK_RETURN` | retour de la pause du matin |
| `AFTERNOON_ARRIVAL` | arrivée ou retour après la pause de midi |
| `AFTERNOON_BREAK_RETURN` | retour de la pause de l'après-midi |

Les horaires exacts découlent du planning ou des paramètres de la
séance. Des points de contrôle supplémentaires de type `CUSTOM` restent
possibles pour les formats atypiques.

## 16.3 Calcul par demi-journée

Le **matin** est validé lorsque `MORNING_ARRIVAL` et
`MORNING_BREAK_RETURN` sont validés de façon cohérente.
L'**après-midi** est validé lorsque `AFTERNOON_ARRIVAL` et
`AFTERNOON_BREAK_RETURN` le sont.

| Résultat journalier | Règle |
|---|---|
| Journée complète | quatre validations cohérentes |
| Demi-journée matin | deux validations cohérentes du matin |
| Demi-journée après-midi | deux validations cohérentes de l'après-midi |
| `PARTIAL` | validations incomplètes |
| `TO_CONFIRM` | incohérence ou incident signalé |
| `ABSENT` | aucune validation, aucune correction |
| `EXCUSED` | absence justifiée et acceptée |

## 16.4 Tolérance de retard

| Délai après le début | Résultat |
|---|---|
| 0 → 15 minutes | `PRESENT` |
| 16 → 30 minutes | `LATE` |
| au-delà de 30 minutes | `LATE` **et** validation manuelle requise |
| après la fenêtre normale | autorisation exceptionnelle du formateur |

Les seuils sont configurables par l'établissement.

## 16.5 Fenêtre d'émargement

L'émargement ouvre 15 minutes avant le début, automatiquement ou sur
action du formateur, et reste ouvert jusqu'à 15 minutes après le début
dans le parcours standard. Au-delà : le QR fixe de salle n'est plus
accepté, le QR dynamique du formateur reste utilisable sous son
contrôle, et une présence tardive peut être enregistrée
exceptionnellement.

## 16.6 QR fixe de salle

Imprimé, associé à une salle, sans donnée personnelle. Il identifie une
**ressource de salle**, jamais directement une séance : le serveur
détermine la salle, la séance active ou imminente, l'apprenant, son
inscription et la fenêtre applicable.

Il est **refusé** après le début de la séance, hors plage réseau
autorisée, et en l'absence de séance correspondante.

## 16.7 Contrôle réseau

Le serveur vérifie que la requête provient d'une plage réseau autorisée,
configurée par le super administrateur. L'adresse IP est utilisée
**pendant la décision uniquement** : elle n'est jamais conservée dans
l'audit métier ni exposée dans un rapport. Sa présence éventuelle dans
les journaux techniques est limitée en durée et en accès.

## 16.8 QR dynamique du formateur

Lié à une séance et à un point de contrôle, généré côté serveur, stocké
dans Redis avec expiration, affiché sur l'écran du formateur, utilisable
en salle comme à distance selon la modalité.

Le code visuel change toutes les **10 secondes**. Le serveur accepte le
code courant et, pendant une brève période de grâce, le code
immédiatement précédent, afin d'absorber la latence, le temps de scan et
les écarts d'horloge.

## 16.9 Code court

Chaque QR dynamique s'accompagne d'un **code court** saisissable, de
même durée de vie, soumis aux mêmes vérifications. Il sert au
distanciel, aux problèmes de caméra, et à l'apprenant qui suit le cours
sur le téléphone avec lequel il devrait scanner.

## 16.10 Prévention du rejeu

Le serveur vérifie systématiquement : identifiant du jeton, expiration,
point de contrôle visé, séance, état du jeton, unicité de la validation,
identité de l'utilisateur, inscription et autorisation. Une seconde
validation du même point de contrôle par le même apprenant est refusée
sans effet de bord.

## 16.11 Présence manuelle

Le formateur enregistre une présence manuelle lorsque l'apprenant n'a
pas de smartphone, que la caméra est défaillante, que WebAuthn est
indisponible, qu'un incident technique survient, qu'un retard
exceptionnel est justifié, ou que l'apprenant suit à distance depuis un
ordinateur. La saisie comprend motif, canal, heure, auteur et
justification éventuelle. Elle est auditée.

## 16.12 Apprenant non inscrit

Le formateur crée une entrée provisoire (`UNREGISTERED_GUEST` ou
`PENDING_REGISTRATION`) avec nom, prénom, adresse si connue,
commentaire, séance et auteur. Cette entrée ne crée pas d'inscription
officielle, est signalée au responsable pédagogique, doit être
régularisée, et reste distincte d'un compte tant que la correspondance
n'est pas validée.

## 16.13 Départ anticipé

L'apprenant signale son départ au formateur, qui accepte, refuse,
recommande favorablement ou transmet au responsable pédagogique. Le
dossier porte apprenant, séance, heure de départ, motif, avis du
formateur, décision, auteur et commentaire. L'effet est `PARTIAL`,
`EXCUSED_PARTIAL` ou `TO_CONFIRM`.

## 16.14 Correction

Toute correction porte l'ancienne valeur, la nouvelle valeur, le motif,
l'auteur, la date, l'heure et l'origine. L'historique est
**append-only** ; aucune présence n'est supprimée, seulement annulée
logiquement.

## 16.15 Détection de fraude

Le système vérifie : expiration du jeton, doublons, nombre de
tentatives, utilisations simultanées, usage d'un même appareil par
plusieurs comptes, événements d'une borne non reconnue, ouverture
effective de la séance, appartenance de l'apprenant à la classe,
existence d'un remplacement autorisé. Les anomalies sont **signalées**,
jamais sanctionnées automatiquement.

---

# 17. Authentification

## 17.1 Identifiant et mot de passe

L'adresse électronique vérifiée est l'identifiant. Les mots de passe
sont hachés (BCrypt, migration Argon2id prévue), soumis à une longueur
minimale, contrôlés contre une liste de mots de passe courants, jamais
conservés en clair, jamais journalisés, et sans expiration périodique
arbitraire.

La réponse est **uniforme** pour un email inconnu, un mot de passe
erroné ou un compte inactif.

## 17.2 WebAuthn et passkeys

WebAuthn permet une connexion sans mot de passe sur un appareil
enregistré, une confirmation locale de l'émargement, et une meilleure
résistance à l'hameçonnage.

**Première connexion** : email → mot de passe → second facteur si requis
→ activation → proposition d'enregistrer une passkey → confirmation
locale → appareil ajouté aux appareils de confiance.

**Connexions suivantes** : sur un appareil reconnu, l'utilisateur emploie
sa passkey ; l'authentificateur local peut être une empreinte, une
reconnaissance faciale du terminal ou un code PIN.

ESIC Connect ne stocke ni empreinte ni modèle facial, ne reçoit aucune
donnée biométrique, et n'obtient qu'une réponse cryptographique. La
vérification reste sur le terminal.

**Secours** : mot de passe, code TOTP, codes de récupération, procédure
de récupération, validation manuelle contrôlée, réenrôlement d'appareil.

## 17.3 Second facteur TOTP

Obligatoire pour `SUPER_ADMIN` et `ADMIN`, ainsi que pour les opérations
sensibles du `PEDAGOGICAL_MANAGER`. Adaptatif pour les apprenants :
demandé à la première connexion, sur un nouvel appareil, lors d'un
changement inhabituel, lors d'une récupération de compte, d'une
réinitialisation de l'application ou d'un changement de moyen
d'authentification.

Le système gère l'enrôlement, la confirmation, les codes de
récupération, la révocation et l'audit des changements.

## 17.4 Authentification adaptative

Une vérification renforcée est exigée lorsque l'utilisateur change
d'appareil, change de pays de façon inhabituelle, réinitialise
l'application, réinitialise son mot de passe, ajoute ou supprime une
passkey, ou demande une action critique.

## 17.5 Tentatives échouées

Après trois échecs : ralentissement progressif, puis challenge
anti-robot, puis verrouillage **temporaire** si les tentatives
continuent. Aucun verrouillage définitif automatique. L'utilisateur peut
être notifié.

## 17.6 Limitation de débit

Redis limite : connexions répétées, demandes de réinitialisation,
validations de jeton d'émargement, réémissions de courriels, créations
de réclamations, et les appels sensibles de l'API. Les compteurs sont
fondés sur une empreinte d'identité et non sur une donnée personnelle en
clair.

## 17.7 Sessions et jetons

Jeton d'accès de courte durée, jeton de renouvellement avec rotation,
cookie `HttpOnly` `Secure` et `SameSite` approprié, protection CSRF
adaptée, politique CORS restrictive. **Aucun jeton sensible dans
`localStorage`.**

Expiration après 30 minutes d'inactivité, durée absolue configurable.
Un appareil de confiance bénéficie d'une reconnexion simplifiée sans
empêcher la révocation ni la réauthentification.

La session est invalidée lors de la déconnexion, de l'expiration, d'une
réinitialisation de mot de passe, d'une révocation, d'un incident ou
d'une désactivation de compte.

## 17.8 Mot de passe oublié

Saisie de l'adresse → **réponse neutre** → limitation du nombre de
demandes → challenge anti-robot si nécessaire → génération d'un jeton
aléatoire à usage unique et limité dans le temps, stocké sous forme
d'empreinte → envoi → vérification → définition du nouveau mot de passe
→ invalidation du jeton → révocation des sessions → notification →
audit.

Le système ne révèle jamais si une adresse existe.

## 17.9 Protection anti-robot

Cloudflare Turnstile protège la connexion après comportement suspect, la
demande de réinitialisation, l'activation de compte et les formulaires
publics. Le jeton est **validé côté serveur** ; un contrôle uniquement
présent dans le front n'est pas une protection. Les jetons sont à usage
unique et rejetés lorsqu'ils sont expirés ou déjà consommés.

Le service reste fonctionnel si le fournisseur est indisponible : la
politique de repli est définie et documentée.

---

# 18. Autorisations

## 18.1 Modèle

Contrôle d'accès par rôle, contrôle de périmètre pour les formations,
contrôle de propriété pour les données individuelles, contrôle
contextuel pour les séances.

## 18.2 Règles

Refus par défaut. Vérification côté serveur à chaque opération. Aucun
droit fondé sur l'affichage Angular. Identifiants exposés non
prédictibles. Tests systématiques des réponses `401` et `403`.

Une ressource hors périmètre renvoie `404` plutôt que `403` lorsque
l'existence même de la ressource est une information à protéger.

## 18.3 Cumul de rôles

Le cumul ne donne jamais un accès transversal non prévu. Un responsable
pédagogique également formateur gère ses formations et enseigne ses
séances, sans jamais voir les formations d'un autre responsable.

---

# 19. Justificatifs

## 19.1 Dépôt et portée

Déposé par l'apprenant, par le formateur pour son compte, par le
responsable pédagogique ou par l'administration. Il concerne une séance,
une demi-journée, une journée ou une période.

## 19.2 Pièces jointes

Formats : JPEG, PNG, PDF. Taille maximale : **5 Mo par fichier**.

Contrôles obligatoires : extension, type MIME déclaré, **type réel
dérivé du contenu** (magic bytes, rejet des archives et conteneurs),
taille. Nom interne généré. Fichiers non exécutables, stockés **hors
base et hors répertoire public**, droits d'accès restreints, traversée
de chemin impossible.

Une **analyse antivirus** est appliquée avant mise à disposition. Tant
qu'elle n'a pas rendu son verdict, la pièce est en quarantaine et n'est
pas téléchargeable.

Le téléchargement force `Content-Disposition: attachment` et
`X-Content-Type-Options: nosniff`, et n'est accessible qu'au
propriétaire et à un examinateur de son périmètre.

## 19.3 Cycle

`SUBMITTED`, `UNDER_REVIEW`, `ACCEPTED`, `REJECTED`,
`ADDITIONAL_INFORMATION_REQUIRED`, `EXPIRED`.

Le responsable pédagogique ou l'administration décide ; un refus exige
un motif. L'apprenant dispose d'un mois pour transmettre son
justificatif, délai configurable.

## 19.4 Effet

Un justificatif accepté transforme `ABSENT` en `EXCUSED`. Il ne
transforme jamais une absence en présence et n'efface jamais
l'historique de l'absence.

## 19.5 Conservation

12 mois par défaut, puis suppression ou archivage selon la politique
validée. Les métadonnées de traçabilité peuvent être conservées plus
longtemps si cela est justifié. Un balayage périodique détecte et
supprime les fichiers orphelins.

---

# 20. Réclamations

## 20.1 Principe

Échange conversationnel encadré, adressé au formateur, au responsable
pédagogique ou à l'administration scolaire. Ce n'est pas une messagerie
instantanée générale.

## 20.2 Contenu

Auteur, catégorie, sujet, description, séance ou période concernée,
destinataire fonctionnel, priorité, statut, pièces jointes, fil de
messages, historique complet.

Chaque message porte auteur, rôle utilisé, date, contenu, pièce jointe
éventuelle et visibilité.

## 20.3 Transfert

Le formateur transfère au responsable pédagogique ; le responsable
transfère à l'administration. Chaque transfert est motivé, daté, audité
et visible dans l'historique.

## 20.4 Statuts

`OPEN`, `IN_PROGRESS`, `WAITING_FOR_STUDENT`, `TRANSFERRED`,
`RESOLVED`, `CLOSED`, `REJECTED`, `REOPENED`.

Une réclamation clôturée peut être rouverte avec motif, nouveau message,
notification et trace d'audit.

---

# 21. Notifications

## 21.1 Canaux

Notification dans l'application, courrier électronique, **notification
push PWA**, et Microsoft Teams lorsque l'intégration est active.

## 21.2 Événements notifiés

Invitation ; publication d'un planning ; modification d'une séance ;
annulation ; changement ou remplacement de formateur ; rappel d'un cours
à venir ; ouverture de l'émargement ; confirmation de présence ;
correction d'une présence ; décision sur un justificatif ; mise à jour
d'une réclamation ; alerte de sécurité sur le compte.

## 21.3 Audience

Chaque événement définit son audience : formateur concerné, remplaçant,
apprenants de la classe, responsable pédagogique du périmètre,
administration. L'échec d'un destinataire n'interrompt jamais les
autres.

## 21.4 Garanties

Les notifications sont produites **après le commit** de la transaction
métier — une transaction annulée ne produit aucune notification — et
sont **idempotentes**. Une **outbox transactionnelle** garantit la
reprise : une panne du diffuseur ne perd pas la notification.

## 21.5 Centre de notifications

Titre, message, type, date, état lu ou non lu, lien vers la ressource
concernée. Fonctions : consulter, marquer comme lu, tout marquer,
ouvrir, filtrer, masquer sans effacer l'audit métier.

Les liens de navigation sont calculés côté client à partir d'une liste
blanche par rôle : le serveur ne transmet jamais un chemin d'interface.

## 21.6 Préférences

L'utilisateur règle ses canaux par catégorie. Les notifications
critiques de sécurité restent obligatoires.

---

# 22. Restitution

## 22.1 Unité de calcul

```text
Deux demi-journées validées = une journée
Une demi-journée validée    = 0,5 journée
```

Les horaires restent affichés, mais la mesure principale ne dépend pas
d'une connexion permanente à l'application.

## 22.2 Filtres

Formation, promotion, classe, groupe, apprenant, formateur, matière,
date, semaine, mois, année scolaire, statut d'assiduité, modalité,
canal de présence.

## 22.3 Rapports

| Rapport | Contenu |
|---|---|
| Journalier de classe | séances, apprenants, matin, après-midi, retards, absences, excusés, anomalies |
| Hebdomadaire et mensuel de classe | demi-journées attendues, présentes, absentes, excusées, taux, retards, évolution |
| Annuel de classe | statistiques mensuelles, taux global, répartition par matière et par modalité, apprenants à suivre |
| Individuel | identité, numéro étudiant, formation, historique de classe, périodes attendues, présences, absences, retards, excuses, taux, détail par séance |
| Par formation, par matière, par formateur | agrégats du périmètre |
| Anomalies | événements détectés, score, statut de revue |
| Réclamations | volumes, délais de traitement, statuts |
| Invitations non activées | comptes en attente, dernière relance |

## 22.4 Exports et documents

Formats : **CSV**, **Excel `.xlsx`**, **PDF**, plus l'impression
navigateur. Les exports CSV sont en UTF-8 avec BOM, séparateur
point-virgule, et neutralisent les injections de formule.

Les documents officiels portent le logo de l'ESIC, le nom du rapport, la
période, la date de génération, l'auteur ou le système émetteur, un
**identifiant de document** et la mention de document électronique.

Une **attestation d'assiduité** peut être générée pour un apprenant sur
une période : elle porte un identifiant vérifiable et n'est produite que
par un acteur autorisé.

## 22.5 Autorisation de téléchargement étudiant

```text
student_report_download_enabled = true | false
```

Lorsque la valeur est `false`, l'action est désactivée avec une
explication, et l'apprenant peut demander l'autorisation.

## 22.6 Tableaux de bord

| Rôle | Contenu |
|---|---|
| Responsable pédagogique | taux par formation et par classe, évolution, retards, absences non justifiées, comptes non activés, invitations échouées, réclamations ouvertes, séances sans formateur, conflits récents, changements récents |
| Administration | taux global, comparaison des formations, volume et délai de traitement des justificatifs, comptes suspendus, anomalies, exports récents, dernières opérations d'audit |
| Formateur | séances du jour, participants attendus, présents, absents, retardataires, apprenants non inscrits, demandes en attente, remplacements actifs |
| Apprenant | prochain cours, prochaine action d'émargement, taux d'assiduité, absences, justificatifs, réclamations, notifications |

Tout graphique dispose d'un titre, d'une légende, de valeurs
accessibles, d'un **tableau équivalent**, d'une palette contrastée, et
ne dépend jamais uniquement de la couleur.

## 22.7 Recherche globale

Recherche unique sur apprenants, formateurs, classes, formations,
salles et séances, restreinte au périmètre de l'utilisateur, avec accès
direct à la fiche trouvée.

---

# 23. Audit

## 23.1 Opérations auditées

Connexion réussie et échouée ; déconnexion ; activation ; récupération
de compte ; changement de mot de passe ; ajout ou suppression d'un
facteur ou d'une passkey ; création d'utilisateur ; changement de rôle
ou de statut ; import d'apprenants ; import de planning ; confirmation
d'import ; publication ; retour à une version antérieure ; modification
de planning ; annulation ; remplacement ; ouverture et clôture de
séance ; correction de présence ; ajout manuel ; décision sur un
justificatif ; transfert de réclamation ; export de données ; génération
d'attestation ; modification des plages réseau ; gestion d'un dispositif
connecté ; suppression d'un doublon ; opération de masse ; toute action
du super administrateur.

## 23.2 Contenu d'un événement

Identifiant, acteur, rôle ou contexte, action, catégorie, ressource,
date et heure, résultat, ancienne et nouvelle valeur si pertinent,
motif, identifiant de corrélation.

## 23.3 Exclusions

Jamais de mot de passe, de secret, de jeton complet, de donnée
biométrique, de contenu sensible inutile, ni d'**adresse IP** dans
l'audit métier.

## 23.4 Garanties

L'écriture d'audit passe par l'**outbox transactionnelle** : elle n'est
jamais perdue par un échec de listener et n'est jamais produite si la
transaction métier est annulée. L'audit est consultable par les rôles
autorisés, filtrable, exportable, et ne peut être modifié ni effacé.

## 23.5 Conservation

Politique à trois niveaux : audit actif, archivage intermédiaire, purge
ou anonymisation. Les durées sont définies dans
`docs/08-securite-rgpd.md`.

---

# 24. Cache et données temporaires

## 24.1 Usages autorisés de Redis

Jetons d'émargement et codes courts ; jetons d'activation et de
réinitialisation ; compteurs de limitation de débit ; données
temporaires de session ; plannings fréquemment consultés ; paramètres de
salle ; droits calculés ; compteurs ; résultats de tableaux de bord
coûteux ; listes de révocation.

## 24.2 Usages interdits

Redis n'est jamais la source de vérité des présences définitives, des
utilisateurs, des inscriptions, des décisions de justificatif ni de
l'audit.

## 24.3 Clés

Préfixées, versionnées si nécessaire, **contextualisées par périmètre
d'autorisation**, jamais exposées au client.

```text
attendance:token:{sessionId}:{checkpoint}
attendance:shortcode:{sessionId}:{checkpoint}
rate-limit:login:{identityHash}
schedule:class:{classId}:{version}
permissions:user:{userId}:{context}
dashboard:{role}:{scopeHash}
```

## 24.4 Invalidation

Après modification ou publication d'un planning, changement de rôle,
changement de classe, remplacement, annulation, ou changement de
paramètre.

## 24.5 Indisponibilité

Si Redis est indisponible, les fonctions qui en dépendent renvoient une
erreur explicite (`503`) : **aucune validation dégradée** n'est acceptée.

---

# 25. Messagerie et effets de bord

## 25.1 Flux

```text
Action métier (transaction)
    ↓ écriture dans l'outbox, même transaction
Commit
    ↓
Diffuseur (worker)
    ↓
Fournisseur (SMTP, push, MQTT, audit)
    ↓
Retour de statut
    ↓
Mise à jour de la traçabilité
```

## 25.2 Reprise

Plusieurs tentatives, attente croissante, statut d'erreur, passage en
**file d'échec** après épuisement, relance manuelle possible depuis
l'interface d'administration.

Données de suivi : destinataire, type, nombre de tentatives, dernière
erreur, prochaine tentative, statut, dates de création et de traitement.

## 25.3 Environnements

En local, un serveur SMTP de développement (Mailpit) reçoit les
messages. En production, un fournisseur réel remonte les statuts de
délivrabilité.

---

# 26. Intelligence artificielle

## 26.1 Service

Service Python / FastAPI isolé, appelé par l'application via un port
dédié. Il ne possède aucune donnée : il reçoit ce qui lui est transmis,
**pseudonymisé**, et répond.

## 26.2 Assistance à l'importation

Entrées : noms de feuilles, en-têtes, contenu des cellules, exemples de
lignes, référentiels internes, règles de format.

Sorties : proposition de correspondance, valeur normalisée, score de
confiance, liste d'erreurs, suggestion de correction, résumé lisible.

Capacités attendues : détecter la ligne d'en-tête ; reconnaître les
synonymes ; séparer un cours et un formateur présents dans une même
cellule ; identifier un jour ; reconnaître matin et après-midi ;
normaliser les horaires et les dates ; proposer une matière et un
formateur existants ; signaler un résultat incertain.

Statuts de proposition : `CONFIDENT`, `TO_REVIEW`, `UNRESOLVED`.

Mise en œuvre : dictionnaire de synonymes, règles, expressions
régulières, mesures de similarité et correspondance approximative, puis
modèle statistique local si le gain est démontré.

## 26.3 Détection d'anomalies

Sortie : score de 0 à 1, niveau `LOW` / `MEDIUM` / `HIGH`, liste de
raisons, recommandation de vérification humaine.

Facteurs : jeton expiré, tentatives répétées, validations simultanées,
appareil associé à plusieurs comptes, comportement inhabituel d'un
dispositif, horaire anormal, durée de présence incohérente, séquence de
points de contrôle incohérente.

## 26.4 Prévention du décrochage

Repérage des absences répétées, de l'évolution du taux d'assiduité, des
retards fréquents, de la participation partielle et d'une rupture
soudaine par rapport aux habitudes. Le résultat est une **information**
adressée au responsable pédagogique.

## 26.5 Limites impératives

L'IA ne prononce aucune sanction, ne supprime aucune présence, ne refuse
aucun justificatif, ne publie aucun planning, ne transmet aucune donnée
à un acteur non autorisé, et n'utilise aucune donnée réelle dans un
service non approuvé.

## 26.6 Traçabilité

Sont conservés : version du mécanisme, entrée ou référence de l'entrée,
proposition, score, décision humaine, correction. Chaque proposition
peut être acceptée, corrigée ou rejetée.

---

# 27. Objets connectés

## 27.1 Dispositif

Une borne repose sur une Raspberry Pi 4. Elle possède un identifiant
unique, s'authentifie, publie un signal de vie, transmet de la
télémétrie, lit ou simule un badge, envoie un événement d'émargement,
reçoit un accusé de réception, met ses événements en file locale en cas
de coupure et rejoue à la reconnexion.

## 27.2 Topics MQTT

```text
esic/devices/{deviceId}/heartbeat
esic/devices/{deviceId}/attendance
esic/devices/{deviceId}/status
esic/devices/{deviceId}/commands
```

## 27.3 Message

```json
{
  "eventId": "uuid",
  "deviceId": "room-a-terminal-01",
  "eventType": "ATTENDANCE_SCAN",
  "sessionId": "uuid",
  "checkpoint": "MORNING_ARRIVAL",
  "subjectReference": "pseudonymous-reference",
  "occurredAt": "2026-08-27T09:01:00Z",
  "sequence": 42,
  "signature": "..."
}
```

## 27.4 Sécurité

Identité de dispositif, authentification, TLS, secret hors du code,
liste de dispositifs autorisés, révocation, protection contre le rejeu
par identifiant unique d'événement et numéro de séquence, journalisation
des événements rejetés, stockage local minimal, file locale et reprise
contrôlée.

Un événement provenant d'un dispositif inconnu ou révoqué est **rejeté**
et journalisé comme incident de sécurité.

## 27.5 Simulateur

Un simulateur logiciel reproduit le protocole complet : identité, signal
de vie, émargement, coupure réseau, file locale, rejeu, doublon
d'événement. Il permet de développer, tester et démontrer la chaîne sans
matériel.

## 27.6 NFC

Un lecteur NFC USB ou GPIO peut être ajouté ultérieurement au dispositif
sans modification du protocole : l'identifiant de badge est
**pseudonymisé** avant transmission.

---

# 28. Intégrations externes

## 28.1 Principe

Toute intégration est encapsulée derrière un **port**. Le produit
fonctionne intégralement sans elle, avec un adaptateur local. L'activation
se fait par configuration, sans modification de code.

## 28.2 Microsoft Graph et Teams

- lecture de l'annuaire pour rapprocher un compte formateur ;
- création automatique d'une réunion Teams pour une séance distancielle
  ou hybride, et publication du lien dans la séance ;
- écriture des séances dans le calendrier du formateur et, en option,
  des apprenants ;
- mise à jour et suppression lors d'une modification ou d'une annulation.

## 28.3 Calendriers

Un **flux iCalendar** signé, propre à chaque utilisateur et révocable,
permet l'abonnement depuis Outlook, Google Calendar, Apple Calendar ou
tout agenda compatible. Le flux ne contient aucune donnée sensible
au-delà du planning de la personne.

## 28.4 Messagerie

Adaptateur SMTP : Mailpit en local, fournisseur réel en production, avec
remontée des statuts de délivrabilité lorsque le fournisseur les expose.

## 28.5 Anti-robot

Adaptateur Cloudflare Turnstile, avec validation serveur et politique de
repli définie en cas d'indisponibilité.

---

# 29. Application mobile et hors ligne

## 29.1 PWA

L'application est **installable** sur Android, iOS et poste de travail :
manifeste, icônes, écran de démarrage, mode autonome.

## 29.2 Hors ligne

La coquille applicative est mise en cache. Sont consultables sans
réseau : le planning récent, la prochaine séance, l'historique
d'assiduité récent et les notifications déjà reçues.

Une action réalisée hors ligne est **mise en file** et rejouée à la
reconnexion, avec résolution de conflit. Une présence enregistrée hors
ligne n'est **jamais définitive** avant validation par le serveur :
l'interface indique clairement l'état « en attente de confirmation ».

## 29.3 Notifications push

Abonnement par appareil, révocable, avec préférences par catégorie. Le
contenu poussé ne comporte aucune donnée sensible : il renvoie vers
l'application.

## 29.4 Accessibilité mobile

Toute fonction reposant sur la caméra dispose d'une alternative (code
court). Toute fonction reposant sur WebAuthn dispose d'une alternative.
Les cibles tactiles respectent les tailles minimales recommandées.

---

# 30. API REST

## 30.1 Principes

Préfixe `/api`, versionnement, JSON, validation systématique, codes HTTP
cohérents, pagination, filtres, tri borné, erreurs structurées,
identifiant de corrélation, documentation OpenAPI publiée et versionnée
dans le dépôt.

Une erreur d'appel du client produit un `400` explicite, jamais un
`500`.

## 30.2 Routes principales

```text
POST   /api/v1/auth/login
POST   /api/v1/auth/logout
POST   /api/v1/auth/refresh
GET    /api/v1/auth/me
POST   /api/v1/auth/forgot-password
POST   /api/v1/auth/reset-password
POST   /api/v1/auth/mfa/enroll
POST   /api/v1/auth/mfa/verify
POST   /api/v1/auth/webauthn/register/options
POST   /api/v1/auth/webauthn/register
POST   /api/v1/auth/webauthn/login/options
POST   /api/v1/auth/webauthn/login
GET    /api/v1/auth/devices
DELETE /api/v1/auth/devices/{id}

GET    /api/v1/users
POST   /api/v1/users
PATCH  /api/v1/users/{id}
POST   /api/v1/users/{id}/suspend
POST   /api/v1/users/{id}/restore
POST   /api/v1/users/bulk

GET    /api/v1/programs
POST   /api/v1/programs
GET    /api/v1/classes
POST   /api/v1/classes
GET    /api/v1/subjects
GET    /api/v1/rooms
GET    /api/v1/alternation-patterns

POST   /api/v1/student-imports/simulate
POST   /api/v1/student-imports/{id}/confirm
GET    /api/v1/student-imports/{id}

POST   /api/v1/planning-imports
POST   /api/v1/planning-imports/{id}/rows/{rowId}
POST   /api/v1/planning-imports/{id}/publish
GET    /api/v1/planning/versions
POST   /api/v1/planning/versions/{id}/rollback
GET    /api/v1/planning/calendar
POST   /api/v1/planning/slots

GET    /api/v1/sessions
POST   /api/v1/sessions
POST   /api/v1/sessions/{id}/open
POST   /api/v1/sessions/{id}/close
POST   /api/v1/sessions/{id}/cancel
POST   /api/v1/sessions/{id}/substitute

GET    /api/v1/sessions/{id}/attendance-token
POST   /api/v1/attendance/validate
POST   /api/v1/attendance/manual
PATCH  /api/v1/attendance/{id}
POST   /api/v1/attendance/early-departure

POST   /api/v1/justifications
POST   /api/v1/justifications/{id}/attachments
PATCH  /api/v1/justifications/{id}/decision

POST   /api/v1/claims
POST   /api/v1/claims/{id}/messages
POST   /api/v1/claims/{id}/transfer
POST   /api/v1/claims/{id}/reopen

GET    /api/v1/me/dashboard
GET    /api/v1/me/notifications
GET    /api/v1/me/calendar.ics
GET    /api/v1/me/attendance

GET    /api/v1/reports/class
GET    /api/v1/reports/student
GET    /api/v1/reports/export
POST   /api/v1/reports/attestation

GET    /api/v1/audit-events
GET    /api/v1/devices
POST   /api/v1/devices/{id}/revoke
GET    /api/v1/anomalies
POST   /api/v1/anomalies/{id}/review
```

---

# 31. Modèle de données

Entités principales : `User`, `Role`, `UserRole`, `TrustedDevice`,
`WebAuthnCredential`, `MfaSecret`, `RecoveryCode`, `Program`, `Level`,
`AcademicYear`, `Promotion`, `ClassGroup`, `StudentGroup`,
`StudentProfile`, `TeacherProfile`, `Enrollment`,
`PedagogicalAssignment`, `WorkStudyPattern`, `WorkStudyException`,
`Subject`, `Site`, `Building`, `Room`, `NetworkRange`, `Schedule`,
`ScheduleVersion`, `ScheduleImport`, `ScheduleImportRow`,
`StudentImport`, `StudentImportRow`, `CourseSession`, `SessionClass`,
`Substitution`, `CancellationRequest`, `AttendanceCheckpoint`,
`AttendanceRecord`, `AttendanceCorrection`, `EarlyDeparture`,
`Justification`, `JustificationAttachment`, `Claim`, `ClaimMessage`,
`Notification`, `NotificationPreference`, `PushSubscription`,
`OutboxMessage`, `EmailDelivery`, `AuditEvent`, `IoTDevice`, `IoTEvent`,
`AnomalyAlert`, `AiSuggestion`, `CalendarSubscription`,
`ReportDocument`.

Principes : identifiants exposés sous forme d'UUID ; contraintes
d'unicité et clés étrangères systématiques ; suppression logique ;
horodatage et auteur des modifications ; verrouillage optimiste sur les
entités concurrentes ; aucune entité partagée entre modules.

Le détail figure dans `docs/04-modele-donnees.md`.

---

# 32. Exigences non fonctionnelles

## 32.1 Performance

| Réf | Exigence |
|---|---|
| NFR-PERF-01 | lecture en cache d'un planning < 100 ms |
| NFR-PERF-02 | génération d'un jeton d'émargement < 100 ms |
| NFR-PERF-03 | validation d'un émargement < 300 ms |
| NFR-PERF-04 | simulation d'un import de 500 apprenants < 10 s |
| NFR-PERF-05 | rapport mensuel de classe < 2 s |
| NFR-PERF-06 | chargement initial de l'application < 2,5 s en 4G |
| NFR-PERF-07 | 200 émargements par minute soutenus sans erreur |
| NFR-PERF-08 | coût SQL borné : aucune requête proportionnelle au nombre d'éléments affichés |
| NFR-PERF-09 | courriels et notifications toujours asynchrones |

## 32.2 Disponibilité et exploitation

Sondes de santé et de disponibilité ; redémarrage reproductible ;
sauvegarde planifiée ; **restauration testée et documentée** ; procédure
d'incident ; environnement de recette.

## 32.3 Sécurité

En-têtes durcis (`nosniff`, `X-Frame-Options: DENY`, CSP,
`Referrer-Policy`, anti-cache sur les réponses sensibles) ; CORS
restrictif piloté par configuration, jamais `*` ; secrets hors du
dépôt ; dépendances surveillées et mises à jour ; analyse statique de
sécurité en intégration continue.

## 32.4 Maintenabilité

Architecture modulaire vérifiée automatiquement ; conventions
homogènes ; migrations ; tests ; documentation à jour ; journal des
décisions d'architecture ; commentaires expliquant les règles métier
complexes, les décisions de sécurité et les algorithmes non évidents,
jamais paraphrasant le code.

## 32.5 Accessibilité

Conformité **WCAG 2.1 niveau AA** visée : navigation clavier complète,
lien d'évitement, libellés, contrastes, messages d'erreur explicites,
tableau alternatif à tout graphique, alternative à la caméra,
alternative à la biométrie, compatibilité avec les technologies
d'assistance. Vérification outillée et manuelle.

## 32.6 Compatibilité

Navigateurs modernes ; Android ; iOS via navigateur ; ordinateurs,
tablettes, smartphones ; conception adaptative.

## 32.7 Internationalisation

Interface en français. Tous les textes sont externalisés afin qu'une
seconde langue puisse être ajoutée sans refonte.

---

# 33. Données et conservation

| Catégorie | Durée initiale | Remarque |
|---|---|---|
| Présences et assiduité | 5 années scolaires | à valider avec la direction et le référent RGPD |
| Justificatifs | 12 mois | métadonnées conservables plus longtemps si justifié |
| Réclamations | 3 ans | historique complet |
| Audit métier | 3 ans actifs, puis archivage | politique à trois niveaux |
| Journaux techniques | 30 jours | accès restreint |
| Comptes archivés | jusqu'à anonymisation décidée | historique préservé |
| Jetons et données Redis | durée de vie propre à chaque usage | jamais au-delà du besoin |

Une tâche de purge identifie les données échues, produit une
prévisualisation, supprime ou anonymise, conserve une preuve de purge,
et ne détruit jamais une donnée sous litige.

Droits des personnes outillés : accès, rectification, limitation,
opposition lorsque applicable, effacement lorsque applicable, export
dans un format lisible.

Le détail figure dans `docs/08-securite-rgpd.md`.

---

# 34. Interface utilisateur

## 34.1 Principes

Angular Material ; conception adaptative ; cohérence des parcours ;
actions primaires visibles ; confirmation des actions risquées ; états
de chargement ; erreurs contextualisées ; formulaires validés ; aide
concise ; aucune information sensible affichée sans nécessité.

## 34.2 Écrans

**Communs** : connexion, connexion par passkey, second facteur,
activation, mot de passe oublié, réinitialisation, profil, sécurité du
compte et appareils, choix du contexte de rôle, notifications,
préférences, recherche globale.

**Responsable pédagogique** : tableau de bord, formations, promotions,
classes, groupes, matières, apprenants, import d'apprenants,
invitations, calendrier de planning, import de planning, revue et
correction, versions, séances, remplacements, justificatifs,
réclamations, rapports, anomalies.

**Formateur** : séances du jour, calendrier, ouverture de séance,
affichage QR et code, présences en direct, ajout manuel, apprenant
provisoire, départ anticipé, clôture, demandes, réclamations.

**Apprenant** : prochain cours, planning, abonnement calendrier, écran
d'émargement, historique, journal de transparence, justificatifs,
réclamations, rapport, préférences, sécurité du compte.

**Administration** : recherche globale, rapports, attestations,
justificatifs, comptes, invitations, anomalies, exports.

**Super administrateur** : dispositifs connectés, plages réseau,
politiques de sécurité, journaux de sécurité, intégrations, sessions,
file d'échec des effets de bord, maintenance.

## 34.3 Messages d'erreur

Compréhensibles, indiquant l'action possible, sans information
sensible, avec identifiant de corrélation lorsque c'est utile.

```text
Le fichier ne contient pas la colonne obligatoire « email ».
Corrigez le fichier, puis relancez la simulation.

Cette séance n'est pas encore ouverte.

Le code d'émargement a expiré. Demandez au formateur d'afficher
un nouveau code.

Votre présence a déjà été enregistrée pour ce point de contrôle.

Vous n'êtes pas autorisé à consulter cette formation.
```

---

# 35. Règles de gestion consolidées

## Identité et accès

- **RG-001** — une adresse électronique correspond à un seul utilisateur.
- **RG-002** — un utilisateur peut posséder plusieurs rôles.
- **RG-003** — le compte super administrateur est distinct du compte quotidien.
- **RG-004** — un compte archivé ou suspendu ne peut pas se connecter.
- **RG-005** — une invitation expire après un mois.
- **RG-006** — l'historique n'est jamais supprimé lors d'un changement de classe.
- **RG-007** — le second facteur est obligatoire pour `SUPER_ADMIN` et `ADMIN`.
- **RG-008** — une passkey est liée à un appareil et révocable individuellement.
- **RG-009** — une réauthentification est exigée avant toute action critique.
- **RG-010** — la réponse d'authentification est uniforme quel que soit le motif d'échec.

## Pédagogie

- **RG-020** — une formation possède un responsable pédagogique principal unique.
- **RG-021** — un responsable peut gérer plusieurs formations.
- **RG-022** — un apprenant appartient à une seule classe principale active.
- **RG-023** — une séance peut concerner plusieurs classes ou un groupe.
- **RG-024** — une séance possède un formateur principal.
- **RG-025** — une séance peut posséder un remplaçant autorisé et daté.
- **RG-026** — une séance normale provient d'un planning publié.
- **RG-027** — une séance exceptionnelle exige un motif.
- **RG-028** — une période en entreprise n'est jamais comptée comme une absence.

## Import

- **RG-030** — un import est simulé avant d'être appliqué.
- **RG-031** — une anomalie bloquante empêche la confirmation.
- **RG-032** — un utilisateur existant est mis à jour, jamais dupliqué.
- **RG-033** — un changement de classe conserve l'historique.
- **RG-034** — une opération groupée exige une confirmation explicite.
- **RG-035** — une suggestion de l'IA reste soumise à confirmation humaine.
- **RG-036** — le fichier téléversé n'est jamais écrit sur disque.
- **RG-037** — l'application d'un import est atomique : tout ou rien.

## Planning

- **RG-040** — le responsable pédagogique publie son planning ; le formateur ne le publie pas.
- **RG-041** — au minimum trois versions sont conservées.
- **RG-042** — un conflit bloquant interdit la publication.
- **RG-043** — une modification publiée génère une notification.
- **RG-044** — une salle peut être affectée après l'import.
- **RG-045** — la publication est atomique et idempotente.
- **RG-046** — un retour à une version antérieure crée une nouvelle version, sans effacer l'historique.
- **RG-047** — l'identité d'un créneau est stable entre deux publications.

## Émargement

- **RG-050** — le QR fixe est lié à une salle, pas à une séance.
- **RG-051** — le QR fixe est utilisable jusqu'au début de la séance.
- **RG-052** — le QR fixe exige une connexion depuis une plage réseau autorisée.
- **RG-053** — le QR dynamique change périodiquement.
- **RG-054** — un jeton est limité dans le temps et à usage unique.
- **RG-055** — une validation est unique par apprenant et par point de contrôle.
- **RG-056** — quatre points de contrôle sont possibles par journée.
- **RG-057** — deux contrôles cohérents valident une demi-journée.
- **RG-058** — quatre contrôles cohérents valident une journée.
- **RG-059** — une validation incomplète produit `PARTIAL` ou `TO_CONFIRM`.
- **RG-060** — une correction manuelle exige un motif.
- **RG-061** — une présence exceptionnelle est auditée.
- **RG-062** — un apprenant non inscrit est enregistré provisoirement et régularisé.
- **RG-063** — une présence enregistrée hors ligne n'est jamais définitive sans validation serveur.
- **RG-064** — un événement de borne déjà traité est ignoré.

## Retards

- **RG-070** — jusqu'à 15 minutes, l'apprenant est `PRESENT`.
- **RG-071** — de 16 à 30 minutes, il est `LATE`.
- **RG-072** — au-delà de 30 minutes, une validation manuelle est requise.
- **RG-073** — un cas exceptionnel peut être accepté par le formateur, avec motif.

## Justificatifs et réclamations

- **RG-080** — un justificatif porte sur une séance, une demi-journée, une journée ou une période.
- **RG-081** — la taille maximale d'une pièce jointe est de 5 Mo.
- **RG-082** — les formats acceptés sont JPEG, PNG et PDF, contrôlés par leur contenu réel.
- **RG-083** — une pièce jointe est analysée avant mise à disposition.
- **RG-084** — un refus exige un motif.
- **RG-085** — le délai de dépôt initial est d'un mois.
- **RG-086** — un justificatif accepté produit `EXCUSED`.
- **RG-087** — un justificatif n'efface jamais l'historique de l'absence.
- **RG-088** — une réclamation conserve son historique complet, y compris après réouverture.

## Sécurité et données

- **RG-090** — aucune donnée personnelle n'est placée dans un QR code.
- **RG-091** — aucune donnée biométrique brute n'est reçue ni conservée.
- **RG-092** — après trois échecs, les contrôles sont renforcés.
- **RG-093** — aucun jeton sensible n'est stocké dans `localStorage`.
- **RG-094** — l'adresse IP n'est pas conservée dans l'audit métier.
- **RG-095** — le cache ne contourne jamais une autorisation.
- **RG-096** — tout effet de bord externe passe par l'outbox transactionnelle.
- **RG-097** — une transaction annulée ne produit aucun effet de bord.
- **RG-098** — les données transmises à l'IA sont pseudonymisées.
- **RG-099** — la démonstration utilise exclusivement des données fictives.

---

# 36. Exigences fonctionnelles

Priorités : `MUST` obligatoire pour la version 1.0 ; `SHOULD` important ;
`COULD` souhaitable. **Toutes sont à réaliser dans les six mois.** La
colonne « Sprint » renvoie à `docs/06-roadmap-six-mois.md`.

## 36.1 Identité et accès

| ID | Exigence | Priorité | Sprint |
|---|---|---|---|
| EF-AUTH-001 | Se connecter par email et mot de passe | MUST | 1 |
| EF-AUTH-002 | Gérer plusieurs rôles par utilisateur | MUST | 1 |
| EF-AUTH-003 | Choisir un contexte de rôle vérifié côté serveur | MUST | 1 |
| EF-AUTH-004 | Activer un compte par invitation | MUST | 1 |
| EF-AUTH-005 | Réinitialiser un mot de passe oublié | MUST | 2 |
| EF-AUTH-006 | Enregistrer et gérer une passkey WebAuthn | MUST | 2 |
| EF-AUTH-007 | Se connecter par passkey sans mot de passe | MUST | 2 |
| EF-AUTH-008 | Activer un second facteur TOTP | MUST | 2 |
| EF-AUTH-009 | Générer et consommer des codes de récupération | MUST | 2 |
| EF-AUTH-010 | Appliquer une authentification adaptative selon le risque | SHOULD | 2 |
| EF-AUTH-011 | Protéger les formulaires publics par anti-robot | MUST | 2 |
| EF-AUTH-012 | Limiter les tentatives sur les routes sensibles | MUST | 2 |
| EF-AUTH-013 | Gérer et révoquer les appareils de confiance | SHOULD | 2 |
| EF-AUTH-014 | Se déconnecter et révoquer une session | MUST | 2 |
| EF-AUTH-015 | Exiger une réauthentification avant une action critique | SHOULD | 2 |

## 36.2 Utilisateurs

| ID | Exigence | Priorité | Sprint |
|---|---|---|---|
| EF-USER-001 | Créer un utilisateur en attente d'activation | MUST | 1 |
| EF-USER-002 | Suspendre et réactiver un utilisateur | MUST | 1 |
| EF-USER-003 | Archiver et restaurer un utilisateur | MUST | 1 |
| EF-USER-004 | Réaliser une opération de masse avec prévisualisation | SHOULD | 4 |
| EF-USER-005 | Détecter et traiter les doublons | MUST | 4 |
| EF-USER-006 | Attribuer et retirer un rôle avec gardes fines | MUST | 1 |
| EF-USER-007 | Émettre, suivre et réémettre une invitation depuis l'interface | MUST | 3 |
| EF-USER-008 | Suivre la délivrabilité des courriels d'invitation | SHOULD | 3 |
| EF-USER-009 | Rechercher globalement dans son périmètre | SHOULD | 11 |

## 36.3 Référentiels

| ID | Exigence | Priorité | Sprint |
|---|---|---|---|
| EF-ACA-001 | Gérer les formations | MUST | 1 |
| EF-ACA-002 | Gérer les niveaux | MUST | 1 |
| EF-ACA-003 | Gérer les promotions | MUST | 1 |
| EF-ACA-004 | Gérer les classes | MUST | 1 |
| EF-ACA-005 | Gérer les années scolaires | MUST | 1 |
| EF-ACA-006 | Gérer les matières | MUST | 3 |
| EF-ACA-007 | Gérer les groupes temporaires | SHOULD | 3 |
| EF-ACA-008 | Affecter un responsable pédagogique à des formations | MUST | 1 |
| EF-ACA-009 | Gérer les rythmes d'alternance et leurs exceptions | MUST | 4 |
| EF-ORG-001 | Gérer sites, bâtiments et salles | MUST | 1 |
| EF-ORG-002 | Gérer les plages réseau autorisées | MUST | 1 |
| EF-ORG-003 | Gérer un QR fixe par salle | MUST | 8 |
| EF-ORG-004 | Détecter les conflits et incohérences de salle | MUST | 6 |

## 36.4 Inscriptions et imports

| ID | Exigence | Priorité | Sprint |
|---|---|---|---|
| EF-ENR-001 | Gérer les profils apprenants | MUST | 3 |
| EF-ENR-002 | Inscrire un apprenant dans une classe | MUST | 3 |
| EF-ENR-003 | Changer un apprenant de classe en conservant l'historique | MUST | 3 |
| EF-ENR-004 | Autoriser un suivi à distance individuel | MUST | 7 |
| EF-IMP-001 | Simuler un import d'apprenants CSV | MUST | 3 |
| EF-IMP-002 | Confirmer un import d'apprenants de façon atomique | MUST | 3 |
| EF-IMP-003 | Importer un classeur Excel `.xlsx` | MUST | 4 |
| EF-IMP-004 | Gérer un classeur multifeuille | SHOULD | 4 |
| EF-IMP-005 | Proposer un mapping de colonnes assisté par l'IA | SHOULD | 12 |
| EF-IMP-006 | Corriger une ligne en anomalie avant confirmation | SHOULD | 4 |

## 36.5 Corps enseignant

| ID | Exigence | Priorité | Sprint |
|---|---|---|---|
| EF-TEA-001 | Créer un formateur externe sans adresse institutionnelle | MUST | 3 |
| EF-TEA-002 | Affecter un formateur à une classe, une matière et une période | MUST | 3 |
| EF-TEA-003 | Désigner un remplaçant sur une séance ou une période | MUST | 6 |
| EF-TEA-004 | Proposer un remplaçant sans pouvoir le valider soi-même | MUST | 6 |
| EF-TEA-005 | Notifier formateur initial et remplaçant | MUST | 6 |

## 36.6 Planning

| ID | Exigence | Priorité | Sprint |
|---|---|---|---|
| EF-PLAN-001 | Importer un planning CSV | MUST | 5 |
| EF-PLAN-002 | Prévisualiser le planning importé sans créer de séance | MUST | 5 |
| EF-PLAN-003 | Corriger les lignes en anomalie dans l'écran de revue | MUST | 5 |
| EF-PLAN-004 | Publier le planning de façon atomique | MUST | 5 |
| EF-PLAN-005 | Versionner le planning | MUST | 5 |
| EF-PLAN-006 | Construire un planning dans un calendrier interactif | MUST | 6 |
| EF-PLAN-007 | Conserver au moins trois versions | MUST | 5 |
| EF-PLAN-008 | Revenir à une version antérieure | MUST | 6 |
| EF-PLAN-009 | Détecter les conflits formateur, classe, salle et horaire | MUST | 5 |
| EF-PLAN-010 | Avertir d'un créneau tombant en période d'entreprise | SHOULD | 6 |
| EF-PLAN-011 | Importer un planning Excel `.xlsx` | SHOULD | 6 |
| EF-PLAN-012 | Importer un planning PDF texte structuré | COULD | 12 |
| EF-PLAN-013 | Proposer un mapping de planning assisté par l'IA | SHOULD | 12 |

## 36.7 Séances

| ID | Exigence | Priorité | Sprint |
|---|---|---|---|
| EF-SES-001 | Créer les séances depuis un planning publié | MUST | 5 |
| EF-SES-002 | Ouvrir une séance | MUST | 7 |
| EF-SES-003 | Clôturer une séance | MUST | 7 |
| EF-SES-004 | Annuler une séance avec motif | MUST | 6 |
| EF-SES-005 | Affecter un remplaçant à une séance | MUST | 6 |
| EF-SES-006 | Créer une séance exceptionnelle | MUST | 6 |
| EF-SES-007 | Reporter une séance annulée | SHOULD | 6 |
| EF-SES-008 | Gérer une demande d'annulation par le formateur | SHOULD | 6 |
| EF-SES-009 | Rattacher plusieurs classes ou un groupe à une séance | SHOULD | 6 |

## 36.8 Émargement et assiduité

| ID | Exigence | Priorité | Sprint |
|---|---|---|---|
| EF-ATT-001 | Générer un QR dynamique rotatif | MUST | 7 |
| EF-ATT-002 | Valider une présence par jeton | MUST | 7 |
| EF-ATT-003 | Gérer les quatre points de contrôle nommés | MUST | 8 |
| EF-ATT-004 | Calculer demi-journées et journées | MUST | 8 |
| EF-ATT-005 | Appliquer les paliers de retard 15 et 30 minutes | MUST | 8 |
| EF-ATT-006 | Saisir manuellement une présence avec motif | MUST | 7 |
| EF-ATT-007 | Ajouter un apprenant provisoire | SHOULD | 8 |
| EF-ATT-008 | Contrôler la plage réseau pour le QR fixe | MUST | 8 |
| EF-ATT-009 | Émarger par code court | MUST | 7 |
| EF-ATT-010 | Émarger par QR fixe de salle | MUST | 8 |
| EF-ATT-011 | Confirmer localement un émargement par WebAuthn | SHOULD | 8 |
| EF-ATT-012 | Corriger une présence avec motif et historique | MUST | 7 |
| EF-ATT-013 | Enregistrer un départ anticipé | SHOULD | 9 |
| EF-ATT-014 | Consulter le journal de transparence de ses présences | SHOULD | 9 |
| EF-ATT-015 | Suivre les présences en direct | MUST | 7 |
| EF-ATT-016 | Émarger depuis une borne connectée | SHOULD | 12 |

## 36.9 Justificatifs et réclamations

| ID | Exigence | Priorité | Sprint |
|---|---|---|---|
| EF-JUS-001 | Déposer un justificatif | MUST | 9 |
| EF-JUS-002 | Joindre une pièce contrôlée et analysée | MUST | 9 |
| EF-JUS-003 | Examiner et décider avec motif | MUST | 9 |
| EF-JUS-004 | Transformer `ABSENT` en `EXCUSED` après acceptation | MUST | 9 |
| EF-CLAIM-001 | Créer une réclamation | MUST | 9 |
| EF-CLAIM-002 | Échanger sous forme conversationnelle | MUST | 9 |
| EF-CLAIM-003 | Transférer une réclamation avec motif | SHOULD | 9 |
| EF-CLAIM-004 | Rouvrir une réclamation clôturée | SHOULD | 9 |

## 36.10 Notifications et mobilité

| ID | Exigence | Priorité | Sprint |
|---|---|---|---|
| EF-NOTIF-001 | Afficher un centre de notifications persistant | MUST | 10 |
| EF-NOTIF-002 | Notifier les modifications de planning et de séance | MUST | 10 |
| EF-NOTIF-003 | Notifier apprenants, formateurs et responsables selon l'audience | MUST | 10 |
| EF-NOTIF-004 | Envoyer les notifications par courriel | MUST | 10 |
| EF-NOTIF-005 | Envoyer des notifications push PWA | SHOULD | 10 |
| EF-NOTIF-006 | Régler ses préférences de notification | SHOULD | 10 |
| EF-PWA-001 | Rendre l'application installable | MUST | 10 |
| EF-PWA-002 | Consulter planning et assiduité hors ligne | SHOULD | 10 |
| EF-PWA-003 | Mettre une action en file hors ligne et la rejouer | SHOULD | 10 |

## 36.11 Restitution

| ID | Exigence | Priorité | Sprint |
|---|---|---|---|
| EF-REP-001 | Produire un rapport de classe | MUST | 11 |
| EF-REP-002 | Produire un rapport individuel | MUST | 11 |
| EF-REP-003 | Exporter en CSV | MUST | 11 |
| EF-REP-004 | Exporter en Excel | MUST | 11 |
| EF-REP-005 | Exporter en PDF avec identité visuelle | MUST | 11 |
| EF-REP-006 | Générer une attestation d'assiduité identifiable | SHOULD | 11 |
| EF-REP-007 | Produire les tableaux de bord des quatre profils | MUST | 11 |
| EF-REP-008 | Fournir un tableau équivalent à chaque graphique | MUST | 11 |
| EF-REP-009 | Produire le rapport des anomalies | SHOULD | 12 |
| EF-REP-010 | Produire le rapport des invitations non activées | SHOULD | 11 |

## 36.12 Intelligence artificielle et objets connectés

| ID | Exigence | Priorité | Sprint |
|---|---|---|---|
| EF-AI-001 | Suggérer une correspondance de colonnes | SHOULD | 12 |
| EF-AI-002 | Produire un score de confiance exploitable | SHOULD | 12 |
| EF-AI-003 | Détecter une anomalie d'émargement | SHOULD | 12 |
| EF-AI-004 | Signaler un risque de décrochage | COULD | 12 |
| EF-AI-005 | Soumettre toute proposition à validation humaine | MUST | 12 |
| EF-IOT-001 | Recevoir un événement d'émargement MQTT | SHOULD | 12 |
| EF-IOT-002 | Gérer l'identité et la révocation d'une borne | SHOULD | 12 |
| EF-IOT-003 | Ignorer un événement déjà traité | MUST | 12 |
| EF-IOT-004 | Recevoir la télémétrie et le signal de vie | COULD | 12 |
| EF-IOT-005 | Rejouer une file locale après coupure | SHOULD | 12 |

## 36.13 Intégrations

| ID | Exigence | Priorité | Sprint |
|---|---|---|---|
| EF-INT-001 | Publier un flux iCalendar signé et révocable | SHOULD | 11 |
| EF-INT-002 | Créer une réunion Teams depuis une séance distancielle | SHOULD | 11 |
| EF-INT-003 | Écrire les séances dans un calendrier Microsoft | COULD | 11 |
| EF-INT-004 | Envoyer les courriels via un fournisseur réel | MUST | 13 |

## 36.14 Transverse

| ID | Exigence | Priorité | Sprint |
|---|---|---|---|
| EF-AUD-001 | Auditer les opérations critiques | MUST | 1 |
| EF-AUD-002 | Consulter et exporter la piste d'audit | SHOULD | 11 |
| EF-AUD-003 | Garantir l'audit par outbox transactionnelle | MUST | 10 |
| EF-RGPD-001 | Exporter les données personnelles d'une personne | SHOULD | 13 |
| EF-RGPD-002 | Rectifier et limiter le traitement sur demande | SHOULD | 13 |
| EF-RGPD-003 | Purger ou anonymiser les données échues | MUST | 13 |
| EF-OPS-001 | Exposer santé, métriques et journaux structurés | MUST | 13 |
| EF-OPS-002 | Sauvegarder et restaurer, avec preuve | MUST | 13 |
| EF-OPS-003 | Déployer par intégration et livraison continues | MUST | 13 |
| EF-OPS-004 | Publier la documentation OpenAPI versionnée | MUST | 13 |
| EF-OPS-005 | Rejouer manuellement un effet de bord en échec | SHOULD | 10 |

---

# 37. Critères d'acceptation

| ID | Critère |
|---|---|
| AC-001 | Un utilisateur actif se connecte avec des identifiants valides ; un utilisateur suspendu reçoit un refus sans divulgation. |
| AC-002 | Un responsable pédagogique ne peut pas lire les classes d'une formation hors de son périmètre : l'API renvoie `403` ou `404`. |
| AC-003 | Un responsable également formateur accède aux deux contextes sans perdre les restrictions de périmètre. |
| AC-004 | Un import de 500 apprenants valides produit une simulation chiffrant créations, mises à jour, déplacements, erreurs et avertissements. |
| AC-005 | Un apprenant existant n'est jamais recréé. |
| AC-006 | Après un changement de classe, l'ancienne inscription reste consultable. |
| AC-007 | Un planning valide ne produit des séances qu'après confirmation et publication. |
| AC-008 | La modification d'un planning publié crée une nouvelle version, l'ancienne passant en `SUPERSEDED`. |
| AC-009 | Un retour à une version antérieure crée une version N+1 dont le contenu est celui de la version choisie. |
| AC-010 | Le QR fixe est refusé après le début de la séance, hors plage réseau autorisée, et sans séance correspondante. |
| AC-011 | Le QR dynamique change périodiquement et est refusé après expiration. |
| AC-012 | Une validation réalisée 20 minutes après le début produit `LATE`. |
| AC-013 | Les deux contrôles du matin produisent une demi-journée présente. |
| AC-014 | Les quatre contrôles produisent une journée présente. |
| AC-015 | Une journée en entreprise n'apparaît jamais comme une absence. |
| AC-016 | Un justificatif accepté transforme `ABSENT` en `EXCUSED` sans effacer l'absence. |
| AC-017 | Un apprenant ne consulte jamais la donnée d'un autre apprenant. |
| AC-018 | Une correction affiche l'ancienne valeur, la nouvelle, l'auteur, la date et le motif. |
| AC-019 | Le rapport individuel affiche le calcul en demi-journées. |
| AC-020 | Le serveur ne reçoit aucune donnée biométrique brute lors d'une opération WebAuthn. |
| AC-021 | Un second facteur est exigé pour toute connexion `SUPER_ADMIN` ou `ADMIN`. |
| AC-022 | Après trois échecs de connexion, un contrôle renforcé est déclenché. |
| AC-023 | Une demande de réinitialisation renvoie la même réponse, que l'adresse existe ou non. |
| AC-024 | Un événement MQTT dont l'identifiant a déjà été traité est ignoré. |
| AC-025 | Un événement provenant d'un dispositif inconnu est rejeté et journalisé. |
| AC-026 | Une suggestion de l'IA à faible confiance n'est jamais appliquée sans confirmation. |
| AC-027 | Une transaction métier annulée ne produit ni notification, ni courriel, ni trace d'audit. |
| AC-028 | Un effet de bord en échec est repris automatiquement, puis placé en file d'échec et rejouable. |
| AC-029 | Une réclamation conserve tout son historique après transfert et réouverture. |
| AC-030 | Une pièce jointe dont le contenu réel ne correspond pas au type déclaré est rejetée. |
| AC-031 | Une présence enregistrée hors ligne est signalée « en attente » et n'est définitive qu'après validation serveur. |
| AC-032 | Un export CSV neutralise les injections de formule. |
| AC-033 | Une attestation porte un identifiant de document et l'émetteur. |
| AC-034 | Le flux iCalendar d'un utilisateur ne contient que son propre planning et se révoque. |
| AC-035 | Toute page dispose d'un lien d'évitement et est parcourable entièrement au clavier. |
| AC-036 | Une restauration de sauvegarde est effectuée et documentée avec sa preuve. |

---

# 38. Tests

## 38.1 Unitaires

Calcul des retards, des demi-journées et du résultat journalier ;
contrôle d'inscription ; contrôle de périmètre ; détection des doublons ;
validation de fichier ; expiration et unicité des jetons ; règles
d'alternance ; transformation `ABSENT` → `EXCUSED` ; normalisation
d'import ; résolution des conflits de planning.

## 38.2 Intégration

Authentification et MFA ; migrations et validation du schéma ; MySQL ;
Redis ; imports ; publication ; émargement ; outbox ; notifications ;
audit ; rapports ; MQTT ; service d'IA ; intégrations externes en
double.

## 38.3 Sécurité

Accès sans authentification ; accès hors rôle ; accès hors périmètre ;
référence directe à un objet ; injection ; XSS ; CSRF ; CORS ; rejeu ;
force brute ; fichier malveillant ; exposition de secrets ; expiration ;
élévation de privilège ; en-têtes de sécurité.

## 38.4 Concurrence

Inscriptions simultanées ; affectations concurrentes ; double
émargement ; corrections simultanées ; confirmations d'import
concurrentes ; publications de planning concurrentes. Aucune de ces
situations ne produit d'erreur `500`.

## 38.5 Performance

Lecture de planning avec et sans cache ; génération de jeton ;
validation de présence ; import de 500 apprenants ; rapport mensuel ;
tenue de charge d'émargement. Les mesures sont publiées, y compris
lorsqu'une cible n'est pas atteinte.

## 38.6 Bout en bout navigateur

Parcours complets pilotés dans un navigateur réel, avec captures
d'écran, pour chaque profil et chaque parcours prioritaire.

## 38.7 Accessibilité

Contrôle outillé sur chaque écran ; navigation clavier ; contrastes ;
messages d'erreur ; alternatives à la caméra et à la biométrie.

## 38.8 Règle de vérité

Aucun test n'est écrit contre un écran qui n'existe pas. Aucune
fonctionnalité n'est déclarée testée sans commande exécutée et
reproductible.

---

# 39. Recette

## 39.1 Scénario principal

1. l'administrateur crée une formation, un niveau et une année ;
2. le responsable crée une promotion et une classe ;
3. il importe une liste d'apprenants et confirme ;
4. les comptes sont créés et les invitations parties ;
5. un apprenant active son compte et enregistre une passkey ;
6. le responsable importe un planning, corrige une ligne et publie ;
7. les séances sont créées et notifiées ;
8. le formateur consulte et ouvre sa séance ;
9. le QR dynamique et le code court sont affichés ;
10. un apprenant émarge par QR, un autre par code court ;
11. un troisième est saisi manuellement avec motif ;
12. les présences apparaissent en direct ;
13. le formateur clôture la séance ;
14. un apprenant dépose un justificatif avec pièce jointe ;
15. le responsable l'accepte, l'absence devient `EXCUSED` ;
16. un apprenant ouvre une réclamation, elle est traitée ;
17. une correction est réalisée et auditée ;
18. les rapports sont produits et exportés en CSV, Excel et PDF ;
19. une attestation est générée ;
20. la piste d'audit est consultée.

## 39.2 Critères de validation

Aucun blocage ; données cohérentes ; autorisations respectées ; erreurs
lisibles ; preuves disponibles ; documentation à jour.

---

# 40. Exploitation

## 40.1 Supervision

Santé de l'application, de MySQL, de Redis, du service d'IA, du broker
MQTT et du service de messagerie. Métriques : temps de réponse, erreurs,
connexions échouées, taux de succès du cache, volume d'émargements,
files d'effets de bord, événements de dispositifs, alertes.

Journaux structurés, niveaux adaptés, identifiant de corrélation, aucun
secret, rotation et durée définies.

## 40.2 Sauvegarde et restauration

Export de la base, sauvegarde des pièces jointes, script documenté,
horodatage, emplacement protégé. La procédure de restauration décrit
l'arrêt contrôlé, la restauration, les migrations, la vérification, le
test de connexion et le contrôle d'intégrité. **Au moins un test de
restauration est réalisé et documenté.**

## 40.3 Environnements

| Environnement | Base | Usage |
|---|---|---|
| Local | `esic_connect` | développement |
| Démonstration | `esic_connect_demo` | jeu de données fictives |
| Tests | `esic_test` | suite automatisée |
| Intégration continue | `esic_connect_ci` | pipeline |
| Recette | dédiée | validation |
| Production | dédiée | exploitation |

Ces bases ne sont jamais confondues : le profil de test lit une variable
distincte de celle du profil applicatif.

---

# 41. Définition de terminé

Une exigence est terminée lorsque :

- l'exigence est identifiée et tracée ;
- le code compile ;
- les tests unitaires, d'intégration, de sécurité et de bout en bout la
  couvrent et passent ;
- les erreurs sont gérées et lisibles ;
- les autorisations sont testées, y compris les refus ;
- la performance attendue est mesurée ;
- l'accessibilité est vérifiée ;
- l'API est documentée ;
- la documentation et `docs/CURRENT-STATE.md` sont à jour ;
- une preuve existe (test, capture, mesure) ;
- la fonctionnalité peut être expliquée et démontrée.

---

# 42. Gouvernance documentaire

Ce cahier des charges est la **référence fonctionnelle unique**. Toute
modification majeure précise l'ancienne règle, la nouvelle, la raison,
l'impact, la priorité, la date et l'auteur.

Interdits absolus lors de la rédaction ou de l'implémentation :

- inventer une fonctionnalité, un test ou un résultat ;
- déclarer terminé sans preuve ;
- confondre implémenté, testé, vérifié et démontré ;
- modifier une règle de gestion sans documenter le changement ;
- placer un secret dans le dépôt ;
- utiliser des données réelles ;
- réécrire intégralement un document pour une modification mineure.

L'état réel de l'implémentation ne figure **pas** dans ce document : il
est tenu dans `docs/CURRENT-STATE.md`, mis à jour à chaque livraison.

---

# 43. Approbation

| Rôle | Nom | Décision | Date |
|---|---|---|---|
| Porteur du produit | Abubacar AFOLABI | Adopté | 3 septembre 2026 |
| Responsable pédagogique consulté | À compléter | À valider | |
| Référent technique | À compléter | À valider | |
| Référent sécurité et RGPD | À compléter | À valider | |
