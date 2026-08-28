# État courant — ESIC Connect

## Dernière mise à jour

```text
29 août 2026
```

## Dernier commit stable

```text
À renseigner
```

## Phase actuelle

```text
Référentiel académique minimal ajouté (branche feature/academic-foundation,
non fusionnée, non committée) — nouveau module `academic` + migration V5
`academic_year` / `program` / `program_level` / `promotion` / `class_group`
(schéma en version 5, appliqué et vérifié). Couvre la hiérarchie
formation → promotion → classe/groupe (docs/04 §12) ; n'aborde ni les
inscriptions, ni les matières, ni les responsabilités pédagogiques, ni
Angular.
- `academic_year` et `program_level` inclus uniquement comme référentiels
  support des FK de `promotion` (academic_year_id) et `class_group`
  (program_level_id). Conventions techniques complètes (public_id,
  created_at/by, updated_at/by, version, status, archived_at/by,
  archive_reason). Écarts documentés vs docs/04 §12 : `program_level`
  reçoit public_id/horodatage/version/archivage (absents du tableau
  §12.3) ; `promotion` reçoit start_date/end_date optionnelles pour la
  validation de période ; les colonnes external_source/external_id de
  §12.2/§12.5 ne sont pas reprises (pas de synchronisation externe).
- CRUD + archivage logique + restauration pour les cinq entités. Aucun
  DELETE physique. `code` immuable après création ; tous les
  rattachements parents (program, academic_year, program_level,
  promotion, site) immuables.
- Consultation paginée (max 100, défaut 20) + filtres : status, q
  (code+name, LIKE échappé) ; promotions filtrables par program /
  academicYear ; classes par promotion / programLevel / site ; niveaux
  listés sous /programs/{id}/levels. Tri liste blanche (sinon 400
  ACAD_INVALID_SORT). Consultation par public_id. Routes exclusivement
  en public_id : `/api/v1/academic-years`, `/api/v1/programs`,
  `/api/v1/programs/{id}/levels` + `/api/v1/program-levels/{id}`,
  `/api/v1/promotions`, `/api/v1/class-groups`.
- Règles métier vérifiées : end_date > start_date (année, + CHECK SQL) ;
  période de promotion, si renseignée, strictement incluse dans celle de
  l'année (ACAD_PROMOTION_PERIOD_OUT_OF_YEAR) ; modification de la période
  d'une année refusée si elle exclurait une promotion existante à période
  renseignée (ACAD_ACADEMIC_YEAR_PERIOD_CONFLICT, deux `exists` ciblés,
  aucun chargement de liste) ; le program_level d'une classe doit
  appartenir à la même formation que sa promotion
  (ACAD_PROGRAM_LEVEL_MISMATCH), revérifié aussi à la restauration ;
  création refusée sous un parent archivé (ACAD_ARCHIVED_PARENT) ;
  archivage refusé tant qu'il reste des enfants actifs
  (ACAD_HAS_ACTIVE_CHILDREN : niveaux/promotions pour une formation,
  promotions pour une année, classes pour niveau/promotion) ;
  restauration d'une classe refusée si un maillon de la chaîne est
  archivé — promotion, sa formation, son année, le niveau, la formation
  du niveau — ou si le site est absent/archivé ; restauration d'une
  promotion refusée si sa formation ou son année est archivée.
  `capacity > 0` (class_group, + CHECK SQL).
- Unicités testées : academic_year.code (global), program.code (global),
  (program_id, code) pour program_level, (program_id, academic_year_id,
  code) pour promotion, (promotion_id, code) pour class_group, tous les
  public_id ; FK RESTRICT vérifiées (program→promotion,
  promotion→class_group).
- Rattachement au site : `class_group.site_id` est une valeur technique
  (FK SQL `fk_class_group_site` vers `site.id`), jamais une relation JPA
  inter-module. Résolu via un nouveau port public minimal
  `organization.SiteDirectory` (impl `organization.internal.
  DefaultSiteDirectory`) qui n'expose que `SiteRef(internalId, publicId,
  archived)` — ni `Site`, ni `SiteRepository`, ni `organization.internal`.
  Le module `academic` n'importe jamais `organization.internal`.
  `ModularityTests` reste vert.
- Autorisations @PreAuthorize : lecture =
  ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION/PEDAGOGICAL_MANAGER ; écriture
  = ADMIN/SUPER_ADMIN uniquement (écriture PEDAGOGICAL_MANAGER reportée
  au périmètre pédagogique T-J1-023). TEACHER et STUDENT exclus (401/403
  testés).
- DTO sans identifiant SQL interne ni colonne auteur ; erreurs via
  ApiError commun (AcademicExceptionHandler, codes ACAD_*).
- Audit : événement applicatif `academic.AcademicChangeEvent` (module
  racine) → `audit/internal.AcademicAuditListener` (catégorie ACADEMIC,
  transaction REQUIRES_NEW), actions
  ACADEMIC_YEAR_/PROGRAM_/PROGRAM_LEVEL_/PROMOTION_/CLASS_GROUP_ +
  CREATED/UPDATED/ARCHIVED/RESTORED, motif non sensible (code), jamais de
  donnée personnelle.
- Aucune formation, promotion ni classe fictive insérée en V5.

Référentiel organisationnel ajouté (branche feature/organization-foundation,
non fusionnée, non committée) — nouveau module `organization` +
migration V4 `site` / `building` / `room` / `site_network_range` (schéma
en version 4, appliqué et vérifié). Ce module élargit et remplace le
module `room` prévu par l'architecture (docs/03 §7.6).
- Hiérarchie site → bâtiment → salle. Conventions techniques complètes
  (public_id, created_at/by, updated_at/by, version, status, archived_at/by,
  archive_reason) ; `site_network_range` reçoit en plus public_id,
  updated_at et version.
- CRUD + archivage logique + restauration (site/bâtiment/salle) ;
  plages réseau = création + activation/désactivation. Aucun DELETE
  physique. `code` immuable après création ; rattachement au site immuable.
- Consultation paginée (max 100, défaut 20) + filtres (status, q, site,
  building, active) + tri liste blanche. Consultation par public_id.
  Routes exclusivement en public_id.
- Règles : refus building/room sous parent archivé ; room.site =
  building.site imposé ; archivage d'un site/bâtiment refusé tant qu'il
  reste des enfants actifs ; unicité site.code (global), (site,code) pour
  building et room, (site,cidr) active pour les plages.
- Validations : fuseau IANA via ZoneId, code pays ISO 3166-1 alpha-2,
  CIDR IPv4 et IPv6 réellement validé (préfixes bornés 0..32 / 0..128,
  sans résolution DNS).
- Autorisations @PreAuthorize : lecture site/bâtiment/salle =
  ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION/PEDAGOGICAL_MANAGER ; écriture
  = ADMIN/SUPER_ADMIN ; site_network_range = SUPER_ADMIN pour TOUTES les
  opérations, consultation comprise.
- DTO sans identifiant SQL interne ni colonne auteur ; erreurs via
  ApiError commun (OrganizationExceptionHandler).
- Audit : événement applicatif `organization.OrganizationChangeEvent`
  (module racine) → `audit/internal.OrganizationAuditListener`
  (catégorie ORGANIZATION, transaction REQUIRES_NEW), actions
  SITE_/BUILDING_/ROOM_/SITE_NETWORK_RANGE_ + CREATED/UPDATED/ARCHIVED/
  RESTORED/ACTIVATED/DEACTIVATED, motif non sensible (code, cidr), jamais
  de donnée personnelle ni d'IP.
- Port public minimal ajouté au module `identity` :
  `identity.CurrentUserResolver` (résout l'id interne depuis le subject
  public du JWT) ; implémentation `DefaultCurrentUserResolver` confinée à
  `identity.internal`. N'expose ni UserAccount, ni repository, ni autre
  classe interne. `ModularityTests` reste vert.
- Aucun site fictif ni donnée métier insérés en V4.

Administration minimale des comptes et des rôles (fusionnée sur main via
PR #6) — n'avait ajouté aucune migration (colonnes suspended_*/archived_at/
user_role.* déjà présentes en V1) :
- GET /api/v1/users (ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION) : liste
  paginée (taille max 100, défaut 20), filtres status / role (affectation
  active) / q (email+prénom+nom, normalisé, borné à 100 car., LIKE
  échappé), tri restreint à createdAt/lastLoginAt/email/lastName ;
- GET /api/v1/users/{public_id} : détail + historique complet des rôles ;
- POST …/{public_id}/suspend | /restore (ACTIVE↔SUSPENDED, motif
  obligatoire) — SCHOOL_ADMINISTRATION autorisé ;
- POST …/{public_id}/archive (ADMIN/SUPER_ADMIN) : statut ARCHIVED,
  clôture de tous les user_role actifs dans la même transaction
  (historique conservé), irréversible dans ce lot ;
- POST …/{public_id}/roles et …/roles/{roleCode}/revoke
  (ADMIN/SUPER_ADMIN) : attribution = nouvelle ligne user_role ; retrait =
  clôture (active=false, valid_until), jamais de suppression ; retrait du
  dernier rôle actif refusé.
DTO exposant uniquement public_id (jamais id SQL, password_hash, jeton).
Contrôles sensibles doublés dans le service (au-delà de @PreAuthorize) :
un compte ou le rôle SUPER_ADMIN n'est administrable que par un
SUPER_ADMIN ; auto-suspension / auto-archivage / retrait de son propre
rôle interdits. Audit ACCOUNT_SUSPENDED / ACCOUNT_REACTIVATED /
ACCOUNT_ARCHIVED / ROLE_ASSIGNED / ROLE_REVOKED (module audit, motif
seul, sans donnée sensible). PEDAGOGICAL_MANAGER exclu tant que le
périmètre pédagogique n'existe pas. Aucune suppression physique. Toujours
aucun MFA, WebAuthn, refresh token ni réinitialisation de mot de passe.

Flux d'invitation et d'activation de compte (fusionné sur main via PR #4) :
- POST /api/v1/account-invitations (protégé ADMIN/SUPER_ADMIN/
  PEDAGOGICAL_MANAGER/SCHOOL_ADMINISTRATION) : émission réservée aux
  comptes PENDING_ACTIVATION, attribution du rôle demandé via user_role,
  jeton SecureRandom 32 octets Base64URL, empreinte SHA-256 seule stockée,
  TTL configurable (défaut P30D, strictement positif), révocation des
  invitations PENDING antérieures, email d'activation via Mailpit ;
- GET /api/v1/account-invitations/validate (public) : réponse générique
  {"valid": bool} — aucune donnée personnelle, réponse identique pour
  jeton inconnu/expiré/révoqué/accepté ;
- POST /api/v1/account-invitations/activate (public) : mot de passe encodé
  (BCrypt), statut ACTIVE, email_verified_at, invitation ACCEPTED à usage
  unique.
Audit ACCOUNT_INVITATION_ISSUED / ACCOUNT_ACTIVATED (module audit, sans
jeton).

Dette technique : l'email d'activation est envoyé de façon synchrone
après commit ; en cas d'échec l'invitation est conservée et seule une
erreur technique est journalisée (ni jeton, ni email, ni lien). Il
n'existe pas encore de file persistante ni de reprise garantie
(docs/03-architecture.md §18, cahier §23.3).
```

