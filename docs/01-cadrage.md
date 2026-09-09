# Note de cadrage — ESIC Connect

## Métadonnées

| Élément | Valeur |
|---|---|
| Nom du produit | ESIC Connect |
| Nature | Plateforme web et mobile de gestion pédagogique, d'émargement et d'assiduité |
| Établissement concerné | ESIC |
| Porteur du produit | Abubacar AFOLABI |
| Version du document | **3.0** |
| Date | 3 septembre 2026 |
| Statut | Référence active |
| Remplace | version 2.1 du 27 août 2026 (cadrage « preuve de concept trois jours ») |
| Horizon de réalisation | Six mois — 13 sprints de deux semaines |
| Environnement | Développement local conteneurisé, puis recette, puis production |

---

## 0. Ce que cette version change

La version 2.1 cadrait une **preuve de concept réalisée en trois jours**.
Elle contenait, pour cette raison, un grand nombre de restrictions
volontaires : périmètre « obligatoire / souhaité / expérimental / hors
périmètre », fonctions déclarées simulées, addendums de réduction de
périmètre.

**Ces restrictions sont levées.** La cible n'est plus un prototype de
démonstration mais une **application complète**, destinée à être
déployée et utilisée. En conséquence :

| Ancienne notion | Devient |
|---|---|
| « Périmètre du prototype de trois jours » (§23 v2.1) | supprimé — remplacé par la trajectoire de six mois (§20) |
| « Souhaité », « Expérimental ou simulé » | **exigences de la version 1.0** |
| Addendum F2 du 31 août 2026 (planning hors périmètre) | supprimé — déjà caduc, le planning est livré |
| Addendum G1 du 3 septembre 2026 | absorbé — le planning est une capacité normale du produit |
| « Hors périmètre » | réduit aux seules **exclusions de conception** (§19) |
| Statut `HORS_PÉRIMÈTRE_ASSUMÉ` | supprimé du vocabulaire projet |

Les fonctions qui étaient annoncées comme « à venir » — service
d'intelligence artificielle, dispositif IoT, PWA installable et hors
ligne, WebAuthn, MFA, protection anti-robot, réclamations, exports
Excel et PDF, calendrier unifié, intégration Microsoft 365 — **font
partie de la version 1.0**.

---

## 1. Présentation du produit

**ESIC Connect** est la plateforme unique de l'ESIC pour :

- structurer l'offre de formation et les cohortes d'apprenants ;
- construire, contrôler et publier les plannings ;
- créer et piloter les séances de cours ;
- enregistrer les présences de façon fiable et traçable ;
- traiter les absences, retards, justificatifs et réclamations ;
- produire les rapports et attestations d'assiduité ;
- superviser la sécurité et la conformité du système d'information.

Elle est accessible depuis :

- un navigateur web sur ordinateur ;
- une tablette ;
- un smartphone, via une **Progressive Web App installable**, capable de
  fonctionner en mode dégradé sans réseau ;
- une **borne d'émargement connectée** installée en salle.

Le produit prend en charge les modalités d'enseignement suivantes :

- présentiel ;
- distanciel pour une classe entière ;
- distanciel individuel pour un apprenant autorisé ;
- hybride, mélangeant les deux au sein d'une même séance.

### 1.1 Proposition de valeur

| Pour | ESIC Connect apporte |
|---|---|
| Direction | une vision consolidée et fiable de l'assiduité, sans ressaisie |
| Administration scolaire | la fin des feuilles papier, des rapports produits en quelques secondes, des attestations générées |
| Responsable pédagogique | la maîtrise de son périmètre, de l'import du planning à la publication, avec détection des conflits |
| Formateur | une liste d'appel prête, un émargement en une action, aucune saisie administrative |
| Apprenant | la visibilité sur son planning, son assiduité et ses démarches, avec un droit de réclamation traçable |
| Responsable sécurité | une authentification forte, une piste d'audit complète et une conformité RGPD documentée |

---

## 2. Contexte

