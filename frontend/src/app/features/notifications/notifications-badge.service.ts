import { Injectable, inject, signal } from '@angular/core';

import { NotificationsApiService } from './notifications-api.service';

/**
 * Compteur de notifications non lues partagé entre la cloche de l'en-tête
 * et le centre de notifications (G1-D). Un seul point de rafraîchissement
 * : la cloche le sonde à intervalle raisonnable et le centre le
 * réactualise après chaque marquage. Aucune donnée sensible n'y transite.
 */
@Injectable({ providedIn: 'root' })
export class NotificationsBadgeService {
  private readonly api = inject(NotificationsApiService);

  private readonly _unread = signal(0);
  readonly unread = this._unread.asReadonly();

  /** Recharge le compteur depuis le serveur. Silencieux en cas d'échec transitoire. */
  refresh(): void {
    this.api.unreadCount().subscribe({
      next: (result) => this._unread.set(Math.max(0, result.unread ?? 0)),
      error: () => {
        /* un échec de sondage n'écrase pas la dernière valeur connue */
      },
    });
  }
}
