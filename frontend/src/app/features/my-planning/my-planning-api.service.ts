import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { MyPlanning } from './my-planning.models';

/**
 * Accès à `GET /api/v1/me/planning` (`com.esic.connect.myplanning`).
 * Une seule route ; le serveur décide seul du périmètre (formateur ou
 * apprenant) à partir du rôle effectif du JWT — aucun identifiant n'est
 * transmis.
 */
@Injectable({ providedIn: 'root' })
export class MyPlanningApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1/me/planning`;

  /** @param from,to bornes ISO facultatives (`null` = fenêtre ouverte). */
  get(from: string | null, to: string | null): Observable<MyPlanning> {
    let params = new HttpParams();
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<MyPlanning>(this.base, { params });
  }
}
