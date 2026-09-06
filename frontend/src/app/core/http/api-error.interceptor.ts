import { HttpErrorResponse, HttpEventType, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, tap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { normalizeHttpError } from '../models/api-error';
import { NotificationService } from '../notifications/notification.service';
import { ConnectivityService } from '../pwa/connectivity.service';

function targetsApi(url: string): boolean {
  return url.startsWith(environment.apiBaseUrl) || url.startsWith('/api/');
}

function isLoginRequest(url: string): boolean {
  return url.endsWith('/v1/auth/login');
}

/**
 * Routes d'authentification : exclues de la reprise sur `401`. Un `401`
 * y a un autre sens (identifiants refusés, cookie de renouvellement
 * absent) qu'un jeton d'accès expiré, et {@link AuthService.restoreSession}
 * / {@link AuthService.refreshSession} gèrent déjà leur propre échec —
 * rejouer un renouvellement par-dessus créerait une boucle.
 */
function isAuthRoute(url: string): boolean {
  return url.includes('/v1/auth/');
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
 * - toute réponse — succès comme erreur — met à jour l'état de connexion
 *   ({@link ConnectivityService}) ;
 * - `401` sur un appel métier → tentative d'un **renouvellement
 *   silencieux** de session (cookie `HttpOnly`), en file unique, puis
 *   rejeu de la requête d'origine avec le nouveau jeton. L'utilisateur
 *   n'est renvoyé vers la connexion que si le renouvellement échoue ;
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
  const connectivity = inject(ConnectivityService);

  return next(req).pipe(
    tap((event) => {
      if (event.type === HttpEventType.Response && targetsApi(req.url)) {
        connectivity.reportNetworkSuccess();
      }
    }),
    catchError((error: unknown) => {
      if (
        !(error instanceof HttpErrorResponse) ||
        !targetsApi(req.url) ||
        isPublicInvitationRequest(req.url)
      ) {
        return throwError(() => error);
      }

      if (error.status === 401 && !isLoginRequest(req.url) && !isAuthRoute(req.url)) {
        return auth.refreshSession().pipe(
          switchMap((renewed) => {
            if (!renewed) {
              auth.handleUnauthorized();
              return throwError(() => error);
            }
            const token = auth.accessToken;
            const retried = token
              ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
              : req;
            return next(retried).pipe(
              catchError((retryError: unknown) => {
                if (retryError instanceof HttpErrorResponse && retryError.status === 401) {
                  // Le renouvellement a réussi mais l'appel échoue encore :
                  // la session n'est plus exploitable.
                  auth.handleUnauthorized();
                }
                return throwError(() => retryError);
              }),
            );
          }),
        );
      }

      if (error.status === 401 && !isLoginRequest(req.url)) {
        auth.handleUnauthorized();
      } else if (error.status === 0) {
        // Statut 0 : la requête n'a jamais atteint le serveur.
        // L'application bascule en mode hors ligne, ce qui permet aux
        // écrans d'annoncer des données datées plutôt que de laisser
        // croire à un état courant.
        connectivity.reportNetworkFailure();
        notifications.error(normalizeHttpError(error).message);
      } else if (error.status >= 500) {
        notifications.error(normalizeHttpError(error).message);
      }
      return throwError(() => error);
    }),
  );
};
