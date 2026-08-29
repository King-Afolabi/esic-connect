import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';

function targetsApi(url: string): boolean {
  return url.startsWith(environment.apiBaseUrl) || url.startsWith('/api/');
}

/**
 * Endpoints publics du parcours d'activation : ils s'authentifient par le
 * jeton d'invitation, jamais par le jeton porteur. Un utilisateur déjà
 * connecté qui ouvre `/activation` ne doit pas y envoyer son JWT.
 */
function isPublicInvitationRequest(url: string): boolean {
  return (
    url.includes('/account-invitations/validate') || url.includes('/account-invitations/activate')
  );
}

/**
 * Ajoute l'en-tête `Authorization: Bearer <token>` aux appels vers l'API
 * lorsqu'une session est active. Le jeton n'est jamais journalisé.
 */
export const authTokenInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).accessToken;
  if (
    !token ||
    !targetsApi(req.url) ||
    req.headers.has('Authorization') ||
    isPublicInvitationRequest(req.url)
  ) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
