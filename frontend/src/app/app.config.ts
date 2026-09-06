import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { PwaService } from './core/pwa/pwa.service';
import { authTokenInterceptor } from './core/http/auth-token.interceptor';
import { apiErrorInterceptor } from './core/http/api-error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptors([authTokenInterceptor, apiErrorInterceptor])),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top' }),
    ),
    // Rétablit la session avant le premier rendu : échange le cookie de
    // renouvellement HttpOnly contre un jeton d'accès (voir
    // AuthService.restoreSession). Sans cookie valide, démarrage anonyme.
    provideAppInitializer(() => firstValueFrom(inject(AuthService).restoreSession())),
    // Enregistrement du service worker (EF-PWA-001). Silencieux si le
    // navigateur ne le prend pas en charge : la PWA est un supplément,
    // jamais une condition d'accès à l'application.
    provideAppInitializer(() => inject(PwaService).register()),
  ],
};
