import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PageResponse } from '../sessions/sessions.models';
import {
  AmendJustificationRequest,
  DecideEarlyDepartureRequest,
  DeclareEarlyDepartureRequest,
  EarlyDeparture,
  ForwardEarlyDepartureRequest,
  ClassReportRow,
  JustificationAttachmentMeta,
  JustificationResponse,
  MyAttendanceDetail,
  MyAttendanceQuery,
  MyAttendanceRow,
  ReportDocumentCheck,
  ReportDocumentSummary,
  ReportExportFormat,
  ReportKind,
  ReportQuery,
  ReviewJustificationRequest,
  SessionReportRow,
  StudentReportRow,
  SubmitJustificationRequest,
  SummaryResponse,
  TransparencyEntry,
} from './attendance.models';

/**
 * Accès HTTP à la gestion de l'assiduité (V10). Ne consomme que des
 * routes réellement exposées par le back-end. Le serveur résout
 * l'apprenant à partir du seul JWT ; aucun identifiant d'apprenant n'est
 * transmis par le client sur les routes `/me`. Les exports CSV sont
 * récupérés en `blob` et remis à l'utilisateur par un téléchargement
 * programmatique — jamais de secret ni de filtre dans l'URL de
 * navigation Angular.
 */
@Injectable({ providedIn: 'root' })
export class AttendanceApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1`;

  // --- Espace apprenant --------------------------------------------

  /** `GET /api/v1/me/attendance`. */
  listMyAttendance(query: MyAttendanceQuery): Observable<PageResponse<MyAttendanceRow>> {
    return this.http.get<PageResponse<MyAttendanceRow>>(`${this.base}/me/attendance`, {
      params: toParams({
        from: query.from,
        to: query.to,
        status: query.status,
        page: query.page,
        size: query.size,
      }),
    });
  }

  /** `GET /api/v1/me/attendance/{attendanceId}`. */
  getMyAttendance(attendanceId: string): Observable<MyAttendanceDetail> {
    return this.http.get<MyAttendanceDetail>(
      `${this.base}/me/attendance/${encodeURIComponent(attendanceId)}`,
    );
  }

  /**
   * `GET /api/v1/me/attendance/transparency` (EF-ATT-014). Le serveur
   * bâtit le journal depuis le seul JWT : aucun identifiant d'apprenant
   * n'est transmis, et il n'en accepterait pas.
   */
  transparencyJournal(query: {
    from?: string | null;
    to?: string | null;
    page?: number;
    size?: number;
  }): Observable<PageResponse<TransparencyEntry>> {
    return this.http.get<PageResponse<TransparencyEntry>>(
      `${this.base}/me/attendance/transparency`,
      {
        params: toParams({
          from: query.from,
          to: query.to,
          page: query.page,
          size: query.size,
        }),
      },
    );
  }

  /** `GET /api/v1/me/attendance/early-departures` (EF-ATT-013). */
  listMyEarlyDepartures(): Observable<EarlyDeparture[]> {
    return this.http.get<EarlyDeparture[]>(`${this.base}/me/attendance/early-departures`);
  }

  /** `POST /api/v1/attendance/early-departure` → 201 (EF-ATT-013). */
  declareEarlyDeparture(body: DeclareEarlyDepartureRequest): Observable<EarlyDeparture> {
    return this.http.post<EarlyDeparture>(`${this.base}/attendance/early-departure`, body);
  }

  /** `GET /api/v1/sessions/{id}/attendance/early-departures` (gestion). */
  listSessionEarlyDepartures(sessionPublicId: string): Observable<EarlyDeparture[]> {
    return this.http.get<EarlyDeparture[]>(
      `${this.base}/sessions/${encodeURIComponent(sessionPublicId)}/attendance/early-departures`,
    );
  }

  /** `POST /api/v1/attendance/early-departures/{id}/forward`. */
  forwardEarlyDeparture(
    publicId: string,
    body: ForwardEarlyDepartureRequest,
  ): Observable<EarlyDeparture> {
    return this.http.post<EarlyDeparture>(
      `${this.base}/attendance/early-departures/${encodeURIComponent(publicId)}/forward`,
      body,
    );
  }

  /** `POST /api/v1/attendance/early-departures/{id}/decision`. */
  decideEarlyDeparture(
    publicId: string,
    body: DecideEarlyDepartureRequest,
  ): Observable<EarlyDeparture> {
    return this.http.post<EarlyDeparture>(
      `${this.base}/attendance/early-departures/${encodeURIComponent(publicId)}/decision`,
      body,
    );
  }

  /** `POST /api/v1/me/attendance/justifications` → 201. */
  submitJustification(body: SubmitJustificationRequest): Observable<JustificationResponse> {
    return this.http.post<JustificationResponse>(`${this.base}/me/attendance/justifications`, body);
  }

  /** `PUT /api/v1/me/attendance/justifications/{id}`. */
  amendJustification(
    justificationId: string,
    body: AmendJustificationRequest,
  ): Observable<JustificationResponse> {
    return this.http.put<JustificationResponse>(
      `${this.base}/me/attendance/justifications/${encodeURIComponent(justificationId)}`,
      body,
    );
  }

  /** `GET /api/v1/me/attendance/justifications`. */
  listMyJustifications(): Observable<JustificationResponse[]> {
    return this.http.get<JustificationResponse[]>(`${this.base}/me/attendance/justifications`);
  }

  /** `GET /api/v1/me/attendance/justifications/{id}`. */
  getMyJustification(justificationId: string): Observable<JustificationResponse> {
    return this.http.get<JustificationResponse>(
      `${this.base}/me/attendance/justifications/${encodeURIComponent(justificationId)}`,
    );
  }

  // --- Pièces jointes des justificatifs (bloc G1-E) --------------

  /** `GET /api/v1/me/attendance/justifications/{id}/attachment` (404 si aucune pièce STORED). */
  getMyJustificationAttachment(justificationId: string): Observable<JustificationAttachmentMeta> {
    return this.http.get<JustificationAttachmentMeta>(
      `${this.base}/me/attendance/justifications/${encodeURIComponent(justificationId)}/attachment`,
    );
  }

  /** `POST /api/v1/me/attendance/justifications/{id}/attachment` (multipart) → 201. */
  uploadMyJustificationAttachment(
    justificationId: string,
    file: File,
  ): Observable<JustificationAttachmentMeta> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<JustificationAttachmentMeta>(
      `${this.base}/me/attendance/justifications/${encodeURIComponent(justificationId)}/attachment`,
      form,
    );
  }

  /** `DELETE /api/v1/me/attendance/justifications/{id}/attachment` → 204. */
  deleteMyJustificationAttachment(justificationId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/me/attendance/justifications/${encodeURIComponent(justificationId)}/attachment`,
    );
  }

  /** `GET /api/v1/me/attendance/justifications/{id}/attachment/download` — blob. */
  downloadMyJustificationAttachment(justificationId: string): Observable<HttpResponseBlob> {
    return this.http.get(
      `${this.base}/me/attendance/justifications/${encodeURIComponent(justificationId)}/attachment/download`,
      { responseType: 'blob', observe: 'response' },
    );
  }

  /** `GET /api/v1/attendance/justifications/{id}/attachment` (examinateur ; 404 si aucune). */
  getJustificationAttachmentForReview(justificationId: string): Observable<JustificationAttachmentMeta> {
    return this.http.get<JustificationAttachmentMeta>(
      `${this.base}/attendance/justifications/${encodeURIComponent(justificationId)}/attachment`,
    );
  }

  /** `GET /api/v1/attendance/justifications/{id}/attachment/download` (examinateur) — blob. */
  downloadJustificationAttachmentForReview(justificationId: string): Observable<HttpResponseBlob> {
    return this.http.get(
      `${this.base}/attendance/justifications/${encodeURIComponent(justificationId)}/attachment/download`,
      { responseType: 'blob', observe: 'response' },
    );
  }

  // --- Gestion des justificatifs ----------------------------------

  /** `GET /api/v1/attendance/justifications?status=`. */
  listJustificationsForReview(status?: string | null): Observable<JustificationResponse[]> {
    return this.http.get<JustificationResponse[]>(`${this.base}/attendance/justifications`, {
      params: toParams({ status }),
    });
  }

  /** `GET /api/v1/attendance/justifications/{id}`. */
  getJustificationForReview(justificationId: string): Observable<JustificationResponse> {
    return this.http.get<JustificationResponse>(
      `${this.base}/attendance/justifications/${encodeURIComponent(justificationId)}`,
    );
  }

  /** `POST /api/v1/attendance/justifications/{id}/review`. */
  reviewJustification(
    justificationId: string,
    body: ReviewJustificationRequest,
  ): Observable<JustificationResponse> {
    return this.http.post<JustificationResponse>(
      `${this.base}/attendance/justifications/${encodeURIComponent(justificationId)}/review`,
      body,
    );
  }

  // --- Rapports ---------------------------------------------------

  /** `GET /api/v1/attendance/reports/summary`. */
  summary(query: ReportQuery): Observable<SummaryResponse> {
    return this.http.get<SummaryResponse>(`${this.base}/attendance/reports/summary`, {
      params: toParams({ from: query.from, to: query.to, classGroup: query.classGroup }),
    });
  }

  sessionsReport(query: ReportQuery): Observable<PageResponse<SessionReportRow>> {
    return this.http.get<PageResponse<SessionReportRow>>(
      `${this.base}/attendance/reports/sessions`,
      { params: reportParams(query) },
    );
  }

  classesReport(query: ReportQuery): Observable<PageResponse<ClassReportRow>> {
    return this.http.get<PageResponse<ClassReportRow>>(`${this.base}/attendance/reports/classes`, {
      params: reportParams(query),
    });
  }

  studentsReport(query: ReportQuery): Observable<PageResponse<StudentReportRow>> {
    return this.http.get<PageResponse<StudentReportRow>>(
      `${this.base}/attendance/reports/students`,
      { params: reportParams(query) },
    );
  }

  /**
   * `GET /api/v1/attendance/reports/{kind}/export` — CSV en `blob`.
   * Le composant appelant déclenche le téléchargement.
   */
  /**
   * Export d'un rapport (EF-REP-003, EF-REP-004, EF-REP-005).
   *
   * Le `format` est transmis au serveur, qui le résout contre une liste
   * fermée : un format inconnu produit un `400` explicite, jamais un
   * repli silencieux sur le CSV.
   */
  exportReport(
    kind: ReportKind,
    query: ReportQuery,
    format: ReportExportFormat = 'csv',
  ): Observable<HttpResponseBlob> {
    return this.http.get(`${this.base}/attendance/reports/${kind}/export`, {
      params: reportParams(query).set('format', format),
      responseType: 'blob',
      observe: 'response',
    });
  }

  /**
   * Émet une attestation d'assiduité (EF-REP-006, AC-033) et renvoie le
   * PDF. L'identifiant du document arrive dans l'en-tête `X-Document-Id`
   * — le client l'affiche sans avoir à ouvrir le PDF.
   */
  issueAttestation(
    studentProfilePublicId: string,
    from: string | null,
    to: string | null,
  ): Observable<HttpResponseBlob> {
    return this.http.post(
      `${this.base}/attendance/reports/attestation`,
      null,
      {
        params: toParams({ studentProfile: studentProfilePublicId, from, to }),
        responseType: 'blob',
        observe: 'response',
      },
    );
  }

  /** Registre des documents officiels émis. */
  listAttestations(limit = 20): Observable<ReportDocumentSummary[]> {
    return this.http.get<ReportDocumentSummary[]>(
      `${this.base}/attendance/reports/attestation`,
      { params: new HttpParams().set('limit', limit) },
    );
  }

  /** Vérifie un identifiant de document présenté par un tiers (AC-033). */
  verifyAttestation(documentId: string): Observable<ReportDocumentCheck> {
    return this.http.get<ReportDocumentCheck>(
      `${this.base}/attendance/reports/attestation/${encodeURIComponent(documentId)}`,
    );
  }
}

