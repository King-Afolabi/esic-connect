import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { SkipLink } from '../../a11y/skip-link';
import { AuthService } from '../../auth/auth.service';
import { RoleContextService } from '../../auth/role-context.service';
import { roleLabel } from '../../models/role';
import { NAV_ITEMS, visibleNavItems } from '../../navigation/navigation';
import { ConnectivityService } from '../../pwa/connectivity.service';
import { OfflineQueueService } from '../../pwa/offline-queue.service';
import { PwaService } from '../../pwa/pwa.service';
import { NotificationBell } from '../../../features/notifications/notification-bell/notification-bell';
import { RoleContextMenu } from '../role-context-menu/role-context-menu';

/**
 * Coquille applicative authentifiée : barre supérieure, navigation
 * latérale filtrée par rôle et zone de contenu principale.
 *
 * Repères sémantiques (`<nav>`, `<main>`), lien d'évitement et
 * `aria-current` pour l'accessibilité (docs/02-cahier-des-charges.md §48).
 */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    RoleContextMenu,
    NotificationBell,
    SkipLink,
  ],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShell {
  private readonly auth = inject(AuthService);
  private readonly roleContext = inject(RoleContextService);
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly pwa = inject(PwaService);
  private readonly queue = inject(OfflineQueueService);

  protected readonly connectivity = inject(ConnectivityService);
  /** Nombre d'actions faites hors ligne, en attente de confirmation (AC-031). */
  protected readonly pendingActions = this.queue.pendingCount;
  protected readonly installable = this.pwa.installable;

  protected readonly roleLabel = roleLabel;

  protected readonly email = this.auth.currentUserEmail;
  protected readonly roles = this.auth.roles;
  // Navigation filtrée selon le contexte d'utilisation actif (docs/02 §6.1) :
  // le rôle choisi restreint les entrées visibles, sans jamais élargir les
  // droits — l'autorisation reste côté Spring Security.
  protected readonly navItems = computed(() =>
    visibleNavItems(NAV_ITEMS, this.roleContext.effectiveRoles()),
  );

  protected readonly isHandset = toSignal(
    this.breakpointObserver.observe(Breakpoints.Handset).pipe(map((result) => result.matches)),
    { initialValue: false },
  );

  protected install(): void {
    void this.pwa.promptInstall();
  }

  /**
   * Déconnexion. La file d'actions et le cache de données de l'appareil
   * sont vidés : sur un poste partagé, rien de la session précédente ne
   * doit rester consultable hors ligne.
   */
  protected logout(): void {
    this.queue.clear();
    this.pwa.clearCachedData();
    this.auth.logout();
  }
}
