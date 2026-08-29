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
