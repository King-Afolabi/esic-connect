import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  AlternationContextResponse,
  ArchivePatternRequest,
  AssignClassRequest,
  AssignmentListQuery,
  CancelExceptionRequest,
  ClassAssignmentResponse,
  CloseAssignmentRequest,
  CreateExceptionRequest,
  CreatePatternRequest,
  EnrollmentContextResponse,
  ExceptionListQuery,
  PageResponse,
  PatternListQuery,
  StudentExceptionResponse,
  UpdatePatternRequest,
  WorkStudyPatternResponse,
} from './alternation.models';

/**
 * Accès HTTP au module `alternation`. Ce service ne consomme que des
 * routes réellement exposées par le back-end ; aucune n'est inventée, et
 * aucun paramètre client ne permet d'élargir le périmètre d'un
 * `PEDAGOGICAL_MANAGER` (le back-end décide du périmètre via
 * `AcademicScopeDirectory`).
 *
 * Les appels passent par les intercepteurs existants (jeton porteur en
 * mémoire ajouté par `authTokenInterceptor` ; `401` → purge de session,
 * `0` / `5xx` → bandeau générique via `apiErrorInterceptor`).
 * L'autorisation effective reste décidée par Spring Security.
 */
@Injectable({ providedIn: 'root' })
export class AlternationApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1/alternation`;

  // --- Modèles de rythme ---------------------------------------------------

  /** `GET /api/v1/alternation/patterns`. */
  listPatterns(query: PatternListQuery): Observable<PageResponse<WorkStudyPatternResponse>> {
    return this.http.get<PageResponse<WorkStudyPatternResponse>>(`${this.base}/patterns`, {
      params: toHttpParams({
        q: query.q,
        status: query.status,
        type: query.type,
        sort: query.sort,
        page: query.page,
        size: query.size,
      }),
    });
  }

  /** `GET /api/v1/alternation/patterns/{publicId}`. */
  getPattern(publicId: string): Observable<WorkStudyPatternResponse> {
    return this.http.get<WorkStudyPatternResponse>(
      `${this.base}/patterns/${encodeURIComponent(publicId)}`,
    );
  }

  /** `POST /api/v1/alternation/patterns` → 201. */
  createPattern(body: CreatePatternRequest): Observable<WorkStudyPatternResponse> {
    return this.http.post<WorkStudyPatternResponse>(`${this.base}/patterns`, body);
  }

  /** `PATCH /api/v1/alternation/patterns/{publicId}`. */
  updatePattern(
    publicId: string,
    body: UpdatePatternRequest,
  ): Observable<WorkStudyPatternResponse> {
    return this.http.patch<WorkStudyPatternResponse>(
      `${this.base}/patterns/${encodeURIComponent(publicId)}`,
      body,
    );
  }

  /** `POST /api/v1/alternation/patterns/{publicId}/archive` → 204. */
  archivePattern(publicId: string, body: ArchivePatternRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/patterns/${encodeURIComponent(publicId)}/archive`,
      body,
    );
  }

  /** `POST /api/v1/alternation/patterns/{publicId}/restore` → 204. */
  restorePattern(publicId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/patterns/${encodeURIComponent(publicId)}/restore`,
      {},
    );
  }

  // --- Affectations de rythme à une classe -------------------------------

  /** `GET /api/v1/alternation/class-assignments` (endpoint plat). */
  listAssignments(query: AssignmentListQuery): Observable<PageResponse<ClassAssignmentResponse>> {
    return this.http.get<PageResponse<ClassAssignmentResponse>>(`${this.base}/class-assignments`, {
      params: toHttpParams({
        class: query.class,
        status: query.status,
        sort: query.sort,
        page: query.page,
        size: query.size,
      }),
    });
  }

  /** `GET /api/v1/alternation/classes/{classPublicId}/assignments`. */
  listAssignmentsByClass(
    classPublicId: string,
    query: Omit<AssignmentListQuery, 'class'>,
  ): Observable<PageResponse<ClassAssignmentResponse>> {
    return this.http.get<PageResponse<ClassAssignmentResponse>>(
      `${this.base}/classes/${encodeURIComponent(classPublicId)}/assignments`,
      {
        params: toHttpParams({
          status: query.status,
          sort: query.sort,
          page: query.page,
          size: query.size,
        }),
      },
    );
  }

  /** `GET /api/v1/alternation/class-assignments/{publicId}`. */
  getAssignment(publicId: string): Observable<ClassAssignmentResponse> {
    return this.http.get<ClassAssignmentResponse>(
      `${this.base}/class-assignments/${encodeURIComponent(publicId)}`,
    );
  }

  /** `POST /api/v1/alternation/class-assignments` → 201. */
  assignClass(body: AssignClassRequest): Observable<ClassAssignmentResponse> {
    return this.http.post<ClassAssignmentResponse>(`${this.base}/class-assignments`, body);
  }

  /** `POST /api/v1/alternation/class-assignments/{publicId}/close` → 204. */
  closeAssignment(publicId: string, body: CloseAssignmentRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/class-assignments/${encodeURIComponent(publicId)}/close`,
      body,
    );
  }

  /** `GET /api/v1/alternation/classes/{classPublicId}/context?date=YYYY-MM-DD`. */
  getClassContext(
    classPublicId: string,
    date: string,
  ): Observable<AlternationContextResponse> {
    return this.http.get<AlternationContextResponse>(
      `${this.base}/classes/${encodeURIComponent(classPublicId)}/context`,
      { params: new HttpParams().set('date', date) },
    );
  }

  // --- Exceptions individuelles ----------------------------------------

  /** `GET /api/v1/alternation/enrollments/{enrollmentPublicId}/exceptions`. */
  listExceptionsByEnrollment(
    enrollmentPublicId: string,
    query: ExceptionListQuery,
  ): Observable<PageResponse<StudentExceptionResponse>> {
    return this.http.get<PageResponse<StudentExceptionResponse>>(
      `${this.base}/enrollments/${encodeURIComponent(enrollmentPublicId)}/exceptions`,
      {
        params: toHttpParams({
          sort: query.sort,
          page: query.page,
          size: query.size,
        }),
      },
    );
  }

  /** `GET /api/v1/alternation/student-exceptions/{publicId}`. */
  getException(publicId: string): Observable<StudentExceptionResponse> {
    return this.http.get<StudentExceptionResponse>(
      `${this.base}/student-exceptions/${encodeURIComponent(publicId)}`,
    );
  }

  /** `POST /api/v1/alternation/student-exceptions` → 201. */
  createException(body: CreateExceptionRequest): Observable<StudentExceptionResponse> {
    return this.http.post<StudentExceptionResponse>(`${this.base}/student-exceptions`, body);
  }

  /** `POST /api/v1/alternation/student-exceptions/{publicId}/cancel` → 204. */
  cancelException(publicId: string, body: CancelExceptionRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/student-exceptions/${encodeURIComponent(publicId)}/cancel`,
      body,
    );
  }

  /** `GET /api/v1/alternation/enrollments/{enrollmentPublicId}/context?date=YYYY-MM-DD`. */
  getEnrollmentContext(
    enrollmentPublicId: string,
    date: string,
  ): Observable<EnrollmentContextResponse> {
    return this.http.get<EnrollmentContextResponse>(
      `${this.base}/enrollments/${encodeURIComponent(enrollmentPublicId)}/context`,
      { params: new HttpParams().set('date', date) },
    );
  }
}

/**
 * Construit des `HttpParams` en ignorant les valeurs absentes (`null`,
 * `undefined`, chaîne vide) — aucune clé n'est envoyée si elle n'est pas
 * renseignée.
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
