import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

/** Un réglage de canal pour une catégorie (EF-NOTIF-006). */
export interface NotificationPreference {
  readonly category: string;
  readonly channel: string;
  readonly enabled: boolean;
  /** Réglage non modifiable : centre de notifications, alertes de sécurité. */
  readonly locked: boolean;
}

export interface NotificationPreferenceList {
  readonly preferences: readonly NotificationPreference[];
}

export interface PushSubscriptionView {
  readonly publicId: string;
  readonly active: boolean;
  readonly createdAt: string;
  readonly lastUsedAt: string | null;
  readonly revokedAt: string | null;
}

export interface PushStatus {
  /**
   * `false` quand aucune clé VAPID n'est configurée : **aucune poussée
   * n'a lieu**, et l'interface doit le dire plutôt que d'afficher un
   * interrupteur sans effet.
   */
  readonly providerActive: boolean;
  readonly subscriptions: readonly PushSubscriptionView[];
}

/**
 * Préférences de notification et abonnements de poussée de l'appelant.
 * Aucun identifiant de destinataire ne transite : le serveur le dérive
 * du JWT.
 */
@Injectable({ providedIn: 'root' })
export class NotificationSettingsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1/me`;

  preferences(): Observable<NotificationPreferenceList> {
    return this.http.get<NotificationPreferenceList>(`${this.base}/notification-preferences`);
  }

  updatePreference(
    category: string,
    channel: string,
    enabled: boolean,
  ): Observable<NotificationPreferenceList> {
    return this.http.put<NotificationPreferenceList>(`${this.base}/notification-preferences`, {
      category,
      channel,
      enabled,
    });
  }

  pushStatus(): Observable<PushStatus> {
    return this.http.get<PushStatus>(`${this.base}/push/status`);
  }

  subscribePush(payload: {
    endpoint: string;
    p256dh: string;
    auth: string;
  }): Observable<PushSubscriptionView> {
    return this.http.post<PushSubscriptionView>(`${this.base}/push/subscriptions`, payload);
  }

  unsubscribePush(publicId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.base}/push/subscriptions/${encodeURIComponent(publicId)}`,
    );
  }
}