L'ESIC propose des formations en commerce et en informatique : BTS,
Bachelors, Mastères, et d'autres parcours pouvant être ajoutés. Chaque
ensemble de formations est placé sous la responsabilité d'un responsable
pédagogique.

Les enseignements sont dispensés en présentiel, entièrement à distance,
en mode hybride, ou à distance pour certains apprenants ne pouvant pas
se déplacer.

### 2.1 Situation avant ESIC Connect

Le planning est construit par le responsable pédagogique dans un tableur
puis partagé sur Microsoft Teams. Les listes d'appel sont produites à la
main. Les présences sont relevées sur des feuilles papier ou dans des
fichiers Excel. Les contrôles d'absence supposent de solliciter
successivement le formateur, le responsable pédagogique, les conseillers
et l'administration.

Il en résulte :

- des ressaisies manuelles et des doublons ;
- des erreurs de saisie et des pertes de documents ;
- une consolidation lente et une visibilité tardive sur l'assiduité ;
- une traçabilité insuffisante des corrections ;
- une difficulté à démontrer la réalité d'une présence ;
- des comptes non activés à cause d'adresses électroniques erronées ;
- une gestion des remplacements difficile à communiquer ;
- aucune détection des situations anormales.

### 2.2 Ce que le produit remplace

ESIC Connect remplace, pour le suivi de l'assiduité : les feuilles
d'émargement papier, les classeurs Excel de présence, les listes d'appel
manuelles, et les échanges de fichiers de planning par messagerie.

ESIC Connect **ne remplace pas** Microsoft Teams comme outil de
visioconférence ni comme espace de discussion : il s'y **intègre**.

---

## 3. Problématique

> Comment doter l'ESIC d'un système d'information unique, performant,
> accessible et hautement sécurisé, qui couvre sans rupture le cycle
> allant de l'intégration d'un apprenant jusqu'à la production d'une
> attestation d'assiduité, en garantissant la fiabilité de la présence
> enregistrée, la traçabilité des décisions, la protection des données
> personnelles et la continuité du service ?

---

## 4. Finalité

ESIC Connect doit permettre à l'établissement de :

1. centraliser l'ensemble des données pédagogiques ;
2. structurer formations, niveaux, promotions, classes et matières ;
3. importer et tenir à jour la population d'apprenants ;
4. créer, inviter et activer les comptes utilisateurs ;
5. importer, contrôler, corriger, versionner et publier les plannings ;
6. créer automatiquement les séances depuis un planning publié ;
7. gérer les salles, les conflits et les remplacements ;
8. enregistrer les présences par plusieurs canaux fiables ;
9. calculer l'assiduité en tenant compte des rythmes d'alternance ;
10. traiter justificatifs, réclamations et départs anticipés ;
11. produire rapports, exports et attestations ;
12. notifier les acteurs sur les canaux qu'ils utilisent ;
13. détecter les anomalies et assister l'humain par l'IA ;
14. intégrer des dispositifs connectés en salle ;
15. s'intégrer à Microsoft 365 et aux calendriers du marché ;
16. garantir la sécurité, l'audit et la conformité RGPD ;
17. être exploité, supervisé, sauvegardé et restauré.

---

## 5. Objectifs

### 5.1 Objectif général

Livrer une application web et mobile complète, sécurisée et exploitable,
couvrant le cycle **apprenant → planning → séance → présence →
assiduité → rapport**, et déployée sur un environnement accessible aux
utilisateurs de l'ESIC.

### 5.2 Objectifs fonctionnels

Le système doit permettre de :

**Identité et accès**

1. authentifier par email et mot de passe ;
2. authentifier sans mot de passe par passkey (WebAuthn) ;
3. imposer un second facteur TOTP aux comptes privilégiés ;
4. appliquer une authentification adaptative selon le risque ;
5. protéger les formulaires publics contre les robots ;
6. limiter les tentatives répétées ;
7. gérer plusieurs rôles par utilisateur et un contexte d'usage ;
8. réinitialiser un mot de passe de façon sécurisée ;
9. gérer les appareils de confiance et les révoquer.

**Référentiels et population**