## Documents

| Document | Statut |
|---|---|
| Cadrage | CONÇU |
| Cahier des charges | CONÇU |
| Architecture | CONÇU |
| Modèle de données | CONÇU |
| Product Backlog | CONÇU |
| Roadmap | CONÇU |
| Sprint Backlog | CONÇU |
| Diagrammes | CONÇU |
| Risques | CONÇU |
| Sécurité/RGPD | CONÇU |
| Tests/recette | CONÇU |
| Matrice RNCP | CONÇU |
| Journal IA | INITIALISÉ |

## Implémentation

| Fonctionnalité | Statut |
|---|---|
| Dépôt Git | INITIALISÉ (`main`, remote `origin` GitHub) |
| Docker Compose | TESTED |
| Spring Boot | TESTED (socle : démarrage du contexte, `mvn test` exécuté avec succès — aucune route ni entité métier) |
| Angular | TODO |
| MySQL | TESTED (healthy, auth root et `esic_app` vérifiée) |
| Redis | TESTED (healthy, auth vérifiée) |
| Flyway | TESTED (V1 tables identité/audit, V2 seed des 6 rôles, V3 table `account_invitation`, V4 tables `site`/`building`/`room`/`site_network_range`, V5 tables `academic_year`/`program`/`program_level`/`promotion`/`class_group` — migrations appliquées et vérifiées, schéma en version 5) |
| Authentification | TESTED (`POST /api/v1/auth/login` : email/mot de passe, JWT HS256 stateless, `last_login_at`, audit succès/échec ; réponse publique uniforme vérifiée pour email inconnu/mauvais mot de passe/compte non actif ; routes protégées refusent sans jeton ; MFA/WebAuthn/refresh token non implémentés) |
| Rôles | TESTED (persistance `role`/`user_role` : 6 rôles système, unicité d'affectation active, réattribution après clôture ; attribués via `user_role` à l'émission d'une invitation ; API d'attribution / retrait dédiée — voir « Gestion des comptes / rôles ») |
| Gestion des comptes / rôles | TESTED (`GET /api/v1/users` paginé/filtré/trié, `GET /api/v1/users/{public_id}`, `POST …/{public_id}/suspend`·`/restore`·`/archive`·`/roles`·`/roles/{roleCode}/revoke` ; `@PreAuthorize` + contrôles sensibles dans `UserManagementService` (protection SUPER_ADMIN, auto-action interdite, dernier rôle actif protégé) ; archivage = clôture transactionnelle des rôles actifs, ARCHIVED irréversible ; DTO sans id SQL / `password_hash` / jeton ; audit `ACCOUNT_SUSPENDED`/`ACCOUNT_REACTIVATED`/`ACCOUNT_ARCHIVED`/`ROLE_ASSIGNED`/`ROLE_REVOKED` ; aucune migration V4 ; `PEDAGOGICAL_MANAGER` exclu jusqu'au périmètre pédagogique) |
| Invitation / activation | TESTED (`POST /api/v1/account-invitations` protégé par rôle, `GET …/validate` et `POST …/activate` publics ; migration V3 `account_invitation` ; jeton SecureRandom 32 o Base64URL, empreinte SHA-256 unique stockée, TTL configurable strictement positif, révocation des invitations PENDING antérieures, jeton à usage unique ; validation publique strictement générique ; email d'activation via Mailpit ; audit `ACCOUNT_INVITATION_ISSUED`/`ACCOUNT_ACTIVATED` sans jeton) |
| Notification (email) | TESTED (module `notification` : écouteur `AFTER_COMMIT` sur `AccountInvitationIssuedEvent`, envoi SMTP `SimpleMailMessage` via Mailpit ; échec d'envoi avalé, invitation conservée, log sans jeton/email/lien ; pas de file persistante — dette technique) |
| Référentiels pédagogiques (formation/niveau/année/promotion/classe) | TESTED (module `academic`, migration V5 ; CRUD + archivage/restauration des 5 entités, aucun DELETE physique ; hiérarchie formation → promotion → classe/groupe ; routes en public_id sous `/api/v1/academic-years`, `/api/v1/programs`, `/api/v1/programs/{id}/levels` + `/api/v1/program-levels/{id}`, `/api/v1/promotions`, `/api/v1/class-groups` ; pagination max 100 + tri liste blanche ; unicités academic_year.code / program.code / (program,code) / (program,academicYear,code) / (promotion,code) ; période année (end>start), période promotion incluse dans l'année, program_level d'une classe = même formation que sa promotion, refus parent archivé, archivage bloqué si enfants actifs, code + rattachements immuables ; `class_group.site_id` = valeur technique via port public `organization.SiteDirectory` (aucun import de `organization.internal`, aucune relation JPA inter-module) ; `@PreAuthorize` lecture ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION/PEDAGOGICAL_MANAGER, écriture ADMIN/SUPER_ADMIN ; DTO sans id SQL ; audit `ACADEMIC_YEAR_*`/`PROGRAM_*`/`PROGRAM_LEVEL_*`/`PROMOTION_*`/`CLASS_GROUP_*` catégorie ACADEMIC). Inscriptions, apprenants, formateurs, matières : hors périmètre de ce lot. |
| Référentiel organisationnel (site/bâtiment/salle/plage réseau) | TESTED (module `organization`, migration V4 ; CRUD + archivage/restauration site·bâtiment·salle, création + activation/désactivation plages réseau, aucun DELETE physique ; routes en public_id sous `/api/v1/sites`, `/api/v1/buildings/{id}`, `/api/v1/rooms/{id}`, `/api/v1/network-ranges/{id}` ; pagination max 100 + tri liste blanche ; unicités site.code / (site,code) / (site,cidr) active ; refus parent archivé, room.site=building.site, archivage bloqué si enfants actifs, code immuable ; ZoneId + ISO 3166-1 + CIDR IPv4/IPv6 validés ; `@PreAuthorize` lecture ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION/PEDAGOGICAL_MANAGER, écriture ADMIN/SUPER_ADMIN, plages réseau SUPER_ADMIN pour toute opération ; DTO sans id SQL ; audit `SITE_*`/`BUILDING_*`/`ROOM_*`/`SITE_NETWORK_RANGE_*` catégorie ORGANIZATION ; port public `identity.CurrentUserResolver` pour l'auteur des écritures) |
| Import apprenants | TODO |
| Import planning | TODO |
| Séances | TODO |
| Émargement | TODO |
| Rapports | TODO |
| Audit | TESTED (persistance `audit_event` + écriture depuis flux métier réels : connexion réussie/refusée, émission d'invitation, activation de compte, suspension/réactivation/archivage d'un compte, attribution/retrait d'un rôle, changements du référentiel organisationnel — catégorie `ORGANIZATION` — et changements du référentiel académique — année/formation/niveau/promotion/classe, catégorie `ACADEMIC` — jamais de jeton, de donnée sensible ni d'IP ; pour les actions d'administration, le compte/la ressource concernée est portée par `resource_public_id`, l'acteur par `actor_user_id`) |
| FastAPI | TODO |
| MQTT | TODO |
| Raspberry Pi | TODO |
| WebAuthn | TODO |
| CI (GitHub Actions) | IMPLEMENTED (`.github/workflows/backend-ci.yml` : déclenché sur PR vers `main` et push sur `main` ; job unique `ubuntu-latest`, `permissions: contents: read`, `timeout-minutes: 20`, concurrence avec annulation des exécutions obsolètes ; Java 21 Temurin + cache Maven ; services `mysql:8.4` et `redis:7.4-alpine` (mot de passe via `command: redis-server --requirepass`) avec identifiants dédiés CI non sensibles ; exécute `./mvnw --batch-mode test` depuis `backend/` ; aucun usage de `.env`, aucun SMTP réel. Non encore exécuté sur GitHub — statut à confirmer au premier run) |
| Staging | TODO |

## Prochaine priorité

```text
Les référentiels organisationnel (module `organization`, V4) et académique
minimal (module `academic`, V5 : formation → promotion → classe/groupe,
+ année scolaire et niveau comme support de FK) sont en place. Prochaines
étapes :
- contrôle d'accès par périmètre pédagogique (T-J1-023) : restreindre la
  lecture/écriture académique d'un PEDAGOGICAL_MANAGER à ses formations,
  puis ouvrir l'écriture académique à ce rôle dans son périmètre ;
- inscriptions historiques et rythmes d'alternance minimaux
  (T-J1-032, T-J1-033), puis import des apprenants ;
- affectation d'un responsable pédagogique principal à une formation
  (pedagogical_assignment, RG-010), non traitée dans ce lot.
/auth/logout et la révocation de session restent à évaluer (jeton
stateless sans état serveur pour l'instant).

Dettes techniques à traiter ultérieurement :
- file persistante + reprise garantie pour les emails d'activation
  (actuellement envoi synchrone après commit, échec seulement journalisé) ;
- purge / expiration explicite des invitations `PENDING` périmées ;
- création de comptes `PENDING_ACTIVATION` par API (l'émission cible
  aujourd'hui un compte déjà existant, créé par fixture ou futur import) ;
- incohérences docs à corriger : docs/03 §6.4 (dépendances du module
  `academic` : ajouter `organization` et la publication vers `audit`) ;
  docs/04 §12.3 (colonnes techniques de `program_level`).
```

## Blocages

```text
Aucun blocage technique vérifié pour le moment.
```

## Commandes de démarrage

```text
docker compose config    # valider la syntaxe et les variables
docker compose up -d     # démarrer mysql, redis, mailpit, mosquitto
docker compose ps        # vérifier l'état des conteneurs
```

Infrastructure vérifiée le 28 août 2026 : MySQL et Redis en état
`healthy` (authentification testée), Mailpit `healthy`, Mosquitto
démarré (pas de healthcheck configuré). Nécessite un fichier `.env`
local non versionné (voir `.env.example`).

```text
cd backend
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
set -a && source ../.env && set +a   # variables MySQL/Redis requises
./mvnw test                          # exécute EsicConnectApplicationTests + ModularityTests
```

Socle back-end vérifié le 28 août 2026 (Java 21.0.12.1, Maven 3.9.16,
Spring Boot 3.5.16, Spring Modulith 1.4.12) : `./mvnw test` →
`BUILD SUCCESS`, 2 tests exécutés (chargement du contexte + vérification
de la structure modulaire), 0 échec. Aucune route métier, aucune entité
JPA, aucune authentification réelle. Sécurité : seules
`/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**` et
`/swagger-ui.html` sont ouvertes ; toutes les autres routes exigent une
authentification (non implémentée). Travaux réalisés sur la branche
`feature/spring-boot-foundation`, non fusionnée et non committée (fusionnée
depuis sur `main` via la PR #1).

Socle identité + audit vérifié le 28 août 2026, sur la branche
`feature/identity-foundation` (non fusionnée, non committée) : `./mvnw test`
(mêmes commandes ci-dessus) → `BUILD SUCCESS`, 9 tests exécutés (rôles
seedés, unicité email, unicité public_id, suppression d'un utilisateur
référencé refusée par `RESTRICT`, unicité d'une affectation active +
réattribution possible après clôture, acteur d'audit mis à `NULL` après
suppression du compte avec conservation du snapshot, chargement du
contexte, structure modulaire), 0 échec, exécuté deux fois pour
vérifier la stabilité. Migrations Flyway `V1` (tables `user_account`,
`role`, `user_role`, `audit_event`) et `V2` (6 rôles système) appliquées
sur la base locale. Aucune donnée de démonstration, aucun compte
administrateur, aucun JWT, aucun contrôleur d'authentification.

Authentification vérifiée le 28 août 2026, sur la branche
`feature/authentication-foundation` (non fusionnée, non committée) :
`./mvnw test` (mêmes commandes ci-dessus) → `BUILD SUCCESS`, 24 tests
exécutés (15 nouveaux : adaptateur `UserDetailsService`, service
d'authentification unitaire — y compris qu'un échec de journalisation
d'audit ne modifie ni ne masque jamais le résultat réel —, connexion
réussie de bout en bout avec jeton décodable et audit committé, réponse
publique strictement identique pour email inconnu/mauvais mot de
passe/compte non actif, aucune fuite de l'email brut d'un compte
inconnu dans l'audit, rejet sans jeton d'une route protégée, refus d'un
jeton correctement signé mais à l'émetteur (`iss`) incorrect (401 nu,
sans détail de validation exposé), routes `/actuator/health` et
`/v3/api-docs` toujours publiques), 0 échec, exécuté deux fois pour
vérifier la stabilité. Aucune migration `V3` : le schéma `V1`
suffisait. `JwtDecoder` vérifie désormais explicitement l'émetteur
(`JwtValidators.createDefaultWithIssuer`) et `AuthenticationService`
refuse de démarrer si `JWT_ACCESS_TOKEN_TTL_SECONDS` n'est pas
strictement positif. Un `AuthenticationEntryPoint` dédié a dû remplacer
celui par défaut de Resource Server, qui exposait le motif technique du
refus (ex. « the iss claim is not valid ») dans l'en-tête
`WWW-Authenticate` — désormais un 401 nu. **`JWT_SECRET` est requis**
(≥ 32 octets, aucune valeur par défaut) : à ajouter manuellement à
votre `.env` local
(voir `.env.example`) pour lancer l'application hors des tests — les
tests utilisent un secret dédié dans `application-test.yml`, jamais
`.env`.

Invitation / activation vérifiée le 28 août 2026, sur la branche
`feature/account-invitation` (non fusionnée, non committée) : `./mvnw test`
(mêmes commandes ci-dessus) → `BUILD SUCCESS`, **50 tests** exécutés
(26 nouveaux), 0 échec, exécuté deux fois pour vérifier la stabilité.
Nouveaux tests : `InvitationTokenServiceTests` (SecureRandom ≥ 32 o,
Base64URL sans padding, SHA-256 hex déterministe + vecteur
`SHA-256("abc")`), `AccountInvitationServiceTests` (TTL non positif
refusé au démarrage, émission limitée à `PENDING_ACTIVATION`, rôle
inconnu/inactif refusé, révocation des invitations PENDING antérieures
avec `flush` avant insert, empreinte stockée jamais égale au jeton,
activation encodant le mot de passe, jetons inconnu/expiré/accepté →
même erreur générique, `validate` = booléen seul),
`InvitationEmailListenerTests` (transmission au mailer, échec avalé sans
propagation), `AccountInvitationIntegrationTests` (émission protégée →
email capturé par un mailer enregistreur → `validate` public générique →
activation → connexion avec le nouveau mot de passe → jeton à usage
unique refusé en 400 `INVITATION_INVALID` → audit
`ACCOUNT_INVITATION_ISSUED`/`ACCOUNT_ACTIVATED` ; réémission révoquant le
jeton précédent), `AccountInvitationSecurityTests` (émission : 401 sans
jeton, 403 rôle `STUDENT` ; `validate` public : uniquement `{"valid":
bool}`, réponse identique pour tout jeton invalide ; `activate` jeton
inconnu → 400 générique sans fuite).

Migration Flyway `V3` (`account_invitation` : `public_id`, `version`,
`token_hash` UNIQUE, `active_invitation_key` générée → une seule
invitation `PENDING` par compte, FK `RESTRICT`) appliquée sur la base
locale (schéma en version 3). `pom.xml` : ajout de
`spring-boot-starter-mail`. `SecurityConfig` : `@EnableMethodSecurity` +
`/api/v1/account-invitations/validate` et `/activate` publics.
`GlobalExceptionHandler` : `AccessDeniedException` → 403 neutre (sinon
masqué en 500 par le catch-all). Module `shared` déclaré `OPEN`
(noyau technique : `ApiError` consommé hors module). Nouveau module
`notification`. `management.health.mail.enabled=false` **uniquement**
dans les profils `local` et `test` (pas globalement). Nouvelles
variables dans `.env.example` (`MAIL_HOST`, `MAIL_PORT`, `APP_MAIL_FROM`,
`APP_ACTIVATION_BASE_URL`, `INVITATION_TOKEN_TTL`) ; `.env`, `compose.yaml`,
`V1` et `V2` inchangés. TTL par défaut `P30D`, configurable via
`INVITATION_TOKEN_TTL`, refus de démarrage si ≤ 0.

Gestion des comptes / rôles vérifiée le 28 août 2026, sur la branche
`feature/user-management` (non fusionnée, non committée) : `./mvnw test`
(mêmes commandes ci-dessus, `JAVA_HOME` OpenJDK 21) → `BUILD SUCCESS`,
**98 tests** exécutés (48 nouveaux), 0 échec, exécuté deux fois pour
vérifier la stabilité (dont `ModularityTests` : frontières de modules
respectées). Nouveaux tests : `UserManagementServiceTests` (35 —
transitions ACTIVE↔SUSPENDED, un compte SUSPENDED ne peut pas se
réactiver lui-même, archivage clôturant les rôles actifs sans
suppression, protection d'un compte `SUPER_ADMIN` (y compris pour une
réactivation par `SCHOOL_ADMINISTRATION` et pour l'attribution/retrait
de *n'importe quel* rôle par un `ADMIN`), `SUPER_ADMIN` interdit de
s'auto-suspendre / s'auto-archiver / retirer son propre rôle, dernier
rôle actif protégé, rôle inconnu, tri hors liste blanche, direction de
tri invalide (`email,wrong`) refusée au lieu d'un ASC silencieux, filtre
invalide, taille de page bornée à 100 / défaut 20),
`UserManagementIntegrationTests` (5 — liste paginée/filtrée/triée sans
`id` ni `password_hash`, détail par `public_id` + 404, suspension →
connexion refusée → réactivation → connexion rétablie, archivage
bloquant la connexion et refusant la réactivation (409), attribution
puis retrait de rôle conservant l'historique, dernier rôle protégé,
audit écrit), `UserManagementSecurityTests` (8 — 401 anonyme, 403
`STUDENT`/`TEACHER`, `SCHOOL_ADMINISTRATION` peut suspendre mais pas
archiver ni gérer les rôles, `ADMIN` ne peut pas archiver un
`SUPER_ADMIN` ni attribuer le rôle `SUPER_ADMIN`, auto-suspension
refusée (409), `public_id` inconnu → 404).

Aucune migration `V4` : `user_account` (`suspended_at`/`suspended_by_id`/
`suspension_reason`/`archived_at`/`updated_by_id`) et `user_role`
(`valid_until`/`active`/`assigned_by_id`/`assignment_reason`) portaient
déjà les colonnes nécessaires depuis `V1`. Fichiers back-end ajoutés
dans `identity.internal` (`UserAccountController`, `UserManagementService`,
`UserAdminSpecifications`, `UserManagementException(+Handler)`, DTO
`UserSummaryResponse`/`UserDetailResponse`/`RoleAssignmentResponse`/
`PageResponse`, requêtes `AccountActionRequest`/`AssignRoleRequest`) ;
modifiés : `UserAccount` (méthodes `suspend`/`reactivate`/`archive` +
getters), `UserRole` (`close` + getters), `UserAccountRepository`
(`JpaSpecificationExecutor`), `UserRoleRepository` (requêtes de rôles
actifs), `identity.AccountLifecycleAction` (+5 actions),
`audit.internal.AuditEvent` (`setResourcePublicId`),
`AccountLifecycleAuditListener`. `.env`, `compose.yaml`, `V1`–`V3`,
`SecurityConfig` et le workflow CI inchangés. Aucun commit, aucun push.

Référentiel organisationnel vérifié le 28 août 2026, sur la branche
`feature/organization-foundation` (non fusionnée, non committée) :
`./mvnw test` (mêmes commandes ci-dessus, `JAVA_HOME` OpenJDK 21) →
`BUILD SUCCESS`, **164 tests** exécutés (66 nouveaux), 0 échec, exécuté
deux fois pour vérifier la stabilité (dont `ModularityTests` : nouveau
module `organization`, frontières respectées, aucun cycle). Nouveaux
tests : `CidrValidatorTests` (32 — littéraux IPv4/IPv6 valides, préfixes
hors bornes, octets > 255, noms d'hôte refusés, absence de résolution
DNS), `OrganizationServiceTests` (11, Mockito — fuseau inconnu, code
pays non ISO, code dupliqué, archivage bloqué par enfants actifs, tri
hors liste blanche / direction invalide, bâtiment d'un autre site,
création sous site archivé, CIDR invalide, doublon de plage active,
publication d'événement), `OrganizationConstraintsTests` (8, `@DataJpaTest`
— unicité `site.code`, `(site,code)` bâtiment libre entre sites, unicité
`(site,code)` salle, `public_id` unique, FK `RESTRICT` site→bâtiment et
bâtiment→salle, unicité de la plage réseau active, créneau libéré après
désactivation), `OrganizationIntegrationTests` (8, `@SpringBootTest`
`RANDOM_PORT` — cycle complet site/bâtiment/salle + archivage en cascade
contrôlée + restauration + audit `SITE_*`/`BUILDING_*`/`ROOM_*`, DTO sans
`id`/`siteId`/`createdById`, archivage refusé avec enfants actifs,
création sous parent archivé refusée, `room.site` ≠ `building.site`
refusé, unicité/fuseau/pays validés, pagination bornée à 100, tri
inconnu 400, CRUD plage réseau IPv4 + IPv6 par SUPER_ADMIN avec audit),
`OrganizationSecurityTests` (7 — 401 anonyme, 403 `STUDENT`/`TEACHER`,
`PEDAGOGICAL_MANAGER` lit mais n'écrit pas, `SCHOOL_ADMINISTRATION` lit
les sites, plages réseau réservées à `SUPER_ADMIN` y compris en lecture,
`ADMIN` et `SCHOOL_ADMINISTRATION` reçoivent 403).

Migration Flyway `V4` (`site`, `building`, `room`, `site_network_range` :
`public_id` unique, FK `RESTRICT` vers `user_account` pour les colonnes
auteur et vers le parent hiérarchique, `version`, colonnes générées
`active_range_key`, `CHECK (capacity > 0)`) appliquée sur la base locale
(schéma en version 4). Fichiers back-end ajoutés : nouveau module
`com.esic.connect.organization` (package racine : `OrganizationChangeEvent`
+ enums `OrganizationResourceType`/`OrganizationChangeAction` ;
`organization.internal` : entités `Site`/`Building`/`Room`/
`SiteNetworkRange` + `OrganizationStatus`, 4 repositories, 4 services,
4 contrôleurs, DTO de réponse et records de requête, `CidrValidator`,
`SiteFieldValidator`, `OrganizationQuerySupport`, `OrganizationSpecifications`,
`OrganizationChangePublisher`, `OrganizationException(+Handler)`,
`PageResponse` local) ; `identity.CurrentUserResolver` (port public) +
`identity.internal.DefaultCurrentUserResolver` (implémentation) ;
`audit.internal.OrganizationAuditListener`. `.env`, `compose.yaml`,
`V1`–`V3`, `SecurityConfig`, `pom.xml` et le workflow CI inchangés.
Aucun site fictif ni donnée métier insérés. Aucun commit, aucun push.

Référentiel académique minimal vérifié le 29 août 2026, sur la branche
`feature/academic-foundation` (non fusionnée, non committée), après la
passe corrective : `./mvnw clean test` (`JAVA_HOME` OpenJDK 21,
`set -a && source ../.env`) → `BUILD SUCCESS`, **214 tests** exécutés
(50 nouveaux : 32 du premier lot + 18 correctifs), 0 échec, exécuté trois
fois pour vérifier la stabilité (dont `ModularityTests` : nouveau module
`academic`, frontières respectées, aucun cycle). Tests académiques :
`AcademicServiceTests` (22, Mockito — période inversée, code dupliqué, tri
hors liste blanche / direction invalide, archivage bloqué par enfants
actifs (formation via niveau **et** via promotion seule, niveau via
classe, promotion via classe), type de formation inconnu, période de
promotion hors année, promotion sous formation archivée, niveau d'une
autre formation, site inconnu, modification d'année excluant une
promotion existante, restauration de promotion refusée si formation ou
année archivée / réussie et auditée si parents actifs, restauration de
classe refusée si année ou formation-du-programme archivée, si site
archivé, si site absent), `AcademicConstraintsTests` (13, `@DataJpaTest`
— unicités `academic_year.code` / `program.code` / `public_id` /
`(program,code)` / `(program,academicYear,code)` / `(promotion,code)`,
FK `RESTRICT` formation→promotion, promotion→classe, année→promotion,
niveau→classe et site→classe (DELETE natif du site refusé), `CHECK`
période `academic_year` et `CHECK` capacité `class_group` ; le site
requis par les FK est inséré en SQL natif),
`AcademicIntegrationTests` (9, `@SpringBootTest` `RANDOM_PORT` — cycle
complet année→formation→niveau→promotion→classe rattachée à un site,
DTO sans `id`/`siteId`/`programId`, archivage en cascade contrôlée +
restauration complète (année, formation, niveau, promotion, classe) +
audit `…_RESTORED` inclus, archivage refusé avec enfants actifs (409),
niveau d'une autre formation refusé (400), période de promotion hors
année refusée (400), période d'année inversée refusée (400), code de
formation dupliqué (409), création de classe sous promotion archivée
refusée (409), restauration de classe refusée sous année archivée (409),
modification d'année excluant une promotion existante refusée (409) puis
acceptée si la période l'englobe, pagination bornée à 100, tri inconnu
400), `AcademicSecurityTests` (6 — 401 anonyme, 403 `STUDENT`/`TEACHER`,
`SCHOOL_ADMINISTRATION` et `PEDAGOGICAL_MANAGER` lisent mais n'écrivent
pas (403), `ADMIN` crée (201)).

Passe corrective (module `academic` + tests uniquement) :
`ClassGroupService.restore` vérifie désormais toute la chaîne de
rattachement (promotion, sa formation, son année, le niveau, la formation
du niveau, le site présent et actif) et revérifie l'invariant
niveau↔formation ; `AcademicYearService.update` refuse une période qui
exclurait une promotion existante à période renseignée (nouveau code
`ACAD_ACADEMIC_YEAR_PERIOD_CONFLICT`, requêtes `exists` ciblées) ;
`ClassGroupService.resolveSitePublicId` lève une erreur métier contrôlée
au lieu de renvoyer `null` silencieusement, sans exposer d'identifiant
SQL.

Migration Flyway `V5` (`academic_year`, `program`, `program_level`,
`promotion`, `class_group` : `public_id` unique, FK `RESTRICT` vers
`user_account` pour les colonnes auteur et vers le parent hiérarchique,
FK `RESTRICT` `class_group.site_id` → `site.id`, `version`,
`CHECK (end_date > start_date)` année, `CHECK (end_date IS NULL OR
start_date IS NULL OR end_date > start_date)` promotion,
`CHECK (capacity IS NULL OR capacity > 0)`) appliquée sur la base locale
(schéma en version 5). Fichiers back-end ajoutés : nouveau module
`com.esic.connect.academic` (package racine : `AcademicChangeEvent` +
enums `AcademicResourceType`/`AcademicChangeAction` ; `academic.internal` :
entités `AcademicYear`/`Program`/`ProgramLevel`/`Promotion`/`ClassGroup`
+ `AcademicStatus`/`ProgramType`, 5 repositories, 5 services,
5 contrôleurs, DTO de réponse et records de requête, `AcademicWeb`,
`AcademicQuerySupport`, `AcademicSpecifications`, `AcademicChangePublisher`,
`AcademicException(+Handler)`, `PageResponse` local) ;
`organization.SiteDirectory` (port public) +
`organization.internal.DefaultSiteDirectory` (implémentation) ;
`audit.internal.AcademicAuditListener`. `.env`, `compose.yaml`, `V1`–`V4`,
`SecurityConfig`, `pom.xml` et le workflow CI inchangés. Aucune formation,
promotion ni classe fictive insérée. Aucun commit, aucun push.

## Règle de mise à jour

Ce document doit refléter le dépôt.

Ne jamais déclarer :

- TESTÉ sans commande exécutée ;
- DÉMONTRÉ sans vérification manuelle ;
- DÉPLOYÉ sans URL ou preuve ;
- FONCTIONNEL uniquement parce que le code existe.