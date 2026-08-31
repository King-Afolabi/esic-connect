import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import {
  ConfirmationResultResponse,
  JobListQuery,
  JobResponse,
  PageResponse,
  RowListQuery,
  RowResponse,
} from './student-import.models';

/**
 * Accès aux endpoints de l'import CSV des apprenants
 * (`com.esic.connect.studentimport.internal.StudentImportController`,
 * rapport §8). Une méthode par route réelle ; aucune n'est inventée.
 *
 * Le fichier est transmis **brut** dans un `FormData` (jamais lu ni
 * parsé côté navigateur). Les appels sont authentifiés par le jeton
 * porteur en mémoire (`authTokenInterceptor`) ; jamais de jeton en URL.
 * L'autorisation effective est décidée par Spring Security
 * (`StudentImportWeb.MANAGE_ROLES` + périmètre `PEDAGOGICAL_MANAGER`) —
 * les gardes de route ne font que masquer une navigation.
 */
@Injectable({ providedIn: 'root' })
export class StudentImportApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1/student-imports`;

  /** `POST /api/v1/student-imports` (multipart) — lance une simulation. */
  simulate(file: File, programCode?: string | null, classCode?: string | null): Observable<JobResponse> {
    const form = new FormData();
    form.append('file', file, file.name);
    if (programCode && programCode.trim()) {
      form.append('programCode', programCode.trim());
    }
    if (classCode && classCode.trim()) {
      form.append('classCode', classCode.trim());
    }
    return this.http.post<JobResponse>(this.base, form);
  }

  /** `GET /api/v1/student-imports` — jobs de l'appelant (globaux pour l'administration). */
  listJobs(query: JobListQuery): Observable<PageResponse<JobResponse>> {
    return this.http.get<PageResponse<JobResponse>>(this.base, {
      params: toHttpParams({
        status: query.status,
        sort: query.sort,
        page: query.page,
        size: query.size,
      }),
    });
  }

  /** `GET /api/v1/student-imports/{publicId}`. */
  getJob(publicId: string): Observable<JobResponse> {
    return this.http.get<JobResponse>(`${this.base}/${encodeURIComponent(publicId)}`);
  }

  /** `GET /api/v1/student-imports/{publicId}/rows` — lignes normalisées + anomalies. */
  listRows(publicId: string, query: RowListQuery): Observable<PageResponse<RowResponse>> {
    return this.http.get<PageResponse<RowResponse>>(
      `${this.base}/${encodeURIComponent(publicId)}/rows`,
      {
        params: toHttpParams({
          rowStatus: query.rowStatus,
          severity: query.severity,
          action: query.action,
          sort: query.sort,
          page: query.page,
          size: query.size,
        }),
      },
    );
  }

  /** `POST /api/v1/student-imports/{publicId}/confirm` — `200` (jamais `201`). */
  confirm(publicId: string): Observable<ConfirmationResultResponse> {
    return this.http.post<ConfirmationResultResponse>(
      `${this.base}/${encodeURIComponent(publicId)}/confirm`,
      {},
    );
  }

  /** `POST /api/v1/student-imports/{publicId}/cancel` — `204`. */
  cancel(publicId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${encodeURIComponent(publicId)}/cancel`, {});
  }
}

/**
 * `HttpParams` sans clé absente (`null` / `undefined` / chaîne vide) —
 * aucun filtre vide n'est envoyé.
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
