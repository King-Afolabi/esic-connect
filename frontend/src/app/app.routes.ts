import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { roleGuard } from './core/guards/role.guard';

const APP_NAME = 'ESIC Connect';

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
        path: 'administration',
        canActivate: [roleGuard(['ADMIN', 'SUPER_ADMIN'])],
        title: `Administration — ${APP_NAME}`,
        data: {
          pageTitle: 'Administration',
          pageDescription:
            "Gestion des comptes, des rôles et des référentiels. Écran à venir dans un prochain lot.",
          docReference: 'docs/02-cahier-des-charges.md §6.3, §9',
        },
        loadComponent: () =>
          import('./shared/components/module-placeholder/module-placeholder').then(
            (m) => m.ModulePlaceholder,
          ),
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