10. gérer formations, niveaux, années, promotions, classes, matières ;
11. gérer sites, bâtiments, salles, équipements et plages réseau ;
12. gérer les rythmes d'alternance et leurs exceptions ;
13. importer les apprenants depuis CSV, Excel et classeur multifeuille ;
14. détecter doublons, conflits et erreurs avant toute écriture ;
15. créer, inviter, activer, suspendre, archiver et restaurer les comptes ;
16. suivre la délivrabilité des invitations et les réémettre ;
17. gérer les formateurs internes, externes et remplaçants.

**Planning et séances**

18. importer un planning CSV, Excel ou PDF texte ;
19. faire assister la reconnaissance des colonnes par l'IA ;
20. prévisualiser, corriger ligne à ligne et valider avant publication ;
21. détecter les conflits de formateur, de classe, de salle et d'horaire ;
22. construire un planning directement dans un calendrier interactif ;
23. versionner un planning et revenir à une version antérieure ;
24. publier et créer ou mettre à jour les séances ;
25. annuler, reporter et remplacer une séance ;
26. synchroniser le planning avec Outlook, Teams, Google et iCalendar.

**Émargement et assiduité**

27. ouvrir et clôturer une séance ;
28. générer un QR code dynamique et un code court de secours ;
29. exposer un QR fixe de salle contrôlé par plage réseau ;
30. confirmer localement l'émargement par WebAuthn ;
31. recevoir un émargement depuis une borne connectée ;
32. gérer quatre points de contrôle journaliers ;
33. calculer demi-journées, journées, retards et présences partielles ;
34. enregistrer une présence manuelle motivée ;
35. accueillir provisoirement un apprenant non encore inscrit ;
36. traiter les départs anticipés ;
37. corriger une présence avec motif, historique et audit.

**Suivi, communication et pilotage**

38. déposer, examiner et décider sur un justificatif avec pièce jointe ;
39. ouvrir, échanger, transférer, résoudre et rouvrir une réclamation ;
40. notifier dans l'application, par email et par notification push ;
41. laisser l'utilisateur régler ses préférences de notification ;
42. produire des tableaux de bord par rôle ;
43. produire les rapports journaliers, mensuels, annuels et individuels ;
44. exporter en CSV, Excel et PDF ;
45. générer une attestation d'assiduité identifiable et vérifiable ;
46. rechercher globalement un apprenant, une classe, une séance ;
47. détecter les anomalies d'émargement et les soumettre à un humain ;
48. anticiper les décrochages d'assiduité ;
49. consulter la piste d'audit ;
50. exercer les droits RGPD des personnes.

### 5.3 Objectifs techniques

- back-end Java 21 / Spring Boot, structuré en monolithe modulaire
  vérifié par Spring Modulith ;
- front-end Angular standalone, zoneless, signaux, Angular Material ;
- Progressive Web App installable, avec cache applicatif et file
  d'actions différées ;
- MySQL 8 comme source de vérité, migrations Flyway ;
- Redis 7 pour les jetons, le cache, les compteurs et la limitation ;
- service d'intelligence artificielle Python / FastAPI isolé ;
- communication IoT MQTT authentifiée et chiffrée ;
- API REST versionnée et documentée par OpenAPI ;
- messagerie asynchrone avec file, reprise et file d'échec ;
- observabilité : santé, métriques, journaux structurés, corrélation ;
- conteneurisation Docker Compose en local, image de production ;
- intégration et déploiement continus ;
- sauvegarde et restauration testées.

### 5.4 Objectifs de performance

| Indicateur | Cible |
|---|---|
| Lecture d'un planning en cache | < 100 ms |
| Génération d'un jeton d'émargement | < 100 ms |
| Validation d'un émargement | < 300 ms |
| Chargement initial de l'application | < 2,5 s en 4G |
| Simulation d'un import de 500 apprenants | < 10 s |
| Rapport mensuel d'une classe | < 2 s |
| Émargements simultanés soutenus | 200 par minute |

Ces cibles sont **mesurées** et publiées ; toute cible non atteinte est
documentée avec sa mesure réelle.

### 5.5 Objectifs de sécurité

