import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../auth/auth.service';

/**
 * Autorise l'accès aux routes authentifiées.
 *
 * Couche de confort de navigation uniquement : la protection réelle des
 * données est assurée par Spring Security à chaque appel d'API
 * (docs/07-securite-rgpd.md §7).
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { redirect: state.url } });
};
