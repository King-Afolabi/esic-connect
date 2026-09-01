import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  ArchiveRequest,
  BuildingResponse,
  CreateBuildingRequest,
  CreateNetworkRangeRequest,
  CreateRoomRequest,
  CreateSiteRequest,
  NetworkRangeListQuery,
  OrganizationListQuery,
  PageResponse,
  RoomListQuery,
  RoomResponse,
  SiteNetworkRangeResponse,
  SiteResponse,
  UpdateBuildingRequest,
  UpdateRoomRequest,
  UpdateSiteRequest,
} from './organization.models';

/**
 * Accès au référentiel organisationnel (`com.esic.connect.organization`) :
 * sites, bâtiments, salles et plages réseau autorisées.
 *
 * Ce service ne consomme que des routes **déjà exposées** par le back-end
 * (`SiteController`, `BuildingController`, `RoomController`,
 * `SiteNetworkRangeController`) ; aucune n'est inventée. Les appels sont
 * authentifiés par le jeton porteur ajouté par `authTokenInterceptor`
 * (jeton en mémoire). L'autorisation effective est décidée par Spring
 * Security : le `roleGuard` de la route et la visibilité des boutons ne
 * font que masquer l'interface (un `403` API est rendu « accès refusé »).
 */
@Injectable({ providedIn: 'root' })
export class OrganizationApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1`;

  // --- Sites ---------------------------------------------------------------

  /** `GET /api/v1/sites`. */
  listSites(query: OrganizationListQuery): Observable<PageResponse<SiteResponse>> {
    return this.http.get<PageResponse<SiteResponse>>(`${this.base}/sites`, {
      params: listParams(query),
    });
  }

  /** `GET /api/v1/sites/{publicId}`. */
  getSite(publicId: string): Observable<SiteResponse> {
    return this.http.get<SiteResponse>(`${this.base}/sites/${encodeURIComponent(publicId)}`);
  }

  /** `POST /api/v1/sites` — `201 Created`. */
  createSite(body: CreateSiteRequest): Observable<SiteResponse> {
    return this.http.post<SiteResponse>(`${this.base}/sites`, body);
  }

  /** `PATCH /api/v1/sites/{publicId}`. */
  updateSite(publicId: string, body: UpdateSiteRequest): Observable<SiteResponse> {
    return this.http.patch<SiteResponse>(
      `${this.base}/sites/${encodeURIComponent(publicId)}`,
      body,
    );
  }

  /** `POST /api/v1/sites/{publicId}/archive` — `204`. */
  archiveSite(publicId: string, body: ArchiveRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/sites/${encodeURIComponent(publicId)}/archive`,
      body,
    );
  }

  /** `POST /api/v1/sites/{publicId}/restore` — `204`. */
  restoreSite(publicId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/sites/${encodeURIComponent(publicId)}/restore`,
      {},
    );
  }

  // --- Buildings (nested under a site) -----------------------------------

  /** `GET /api/v1/sites/{sitePublicId}/buildings`. */
  listBuildings(
    sitePublicId: string,
    query: OrganizationListQuery,
  ): Observable<PageResponse<BuildingResponse>> {
    return this.http.get<PageResponse<BuildingResponse>>(
      `${this.base}/sites/${encodeURIComponent(sitePublicId)}/buildings`,
      { params: listParams(query) },
    );
  }

  /** `POST /api/v1/sites/{sitePublicId}/buildings` — `201 Created`. */
  createBuilding(
    sitePublicId: string,
    body: CreateBuildingRequest,
  ): Observable<BuildingResponse> {
    return this.http.post<BuildingResponse>(
      `${this.base}/sites/${encodeURIComponent(sitePublicId)}/buildings`,
      body,
    );
  }

  /** `GET /api/v1/buildings/{publicId}`. */
  getBuilding(publicId: string): Observable<BuildingResponse> {
    return this.http.get<BuildingResponse>(
      `${this.base}/buildings/${encodeURIComponent(publicId)}`,
    );
  }

  /** `PATCH /api/v1/buildings/{publicId}`. */
  updateBuilding(publicId: string, body: UpdateBuildingRequest): Observable<BuildingResponse> {
    return this.http.patch<BuildingResponse>(
      `${this.base}/buildings/${encodeURIComponent(publicId)}`,
      body,
    );
  }

  /** `POST /api/v1/buildings/{publicId}/archive` — `204`. */
  archiveBuilding(publicId: string, body: ArchiveRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/buildings/${encodeURIComponent(publicId)}/archive`,
      body,
    );
  }

  /** `POST /api/v1/buildings/{publicId}/restore` — `204`. */
  restoreBuilding(publicId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/buildings/${encodeURIComponent(publicId)}/restore`,
      {},
    );
  }

  // --- Rooms (nested under a site) -------------------------------------

  /** `GET /api/v1/sites/{sitePublicId}/rooms` — filtre `building` inclus. */
  listRooms(
    sitePublicId: string,
    query: RoomListQuery,
  ): Observable<PageResponse<RoomResponse>> {
    return this.http.get<PageResponse<RoomResponse>>(
      `${this.base}/sites/${encodeURIComponent(sitePublicId)}/rooms`,
      { params: listParams(query, { building: query.building }) },
    );
  }

  /** `POST /api/v1/sites/{sitePublicId}/rooms` — `201 Created`. */
  createRoom(sitePublicId: string, body: CreateRoomRequest): Observable<RoomResponse> {
    return this.http.post<RoomResponse>(
      `${this.base}/sites/${encodeURIComponent(sitePublicId)}/rooms`,
      body,
    );
  }

  /** `GET /api/v1/rooms/{publicId}`. */
  getRoom(publicId: string): Observable<RoomResponse> {
    return this.http.get<RoomResponse>(`${this.base}/rooms/${encodeURIComponent(publicId)}`);
  }

  /** `PATCH /api/v1/rooms/{publicId}`. */
  updateRoom(publicId: string, body: UpdateRoomRequest): Observable<RoomResponse> {
    return this.http.patch<RoomResponse>(
      `${this.base}/rooms/${encodeURIComponent(publicId)}`,
      body,
    );
  }

  /** `POST /api/v1/rooms/{publicId}/archive` — `204`. */
  archiveRoom(publicId: string, body: ArchiveRequest): Observable<void> {
    return this.http.post<void>(
      `${this.base}/rooms/${encodeURIComponent(publicId)}/archive`,
      body,
    );
  }

  /** `POST /api/v1/rooms/{publicId}/restore` — `204`. */
  restoreRoom(publicId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/rooms/${encodeURIComponent(publicId)}/restore`,
      {},
    );
  }

  // --- Network ranges (SUPER_ADMIN only, server-enforced) ---------------

  /** `GET /api/v1/sites/{sitePublicId}/network-ranges`. */
  listNetworkRanges(
    sitePublicId: string,
    query: NetworkRangeListQuery,
  ): Observable<PageResponse<SiteNetworkRangeResponse>> {
    return this.http.get<PageResponse<SiteNetworkRangeResponse>>(
      `${this.base}/sites/${encodeURIComponent(sitePublicId)}/network-ranges`,
      {
        params: toHttpParams({
          active: query.active,
          sort: query.sort,
          page: query.page,
          size: query.size,
        }),
      },
    );
  }

  /** `POST /api/v1/sites/{sitePublicId}/network-ranges` — `201 Created`. */
  createNetworkRange(
    sitePublicId: string,
    body: CreateNetworkRangeRequest,
  ): Observable<SiteNetworkRangeResponse> {
    return this.http.post<SiteNetworkRangeResponse>(
      `${this.base}/sites/${encodeURIComponent(sitePublicId)}/network-ranges`,
      body,
    );
  }

  /** `POST /api/v1/network-ranges/{publicId}/activate` — `204`. */
  activateNetworkRange(publicId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/network-ranges/${encodeURIComponent(publicId)}/activate`,
      {},
    );
  }

  /** `POST /api/v1/network-ranges/{publicId}/deactivate` — `204`. */
  deactivateNetworkRange(publicId: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/network-ranges/${encodeURIComponent(publicId)}/deactivate`,
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
  query: OrganizationListQuery,
  extra: Record<string, string | null | undefined> = {},
): HttpParams {
  return toHttpParams({
    q: query.q,
    status: query.status,
    sort: query.sort,
    page: query.page,
    size: query.size,
    ...extra,
  });
}

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
