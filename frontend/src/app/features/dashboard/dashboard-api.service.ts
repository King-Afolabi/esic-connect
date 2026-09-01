import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { DashboardResponse } from './dashboard.models';

/**
 * Accès HTTP au tableau de bord par rôle (bloc G1-F). Une seule route ;
 * aucun paramètre : le serveur résout le rôle effectif et le périmètre à
 * partir du seul JWT.
 */
@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  private readonly http = inject(HttpClient);

  /** `GET /api/v1/me/dashboard`. */
  getDashboard(): Observable<DashboardResponse> {
    return this.http.get<DashboardResponse>(`${environment.apiBaseUrl}/v1/me/dashboard`);
  }
}
