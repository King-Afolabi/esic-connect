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
        // publication), `/planning/versions` (versions publiées + détail).
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
        // Suivi d'assiduité (V10) : synthèse, rapports par séance / classe
        // / apprenant, file des justificatifs. Périmètre aligné sur
        // `AttendanceManagementWeb.REPORT_ROLES` / `REVIEW_LIST_ROLES` —
        // Spring Security reste l'autorité (un `403` est rendu « accès
        // refusé »), un `PEDAGOGICAL_MANAGER` reste filtré par périmètre.
        path: 'attendance-management',
        canActivate: [roleGuard([...ATTENDANCE_MANAGE_ROLES])],
        canActivateChild: [roleGuard([...ATTENDANCE_MANAGE_ROLES])],
        title: `Suivi d'assiduité — ${APP_NAME}`,
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
