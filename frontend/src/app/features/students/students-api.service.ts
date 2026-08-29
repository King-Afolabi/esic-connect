import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  EnrollmentListQuery,
  EnrollmentResponse,
  PageResponse,
  StudentProfileListQuery,
  StudentProfileResponse,
  UserIdentitySummary,
} from './students.models';

/**
 * Accès en **lecture seule** aux endpoints du module `enrollment` et, de
 * façon facultative, à la fiche d'identité `GET /api/v1/users/{publicId}`.
 *
 * Ce service ne consomme que des routes déjà exposées par le back-end ;
 * aucune n'est inventée. Les appels sont authentifiés par le jeton
 * porteur ajouté par `authTokenInterceptor` (le jeton reste en mémoire).
 * L'autorisation effective est décidée par Spring Security
 * (`EnrollmentWeb.MANAGE_ROLES`) : les gardes de route côté client ne
 * font que masquer une navigation.
 */
@Injectable({ providedIn: 'root' })
export class StudentsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1`;

  /** `GET /api/v1/student-profiles` — liste paginée, filtrée, triée. */
  listProfiles(
    query: StudentProfileListQuery,
  ): Observable<PageResponse<StudentProfileResponse>> {
    return this.http.get<PageResponse<StudentProfileResponse>>(`${this.base}/student-profiles`, {
      params: toHttpParams({
        q: query.q,
        status: query.status,
        sort: query.sort,
        page: query.page,
        size: query.size,
      }),
    });
  }

  /** `GET /api/v1/student-profiles/{publicId}`. */
  getProfile(publicId: string): Observable<StudentProfileResponse> {
    return this.http.get<StudentProfileResponse>(
      `${this.base}/student-profiles/${encodeURIComponent(publicId)}`,
    );
  }

  /**
   * `GET /api/v1/enrollments` — utilisé ici avec le filtre `student` pour
   * l'historique d'inscriptions d'un apprenant.
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

  /**
   * `GET /api/v1/users/{publicId}` — identité civile, **facultative**. Le
   * profil apprenant n'exposant que `userPublicId`, cet appel enrichit la
   * fiche ; son échec est ignoré par l'appelant.
   */
  getUserIdentity(publicId: string): Observable<UserIdentitySummary> {
    return this.http.get<UserIdentitySummary>(
      `${this.base}/users/${encodeURIComponent(publicId)}`,
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