- hachage des mots de passe par BCrypt, migration Argon2id prévue ;
- authentification forte adaptée à la sensibilité du compte ;
- contrôle d'accès par rôle, par périmètre et par ressource ;
- refus par défaut, contrôle systématique côté serveur ;
- jetons courts, rotatifs, révocables, jamais dans `localStorage` ;
- aucune donnée personnelle dans un QR code ;
- aucune donnée biométrique reçue ni stockée ;
- limitation des tentatives sur toutes les routes sensibles ;
- protection anti-robot sur les formulaires exposés ;
- journalisation de toutes les opérations sensibles, sans donnée
  personnelle ni adresse IP dans l'audit métier ;
- secrets hors du dépôt, gérés par variables d'environnement puis par un
  gestionnaire de secrets ;
- validation humaine obligatoire de toute alerte produite par l'IA ;
- données de démonstration exclusivement fictives.

---

## 6. Périmètre organisationnel

```text
ESIC
└── Formation
    └── Promotion (année scolaire)
        └── Classe ou groupe
            └── Apprenants
```

Acteurs : administration technique, administration scolaire,
responsables pédagogiques, formateurs internes, formateurs externes,
formateurs remplaçants, apprenants.

Un responsable pédagogique peut gérer plusieurs formations. Une
formation possède un responsable principal et, si nécessaire, des
responsables délégués.

---

## 7. Rôles

Le détail des droits figure au cahier des charges (`docs/02`, §6). En
synthèse :

| Rôle | Vocation | Second facteur |
|---|---|---|
| `SUPER_ADMIN` | contrôle technique global, incidents, dispositifs, politiques de sécurité | **obligatoire** |
| `ADMIN` | administration fonctionnelle, utilisateurs, référentiels, invitations | **obligatoire** |
| `SCHOOL_ADMINISTRATION` | assiduité, justificatifs, réclamations, rapports, attestations | recommandé |
| `PEDAGOGICAL_MANAGER` | propriétaire fonctionnel de son périmètre : classes, imports, plannings, séances | obligatoire sur opérations sensibles |
| `TEACHER` | séances, émargement, présences, réclamations de ses séances | facultatif |
| `STUDENT` | planning, émargement, assiduité, justificatifs, réclamations | adaptatif |

Le cumul de rôles est natif. Il n'élargit jamais un périmètre : un
responsable pédagogique également formateur ne voit pas les formations
d'un autre responsable.

---

## 8. Chaîne de valeur couverte

```text
Référentiels
    ↓
Import des apprenants  →  Invitation  →  Activation
    ↓
Import ou création du planning  →  Contrôle  →  Correction  →  Publication
    ↓
Création des séances  →  Affectation  →  Remplacement  →  Notification
    ↓
Ouverture de séance  →  Émargement (QR dynamique, QR salle, code, borne, manuel)
    ↓
Points de contrôle  →  Calcul demi-journées  →  Retards  →  Anomalies
    ↓
Justificatifs  →  Réclamations  →  Corrections auditées
    ↓
Tableaux de bord  →  Rapports  →  Exports  →  Attestations
```

Chaque flèche de cette chaîne est une exigence de la version 1.0.

---

## 9. Émargement — principes directeurs

Le produit propose **cinq canaux** d'enregistrement de présence, tous
soumis aux mêmes contrôles serveur :

| Canal | Usage | Contrainte propre |
|---|---|---|
| QR dynamique du formateur | cas nominal, présentiel et distanciel | jeton rotatif, expiration courte, anti-rejeu |
| Code court | caméra indisponible, apprenant sur un seul appareil | même durée de vie que le jeton |
| QR fixe de salle | avant le début de la séance | plage réseau de l'établissement obligatoire |
| Borne connectée | salle équipée, badge ou interface locale | identité de dispositif, séquence, anti-rejeu |
| Saisie manuelle | incident, régularisation, autorisation exceptionnelle | motif obligatoire, audité |

Le QR ne contient **jamais** de donnée personnelle : uniquement un jeton
aléatoire, temporaire, non prédictible, associé à une séance et à un
point de contrôle, révocable et protégé contre le rejeu.

