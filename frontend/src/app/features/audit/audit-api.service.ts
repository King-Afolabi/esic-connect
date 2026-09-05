import { HttpClient, HttpParams } from '@angular/common/http';
import { HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuditExportFormat, AuditFacets, AuditPage, AuditQuery } from './audit.models';

/**
 * Accès HTTP à la piste d'audit (EF-AUD-002).
 *
 * **Lecture seule.** Aucune méthode d'écriture ni de suppression n'est
 * exposée ici, parce qu'aucune route ne l'est côté serveur : « l'audit
 * […] ne peut être modifié ni effacé » (docs/02 §23.4).
 */
@Injectable({ providedIn: 'root' })
export class AuditApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1/audit-events`;

  list(query: AuditQuery, page: number, size: number): Observable<AuditPage> {
    return this.http.get<AuditPage>(this.base, {
      params: toParams(query).set('page', page).set('size', size),
    });
  }

  facets(): Observable<AuditFacets> {
    return this.http.get<AuditFacets>(`${this.base}/facets`);
  }

  export(query: AuditQuery, format: AuditExportFormat): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/export`, {
      params: toParams(query).set('format', format),
      responseType: 'blob',
      observe: 'response',
    });
  }
}

function toParams(query: AuditQuery): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== null && value !== undefined && value !== '') {
      params = params.set(key, value);
    }
  }
  return params;
}
