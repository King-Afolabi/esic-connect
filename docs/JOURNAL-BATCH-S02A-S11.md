# Journal d'exécution — lot S2A → S11

> **Nature de ce document.** Un relevé, sprint par sprint, de ce qui a été
> réellement livré, testé et documenté pendant l'exécution du lot
> `batch/S02A-S11`. Il ne remplace pas `docs/CURRENT-STATE.md`, qui reste
> la seule source de vérité sur l'état du dépôt : il en donne l'historique.
>
> Aucun chiffre de test n'est reporté ici sans avoir été produit par une
> commande exécutée. Aucun jalon n'est posé sans que l'incrément
> correspondant soit présent, testé et documenté.

## Référence d'entrée

| Élément | Valeur |
|---|---|
| Branche de coordination | `batch/S02A-S11` |
| Base | `f0d02d4` sur `feature/produit-complet-v2` |
| État initial mesuré | back-end **858 tests / 0 échec**, front-end **617 tests / 0 échec** |
| Commande de mesure | `cd backend && ./mvnw clean test` ; `cd frontend && npm test -- --watch=false` |

---

## S2 — Sécurité forte (`sprint/S02-securite`)

### Ce qui existait déjà (vérifié avant d'écrire une ligne)

`EF-AUTH-005` mot de passe oublié, `EF-AUTH-012` limitation de débit,
`EF-AUTH-014` déconnexion et révocation, migration `V17`, décision
`DEC-S2-003`. Rien de tout cela n'a été réécrit.

### Ce qui a été livré

| Exigence | Contenu |
|---|---|
| `EF-AUTH-006` | passkeys WebAuthn : options d'enregistrement, vérification d'attestation, liste, révocation individuelle |
| `EF-AUTH-007` | connexion sans mot de passe par passkey, défi à usage unique, compteur de signature contrôlé |
| `EF-AUTH-008` | second facteur TOTP (RFC 6238) : enrôlement, confirmation, vérification, anti-rejeu du pas de temps |
| `EF-AUTH-009` | dix codes de récupération à usage unique, régénération invalidant les anciens |
| `EF-AUTH-010` | authentification adaptative : appareil inconnu → second facteur ; appareil reconnu → reconnexion allégée, jamais pour un rôle privilégié |
| `EF-AUTH-011` | anti-robot Turnstile derrière un port, vérification **serveur**, adaptateur local documenté, politique de repli `DEC-S2-004` |
| `EF-AUTH-013` | appareils de confiance : empreinte seule, durée bornée, liste et révocation, isolation entre comptes |
| `EF-AUTH-015` | réauthentification forte exigée avant un changement de rôle (claim `amr`) |

### Critères d'acceptation couverts

| Critère | Test |
|---|---|
| `AC-020` — aucune donnée biométrique reçue | `WebAuthnIntegrationTests` |
| `AC-021` — second facteur exigé pour `SUPER_ADMIN` / `ADMIN` | `MfaIntegrationTests` |
| `AC-022` — contrôle renforcé après trois échecs | `CaptchaIntegrationTests` |
| `AC-023` — réponse neutre à la demande de réinitialisation | déjà couvert par `PasswordResetIntegrationTests` (S2 partie A) |

### Migration

`V18__create_strong_authentication_tables.sql` — `mfa_credential`,
`mfa_recovery_code`, `webauthn_credential`, `trusted_device`.

### Décisions

`DEC-S2-004` repli anti-robot, `DEC-S2-005` enrôlement forcé pendant la
connexion, `DEC-S2-006` portée d'un appareil de confiance. Consignées
dans `docs/03-architecture.md`.

### Effet de bord assumé sur la suite de tests

Rendre le second facteur réellement obligatoire change le contrat de
`POST /auth/login` : un compte privilégié n'obtient plus de jeton contre
son seul mot de passe. Trente-quatre classes de test obtenaient leur
jeton ainsi. Elles passent désormais par `AuthTestSupport`, qui franchit
le défi comme le ferait un utilisateur — **aucun raccourci, aucun profil
dérogatoire, aucune porte dérobée de test**.

`AuthRateLimitIntegrationTests` a été rendu autonome : il remet à zéro
les compteurs indexés sur l'origine (`127.0.0.1`), partagés par toute la
suite. Ce couplage préexistait ; il devenait visible dès que le nombre de
connexions augmentait.

### Limites restantes, explicitement assumées

- La **cérémonie WebAuthn complète** n'est pas rejouée en test : elle
  exige un authentificateur qui signe réellement. Les tests couvrent le
  contrat, le cycle de vie du défi, l'isolation et l'absence de donnée
  biométrique ; la vérification cryptographique est celle de la
  bibliothèque. Une signature « simulée » ne prouverait rien.