WebAuthn ajoute une **confirmation locale** de l'émargement : le
terminal produit une preuve cryptographique. ESIC Connect ne reçoit ni
empreinte, ni modèle facial. Cette confirmation est présentée pour ce
qu'elle est — un renforcement, non une preuve absolue de la présence
physique d'une personne. Un parcours de secours est toujours disponible.

---

## 10. Intelligence artificielle

L'IA est un **service d'assistance**, jamais un décideur.

| Fonction | Apport | Garde-fou |
|---|---|---|
| Assistance à l'importation | reconnaissance des colonnes, synonymes d'en-tête, normalisation des dates et horaires, séparation cours/formateur dans une cellule, rapprochement d'un formateur existant | score de confiance, proposition modifiable, confirmation humaine obligatoire |
| Détection d'anomalies d'émargement | score 0–1, niveau `LOW`/`MEDIUM`/`HIGH`, raisons explicites | aucune sanction automatique, revue humaine |
| Prévention du décrochage | repérage des absences répétées et des ruptures d'habitude | information au responsable, jamais de décision |
| Synthèse d'erreurs d'import | résumé lisible des anomalies | ne modifie aucune donnée |

L'IA ne publie jamais un planning, ne supprime jamais une présence, ne
refuse jamais un justificatif, ne prononce jamais de sanction et ne
reçoit jamais de données réelles non pseudonymisées.

---

## 11. Objets connectés

Une borne d'émargement repose sur une Raspberry Pi 4 et dialogue en
MQTT. Elle possède une identité unique, s'authentifie, chiffre ses
communications, publie un signal de vie et de la télémétrie, met ses
événements en file locale en cas de coupure et rejoue à la reconnexion.
Chaque événement porte un identifiant unique et un numéro de séquence :
un événement déjà traité est ignoré.

Le back-end tient un registre des dispositifs autorisés, avec
révocation. Un événement provenant d'un dispositif inconnu est rejeté et
journalisé comme incident de sécurité.

Un **simulateur logiciel** reproduit fidèlement le protocole de la
borne : il permet de développer, tester et démontrer la chaîne complète
sans matériel.

---

## 12. Intégrations externes

| Intégration | Rôle | Mise en œuvre |
|---|---|---|
| Microsoft Graph | lecture de l'annuaire, création de réunions Teams depuis les séances distancielles, écriture dans les calendriers | adaptateur dédié, activable par configuration |
| Flux iCalendar | abonnement au planning depuis n'importe quel agenda | flux signé, par utilisateur, révocable |
| Google Calendar | synchronisation optionnelle | même adaptateur de calendrier |
| Fournisseur SMTP | envoi réel des courriels et retours de délivrabilité | Mailpit en local, fournisseur réel en production |
| Cloudflare Turnstile | protection anti-robot | validation serveur obligatoire |

Toute intégration externe est **encapsulée derrière un port** : le
produit fonctionne intégralement sans elle, avec un adaptateur local.

---

## 13. Architecture — orientations

- **Monolithe modulaire** Spring Boot, modules isolés et vérifiés
  automatiquement (aucune dépendance vers l'interne d'un autre module,
  aucun cycle). Ce choix est assumé : il donne la cohésion
  transactionnelle nécessaire à l'émargement et à la publication, sans
  le coût opérationnel d'une constellation de services.
- **Deux services séparés seulement** : l'application Spring Boot et le
  service d'IA Python, parce que ce dernier a un écosystème et un cycle
  de vie distincts.
- **Communication entre modules par événements** et par ports publics ;
  aucune entité JPA partagée entre modules.
- **Outbox transactionnelle** pour tout effet de bord externe : courriel,
  notification, audit, publication MQTT. Un effet de bord n'est jamais
  perdu par une panne du consommateur, ni produit si la transaction
  métier est annulée.
- **Front-end** en composants standalone, détection de changement sans
  zone, état par signaux, jeton en mémoire uniquement.
