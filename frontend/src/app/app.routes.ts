import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { roleGuard } from './core/guards/role.guard';

const APP_NAME = 'ESIC Connect';

/**
 * Périmètre de lecture du référentiel académique, repris **à l'identique**
 * de `AcademicWeb.READ_ROLES` (`GET /api/v1/academic-years`,
 * `/programs`, `/promotions`, `/class-groups`, `/program-levels`). Le
 * garde ne fait que masquer la navigation : Spring Security reste
 * l'autorité, et un `PEDAGOGICAL_MANAGER` reste filtré par périmètre
 * (`AcademicScopeGuard`) — un `403` de l'API est rendu « accès refusé ».
 */
const ACADEMIC_READ_ROLES = [
  'ADMIN',
  'SUPER_ADMIN',
  'SCHOOL_ADMINISTRATION',
  'PEDAGOGICAL_MANAGER',
] as const;

/**
 * Périmètre de consultation de l'alternance, repris de
 * `AlternationWeb.PATTERN_READ_ROLES` / `SCOPED_ROLES`. Un
 * `PEDAGOGICAL_MANAGER` reste restreint à son périmètre **côté serveur**
 * (`AcademicScopeDirectory`) : un `403 ALT_FORBIDDEN` est rendu « accès
 * refusé ». L'écriture des modèles de rythme est en plus limitée à
 * `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION` par un garde de route
 * dédié sur les écrans de création / modification.
 */
const ALTERNATION_READ_ROLES = [
  'ADMIN',
  'SUPER_ADMIN',
  'SCHOOL_ADMINISTRATION',
  'PEDAGOGICAL_MANAGER',
] as const;
const ALTERNATION_PATTERN_WRITE_ROLES = [
  'ADMIN',
  'SUPER_ADMIN',
  'SCHOOL_ADMINISTRATION',
] as const;

/**
 * Référentiel organisationnel (`com.esic.connect.organization`) : lecture
 * ouverte à `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION` /
 * `PEDAGOGICAL_MANAGER` (`SiteController.READ_ROLES`) ; écriture des sites
 * / bâtiments / salles restreinte à `ADMIN` / `SUPER_ADMIN`
 * (`SiteController.WRITE_ROLES`). Les plages réseau — lecture comprise —
 * sont réservées à `SUPER_ADMIN` côté serveur (`SiteNetworkRangeController`,
 * `@PreAuthorize` de classe) ; leur panneau n'apparaît dans la fiche d'un
 * site que pour ce contexte. Spring Security reste l'autorité : un `403`
 * est rendu « accès refusé ».
 */
const ORGANIZATION_READ_ROLES = [
  'ADMIN',
  'SUPER_ADMIN',
  'SCHOOL_ADMINISTRATION',
  'PEDAGOGICAL_MANAGER',
] as const;
const ORGANIZATION_WRITE_ROLES = ['ADMIN', 'SUPER_ADMIN'] as const;

/**
 * Import, versionnement et publication d'un planning
 * (`com.esic.connect.planning`). Périmètre repris **à l'identique** de
 * `PlanningWeb.MANAGE_ROLES` ; un `PEDAGOGICAL_MANAGER` est en plus
 * filtré par périmètre pédagogique côté serveur
 * (`AcademicScopeDirectory`) et ne voit que ses propres jobs. Spring
 * Security reste l'autorité : un `403` est rendu « accès refusé ».
 */
const PLANNING_MANAGE_ROLES = [
  'ADMIN',
  'SUPER_ADMIN',
  'SCHOOL_ADMINISTRATION',
  'PEDAGOGICAL_MANAGER',
] as const;

/**
 * Séances : lecture ouverte à
 * `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION` / `PEDAGOGICAL_MANAGER` / `TEACHER`
 * (`CourseSessionWeb.READ_ROLES`) ; un `TEACHER` ne voit que ses séances
 * et un `PEDAGOGICAL_MANAGER` que son périmètre, **décidé côté serveur**.
 * La création est en plus restreinte à
 * `ADMIN` / `SUPER_ADMIN` / `PEDAGOGICAL_MANAGER` (`CourseSessionWeb.CREATE_ROLES`).
 */
