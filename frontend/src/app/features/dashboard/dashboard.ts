import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { RouterLink } from '@angular/router';
import { interval, startWith } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { RoleContextService } from '../../core/auth/role-context.service';
import { roleLabel } from '../../core/models/role';
import { NAV_ITEMS, visibleNavItems } from '../../core/navigation/navigation';

@Component({
  selector: 'app-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MatCardModule, MatIconModule, MatListModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly auth = inject(AuthService);
  private readonly roleContext = inject(RoleContextService);

  protected readonly roleLabel = roleLabel;

  protected readonly session = this.auth.session;
  protected readonly roles = this.auth.roles;

  /** Contexte d'utilisation actif (docs/02 §6.1) — affichage seul. */
  protected readonly activeContextLabel = this.roleContext.activeLabel;
  protected readonly hasContextChoice = this.roleContext.hasChoice;

  /** Ré-évalue l'échéance affichée chaque minute. */
  private readonly tick = toSignal(interval(60_000).pipe(startWith(0)), { initialValue: 0 });

  protected readonly quickLinks = computed(() =>
    visibleNavItems(NAV_ITEMS, this.roleContext.effectiveRoles()).filter(
      // Le tableau de bord lui-même et les Notifications (toujours
      // accessibles via la cloche de l'en-tête) ne sont pas des « accès
      // rapides » de contenu métier.
      (item) => item.path !== '/dashboard' && item.path !== '/notifications',
    ),
  );

  protected readonly expiresLabel = computed(() => {
    this.tick();
    const session = this.session();
    if (!session) {
      return null;
    }
    const remainingMs = session.expiresAt - Date.now();
    if (remainingMs <= 0) {
      return 'expirée';
    }
    const minutes = Math.floor(remainingMs / 60_000);
    const time = new Date(session.expiresAt).toLocaleTimeString('fr-FR', {
      hour: '2-digit',
      minute: '2-digit',
    });
    return minutes >= 1 ? `dans ${minutes} min (à ${time})` : `dans moins d'une minute (à ${time})`;
  });
}
