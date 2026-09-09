import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { GlobalSearchResponse } from './global-search.models';

/**
 * Accès HTTP à la recherche globale (EF-USER-009).
 *
 * Le périmètre n'est **jamais** transmis : le serveur le relit du
 * contexte de sécurité à chaque appel. Envoyer un filtre de périmètre
 * depuis le client reviendrait à lui laisser choisir ce qu'il a le droit
 * de voir.
 */
@Injectable({ providedIn: 'root' })
export class GlobalSearchApiService {
  private readonly http = inject(HttpClient);

  search(query: string): Observable<GlobalSearchResponse> {
    return this.http.get<GlobalSearchResponse>(`${environment.apiBaseUrl}/v1/search`, {
      params: new HttpParams().set('q', query),
    });
  }
}
