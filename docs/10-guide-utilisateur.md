# Guide d'utilisation — ESIC Connect

| Élément | Valeur |
|---|---|
| Version | **Refonte complète — 9 septembre 2026** (l'édition « F6 / 31 août » décrivait un état antérieur aux sprints 8 à 11) |
| Périmètre | ce que chaque rôle peut réellement faire dans l'application livrée |
| Référence | `docs/STATUS.md` — **seule source de vérité** sur l'avancement ; en cas de doute, c'est elle qui prime sur ce guide |

Ce guide décrit **l'application telle qu'elle est livrée**. Les fonctions
non implémentées ou partielles sont signalées comme telles (§9) et ne
doivent jamais être présentées comme disponibles.

---

## 1. Généralités

### 1.1 Se connecter

- **Mot de passe** : `/login`, adresse e-mail + mot de passe. La réponse
  est **uniforme** — e-mail inconnu, mot de passe erroné et compte
  inactif produisent le même message (aucune indication du motif).
- **Passkey (WebAuthn)** : bouton « Se connecter avec une clé d'accès »
  sur un appareil déjà enregistré — sans mot de passe. Le serveur ne
  reçoit **qu'une signature cryptographique**, jamais de donnée
  biométrique.
- **Second facteur (TOTP)** : **obligatoire** pour `SUPER_ADMIN` et
  `ADMIN` (aucun jeton n'est délivré contre le seul mot de passe — la
  connexion renvoie un défi `VERIFY` ou, si aucun facteur n'est encore
  enrôlé, `ENROLL` puis confirmation). **Adaptatif** pour les autres
  rôles : demandé à la première connexion, sur un appareil inconnu, ou
  lors d'un changement inhabituel. 10 **codes de récupération** à usage
  unique sont fournis à l'enrôlement.
- **Anti-robot** : Cloudflare Turnstile protège l'activation, la demande
  de réinitialisation et la connexion après trois échecs. Sans clé
  configurée, le produit **déclare** qu'aucun contrôle n'est actif — il
  n'en simule pas un.
- **Limitation de débit** : après plusieurs tentatives, ralentissement
  puis verrouillage **temporaire** (jamais définitif). Message
  `429` + `Retry-After`, sans rien révéler sur l'existence du compte.

### 1.2 Activer son compte, gérer son mot de passe

- **Activation** : `/activation?token=…` (lien reçu par e-mail), page
  **publique** ; l'utilisateur y définit son mot de passe et peut
  enregistrer une passkey dans la foulée. Le lien expire après **un
  mois**.
- **Mot de passe oublié** : écran « Mot de passe oublié » depuis la page
  de connexion — **réponse neutre** (l'existence d'un compte n'est jamais
  révélée), lien à usage unique et limité dans le temps ; à son
  ouverture, définition d'un nouveau mot de passe puis **révocation de
  toutes les sessions**.
- **Changer son mot de passe** : `/mon-compte/securite`, carte « Mot de
  passe » — mot de passe actuel + nouveau + confirmation. Le mot de passe
  actuel et la politique (12 caractères minimum, liste de mots de passe
  courants, pas d'adresse dans le mot de passe) sont vérifiés **côté
  serveur**. On ne peut changer que **son propre** mot de passe (tous les
  rôles). Au succès : **toutes les sessions du compte sont fermées**,
  renvoi vers `/login`, l'ancien mot de passe devient inutilisable.
- **Sécurité de mon compte** (`/mon-compte/securite`) : passkeys (ajout,
  liste, révocation individuelle), second facteur, **appareils de
  confiance** (liste, révocation), déconnexion de toutes les sessions.

### 1.3 Session et navigation

- **Jeton d'accès en mémoire seule** (jamais dans `localStorage`). Un
  **rechargement de page en ligne rétablit la session** via un cookie de
  renouvellement `HttpOnly` `SameSite=Strict` rotatif avec détection de
  rejeu. Un démarrage **hors ligne** affiche l'écran de connexion (le
  cookie exige le serveur). Expiration après **30 min d'inactivité**,
  plafond absolu configurable.
- **Sélecteur de contexte de rôle** (en-tête, panneau « Profil ») :
  visible **uniquement** si le compte a au moins deux rôles. Il
  **restreint** l'affichage au rôle choisi et est **transmis au serveur
  puis vérifié** contre les autorités réellement détenues ; il
  **n'élargit jamais** les droits (Spring Security reste l'autorité à
  chaque appel). Le cumul de rôles ne donne jamais un accès transversal :
  un responsable également formateur ne voit pas les formations d'un
  autre responsable.
- **Navigation** : seules les entrées correspondant aux rôles du compte
  et au contexte actif apparaissent. Un accès direct à une URL interdite
  renvoie « Accès refusé » ; quand l'existence même de la ressource est
  sensible, la réponse est « introuvable » (`404`) plutôt que `403`.
