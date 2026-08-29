# Matrice de traçabilité RNCP 39394 — ESIC Connect

## Métadonnées

| Élément | Valeur |
|---|---|
| Projet | ESIC Connect |
| Candidat | Abubacar AFOLABI |
| Certification | RNCP 39394 |
| Version | 1.0 |
| Date | 28 août 2026 |
| Statut | À mettre à jour selon les preuves réelles |

---

# 1. Principe

La certification RNCP 39394 est évaluée par un rapport d’expérience
professionnelle comportant quatre parties correspondant aux quatre
blocs. ([francecompetences.fr](https://www.francecompetences.fr/recherche/rncp/39394?utm_source=openai))

Statuts utilisés :

- `CONÇU` ;
- `IMPLÉMENTÉ` ;
- `TESTÉ` ;
- `DÉMONTRÉ` ;
- `SIMULÉ` ;
- `REPORTÉ`.

Une exigence décrite n’est pas automatiquement réalisée.

---

# 2. Bloc 1 — Pilotage stratégique

| Compétence projet | Livrable | Preuve | Statut |
|---|---|---|---|
| Analyser l’existant | Cadrage | Description du processus actuel | CONÇU |
| Recueillir les besoins | Cahier des charges | Besoins par acteur | CONÇU |
| Traduire les besoins | Backlog | Epics et user stories | CONÇU |
| Piloter le projet | Roadmap | 13 sprints | CONÇU |
| Gérer les priorités | Product Backlog | MoSCoW | CONÇU |
| Gérer les risques | Registre des risques | Cotation et mesures | CONÇU |
| Définir les indicateurs | Cahier des charges | KPI | CONÇU |
| Assurer la traçabilité | Matrice RNCP | Exigence-code-test | CONÇU |
| Conduire le changement | Guide utilisateur futur | Formation et notifications | À FAIRE |
| Prendre en compte l’accessibilité | Cahier des charges | Alternatives au QR | CONÇU |
| Prendre en compte la durabilité | Architecture | PWA, mutualisation, archivage | CONÇU |
| Manager les parties prenantes | Gouvernance | PO, Scrum Master, développeur | CONÇU |

## Preuves attendues

- `01-cadrage.md` ;
- `02-cahier-des-charges.md` ;
- `05-product-backlog.md` ;
- `05a-roadmap-six-mois.md` ;
- `06-risques.md` ;
- comptes rendus disponibles ;
- GitHub Projects ;
- historique Git.

---

# 3. Bloc 2 — Technologies avancées et développement

| Compétence projet | Réalisation | Preuve | Statut |
|---|---|---|---|
| Choisir une méthode | Scrum/Kanban | Roadmap et backlog | CONÇU |
| Choisir les technologies | Stack | Architecture | CONÇU |
| Concevoir l’UX | Angular Material, coquille responsive, accessibilité (labels, focus, repères, `aria-live`, `aria-current`, `aria-haspopup`) | `frontend/` : `AppShell`, écran de connexion, tableau de bord, activation, sélecteur de contexte de rôle | PARTIEL (socle + activation fusionnés ; contexte de rôle sur `feature/frontend-role-context`) |
| Développer le back-end | Spring Boot | Code et tests | À FAIRE |
| Développer le front-end | Angular 21.2 (standalone, signaux, lazy routes), `authGuard`/`guestGuard`/`roleGuard`, intercepteurs, `RoleContextService` (contexte de rôle en mémoire seule), 102 tests Vitest | socle `frontend/` fusionné (PR #11, `6fa341f`) ; activation de compte fusionnée (PR #12, `2ff7aa8`) ; contexte de rôle sur `feature/frontend-role-context` (PR ouverte) ; `npm ci` / `npm test` / `npm run build` / `npm run lint` verts | IMPLÉMENTÉ (connexion → tableau de bord rapportant un état de session local ; gardes de route par rôle ; navigation limitée aux écrans livrés ; parcours public `/activation` consommant `GET/POST /api/v1/account-invitations/validate\|activate`, jeton retiré de l'URL, aucune connexion automatique ; **sélecteur de contexte de rôle (docs/02 §6.1) — rôles du seul JWT, mémoire seule, affichage/navigation uniquement, sans effet sur Spring Security**) |
| Concevoir la base | MySQL | Modèle de données | CONÇU |
| Utiliser Redis | Cache et QR | Tests | À FAIRE |
| Importer les données | CSV/XLSX | Démonstration | À FAIRE |
| Produire des rapports | CSV/Excel | Exports | À FAIRE |
| Utiliser l’IA | Mapping intelligent | FastAPI et scores | À FAIRE |
| Sécuriser l’application | Spring Security | Tests | À FAIRE |
| Tester la plateforme | Tests | Rapports de tests | À FAIRE |
| Documenter l’API | OpenAPI | Swagger | À FAIRE |
| Former les utilisateurs | Guides | Documents | À FAIRE |

---

# 4. Bloc 3 — Infrastructure et cybersécurité

| Compétence projet | Réalisation | Preuve | Statut |
|---|---|---|---|
| Concevoir l’infrastructure | Local/staging/AWS | Diagrammes | CONÇU |
| Conteneuriser | Docker Compose | Fichiers Compose | À FAIRE |
| Séparer les environnements | 4 profils | Configuration | À FAIRE |
| Authentifier | Password/WebAuthn/MFA | Tests | À FAIRE |
| Autoriser | RBAC et périmètres | Tests `403` | IMPLÉMENTÉ |
| Protéger les sessions | Cookies sécurisés | Configuration | À FAIRE |
| Limiter les attaques | Redis/Turnstile | Tests | À FAIRE |
| Protéger les données | MySQL/fichiers | Tests | À FAIRE |
| Auditer | AuditEvent | Écran et données | IMPLÉMENTÉ |
| Superviser | Actuator | Health checks | À FAIRE |
| Sauvegarder | MySQL/fichiers | Script | À FAIRE |
| Restaurer | Procédure | Rapport de test | À FAIRE |
| Gérer les incidents | Procédure | Sécurité/RGPD | CONÇU |
| Détecter les anomalies | IA/règles | Alertes | À FAIRE |
| Assurer la résilience | Modes dégradés | Tests | À FAIRE |

---

# 5. Bloc 4 — IoT sécurisé et IA

| Compétence projet | Réalisation | Preuve | Statut |
|---|---|---|---|
| Développer un objet connecté | Client Raspberry Pi | Code Python | À FAIRE |
| Intégrer l’objet au SI | MQTT → Spring Boot | Démonstration | À FAIRE |
| Identifier le dispositif | Device ID/credential | Configuration | À FAIRE |
| Sécuriser les événements | Anti-rejeu | Tests | À FAIRE |
| Gérer la télémétrie | Heartbeat | Logs | À FAIRE |
| Gérer la coupure réseau | File locale | Test | À FAIRE |
| Analyser les événements | IA/règles | Alertes | À FAIRE |
| Détecter une anomalie | Score | Écran | À FAIRE |
| Préparer l’évolution NFC | Architecture | Dossier | CONÇU |
| Préparer AWS IoT Core | Architecture cible | Diagramme | CONÇU |

---

# 6. Matrice exigence → code → test → preuve

| ID | Exigence | Code cible | Test cible | Preuve | Bloc |
|---|---|---|---|---|---|
| TR-001 | Connexion | `identity` | TA-001 | Swagger/capture | BC02/BC03 |
| TR-002 | Rôles | `identity` | TZ-001 à 010 | Tests | BC03 |
| TR-003 | Import apprenants | `enrollment` | TI-001 à 012 | Vidéo | BC02 |
| TR-004 | Import planning | `planning` | TI-013 à 017 | Capture | BC02 |
| TR-005 | Publication | `planning` | Intégration | Séances créées | BC02 |
| TR-006 | QR | `attendance` | TE-001 à 007 | Démo | BC02/BC03 |
| TR-007 | WebAuthn | `identity` | TA-008 | Démo | BC02/BC03 |
| TR-008 | Rapports | `reporting` | REC-004 | Export | BC02 |
| TR-009 | Audit | `audit` | Contrôle DB | Écran | BC03 |
| TR-010 | IA | `ai-service` | TIA | Scores | BC02/BC03 |
| TR-011 | MQTT | `iot` | TO | Logs | BC04 |
| TR-012 | Anti-rejeu | `iot` | TO-003 | Refus doublon | BC03/BC04 |
| TR-013 | Sauvegarde | scripts | TR-007 | Rapport | BC03 |
| TR-014 | Pilotage | docs | Revue | Backlog | BC01 |
| TR-015 | Invitation / activation de compte | `identity`, `notification` | `AccountInvitation*Tests` | `./mvnw test` + Mailpit | BC02/BC03 |
| TR-016 | Administration des comptes et des rôles | `identity`, `audit` | `UserManagement*Tests` | `./mvnw test` | BC02/BC03 |
| TR-017 | Référentiel organisationnel (site/bâtiment/salle/plage réseau) | `organization`, `identity`, `audit` | `Organization*Tests`, `CidrValidatorTests` | `./mvnw test` (V4 appliquée) | BC02/BC03 |
| TR-018 | Référentiel académique (formation/niveau/année/promotion/classe) | `academic`, `organization`, `identity`, `audit` | `Academic*Tests` | `./mvnw clean test` (V5 appliquée) | BC02/BC03 |
| TR-019 | Périmètre pédagogique (affectation responsable → formation, contrôle d'accès sur formation/niveau/promotion/classe) | `academic`, `identity`, `audit` | `PedagogicalAssignment*Tests`, `PedagogicalScopeIntegrationTests`, `PedagogicalAssignmentIntegrationTests` | `./mvnw clean test` (V6 appliquée) | BC02/BC03 |
| TR-020 | Inscriptions historiques (profil apprenant, inscription apprenant → classe/année, changement de classe conservant l'historique) | `enrollment`, `academic`, `identity`, `audit` | `EnrollmentServiceTests`, `StudentProfileServiceTests`, `EnrollmentConstraintsTests`, `EnrollmentIntegrationTests`, `EnrollmentSecurityTests`, `ClassGroupDirectoryTests` | `./mvnw clean test` (V7 appliquée) | BC02/BC03 |

## Avancement vérifié — 28 août 2026

- **TR-002 (Rôles)** : `IMPLÉMENTÉ` et `TESTÉ` — persistance
  (`identity/internal` : `UserAccount`, `Role`, `UserRole` ; migrations
  Flyway `V1`/`V2` ; 6 rôles système seedés ; unicité d'une affectation
  active + réattribution après clôture) **et** désormais portés dans le
  jeton JWT émis à la connexion (claim `roles`, autorités
  `ROLE_<code>`, filtrées aux affectations actives) **et** exposés par
  une API d'administration dédiée (voir TR-016 : attribution / retrait
  auditée, clôture sans suppression, historique conservé). Le contrôle
  d'accès par rôle sur les autres routes métier (TZ-001 à 010) reste
  `REPORTÉ` : elles n'existent pas encore.
- **TR-009 (Audit)** : `IMPLÉMENTÉ` et `TESTÉ`, alimenté par des flux
  métier réels (connexion réussie/refusée ; émission d'invitation et
  activation ; suspension, réactivation, archivage d'un compte ;
  attribution et retrait d'un rôle), plus seulement par test direct de
  persistance : événements applicatifs découplés
  (`identity.LoginSucceededEvent`/`LoginFailedEvent` /
  `AccountLifecycleEvent` → `audit/internal.*Listener`, transaction
  dédiée `REQUIRES_NEW`), acteur nullable conservé pour un email inconnu
  sans jamais stocker l'adresse brute, échec de journalisation vérifié
  sans impact sur la réponse d'authentification. Pour les actions
  d'administration, le compte concerné est porté par `resource_public_id`
  et l'acteur (administrateur) par `actor_user_id` ; seul un motif non
  sensible est journalisé.
- **TR-001 (Connexion)** : `IMPLÉMENTÉ` et `TESTÉ` —
  `POST /api/v1/auth/login` (email/mot de passe, `UserDetailsService`
  standard, `PasswordEncoder` délégué BCrypt, `AuthenticationManager`
  standard), JWT HS256 stateless (Spring Security OAuth2 Resource
  Server + Nimbus, sujet = identifiant public, `iat`/`exp`/`jti`/`roles`,
  aucune donnée personnelle), réponse publique strictement uniforme
  vérifiée pour email inconnu/mauvais mot de passe/compte non actif,
  `last_login_at` mis à jour. MFA et WebAuthn restent `REPORTÉ`
  (hors périmètre de cette tâche).
- **TR-015 (Invitation / activation de compte)** : `IMPLÉMENTÉ` et
  `TESTÉ` — `POST /api/v1/account-invitations` (protégé par
  `@PreAuthorize` : `ADMIN`, `SUPER_ADMIN`, `PEDAGOGICAL_MANAGER`,
  `SCHOOL_ADMINISTRATION` ; émission limitée aux comptes
  `PENDING_ACTIVATION` ; attribution du rôle demandé via `user_role` ;
  rôle inconnu ou inactif refusé), `GET …/validate` et `POST …/activate`
  publics. Jeton `SecureRandom` 32 octets Base64URL sans padding, seule
  l'empreinte SHA-256 est stockée (`account_invitation`, migration `V3`,
  `token_hash` UNIQUE, une seule invitation `PENDING` par compte via
  colonne générée), TTL configurable (`P30D` par défaut, refus de
  démarrage si ≤ 0), révocation des invitations `PENDING` antérieures,
  jeton à usage unique. Validation publique strictement générique
  (`{"valid": bool}`, réponse identique pour jeton inconnu / expiré /
  révoqué / accepté). Activation : mot de passe encodé (BCrypt), statut
  `ACTIVE`, `email_verified_at`. Email d'activation via Mailpit (module
  `notification`, écouteur `AFTER_COMMIT`, échec avalé sans jeton/email/
  lien dans les logs — file persistante = dette technique). Audit
  `ACCOUNT_INVITATION_ISSUED` / `ACCOUNT_ACTIVATED` sans jeton.
  `@PreAuthorize` refusé désormais traduit en `403` neutre
  (`GlobalExceptionHandler`). Côté front-end (branche
  `feature/frontend-account-activation`) : route publique `/activation`
  sans garde consommant `GET …/validate` (jeton en paramètre de requête)
  et `POST …/activate` (`{ token, password }` → `204`), jeton lu puis
  retiré de la barre d'adresse, jamais journalisé ni stocké ni envoyé en
  bearer ; intercepteurs excluant ces endpoints publics ; état terminal
  unique pour `INVITATION_INVALID` ; aucune session créée à l'activation.
- **TR-016 (Administration des comptes et des rôles)** : `IMPLÉMENTÉ` et
  `TESTÉ` — `GET /api/v1/users` (liste paginée, taille bornée à 100 /
  défaut 20 ; filtres `status`, `role` sur affectation active, `q`
  normalisé et `LIKE` échappé ; tri restreint à une liste blanche
  `createdAt`/`lastLoginAt`/`email`/`lastName`), `GET
  /api/v1/users/{public_id}` (détail + historique complet des rôles),
  `POST …/{public_id}/suspend` · `/restore` (ACTIVE↔SUSPENDED, motif
  obligatoire, `SCHOOL_ADMINISTRATION` autorisé), `POST …/archive`
  (`ADMIN`/`SUPER_ADMIN` : statut `ARCHIVED`, clôture transactionnelle de
  tous les `user_role` actifs sans suppression, irréversible dans ce
  lot), `POST …/roles` et `…/roles/{roleCode}/revoke`
  (`ADMIN`/`SUPER_ADMIN` : attribution = nouvelle ligne, retrait =
  clôture, retrait du dernier rôle actif refusé). Contrôles fins doublés
  dans `UserManagementService` en complément des `@PreAuthorize` : un
  compte `SUPER_ADMIN` (y compris sa réactivation) et le rôle
  `SUPER_ADMIN` ne sont administrables que par un `SUPER_ADMIN`, et un
  `ADMIN` ne peut modifier *aucun* rôle d'un compte `SUPER_ADMIN` ;
  auto-suspension / auto-réactivation / auto-archivage / retrait de son
  propre rôle interdits (y compris pour un `SUPER_ADMIN`). Direction de
  tri invalide refusée (jamais réinterprétée en ASC). DTO exposant
  uniquement `public_id` (jamais l'id
  SQL, le `password_hash` ni un jeton). Audit `ACCOUNT_SUSPENDED` /
  `ACCOUNT_REACTIVATED` / `ACCOUNT_ARCHIVED` / `ROLE_ASSIGNED` /
  `ROLE_REVOKED`. Aucune migration `V4` (colonnes déjà en `V1`).
  `PEDAGOGICAL_MANAGER` exclu tant que le périmètre pédagogique n'est pas
  implémenté. Tests : `UserManagementServiceTests` (unitaires),
  `UserManagementIntegrationTests`, `UserManagementSecurityTests`,
  `ModularityTests`.
- **TR-017 (Référentiel organisationnel)** : `IMPLÉMENTÉ` et `TESTÉ` —
  nouveau module `organization` (élargit et remplace le module `room`
  prévu par l'architecture, docs/03 §7.6) + migration Flyway `V4`
  (`site`, `building`, `room`, `site_network_range` ; schéma en
  version 4). Hiérarchie site → bâtiment → salle avec conventions
  techniques complètes (`public_id`, `created_at/by`, `updated_at/by`,
  `version`, `status`, `archived_at/by`, `archive_reason` ;
  `site_network_range` reçoit en plus `public_id`, `updated_at`,
  `version`). CRUD + archivage logique + restauration (site/bâtiment/
  salle), plages réseau en création + activation/désactivation ;
  **aucun DELETE physique**, `code` et rattachement au site immuables.
  Consultation paginée (max 100, défaut 20) + filtres + tri liste
  blanche ; routes exclusivement en `public_id`. Règles vérifiées :
  refus building/room sous parent archivé, `room.site` = `building.site`,
  archivage d'un site/bâtiment refusé tant qu'il reste des enfants
  actifs, unicités `site.code` / `(site,code)` / `(site,cidr)` active.
  Validations réelles : fuseau IANA (`ZoneId`), code pays ISO 3166-1
  alpha-2, CIDR IPv4 **et** IPv6 (préfixes bornés 0..32 / 0..128, sans
  résolution DNS). `@PreAuthorize` : lecture site/bâtiment/salle =
  `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`/`PEDAGOGICAL_MANAGER`,
  écriture = `ADMIN`/`SUPER_ADMIN`, `site_network_range` = `SUPER_ADMIN`
  pour **toute** opération, consultation comprise. DTO sans identifiant
  SQL interne ni colonne auteur. Audit `SITE_*` / `BUILDING_*` /
  `ROOM_*` / `SITE_NETWORK_RANGE_*` (catégorie `ORGANIZATION`,
  transaction `REQUIRES_NEW`, motif non sensible — code, CIDR —, jamais
  d'IP ni de donnée personnelle) via `organization.OrganizationChangeEvent`
  → `audit.internal.OrganizationAuditListener`. Port public
  `identity.CurrentUserResolver` (résout l'id interne depuis le sujet du
  JWT ; n'expose ni `UserAccount`, ni repository, ni classe
  `identity.internal`). Aucun site fictif ni donnée métier en `V4`.
  Tests : `CidrValidatorTests`, `OrganizationServiceTests` (Mockito),
  `OrganizationConstraintsTests` (`@DataJpaTest`),
  `OrganizationIntegrationTests`, `OrganizationSecurityTests`,
  `ModularityTests` (frontières du nouveau module respectées).
- **TR-018 (Référentiel académique)** : `IMPLÉMENTÉ` et `TESTÉ` —
  nouveau module `academic` (docs/03 §7.2) + migration Flyway `V5`
  (`academic_year`, `program`, `program_level`, `promotion`,
  `class_group` ; schéma en version 5). Hiérarchie
  formation → promotion → classe/groupe ; `academic_year` et
  `program_level` inclus comme support des FK de `promotion` et
  `class_group`. Périmètre limité : ni inscriptions, ni matières, ni
  responsabilités pédagogiques, ni Angular. CRUD + archivage logique +
  restauration des 5 entités, **aucun DELETE physique**, `code` et
  rattachements parents immuables. Consultation paginée (max 100,
  défaut 20) + filtres (`status`, `q` ; promotions par
  `program`/`academicYear` ; classes par
  `promotion`/`programLevel`/`site`) + tri liste blanche ; routes
  exclusivement en `public_id`. Règles vérifiées : `end_date >
  start_date` (année), période de promotion incluse dans celle de
  l'année, `program_level` d'une classe = même formation que sa
  promotion, refus de création sous parent archivé, archivage refusé
  tant qu'il reste des enfants actifs, restauration refusée sous parent
  archivé, unicités `academic_year.code` / `program.code` /
  `(program,code)` / `(program,academicYear,code)` / `(promotion,code)`.
  `@PreAuthorize` : lecture =
  `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`/`PEDAGOGICAL_MANAGER`,
  écriture = `ADMIN`/`SUPER_ADMIN` (écriture `PEDAGOGICAL_MANAGER`
  reportée au périmètre pédagogique T-J1-023 ; `TEACHER`/`STUDENT`
  exclus). DTO sans identifiant SQL interne ni colonne auteur. Audit
  `ACADEMIC_YEAR_*` / `PROGRAM_*` / `PROGRAM_LEVEL_*` / `PROMOTION_*` /
  `CLASS_GROUP_*` (catégorie `ACADEMIC`, transaction `REQUIRES_NEW`,
  motif non sensible — code —, jamais de donnée personnelle) via
  `academic.AcademicChangeEvent` → `audit.internal.AcademicAuditListener`.
  Nouveau port public `organization.SiteDirectory` (impl
  `organization.internal.DefaultSiteDirectory`) : `class_group.site_id`
  est une valeur technique, aucun import de `organization.internal`
  depuis `academic`, aucune relation JPA inter-module. Aucune donnée
  fictive en `V5`. Passe corrective : restauration d'une classe vérifiant
  toute la chaîne de rattachement (promotion, sa formation, son année, le
  niveau, la formation du niveau, le site présent et actif) + invariant
  niveau↔formation revérifié ; modification de la période d'une année
  refusée si elle exclut une promotion existante à période renseignée
  (`ACAD_ACADEMIC_YEAR_PERIOD_CONFLICT`) ; résolution du site sans retour
  `null` silencieux. Tests : `AcademicServiceTests` (Mockito),
  `AcademicConstraintsTests` (`@DataJpaTest`, unicités + FK `RESTRICT`
  année→promotion / niveau→classe / site→classe + `CHECK` période et
  capacité), `AcademicIntegrationTests`, `AcademicSecurityTests`,
  `ModularityTests` (frontières du nouveau module respectées).

- **TR-019 (Périmètre pédagogique)** : `IMPLÉMENTÉ` et `TESTÉ` — table
  `pedagogical_assignment` (migration Flyway `V6`, réécrite tant qu'elle
  était non committée ; état Flyway local V6 remis à zéro, `V1`–`V5`
  intactes ; schéma de nouveau en version 6). Entité
  `academic.internal.PedagogicalAssignment` reliant un compte porteur du
  rôle `PEDAGOGICAL_MANAGER` à une formation ; rôles `PRIMARY_MANAGER`
  (un seul actif par formation via colonne générée `active_primary_key` +
  pré-contrôle applicatif `ACAD_PRIMARY_MANAGER_EXISTS` ; la course
  concurrente est gérée par un `INSERT` isolé dans
  `AssignmentPersister` (`@Transactional REQUIRES_NEW`), la
  `DataIntegrityViolationException` étant reçue **hors** transaction en
  échec et retraduite en 409 **uniquement** si la contrainte violée est
  `uq_pedagogical_assignment_active_primary` — toute autre violation
  (FK, `CHECK`, `NOT NULL`, `public_id`) est relancée intacte) /
  `DELEGATE` (multiples), statuts `ACTIVE` / `CLOSED`, validité en `LocalDate` /
  `DATE` bornes inclusives (`CHECK valid_until >= valid_from`), colonnes
  `reason` / `close_reason` / `delegated_by_id` — le créneau du
  responsable principal n'est libéré que par une clôture explicite. Cible
  contrôlée via le port `identity.UserDirectory` (compte existant, non
  archivé, rôle actif `PEDAGOGICAL_MANAGER`, sinon 422
  `ACAD_TARGET_NOT_ELIGIBLE`). Routes minimales
  `/api/v1/pedagogical-assignments` (GET liste — filtres `program` /
  `user` / `type` / `status` / `activeOn` en dates inclusives, tri liste
  blanche stricte —, GET détail, POST création, POST `{id}/close` —
  `reason` obligatoire, `effectiveDate` par défaut aujourd'hui, `>=
  validFrom` sinon 400 `ACAD_ASSIGNMENT_DATE_INVALID`, persiste
  `validUntil`), réservées `ADMIN`/`SUPER_ADMIN` ; aucun `PATCH`, aucun
  `DELETE`, aucune route nichée.
  Contrôle d'accès par périmètre **centralisé** dans
  `AcademicScopeGuard` et branché sur **formation, niveau, promotion,
  classe** : accès global = autorité `ROLE_ADMIN` / `ROLE_SUPER_ADMIN` /
  `ROLE_SCHOOL_ADMINISTRATION` (déduit du contexte Spring Security,
  jamais d'un paramètre client) ; sinon lecture des listes filtrée par
  sous-requête `IN` sur les formations du périmètre effectif (affectation
  `ACTIVE` couvrant le jour courant), et détail comme
  create/update/archive/restore hors périmètre → `403 ACAD_FORBIDDEN`.
  Écriture ouverte au `PEDAGOGICAL_MANAGER` dans son périmètre
  (`SCOPED_WRITE_ROLES`) ; création d'une formation réservée à
  `ADMIN`/`SUPER_ADMIN` ; `AcademicYear` inchangé et global. Audit
  `PEDAGOGICAL_ASSIGNMENT_CREATED` / `_CLOSED` (catégorie `ACADEMIC`,
  motif `program=<code>;type=<type>`) via `AcademicChangeEvent` →
  `audit.internal.AcademicAuditListener` (inchangé). Horloge
  `java.time.Clock` injectée (`shared.config.ClockConfig`) dans
  `AcademicScopeGuard` et `PedagogicalAssignmentService` en lieu et place
  de `LocalDate.now()`. `docs/03` et `docs/04` non modifiés. Tests :
  `PedagogicalAssignmentServiceTests` (Mockito — persister mocké, horloge
  figée ; retraduction de collision `active_primary` avec message SQL
  réaliste, relance intacte d'une violation FK, dates par défaut sur
  l'horloge, clôture avant `validFrom`),
  `AcademicScopeGuardTests` (Mockito + `SecurityContextHolder` +
  `Clock.fixed` — global/limité, requêtes de périmètre datées par
  l'horloge injectée), `PedagogicalAssignmentConstraintsTests`
  (`@DataJpaTest`, exceptions de persistance précises —
  `ConstraintViolationException` pour les FK `RESTRICT` ; reconnaissance
  d'une vraie collision `active_primary` et rejet d'une violation
  `public_id`), `PedagogicalScopeIntegrationTests` (`@SpringBootTest` —
  scope sur les quatre entités, cumuls de rôles, school-admin),
  `PedagogicalAssignmentIntegrationTests` (`@SpringBootTest` — filtres,
  clôture, éligibilité, **deux créations concurrentes → un 201 + un
  409**, matrice d'autorisation), `ModularityTests`.

- **TR-020 (Inscriptions historiques)** : `IMPLÉMENTÉ` et `TESTÉ` —
  nouveau module `enrollment` (docs/03 §7.3) + migration Flyway `V7`
  (`student_profile`, `enrollment` ; schéma en version 7). Profil
  apprenant (`user_id` valeur technique via `identity.UserDirectory`,
  unique ; `student_number` unique) et inscription d'un apprenant dans
  une classe pour une année scolaire, `class_group_id` /
  `academic_year_id` résolus via le **nouveau port public**
  `academic.ClassGroupDirectory` (n'expose ni `ClassGroup` ni
  repository ; `openForEnrollment` faux si la classe ou un maillon de sa
  chaîne — promotion, formation, année — est archivé). Règle RG-012 /
  docs/04 §13.3 : au plus une inscription `ACTIVE` par apprenant et par
  année scolaire — pré-contrôle applicatif
  (`ENR_ACTIVE_ENROLLMENT_EXISTS`, 409) **et** contrainte SQL
  `uq_enrollment_active_per_year` (deux colonnes générées `VIRTUAL`) ;
  la collision concurrente est retraduite en 409 ciblé par
  `EnrollmentExceptionHandler` (jamais un 500 générique ; aucun
  `catch (Exception)`). Changement de classe
  (`POST /api/v1/enrollments/{id}/transfer`, docs/04 §13.2) : ancienne
  inscription clôturée en `TRANSFERRED` (`end_date`, **historique
  conservé et consultable — AC-006**), nouvelle inscription `ACTIVE`
  liée par `previous_enrollment_id`. Clôture explicite
  (`COMPLETED`/`WITHDRAWN`, motif obligatoire, horloge injectée).
  Routes en `public_id` sous `/api/v1/student-profiles` et
  `/api/v1/enrollments` (liste filtrée + tri liste blanche + pagination
  max 100, détail, création, `transfer`, `close`), `@PreAuthorize`
  `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION` (cahier §6.4, §10.1 ;
  `PEDAGOGICAL_MANAGER` reporté à un port de périmètre pédagogique
  public ; `TEACHER`/`STUDENT` exclus). Aucun `PATCH`, aucun `DELETE`,
  aucune suppression physique. DTO sans identifiant SQL interne. Audit
  `STUDENT_PROFILE_CREATED` / `ENROLLMENT_CREATED` / `_TRANSFERRED` /
  `_CLOSED` (catégorie `ENROLLMENT`, transaction `REQUIRES_NEW`, motif
  non sensible — codes classe/année/statut —, jamais de numéro étudiant,
  de nom ni d'adresse) via `enrollment.EnrollmentChangeEvent` →
  `audit.internal.EnrollmentAuditListener`. Tests :
  `EnrollmentServiceTests` (17, Mockito, `Clock.fixed`),
  `StudentProfileServiceTests` (7, Mockito),
  `EnrollmentConstraintsTests` (12, `@DataJpaTest` — unicités + FK
  `RESTRICT` + `CHECK` de période + reconnaissance ciblée de la
  collision), `ClassGroupDirectoryTests` (3, `@SpringBootTest`),
  `EnrollmentIntegrationTests` (9, `@SpringBootTest` — cycle complet,
  audit, **deux créations concurrentes → un 201 + un 409**, refus sous
  classe archivée, matrice d'autorisation), `EnrollmentSecurityTests`
  (3), `ModularityTests`. `application-test.yml` : pool HikariCP
  plafonné (`maximum-pool-size: 6`) — un contexte Spring (et un pool)
  étant mis en cache par classe `@SpringBootTest`, MySQL saturait
  (« Too many connections ») ; aucun test métier existant modifié.
  `docs/03` et `docs/04` non modifiés.

Preuve : `backend/src/test/java/com/esic/connect/identity/`,
`backend/src/test/java/com/esic/connect/notification/`,
`backend/src/test/java/com/esic/connect/audit/`,
`backend/src/test/java/com/esic/connect/organization/`,
`backend/src/test/java/com/esic/connect/academic/`,
`backend/src/test/java/com/esic/connect/enrollment/`, exécution réelle
de `./mvnw clean test` (**314/314**, `BUILD SUCCESS`, lancé deux fois
après le lot inscriptions historiques ; **263/263** au lot périmètre
pédagogique précédent — isolation transactionnelle de la collision +
injection de `Clock` ; **214/214** au lot académique) — voir
`docs/CURRENT-STATE.md`. Émetteur JWT vérifié explicitement
(`JwtValidators`), jeton à émetteur incorrect refusé (401 nu, aucun
détail de validation exposé).

---

# 7. Mise à jour

Après chaque fonctionnalité :

1. renseigner le statut ;
2. ajouter le chemin du code ;
3. ajouter la commande de test ;
4. ajouter la preuve ;
5. ajouter le commit ;
6. identifier le bloc concerné.

---

# 8. Couverture finale attendue

## BC01

Preuves documentaires et de pilotage.

## BC02

Application fonctionnelle, IA, UX, base, imports et rapports.

## BC03

Infrastructure, sécurité, audit, tests, supervision et résilience.

## BC04

Raspberry Pi, MQTT, sécurité IoT et analyse des événements.

La fiche officielle exige que les quatre parties soient présentées dans
le rapport d’expérience professionnelle.