type HttpResponseBlob = import('@angular/common/http').HttpResponse<Blob>;

function reportParams(query: ReportQuery): HttpParams {
  return toParams({
    from: query.from,
    to: query.to,
    classGroup: query.classGroup,
    studentProfile: query.studentProfile,
    sort: query.sort,
    page: query.page,
    size: query.size,
  });
}

function toParams(values: Record<string, string | number | null | undefined>): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value === null || value === undefined || value === '') {
      continue;
    }
    params = params.set(key, String(value));
  }
  return params;
}

/**
 * Déclenche le téléchargement d'un blob CSV côté navigateur, en lisant le
 * nom depuis l'en-tête `Content-Disposition` s'il est présent.
 */
export function triggerCsvDownload(response: HttpResponseBlob, fallbackName: string): void {
  const disposition = response.headers.get('content-disposition') ?? '';
  const match = /filename="?([^"]+)"?/i.exec(disposition);
  const name = match?.[1] ?? fallbackName;
  const blob = response.body ?? new Blob([], { type: 'text/csv' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = name;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

/**
 * Déclenche le téléchargement d'une pièce jointe (bloc G1-E). Le serveur
 * force déjà `Content-Disposition: attachment` ; côté navigateur on crée
 * une `object URL`, on clique un lien, puis on révoque immédiatement
 * l'URL (aucun blob conservé).
 */
export function triggerAttachmentDownload(response: HttpResponseBlob, fallbackName: string): void {
  triggerCsvDownload(response, fallbackName);
}
