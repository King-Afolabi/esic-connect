import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Role } from '../../core/models/role';
import { DashboardResponse } from './dashboard.models';

/**
 * Accès HTTP au tableau de bord par rôle (bloc G1-F).
 *
 * Le paramètre facultatif `context` transmet le rôle sous lequel un compte
 * multi-rôles souhaite voir son tableau de bord (EF-AUTH-003). Le serveur
 * le **vérifie** contre les autorités du JWT : un rôle non détenu renvoie
 * `403` — le contexte n'accorde aucun droit. Sans `context`, le serveur
 * retombe sur une priorité fixe déterministe.
 */
@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  private readonly http = inject(HttpClient);

  /** `GET /api/v1/me/dashboard` (avec `?context=<rôle>` si un contexte est actif). */
  getDashboard(context?: Role | null): Observable<DashboardResponse> {
    let params = new HttpParams();
    if (context) {
      params = params.set('context', context);
    }
    return this.http.get<DashboardResponse>(`${environment.apiBaseUrl}/v1/me/dashboard`, { params });
  }
}
