import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  AcademicListQuery,
  AcademicYearResponse,
  ArchiveAcademicRequest,
  ClassGroupListQuery,
  ClassGroupResponse,
  CreateAcademicYearRequest,
  CreateClassGroupRequest,
  CreateProgramLevelRequest,
  CreateProgramRequest,
  CreatePromotionRequest,
  PageResponse,
  ProgramLevelResponse,
  ProgramResponse,
  PromotionListQuery,
  PromotionResponse,
  UpdateAcademicYearRequest,
  UpdateClassGroupRequest,
  UpdateProgramLevelRequest,
  UpdateProgramRequest,
  UpdatePromotionRequest,
} from './academic.models';

/**
 * Accès au référentiel académique (`com.esic.connect.academic`) : lecture
 * (listes, fiches) **et** écriture (création, modification, archivage,
 * restauration — EF-ACA-001..007, docs/02 §39.1 : *« l'administrateur crée
 * une formation, un niveau et une année ; le responsable crée une
 * promotion et une classe »*). Chaque route consommée existe déjà côté
 * back-end ; aucune n'est inventée.
 *
 * Les appels sont authentifiés par le jeton porteur ajouté par
 * `authTokenInterceptor` (jeton en mémoire). L'autorisation effective est
 * décidée par Spring Security (`AcademicWeb.READ_ROLES` / `WRITE_ROLES` /
 * `SCOPED_WRITE_ROLES` + filtrage de périmètre pour un
 * `PEDAGOGICAL_MANAGER`) : le `roleGuard` de la route ne fait que masquer
 * une navigation, jamais une garantie de sécurité.
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

  // --- Écriture : années scolaires -----------------------------------

  /** `POST /api/v1/academic-years`. */
  createAcademicYear(request: CreateAcademicYearRequest): Observable<AcademicYearResponse> {
    return this.http.post<AcademicYearResponse>(`${this.base}/academic-years`, request);
  }

  /** `PATCH /api/v1/academic-years/{publicId}`. */
  updateAcademicYear(
    publicId: string,
    request: UpdateAcademicYearRequest,
  ): Observable<AcademicYearResponse> {
    return this.http.patch<AcademicYearResponse>(
      `${this.base}/academic-years/${encodeURIComponent(publicId)}`,
      request,
    );
  }

  /** `POST /api/v1/academic-years/{publicId}/archive`. */
  archiveAcademicYear(publicId: string, request: ArchiveAcademicRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/academic-years/${encodeURIComponent(publicId)}/archive`,
      request,
    );
  }

  /** `POST /api/v1/academic-years/{publicId}/restore`. */
  restoreAcademicYear(publicId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/academic-years/${encodeURIComponent(publicId)}/restore`,
      {},
    );
  }

  // --- Écriture : formations ------------------------------------------

  /** `POST /api/v1/programs`. */
  createProgram(request: CreateProgramRequest): Observable<ProgramResponse> {
    return this.http.post<ProgramResponse>(`${this.base}/programs`, request);
  }

  /** `PATCH /api/v1/programs/{publicId}`. */
  updateProgram(publicId: string, request: UpdateProgramRequest): Observable<ProgramResponse> {
    return this.http.patch<ProgramResponse>(
      `${this.base}/programs/${encodeURIComponent(publicId)}`,
      request,
    );
  }

  /** `POST /api/v1/programs/{publicId}/archive`. */
  archiveProgram(publicId: string, request: ArchiveAcademicRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/programs/${encodeURIComponent(publicId)}/archive`,
      request,
    );
  }

  /** `POST /api/v1/programs/{publicId}/restore`. */
  restoreProgram(publicId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/programs/${encodeURIComponent(publicId)}/restore`, {});
  }

  // --- Écriture : niveaux (nichés sous une formation) -----------------

  /** `POST /api/v1/programs/{programPublicId}/levels`. */
  createProgramLevel(
    programPublicId: string,
    request: CreateProgramLevelRequest,
  ): Observable<ProgramLevelResponse> {
    return this.http.post<ProgramLevelResponse>(
      `${this.base}/programs/${encodeURIComponent(programPublicId)}/levels`,
      request,
    );
  }

  /** `PATCH /api/v1/program-levels/{publicId}`. */
  updateProgramLevel(
    publicId: string,
    request: UpdateProgramLevelRequest,
  ): Observable<ProgramLevelResponse> {
    return this.http.patch<ProgramLevelResponse>(
      `${this.base}/program-levels/${encodeURIComponent(publicId)}`,
      request,
    );
  }

  /** `POST /api/v1/program-levels/{publicId}/archive`. */
  archiveProgramLevel(publicId: string, request: ArchiveAcademicRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/program-levels/${encodeURIComponent(publicId)}/archive`,
      request,
    );
  }

  /** `POST /api/v1/program-levels/{publicId}/restore`. */
  restoreProgramLevel(publicId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/program-levels/${encodeURIComponent(publicId)}/restore`,
      {},
    );
  }

  // --- Écriture : promotions -------------------------------------------

  /** `POST /api/v1/promotions`. */
  createPromotion(request: CreatePromotionRequest): Observable<PromotionResponse> {
    return this.http.post<PromotionResponse>(`${this.base}/promotions`, request);
  }

  /** `PATCH /api/v1/promotions/{publicId}`. */
  updatePromotion(
    publicId: string,
    request: UpdatePromotionRequest,
  ): Observable<PromotionResponse> {
    return this.http.patch<PromotionResponse>(
      `${this.base}/promotions/${encodeURIComponent(publicId)}`,
      request,
    );
  }

  /** `POST /api/v1/promotions/{publicId}/archive`. */
  archivePromotion(publicId: string, request: ArchiveAcademicRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/promotions/${encodeURIComponent(publicId)}/archive`,
      request,
    );
  }

  /** `POST /api/v1/promotions/{publicId}/restore`. */
  restorePromotion(publicId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/promotions/${encodeURIComponent(publicId)}/restore`,
      {},
    );
  }

  // --- Écriture : classes ----------------------------------------------

  /** `POST /api/v1/class-groups`. */
  createClassGroup(request: CreateClassGroupRequest): Observable<ClassGroupResponse> {
    return this.http.post<ClassGroupResponse>(`${this.base}/class-groups`, request);
  }

  /** `PATCH /api/v1/class-groups/{publicId}`. */
  updateClassGroup(
    publicId: string,
    request: UpdateClassGroupRequest,
  ): Observable<ClassGroupResponse> {
    return this.http.patch<ClassGroupResponse>(
      `${this.base}/class-groups/${encodeURIComponent(publicId)}`,
      request,
    );
  }

  /** `POST /api/v1/class-groups/{publicId}/archive`. */
  archiveClassGroup(publicId: string, request: ArchiveAcademicRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/class-groups/${encodeURIComponent(publicId)}/archive`,
      request,
    );
  }

  /** `POST /api/v1/class-groups/{publicId}/restore`. */
  restoreClassGroup(publicId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/class-groups/${encodeURIComponent(publicId)}/restore`,
      {},
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