- Turnstile n'est **pas vérifié contre le service réel** : aucune clé
  secrète n'est configurée dans le dépôt. Sans clé, le produit déclare
  franchement, via `GET /api/v1/auth/captcha`, qu'aucun contrôle n'est
  actif — il n'en simule pas un.
- Les passkeys exigent un contexte sûr : elles fonctionnent sur
  `localhost`, et exigeront **un domaine et HTTPS** hors du poste de
  développement.

---

## S3 — Population et invitations (`sprint/S03-population`)

### Ce qui existait déjà (vérifié avant d'écrire une ligne)

`EF-ENR-001..003` profils, inscriptions et changement de classe
historisé ; `EF-IMP-001/002` simulation et confirmation atomique de
l'import CSV. Rien de tout cela n'a été réécrit.

### Ce qui a été livré

| Exigence | Contenu |
|---|---|
| `EF-ACA-006` | matières : CRUD, archivage, rattachement multi-formations contrôlé par périmètre, code immuable |
| `EF-ACA-007` | groupes temporaires : membres issus de classes différentes, rattachés à l'inscription, retrait logique |
| `EF-USER-001` | `POST /api/v1/users` — compte `PENDING_ACTIVATION` + invitation, sans aucun champ de mot de passe |
| `EF-TEA-001` | formateur externe : même route, adresse de n'importe quel domaine, jamais critère de confiance |
| `EF-USER-007` | suivi des invitations et réémission révoquant le jeton précédent |
| `EF-USER-008` | journal de délivrabilité séparant strictement « remis au serveur » et « délivré » |

### Critères d'acceptation

`AC-004` à `AC-006` étaient déjà couverts par la suite d'import livrée
avant ce lot (`StudentImportRecetteTests`,
`EnrollmentIntegrationTests`) : ils n'ont pas été réécrits, et le sprint
n'a rien modifié qui les remette en cause.

### Migration

`V19__create_subjects_groups_and_email_delivery.sql` — `subject`,
`subject_program`, `student_group`, `student_group_member`,
`email_delivery`.

### Décisions

`DEC-S3-001` — les groupes temporaires vivent dans `enrollment`, pas dans
`academic` : un membre de groupe est une inscription, et loger le groupe
dans `academic` créerait un cycle `academic → enrollment` alors que
l'inverse existe déjà. Les ports publics `AcademicReferenceDirectory` et
`SubjectDirectory` ont été ajoutés pour que `enrollment` résolve
formation, année et matière sans importer l'interne d'`academic`.

`DEC-S3-002` — le journal de délivrabilité ne stocke pas l'adresse en
clair : empreinte pour le rapprochement, forme masquée pour l'affichage.
Un responsable doit pouvoir repérer une faute de frappe ; la table ne
doit pas constituer un annuaire exploitable en cas de fuite.

### Vérifications

| Commande | Résultat |
|---|---|
| `cd backend && ./mvnw test` | 112 classes / **959 tests** / 0 échec |
| `cd frontend && npm test -- --watch=false` | 78 fichiers / **645 tests** / 0 échec |
| `cd frontend && npm run lint` | « All files pass linting » |
| `cd frontend && npm run build` | bundle produit, aucune alerte de budget |

### Limites restantes, explicitement assumées

- `EF-TEA-002` reste **partiel** : l'API d'affectation pédagogique existe
  depuis le sprint 1, mais aucun écran ne permet encore de composer une
  association classe–matière–période. Le sprint 6 en a besoin ; il est
  logé là.
- Mailpit ne remonte **aucun** retour de délivrabilité : le statut
  fournisseur reste `UNKNOWN` en développement, et l'interface le dit
  franchement plutôt que d'afficher un « délivré » sans preuve. Le
  raccordement à un fournisseur réel est `EF-INT-004`, sprint 13.
- Les écrans livrés ici ne sont pas encore couverts par la recette
  navigateur : `NOT_PERFORMED` pour ces parcours.

---

## S4 — Excel, opérations de masse, doublons (`sprint/S04-excel-alternance`)

### Ce qui existait déjà (vérifié avant d'écrire une ligne)

`EF-ACA-009` — rythmes d'alternance, affectations historisées,
exceptions individuelles, résolution `SCHOOL` / `COMPANY` / `UNKNOWN` —
était déjà livré et testé avant ce lot. Le sprint ne l'a pas réécrit et
n'y a rien changé.

### Ce qui a été livré

| Exigence | Contenu |
|---|---|
| `EF-IMP-003` | import `.xlsx` ; `.xls` refusé ; type réel dérivé du contenu ; conversion de cellule prévisible |
| `EF-IMP-004` | classeur multifeuille ; feuille divergente signalée et écartée ; feuille d'origine conservée par ligne |
| `EF-IMP-006` | correction de ligne avant confirmation, rejouant la validation et recalculant la synthèse |
| `EF-USER-004` | opérations de masse avec prévisualisation obligatoire |
| `EF-USER-005` | détection de doublons par nom normalisé ou téléphone, sans fusion ni suppression |

