import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, interval } from 'rxjs';

import { NotificationsBadgeService } from '../notifications-badge.service';

const POLL_MS = 60_000;

/**
 * Cloche de l'en-tête (G1-D, DEC-G1-D-UI) : lien vers `/notifications`
 * avec un badge du nombre de non-lus. Le compteur est rafraîchi à
 * l'initialisation, à chaque navigation et à intervalle raisonnable
 * (60 s) — jamais de sondage agressif. Aucune autorisation côté front.
 */
@Component({
  selector: 'app-notification-bell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MatButtonModule, MatIconModule, MatBadgeModule],
  templateUrl: './notification-bell.html',
})
export class NotificationBell {
  private readonly badge = inject(NotificationsBadgeService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly unread = this.badge.unread;
  protected readonly badgeText = computed(() => {
    const n = this.unread();
    return n > 99 ? '99+' : String(n);
  });

  constructor() {
    this.badge.refresh();
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.badge.refresh());
    interval(POLL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.badge.refresh());
  }
}
