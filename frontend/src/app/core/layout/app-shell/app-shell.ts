import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';

import { SkipLink } from '../../a11y/skip-link';
import { AuthService } from '../../auth/auth.service';
import { RoleContextService } from '../../auth/role-context.service';
import { SessionActivityService } from '../../auth/session-activity.service';
import { roleLabel } from '../../models/role';
import { activeNavPath, NAV_ITEMS, visibleNavItems } from '../../navigation/navigation';
import { ConnectivityService } from '../../pwa/connectivity.service';
import { OfflineQueueService } from '../../pwa/offline-queue.service';
import { PwaService } from '../../pwa/pwa.service';
import { NotificationBell } from '../../../features/notifications/notification-bell/notification-bell';
import { RoleContextMenu } from '../role-context-menu/role-context-menu';
import { SessionTimeoutWarning } from '../session-timeout-warning/session-timeout-warning';

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
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    RoleContextMenu,
    NotificationBell,
    SkipLink,
    SessionTimeoutWarning,
  ],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShell implements OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly roleContext = inject(RoleContextService);
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly pwa = inject(PwaService);
  private readonly queue = inject(OfflineQueueService);
  private readonly sessionActivity = inject(SessionActivityService);

  constructor() {
    // La coquille n'existe que sous session ouverte : c'est le bon moment
    // pour armer l'expiration glissante (Lot A), et sa destruction — au
    // retour vers `/login` — pour la désarmer.
    this.sessionActivity.start();
  }

  ngOnDestroy(): void {
    this.sessionActivity.stop();
  }

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

  /**
   * `path` de l'unique entrée de navigation active (Lot C). Recalculé à
   * chaque navigation terminée : une seule entrée porte `.active` et
   * `aria-current="page"`, y compris sur une route imbriquée ou une fiche
   * de détail hors menu.
   */
  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );
  protected readonly activeNavPath = computed(() =>
    activeNavPath(this.currentUrl(), this.navItems()),
  );

  protected readonly isHandset = toSignal(
    this.breakpointObserver.observe(Breakpoints.Handset).pipe(map((result) => result.matches)),
    { initialValue: false },
  );

  // Rail de navigation replié en bande d'icônes (desktop). Préférence par
  // appareil : `localStorage` est ici une commodité d'affichage, jamais un
  // jeton (RG-093). Lecture et écriture protégées — un navigateur peut
  // refuser l'accès (fenêtre privée, cookies bloqués).
  private readonly railKey = 'esic.rail.collapsed';
  protected readonly railCollapsed = signal(this.readRailPreference());

  protected toggleRail(): void {
    const next = !this.railCollapsed();
    this.railCollapsed.set(next);
    try {
      localStorage.setItem(this.railKey, next ? '1' : '0');
    } catch {
      // Préférence non persistée : sans effet sur la session en cours.
    }
  }

  private readRailPreference(): boolean {
    try {
      return localStorage.getItem(this.railKey) === '1';
    } catch {
      return false;
    }
  }

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
