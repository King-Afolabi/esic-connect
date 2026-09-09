import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ClaimPage, ClaimSummary, ClaimThread, CreateClaimRequest } from './claims.models';

/**
 * Accès HTTP aux réclamations (docs/02 §20). Ne consomme que des routes
 * réellement exposées par le module `claim`.
 *
 * <p>Aucun identifiant d'auteur n'est transmis : le serveur résout
 * l'appelant depuis le JWT et décide seul de ce qu'il a le droit de voir.
 * Un fil hors périmètre répond `404`, jamais `403` — l'existence d'une
 * réclamation d'autrui est elle-même une information à protéger.
 */
@Injectable({ providedIn: 'root' })
export class ClaimsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1/claims`;

  /** `GET /api/v1/claims`. */
  list(query: {
    audience?: string | null;
    page?: number;
    size?: number;
    sort?: string | null;
  }): Observable<ClaimPage> {
    let params = new HttpParams();
    if (query.audience) {
      params = params.set('audience', query.audience);
    }
    if (query.page !== undefined) {
      params = params.set('page', String(query.page));
    }
    if (query.size !== undefined) {
      params = params.set('size', String(query.size));
    }
    if (query.sort) {
      params = params.set('sort', query.sort);
    }
    return this.http.get<ClaimPage>(this.base, { params });
  }

  /** `POST /api/v1/claims` → 201. */
  create(body: CreateClaimRequest): Observable<ClaimSummary> {
    return this.http.post<ClaimSummary>(this.base, body);
  }

  /** `GET /api/v1/claims/{id}`. */
  thread(publicId: string): Observable<ClaimThread> {
    return this.http.get<ClaimThread>(`${this.base}/${encodeURIComponent(publicId)}`);
  }

  /** `POST /api/v1/claims/{id}/messages`. */
  postMessage(publicId: string, body: string): Observable<ClaimThread> {
    return this.http.post<ClaimThread>(`${this.base}/${encodeURIComponent(publicId)}/messages`, {
      body,
    });
  }

  /** `POST /api/v1/claims/{id}/transfer`. */
  transfer(publicId: string, audience: string, motive: string): Observable<ClaimThread> {
    return this.http.post<ClaimThread>(`${this.base}/${encodeURIComponent(publicId)}/transfer`, {
      audience,
      motive,
    });
  }

  /** `POST /api/v1/claims/{id}/decision`. */
  decide(publicId: string, status: string, motive: string): Observable<ClaimThread> {
    return this.http.post<ClaimThread>(`${this.base}/${encodeURIComponent(publicId)}/decision`, {
      status,
      motive,
    });
  }

  /** `POST /api/v1/claims/{id}/reopen`. */
  reopen(publicId: string, motive: string): Observable<ClaimThread> {
    return this.http.post<ClaimThread>(`${this.base}/${encodeURIComponent(publicId)}/reopen`, {
      motive,
    });
  }
}
