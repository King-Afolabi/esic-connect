import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CreateSubjectRequest,
  SubjectListQuery,
  SubjectPage,
  SubjectResponse,
  UpdateSubjectRequest,
} from './subjects.models';

/**
 * Accès HTTP au référentiel des matières (EF-ACA-006).
 *
 * <p>Aucun paramètre client ne permet d'élargir un périmètre : le
 * rattachement d'une matière à une formation est contrôlé côté serveur
 * (`AcademicScopeGuard`), formation par formation.
 */
@Injectable({ providedIn: 'root' })
export class SubjectsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1/subjects`;

  list(query: SubjectListQuery): Observable<SubjectPage> {
    return this.http.get<SubjectPage>(this.base, { params: toHttpParams(query) });
  }

  get(id: string): Observable<SubjectResponse> {
    return this.http.get<SubjectResponse>(`${this.base}/${id}`);
  }

  create(request: CreateSubjectRequest): Observable<SubjectResponse> {
    return this.http.post<SubjectResponse>(this.base, request);
  }

  update(id: string, request: UpdateSubjectRequest): Observable<SubjectResponse> {
    return this.http.patch<SubjectResponse>(`${this.base}/${id}`, request);
  }

  archive(id: string, reason: string): Observable<SubjectResponse> {
    return this.http.post<SubjectResponse>(`${this.base}/${id}/archive`, { reason });
  }

  restore(id: string): Observable<SubjectResponse> {
    return this.http.post<SubjectResponse>(`${this.base}/${id}/restore`, {});
  }
}

function toHttpParams(query: SubjectListQuery): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}