- **Recherche globale** (en-tête) : apprenants, formateurs, classes,
  formations, salles, séances — **restreinte au périmètre** de
  l'utilisateur, résolu côté serveur. L'adresse e-mail n'est jamais un
  critère ; un fragment de moins de deux caractères est refusé.
- **PWA** : l'application est **installable** (bouton « Installer » dans
  l'en-tête). Hors ligne, application ouverte : consultation du planning,
  de l'assiduité et des notifications récents ; une action d'émargement
  faite hors ligne est **mise en file** et rejouée à la reconnexion, avec
  l'état explicite « en attente de confirmation » — jamais définitive
  avant validation serveur.

---

## 2. Rôles et écrans

`R/W` = lecture et écriture. « périmètre » = limité aux formations gérées
par le responsable pédagogique (ou aux classes des séances d'un
formateur), **décidé côté serveur**.

| Écran (route) | SUPER_ADMIN | ADMIN | SCHOOL_ADMIN | PEDAGO_MANAGER | TEACHER | STUDENT |
|---|:--:|:--:|:--:|:--:|:--:|:--:|
| Tableau de bord (`/dashboard`) | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Sécurité de mon compte (`/mon-compte/securite`) | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Notifications (`/notifications`, `/notifications/preferences`) | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Abonnement calendrier iCal (`/calendar-subscriptions`) | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| Recherche globale (en-tête) | ✔ | ✔ | ✔ | ✔ (périmètre) | ✔ (périmètre) | — |
| Administration des comptes (`/administration`) | ✔ (R/W) | ✔ (R/W) | ✔ (lecture + suspendre/réactiver) | — | — | — |
| Doublons (`/administration/duplicates`) | ✔ (comparaison, lecture seule) | ✔ (comparaison, lecture seule) | — | — | — | — |
| Invitations (`/invitations`, + « Non activées ») | ✔ | ✔ | — | — | — | — |
| Référentiel organisationnel (`/organization`) | ✔ (R/W) | ✔ (R/W) | — | — | — | — |
| QR fixe de salle — consulter / imprimer | ✔ | ✔ | ✔ | — | — | — |
| QR fixe de salle — renouveler / révoquer | — | ✔ | — | — | — | — |
| Matières (`/subjects`) | ✔ (R/W) | ✔ (R/W) | ✔ (lecture) | ✔ (R/W, périmètre) | ✔ (lecture) | — |
| Référentiels académiques (`/academic`) | ✔ (R/W) | ✔ (R/W) | ✔ (lecture) | ✔ (lecture, périmètre) | — | — |
| Alternance (`/alternation`) | ✔ (R/W) | ✔ (R/W) | ✔ (R/W) | ✔ (périmètre ; création de **modèle** : non) | — | — |
| Apprenants (`/students`) | ✔ (lecture) | ✔ (lecture) | ✔ (lecture) | ✔ (lecture, périmètre) | ✔ (lecture, classes de ses séances) | — |
| Ajouter un apprenant (`/students/nouveau`) | ✔ | ✔ | — | — | — | — |
| Import d'apprenants (`/students/import`) | ✔ | ✔ | ✔ | ✔ (son périmètre) | — | — |
| Planning — import / calendrier / versions (`/planning`) | ✔ | ✔ | — | ✔ (son périmètre) | — | — |
| Séances (`/sessions`) | ✔ (R/W) | ✔ (R/W) | ✔ (lecture) | ✔ (périmètre, R/W) | ✔ (ses séances **+ celles où il est remplaçant actif**) | — |
| Émargement (`/attendance`) | — | — | — | — | — | ✔ |
| Mes présences (`/my-attendance`, transparence, départs anticipés) | — | — | — | — | — | ✔ |
| Suivi d'assiduité (`/attendance-management`) | ✔ | ✔ | ✔ | ✔ (périmètre) | — | — |
| Réclamations (`/claims`) | ✔ | ✔ | ✔ | ✔ (périmètre) | ✔ (ses séances) | ✔ (les siennes) |
| Justificatifs — file d'examen | ✔ | ✔ | ✔ | ✔ (périmètre) | — | — |
| Attestations d'assiduité (`/attestations`) | ✔ | ✔ | ✔ | ✔ (périmètre) | — | — |
| Piste d'audit (`/audit`) | ✔ | ✔ | — | — | — | — |
| Effets de bord / file d'échec (`/exploitation/effets-de-bord`) | ✔ | ✔ | — | — | — | — |

> **Périmètre de `/students`.** Pour le `PEDAGOGICAL_MANAGER`, ce sont
> les apprenants inscrits (`ACTIVE`) dans les classes de ses formations ;
> pour le `TEACHER`, les apprenants des classes rattachées à ses séances
> (formateur principal ou remplaçant actif). Cumul de rôles = **union**
> des deux, jamais au-delà. Une fiche hors périmètre renvoie
> « introuvable » (`404`). Ces deux rôles n'ont que la **lecture** ;
> créer un profil, inscrire ou transférer un apprenant reste réservé à
> `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION`.

