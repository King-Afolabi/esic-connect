import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  PageResponse,
  PlanningJobResponse,
  PlanningPublicationResponse,
  PlanningRowListQuery,
  PlanningRowResponse,
  PlanningVersionDetailResponse,
  PlanningVersionResponse,
} from './planning.models';

/**
 * Accès aux endpoints du module `planning`
 * (`com.esic.connect.planning.internal`). Une méthode par route réelle ;
 * aucune n'est inventée. Le CSV est transmis **brut** dans un `FormData`
 * (jamais lu ni parsé côté navigateur). Les appels sont authentifiés par
 * le jeton porteur en mémoire (`authTokenInterceptor`). L'autorisation
 * effective est décidée par Spring Security (`PlanningWeb.MANAGE_ROLES` +
 * périmètre `PEDAGOGICAL_MANAGER`).
 */
@Injectable({ providedIn: 'root' })
export class PlanningApiService {
  private readonly http = inject(HttpClient);
  private readonly imports = `${environment.apiBaseUrl}/v1/planning-imports`;
  private readonly versions = `${environment.apiBaseUrl}/v1/planning/versions`;

  /** `POST /api/v1/planning-imports` (multipart) — lance une simulation. */
  simulate(file: File, classGroupPublicId: string): Observable<PlanningJobResponse> {
    const form = new FormData();
    form.append('file', file, file.name);
    form.append('classGroupPublicId', classGroupPublicId);
    return this.http.post<PlanningJobResponse>(this.imports, form);
  }

  /** `GET /api/v1/planning-imports/{publicId}`. */
  getJob(publicId: string): Observable<PlanningJobResponse> {
    return this.http.get<PlanningJobResponse>(`${this.imports}/${encodeURIComponent(publicId)}`);
  }

  /** `GET /api/v1/planning-imports/{publicId}/rows` — lignes + anomalies. */
  listRows(
    publicId: string,
    query: PlanningRowListQuery,
  ): Observable<PageResponse<PlanningRowResponse>> {
    return this.http.get<PageResponse<PlanningRowResponse>>(
      `${this.imports}/${encodeURIComponent(publicId)}/rows`,
      { params: toHttpParams({ sort: query.sort, page: query.page, size: query.size }) },
    );
  }

  /** `POST /api/v1/planning-imports/{publicId}/publish` — `200` (jamais `201`). */
  publish(publicId: string): Observable<PlanningPublicationResponse> {
    return this.http.post<PlanningPublicationResponse>(
      `${this.imports}/${encodeURIComponent(publicId)}/publish`,
      {},
    );
  }

  /** `POST /api/v1/planning-imports/{publicId}/cancel` — `204`. */
  cancel(publicId: string): Observable<void> {
    return this.http.post<void>(`${this.imports}/${encodeURIComponent(publicId)}/cancel`, {});
  }

  /** `GET /api/v1/planning/versions?classGroupPublicId=…`. */
  listVersions(
    classGroupPublicId: string,
    query: { sort?: string | null; page?: number; size?: number },
  ): Observable<PageResponse<PlanningVersionResponse>> {
    return this.http.get<PageResponse<PlanningVersionResponse>>(this.versions, {
      params: toHttpParams({
        classGroupPublicId,
        sort: query.sort,
        page: query.page,
        size: query.size,
      }),
    });
  }

  /** `GET /api/v1/planning/versions/{publicId}` — en-tête + entrées. */
  getVersion(publicId: string): Observable<PlanningVersionDetailResponse> {
    return this.http.get<PlanningVersionDetailResponse>(
      `${this.versions}/${encodeURIComponent(publicId)}`,
    );
  }
}

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
