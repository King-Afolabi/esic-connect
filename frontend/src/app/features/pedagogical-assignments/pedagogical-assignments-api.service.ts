import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  ClosePedagogicalAssignmentRequest,
  CreatePedagogicalAssignmentRequest,
  PageResponse,
  PedagogicalAssignmentListQuery,
  PedagogicalAssignmentResponse,
} from './pedagogical-assignments.models';

/**
 * Accès HTTP au module `academic` pour le périmètre pédagogique
 * (`PedagogicalAssignmentController`). Aucune route inventée ; les rôles
 * fins restent décidés par Spring Security (`ADMIN` / `SUPER_ADMIN`
 * uniquement) — le `roleGuard` de la route ne fait que masquer l'écran.
 */
@Injectable({ providedIn: 'root' })
export class PedagogicalAssignmentsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1/pedagogical-assignments`;

  /** `GET /api/v1/pedagogical-assignments`. */
  list(query: PedagogicalAssignmentListQuery): Observable<PageResponse<PedagogicalAssignmentResponse>> {
    return this.http.get<PageResponse<PedagogicalAssignmentResponse>>(this.base, {
      params: toHttpParams({
        program: query.program,
        user: query.user,
        type: query.type,
        status: query.status,
        activeOn: query.activeOn,
        sort: query.sort,
        page: query.page,
        size: query.size,
      }),
    });
  }

  /** `GET /api/v1/pedagogical-assignments/{publicId}`. */
  get(publicId: string): Observable<PedagogicalAssignmentResponse> {
    return this.http.get<PedagogicalAssignmentResponse>(
      `${this.base}/${encodeURIComponent(publicId)}`,
    );
  }

  /** `POST /api/v1/pedagogical-assignments` → 201. */
  create(body: CreatePedagogicalAssignmentRequest): Observable<PedagogicalAssignmentResponse> {
    return this.http.post<PedagogicalAssignmentResponse>(this.base, body);
  }

  /** `POST /api/v1/pedagogical-assignments/{publicId}/close` → 204. */
  close(publicId: string, body: ClosePedagogicalAssignmentRequest): Observable<void> {
    return this.http.post<void>(`${this.base}/${encodeURIComponent(publicId)}/close`, body);
  }
}

function toHttpParams(values: Record<string, string | number | null | undefined>): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value === null || value === undefined || value === '') {
      continue;
    }
    params = params.set(key, String(value));
  }
  return params;
}
