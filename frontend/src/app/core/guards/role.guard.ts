import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { Role } from '../models/role';

/**
 * Construit un garde exigeant au moins un des rôles indiqués.
 *
 * Le périmètre de rôles est repris du `@PreAuthorize` de la route API
 * correspondante côté Spring Boot. Ce garde ne fait que masquer une
 * navigation : il n'élargit ni ne remplace le contrôle serveur
 * (docs/07-securite-rgpd.md §7 ; consigne « les gardes sont une couche
 * d'ergonomie »).
 */
export function roleGuard(required: readonly Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    if (!auth.isAuthenticated()) {
      return router.createUrlTree(['/login']);
    }
    return auth.hasAnyRole(required) ? true : router.createUrlTree(['/forbidden']);
  };
}
