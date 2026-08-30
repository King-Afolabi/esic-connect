import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  AccountActionRequest,
  AssignRoleRequest,
  PageResponse,
  UserDetailResponse,
  UserListQuery,
  UserSummaryResponse,
} from './administration.models';

/**
 * Accès à l'administration des comptes du module `identity`
 * (`UserAccountController`).
 *
 * Ce service ne consomme que des routes déjà exposées par le back-end ;
 * aucune n'est inventée. Les lectures (`GET`) alimentent la liste et la
 * fiche ; les mutations (`POST`) couvrent le cycle de vie du compte
 * (suspension, réactivation, archivage) et la gestion de rôle
 * (attribution, retrait). Toutes les mutations répondent `204` et un
 * corps vide.
 *
 * Les appels sont authentifiés par le jeton porteur ajouté par
 * `authTokenInterceptor` (le jeton reste en mémoire). L'autorisation
 * effective est décidée par Spring Security ; le `roleGuard` de la route
 * et la visibilité des boutons ne font que masquer l'interface.
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

  /** `POST /api/v1/users/{publicId}/suspend` — `ACTIVE` → `SUSPENDED`. */
  suspendUser(publicId: string, body: AccountActionRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/users/${encodeURIComponent(publicId)}/suspend`,
      body,
    );
  }

  /** `POST /api/v1/users/{publicId}/restore` — `SUSPENDED` → `ACTIVE`. */
  restoreUser(publicId: string, body: AccountActionRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/users/${encodeURIComponent(publicId)}/restore`,
      body,
    );
  }

  /**
   * `POST /api/v1/users/{publicId}/archive` — statut `ARCHIVED` ; clôture
   * tous les rôles actifs, irréversible dans ce lot.
   */
  archiveUser(publicId: string, body: AccountActionRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/users/${encodeURIComponent(publicId)}/archive`,
      body,
    );
  }

  /** `POST /api/v1/users/{publicId}/roles` — attribue un rôle (nouvelle affectation active). */
  assignRole(publicId: string, body: AssignRoleRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/users/${encodeURIComponent(publicId)}/roles`,
      body,
    );
  }

  /**
   * `POST /api/v1/users/{publicId}/roles/{roleCode}/revoke` — clôture une
   * affectation active sans supprimer son historique.
   */
  revokeRole(publicId: string, roleCode: string, body: AccountActionRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/users/${encodeURIComponent(publicId)}/roles/${encodeURIComponent(roleCode)}/revoke`,
      body,
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
