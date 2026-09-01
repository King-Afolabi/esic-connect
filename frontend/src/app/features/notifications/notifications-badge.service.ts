import { Injectable, inject, signal } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';
import { NotificationsApiService } from './notifications-api.service';

/**
 * Compteur de notifications non lues partagé entre la cloche de l'en-tête
 * et le centre de notifications (G1-D). Un seul point de rafraîchissement
 * : la cloche le sonde à intervalle raisonnable et le centre le
 * réactualise après chaque marquage. Aucune donnée sensible n'y transite.
 *
 * <p><strong>G1-D.1 — sûreté du sondage.</strong>
 * <ul>
 *   <li>aucun appel réseau si la session n'est pas authentifiée (session
 *       expirée / déconnexion) : le compteur est remis à zéro ;</li>
 *   <li>un seul sondage à la fois (garde « en vol ») : un
 *       {@code NavigationEnd} concomitant de l'intervalle ne double pas
 *       la requête ;</li>
 *   <li>un échec transitoire n'écrase pas la dernière valeur connue.</li>
 * </ul>
 */
@Injectable({ providedIn: 'root' })
export class NotificationsBadgeService {
  private readonly api = inject(NotificationsApiService);
  private readonly auth = inject(AuthService);

  private readonly _unread = signal(0);
  readonly unread = this._unread.asReadonly();

  private inFlight = false;

  /**
   * Recharge le compteur depuis le serveur. Sans session authentifiée :
   * remet le compteur à zéro et n'émet aucune requête. Silencieux en cas
   * d'échec transitoire (la dernière valeur connue est conservée).
   */
  refresh(): void {
    if (!this.auth.isAuthenticated()) {
      this._unread.set(0);
      return;
    }
    if (this.inFlight) {
      return;
    }
    this.inFlight = true;
    this.api.unreadCount().subscribe({
      next: (result) => {
        this.inFlight = false;
        this._unread.set(Math.max(0, result.unread ?? 0));
      },
      error: () => {
        this.inFlight = false;
        /* un échec de sondage n'écrase pas la dernière valeur connue */
      },
    });
  }

  /** Remet le compteur à zéro (déconnexion / changement de compte). */
  reset(): void {
    this._unread.set(0);
  }
}
