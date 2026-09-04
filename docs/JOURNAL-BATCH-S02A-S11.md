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
