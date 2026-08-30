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
