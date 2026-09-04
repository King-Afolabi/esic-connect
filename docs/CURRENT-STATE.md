# État courant — ESIC Connect

> **But** : donner en une lecture l'état **réel** du dépôt — ce qui est
> implémenté, testé, partiel ou absent — et les preuves associées.
>
> **Ce document est la seule source de vérité sur l'avancement.** En cas
> de contradiction avec le cahier des charges, la roadmap, le backlog ou
> le README, c'est ce document qui a raison, et l'autre qui doit être
> corrigé.

## Dernière mise à jour

```text
4 septembre 2026 — sprint 8 terminé : quatre points de contrôle nommés,
résultat journalier, paliers de retard, QR fixe de salle sous contrôle de
plage réseau, apprenant provisoire.
Backend 1064 tests, frontend 663 tests, tout vert.
Base `esic_test` **recréée** : la pollution par les fixtures est levée.
```

## Repère Git

| Élément | Valeur |
|---|---|
| Branche de travail | `batch/S02A-S11` (lot de sprints S2 → S11) |
| Base | `f0d02d4` sur `feature/produit-complet-v2` |
| Jalons posés | `v0.2` (S2), `v0.3` (S3), `v0.4` (S4), `v0.5` (S5), `v0.6` (S6), `v0.7` (S7), `v0.8` (S8) |
| Documents cadres | `docs/01-cadrage.md` v3.0, `docs/02-cahier-des-charges.md` v2.0 |

---

## 1. Couverture des exigences

Le cahier des charges v2.0 définit **142 exigences fonctionnelles**.

| Statut | Nombre | Part |
|---|---:|---:|
| `IMPLEMENTED_AND_TESTED` | 88 | 62 % |
| `PARTIAL` | 3 | 2 % |
| `NOT_IMPLEMENTED` | 51 | 36 % |

Cette répartition est **attendue** : la version 2.0 du cahier des
charges a volontairement élargi le périmètre à l'ensemble du produit
cible. Les 51 exigences non implémentées ne sont pas des régressions :
ce sont les sprints 9 à 13 de la roadmap.

Le sprint 2 a fait passer huit exigences de `NOT_IMPLEMENTED` à
`IMPLEMENTED_AND_TESTED` : `EF-AUTH-006` à `EF-AUTH-011`, `EF-AUTH-013`
et `EF-AUTH-015`.

Le sprint 3 en a fait passer six de plus : `EF-ACA-006` (matières),
`EF-ACA-007` (groupes temporaires), `EF-USER-001` (création de compte),
`EF-USER-007` (suivi et réémission des invitations), `EF-USER-008`
(délivrabilité) et `EF-TEA-001` (formateur externe).

Le sprint 4 en ajoute cinq : `EF-IMP-003` (Excel), `EF-IMP-004`
(classeur multifeuille), `EF-IMP-006` (correction de ligne),
`EF-USER-004` (opérations de masse) et `EF-USER-005` (doublons).

Le sprint 5 clôt deux exigences restées partielles depuis l'origine :
`EF-PLAN-003` (correction ligne à ligne) et `EF-PLAN-009` (conflit de
salle contre les séances déjà publiées).

Le sprint 8 en ajoute quatre et clôt trois partiels : `EF-ORG-003` (QR
fixe de salle), `EF-ATT-007` (apprenant provisoire), `EF-ATT-008`
(contrôle de plage réseau) et `EF-ATT-010` (émargement par QR de salle) ;
`EF-ATT-003` (quatre points nommés), `EF-ATT-004` (résultat journalier) et
`EF-ATT-005` (paliers de retard) passent de `PARTIAL` à
`IMPLEMENTED_AND_TESTED`.

Le sprint 7 ajoute `EF-ENR-004` (suivi à distance individuel) — la seule
exigence du sprint 7 qui n'était pas déjà livrée : l'émargement nominal
(`EF-ATT-001/002/006/009/012/015`, `EF-SES-002/003`) l'était depuis
l'origine.

