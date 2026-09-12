import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CreateStudentAccountRequest,
  CreatedUserResponse,
  EnrollStudentRequest,
  EnrollmentListQuery,
  EnrollmentResponse,
  PageResponse,
  CloseEnrollmentRequest,
  RemoteAttendanceAuthorizationResponse,
  RemoteAttendanceAuthorizeRequest,
  StudentListQuery,
  StudentResponse,
  TransferEnrollmentRequest,
} from './students.models';

/**
 * Accès aux endpoints des modules `enrollment` et `identity` de l'espace
 * « Apprenants » : consultation (écran « Apprenants », profils facultatifs,
 * inscriptions), suivi à distance individuel, et **création manuelle d'un
 * apprenant** (Lot H — trois routes existantes enchaînées).
 *
 * Refonte 2026-09 : l'écran « Apprenants » repose sur
 * `GET /api/v1/students` — tous les comptes porteurs du rôle `STUDENT`,
 * avec ou sans numéro étudiant, avec ou sans inscription. Il n'existe
 * plus de `student-profiles` séparé : `enrollments` reste la seule route
 * de gestion de données facultatives, en plus de la source de la liste
 * elle-même.
 *
 * Ce service ne consomme que des routes déjà exposées par le back-end ;
 * aucune n'est inventée. Les appels sont authentifiés par le jeton
 * porteur ajouté par `authTokenInterceptor` (le jeton reste en mémoire).
 * L'autorisation effective est décidée par Spring Security
 * (`EnrollmentWeb.READ_ROLES` / `MANAGE_ROLES` ; `POST /users` exige
 * `ADMIN` / `SUPER_ADMIN`) : les gardes de route côté client ne font que
 * masquer une navigation.
 */
@Injectable({ providedIn: 'root' })
export class StudentsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1`;

  /** `GET /api/v1/students` — liste paginée, filtrée, triée (rôle STUDENT). */
  listStudents(query: StudentListQuery): Observable<PageResponse<StudentResponse>> {
    return this.http.get<PageResponse<StudentResponse>>(`${this.base}/students`, {
      params: toHttpParams({
        q: query.q,
        status: query.status,
        sort: query.sort,
        page: query.page,
        size: query.size,
      }),
    });
  }

  /** `GET /api/v1/students/{userPublicId}`. */
  getStudent(userPublicId: string): Observable<StudentResponse> {
    return this.http.get<StudentResponse>(`${this.base}/students/${encodeURIComponent(userPublicId)}`);
  }

  /**
   * `GET /api/v1/enrollments` — utilisé ici avec le filtre `student` (le
   * **compte** apprenant) pour l'historique d'inscriptions d'un apprenant.
   */
  listEnrollments(query: EnrollmentListQuery): Observable<PageResponse<EnrollmentResponse>> {
    return this.http.get<PageResponse<EnrollmentResponse>>(`${this.base}/enrollments`, {
      params: toHttpParams({
        student: query.student,
        classGroup: query.classGroup,
        status: query.status,
        sort: query.sort,
        page: query.page,
        size: query.size,
      }),
    });
  }

  // -------------------------------------------------------------------
  // Création manuelle d'un apprenant (Lot H)
  // -------------------------------------------------------------------

  /**
   * `POST /api/v1/users` — crée le compte `PENDING_ACTIVATION` avec le
   * rôle `STUDENT` et lui émet son invitation (aucun mot de passe n'est
   * transmis : la personne le choisit via le lien). Le numéro étudiant et
   * la date de naissance, facultatifs, sont portés directement par ce
   * même appel (refonte 2026-09 : plus de `student-profiles` séparé).
   * `409` si l'adresse est déjà utilisée. Réservé à `ADMIN` / `SUPER_ADMIN`
   * côté serveur. L'apprenant est immédiatement visible dans
   * `/api/v1/students`, sans qu'aucune autre étape ne soit requise.
   */
  createStudentAccount(body: CreateStudentAccountRequest): Observable<CreatedUserResponse> {
    return this.http.post<CreatedUserResponse>(`${this.base}/users`, body);
  }

  /**
   * `POST /api/v1/enrollments` — inscription initiale dans une classe,
   * pour le **compte** apprenant désigné (`studentUserPublicId`), avec sa
   * situation d'alternance éventuelle. Opération distincte et non
   * obligatoire : un compte `STUDENT` sans inscription reste un apprenant
   * pleinement visible.
   */
  enrollStudent(body: EnrollStudentRequest): Observable<EnrollmentResponse> {
    return this.http.post<EnrollmentResponse>(`${this.base}/enrollments`, body);
  }

  /**
   * `POST /api/v1/enrollments/{publicId}/transfer` — changement de classe
   * (docs/04 §13.2). Clôture l'inscription courante en `TRANSFERRED` et en
   * ouvre une nouvelle dans la classe cible dès le lendemain de
   * `effectiveDate` : l'ancienne inscription reste consultable dans
   * l'historique (RG-006, RG-023). Typiquement un passage en année
   * supérieure en fin d'année scolaire.
   */
  transferEnrollment(publicId: string, body: TransferEnrollmentRequest): Observable<EnrollmentResponse> {
    return this.http.post<EnrollmentResponse>(
      `${this.base}/enrollments/${encodeURIComponent(publicId)}/transfer`,
      body,
    );
  }

  /**
   * `POST /api/v1/enrollments/{publicId}/close` — clôture définitive
   * (fin de cursus ou départ), sans nouvelle inscription associée.
   */
  closeEnrollment(publicId: string, body: CloseEnrollmentRequest): Observable<EnrollmentResponse> {
    return this.http.post<EnrollmentResponse>(
      `${this.base}/enrollments/${encodeURIComponent(publicId)}/close`,
      body,
    );
  }

  // -------------------------------------------------------------------
  // Suivi à distance individuel (EF-ENR-004)
  // -------------------------------------------------------------------

  /** `GET /api/v1/remote-attendance-authorizations/students/{userPublicId}`. */
  listRemoteAuthorizations(
    studentUserPublicId: string,
  ): Observable<RemoteAttendanceAuthorizationResponse[]> {
    return this.http.get<RemoteAttendanceAuthorizationResponse[]>(
      `${this.base}/remote-attendance-authorizations/students/${encodeURIComponent(
        studentUserPublicId,
      )}`,
    );
  }

  /** `POST /api/v1/remote-attendance-authorizations` — `201`. */
  authorizeRemoteAttendance(
    request: RemoteAttendanceAuthorizeRequest,
  ): Observable<RemoteAttendanceAuthorizationResponse> {
    return this.http.post<RemoteAttendanceAuthorizationResponse>(
      `${this.base}/remote-attendance-authorizations`,
      request,
    );
  }

  /**
   * `POST /api/v1/remote-attendance-authorizations/{publicId}/revoke`.
   * L'autorisation n'est pas supprimée : elle passe `REVOKED`, motif
   * conservé.
   */
  revokeRemoteAttendance(
    publicId: string,
    reason: string,
  ): Observable<RemoteAttendanceAuthorizationResponse> {
    return this.http.post<RemoteAttendanceAuthorizationResponse>(
      `${this.base}/remote-attendance-authorizations/${encodeURIComponent(publicId)}/revoke`,
      { reason },
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