### Défaut réel découvert et corrigé

L'unicité `(travail, ligne)` de `V11` supposait un fichier plat : dans un
classeur, la ligne 2 existe dans chaque feuille, et la deuxième feuille
échouait sur une violation d'unicité avec un `500`. La clé devient
`(travail, feuille, ligne)` (`DEC-S4-001`). La détection de doublons
intra-fichier, qui indexait sur le seul numéro de ligne, a suivi.

Un second défaut de conception a été corrigé de la même façon : envelopper
une opération de masse dans une transaction unique faisait échouer le lot
entier dès qu'un compte était refusé — un refus remontant d'une méthode
`@Transactional` imbriquée marque la transaction englobante
`rollback-only`. L'exécution est désormais isolée par compte
(`DEC-S4-002`).

### Migration

`V20__extend_student_import_for_workbooks_and_corrections.sql` —
`student_import_row.sheet_name`, unicité `(job, feuille, ligne)`,
`student_import_row_correction`.

### Vérifications

| Commande | Résultat |
|---|---|
| `cd backend && ./mvnw test` | 115 classes / **980 tests** / 0 échec |
| `cd frontend && npm test -- --watch=false` | 78 fichiers / **645 tests** / 0 échec |
| `cd frontend && npm run lint` | « All files pass linting » |
| `cd frontend && npm run build` | bundle produit, aucune alerte de budget |

### Limites restantes, explicitement assumées

- La détection de doublons est **conservatrice** : nom complet normalisé
  ou téléphone. Elle ne rapproche pas deux personnes ayant changé de nom,
  et n'utilise pas la date de naissance — souvent absente. C'est un choix
  assumé : un faux positif coûte plus cher qu'un faux négatif quand la
  suite possible est une suppression.
- La correction de ligne ne rejoue pas la détection de doublons
  intra-fichier, qui porte sur l'ensemble du lot. Une correction créant un
  doublon est rattrapée à la confirmation, qui revalide tout sous verrou —
  c'est là que se prend la décision d'écrire.
- Aucun écran d'opération de masse : l'API est livrée et testée, le
  bouton reste à faire. `EF-USER-004` est donc `IMPLEMENTED_AND_TESTED`
  côté serveur, sans interface.

---

## S5 — Planning : import et publication (`sprint/S05-planning-publication`)

### Ce qui existait déjà (vérifié avant d'écrire une ligne)

`EF-PLAN-001/002` import CSV borné et simulation sans création de séance,
`EF-PLAN-004/005/007` publication atomique versionnée avec idempotence
stricte, `EF-SES-001` création des séances depuis le planning publié, et
les critères `AC-007` / `AC-008`. Rien de tout cela n'a été réécrit ; le
sprint n'a traité que les **deux exigences restées partielles**.

### Ce qui a été livré

| Exigence | Contenu |
|---|---|
| `EF-PLAN-003` | correction ligne à ligne dans l'écran de revue, réanalysant tout le lot |
| `EF-PLAN-009` | conflit de **salle** contre les séances déjà publiées, à l'échelle de l'établissement |

### Le défaut que le sprint corrige

Le planning transportait `room_code` de bout en bout — colonne d'import,
entrée de planning, commande de publication — mais **la séance créée ne
le conservait pas**. Le contrôle de conflit de salle ne pouvait donc
s'exercer qu'à l'intérieur d'un même fichier : deux imports successifs
pouvaient placer deux classes dans la même salle à la même heure sans que
rien ne le signale, alors que le cahier demande explicitement de le
détecter (§13.5).

### Effet de bord assumé sur la suite de tests

Rendre le conflit de salle établissement-wide a révélé que plusieurs
fixtures réutilisaient « A1 » pour des classes différentes — ce qui est
désormais, à juste titre, un conflit. Six classes de test tirent
maintenant un code de salle unique par cas. Le code reste constant à
l'intérieur d'un cas : le comparer d'une version à la suivante est
précisément ce que certains mesurent.

### Migration

`V21__add_room_code_to_course_session.sql` — colonne `room_code` et index
`(room_code, starts_at)`.

### Décisions

`DEC-S5-001` (la séance conserve sa salle ; le conflit est
établissement-wide) et `DEC-S5-002` (corriger une ligne de planning
réanalyse tout le lot, les conflits étant croisés).

### Vérifications

| Commande | Résultat |
|---|---|
| `cd backend && ./mvnw test` | 116 classes / **990 tests** / 0 échec |
| `cd frontend && npm test -- --watch=false` | 78 fichiers / **645 tests** / 0 échec |
| `cd frontend && npm run lint` | « All files pass linting » |
| `cd frontend && npm run build` | bundle produit, aucune alerte de budget |