---

## 3. Parcours par rôle

### 3.1 `SUPER_ADMIN`

Mêmes écrans qu'`ADMIN` **plus** des gardes renforcées : seul un
`SUPER_ADMIN` peut administrer un compte portant le rôle `SUPER_ADMIN` ou
attribuer / retirer ce rôle. Le compte `SUPER_ADMIN` est **distinct** du
compte d'administration quotidienne (RG-003) — en démonstration,
`superadmin@example.test` ≠ `admin@example.test`.

- **Administration des comptes** (`/administration`) : suspendre /
  réactiver / archiver, attribuer / retirer un rôle (motif obligatoire).
  L'archivage clôture tous les rôles actifs. Auto-suspension,
  auto-archivage et retrait de son propre dernier rôle **interdits**
  (`USER_SELF_ACTION_FORBIDDEN`, `USER_LAST_ACTIVE_ROLE`).
- **Opérations de masse** : sélection multiple → **aperçu obligatoire**
  (nombre concerné, conséquences, comptes ignorés / refusés) → puis
  confirmation explicite. Sans confirmation, **rien n'est écrit**.
  L'exécution est isolée **par compte** (un compte protégé est reporté
  sans priver les autres).
- **Doublons** (`/administration/duplicates`) : voir §7.
- **QR fixe de salle** : consultation, impression **et** renouvellement /
  révocation… non — le **renouvellement et la révocation sont réservés à
  `ADMIN`** (`403` pour `SUPER_ADMIN` et `SCHOOL_ADMINISTRATION`). Le
  `SUPER_ADMIN` consulte et imprime.
- **Plages réseau CIDR** : **API uniquement** (pas d'écran).
- **Piste d'audit** (`/audit`), **file d'échec des effets de bord**
  (`/exploitation/effets-de-bord`) : voir §8.

### 3.2 `ADMIN`

- **Administration des comptes** : mêmes actions, **sauf** sur un compte
  `SUPER_ADMIN` (masqué + refusé côté serveur,
  `USER_SUPER_ADMIN_PROTECTED`).
- **Création de compte** (`/administration`) : crée un compte
  `PENDING_ACTIVATION` **de n'importe quel rôle** (formateur externe,
  admin, apprenant…) et émet son invitation. Aucun mot de passe n'est
  transmis. Adresse déjà utilisée → `409`.
- **Invitations** (`/invitations`) : statut d'envoi, statut de
  délivrabilité (jamais « délivré » du seul fait de la remise au serveur
  de messagerie), corriger l'adresse, **révoquer l'ancien jeton et en
  générer un nouveau**, relancer. Sous-écran « Non activées » : comptes
  en attente, dernière relance, jours d'attente (adresse **masquée**).
- **Référentiel organisationnel** (`/organization`) : sites, bâtiments,
  salles (R/W, archivage / restauration), **plages réseau** par API.
  Fiche de site → colonne **« QR fixe »** par salle : afficher le QR,
  l'émettre, ouvrir l'**affiche imprimable** (`room-qr-poster` — logo,
  code de salle, QR, instructions), copier l'**URL pour tag NFC**, et
  **renouveler** le QR (`ADMIN` seul, confirmation explicite — « toutes
  les affiches déjà posées deviennent immédiatement invalides »).
- **Référentiels académiques** (`/academic`), **matières** (`/subjects`),
  **alternance** (`/alternation`) : R/W dans les limites du rôle.
- **Apprenants** : `/students` (liste + recherche par nom ou numéro),
  `/students/nouveau` (enchaîne compte + invitation, **profil apprenant**,
  **inscription en classe** — parcours non atomique, reprise guidée),
  fiche apprenant (bloc « Scolarité actuelle », historique des
  inscriptions, suivi à distance).
- **Planning** : voir §5.
- **Séances**, **Suivi d'assiduité**, **Réclamations**, **Justificatifs**,
  **Attestations** : voir §5, §6.

### 3.3 `SCHOOL_ADMINISTRATION`