- **PWA** : coquille applicative en cache, consultation hors ligne du
  planning et de l'assiduité récents, file d'actions différées avec
  résolution de conflit au retour du réseau. Une présence enregistrée
  hors ligne n'est **jamais** définitive avant validation serveur.

Le détail figure dans `docs/03-architecture.md`.

---

## 14. Données et conformité

- source de vérité MySQL, identifiants exposés sous forme d'UUID ;
- Redis réservé aux données temporaires — jamais source de vérité ;
- minimisation : seules les données nécessaires sont collectées ;
- durées de conservation définies par catégorie, avec archivage
  intermédiaire puis purge ou anonymisation ;
- droits des personnes outillés : accès, rectification, limitation,
  export, effacement lorsque applicable ;
- registre des traitements et analyse d'impact préparés ;
- pseudonymisation systématique avant tout traitement par l'IA ;
- aucune donnée biométrique, aucune géolocalisation permanente.

Le détail figure dans `docs/08-securite-rgpd.md`.

---

## 15. Exploitation

Le produit est **exploitable** : cela fait partie de la définition de
terminé.

- démarrage complet en une commande en local ;
- images de production reproductibles ;
- migrations de base automatiques et vérifiées ;
- sondes de santé et de disponibilité ;
- métriques applicatives et journaux structurés corrélés ;
- sauvegarde planifiée et **restauration testée** ;
- procédure d'incident documentée ;
- intégration continue exécutant l'ensemble des contrôles ;
- déploiement continu vers un environnement de recette.

---

## 16. Qualité — exigences transverses

| Domaine | Exigence |
|---|---|
| Tests | tests unitaires, d'intégration, de sécurité, de concurrence, de performance et de bout en bout navigateur ; toute exigence `MUST` est couverte |
| Accessibilité | conformité WCAG 2.1 niveau AA visée, vérifiée par outil et par navigation clavier ; alternative à la caméra et à la biométrie systématique |
| Internationalisation | interface française, textes externalisés, seconde langue possible sans refonte |
| Compatibilité | navigateurs modernes, Android, iOS via navigateur, ordinateurs, tablettes, smartphones |
| Documentation | à jour à chaque livraison ; une fonction non documentée n'est pas terminée |
| Traçabilité | exigence → story → code → test → preuve |

---

## 17. Vocabulaire de statut

Vocabulaire **unique** du dépôt :

| Statut | Signification |
|---|---|
| `IMPLEMENTED_AND_TESTED` | code livré **et** couvert par des tests automatisés passants |
| `PARTIAL` | une partie seulement de l'exigence est livrée — jamais présentée comme complète |
| `NOT_IMPLEMENTED` | aucun code ; limite explicitement assumée |
| `NOT_PERFORMED` | action jamais exécutée (démonstration manuelle, déploiement) |
| `À_DÉFINIR` | décision non prise |

Ne jamais confondre **implémenté**, **testé automatiquement**, **vérifié
manuellement** et **démontré**. Un navigateur piloté par un script n'est
pas une démonstration manuelle.

Le statut `HORS_PÉRIMÈTRE_ASSUMÉ` est **supprimé** : il n'y a plus de
domaine fonctionnel exclu, seulement des exclusions de conception (§19).

---

## 18. Trajectoire sur six mois

Le produit est construit en **13 sprints de deux semaines**. Chaque
sprint produit un incrément utilisable, testé et documenté.

| Phase | Sprints | Aboutissement |
|---|---|---|
| Socle | 1–2 | identité complète, sécurité forte, référentiels administrables |
| Population | 3–4 | imports multiformats, cycle de vie des comptes, alternance |
| Planification | 5–6 | planning importé, construit, versionné, publié, synchronisé |
| Présence | 7–8 | cinq canaux d'émargement, points de contrôle, calculs d'assiduité |
| Accompagnement | 9–10 | justificatifs, réclamations, notifications multicanal, PWA |
| Pilotage | 11 | tableaux de bord, rapports, exports, attestations |
| Intelligence et objets | 12 | service IA, borne connectée, détection d'anomalies |
| Mise en service | 13 | durcissement, observabilité, sauvegarde, déploiement |

