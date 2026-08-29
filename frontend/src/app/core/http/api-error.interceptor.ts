import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { normalizeHttpError } from '../models/api-error';
import { NotificationService } from '../notifications/notification.service';

function targetsApi(url: string): boolean {
  return url.startsWith(environment.apiBaseUrl) || url.startsWith('/api/');
}

function isLoginRequest(url: string): boolean {
  return url.endsWith('/v1/auth/login');
}

/**
 * Endpoints publics du parcours d'activation : leurs erreurs ne
 * concernent jamais une session authentifiée. Le composant d'activation
 * possède son propre traitement d'erreurs ; l'intercepteur ne doit ni
 * purger la session sur un `401`, ni afficher de bandeau global.
 */
function isPublicInvitationRequest(url: string): boolean {
  return (
    url.includes('/account-invitations/validate') || url.includes('/account-invitations/activate')
  );
}

/**
 * Traitement transversal des erreurs HTTP de l'API :
 *
 * - `401` sur un appel authentifié → la session locale est purgée et
 *   l'utilisateur est renvoyé vers la connexion ({@link AuthService.handleUnauthorized}) ;
 * - `0` (réseau) et `5xx` → bandeau générique (aucune trace serveur exposée) ;
 * - `4xx` → laissé au composant appelant, qui affiche un message ciblé ;
 * - endpoints publics d'activation → aucun traitement transversal.
 *
 * L'erreur d'origine ({@link HttpErrorResponse}) est toujours relayée afin
 * que les appelants conservent l'accès au `code` métier du back-end.
 */
export const apiErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const notifications = inject(NotificationService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        targetsApi(req.url) &&
        !isPublicInvitationRequest(req.url)
      ) {
        if (error.status === 401 && !isLoginRequest(req.url)) {
          auth.handleUnauthorized();
        } else if (error.status === 0 || error.status >= 500) {
          notifications.error(normalizeHttpError(error).message);
        }
      }
      return throwError(() => error);
    }),
  );
};
