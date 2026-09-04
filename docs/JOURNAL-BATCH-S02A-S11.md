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