Le détail — objectifs de sprint, contenu, jalons, dépendances — figure
dans `docs/06-roadmap-six-mois.md` et `docs/05-product-backlog.md`.

---

## 19. Exclusions de conception

Ces exclusions ne sont pas des reports : ce sont des **choix
définitifs**, motivés.

| Exclusion | Motif |
|---|---|
| Reconnaissance faciale centralisée | disproportionnée, risque RGPD majeur ; WebAuthn couvre le besoin sans donnée biométrique |
| Stockage de données biométriques brutes | jamais nécessaire : la vérification reste sur le terminal |
| Géolocalisation permanente | disproportionnée ; le contrôle réseau et le QR de salle suffisent |
| Reconnaissance universelle de PDF scanné | fiabilité insuffisante ; l'import PDF est limité au PDF texte |
| Décision disciplinaire automatisée | une décision affectant une personne relève d'un humain |
| Suppression automatique d'un apprenant | l'archivage préserve l'historique ; la suppression est exceptionnelle et contrôlée |
| Remplacement de Microsoft Teams | Teams reste l'outil de visioconférence ; ESIC Connect s'y intègre |
| Application iOS native publiée | la PWA couvre le besoin mobile |
| Kubernetes | inadapté à la volumétrie ; conteneurs et service managé suffisent |
| Architecture en microservices | complexité opérationnelle sans bénéfice à cette échelle |

---

## 20. Risques

| Risque | Probabilité | Impact | Atténuation |
|---|---:|---:|---|
| Ampleur du périmètre | Élevée | Élevé | trajectoire en 13 sprints, incrément utilisable à chaque fin de sprint |
| Fraude à l'émargement | Élevée | Élevé | jeton rotatif, anti-rejeu, WebAuthn, détection d'anomalies, audit |
| Attaque automatisée | Élevée | Élevé | anti-robot, limitation, verrouillage progressif, MFA |
| Hétérogénéité des plannings sources | Élevée | Moyen | modèle imposé, assistant IA, correction ligne à ligne |
| Adresses électroniques invalides | Élevée | Moyen | validation, suivi de délivrabilité, réémission |
| Confusion envoi / délivrabilité | Moyenne | Moyen | statuts internes et externes séparés |
| Indisponibilité de WebAuthn | Moyenne | Moyen | parcours de secours systématique |
| Erreur sur les rôles cumulés | Moyenne | Élevé | matrices de tests d'autorisation par module |
| Fuite par le cache | Faible | Élevé | clés contextualisées par périmètre, jamais de contournement d'autorisation |
| Perte d'un effet de bord | Moyenne | Moyen | outbox transactionnelle avec reprise |
| Défaillance de la borne | Moyenne | Moyen | file locale, reprise, simulateur |
| Erreur produite par l'IA | Moyenne | Élevé | score de confiance, validation humaine obligatoire |
| Dépendance à un service externe | Moyenne | Moyen | ports et adaptateurs, fonctionnement complet en local |
| Dérive documentaire | Moyenne | Élevé | `docs/STATUS.md` mis à jour à chaque livraison |
| Objectif de performance non atteint | Moyenne | Faible | mesure, publication du réel, optimisation ciblée |

---

## 21. Indicateurs de réussite

La version 1.0 est atteinte lorsque :

1. un utilisateur se connecte par mot de passe **ou** par passkey ;
2. un compte privilégié est protégé par un second facteur ;
3. le cumul de rôles fonctionne sans élargir un périmètre ;
4. un responsable importe 500 apprenants sans doublon ni perte ;
5. les invitations partent, sont suivies et réémissibles ;
6. un planning est importé, corrigé, versionné et publié ;
7. un planning est construit directement dans le calendrier ;
8. les séances sont créées, affectables et remplaçables ;
9. le planning est synchronisé vers un agenda externe ;
10. les cinq canaux d'émargement fonctionnent ;
11. les quatre points de contrôle produisent le calcul journalier ;
12. l'alternance n'est jamais comptée comme une absence ;
13. un justificatif accepté transforme `ABSENT` en `EXCUSED` ;
14. une réclamation vit son cycle complet avec historique ;
15. les notifications arrivent dans l'application, par email et en push ;
16. l'application est installable et consultable hors ligne ;
17. les rapports et attestations sont produits en CSV, Excel et PDF ;
18. l'IA propose un mapping et un score d'anomalie, validés par un humain ;
19. une borne connectée émarge et résiste à une coupure réseau ;
20. la piste d'audit couvre toutes les opérations sensibles ;
21. l'application est déployée sur un environnement accessible ;
22. une restauration de sauvegarde est démontrée ;
23. l'ensemble des tests passe en intégration continue.