### Limites restantes, explicitement assumées

- Le conflit de salle compare des **codes**, sans clé étrangère vers
  `room` : le cahier prévoit qu'une salle soit laissée indéterminée puis
  affectée plus tard (`RG-044`). Deux orthographes différentes d'une même
  salle ne seraient donc pas rapprochées.
- La capacité de salle n'est pas contrôlée : `EF-ORG-004` mentionne aussi
  « une capacité insuffisante », qui reste à faire.

---

## S6 — Planning avancé et séances (`sprint/S06-planning-seances`)

### Ce qui existait déjà (vérifié avant d'écrire une ligne)

- `EF-SES-009` (séance multi-classes) était **déjà implémenté** :
  `CourseSession` porte une collection `SessionClass` et l'API accepte
  `classPublicIds`. `CURRENT-STATE.md` le listait à tort comme absent —
  le dépôt a raison, le document a été corrigé.
- Le versionnement (`EF-PLAN-005/007`) et la publication atomique
  (`EF-PLAN-004`) étaient livrés au sprint 5 : le retour arrière s'y
  greffe, il ne les réécrit pas.
- L'annulation d'une séance avec motif (`EF-SES-004`) existait : le
  report et la demande d'annulation s'y appuient.

### Ce qui a été livré

| Exigence | Contenu |
|---|---|
| `EF-PLAN-006` | construction directe dans un calendrier : ajout, modification, déplacement, suppression, duplication d'une semaine, répétition d'un créneau, brouillon, publication |
| `EF-PLAN-008` | retour à une version antérieure — crée une version **N+1** dont le contenu est celui de la version choisie (AC-009) |
| `EF-PLAN-010` | avertissement **non bloquant** quand un créneau tombe sur une période résolue en entreprise |
| `EF-PLAN-011` | import de planning Excel `.xlsx`, type réel dérivé du contenu |
| `EF-SES-007` | report d'une séance annulée : crée une séance liée, l'originale reste `CANCELLED` et consultable |
| `EF-SES-008` | demande d'annulation par le formateur, décidée par le responsable ; retrait possible tant qu'elle n'est pas décidée |

### Le choix structurant du sprint

**Le calendrier interactif n'est pas un second modèle de planning.** Un
créneau saisi à la main devient une ligne d'un travail d'import ordinaire,
et chaque mutation rejoue `PlanningSimulationService.revalidate` sur le lot
entier. La publication reste `POST /planning-imports/{id}/publish` :
mêmes conflits, même verrou, même versionnement atomique.

La raison n'est pas l'économie de code, c'est la sûreté : le cahier exige
que « les mêmes contrôles de conflit s'appliquent » (§13.7). Un moteur de
conflits dupliqué pour le calendrier finirait par diverger de celui de
l'import, et la divergence ne se verrait qu'au moment où deux classes se
retrouveraient dans la même salle.

### Défaut réel découvert et corrigé

`postpone` et `decideCancellation` portaient `MANAGE_ROLES`, qui inclut
`TEACHER`. C'est correct pour ouvrir et clore une séance — c'est faux
pour reporter (cela crée une séance) et pour décider d'une annulation :
« le formateur ne valide jamais lui-même » (RG-024, docs/02 §5.6). Les
deux routes portent désormais `CREATE_ROLES`. Un test vérifie qu'un
formateur reçoit `403` sur sa propre demande.

Le lien de report n'était par ailleurs pas exposé : la séance annulée
portait `postponed_to_session_id` en base sans que l'API le montre, alors
que le cahier veut que l'originale « reste consultable en historique » en
portant le lien. `CourseSessionResponse` expose maintenant
`postponedToPublicId`, résolu par une lecture supplémentaire **uniquement**
lorsque la séance a réellement été reportée.

### Migrations

- `V22__create_session_postponement_and_cancellation_requests.sql` — lien
  de report et table `session_cancellation_request`.
- `V23__allow_calendar_built_planning_drafts.sql` — la contrainte V12
  `file_size_bytes > 0` devient `>= 0` : un planning saisi au calendrier
  n'a pas de fichier. Un contenu vide reste refusé bien en amont par les
  gardes CSV et classeur. V12 n'est pas modifiée.

### Décisions

`DEC-S6-001` (le calendrier réutilise le pipeline d'import plutôt que de
dupliquer le moteur de conflits) et `DEC-S6-002` (le retour arrière crée
une version N+1 et refuse si un formateur n'est plus éligible, plutôt que
de restaurer un planning impubliable).

### Vérifications

