import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  AcademicListQuery,
  AcademicYearResponse,
  ClassGroupListQuery,
  ClassGroupResponse,
  PageResponse,
  ProgramLevelResponse,
  ProgramResponse,
  PromotionListQuery,
  PromotionResponse,
} from './academic.models';

/**
 * Accès en **lecture seule** au référentiel académique
 * (`com.esic.connect.academic`). Ce service ne consomme que des routes
 * déjà exposées par le back-end ; aucune n'est inventée, aucune écriture
 * (`POST` / `PATCH` create·update·archive·restore) n'est appelée.
 *
 * Les appels sont authentifiés par le jeton porteur ajouté par
 * `authTokenInterceptor` (jeton en mémoire). L'autorisation effective est
 * décidée par Spring Security (`AcademicWeb.READ_ROLES` + filtrage de
 * périmètre pour un `PEDAGOGICAL_MANAGER`) : le `roleGuard` de la route
 * ne fait que masquer une navigation.
 */
@Injectable({ providedIn: 'root' })
export class AcademicApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1`;

  /** `GET /api/v1/academic-years`. */
  listAcademicYears(query: AcademicListQuery): Observable<PageResponse<AcademicYearResponse>> {
    return this.http.get<PageResponse<AcademicYearResponse>>(`${this.base}/academic-years`, {
      params: listParams(query),
    });
  }

  /** `GET /api/v1/academic-years/{publicId}`. */
  getAcademicYear(publicId: string): Observable<AcademicYearResponse> {
    return this.http.get<AcademicYearResponse>(
      `${this.base}/academic-years/${encodeURIComponent(publicId)}`,
    );
  }

  /** `GET /api/v1/programs`. */
  listPrograms(query: AcademicListQuery): Observable<PageResponse<ProgramResponse>> {
    return this.http.get<PageResponse<ProgramResponse>>(`${this.base}/programs`, {
      params: listParams(query),
    });
  }

  /** `GET /api/v1/programs/{publicId}`. */
  getProgram(publicId: string): Observable<ProgramResponse> {
    return this.http.get<ProgramResponse>(`${this.base}/programs/${encodeURIComponent(publicId)}`);
  }

  /** `GET /api/v1/programs/{programPublicId}/levels` (liste nichée). */
  listProgramLevels(
    programPublicId: string,
    query: AcademicListQuery,
  ): Observable<PageResponse<ProgramLevelResponse>> {
    return this.http.get<PageResponse<ProgramLevelResponse>>(
      `${this.base}/programs/${encodeURIComponent(programPublicId)}/levels`,
      { params: listParams(query) },
    );
  }

  /** `GET /api/v1/program-levels/{publicId}`. */
  getProgramLevel(publicId: string): Observable<ProgramLevelResponse> {
    return this.http.get<ProgramLevelResponse>(
      `${this.base}/program-levels/${encodeURIComponent(publicId)}`,
    );
  }

  /** `GET /api/v1/promotions` — filtres `program` / `academicYear` inclus. */
  listPromotions(query: PromotionListQuery): Observable<PageResponse<PromotionResponse>> {
    return this.http.get<PageResponse<PromotionResponse>>(`${this.base}/promotions`, {
      params: listParams(query, { program: query.program, academicYear: query.academicYear }),
    });
  }

  /** `GET /api/v1/promotions/{publicId}`. */
  getPromotion(publicId: string): Observable<PromotionResponse> {
    return this.http.get<PromotionResponse>(
      `${this.base}/promotions/${encodeURIComponent(publicId)}`,
    );
  }

  /** `GET /api/v1/class-groups` — filtres `promotion` / `programLevel` / `site` inclus. */
  listClassGroups(query: ClassGroupListQuery): Observable<PageResponse<ClassGroupResponse>> {
    return this.http.get<PageResponse<ClassGroupResponse>>(`${this.base}/class-groups`, {
      params: listParams(query, {
        promotion: query.promotion,
        programLevel: query.programLevel,
        site: query.site,
      }),
    });
  }

  /** `GET /api/v1/class-groups/{publicId}`. */
  getClassGroup(publicId: string): Observable<ClassGroupResponse> {
    return this.http.get<ClassGroupResponse>(
      `${this.base}/class-groups/${encodeURIComponent(publicId)}`,
    );
  }
}

/**
 * Construit les `HttpParams` communs (`q`, `status`, `sort`, `page`,
 * `size`) plus d'éventuels filtres additionnels, en ignorant toute valeur
 * absente (`null`, `undefined`, chaîne vide) : aucune clé non renseignée
 * n'est envoyée.
 */
function listParams(
  query: AcademicListQuery,
  extra: Record<string, string | null | undefined> = {},
): HttpParams {
  const values: Record<string, string | number | null | undefined> = {
    q: query.q,
    status: query.status,
    sort: query.sort,
    page: query.page,
    size: query.size,
    ...extra,
  };
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value === null || value === undefined || value === '') {
      continue;
    }
    params = params.set(key, String(value));
  }
  return params;
}
