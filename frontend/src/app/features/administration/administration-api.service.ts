import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  PageResponse,
  UserDetailResponse,
  UserListQuery,
  UserSummaryResponse,
} from './administration.models';

/**
 * Accès en **lecture seule** à l'administration des comptes du module
 * `identity` (`UserAccountController`).
 *
 * Ce service ne consomme que des routes déjà exposées par le back-end ;
 * aucune n'est inventée, aucune écriture (`POST …/suspend` · `/restore` ·
 * `/archive` · `/roles` · `/roles/{roleCode}/revoke`) n'est appelée.
 *
 * Les appels sont authentifiés par le jeton porteur ajouté par
 * `authTokenInterceptor` (le jeton reste en mémoire). L'autorisation
 * effective est décidée par Spring Security (`READ_ROLES` =
 * `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION`) : le `roleGuard` de
 * la route ne fait que masquer une navigation.
 */
@Injectable({ providedIn: 'root' })
export class AdministrationApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1`;

  /** `GET /api/v1/users` — liste paginée, filtrée, triée. */
  listUsers(query: UserListQuery): Observable<PageResponse<UserSummaryResponse>> {
    return this.http.get<PageResponse<UserSummaryResponse>>(`${this.base}/users`, {
      params: toHttpParams({
        q: query.q,
        status: query.status,
        role: query.role,
        sort: query.sort,
        page: query.page,
        size: query.size,
      }),
    });
  }

  /** `GET /api/v1/users/{publicId}` — détail + historique complet des rôles. */
  getUser(publicId: string): Observable<UserDetailResponse> {
    return this.http.get<UserDetailResponse>(
      `${this.base}/users/${encodeURIComponent(publicId)}`,
    );
  }
}

/**
 * Construit des `HttpParams` en ignorant les valeurs absentes (`null`,
 * `undefined`, chaîne vide) — aucune clé de filtre n'est envoyée si elle
 * n'est pas renseignée.
 */
function toHttpParams(
  values: Record<string, string | number | null | undefined>,
): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value === null || value === undefined || value === '') {
      continue;
    }
    params = params.set(key, String(value));
  }
  return params;
}
