import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  EmailDeliveryPage,
  InvitationPage,
  PendingInvitationExportFormat,
  PendingInvitationRow,
} from './invitations.models';

/** Suivi des invitations et de la délivrabilité (EF-USER-007, EF-USER-008). */
@Injectable({ providedIn: 'root' })
export class InvitationsApiService {
  private readonly http = inject(HttpClient);
  private readonly invitations = `${environment.apiBaseUrl}/v1/account-invitations`;
  private readonly deliveries = `${environment.apiBaseUrl}/v1/email-deliveries`;
  private readonly pendingReport = `${environment.apiBaseUrl}/v1/reports/pending-invitations`;

  /** Rapport des invitations non activées (EF-REP-010). */
  listPendingInvitations(): Observable<PendingInvitationRow[]> {
    return this.http.get<PendingInvitationRow[]>(this.pendingReport);
  }

  exportPendingInvitations(
    format: PendingInvitationExportFormat,
  ): Observable<import('@angular/common/http').HttpResponse<Blob>> {
    return this.http.get(`${this.pendingReport}/export`, {
      params: new HttpParams().set('format', format),
      responseType: 'blob',
      observe: 'response',
    });
  }

  list(status?: string, page = 0, size = 20): Observable<InvitationPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<InvitationPage>(this.invitations, { params });
  }

  /**
   * Réémet une invitation : l'ancien jeton est révoqué côté serveur, un
   * nouveau part. C'est la seule réponse correcte après correction d'une
   * adresse.
   */
  resend(id: string): Observable<{ publicId: string; expiresAt: string }> {
    return this.http.post<{ publicId: string; expiresAt: string }>(
      `${this.invitations}/${id}/resend`,
      {},
    );
  }

  listDeliveries(
    internalStatus?: string,
    messageType?: string,
    page = 0,
    size = 20,
  ): Observable<EmailDeliveryPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (internalStatus) {
      params = params.set('internalStatus', internalStatus);
    }
    if (messageType) {
      params = params.set('messageType', messageType);
    }
    return this.http.get<EmailDeliveryPage>(this.deliveries, { params });
  }
}
