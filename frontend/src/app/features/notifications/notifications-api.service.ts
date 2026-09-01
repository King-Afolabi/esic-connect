import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { NotificationPage, NotificationStatus, UnreadCount } from './notifications.models';

/**
 * Accès HTTP au centre de notifications (`com.esic.connect.notification`).
 * Ne consomme que des routes réellement exposées ; aucun paramètre client
 * ne désigne un destinataire (le serveur le dérive du JWT).
 */
@Injectable({ providedIn: 'root' })
export class NotificationsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1/me/notifications`;

  /** `GET /api/v1/me/notifications`. */
  list(query: {
    status?: NotificationStatus | null;
    page?: number;
    size?: number;
  }): Observable<NotificationPage> {
    let params = new HttpParams();
    if (query.status) {
      params = params.set('status', query.status);
    }
    if (query.page !== undefined) {
      params = params.set('page', String(query.page));
    }
    if (query.size !== undefined) {
      params = params.set('size', String(query.size));
    }
    return this.http.get<NotificationPage>(this.base, { params });
  }

  /** `GET /api/v1/me/notifications/unread-count`. */
  unreadCount(): Observable<UnreadCount> {
    return this.http.get<UnreadCount>(`${this.base}/unread-count`);
  }

  /** `POST /api/v1/me/notifications/{publicId}/read` → 204 (idempotent). */
  markRead(publicId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${encodeURIComponent(publicId)}/read`, {});
  }

  /** `POST /api/v1/me/notifications/read-all` → 204. */
  markAllRead(): Observable<void> {
    return this.http.post<void>(`${this.base}/read-all`, {});
  }
}
