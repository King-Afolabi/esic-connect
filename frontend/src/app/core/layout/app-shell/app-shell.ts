import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { AuthService } from '../../auth/auth.service';
import { roleLabel } from '../../models/role';
import { NAV_ITEMS, visibleNavItems } from '../../navigation/navigation';

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
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
  ],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShell {
  private readonly auth = inject(AuthService);
  private readonly breakpointObserver = inject(BreakpointObserver);

  protected readonly roleLabel = roleLabel;

  protected readonly email = this.auth.currentUserEmail;
  protected readonly roles = this.auth.roles;
  protected readonly navItems = computed(() => visibleNavItems(NAV_ITEMS, this.auth.roles()));

  protected readonly isHandset = toSignal(
    this.breakpointObserver.observe(Breakpoints.Handset).pipe(map((result) => result.matches)),
    { initialValue: false },
  );

  protected logout(): void {
    this.auth.logout();
  }
}