| Commande | Résultat |
|---|---|
| `cd backend && ./mvnw test` | 118 classes / **1019 tests** / 0 échec |
| `cd frontend && npm test` | 79 fichiers / **653 tests** / 0 échec |
| `cd frontend && npm run lint` | « All files pass linting » |
| `cd frontend && npm run build` | bundle produit, aucune alerte de budget |

### Limites restantes, explicitement assumées

- Le calendrier saisit un formateur par son **identifiant public**, sans
  sélecteur : l'écran reste utilisable mais peu confortable. Le
  rapprochement d'un formateur par nom relève de l'assistance IA
  (`EF-IMP-005` / `EF-PLAN-013`, sprint 12).
- La répétition d'un créneau est fixée à trois occurrences hebdomadaires
  depuis l'écran ; l'API accepte n'importe quelles valeurs. Le formulaire
  de paramétrage reste à faire.
- `EF-PLAN-012` (planning PDF texte) et `EF-PLAN-013` (mapping assisté)
  restent au sprint 12, conformément à la roadmap.
- Un seul brouillon de calendrier par classe **et par auteur** : deux
  responsables construisant simultanément le même planning travailleraient
  chacun sur le sien et la seconde publication supersèderait la première.
  Le comportement est cohérent avec le versionnement, mais aucun
  avertissement ne signale l'autre brouillon.

---

## S7 — Émargement : modalité et suivi à distance (`sprint/S07-emargement`)

### Ce qui existait déjà (vérifié avant d'écrire une ligne)

Le sprint 7 de la roadmap porte sur l'émargement nominal. La quasi-totalité
en était **déjà livrée** : `EF-ATT-001` (QR dynamique), `EF-ATT-002`
(validation par jeton), `EF-ATT-006` et `EF-ATT-012` (présence manuelle,
correction auditée), `EF-ATT-009` (code court), `EF-ATT-015` (suivi en
direct), `EF-SES-002` et `EF-SES-003` (ouverture, clôture).

Une seule exigence du sprint restait entière : **`EF-ENR-004`** — autoriser
un suivi à distance individuel. Rien n'a été réécrit de ce qui fonctionnait.

### Ce qui a été livré

| Exigence | Contenu |
|---|---|
| `EF-ENR-004` | autorisation de suivi à distance : octroi, révocation, historique, contrôle de périmètre |
| docs/02 §15.1–15.4 | modalité d'enseignement d'une séance (`ON_SITE` / `REMOTE` / `HYBRID`) et lien distant |
| docs/02 §15.4 | canaux `REMOTE_QR` et `REMOTE_CODE` enregistrés distinctement |

La règle du cahier est appliquée telle qu'écrite : « sans autorisation, le
canal distant est refusé ». Sur une séance présentielle, un émargement
déclaré à distance exige une autorisation active couvrant le jour de la
séance et la classe concernée — sinon `403 ATT_REMOTE_NOT_AUTHORIZED`. Sur
une séance `REMOTE` ou `HYBRID`, aucune autorisation individuelle n'est
demandée : la classe est déjà attendue à distance.

### Ce que ce mécanisme n'est pas

Le drapeau `remote` est une **déclaration de l'apprenant**, pas une preuve
de localisation : un client pourrait ne pas le lever. Le contrôle réel de
la présence sur site est le QR fixe de salle associé à la plage réseau de
l'établissement (`EF-ATT-008` / `EF-ATT-010`, sprint 8). Ce que le
mécanisme garantit aujourd'hui, c'est qu'une décision pédagogique existe,
qu'elle est datée, motivée, révocable et tracée — et que le canal employé
figure dans la présence enregistrée.

Cette limite est écrite dans le code (javadoc de `AttendanceRecordSource`
et de la requête de validation) plutôt que laissée à l'interprétation.

### Choix de modélisation

La portée « une séance / une période / l'année » du cahier (§15.3) est
exprimée par un **intervalle de dates**, non par une énumération : une
autorisation d'une seule séance est un intervalle d'un jour. Le calcul de
couverture reste ainsi unique, là où trois portées distinctes auraient
produit trois chemins à maintenir — et à faire diverger.

Une autorisation **générale** (sans classe) est réservée à un périmètre
global : accordée par un responsable pédagogique, elle vaudrait pour les
classes d'un autre.

### Défaut réel découvert et corrigé

`JustificationAttachmentIntegrationTests.reconciliationPromotesAnAgedPendingRowWhoseFileIsValid`
échouait dans la suite complète mais passait isolément. Cause :
`reconcile()` ne traite qu'un **lot borné** (100) de lignes
`PENDING_STORAGE` vieillies, les plus anciennes d'abord ; la base de test
n'étant jamais remise à zéro, les résidus accumulés d'exécutions
antérieures avaient fini par repousser la ligne du test hors du lot. Le
test remet donc à zéro les lignes vieillies qui ne lui appartiennent pas,
exactement comme `AuthRateLimitIntegrationTests` remet à zéro ses compteurs
Redis. Un test doit échouer pour ce qu'il mesure.

