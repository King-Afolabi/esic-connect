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
        path: 'students',
        canActivate: [roleGuard(['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION'])],
        title: `Apprenants — ${APP_NAME}`,
        data: {
          pageTitle: 'Apprenants',
          pageDescription:
            'Profils apprenants et inscriptions historiques. Écran à venir dans un prochain lot.',
          docReference: 'docs/02-cahier-des-charges.md §7.6, §10',
        },
        loadComponent: () =>
          import('./shared/components/module-placeholder/module-placeholder').then(
            (m) => m.ModulePlaceholder,
          ),
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