const SESSION_READ_ROLES = [
  'ADMIN',
  'SUPER_ADMIN',
  'SCHOOL_ADMINISTRATION',
  'PEDAGOGICAL_MANAGER',
  'TEACHER',
] as const;
const SESSION_CREATE_ROLES = ['ADMIN', 'SUPER_ADMIN', 'PEDAGOGICAL_MANAGER'] as const;

/**
 * Suivi d'assiduité : rapports agrégés et examen des justificatifs.
 * Repris de `AttendanceManagementWeb.REPORT_ROLES` / `REVIEW_LIST_ROLES` ;
 * un `TEACHER` consulte les présences de ses séances via `/sessions`.
 */
const ATTENDANCE_MANAGE_ROLES = [
  'ADMIN',
  'SUPER_ADMIN',
  'SCHOOL_ADMINISTRATION',
  'PEDAGOGICAL_MANAGER',
] as const;

const academicList = () =>
  import('./features/academic/academic-reference-list/academic-reference-list').then(
    (m) => m.AcademicReferenceList,
  );
/**
 * Lecture du catalogue des matières, repris de
 * `SubjectController.SUBJECT_READ_ROLES` : un formateur doit pouvoir
 * qualifier une séance. L'écriture reste fermée côté serveur.
 */
const SUBJECT_READ_ROLES = [
  'ADMIN',
  'SUPER_ADMIN',
  'SCHOOL_ADMINISTRATION',
  'PEDAGOGICAL_MANAGER',
  'TEACHER',
] as const;

/**
 * Suivi des invitations et de la délivrabilité, repris de
 * `AccountInvitationController` et `EmailDeliveryController`.
 */
const INVITATION_TRACKING_ROLES = [
  'ADMIN',
  'SUPER_ADMIN',
  'SCHOOL_ADMINISTRATION',
  'PEDAGOGICAL_MANAGER',
] as const;