---

## 22. Livrables

**Produit** : application déployée, code source, migrations, jeu de
données fictives, tests, documentation d'API.

**Documentation** : cadrage, cahier des charges, architecture, modèle de
données, backlog, roadmap, risques, sécurité et RGPD, stratégie de
tests, guide utilisateur, guide de déploiement, prérequis externes,
état courant.

**Exploitation** : composition Docker, images de production, chaîne
d'intégration continue, scripts d'administration, procédures de
sauvegarde, de restauration et d'incident.

---

## 23. Hypothèses

- l'établissement fournit les adresses électroniques des utilisateurs ;
- un modèle de fichier peut être imposé aux responsables pédagogiques ;
- certains formateurs utilisent une adresse externe ;
- les données de développement et de démonstration sont fictives ;
- la borne connectée dispose d'un accès au réseau local ;
- les intégrations externes peuvent être activées progressivement, le
  produit restant complet sans elles ;
- l'architecture cible peut être documentée avant d'être déployée.

---

## 24. Règles de gestion structurantes

Les règles complètes sont au cahier des charges (`docs/02`, §43). Les
règles structurantes du cadrage :

- un utilisateur peut posséder plusieurs rôles ; le cumul n'élargit
  jamais un périmètre ;
- un responsable pédagogique ne voit que son périmètre ;
- une séance normale provient d'un planning publié ;
- un formateur ne crée pas librement une séance de planning et ne valide
  pas son propre remplacement ;
- un apprenant émarge uniquement pour une séance à laquelle il est
  attendu ;
- une présence est unique par séance, par apprenant et par point de
  contrôle ;
- un jeton est limité dans le temps, à usage unique, révocable ;
- toute correction exige un motif et est auditée ;
- un fichier invalide ne crée aucune donnée avant confirmation ;
- un conflit bloquant interdit la publication ;
- une production de l'IA est toujours soumise à validation humaine ;
- le cache ne contourne jamais une autorisation ;
- une période en entreprise n'est jamais comptée comme une absence ;
- aucune donnée biométrique brute n'est stockée ;
- la démonstration utilise uniquement des données fictives.

---

## 25. Documents de référence

| Document | Objet |
|---|---|
| `docs/02-cahier-des-charges.md` | exigences fonctionnelles et techniques, règles, critères d'acceptation |
| `docs/03-architecture.md` | architecture logique, technique, décisions |
| `docs/04-modele-donnees.md` | modèle conceptuel et physique |
| `docs/05-product-backlog.md` | backlog produit complet, priorisé, estimé |
| `docs/06-roadmap-six-mois.md` | trajectoire, sprints, jalons |
| `docs/07-risques.md` | registre des risques |
| `docs/08-securite-rgpd.md` | analyse de sécurité et conformité |
| `docs/09-strategie-tests.md` | stratégie et plan de tests |
| `docs/10-guide-utilisateur.md` | guide fonctionnel par rôle |
| `docs/11-guide-deploiement.md` | installation, exploitation, déploiement |
| `docs/12-prerequis-externes.md` | comptes, clés et matériel à préparer |
| `docs/STATUS.md` | état réel du dépôt, mis à jour à chaque livraison |

---

## 26. Décision

Le cadrage version 3.0 est adopté comme référence unique. La cible est
une **application complète**, construite en six mois, déployée et
exploitable. Les restrictions de la version 2.1, liées à un exercice de
trois jours, sont sans objet et ne doivent plus être citées.
