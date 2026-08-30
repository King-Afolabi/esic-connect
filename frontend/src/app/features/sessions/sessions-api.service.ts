import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  AttendanceRecordResponse,
  AttendanceTokenResponse,
  CourseSessionResponse,
  CreateSessionRequest,
  PageResponse,
  SessionAttendanceResponse,
  SessionListQuery,
  TeacherOptionResponse,
  ValidateAttendanceRequest,
} from './sessions.models';

/**
 * Accès HTTP aux modules `coursesession` et `attendance`. Ce service ne
 * consomme que des routes réellement exposées par le back-end ; aucune
 * n'est inventée, et aucun paramètre client ne permet d'élargir le
 * périmètre d'un `PEDAGOGICAL_MANAGER` ou d'un `TEACHER` (le back-end
 * décide du périmètre à partir du contexte Spring Security).
 *
 * `token` / `shortCode` d'un jeton d'émargement ne transitent que dans le
 * corps HTTPS des réponses : ils ne sont jamais placés dans une URL.
 */
@Injectable({ providedIn: 'root' })
export class SessionsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1`;

  // --- Séances ----------------------------------------------------------

  /** `GET /api/v1/sessions`. */
  listSessions(query: SessionListQuery): Observable<PageResponse<CourseSessionResponse>> {
    return this.http.get<PageResponse<CourseSessionResponse>>(`${this.base}/sessions`, {
      params: toHttpParams({
        status: query.status,
        teacher: query.teacher,
        classGroup: query.classGroup,
        from: query.from,
        to: query.to,
        sort: query.sort,
        page: query.page,
        size: query.size,
      }),
    });
  }

  /** `GET /api/v1/sessions/teachers` — formateurs éligibles à une séance. */
  listEligibleTeachers(): Observable<TeacherOptionResponse[]> {
    return this.http.get<TeacherOptionResponse[]>(`${this.base}/sessions/teachers`);
  }

  /** `GET /api/v1/sessions/{publicId}`. */
  getSession(publicId: string): Observable<CourseSessionResponse> {
    return this.http.get<CourseSessionResponse>(
      `${this.base}/sessions/${encodeURIComponent(publicId)}`,
    );
  }

  /** `POST /api/v1/sessions` → 201. */
  createSession(body: CreateSessionRequest): Observable<CourseSessionResponse> {
    return this.http.post<CourseSessionResponse>(`${this.base}/sessions`, body);
  }

  /** `POST /api/v1/sessions/{publicId}/open` → 204. */
  openSession(publicId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/sessions/${encodeURIComponent(publicId)}/open`, {});
  }

  /** `POST /api/v1/sessions/{publicId}/close` → 204. */
  closeSession(publicId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/sessions/${encodeURIComponent(publicId)}/close`, {});
  }

  // --- Émargement -----------------------------------------------------

  /**
   * `POST /api/v1/sessions/{publicId}/attendance-token` — émet / renouvelle
   * un jeton dynamique + code court (opération qui génère une capacité
   * temporaire → `POST`).
   */
  issueAttendanceToken(sessionPublicId: string): Observable<AttendanceTokenResponse> {
    return this.http.post<AttendanceTokenResponse>(
      `${this.base}/sessions/${encodeURIComponent(sessionPublicId)}/attendance-token`,
      {},
    );
  }

  /** `GET /api/v1/sessions/{publicId}/attendance` — présences de la séance. */
  getSessionAttendance(sessionPublicId: string): Observable<SessionAttendanceResponse> {
    return this.http.get<SessionAttendanceResponse>(
      `${this.base}/sessions/${encodeURIComponent(sessionPublicId)}/attendance`,
    );
  }

  /**
   * `POST /api/v1/attendance/validate` → 200. Le corps ne contient que
   * `token` **ou** `shortCode` ; l'apprenant est déterminé par le serveur
   * à partir du seul JWT (jamais d'identifiant d'apprenant transmis).
   */
  validateAttendance(body: ValidateAttendanceRequest): Observable<AttendanceRecordResponse> {
    return this.http.post<AttendanceRecordResponse>(`${this.base}/attendance/validate`, body);
  }
}

/**
 * Construit des `HttpParams` en ignorant les valeurs absentes (`null`,
 * `undefined`, chaîne vide) — aucune clé n'est envoyée si elle n'est pas
 * renseignée.
 */
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