Le sprint 6 en ajoute six : `EF-PLAN-006` (calendrier interactif),
`EF-PLAN-008` (retour à une version antérieure), `EF-PLAN-010`
(avertissement d'alternance), `EF-PLAN-011` (planning Excel),
`EF-SES-007` (report) et `EF-SES-008` (demande d'annulation).

`EF-SES-009` (séance multi-classes) était **déjà implémenté** et listé à
tort comme absent : `CourseSession` porte une collection `SessionClass`
et l'API accepte `classPublicIds`. Correction faite ici — le dépôt a
raison, le document avait tort.

### 1.1 Par domaine

| Domaine | Livré | Partiel | Absent |
|---|---:|---:|---:|
| Identité et accès (15) | 15 | 0 | 0 |
| Utilisateurs (9) | 8 | 0 | 1 |
| Référentiels et organisation (13) | 12 | 0 | 1 |
| Inscriptions et imports (10) | 9 | 0 | 1 |
| Corps enseignant (5) | 4 | 1 | 0 |
| Planning (13) | 11 | 0 | 2 |
| Séances (9) | 9 | 0 | 0 |
| Émargement et assiduité (16) | 13 | 1 | 2 |
| Justificatifs et réclamations (8) | 3 | 1 | 4 |
| Notifications et mobilité (9) | 1 | 1 | 7 |
| Restitution (10) | 3 | 1 | 6 |
| IA et objets connectés (10) | 0 | 0 | 10 |
| Intégrations (4) | 0 | 0 | 4 |
| Transverse (11) | 1 | 0 | 10 |

---

## 2. Ce qui est livré et testé

### 2.1 Identité et accès

- `EF-AUTH-001` connexion email + mot de passe → JWT HS256 stateless
  (signature, `exp`, `iss` vérifiés). Réponse **uniforme** pour email
  inconnu, mot de passe erroné et compte inactif.
- `EF-AUTH-002` multi-rôles, autorités `ROLE_*` dans le jeton,
  `@PreAuthorize` sur toute route non publique.
- `EF-AUTH-003` sélecteur de contexte de rôle **transmis au serveur** et
  vérifié contre les autorités du jeton
  (`403 DASHBOARD_CONTEXT_NOT_HELD`). Le cumul n'élargit jamais le jeton.
- `EF-AUTH-004` invitation et activation : jeton `SecureRandom`,
  empreinte SHA-256 seule stockée, durée de vie configurable, usage
  unique.
- `EF-USER-002/003/006` suspension, réactivation, archivage, attribution
  et retrait de rôle, avec gardes fines côté serveur (protection
  `SUPER_ADMIN`, auto-action interdite, dernier rôle actif protégé).
  Écran `/administration` en lecture et écriture.
- `EF-AUTH-005` **mot de passe oublié** : réponse strictement neutre —
  adresse connue, inconnue, suspendue ou archivée produisent la même
  réponse et le même corps ; jeton `SecureRandom` à usage unique, durée de
  vie 30 minutes, empreinte SHA-256 seule stockée, une demande active par
  compte garantie en base ; une nouvelle demande révoque la précédente ;
  un mot de passe refusé par la politique ne consomme pas le jeton ; un
  compte `PENDING_ACTIVATION` devient `ACTIVE` (contrôler l'adresse vaut
  activation) ; un compte suspendu ne peut pas contourner la décision
  administrative.
- `EF-AUTH-012` **limitation de débit** : compteurs à fenêtre fixe dans
  Redis sur la connexion (par identité **et** par origine réseau), la
  demande de réinitialisation et la consommation d'un jeton. Les clés sont
  des empreintes : ni adresse électronique ni adresse IP en clair
  (RG-094). Une connexion réussie remet le seau d'identité à zéro, jamais
  celui de l'origine. Réponse `429 RATE_LIMITED` + `Retry-After`, sans
  rien révéler sur l'existence du compte. Repli permissif si Redis est
  indisponible (`DEC-S2-001`).
- `EF-AUTH-014` **déconnexion et révocation** : liste de refus Redis
  indexée par `jti` pour une session, colonne
  `user_account.credentials_invalidated_at` pour la révocation globale
  (changement de mot de passe, `logout-all`). Validées à chaque requête
  par un `OAuth2TokenValidator` contribué par le module `identity`, sans
  créer de dépendance de `shared` vers `identity` (`DEC-S2-002`).
- **Politique de mot de passe** : longueur minimale 12, liste de mots de
  passe courants canonicalisée (casse et accents neutralisés), refus d'un
  mot de passe contenant l'adresse, borne haute anti-déni de service.
  Aucune exigence de composition, aucune expiration périodique.
- `EF-AUTH-006`/`007` **passkeys WebAuthn** : options d'enregistrement
  avec défi aléatoire à usage unique (Redis), vérification d'attestation
  et d'assertion par `webauthn4j`, compteur de signature contrôlé et mis
  à jour, connexion sans mot de passe, liste et révocation individuelle
  (RG-008). Le serveur ne reçoit **qu'une clé publique et une signature** :
  aucune structure de ce module ne peut porter de donnée biométrique
  (`AC-020`). Les options d'assertion ne renvoient aucune liste de
  justificatifs — elle révélerait l'existence d'un compte.
- `EF-AUTH-008` **second facteur TOTP** (RFC 6238, HMAC-SHA1, 6 chiffres,
  pas de 30 s), vérifié contre les vecteurs officiels de la RFC. Secret
  partagé **chiffré au repos** en AES-256-GCM, jamais renvoyé après
  l'écran d'enrôlement. Anti-rejeu : le dernier pas consommé est mémorisé,
  un même code ne sert pas deux fois. Limitation de débit dédiée.
- `EF-AUTH-009` **dix codes de récupération** à usage unique, empreinte
  SHA-256 seule stockée, affichés une seule fois, régénérables — la
  régénération invalide toute la série précédente.
- `EF-AUTH-010` **authentification adaptative** : un appareil inconnu
  déclenche le second facteur ; un appareil reconnu allège la reconnexion
  d'un compte ordinaire et n'accorde **aucune dispense** à un compte
  privilégié (`DEC-S2-006`).
- `EF-AUTH-011` **anti-robot** : port `CaptchaVerifier`, adaptateur
  Cloudflare Turnstile et adaptateur local. Vérification **côté serveur**,
  systématique sur la demande de réinitialisation et l'activation,
  déclenchée après trois échecs sur la connexion (`AC-022`). Sans clé
  secrète configurée, le produit **déclare** qu'aucun contrôle n'est actif
  (`GET /api/v1/auth/captcha`) — il n'en simule pas un. Politique de repli
  `DEC-S2-004`.
- `EF-AUTH-013` **appareils de confiance** : empreinte seule en base (ni
  user-agent, ni adresse IP), confiance bornée dans le temps, liste et
  révocation par le propriétaire, `404` sur l'appareil d'autrui. Mémorisé
  **uniquement** après une authentification complète.
- `EF-AUTH-015` **réauthentification avant action critique** : le claim
  `amr` du jeton porte les moyens réellement employés ; un changement de
  rôle exige un jeton obtenu avec un facteur fort, jamais un mot de passe
  seul.
- **Politique de second facteur** (RG-007, `AC-021`) : un compte
  `SUPER_ADMIN` ou `ADMIN` n'obtient **jamais** de jeton contre son seul
  mot de passe. La connexion renvoie un défi — `VERIFY` s'il a un facteur,
  `ENROLL` sinon, l'enrôlement se faisant dans la foulée (`DEC-S2-005`).
- `EF-AUD-001` piste d'audit `audit_event` alimentée par tous les flux
  métier, **sans donnée personnelle, sans jeton, sans adresse IP** ;
  `PASSWORD_CHANGED`, `SESSIONS_REVOKED`, `MFA_*`, `PASSKEY_*` et
  `TRUSTED_DEVICE_*` publiés **après commit** (`DEC-S2-003`).

### 2.2 Référentiels et organisation

- `EF-ORG-001/002` sites, bâtiments, salles, plages réseau CIDR IPv4 et
  IPv6 validées sans résolution DNS. CRUD, archivage, restauration.
  Écrans Angular livrés.
- `EF-ACA-001..005, 008` années, formations, niveaux, promotions,
  classes, affectations pédagogiques, avec contrôle de périmètre décidé
  côté serveur (`AcademicScopeGuard`).
- `EF-ACA-009` alternance : quatre types de rythme, configuration
  validée et canonicalisée, affectation historisée, exceptions
  individuelles, résolution `SCHOOL` / `COMPANY` / `UNKNOWN`. Écran en
  lecture et écriture.

### 2.3 Population, matières et groupes

- `EF-ACA-006` **matières** : CRUD, archivage et restauration,
  rattachement à une ou plusieurs formations contrôlé formation par
  formation (`AcademicScopeGuard`), code immuable après création — il sert
  de référence dans les fichiers de planning. **Aucun champ formateur**,
  ni en base, ni dans l'API, ni dans l'écran : le cahier réserve
  l'affectation à la séance, à une période ou à une association
  classe–matière–période (docs/02 §6.4). Écran `/subjects`, lecture
  ouverte aux formateurs.
- `EF-ACA-007` **groupes temporaires** : un groupe rassemble des
  apprenants issus de **classes différentes** pour une période, sans
  jamais toucher à leur classe principale (RG-022). Membres rattachés à
  l'**inscription** et non au profil, retrait logique, périmètre
  pédagogique contrôlé côté serveur. Hébergé dans `enrollment` et non
  `academic` : l'inverse créerait un cycle entre modules (`DEC-S3-001`).
- `EF-USER-001` **création de compte** : `POST /api/v1/users` crée un
  compte `PENDING_ACTIVATION` et émet son invitation dans la foulée.
  **Aucun champ de mot de passe** — la personne choisit le sien via son
  lien (docs/02 §11.2). Adresse déjà utilisée → `409`, jamais de doublon
  (RG-001). Formulaire dans `/administration`.
- `EF-TEA-001` **formateur externe** : créé par la même route, avec une
  adresse de n'importe quel domaine. Le domaine n'est jamais un critère
  de confiance (docs/02 §12.1).
- `EF-USER-007` **suivi et réémission des invitations** :
  `GET /api/v1/account-invitations` (statut, expiration déduite de
  `expires_at`) et `POST /{id}/resend`, qui **révoque le jeton
  précédent** — sans quoi une adresse corrigée laisserait un lien valide
  dans la mauvaise boîte. Écran `/invitations`.
- `EF-USER-004` **opérations de masse** : suspension, réactivation,
  archivage et réémission groupés. **Sans `confirm: true`, rien n'est
  écrit** (RG-034) : l'appel produit le même calcul — éligibles, ignorés,
  refusés — et le rend, sans effet. L'exécution est isolée **par
  compte** et non atomique sur le lot : un compte protégé est reporté
  sans priver les autres de l'opération (`DEC-S4-002`).
- `EF-USER-005` **doublons** : comptes rapprochés par nom complet
  normalisé (accents et casse neutralisés) ou par numéro de téléphone.
  Le service **signale**, il ne fusionne ni ne supprime : la suppression
  d'un doublon reste une action humaine, exceptionnelle et doublement
  confirmée (docs/02 §9.5).
- `EF-USER-008` **délivrabilité** : table `email_delivery` tenant
  **deux axes distincts** — ce que le produit a fait
  (`QUEUED` / `SENT_TO_PROVIDER` / `PROCESSING_FAILED`) et ce que le
  fournisseur a constaté (`UNKNOWN` par défaut). « Remis au serveur de
  messagerie » n'est jamais présenté comme « délivré » (docs/02 §11.3).
  L'adresse n'est stockée ni exposée en clair : empreinte + forme masquée
  (`c…e@e…c.test`).

### 2.4 Imports

- `EF-IMP-003` **import Excel `.xlsx`** : le format binaire ancien
  (`.xls`, OLE2) est **refusé**, le cahier ne demandant que `.xlsx` et
  OLE2 ouvrant la porte aux macros. Le type réel est dérivé du contenu
  (magie ZIP) : un CSV renommé en `.xlsx` — ou l'inverse — est rejeté,
  jamais deviné. Le classeur converge vers la **même structure** que le
  CSV, afin que la validation métier ne diverge pas par format. Les
  cellules sont converties de façon prévisible : une date reste une date
  ISO, un numéro étudiant saisi comme nombre ne devient pas
  `20260001.0`, et une formule est lue par son résultat mis en cache —
  jamais recalculée.
- `EF-IMP-004` **classeur multifeuille** : toutes les feuilles non
  masquées sont lues ; une feuille dont l'en-tête diffère est
  **signalée et écartée**, jamais lue avec le mauvais mapping. Chaque
  ligne conserve sa feuille d'origine, de sorte qu'une anomalie est
  située « fichier, feuille, ligne, colonne » (docs/02 §10.7). Critère
  IMP-STU-05 vérifié : un classeur de trois feuilles rattache trois
  classes.
- `EF-IMP-006` **correction de ligne avant confirmation** : corriger une
  ligne en anomalie sans recommencer l'import. La correction rejoue
  **exactement** la validation de la simulation, recalcule la synthèse du
  travail — corriger la dernière ligne fautive le rend confirmable — et
  est tracée en append-only (qui, quand, valeur avant, valeur après). La
  liste des champs corrigeables est fermée côté serveur.

- `EF-ENR-001..003` profils apprenants, inscriptions, changement de
  classe conservant l'historique. Une seule inscription active par
  apprenant et par année, garantie par contrainte SQL et testée en
  concurrence.
- `EF-IMP-001` simulation d'import CSV **sans aucune écriture métier** :
  extension contrôlée, rejet ZIP / OLE2 / PDF / octet nul, UTF-8 strict,
  RFC 4180, séparateur auto-détecté, fichier **jamais écrit sur
  disque**, plafond `2 MiB` → `413`.
- `EF-IMP-002` confirmation transactionnelle unique : verrou
  `SELECT … FOR UPDATE`, revalidation complète, idempotence, rollback
  total sur toute exception, courriel émis **uniquement après commit**,
  numéro `ESIC-{année}-{NNNNN}` alloué atomiquement.

### 2.5 Planning

- `EF-PLAN-001/002` import CSV borné, jamais écrit sur disque
  (SHA-256 seul), simulation produisant lignes, anomalies et synthèse
  **sans créer aucune séance**.
- `EF-PLAN-004/005/007` publication **atomique** : verrou `FOR UPDATE`,
  revalidation, version N/N+1, ancienne version `SUPERSEDED`, séances
  créées ou réutilisées via le **port public**
  `coursesession.PlanningSessionWriter`. Publication concurrente
  strictement idempotente. Identité de créneau stable et déterministe.
- `EF-PLAN-003` **correction ligne à ligne** dans l'écran de revue : la
  correction rejoue l'analyse de **tout** le travail — les conflits de
  planning sont croisés, corriger une ligne peut en lever ou en créer un
  ailleurs (`DEC-S5-002`). Corriger la dernière ligne fautive rend le
  travail publiable sans réimport.
- `EF-PLAN-009` **conflit de salle contre les séances déjà publiées** :
  la séance conserve désormais son `room_code` (migration `V21`). Le
  contrôle porte sur **tout l'établissement** — une salle n'appartient
  pas à une classe (`DEC-S5-001`). Deux créneaux sans salle ne sont
  jamais en conflit ; le même créneau republié reste exclu.
- `EF-PLAN-006` **calendrier interactif** : ajout, modification,
  déplacement, suppression, duplication d'une semaine, répétition d'un
  créneau, brouillon, publication. Un créneau saisi devient une ligne d'un
  travail d'import ordinaire et chaque mutation rejoue l'analyse du lot
  entier ; la publication reste `POST /planning-imports/{id}/publish`,
  avec les mêmes conflits et le même versionnement (`DEC-S6-001`). Le
  publié et le brouillon sont présentés séparément.
- `EF-PLAN-008` **retour à une version antérieure** : crée une version
  **N+1** dont le contenu est celui de la version choisie (AC-009).
  L'historique n'est jamais effacé ; `slot_public_id` étant conservé, les
  séances existantes sont réutilisées et non recréées (RG-047). Refusé si
  un formateur n'est plus éligible ou si la version est vide
  (`DEC-S6-002`).
- `EF-PLAN-010` **avertissement d'alternance** non bloquant : un créneau
  tombant sur une période résolue `COMPANY` produit
  `PLAN_ALTERNATION_COMPANY_PERIOD` sans empêcher la publication. Sans
  rythme affecté (axe `UNKNOWN`), le produit se tait plutôt que de
  présumer.
- `EF-PLAN-011` **import Excel `.xlsx`** : type réel dérivé du contenu
  (un CSV renommé `.xlsx` est refusé), dates et heures converties de
  façon prévisible, feuilles supplémentaires signalées et non lues en
  silence — un planning porte sur une seule classe.
- Écrans `/planning/import`, `/planning/import/:jobId`,
  `/planning/calendar`, `/planning/versions`.

### 2.6 Séances et remplacements

- `EF-SES-001..006` séance issue d'un planning publié ou créée
  manuellement avec motif ; cycle strict `PLANNED → OPEN → CLOSED` sans
  réouverture ; `CANCELLED` avec motif, la séance restant consultable en
  historique ; séance supersédée inactive partout.
- `EF-SES-007` **report** d'une séance annulée : crée une séance de
  remplacement **liée** à l'originale, qui reste `CANCELLED` et
  consultable en portant `postponedToPublicId`. Une séance non annulée
  n'est pas reportable ; un second report est refusé
  (`SESSION_ALREADY_POSTPONED`).
- `EF-SES-008` **demande d'annulation** par le formateur, décidée par le
  responsable : une acceptation annule la séance dans la foulée, un refus
  la laisse `PLANNED`. Une seule demande en attente par séance ; le
  demandeur peut la retirer, l'historique la conservant en `WITHDRAWN`.
  Le formateur reçoit `403` s'il tente de décider — « il demande, il ne
  décide pas » (RG-024).
- `EF-SES-009` séance rattachée à **plusieurs classes** (RG-023) : porté
  par `SessionClass`, exposé par `classPublicIds`.
- `EF-TEA-003..005` remplacements datés : formateur principal jamais
  écrasé, une seule substitution active applicable, droits accordés au
  remplaçant **uniquement** pendant sa période, `TEACHER` exclu de la
  création.

### 2.7 Émargement

- `EF-ATT-001/009` jeton d'émargement **opaque** et code court dans
  Redis : durée de vie, rotation, purge à la fermeture **après commit**.
  Le QR n'encode que le jeton opaque, aucune donnée personnelle.
- `EF-ATT-002` validation par un `STUDENT` inscrit, anti-double présence
  par contrainte SQL ; concurrence → `200` / `409`, jamais `500`.
- `EF-ATT-006/012` présence manuelle, correction, annulation logique,
  motif obligatoire, historique append-only, verrou optimiste → `409`.
- `EF-ATT-015` suivi des présences en direct.
- `EF-ENR-004` **suivi à distance individuel** : autorisation datée,
  motivée, révocable et auditée ; sur une séance présentielle, le canal
  distant sans autorisation active est refusé
  (`403 ATT_REMOTE_NOT_AUTHORIZED`). La portée « séance / période / année »
  est un **intervalle de dates** ; une autorisation générale est réservée
  au périmètre global (`DEC-S7-002`). Une séance porte désormais sa
  modalité (`ON_SITE` / `REMOTE` / `HYBRID`) et son lien distant ; les
  canaux `REMOTE_QR` et `REMOTE_CODE` sont enregistrés distinctement.
  **Le drapeau `remote` est une déclaration, pas une preuve de
  localisation** (`DEC-S7-001`) : le contrôle de présence sur site reste
  le QR fixe de salle et la plage réseau (sprint 8). Écran : section
  « suivi à distance » de la fiche apprenant.
- `EF-ATT-003` **quatre points de contrôle nommés** (`MORNING_ARRIVAL`,
  `MORNING_BREAK_RETURN`, `AFTERNOON_ARRIVAL`, `AFTERNOON_BREAK_RETURN`),
  uniques par séance. V10 les disait « réalisables via des points
  `CUSTOM` libellés » : vrai fonctionnellement, faux structurellement —
  un calcul journalier ne peut pas se fonder sur un libellé libre.
- `EF-ATT-004` **résultat journalier** (`GET /attendance/reports/daily`) :
  `FULL_DAY`, `MORNING`, `AFTERNOON`, `PARTIAL`, `TO_CONFIRM`, `ABSENT`,
  `EXCUSED`, plus `COMPANY` (RG-028 — jamais une absence) et
  `NOT_EXPECTED`, absents de la table du cahier qui suppose une journée
  attendue. Un **retour de pause sans l'arrivée** qui le précède est une
  incohérence → `TO_CONFIRM` ; l'inverse est incomplet → `PARTIAL`.
- `EF-ATT-005` **paliers de retard** configurables : `PRESENT` jusqu'à 15
  min, `LATE` jusqu'à 30, au-delà `LATE` **plus** validation humaine
  requise. Le troisième palier ne refuse pas l'émargement : refuser
  produirait une absence là où il y a un retard constaté.
- `EF-ORG-003` **QR fixe de salle** : jeton `SecureRandom` généré par le
  serveur, unique, daté, renouvelable et révocable. Une référence saisie
  à la main serait devinable, donc sans valeur (`DEC-S8-001`). `V26`
  efface les valeurs libres héritées de V4.
- `EF-ATT-010` **émargement par QR de salle** : le corps ne porte que le
  jeton ; le serveur détermine salle, séance imminente, inscription et
  fenêtre. Refusé après le début de la séance (RG-051), sans séance
  correspondante, et sur jeton inconnu (`404`).
- `EF-ATT-008` **contrôle de plage réseau** : comparaison CIDR IPv4/IPv6
  sur les octets, sans résolution DNS, **avant** toute autre décision.
  Refus par défaut — un site sans plage déclarée n'autorise rien. L'adresse
  sert à décider puis disparaît : ni persistée, ni auditée, ni renvoyée
  (RG-094, `DEC-S8-002`).
- `EF-ATT-007` **apprenant provisoire** : signalement par le formateur
  (`UNREGISTERED_GUEST` / `PENDING_REGISTRATION`), puis régularisation
  motivée — rattachement à une inscription réelle, ou mise à l'écart.
  L'entrée n'entre dans **aucun** calcul d'assiduité tant qu'elle n'est
  pas régularisée, et le rattachement ne fabrique pas de présence
  (`DEC-S8-003`).
- Redis indisponible → `503 ATT_TOKEN_BACKEND_UNAVAILABLE` : **aucune
  validation dégradée**.

### 2.8 Justificatifs et restitution

- `EF-JUS-001/003/004` dépôt, modification tant que `PENDING`, examen,
  décision motivée, `ACCEPTED` → `ABSENT` devient `EXCUSED_ABSENCE` ;
  `TEACHER` exclu de l'examen.
- `EF-JUS-002` pièce jointe : dépôt multipart propriétaire, validation
  extension + type déclaré + **magic bytes** (type re-dérivé du contenu,
  rejet ZIP/OLE2), stockage **hors base et hors webroot**, séquence
  base ↔ fichier avec compensation, réconciliation planifiée des lignes
  `PENDING_STORAGE`, téléchargement forcé en pièce jointe avec
  `nosniff`, accès propriétaire et examinateur périmétré uniquement.
- `EF-REP-001..003` rapports séance / classe / apprenant / synthèse en
  JSON paginé avec tri serveur borné, export CSV UTF-8 + BOM, séparateur
  `;`, neutralisation d'injection de formule.
- Espace apprenant `/me/attendance*` : absences **dérivées** d'un point
  de contrôle fermé, jamais persistées ; aucun accès croisé (`AC-017`).

### 2.9 Notifications

- `EF-NOTIF-001` centre in-app persistant : planning publié, séance
  annulée, remplaçant affecté, remplacement terminé. Notifications
  produites **après commit** (rollback ⇒ zéro notification),
  **idempotentes** (`dedup_key` SHA-256), **isolées par destinataire**
  (notification d'autrui → `404`). Cloche `mat-badge` et centre Angular ;
  liens en liste blanche par rôle, aucun chemin d'interface transmis par
  le serveur.

### 2.10 Transverse

- En-têtes durcis : `nosniff`, `X-Frame-Options: DENY`, anti-cache,
  CSP, `Referrer-Policy: no-referrer`.
- CORS restrictif piloté par `APP_ALLOWED_ORIGINS`, jamais `*`,
  `allowCredentials=false`.
- Erreur d'appel client → `400 VALIDATION_ERROR`, jamais `500`.
- Front Angular 21 zoneless / standalone / Material ; jeton et contexte
  de rôle **en mémoire seule**, aucun `localStorage` ni
  `sessionStorage`, asserté par test.
- Lien d'évitement présent sur les pages applicatives **et** publiques.
- Matrices `*SecurityTests` (`401` / `403` / `200`) par module ;
  concurrence testée sur inscriptions, affectations, émargement,
  corrections, confirmations d'import, publications de planning.

---

## 3. Partiels

| Exigence | Ce qui existe | Ce qui manque |
|---|---|---|
| `EF-TEA-002` | API d'affectation pédagogique livrée | aucun écran d'affectation classe–matière–période |
| `EF-JUS-002` | contrôle structurel complet, stockage sécurisé, compensation, réconciliation | **antivirus absent** — ne jamais écrire « garanti sans logiciel malveillant » ; balayage des fichiers orphelins absent |
| `EF-NOTIF-002` | notifications produites pour les événements de planning et de séance | audience **formateur uniquement** ; apprenants et responsables non notifiés |
| `EF-REP-007` | endpoint typé par rôle, périmètre serveur, contexte multi-rôle vérifié ; cartes `STUDENT` et `TEACHER` complètes | cartes `PEDAGOGICAL_MANAGER` et `ADMINISTRATION` incomplètes ; coût SQL linéaire par séance |
| Audit transactionnel | `coursesession` et `studentimport` publient après commit | 8 des 9 écouteurs restent synchrones `REQUIRES_NEW` ; pas d'outbox |

---

## 4. Non implémenté

Aucune ligne de code. Ce sont les sprints à venir — voir
`docs/06-roadmap-six-mois.md`.

| Bloc | Exigences | Sprint |
|---|---|---|
| Confirmation locale d'un émargement par WebAuthn | `EF-ATT-011` | 8 |
| Opérations de masse, doublons, invitations pilotées, délivrabilité, recherche globale | `EF-USER-004`, `005`, `007`, `008`, `009` | 3–4, 11 |
| Matières, groupes temporaires | `EF-ACA-006`, `007` | 3 |
| Import Excel, multifeuille, correction de ligne | `EF-IMP-003`, `004`, `006` | 4 |
| Planning PDF texte et assistance IA au mapping | `EF-PLAN-012`, `013` | 12 |
| Départ anticipé, transparence, borne connectée | `EF-ATT-013`, `014`, `016` | 9, 12 |
| Réclamations | `EF-CLAIM-001..004` | 9 |
| Audience élargie, courriel, push, préférences, PWA | `EF-NOTIF-003..006`, `EF-PWA-001..003` | 10 |
| Excel, PDF, attestations, tableaux alternatifs, rapports d'anomalies et d'invitations | `EF-REP-004`, `005`, `006`, `008`, `009`, `010` | 11–12 |
| Service d'IA complet | `EF-AI-001..005` | 12 |
| Objets connectés | `EF-IOT-001..005` | 12 |
| Intégrations Microsoft, iCalendar, fournisseur de courriel | `EF-INT-001..004` | 11, 13 |
| Consultation d'audit, outbox, RGPD, exploitation | `EF-AUD-002`, `003`, `EF-RGPD-001..003`, `EF-OPS-001..005` | 10, 13 |

**Vérifications de terrain** (3 septembre 2026, après sprint 2) : aucune
occurrence de `MQTT`, `outbox`, `service-worker`, Apache POI ni
bibliothèque PDF dans `backend/src/main` ou `frontend/src`. Aucun module
`claim`, `ai` ou `iot`.

---

## 5. Architecture réelle

### 5.1 Modules Spring Modulith — 14

`ModularityTests` **vert** : aucune dépendance vers l'interne d'un autre
module, aucun cycle.

| Module | Rôle | Migrations |
|---|---|---|
| `identity` | comptes, rôles, JWT, invitation, administration, mot de passe oublié, révocation, second facteur, passkeys, appareils de confiance | V1, V2, V3, V17, V18 |
| `organization` | site, bâtiment, salle, plage réseau, QR fixe de salle | V4, V26 |
| `academic` | année, formation, niveau, promotion, classe, affectation, matières | V5, V6, V19 |
| `enrollment` | profil apprenant, inscription, changement de classe, groupes temporaires, suivi à distance | V7, V19, V24 |
| `alternation` | rythmes, affectations, exceptions, résolution | V8 |
| `planning` | import CSV et Excel, simulation, conflits, correction de ligne, calendrier interactif, publication versionnée, retour arrière | V12, V13, V23 |
| `coursesession` | séances, cycle de vie, points de contrôle nommés, remplacements, salle, modalité, report, demandes d'annulation | V9, V10, V13, V14, V21, V22, V24, V25 |
| `attendance` | jetons, validation, QR de salle, corrections, apprenants provisoires, justificatifs, rapports, résultat journalier | V9, V10, V16, V26, V27 |
| `studentimport` | import CSV et Excel des apprenants, correction de ligne | V11, V20 |
| `notification` | centre de notifications persistant, délivrabilité des courriels | V15, V19 |
| `dashboard` | tableau de bord par rôle | — |
| `audit` | piste d'audit | V1 |
| `bootstrap` | amorçage du profil `demo` | — |
| `shared` | types transverses, gestion d'erreurs, horloge | — |

Modules du cahier des charges **non encore créés** : `claim`,
`reporting` (fusionné dans `attendance`), `ai`, `iot`, `integration`.

### 5.2 Migrations Flyway — schéma en V27

54 tables métier, `ddl-auto = validate`, aucune donnée métier insérée
par une migration. `V17` ajoute `password_reset_token` et la colonne
`user_account.credentials_invalidated_at` ; `V18` ajoute
`mfa_credential`, `mfa_recovery_code`, `webauthn_credential` et
`trusted_device` ; `V19` ajoute `subject`, `subject_program`,
`student_group`, `student_group_member` et `email_delivery` ; `V20`
ajoute `student_import_row_correction`, la colonne
`student_import_row.sheet_name` et **remplace** l'unicité
`(job, ligne)` par `(job, feuille, ligne)` ; `V21` ajoute
`course_session.room_code` ; `V22` ajoute le lien de report et
`session_cancellation_request` ; `V23` assouplit la contrainte
`file_size_bytes > 0` en `>= 0`, un planning construit au calendrier
n'ayant pas de fichier ; `V24` ajoute `course_session.attendance_mode` et
`remote_link`, la table `remote_attendance_authorization`, et remplace la
contrainte `chk_attendance_record_source` de V10 pour accepter les canaux
distants ; `V25` ajoute les quatre types de point de contrôle nommés,
élargit `checkpoint_type` (`AFTERNOON_BREAK_RETURN` fait 21 caractères) et
impose l'unicité d'un type nommé par séance ; `V26` transforme
`room.static_qr_reference` en jeton serveur unique et daté et ajoute le
canal `ROOM_STATIC_QR` ; `V27` crée `session_guest_attendance`.

> **Règle absolue** : une migration appliquée n'est **jamais** modifiée,
> pas même un commentaire — cela invalide sa somme de contrôle et casse
> toute base existante. Les corrections passent par une nouvelle
> migration.
>
> Les migrations V10 à V16 citent en commentaire des chemins de rapports
> supprimés le 3 septembre 2026 (`docs/reports/…`). Ces références sont
> **conservées telles quelles** pour cette raison. Les décisions
> correspondantes sont reprises dans `docs/03-architecture.md` sous les
> mêmes identifiants `DEC-G1-*`.

---

## 6. Résultats de tests

Mesurés sur ce dépôt, branche `batch/S02A-S11`, 4 septembre 2026. Environnement : OpenJDK 21.0.12, Node 24.13.0,
npm 11.6.2, MySQL 8.4 et Redis 7.4 en Docker Compose.

| Commande | Résultat |
|---|---|
| `cd backend && ./mvnw clean test` | **121 classes / 1064 tests / 0 échec / 0 erreur** — `ModularityTests` vert (14 modules), schéma V27 |
| `cd frontend && npm test` | **79 fichiers / 663 tests / 0 échec** |
| `cd frontend && npm run lint` | « All files pass linting » |
| `cd frontend && npm run build` | bundle produit, aucune alerte de budget |

Les tests portant le tag `perf` sont exclus par défaut
(`./mvnw test -Pperf` pour les exécuter).

**Piège d'exécution** : la suite back-end exige les variables du `.env`.
Sans `set -a && source ../.env && set +a`, Flyway échoue avec
`Access denied for user '${MYSQL_USER}'` et toute la suite tombe en
erreur. Ce n'est pas un défaut du produit.

**Couplage connu entre classes de test** : les compteurs de limitation
indexés sur l'*origine* réseau vivent dans Redis et sont partagés par
toute la suite — toutes les classes se connectent depuis `127.0.0.1`.
`AuthRateLimitIntegrationTests` et `CaptchaIntegrationTests` les remettent
donc à zéro dans un `@BeforeEach`. Sans cela, elles échoueraient pour une
raison sans rapport avec ce qu'elles vérifient, dès que le nombre total de
connexions de la suite augmente.

### 6.1 Recette navigateur

| Indicateur | Valeur |
|---|---|
| Commande | `npm run test:e2e` (pile démarrée, `ESIC_DEMO_PASSWORD` exporté) |
| Fichiers / tests | 10 / 149 |
| Navigateurs | chromium exécuté ; firefox, webkit et mobile configurés, non exécutés par défaut |
| Intégration continue | `.github/workflows/e2e.yml`, déclenchement manuel |

La suite ne couvre pas les fonctions qui n'ont pas d'écran : QR fixe de
salle, scan caméra, exports Excel et PDF, réclamations, écrans d'écriture
`academic` et `enrollment`. **Aucun test n'est écrit contre un écran qui
n'existe pas.**

Les écrans livrés au sprint 2 — vérification en deux étapes, sécurité du
compte — sont couverts par des tests de composant Angular, **pas encore**
par la recette navigateur : `NOT_PERFORMED` pour ces parcours.

---

## 7. Démonstration

| Nature | Statut |
|---|---|
| Recette d'intégration API du parcours prioritaire | `IMPLEMENTED_AND_TESTED` |
| Parcours prioritaire rejoué dans un vrai navigateur (2 apprenants, création → ouverture → QR et code court → émargement → anti-rejeu → clôture → isolation `AC-017`) | `IMPLEMENTED_AND_TESTED` |
| Démonstration **manuelle** de bout en bout par un humain | **`NOT_PERFORMED`** — un navigateur piloté par script n'en est pas une |
| Déploiement | **`NOT_PERFORMED`** — aucune instance, aucune URL |

---

## 8. Dettes et risques

| Réf | Dette | Effet |
|---|---|---|
| T-01 | pas d'outbox : notifications produites après commit sans reprise | une panne du diffuseur perd la notification |
| T-02 | 8 écouteurs d'audit synchrones | une trace peut manquer sans annuler l'action |
| T-04 | aucun antivirus sur les pièces jointes | contrôle structurel seul |
| T-04 | balayage des fichiers orphelins absent | un fichier peut subsister après une suppression échouée |
| T-03 | coût SQL linéaire par séance sur le tableau de bord | dégradation quand la fenêtre contient beaucoup de séances |
| T-05 | rétention des pièces supprimées `À_DÉFINIR` | politique RGPD à arrêter avant tout usage réel |
| T-06 | pièces jointes sur système de fichiers local | non persistant sur un hébergement éphémère |
| T-10 | opérations de masse et doublons sans écran | l'API est livrée et testée ; l'interface reste à faire (sprint 11, recherche et pilotage) |
| T-07 | cérémonie WebAuthn complète non rejouée en test | la vérification cryptographique repose sur la bibliothèque ; les tests couvrent contrat, défi, isolation et absence de donnée biométrique |
| T-08 | Turnstile jamais vérifié contre le service réel | aucune clé secrète dans le dépôt ; sans clé, le produit **déclare** qu'aucun contrôle n'est actif |
| T-09 | passkeys inutilisables hors `localhost` sans domaine ni HTTPS | contrainte du standard WebAuthn, pas du produit |
| — | base `esic_test` **recréée** au sprint 8 : la pollution par les fixtures y est levée | `esic_connect` (local) reste à recréer — `./scripts/db-reset.sh esic_connect` non exécuté |

---

## 9. Infrastructure

`docker compose up -d` démarre `mysql` (8.4), `redis` (7.4), `mailpit`
et `mosquitto`. Les trois premiers passent `healthy` ; **Mosquitto n'a
pas de sonde et aucun code back-end ne le consomme.**

Quatre bases distinctes : `esic_connect` (local), `esic_connect_demo`
(démonstration), `esic_test` (tests), `esic_connect_ci` (intégration
continue). Le profil `test` lit `MYSQL_TEST_DATABASE`.

---

## 10. Prochaines priorités

1. **Exécuter `./scripts/db-reset.sh esic_connect`** — l'outillage est
   livré, l'exécution ne l'est pas.
2. **Sprint 6 — planning avancé** : calendrier interactif, retour à une
   version antérieure, avertissement d'alternance, import Excel du
   planning, report et demande d'annulation, séance multi-classes.
4. **Sprint 6 — planning avancé** : calendrier interactif, retour
   arrière, conflit de salle.
5. **Sprint 8 — assiduité conforme** : quatre points de contrôle nommés,
   paliers de retard, QR fixe et contrôle réseau.
6. **Sprint 10 — outbox** : lève les dettes T-01 et T-02 d'un coup.

---

## 11. Règle de mise à jour

Ne jamais déclarer :

- `IMPLEMENTED_AND_TESTED` sans commande exécutée et reproductible ;
- démontré sans vérification manuelle enregistrée ;
- déployé sans URL ni preuve ;
- fonctionnel au seul motif que le code existe.

Ce document est mis à jour **à chaque livraison**, dans le même commit
que le code qu'il décrit.