const academicDetail = () =>
  import('./features/academic/academic-reference-detail/academic-reference-detail').then(
    (m) => m.AcademicReferenceDetail,
  );

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'login',
    canActivate: [guestGuard],
    title: `Connexion — ${APP_NAME}`,
    loadComponent: () =>
      import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    // Deuxième étape de connexion (EF-AUTH-008, AC-021). Sous
    // `guestGuard` : une session déjà ouverte n'a rien à vérifier. Le
    // défi n'est pas dans l'URL — il vaut preuve de la première étape et
    // ne doit pas entrer dans l'historique du navigateur ; il transite
    // par `PendingChallengeStore`, en mémoire. Une arrivée directe sur
    // cette route renvoie donc vers la connexion.
    path: 'connexion/verification',
    canActivate: [guestGuard],
    title: `Vérification en deux étapes — ${APP_NAME}`,
    loadComponent: () =>
      import('./features/auth/mfa-challenge/mfa-challenge').then((m) => m.MfaChallenge),
  },
  {
    // Parcours PUBLIC : demander un lien de réinitialisation
    // (EF-AUTH-005). Sous `guestGuard` — un utilisateur déjà connecté n'a
    // rien à y faire, il change son mot de passe depuis son profil.
    path: 'mot-de-passe-oublie',
    canActivate: [guestGuard],
    title: `Mot de passe oublié — ${APP_NAME}`,
    loadComponent: () =>
      import('./features/auth/forgot-password/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    // Parcours PUBLIC atteint via le lien reçu par courriel
    // (`/reinitialisation?token=…`). Aucune garde : comme pour
    // l'activation, le jeton fait foi, indépendamment d'une éventuelle
    // session en mémoire.
    path: 'reinitialisation',
    title: `Nouveau mot de passe — ${APP_NAME}`,
    loadComponent: () =>
      import('./features/auth/reset-password/reset-password').then((m) => m.ResetPassword),
  },
  {
    // Parcours PUBLIC atteint via le lien d'invitation du back-end
    // (`/activation?token=…`). Aucune garde : le jeton d'invitation fait
    // foi, indépendamment d'une éventuelle session en mémoire.
    path: 'activation',
    title: `Activation du compte — ${APP_NAME}`,
    loadComponent: () =>
      import('./features/account-activation/account-activation').then((m) => m.AccountActivation),
  },
  {
    path: '',
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    loadComponent: () =>
      import('./core/layout/app-shell/app-shell').then((m) => m.AppShell),
    children: [
      {
        path: 'dashboard',
        title: `Tableau de bord — ${APP_NAME}`,
        loadComponent: () =>
          import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        // Écran livré (sprint 3) : référentiel des matières (EF-ACA-006).
        // Périmètre repris de `SubjectController` — lecture ouverte aux
        // formateurs, écriture aux rôles de gestion, le serveur restant
        // l'autorité (un `403` est rendu « accès refusé »).
        path: 'subjects',
        canActivate: [roleGuard],
        data: { roles: SUBJECT_READ_ROLES },
        title: `Matières — ${APP_NAME}`,
        loadComponent: () =>
          import('./features/subjects/subject-list/subject-list').then((m) => m.SubjectList),
      },
      {
        // Écran livré (sprint 3) : suivi des invitations et de leur
        // délivrabilité (EF-USER-007, EF-USER-008).
        path: 'invitations',
        canActivate: [roleGuard],
        data: { roles: INVITATION_TRACKING_ROLES },
        title: `Invitations — ${APP_NAME}`,
        loadComponent: () =>
          import('./features/invitations/invitation-list/invitation-list').then(
            (m) => m.InvitationList,
          ),
      },
      {
        // Sécurité du compte de l'appelant : second facteur, clés d'accès,
        // appareils reconnus (EF-AUTH-006, 008, 009, 013). Aucune garde de
        // rôle — chacun gère ses propres moyens d'authentification, et le
        // serveur déduit le périmètre du sujet du jeton.
        path: 'mon-compte/securite',
        title: `Sécurité de mon compte — ${APP_NAME}`,
        loadComponent: () =>
          import('./features/account/security/account-security').then((m) => m.AccountSecurity),
      },
      {
        // Espace « Notifications » : une seule entrée latérale, deux vues
        // internes en onglets (liste / préférences). Les anciennes URL
        // `/notifications` et `/notifications/preferences` restent valides
        // — ce sont les chemins des enfants. Aucune garde de rôle :
        // `NotificationController` et `NotificationPreferenceController`
        // portent `@PreAuthorize("isAuthenticated()")` et l'isolation par
        // destinataire est faite côté serveur.
        path: 'notifications',
        loadComponent: () =>
          import('./features/notifications/notifications-shell/notifications-shell').then(
            (m) => m.NotificationsShell,
          ),
        children: [
          {
            path: '',
            title: `Notifications — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/notifications/notification-list/notification-list').then(
                (m) => m.NotificationList,
              ),
          },
          {
            path: 'preferences',
            title: `Préférences de notification — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/notifications/preferences/notification-preferences').then(
                (m) => m.NotificationPreferences,
              ),
          },
        ],
      },
      {
        // Recherche globale (EF-USER-009 ; docs/02 §22.7). Périmètre
        // aligné **à l'identique** sur le `@PreAuthorize` de
        // `GlobalSearchController` : seuls les rôles qui disposent d'un
        // périmètre à parcourir. Le garde ne fait que masquer la
        // navigation — le serveur applique le périmètre réel.
        path: 'recherche',
        canActivate: [
          roleGuard(['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER']),
        ],
        title: `Recherche globale — ${APP_NAME}`,
        loadComponent: () =>
          import('./features/search/global-search').then((m) => m.GlobalSearch),
      },
      {
        // Consultation et export de la piste d'audit (EF-AUD-002 ;
        // docs/02 §23.4). Périmètre aligné sur `AuditController`
        // (`ADMIN` / `SUPER_ADMIN`) : l'apprenant dispose de son journal
        // de transparence, qui est la bonne granularité pour lui.
        path: 'exploitation/audit',
        canActivate: [roleGuard(['ADMIN', 'SUPER_ADMIN'])],
        title: `Piste d'audit — ${APP_NAME}`,
        loadComponent: () =>
          import('./features/audit/audit-trail').then((m) => m.AuditTrail),
      },
      {
        // Attestations d'assiduité (EF-REP-006, AC-033). Périmètre aligné
        // sur `AttendanceManagementWeb.REPORT_ROLES` — un `TEACHER` n'y a
        // pas accès, il consulte les présences de ses séances.
        path: 'attestations',
        canActivate: [
          roleGuard(['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER']),
        ],
        title: `Attestations — ${APP_NAME}`,
        loadComponent: () =>
          import('./features/attestations/attestations').then((m) => m.Attestations),
      },
      {
        // Abonnement iCalendar de l'appelant (EF-INT-001, AC-034). Aucune
        // garde de rôle : `CalendarSubscriptionController` porte
        // `@PreAuthorize("isAuthenticated()")` et le propriétaire est le
        // sujet du JWT, jamais un paramètre.
        path: 'mon-compte/calendrier',
        title: `Abonnement calendrier — ${APP_NAME}`,
        loadComponent: () =>
          import('./features/calendar-subscriptions/calendar-subscriptions').then(
            (m) => m.CalendarSubscriptions,
          ),
      },
      {
        // Rapport des invitations non activées (EF-REP-010). Mêmes rôles
        // que le suivi des invitations : c'est le même besoin.
        path: 'invitations/non-activees',
        canActivate: [
          roleGuard(['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER']),
        ],
        title: `Invitations non activées — ${APP_NAME}`,
        loadComponent: () =>
          import('./features/invitations/pending-report/pending-invitation-report').then(
            (m) => m.PendingInvitationReport,
          ),
      },
      {
        // File d'échec des effets de bord (EF-OPS-005 ; docs/02 §34.2,
        // écrans du super administrateur). Périmètre aligné **à
        // l'identique** sur le `@PreAuthorize` de `OutboxAdminController`
        // (`ADMIN` / `SUPER_ADMIN`) : rejouer un effet de bord peut
        // envoyer un courriel, ce n'est pas une lecture. Le garde ne fait
        // que masquer la navigation — Spring Security reste l'autorité.
        path: 'exploitation/effets-de-bord',
        canActivate: [roleGuard(['ADMIN', 'SUPER_ADMIN'])],
        title: `Effets de bord — ${APP_NAME}`,
        loadComponent: () =>
          import('./features/operations/outbox/outbox-console').then((m) => m.OutboxConsole),
      },
      {
        // Administration des comptes utilisateurs et de leurs rôles, en
        // LECTURE SEULE : liste → fiche → historique des rôles. Périmètre
        // de rôles aligné **à l'identique** sur le `@PreAuthorize` de
        // `UserAccountController` (`READ_ROLES` =
        // `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION`). Le
        // `roleGuard` ne fait que masquer la navigation : Spring Security
        // reste l'autorité (un `403` API est rendu « accès refusé »).
        path: 'administration',
        canActivate: [roleGuard(['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION'])],
        canActivateChild: [roleGuard(['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION'])],
        title: `Administration des comptes — ${APP_NAME}`,
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/administration/user-list/user-list').then((m) => m.UserList),
          },
          {
            // Déclaré AVANT `:publicId` et hors de son sous-arbre — sinon
            // Angular router route « duplicates » vers `UserDetail` avec
            // `publicId = 'duplicates'` (même précaution que `students/import`
            // face à `students/:id`).
            path: 'duplicates',
            // Périmètre plus restreint (`ADMIN_ROLES`) que le reste de
            // `/administration` (`READ_ROLES`, qui inclut aussi
            // `SCHOOL_ADMINISTRATION`) — aligné sur
            // `UserAccountController.duplicates()` (EF-USER-005).
            canActivate: [roleGuard(['ADMIN', 'SUPER_ADMIN'])],
            title: `Doublons détectés — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/administration/duplicate-list/duplicate-list').then(
                (m) => m.DuplicateList,
              ),
          },
          {
            path: ':publicId',
            title: `Fiche compte — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/administration/user-detail/user-detail').then(
                (m) => m.UserDetail,
              ),
          },
        ],
      },
      {
        // Import CSV contrôlé des apprenants
        // (`com.esic.connect.studentimport`, `StudentImportWeb.MANAGE_ROLES`).
        // Déclaré AVANT `students` et hors de son sous-arbre : le parent
        // `students` restreint ses enfants à
        // `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION` (lecture des
        // profils), alors que l'import est aussi ouvert au
        // `PEDAGOGICAL_MANAGER` (limité à son périmètre côté serveur ; un
        // `403 IMP_*` est rendu « accès refusé »).
        path: 'students/import',
        canActivate: [roleGuard(['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'])],
        canActivateChild: [
          roleGuard(['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER']),
        ],
        title: `Import des apprenants — ${APP_NAME}`,
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/students/import/student-import-home/student-import-home').then(
                (m) => m.StudentImportHome,
              ),
          },
          {
            path: ':publicId',
            title: `Revue d'un import — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/students/import/student-import-review/student-import-review').then(
                (m) => m.StudentImportReview,
              ),
          },
        ],
      },
      {
        // Périmètre de rôles aligné sur `EnrollmentWeb.MANAGE_ROLES`
        // (`GET /api/v1/student-profiles`, `GET /api/v1/enrollments`).
        // Le garde ne fait que masquer la navigation : Spring Security
        // reste l'autorité (un 403 API est rendu comme « accès refusé »).
        path: 'students',
        canActivate: [roleGuard(['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION'])],
        canActivateChild: [roleGuard(['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION'])],
        title: `Apprenants — ${APP_NAME}`,
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/students/student-list/student-list').then((m) => m.StudentList),
          },
          {
            // Déclaré AVANT `:publicId` (sinon `nouveau` serait pris pour
            // un identifiant). Création manuelle d'un apprenant (Lot H) :
            // `POST /api/v1/users` exige `ADMIN` / `SUPER_ADMIN` côté
            // serveur, d'où ce garde plus restrictif que le parent.
            path: 'nouveau',
            canActivate: [roleGuard(['ADMIN', 'SUPER_ADMIN'])],
            title: `Ajouter un apprenant — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/students/student-create/student-create').then(
                (m) => m.StudentCreate,
              ),
          },
          {
            path: ':publicId',
            title: `Fiche apprenant — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/students/student-profile/student-profile').then(
                (m) => m.StudentProfile,
              ),
          },
        ],
      },
      {
        // Point d'entrée « Organisation & planning » : une seule entrée
        // latérale rassemblant les quatre sous-sections ci-dessous
        // (référentiels, organisation, planning, alternance). Aucune route
        // n'est déplacée — ce hub ne fait que lancer. Périmètre : union
        // des rôles de lecture des quatre, le serveur restant l'autorité.
        path: 'organisation-planning',
        canActivate: [
          roleGuard(['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER']),
        ],
        title: `Organisation & planning — ${APP_NAME}`,
        loadComponent: () =>
          import('./features/organisation-planning/organisation-planning-hub').then(
            (m) => m.OrganisationPlanningHub,
          ),
      },
      {
        // Consultation en LECTURE SEULE du référentiel académique
        // (`com.esic.connect.academic`) : années scolaires → formations →
        // niveaux → promotions → classes. Périmètre aligné sur
        // `AcademicWeb.READ_ROLES`. `data.resource` sélectionne la
        // configuration d'affichage (colonnes, tris, sous-listes).
        path: 'academic',
        canActivate: [roleGuard([...ACADEMIC_READ_ROLES])],
        canActivateChild: [roleGuard([...ACADEMIC_READ_ROLES])],
        title: `Référentiels académiques — ${APP_NAME}`,
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'academic-years' },
          {
            path: 'academic-years',
            data: { resource: 'academic-years' },
            loadComponent: academicList,
          },
          {
            path: 'academic-years/:publicId',
            title: `Année scolaire — ${APP_NAME}`,
            data: { resource: 'academic-years' },
            loadComponent: academicDetail,
          },
          {
            path: 'programs',
            data: { resource: 'programs' },
            loadComponent: academicList,
          },
          {
            path: 'programs/:publicId',
            title: `Formation — ${APP_NAME}`,
            data: { resource: 'programs' },
            loadComponent: academicDetail,
          },
          {
            path: 'program-levels/:publicId',
            title: `Niveau — ${APP_NAME}`,
            data: { resource: 'program-levels' },
            loadComponent: academicDetail,
          },
          {
            path: 'promotions',
            data: { resource: 'promotions' },
            loadComponent: academicList,
          },
          {
            path: 'promotions/:publicId',
            title: `Promotion — ${APP_NAME}`,
            data: { resource: 'promotions' },
            loadComponent: academicDetail,
          },
          {
            path: 'class-groups',
            data: { resource: 'class-groups' },
            loadComponent: academicList,
          },
          {
            path: 'class-groups/:publicId',
            title: `Classe — ${APP_NAME}`,
            data: { resource: 'class-groups' },
            loadComponent: academicDetail,
          },
        ],
      },
      {
        // Référentiel organisationnel (`com.esic.connect.organization`) :
        // sites (liste → fiche → création / modification) puis bâtiments,
        // salles et plages réseau gérés depuis la fiche d'un site.
        // Périmètre de lecture aligné sur `SiteController.READ_ROLES` ;
        // les formulaires de site sont en plus gardés par
        // `ORGANIZATION_WRITE_ROLES` (`SiteController.WRITE_ROLES`).
        // Spring Security reste l'autorité (un `403` est rendu « accès
        // refusé ») ; un `PEDAGOGICAL_MANAGER` garde une lecture seule.
        path: 'organization',
        canActivate: [roleGuard([...ORGANIZATION_READ_ROLES])],
        canActivateChild: [roleGuard([...ORGANIZATION_READ_ROLES])],
        title: `Organisation — ${APP_NAME}`,
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'sites' },
          {
            path: 'sites',
            loadComponent: () =>
              import('./features/organization/site-list/site-list').then((m) => m.SiteList),
          },
          {
            path: 'sites/new',
            canActivate: [roleGuard([...ORGANIZATION_WRITE_ROLES])],
            data: { mode: 'create' },
            title: `Nouveau site — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/organization/site-form/site-form').then((m) => m.SiteForm),
          },
          {
            path: 'sites/:publicId',
            title: `Site — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/organization/site-detail/site-detail').then((m) => m.SiteDetail),
          },
          {
            path: 'sites/:publicId/edit',
            canActivate: [roleGuard([...ORGANIZATION_WRITE_ROLES])],
            data: { mode: 'edit' },
            title: `Modifier un site — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/organization/site-form/site-form').then((m) => m.SiteForm),
          },
        ],
      },
      {
        // Import CSV, simulation, versionnement et publication d'un
        // planning de classe (`com.esic.connect.planning`, EF-PLAN-001..007,
        // EF-SES-001) : `/planning/import` (upload + choix de la classe),
        // `/planning/import/:jobId` (revue des lignes + anomalies +
        // publication), `/planning/calendar` (construction directe —
        // EF-PLAN-006), `/planning/versions` (versions publiées + détail).
        // Périmètre aligné sur `PlanningWeb.MANAGE_ROLES` ; Spring Security
        // reste l'autorité (un `403` est rendu « accès refusé »).
        path: 'planning',
        canActivate: [roleGuard([...PLANNING_MANAGE_ROLES])],
        canActivateChild: [roleGuard([...PLANNING_MANAGE_ROLES])],
        title: `Planning — ${APP_NAME}`,
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'import' },
          {
            path: 'import',
            loadComponent: () =>
              import('./features/planning/planning-import/planning-import').then(
                (m) => m.PlanningImport,
              ),
          },
          {
            path: 'import/:jobId',
            title: `Revue d'un import de planning — ${APP_NAME}`,
            loadComponent: () =>
              import(
                './features/planning/planning-import-review/planning-import-review'
              ).then((m) => m.PlanningImportReview),
          },
          {
            path: 'calendar',
            title: `Calendrier de planning — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/planning/planning-calendar/planning-calendar').then(
                (m) => m.PlanningCalendar,
              ),
          },
          {
            path: 'versions',
            title: `Versions de planning — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/planning/planning-versions/planning-versions').then(
                (m) => m.PlanningVersions,
              ),
          },
        ],
      },
      {
        // Gestion et consultation de l'alternance
        // (`com.esic.connect.alternation`) : modèles de rythme,
        // affectations de rythme aux classes, exceptions individuelles et
        // résolution du contexte SCHOOL / COMPANY / UNKNOWN. Périmètre de
        // rôles aligné sur `AlternationWeb`. Spring Security reste
        // l'autorité (un `403 ALT_FORBIDDEN` est rendu « accès refusé »).
        path: 'alternation',
        canActivate: [roleGuard([...ALTERNATION_READ_ROLES])],
        canActivateChild: [roleGuard([...ALTERNATION_READ_ROLES])],
        title: `Alternance — ${APP_NAME}`,
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'patterns' },
          {
            path: 'patterns',
            loadComponent: () =>
              import('./features/alternation/patterns/pattern-list/pattern-list').then(
                (m) => m.PatternList,
              ),
          },
          {
            path: 'patterns/new',
            canActivate: [roleGuard([...ALTERNATION_PATTERN_WRITE_ROLES])],
            data: { mode: 'create' },
            title: `Nouveau modèle de rythme — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/alternation/patterns/pattern-form/pattern-form').then(
                (m) => m.PatternForm,
              ),
          },
          {
            path: 'patterns/:publicId',
            title: `Modèle de rythme — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/alternation/patterns/pattern-detail/pattern-detail').then(
                (m) => m.PatternDetail,
              ),
          },
          {
            path: 'patterns/:publicId/edit',
            canActivate: [roleGuard([...ALTERNATION_PATTERN_WRITE_ROLES])],
            data: { mode: 'edit' },
            title: `Modifier un modèle de rythme — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/alternation/patterns/pattern-form/pattern-form').then(
                (m) => m.PatternForm,
              ),
          },
          {
            path: 'classes',
            loadComponent: () =>
              import('./features/alternation/class-alternation/class-picker/class-picker').then(
                (m) => m.ClassPicker,
              ),
          },
          {
            path: 'classes/:classPublicId',
            title: `Rythme d'une classe — ${APP_NAME}`,
            loadComponent: () =>
              import(
                './features/alternation/class-alternation/class-alternation/class-alternation'
              ).then((m) => m.ClassAlternation),
          },
          {
            path: 'enrollments',
            loadComponent: () =>
              import(
                './features/alternation/enrollment-alternation/enrollment-picker/enrollment-picker'
              ).then((m) => m.EnrollmentPicker),
          },
          {
            path: 'enrollments/:enrollmentPublicId',
            title: `Exceptions d'une inscription — ${APP_NAME}`,
            loadComponent: () =>
              import(
                './features/alternation/enrollment-alternation/enrollment-alternation/enrollment-alternation'
              ).then((m) => m.EnrollmentAlternation),
          },
        ],
      },
      {
        // Séances exceptionnelles et émargement
        // (`com.esic.connect.coursesession` + `com.esic.connect.attendance`) :
        // liste, création, détail (ouverture / fermeture, QR + code court,
        // présences). Périmètre de rôles aligné sur `CourseSessionWeb`.
        // Spring Security reste l'autorité (un `403` API est rendu
        // « accès refusé ») ; un `TEACHER` ne voit que ses séances.
        path: 'sessions',
        canActivate: [roleGuard([...SESSION_READ_ROLES])],
        canActivateChild: [roleGuard([...SESSION_READ_ROLES])],
        title: `Séances — ${APP_NAME}`,
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/sessions/session-list/session-list').then((m) => m.SessionList),
          },
          {
            path: 'new',
            canActivate: [roleGuard([...SESSION_CREATE_ROLES])],
            title: `Nouvelle séance — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/sessions/session-form/session-form').then((m) => m.SessionForm),
          },
          {
            path: ':publicId',
            title: `Séance — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/sessions/session-detail/session-detail').then(
                (m) => m.SessionDetail,
              ),
          },
        ],
      },
      {
        // Émargement de l'apprenant (`POST /api/v1/attendance/validate`) :
        // saisie du code court affiché par le formateur. Réservé au rôle
        // `STUDENT` (`AttendanceWeb.VALIDATE_ROLE`).
        path: 'attendance',
        canActivate: [roleGuard(['STUDENT'])],
        title: `Émargement — ${APP_NAME}`,
        loadComponent: () =>
          import('./features/attendance-check-in/attendance-check-in').then(
            (m) => m.AttendanceCheckIn,
          ),
      },
      {
        // Espace « Mes présences » de l'apprenant (V10) :
        // `GET /api/v1/me/attendance*`, dépôt et suivi d'un justificatif
        // métier. Réservé au rôle `STUDENT` (`AttendanceManagementWeb.STUDENT_ROLE`).
        path: 'my-attendance',
        canActivate: [roleGuard(['STUDENT'])],
        canActivateChild: [roleGuard(['STUDENT'])],
        title: `Mes présences — ${APP_NAME}`,
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/attendance/my-attendance/my-attendance-list').then(
                (m) => m.MyAttendanceList,
              ),
          },
          {
            // Journal de transparence (EF-ATT-014). Déclaré AVANT `:id` :
            // le chemin littéral l'emporte, mais l'ordre le rend évident
            // à la lecture.
            path: 'transparency',
            title: `Journal de transparence — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/attendance/my-attendance/my-transparency').then(
                (m) => m.MyTransparency,
              ),
          },
          {
            // Départ anticipé côté apprenant (EF-ATT-013).
            path: 'early-departures',
            title: `Départs anticipés — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/attendance/my-attendance/my-early-departures').then(
                (m) => m.MyEarlyDepartures,
              ),
          },
          {
            path: ':id',
            title: `Présence — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/attendance/my-attendance/my-attendance-detail').then(
                (m) => m.MyAttendanceDetail,
              ),
          },
        ],
      },
      {
        // Réclamations (EF-CLAIM-001..004 ; docs/02 §20). Aucune garde de
        // rôle : la route est ouverte à tout compte authentifié, comme
        // `POST /api/v1/claims`. Le serveur décide seul de ce que chacun
        // voit — un apprenant ses propres réclamations, un intervenant
        // celles de son guichet et de son périmètre.
        path: 'claims',
        title: `Réclamations — ${APP_NAME}`,
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/claims/claim-list/claim-list').then((m) => m.ClaimList),
          },
          {
            path: ':publicId',
            title: `Réclamation — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/claims/claim-thread/claim-thread').then((m) => m.ClaimThread),
          },
        ],
      },
      {
        // Suivi d'assiduité (V10) : synthèse, rapports par séance / classe
        // / apprenant, file des justificatifs. Périmètre aligné sur
        // `AttendanceManagementWeb.REPORT_ROLES` / `REVIEW_LIST_ROLES` —
        // Spring Security reste l'autorité (un `403` est rendu « accès
        // refusé »), un `PEDAGOGICAL_MANAGER` reste filtré par périmètre.
        path: 'attendance-management',
        canActivate: [roleGuard([...ATTENDANCE_MANAGE_ROLES])],
        canActivateChild: [roleGuard([...ATTENDANCE_MANAGE_ROLES])],
        title: `Suivi d'assiduité — ${APP_NAME}`,
        // Coquille commune : un seul titre de page + navigation secondaire
        // visible (`.esic-subnav`) entre les cinq vues. Les chemins des
        // enfants sont inchangés (favoris, liens).
        loadComponent: () =>
          import('./features/attendance/management/attendance-management-shell').then(
            (m) => m.AttendanceManagementShell,
          ),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'summary' },
          {
            path: 'summary',
            loadComponent: () =>
              import('./features/attendance/management/attendance-summary').then(
                (m) => m.AttendanceSummary,
              ),
          },
          {
            path: 'sessions',
            data: { kind: 'sessions' },
            title: `Rapport par séance — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/attendance/management/attendance-report').then(
                (m) => m.AttendanceReport,
              ),
          },
          {
            path: 'classes',
            data: { kind: 'classes' },
            title: `Rapport par classe — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/attendance/management/attendance-report').then(
                (m) => m.AttendanceReport,
              ),
          },
          {
            path: 'students',
            data: { kind: 'students' },
            title: `Rapport par apprenant — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/attendance/management/attendance-report').then(
                (m) => m.AttendanceReport,
              ),
          },
          {
            path: 'justifications',
            title: `Justificatifs — ${APP_NAME}`,
            loadComponent: () =>
              import('./features/attendance/management/justification-queue').then(
                (m) => m.JustificationQueue,
              ),
          },
        ],
      },
    ],
  },
  {
    path: 'forbidden',
    title: `Accès refusé — ${APP_NAME}`,
    loadComponent: () =>
      import('./features/errors/forbidden').then((m) => m.Forbidden),
  },
  {
    path: '**',
    title: `Page introuvable — ${APP_NAME}`,
    loadComponent: () =>
      import('./features/errors/not-found').then((m) => m.NotFound),
  },
];