### Migration

`V24__create_remote_attendance_authorization_and_session_mode.sql` —
`course_session.attendance_mode` et `remote_link`, table
`remote_attendance_authorization`, et remplacement de la contrainte
`chk_attendance_record_source` de V10 pour accepter les canaux distants.
V10 n'est pas modifiée.

### Vérifications

| Commande | Résultat |
|---|---|
| `cd backend && ./mvnw test` | 119 classes / **1033 tests** / 0 échec |
| `cd frontend && npm test` | 79 fichiers / **659 tests** / 0 échec |
| `cd frontend && npm run lint` | « All files pass linting » |
| `cd frontend && npm run build` | bundle produit, aucune alerte de budget |

### Limites restantes, explicitement assumées

- Le drapeau `remote` est **déclaratif** : il ne prouve pas où se trouve
  l'apprenant. Le contrôle de présence sur site arrive au sprint 8 (QR fixe
  de salle + plage réseau, `EF-ATT-008` / `EF-ATT-010`).
- La classe est saisie par son **identifiant public** dans le formulaire
  d'autorisation, sans sélecteur : utilisable, peu confortable.
- La modalité d'une séance n'est pas encore alimentée par le planning : les
  colonnes `attendance_mode` et `remote_link` du modèle de fichier
  (docs/02 §13.3) ne sont pas lues. Une séance issue d'un planning est donc
  `ON_SITE` par défaut, et la modalité se règle à la création manuelle ou
  après coup. À reprendre quand le planning couvrira ces colonnes.
- Le statut `EXPIRED` d'une autorisation existe en base mais n'est calculé
  par aucune tâche : la couverture se lit sur les dates, seule source qui
  ne peut pas se désynchroniser.

---

## S8 — Assiduité conforme (`sprint/S08-assiduite`)

### Ce qui existait déjà (vérifié avant d'écrire une ligne)

- `EF-ATT-003` était `PARTIAL` : V10 offrait N points de contrôle typés
  `START` / `END` / `CUSTOM`, en indiquant que les quatre points du cahier
  restaient « réalisables via des points `CUSTOM` libellés ».
- `EF-ATT-004` était `PARTIAL` : le calcul de demi-journées existait, sans
  s'appuyer sur les quatre points nommés.
- `EF-ATT-005` était `PARTIAL` : un seuil unique `PT10M`.
- `EF-ORG-003`, `EF-ATT-007`, `EF-ATT-008`, `EF-ATT-010` : aucun code.
  `room.static_qr_reference` existait depuis V4 comme **texte libre**,
  jamais consommé.

### Ce qui a été livré

| Exigence | Contenu |
|---|---|
| `EF-ATT-003` | les quatre points journaliers nommés, uniques par séance |
| `EF-ATT-004` | résultat journalier complet : `FULL_DAY`, `MORNING`, `AFTERNOON`, `PARTIAL`, `TO_CONFIRM`, `ABSENT`, `EXCUSED`, plus `COMPANY` et `NOT_EXPECTED` |
| `EF-ATT-005` | paliers 15 / 30 minutes configurables, validation humaine requise au-delà |
| `EF-ORG-003` | QR fixe de salle **généré par le serveur**, unique, renouvelable, révocable |
| `EF-ATT-010` | émargement par QR fixe : le serveur déduit salle, séance, inscription et fenêtre |
| `EF-ATT-008` | contrôle de plage réseau CIDR IPv4/IPv6, refus par défaut |
| `EF-ATT-007` | apprenant provisoire : signalement, puis régularisation tracée |

### Ce que le cahier laissait implicite, et qui a été tranché

**« Validations cohérentes » (§16.3).** Le cahier exige des validations
cohérentes sans les définir. Décision : un *retour de pause validé sans
l'arrivée qui le précède* est une incohérence → `TO_CONFIRM`, qui appelle
un humain. L'inverse — arrivée sans retour de pause — est simplement
incomplet → `PARTIAL`. Sans cette lecture, `TO_CONFIRM` n'aurait aucun
cas d'emploi.

**Deux valeurs ajoutées à la table.** `COMPANY` et `NOT_EXPECTED` ne
figurent pas au cahier, qui suppose une journée attendue. Sans elles, une
journée en entreprise tomberait dans `ABSENT` — ce que RG-028 interdit
explicitement.

**Le troisième palier de retard ne refuse pas.** Au-delà de 30 minutes,
la présence est **enregistrée** et marquée comme demandant une validation
humaine. Refuser produirait une absence là où il y a un retard constaté ;
le cahier demande une validation, pas une porte fermée.