- **Administration des comptes** : consultation + **suspendre /
  réactiver** (pas d'archivage, pas de gestion des rôles).
- **Import d'apprenants**, **apprenants**, **référentiels académiques** :
  consultation ; **alternance** et **matières** : R/W.
- **QR fixe de salle** : consultation et impression (jamais
  renouvellement).
- **Séances** : **lecture seule**.
- **Suivi d'assiduité** : rapports séance / classe / apprenant /
  synthèse, exports **CSV / Excel / PDF**, **examen des justificatifs**,
  **génération d'attestations**, consultation des statistiques globales.
- **Réclamations** : guichet « administration scolaire » — traite et
  reçoit les réclamations transférées.
- Pas d'accès à la piste d'audit ni à la file d'échec.

### 3.4 `PEDAGOGICAL_MANAGER`

Tout est **borné à son périmètre** (formations qu'il gère). Un accès hors
périmètre renvoie `403` ou « introuvable » (`404`) selon la sensibilité.

- **Apprenants** (`/students`) : **lecture** des apprenants inscrits
  (`ACTIVE`) dans les classes de ses formations — recherche par nom /
  numéro, fiche complète (scolarité actuelle, historique **de ses
  classes**, suivi à distance). Une inscription antérieure dans la classe
  d'un autre responsable est **masquée** (isolation volontaire, cahier
  §18.3). Pas de création ni de transfert. La liste des inscriptions
  (`GET /enrollments`) suit le même périmètre.
- **Import d'apprenants** : sur ses classes ; une ligne visant une classe
  hors périmètre est marquée `IMP_CLASS_OUT_OF_SCOPE` et le job devient
  **non confirmable**. L'import se lance depuis l'en-tête de la liste des
  apprenants.
- **Référentiels académiques** : consultation de son périmètre.
  **Matières** : R/W sur son périmètre.
- **Alternance** : affectation d'un rythme à ses classes, exceptions
  individuelles de ses apprenants. **Création d'un modèle de rythme :
  non** (réservé à `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION`).
- **Planning** (`/planning`) : import CSV / Excel, calendrier interactif,
  correction ligne à ligne, publication versionnée, retour à une version
  antérieure — voir §5.
- **Séances** : création (issue du planning **ou** exceptionnelle),
  ouverture, fermeture, annulation motivée, **report**, décision sur une
  **demande d'annulation** d'un formateur, **remplacement daté**.
- **Suivi d'assiduité** : rapports de son périmètre, exports, **examen
  des justificatifs**, décision sur un **départ anticipé** transmis,
  **attestations** de son périmètre.
- **Réclamations** : guichet « responsable pédagogique » — traite,
  transfère à l'administration.
- **Tableau de bord** : taux par formation et par classe, retards,
  absences non justifiées, **justificatifs en attente de son périmètre**,
  **réclamations ouvertes de son guichet**, **comptes non activés de ses
  classes** (cartes complétées au sprint 11).
- **Non disponible dans l'UI** : l'affectation d'un responsable
  pédagogique à une formation se fait par l'API (`ADMIN` / `SUPER_ADMIN`)
  ou, en démonstration, par `scripts/seed-demo.sh`.

### 3.5 `TEACHER`

- **Séances** (`/sessions`) : ses propres séances **et celles où il est
  remplaçant actif**. Ouvrir / fermer (cycle strict `PLANNED → OPEN →
  CLOSED`, pas de réouverture), afficher le **QR dynamique** et le **code
  court**, suivre les présences en direct, **saisir une présence
  manuelle** motivée, **ajouter un apprenant provisoire**, saisir un
  **motif de retard**, enregistrer un **départ anticipé**, **corriger**
  une présence (motif obligatoire, historique append-only), exporter le
  CSV de la séance.
- **Demande d'annulation** : il **demande** (motif) ou **propose un
  remplaçant** — il ne décide jamais lui-même (`403` s'il tente) et ne
  valide jamais son propre remplacement.
- **Apprenants** (`/students`) : **lecture** des apprenants des classes
  rattachées à ses séances — recherche par nom / numéro, fiche. Fiche
  hors périmètre → `404`. Aucune écriture.
- **Réclamations** : répond aux réclamations liées à ses séances,
  transfère au responsable pédagogique.
- **Ne peut pas** : publier un planning, examiner un justificatif,
  accéder aux rapports agrégés, créer ou inscrire un apprenant.

### 3.6 `STUDENT`

- **Émargement** (`/attendance`) — plusieurs entrées, une seule autorité
  (le serveur) :
  - **Scanner un QR code** (bouton principal) : autoriser la caméra
    (arrière par défaut ; **HTTPS requis** ; accélération native
    `BarcodeDetector` si disponible, décodeur logiciel sinon). Viser le
    QR **dynamique** du formateur ou le **QR fixe** de la salle. Le code
    est vérifié en ligne, le résultat du serveur s'affiche. Bouton
    « Saisir un code court à la place » **toujours** présent ; à la
    fermeture du scanner, le focus revient sur le champ « Code court ».
  - **Code court** affiché par le formateur (champ de saisie, même durée
    de vie que le QR).
  - **QR fixe de salle** : utilisable **avant** le début du cours et
    **depuis le réseau de l'établissement uniquement** ; refusé à l'heure
    de début et au-delà, ou hors plage réseau.
  - Ouvrir un lien `/attendance?ref=…` (QR fixe **ou tag NFC** de salle
    lu par l'appareil photo système) **pré-remplit** le champ « QR de
    salle » ; **rien n'est envoyé sans validation**.
  - Erreurs : « code expiré / invalide » → demander un nouveau code ;
    « séance non ouverte » ; « présence déjà enregistrée pour ce point de
    contrôle » ; « vous n'êtes pas inscrit » ; « QR de salle non
    reconnu » ; « hors réseau de l'établissement » ; « la séance a
    commencé » ; « suivi à distance non autorisé » ; « ce QR n'est pas un
    code d'émargement ESIC Connect ».
- **Mes présences** (`/my-attendance`) : historique (présences réelles +
  absences **dérivées** d'un point de contrôle fermé), **taux
  d'assiduité**, dépôt et suivi d'un **justificatif** avec **pièce
  jointe** (PDF / JPEG / PNG, 5 Mo), modification tant qu'il est
  `PENDING`.
- **Journal de transparence** : compose à la lecture l'émargement,
  l'historique des corrections, le cycle des justificatifs et celui des
  départs anticipés. Les acteurs y sont désignés par leur **fonction**,
  jamais par leur nom.
- **Départ anticipé** (`/my-attendance/early-departures`) : signaler un
  départ au formateur (heure, motif) et suivre la décision.
- **Réclamations** (`/claims`) : créer, échanger, suivre les réponses,
  rouvrir une réclamation close.
- **Abonnement calendrier** : générer un flux **iCalendar** signé et
  révocable pour Outlook / Google / Apple. Le jeton n'est affiché
  **qu'une fois**.
- **Passkeys** : enregistrer et révoquer ses clés d'accès depuis
  « Sécurité de mon compte ».
- **Ne voit jamais** les données d'un autre apprenant.

---

## 4. Import de la population (`/students/import`)

Formats : **CSV** et **Excel `.xlsx`** (y compris **classeur
multifeuille** — une feuille par classe). Un CSV renommé `.xlsx` ou
l'inverse est **refusé** (type réel dérivé du contenu). Le fichier n'est
**jamais écrit sur disque** ; seule son empreinte est conservée. Plafond
2 Mo → `413`.

1. **Téléverser**. Colonnes de référence :
   `student_number` (facultatif), `last_name`, `first_name`, `email`,
   `phone`, `birth_date`, `formation_code`, `level_code`,
   `promotion_code`, `class_code`, `academic_year`, `work_study`,
   `work_study_pattern`, `company_name`.
2. **Simulation** (automatique) — **aucune donnée n'est créée**. Par
   ligne : l'action prévue (`création + inscription`, `inscription`,
   `mise à jour`, `transfert de classe`, `aucune`) et les **anomalies**
   avec gravité `INFO` / `WARNING` / `ERROR` / `BLOCKING` (fichier,
   feuille, ligne, colonne, valeur reçue, motif). Doublons intra-fichier
   **et** contre l'existant : un compte existant est **mis à jour**,
   jamais dupliqué.
3. **Correction ligne à ligne** dans l'écran de revue : modifier une
   valeur en anomalie **sans recommencer l'import**. La ligne et le lot
   sont revalidés immédiatement ; corriger la dernière ligne fautive rend
   le job confirmable. Chaque correction est tracée (auteur, avant,
   après). L'annulation puis le réimport restent possibles.
4. **Confirmation** — **une seule transaction** : création des comptes
   (`PENDING_ACTIVATION`), rôle `STUDENT`, inscription, **envoi des
   invitations**, rapport d'import, audit. Toute exception annule
   l'ensemble. Une confirmation déjà appliquée est **idempotente**.
   Numéro étudiant `ESIC-{année}-{NNNNN}` alloué atomiquement si le champ
   est vide.

Un job avec au moins une anomalie **BLOCKING** est **non confirmable**.
Jeux d'exemple : `docs/demo-data/` (voir `docs/demo-data/README.md`).

---

## 5. Planning et séances

### 5.1 Planning (`/planning`) — `ADMIN`, `PEDAGOGICAL_MANAGER` (périmètre)

- **Importer** (`/planning/import`) : CSV ou Excel `.xlsx`. Colonnes de
  référence : `academic_year`, `formation_code`, `promotion_code`,
  `class_code`, `group_code`, `session_date`, `half_day`, `start_time`,
  `end_time`, `course_code`, `course_name`, `teacher_email`, `room_code`,
  `attendance_mode`, `remote_link`, `work_study_exception`, `notes`. Le
  fichier n'est pas écrit sur disque.
- **Simulation** — lignes, anomalies et **conflits** (formateur / classe
  / **salle** sur tout l'établissement / plage horaire ; avertissement
  non bloquant si un créneau tombe sur une période résolue « entreprise »,
  ou si une séance est sans formateur). **Aucune séance n'est créée à ce
  stade.**
- **Correction ligne à ligne** dans l'écran de revue — même moteur de
  validation que la simulation ; corriger une ligne peut lever ou créer
  un conflit ailleurs.
- **Publication atomique versionnée** : crée la version N+1 et les
  séances (ou les réutilise — l'identité d'un créneau est **stable**
  entre deux publications) ; la version N passe en `SUPERSEDED`. Une
  ligne en erreur ou un **conflit bloquant** empêche la publication. Une
  publication concurrente est **idempotente**.
- **`/planning/versions`** : au moins **3 versions** conservées ; **retour
  à une version antérieure** — crée une nouvelle version N+1 dont le
  contenu est celui choisie, sans effacer l'historique. Refusé si un
  formateur n'est plus éligible ou si la version est vide.
- **Calendrier interactif** (`/planning/calendar`) : ajouter, déplacer,
  modifier, dupliquer une semaine, répéter un créneau, appliquer un
  rythme, enregistrer un brouillon, publier — mêmes contrôles de conflit.

### 5.2 Séances (`/sessions`)

- **Créer** : une séance **normale** provient d'un planning publié ; une
  séance **exceptionnelle** est créée par un responsable / administrateur
  avec classe(s) ou groupe, matière, formateur, date, horaires, salle ou
  lien, **motif obligatoire**, type d'exception. Une séance peut
  concerner **plusieurs classes**.
- **Cycle strict** : `PLANNED → OPEN → CLOSED`, **sans réouverture**.
  `PLANNED` / `OPEN → CANCELLED` avec motif (reste consultable, ne
  produit aucune absence).
- **Annulation / report** : le responsable annule ; le formateur **demande**
  (`REQUESTED → APPROVED / REJECTED`). Une séance annulée n'est pas
  reportée automatiquement — le responsable fixe une nouvelle date, ce
  qui crée une séance **liée** à l'originale.
- **Remplacement daté** : le responsable désigne un remplaçant sur une
  période, avec motif. Le formateur principal n'est **jamais** écrasé ;
  le remplaçant a les droits **uniquement pendant sa période**. Formateur
  initial et remplaçant sont **toujours** notifiés.
- **Modalité** : présentiel / distanciel collectif / distanciel
  individuel autorisé / hybride. Sans autorisation de suivi à distance
  active, le canal distant est refusé (`ATT_REMOTE_NOT_AUTHORIZED`).

### 5.3 Émargement et points de contrôle

- **Quatre points de contrôle nommés** : `MORNING_ARRIVAL`,
  `MORNING_BREAK_RETURN`, `AFTERNOON_ARRIVAL`, `AFTERNOON_BREAK_RETURN`
  (des points `CUSTOM` restent possibles pour les formats atypiques).
- **QR dynamique** : jeton **opaque** (aucune donnée personnelle), lié à
  une séance et à un point de contrôle. Le code visuel **change toutes
  les 10 secondes** ; le serveur accepte le code courant et, brièvement,
  le précédent. **Code court** : même durée de vie, mêmes vérifications.
- **Fenêtre** : ouvre 15 min avant le début, reste ouverte jusqu'à 15 min
  après dans le parcours standard. Au-delà : le QR fixe de salle n'est
  plus accepté ; le QR dynamique reste utilisable sous le contrôle du
  formateur ; une présence tardive peut être enregistrée
  exceptionnellement.
- **Paliers de retard** (configurables) : 0–15 min → `PRESENT` ;
  16–30 min → `LATE` ; au-delà de 30 min → `LATE` **et validation
  manuelle requise**.
- **Anti-rejeu** : une seconde validation du même point de contrôle par
  le même apprenant est refusée **sans effet de bord**.
- **Présence manuelle** : motif, canal, heure, auteur — auditée.
- **Apprenant provisoire** (`UNREGISTERED_GUEST` / `PENDING_REGISTRATION`) :
  signalé au responsable, à régulariser ; n'entre dans **aucun** calcul
  d'assiduité tant qu'il n'est pas rattaché.
- **Correction** : ancienne valeur, nouvelle, motif, auteur, date,
  origine. Historique **append-only** ; aucune présence supprimée,
  seulement annulée logiquement. L'auteur d'une correction est **exposé**
  (nom + fonction pour le personnel, fonction seule pour l'apprenant).
- **Redis indisponible** → `503` : **aucune validation dégradée**.

### 5.4 Suivi d'assiduité (`/attendance-management`)

Coquille à navigation secondaire : **Synthèse**, **Par classe**, **Par
apprenant**, **Justificatifs**, corrections. La mesure est en
**demi-journées** (deux demi-journées validées = une journée). Une
**période en entreprise n'est jamais comptée comme une absence** quand un
rythme d'alternance est affecté ; un jour d'alternance « école » sans
séance publiée **ne dégrade pas** le taux (rollup corrigé le 9 septembre
2026 — ANO-UX-007, regroupement par (inscription, jour), aligné sur le
rapport journalier canonique).

Rapports : journalier de classe, hebdomadaire / mensuel, annuel,
individuel, par formation / matière / formateur, anomalies, réclamations,
invitations non activées. Exports **CSV** (UTF-8 + BOM, `;`, injection de
formule neutralisée), **Excel `.xlsx`**, **PDF** (identité visuelle,
identifiant de document). Chaque graphique est doublé d'un **tableau
équivalent** avec la valeur en toutes lettres.

---

## 6. Justificatifs, réclamations, attestations

### 6.1 Justificatifs

- **Dépôt** par l'apprenant (ou le formateur pour son compte, le
  responsable, l'administration) : concerne une séance, une demi-journée,
  une journée ou une période. Modifiable tant que `PENDING`.
- **Pièce jointe** : **un** fichier PDF / JPEG / PNG, **5 Mo** max.
  Contrôles : extension + type MIME + **type réel dérivé du contenu**
  (magic bytes — un ZIP ou un exécutable renommé `.pdf` est refusé).
  Stockage **hors base et hors répertoire public**, téléchargement forcé
  `Content-Disposition: attachment` + `nosniff`, accessible **au seul
  propriétaire et à un examinateur de son périmètre** (sinon
  « introuvable »).
- **Analyse antivirus** : port ClamAV **activable par configuration** —
  **inactive par défaut**. Une pièce non analysée n'est **jamais**
  présentée comme saine ; tant que le verdict n'est pas rendu, la pièce
  est en quarantaine.
- **Cycle** : `SUBMITTED → UNDER_REVIEW → ACCEPTED / REJECTED /
  ADDITIONAL_INFORMATION_REQUIRED / EXPIRED`. Un **refus exige un motif**.
  Un justificatif accepté transforme `ABSENT` en `EXCUSED` — il n'efface
  **jamais** l'historique de l'absence et ne crée jamais une présence.
- **Limites** : une seule pièce active par justificatif ; pas de
  remplacement direct (retirer puis redéposer).

### 6.2 Réclamations (`/claims`)

Échange conversationnel encadré adressé à un **guichet** (formateur,
responsable pédagogique, administration scolaire) — jamais à une
personne, ce qui rend le **transfert** possible. Fil de messages
**append-only** (rôle figé à l'écriture), décisions dans une table
distincte. Statuts : `OPEN`, `IN_PROGRESS`, `WAITING_FOR_STUDENT`,
`TRANSFERRED`, `RESOLVED`, `CLOSED`, `REJECTED`, `REOPENED`. Une
réclamation close peut être **rouverte** (motif, message, notification,
audit). La réclamation d'autrui répond « introuvable » (`404`).

### 6.3 Attestations d'assiduité (`/attestations`)

Émission pour un apprenant sur une période, par un **acteur autorisé**
(administration, responsable pédagogique de son périmètre). L'attestation
porte un **identifiant vérifiable** `ESIC-ATT-<année>-<10 caractères>`
inscrit au registre, l'émetteur et l'auteur, et la mesure **en
demi-journées et journées équivalentes**. Le PDF **n'est pas conservé**
(reproductible depuis les données) — une **empreinte SHA-256** l'est. La
route de vérification ne renvoie **aucune donnée d'assiduité**.

---

## 7. Doublons de comptes (`/administration/duplicates`) — `ADMIN` / `SUPER_ADMIN`

Les comptes rapprochés par **nom normalisé** (accents / casse
neutralisés) ou **numéro de téléphone** sont listés. Cocher **exactement
deux** comptes → « Comparer » : une **comparaison côte à côte en lecture
seule** s'ouvre **dans le flux de la page** (aucune fenêtre modale) —
concordances, différences, conflits bloquants, avertissements, volume de
données rattaché (inscriptions, présences, justificatifs, réclamations,
notifications, invitations, passkeys, appareils) et un **verdict
informatif** : « Fusion potentiellement sûre », « Revue manuelle
requise » ou « Fusion impossible ».

**Aucune fusion n'est réalisée.** Ce parcours s'arrête à la comparaison ;
le bouton « Fusionner » est présent mais **désactivé** (décision porteur).
La comparaison ne renvoie jamais de hachage de mot de passe, secret MFA,
code de récupération, jeton d'invitation ni structure de passkey.

---

## 8. Notifications, audit, effets de bord

### 8.1 Centre de notifications (`/notifications`)

La **cloche** de l'en-tête affiche le nombre de non lues. L'écran liste,
filtre (toutes / non lues), marque comme lu, ouvre la ressource
concernée. **Audience réelle** (résolue **après le commit** de la
transaction métier) : une annulation de séance prévient le formateur, ses
remplaçants, **les apprenants attendus** et **le responsable pédagogique
du périmètre** ; une décision de justificatif prévient le propriétaire ;
une réclamation prévient les participants du fil **et le guichet
courant**. Un **remplacement** ne réveille pas la classe (choix assumé).

- **Canaux** : in-app, **e-mail** ; **push PWA partielle** (chiffrement
  RFC 8291 vérifié, aucun service de push réel sollicité — sans clés
  VAPID l'API déclare `providerActive: false`).
- **Préférences** (`/notifications/preferences`) : par catégorie et par
  canal. Le centre in-app et la catégorie `SECURITY` ne se désactivent
  pas (verrouillés à l'écran comme côté serveur).

### 8.2 Piste d'audit (`/audit`) — `ADMIN` / `SUPER_ADMIN`

Consultation et **export** (CSV / Excel / PDF) de la piste d'audit :
filtres fermés (période, action, catégorie, type de ressource, résultat,
acteur, corrélation), pagination bornée. **Aucune route d'écriture ni de
suppression** — l'audit ne peut être ni modifié ni effacé. Il ne contient
jamais de mot de passe, de secret, de jeton complet, de donnée
biométrique ni d'**adresse IP**. L'apprenant dispose de son **journal de
transparence** (§3.6), à la bonne granularité pour lui.

### 8.3 File d'échec des effets de bord (`/exploitation/effets-de-bord`) — `ADMIN` / `SUPER_ADMIN`

Tout effet de bord externe (e-mail, notification, audit, MQTT) passe par
une **outbox transactionnelle** : jamais perdu par une panne du
diffuseur, jamais produit si la transaction métier est annulée. L'écran
liste ce qui a échoué **et pourquoi** (destinataire, type, tentatives,
dernière erreur), et permet un **rejeu manuel** (refusé hors file
d'échec). Le **contenu** du message n'est jamais réaffiché.

---

## 9. Fonctions non disponibles dans l'interface

> Source de vérité : `docs/STATUS.md` §3 (partiels) et §4 (non
> implémenté).

**Livré, mais sans écran dédié** (API uniquement) :

- affectation d'un formateur à une association classe–matière–période
  (`EF-TEA-002`) ;
- affectation d'un responsable pédagogique à une formation ;
- création / transfert / clôture d'inscription **hors** import (mais la
  fiche apprenant et `/students/nouveau` couvrent l'essentiel) ;
- configuration des **plages réseau CIDR** ;
- **résultat journalier** d'assiduité `GET /api/v1/attendance/reports/daily`
  (`EF-ATT-004`) — le calcul est livré et testé, aucun écran ne l'expose.

**Partiel** :

- **push PWA réelle** (`EF-NOTIF-005`) — aucun service de push réel
  sollicité ;
- **consultation hors ligne au démarrage à froid** (`EF-PWA-002`) — le
  jeton ne vit qu'en mémoire ;
- **Microsoft Graph / Teams / calendrier réel** (`EF-INT-002/003`) —
  ports et adaptateurs écrits et testés, **aucun locataire Microsoft
  réel** ; l'API déclare `meetingActive: false` ;
- **fournisseur d'e-mail réel** — Mailpit en local ; en production, la
  remise en boîte dépend d'un expéditeur validé côté fournisseur.

**Non implémenté (perspective — sprints 12-13)** :

- détection de conflits / incohérences de salle **hors** planning
  (`EF-ORG-004`) ;
- mapping d'import assisté par IA (`EF-IMP-005`) ; planning **PDF texte**
  + mapping IA (`EF-PLAN-012/013`) ;
- confirmation locale d'un émargement par **WebAuthn** (`EF-ATT-011`) ;
  **borne connectée MQTT** + simulateur (`EF-ATT-016`, `EF-IOT-001..005`) ;
- **rapport des anomalies** d'émargement (`EF-REP-009`) ;
- **service d'IA** complet (`EF-AI-001..005`) — mapping de colonnes,
  score d'anomalie, prévention du décrochage ;
- fournisseur d'e-mail réel de production (`EF-INT-004`) ;
- **RGPD avancé** : export, rectification, purge / anonymisation
  (`EF-RGPD-001..003`) ;
- **exploitation complète** : métriques et journaux structurés
  corrélés, **restauration de sauvegarde prouvée**, CI/CD de bout en
  bout, OpenAPI publiée et versionnée (`EF-OPS-001..004`).

---

## 10. Comptes de démonstration

Le profil `demo` amorce six comptes fictifs (`DemoDataInitializer`). Le
second facteur des comptes privilégiés est réellement exigé ; en
démonstration il est franchi via un secret TOTP déterministe
(`ESIC_DEMO_TOTP_SECRET`, **variable d'environnement uniquement, jamais
committée**). Voir `docs/11-guide-deploiement.md` et
`docs/demo-data/README.md`.

| Compte | Rôle(s) |
|---|---|
| `superadmin@example.test` | `SUPER_ADMIN` |
| `admin@example.test` | `ADMIN` |
| `responsable@example.test` | `PEDAGOGICAL_MANAGER` + `TEACHER` |
| `formateur@example.test` | `TEACHER` |
| … | `STUDENT` (apprenants du jeu de données) |

> Il n'existe pas de compte de démonstration `SCHOOL_ADMINISTRATION` : sa
> matrice de droits est couverte par les tests d'intégration
> (`*SecurityTests`), pas par un parcours navigateur.
