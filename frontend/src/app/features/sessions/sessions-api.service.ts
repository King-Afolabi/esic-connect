import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  AttendanceCandidate,
  AttendanceCorrectionEntry,
  AttendanceRecordResponse,
  AttendanceTokenResponse,
  CancelAttendanceRequest,
  CheckpointView,
  CorrectAttendanceRequest,
  CourseSessionResponse,
  CreateCheckpointRequest,
  CreateSessionRequest,
  ManualAttendanceRequest,
  PageResponse,
  SessionAttendanceResponse,
  SessionListQuery,
  TeacherOptionResponse,
  ValidateAttendanceRequest,
} from './sessions.models';

type HttpResponseBlob = import('@angular/common/http').HttpResponse<Blob>;

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

  /** `POST /api/v1/sessions/{publicId}/cancel` → 204 (G1-C — motif obligatoire). */
  cancelSession(publicId: string, reason: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/sessions/${encodeURIComponent(publicId)}/cancel`,
      { reason },
    );
  }

  // --- Points de contrôle (V10) --------------------------------------

  /** `GET /api/v1/sessions/{sessionId}/checkpoints`. */
  listCheckpoints(sessionId: string): Observable<CheckpointView[]> {
    return this.http.get<CheckpointView[]>(
      `${this.base}/sessions/${encodeURIComponent(sessionId)}/checkpoints`,
    );
  }

  /** `POST /api/v1/sessions/{sessionId}/checkpoints` → 201. */
  createCheckpoint(sessionId: string, body: CreateCheckpointRequest): Observable<CheckpointView> {
    return this.http.post<CheckpointView>(
      `${this.base}/sessions/${encodeURIComponent(sessionId)}/checkpoints`,
      body,
    );
  }

  /** `POST /api/v1/sessions/{sessionId}/checkpoints/{checkpointId}/open` → 204. */
  openCheckpoint(sessionId: string, checkpointId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/sessions/${encodeURIComponent(sessionId)}/checkpoints/${encodeURIComponent(checkpointId)}/open`,
      {},
    );
  }

  /** `POST /api/v1/sessions/{sessionId}/checkpoints/{checkpointId}/close` → 204. */
  closeCheckpoint(sessionId: string, checkpointId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/sessions/${encodeURIComponent(sessionId)}/checkpoints/${encodeURIComponent(checkpointId)}/close`,
      {},
    );
  }

  /** `POST /api/v1/sessions/{sessionId}/checkpoints/{checkpointId}/cancel` → 204. */
  cancelCheckpoint(
    sessionId: string,
    checkpointId: string,
    body: CancelAttendanceRequest,
  ): Observable<void> {
    return this.http.post<void>(
      `${this.base}/sessions/${encodeURIComponent(sessionId)}/checkpoints/${encodeURIComponent(checkpointId)}/cancel`,
      body,
    );
  }

  // --- Présence manuelle / correction (V10) -------------------------

  /** `POST /api/v1/sessions/{sessionId}/attendance/manual` → 201. */
  recordManual(
    sessionId: string,
    body: ManualAttendanceRequest,
  ): Observable<AttendanceRecordResponse> {
    return this.http.post<AttendanceRecordResponse>(
      `${this.base}/sessions/${encodeURIComponent(sessionId)}/attendance/manual`,
      body,
    );
  }

  /** `POST /api/v1/sessions/{sessionId}/attendance/{attendanceId}/correct`. */
  correctAttendance(
    sessionId: string,
    attendanceId: string,
    body: CorrectAttendanceRequest,
  ): Observable<AttendanceRecordResponse> {
    return this.http.post<AttendanceRecordResponse>(
      `${this.base}/sessions/${encodeURIComponent(sessionId)}/attendance/${encodeURIComponent(attendanceId)}/correct`,
      body,
    );
  }

  /** `POST /api/v1/sessions/{sessionId}/attendance/{attendanceId}/cancel`. */
  cancelAttendance(
    sessionId: string,
    attendanceId: string,
    body: CancelAttendanceRequest,
  ): Observable<AttendanceRecordResponse> {
    return this.http.post<AttendanceRecordResponse>(
      `${this.base}/sessions/${encodeURIComponent(sessionId)}/attendance/${encodeURIComponent(attendanceId)}/cancel`,
      body,
    );
  }

  /** `GET /api/v1/sessions/{sessionId}/attendance/{attendanceId}/history`. */
  attendanceHistory(
    sessionId: string,
    attendanceId: string,
  ): Observable<AttendanceCorrectionEntry[]> {
    return this.http.get<AttendanceCorrectionEntry[]>(
      `${this.base}/sessions/${encodeURIComponent(sessionId)}/attendance/${encodeURIComponent(attendanceId)}/history`,
    );
  }

  /**
   * `GET /api/v1/sessions/{sessionId}/attendance/candidates` — inscriptions
   * actives des classes de la séance, pour alimenter le sélecteur de
   * saisie manuelle. Le serveur applique le contrôle fin de la lecture
   * des présences ; aucun identifiant n'élargit le périmètre.
   */
  listAttendanceCandidates(sessionId: string): Observable<AttendanceCandidate[]> {
    return this.http.get<AttendanceCandidate[]>(
      `${this.base}/sessions/${encodeURIComponent(sessionId)}/attendance/candidates`,
    );
  }

  /**
   * `GET /api/v1/sessions/{sessionId}/attendance/export` — CSV des
   * présences de cette séance, en `blob`. Le composant appelant déclenche
   * le téléchargement ; le jeton ne transite jamais par l'URL.
   */
  exportSessionAttendance(sessionId: string): Observable<HttpResponseBlob> {
    return this.http.get(
      `${this.base}/sessions/${encodeURIComponent(sessionId)}/attendance/export`,
      { responseType: 'blob', observe: 'response' },
    );
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

  /**
   * `POST /api/v1/sessions/{sessionId}/checkpoints/{checkpointId}/attendance-token`
   * — émet un jeton pour un point de contrôle précis (V10).
   */
  issueCheckpointToken(
    sessionId: string,
    checkpointId: string,
  ): Observable<AttendanceTokenResponse> {
    return this.http.post<AttendanceTokenResponse>(
      `${this.base}/sessions/${encodeURIComponent(sessionId)}/checkpoints/${encodeURIComponent(checkpointId)}/attendance-token`,
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