**L'entrée provisoire n'est pas une présence.** Table séparée, et non une
ligne d'`attendance_record` : celle-ci exige une inscription, et la rendre
facultative ouvrirait une présence sans inscription dans *tous* les
calculs d'assiduité. Conséquence assumée : une entrée provisoire n'entre
dans aucun rapport tant qu'elle n'est pas régularisée — c'est un
signalement, pas une présence. Le rattachement lui-même ne fabrique pas
de présence : elle se saisit ensuite par la voie manuelle, motivée et
auditée.

### Défauts réels découverts et corrigés

1. **`static_qr_reference` était un texte libre saisi à la création.**
   Suffisant tant que personne n'émargeait avec ; plus du tout ensuite —
   la valeur aurait été « A101 », donc fabricable par n'importe qui. Le
   jeton est désormais tiré d'un `SecureRandom`, unique, non saisissable,
   et renouvelable (une affiche photographiée se remplace). Les valeurs
   déjà saisies sont **effacées** par V26 plutôt que converties : les
   garder laisserait des références devinables actives sur des salles
   réelles.

2. **`CHECKPOINT_INVALID_TYPE` confondait deux situations** — un type
   inconnu (requête malformée, `400`) et un type déjà présent sur la
   séance (conflit d'état, `409`). Son message énumérait en outre
   « START, END ou CUSTOM », devenu faux. Les deux cas sont séparés.

3. **`MODIFY COLUMN` supprime la valeur par défaut.** V25 élargissait
   `checkpoint_type` en `VARCHAR(32)` sans répéter `DEFAULT 'START'` : la
   valeur par défaut de V10 disparaissait et seize tests d'insertion
   directe échouaient. Défaut de migration, corrigé dans V25 avant tout
   commit.

### Migrations

- `V25__named_daily_checkpoints.sql` — quatre types nommés, colonne
  élargie (`AFTERNOON_BREAK_RETURN` fait 21 caractères, la colonne en
  faisait 20), unicité par type nommé et par séance.
- `V26__room_static_qr_and_network_control.sql` — jeton de QR de salle
  unique et daté, canal `ROOM_STATIC_QR`.
- `V27__session_guest_attendance.sql` — entrées provisoires.

### Vérifications

| Commande | Résultat |
|---|---|
| `cd backend && ./mvnw test` | 121 classes / **1064 tests** / 0 échec / 0 erreur |
| `cd frontend && npm test` | 79 fichiers / **663 tests** / 0 échec |
| `cd frontend && npm run lint` | « All files pass linting » |
| `cd frontend && npm run build` | bundle produit, aucune alerte de budget |

La suite a été rejouée sur une base `esic_test` **recréée** : les
migrations V1 → V27 s'appliquent de bout en bout sur une base neuve, ce
qu'une base incrémentale ne démontre pas. La pollution par les fixtures
(dette ouverte depuis le rapport F-ENV-1) est du même coup levée sur
`esic_test` ; `esic_connect` reste à recréer.

### Limites restantes, explicitement assumées

- `EF-ATT-011` (confirmation locale d'un émargement par WebAuthn) n'est
  **pas** livré. La cérémonie WebAuthn existe pour l'authentification
  (sprint 2) ; l'appliquer à l'émargement demande un défi lié à la séance
  et au point de contrôle, qui reste à écrire.
- Le QR de salle se saisit **au clavier** dans l'écran apprenant : le scan
  caméra n'est toujours pas implémenté. La saisie reste l'alternative
  exigée par le cahier (§29.4), mais elle n'est pas le parcours nominal.
- Le résultat journalier se lit par classe et par jour ; aucun écran ne le
  présente encore. L'API est livrée et testée, l'interface relève du
  sprint 11 (pilotage et restitution).
- Le contrôle réseau lit `getRemoteAddr()`. Derrière un proxy inverse,
  c'est l'adresse du proxy tant que le serveur n'est pas configuré pour
  honorer `Forwarded` / `X-Forwarded-For` — **configuration de
  déploiement**, jamais une confiance accordée à un en-tête client. Même
  convention que l'authentification.
- Une salle sans plage réseau déclarée refuse tout émargement par QR fixe.
  C'est le refus par défaut voulu, mais cela signifie qu'un site doit être
  configuré avant que ses affiches ne servent à quoi que ce soit.

---

## S9 — Justificatifs, réclamations, départ anticipé, transparence (`sprint/S09-justificatifs-reclamations`)

### Ce qui existait déjà (vérifié avant d'écrire une ligne)

`EF-JUS-001/003/004` (dépôt, examen, `ABSENT → EXCUSED_ABSENCE`) et le
contrôle structurel des pièces jointes (extension, type déclaré, *magic
bytes*, stockage hors webroot, compensation, réconciliation) étaient
livrés depuis le bloc G1-E. Rien n'a été réécrit. Le sprint complète ce
qui manquait : l'analyse antivirus et le balayage des orphelins.

### Ce qui a été livré

| Exigence | Livré |
|---|---|
| `EF-CLAIM-001..004` | module `claim` (15ᵉ module Modulith), guichets, fil de messages, transfert motivé, décision, réouverture |
| `EF-ATT-013` | départ anticipé : dossier, avis du formateur, transmission, décision, effet sur le résultat journalier |
| `EF-ATT-014` | journal de transparence de l'apprenant |
| `EF-JUS-002` | analyse antivirus (port + ClamAV optionnel + adaptateur inactif explicite), balayage des orphelins |
| `AC-018` | l'auteur d'une correction est désormais **exposé** — il ne l'était pas |

Décisions : `DEC-S9-001` à `DEC-S9-006` (`docs/03-architecture.md`).

### Défauts réels trouvés et corrigés

1. **`AC-018` n'était pas satisfait.** `AttendanceCorrectionResponse`
   n'exposait **aucun** auteur, alors que le critère exige que la
   correction affiche « l'ancienne valeur, la nouvelle, l'auteur, la date
   et le motif ». La colonne `actor_user_id` existait en base depuis V10 ;
   elle n'atteignait ni l'API ni l'écran. Corrigé : nom + fonction pour le
   personnel, fonction seule pour l'apprenant (`DEC-S9-003`).

2. **Une réclamation pouvait devenir invisible de tous.** Un apprenant
   sans classe active adressant sa réclamation à un guichet à périmètre
   n'aurait été vu par aucun responsable — le dossier aurait été accepté
   puis perdu. Refusé explicitement à la création, avec orientation vers
   l'administration scolaire (`CLAIM_NO_SCOPE_FOR_AUDIENCE`).

3. **Le canal, pas la colonne auteur, dit qui a émargé.** Sur un
   émargement porté par l'apprenant, `recorded_by_id` reste nul — personne
   n'a enregistré *pour* lui. Se fier à cette colonne faisait passer chaque
   émargement pour une saisie manuelle dans le journal de transparence.
   Trouvé par un test qui échouait, corrigé avant livraison.

4. **Le compilateur incrémental masquait deux erreurs de compilation.**
   `./mvnw -o test-compile` répondait « BUILD SUCCESS » alors que
   `./mvnw -o clean test-compile` échouait sur deux fichiers de test.
   L'extension Java de l'éditeur avait par ailleurs laissé dans `target/`
   une classe portant `Unresolved compilation problem`. **Ne jamais
   conclure d'un `test-compile` incrémental** : seul `clean` fait foi.

### Migrations

- `V29__create_early_departure.sql` — dossier de départ anticipé. Aucune
  colonne d'effet : il se déduit du statut (`DEC-S9-001`).
- `V30__justification_attachment_scan.sql` — verdict d'analyse antivirus,
  date et signature. Défaut `NOT_SCANNED` : marquer `CLEAN`
  rétroactivement les pièces déjà stockées serait une affirmation que rien
  ne fonde.

*(`V28__create_claims.sql` avait été écrite lors de la session précédente
et n'était pas encore commitée ; elle l'est avec ce sprint.)*

### Écrans livrés

- apprenant : journal de transparence, départs anticipés ;
- tous rôles : liste et fil des réclamations, avec transfert, décision et
  réouverture ;
- formateur / responsable : panneau des départs anticipés dans la fiche
  de séance, où la décision disparaît une fois le dossier transmis.

### Vérifications

| Commande | Résultat |
|---|---|
| `cd backend && ./mvnw clean test` | 124 classes / **1112 tests** / 0 échec / 0 erreur |
| `cd frontend && npm test` | 84 fichiers / **695 tests** / 0 échec |
| `cd frontend && npm run lint` | « All files pass linting » |
| `cd frontend && npm run build` | bundle produit, aucune alerte de budget |

### Limites restantes, explicitement assumées

- **Aucun antivirus n'est actif par défaut.** Le service `clamav` du
  profil `antivirus` de `compose.yaml` n'a **jamais été démarré ni
  vérifié** dans ce dépôt : l'adaptateur ClamAV est écrit et compilé, non
  éprouvé contre un `clamd` réel. Les tests couvrent le contrat du port et
  ce que le produit fait de chaque verdict, avec un double piloté.
- Le balayage des orphelins traite un **lot borné** par passage : il ne
  prétend pas nettoyer tout un stockage en une fois.
- La séance d'un départ anticipé se saisit par son identifiant public dans
  l'écran apprenant : aucun sélecteur ne la propose depuis le planning.
- Les réclamations n'émettent **aucune notification** : l'audience élargie
  et le courriel relèvent du sprint 10.
- Aucun de ces écrans n'est couvert par la recette navigateur : ils le
  sont par des tests de composant Angular. `NOT_PERFORMED` pour ces
  parcours.
