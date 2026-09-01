# Guide d'utilisation — ESIC Connect (prototype)

| Élément | Valeur |
|---|---|
| Version | Finalisation F6 — 31 août 2026 |
| Périmètre | ce que chaque rôle peut réellement faire dans l'application livrée |
| Référence | `docs/CURRENT-STATE.md`, `docs/reports/PROJECT_FINAL_AUDIT.md` |

Ce guide décrit **l'application telle qu'elle est livrée**. Les
fonctions non implémentées sont signalées comme telles et ne doivent pas
être présentées comme disponibles.

## 1. Généralités

- **Connexion** : `/login`, adresse e-mail + mot de passe. En cas
  d'échec, message **générique** (aucune indication du motif).
- **Session** : le jeton reste **en mémoire du navigateur**. Un
  rechargement de page **déconnecte** (pas de « rester connecté » :
  choix de prototype, JWT sans refresh token).
- **Sélecteur de contexte de rôle** (en haut à droite) : visible
  **uniquement si le compte a au moins deux rôles**. Il **restreint**
  l'affichage au rôle choisi ; il **n'élargit jamais** les droits réels
  (Spring Security reste l'autorité). Un compte mono-rôle ne le voit pas.
- **Navigation** : seules les entrées correspondant aux rôles du compte
  (et au contexte actif) apparaissent. Un accès direct à une URL
  interdite renvoie « Accès refusé ».
- **Activation de compte** : `/activation?token=…` (lien reçu par
  e-mail), **page publique** ; l'utilisateur y définit son mot de passe.
- **Mot de passe oublié** : **non disponible** (non implémenté).

## 2. Rôles et écrans visibles

| Écran (route) | SUPER_ADMIN | ADMIN | SCHOOL_ADMIN | PEDAGO_MANAGER | TEACHER | STUDENT |
|---|:--:|:--:|:--:|:--:|:--:|:--:|
| Tableau de bord (`/dashboard`) | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Administration des comptes (`/administration`) | ✔ (R/W) | ✔ (R/W) | ✔ (lecture + suspend/réactiver) | — | — | — |
| Import apprenants (`/students/import`) | ✔ | ✔ | ✔ | ✔ (son périmètre) | — | — |
| Apprenants (`/students`) | ✔ (lecture) | ✔ (lecture) | ✔ (lecture) | — | — | — |
| Référentiels académiques (`/academic`) | ✔ (lecture) | ✔ (lecture) | ✔ (lecture) | ✔ (lecture, périmètre) | — | — |
| Alternance (`/alternation`) | ✔ (R/W) | ✔ (R/W) | ✔ (R/W) | ✔ (périmètre ; création de modèle : non) | — | — |
| Séances (`/sessions`) | ✔ (R/W) | ✔ (R/W) | ✔ (lecture) | ✔ (périmètre, R/W) | ✔ (ses séances) | — |
| Émargement (`/attendance`) | — | — | — | — | — | ✔ |
| Mes présences (`/my-attendance`) | — | — | — | — | — | ✔ |
| Suivi d'assiduité (`/attendance-management`) | ✔ | ✔ | ✔ | ✔ (périmètre) | — | — |

R/W = lecture et écriture. « périmètre » = limité aux formations gérées
par le responsable pédagogique, **décidé côté serveur**.

## 3. Parcours par rôle

### 3.1 `SUPER_ADMIN`

Techniquement, `SUPER_ADMIN` a les mêmes écrans qu'`ADMIN` **plus** des
gardes renforcées : seul un `SUPER_ADMIN` peut administrer un compte
portant le rôle `SUPER_ADMIN` ou attribuer / retirer ce rôle.

- **Administration des comptes** : suspendre / réactiver / archiver un
  compte, attribuer / retirer un rôle (avec motif obligatoire).
  L'archivage clôture tous les rôles actifs et est **irréversible** dans
  ce lot.
- Restrictions : auto-suspension / auto-archivage / retrait de son
  propre dernier rôle **interdits** (`USER_SELF_ACTION_FORBIDDEN`,
  `USER_LAST_ACTIVE_ROLE`).
- **Non disponible dans l'UI** (API uniquement) : gestion des salles /
  du réseau (`organization`), configuration des plages réseau CIDR
  (`SUPER_ADMIN` requis).

### 3.2 `ADMIN`

- **Administration des comptes** : mêmes actions que ci-dessus, **sauf**
  sur un compte `SUPER_ADMIN` (masqué + refusé côté serveur :
  `USER_SUPER_ADMIN_PROTECTED`).
- **Import CSV des apprenants** : voir §4.
- **Référentiels académiques** et **apprenants** : consultation
  seulement (les créations / modifications passent par l'API).
- **Alternance** : création / édition / archivage de modèles de rythme,
  affectation à une classe, exceptions individuelles.
- **Séances** : création d'une séance **exceptionnelle**, ouverture,
  fermeture, points de contrôle, QR / code court, présences,
  corrections, export CSV.
- **Suivi d'assiduité** : rapports séance / classe / apprenant /
  synthèse, export CSV, examen des justificatifs.

### 3.3 `SCHOOL_ADMINISTRATION`

- **Administration des comptes** : consultation + **suspendre /
  réactiver** (pas d'archivage, pas de gestion des rôles).
- **Import CSV**, **apprenants**, **référentiels académiques** :
  consultation.
- **Alternance** : R/W (y compris création de modèle de rythme).
- **Séances** : **lecture seule** (pas de création, pas d'ouverture).
- **Suivi d'assiduité** : rapports + export + examen des justificatifs.

### 3.4 `PEDAGOGICAL_MANAGER`

Tout est **borné à son périmètre** (formations qu'il gère). Un accès
hors périmètre renvoie `403` (« Accès refusé ») même si l'écran est
visible.

- **Import CSV des apprenants** : sur ses classes ; une ligne visant une
  classe hors périmètre est marquée `IMP_CLASS_OUT_OF_SCOPE` et le job
  devient non confirmable.
- **Référentiels académiques** : consultation de son périmètre.
- **Alternance** : affectation d'un rythme à ses classes, exceptions
  individuelles de ses apprenants. **Création d'un modèle de rythme :
  non** (réservé à ADMIN / SUPER_ADMIN / SCHOOL_ADMINISTRATION).
- **Séances** : création / ouverture / fermeture pour ses classes ;
  émargement, présences, corrections.
- **Suivi d'assiduité** : rapports de son périmètre.
- **Limite connue** : la liste des inscriptions (`GET /enrollments`) est
  fermée au `PEDAGOGICAL_MANAGER` ; l'écran d'alternance propose alors
  une saisie directe d'identifiant d'inscription en repli.
- **Non disponible dans l'UI** : l'affectation d'un responsable
  pédagogique à une formation se fait par l'API (`ADMIN` / `SUPER_ADMIN`)
  ou, en démonstration, par `scripts/seed-demo.sh`.

### 3.5 `TEACHER`

- **Séances** (`/sessions`) : voit **uniquement ses propres séances**.
  Peut les ouvrir, afficher le **QR code** et le **code court**, suivre
  les présences en direct (rafraîchissement + polling), saisir une
  présence manuelle, corriger une présence (motif obligatoire), annuler
  une présence (logique, historisée), exporter le CSV de la séance,
  fermer la séance.
- **Ne peut pas** : créer une séance depuis un planning (pas de
  planning), examiner un justificatif, accéder aux rapports agrégés.
- Pas d'accès aux autres écrans.

### 3.6 `STUDENT`

- **Émargement** (`/attendance`) : saisir le **code court** affiché par
  le formateur. Erreurs possibles :
  - « code expiré / invalide » (`ATT_TOKEN_INVALID`) → demander un
    nouveau code au formateur ;
  - « séance fermée » (`ATT_SESSION_CLOSED`) ;
  - « présence déjà enregistrée » (`ATT_ALREADY_RECORDED`) ;
  - « vous n'êtes pas inscrit » (`ATT_NOT_ENROLLED`).
  - **Scan caméra : non disponible** (le code court est le seul
    parcours). Une note l'indique à l'écran.
- **Mes présences** (`/my-attendance`) : historique (présences réelles +
  absences dérivées d'un point de contrôle fermé), dépôt et suivi d'un
  **justificatif métier** (catégorie, période, motif — **sans pièce
  jointe**), modification tant qu'il est `PENDING`.
- **Ne voit jamais** les données d'un autre apprenant.

## 4. Import CSV des apprenants (`/students/import`)

1. **Téléverser** un fichier `.csv` (≤ 2 Mo). Colonnes minimales :
   `last_name, first_name, email, formation_code, class_code,
   academic_year` (+ `phone` facultatif). En-têtes `level_code`,
   `promotion_code`, `work_study_pattern` : **ignorés avec un
   avertissement**.
2. **Simulation** (automatique) : le fichier est analysé, **aucune
   donnée n'est créée**. Le résultat affiche, par ligne, l'action
   prévue (`création + inscription`, `inscription`, `mise à jour`,
   `transfert de classe`, `aucune`) et les **anomalies** (colonne
   manquante, e-mail invalide, doublon dans le fichier, doublon avec un
   compte existant, classe / année inconnue, hors périmètre…).
3. **Revue** : filtrer par statut de ligne / gravité, déplier les
   anomalies. Un job avec au moins une ligne en **erreur** est
   **non confirmable**.
4. **Confirmation** : crée les comptes (`PENDING_ACTIVATION`), le rôle
   `STUDENT`, l'inscription, et **envoie l'e-mail d'invitation** (visible
   dans Mailpit). Opération **transactionnelle** : en cas d'échec,
   **rien** n'est créé. Une confirmation déjà appliquée est **idempotente**.
5. **Annulation** possible tant que le job n'est pas confirmé.

Fichier d'exemple fourni : `docs/demo-data/apprenants-demo.csv` (voir
`docs/demo-data/README.md`).

## 5. Séances et émargement (parcours prototype)

> **Il n'y a pas d'import de planning.** Les séances sont créées
> **manuellement** comme séances **exceptionnelles** (motif obligatoire).

1. `ADMIN` / `SUPER_ADMIN` / `PEDAGOGICAL_MANAGER` : **Séances → Nouvelle
   séance**. Choisir le formateur (compte `TEACHER` actif), ≥ 1 classe,
   date / heures, fuseau, motif.
2. Le formateur ouvre la séance (`PLANNED → OPEN`). Cycle strict, **pas
   de réouverture**, pas de modification, pas d'annulation, pas de
   remplaçant.
3. Points de contrôle : au moins le point `START` (créé avec la séance) ;
   possibilité d'en ajouter (`CUSTOM`).
4. Le formateur affiche le **QR code** (jeton opaque, sans donnée
   personnelle) et le **code court** (8 caractères). Le jeton **tourne**
   toutes les ~30 s.
5. L'apprenant saisit le code court dans **Émargement**. Présence
   classée `PRESENT` ou `LATE` (seuil unique de 10 minutes).
6. Le formateur voit la liste des présences se mettre à jour, corrige si
   besoin (motif obligatoire, historique conservé), exporte le CSV.
7. Le formateur ferme la séance : les jetons deviennent inutilisables.
8. **Suivi d'assiduité** : rapports (demi-journées ; une période en
   entreprise n'est jamais comptée en absence si un rythme d'alternance
   est affecté à la classe), export CSV.

## 6. Fonctions non disponibles dans l'interface

**Endpoints livrés mais sans écran** (API uniquement) :

- gestion des salles / bâtiments / sites / plages réseau
  (`organization`) ;
- affectation d'un responsable pédagogique à une formation
  (`pedagogical-assignments`) ;
- création / modification / archivage des référentiels académiques
  (`academic`) — l'UI est en lecture seule ;
- création / transfert / clôture d'inscription (`enrollment`) — sauf via
  l'import CSV ;
- émission / relance d'une invitation d'activation.

**Non implémenté du tout** (à ne pas présenter comme disponible) :

- import du planning, publication, création des séances depuis un
  planning, versionnement ;
- annulation de séance, remplaçant, `PATCH` de séance ;
- QR fixe de salle, contrôle réseau, scan caméra ;
- WebAuthn / passkeys, MFA TOTP, anti-bot ;
- réclamations / messagerie, départ anticipé, justificatif **avec pièce
  jointe**, import Excel / multifeuille ;
- notifications **push PWA** et **email métier**, préférences de
  notification (le **centre de notifications in-app** `/notifications`
  est livré depuis G1-D pour l'audience **formateur** — planning publié /
  séance annulée / remplaçant ; livraison « au mieux » après commit) ;
- service IA (mapping de colonnes, score d'anomalie) ;
- IoT / MQTT / Raspberry Pi ;
- PWA installable / hors ligne ;
- mot de passe oublié, déconnexion serveur / révocation de session ;
- rapports « officiels » (logo, PDF), export Excel.

Détail et justifications : `docs/reports/PROJECT_FINAL_AUDIT.md` §0.3 et
§7.4 ; `docs/01-cadrage.md` §23.5 ; `docs/02-cahier-des-charges.md`
§4.5.1.
