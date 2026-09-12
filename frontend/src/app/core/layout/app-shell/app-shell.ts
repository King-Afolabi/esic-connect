import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  NavigationCancel,
  NavigationEnd,
  NavigationSkipped,
  Router,
  RouterLink,
  RouterOutlet,
} from '@angular/router';
import { filter, map, startWith } from 'rxjs';

import { SkipLink } from '../../a11y/skip-link';
import { AuthService } from '../../auth/auth.service';
import { RoleContextService } from '../../auth/role-context.service';
import { SessionActivityService } from '../../auth/session-activity.service';
import { activeNavPath, NAV_ITEMS, visibleNavItems } from '../../navigation/navigation';
import { ConnectivityService } from '../../pwa/connectivity.service';
import { OfflineQueueService } from '../../pwa/offline-queue.service';
import { PwaService } from '../../pwa/pwa.service';
import { NotificationBell } from '../../../features/notifications/notification-bell/notification-bell';
import { ProfileMenu } from '../profile-menu/profile-menu';
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
    ProfileMenu,
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

    // Quelle que soit la manière dont on arrive sur une page (clic dans le
    // menu, lien externe, rechargement sur une URL profonde, navigation
    // arrière), l'entrée active du menu doit rester visible : on la fait
    // défiler dans le rail desktop (liste parfois plus haute que l'écran)
    // et dans le panneau mobile (déjà ouvert ou qui le sera plus tard).
    effect(() => {
      this.activeNavPath();
      requestAnimationFrame(() => this.scrollActiveNavItemIntoView());
    });
  }

  ngOnDestroy(): void {
    this.sessionActivity.stop();
  }

  protected readonly connectivity = inject(ConnectivityService);
  /** Nombre d'actions faites hors ligne, en attente de confirmation (AC-031). */
  protected readonly pendingActions = this.queue.pendingCount;
  protected readonly installable = this.pwa.installable;

  // L'adresse et les rôles ne sont plus affichés à plat dans l'en-tête :
  // ils sont regroupés dans le panneau compact `app-profile-menu` (Lot §3).
  // Navigation filtrée selon le contexte d'utilisation actif (docs/02 §6.1) :
  // le rôle choisi restreint les entrées visibles, sans jamais élargir les
  // droits — l'autorisation reste côté Spring Security.
  protected readonly navItems = computed(() =>
    visibleNavItems(NAV_ITEMS, this.roleContext.effectiveRoles()),
  );

  /**
   * `path` de l'unique entrée de navigation active (Lot C). Recalculé à
   * chaque navigation qui se pose : une seule entrée porte `.active` et
   * `aria-current="page"`, y compris sur une route imbriquée ou une fiche
   * de détail hors menu.
   *
   * On lit `router.url` (toujours à jour) sur `NavigationEnd` **mais
   * aussi** `NavigationSkipped` / `NavigationCancel` : une vue de liste
   * qui réécrit ses filtres dans l'URL au chargement (Lot G) déclenche
   * une navigation « même URL » qui supplante et annule celle du rail,
   * sans émettre `NavigationEnd` — ce qui figeait l'entrée active sur la
   * page précédente en mode zoneless.
   */
  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter(
        (event) =>
          event instanceof NavigationEnd ||
          event instanceof NavigationSkipped ||
          event instanceof NavigationCancel,
      ),
      map(() => this.router.url),
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

  // -------------------------------------------------------------------
  // Position de défilement du menu latéral
  // -------------------------------------------------------------------
  // Règle générale : quand le menu est visible (rail desktop ou panneau
  // mobile), l'entrée active doit être visible, sans jamais dépendre de
  // l'historique de défilement de l'utilisateur. `scrollIntoView({block:
  // 'nearest'})` ne bouge rien si l'entrée est déjà dans le cadre — donc
  // aucun à-coup sur les navigations qui ne changent pas la position.
  //
  // Sur mobile (mode « over »), Angular Material ne détruit jamais le
  // contenu du `mat-sidenav` : il l'anime hors champ, sans jamais réduire
  // sa hauteur (voir `.shell__rail` en SCSS) — le défilement interne
  // survit donc naturellement à une fermeture/réouverture. Le seul cas
  // que le repositionnement actif ne couvre pas nativement est une
  // première ouverture sans entrée active déterminée : `lastNavScrollTop`
  // sert alors de filet, capturé à la fermeture.
  private readonly navScrollRef = viewChild<ElementRef<HTMLElement>>('navScroll');
  private lastNavScrollTop = 0;

  protected onSidenavOpenedChange(opened: boolean): void {
    const el = this.navScrollRef()?.nativeElement;
    if (!el) {
      return;
    }
    if (opened) {
      // Attendre que le panneau soit effectivement visible et mesurable
      // (fin d'animation) avant d'imposer une position de défilement.
      requestAnimationFrame(() => this.scrollActiveNavItemIntoView());
    } else {
      this.lastNavScrollTop = el.scrollTop;
    }
  }

  private scrollActiveNavItemIntoView(): void {
    const el = this.navScrollRef()?.nativeElement;
    if (!el) {
      return;
    }
    const active = el.querySelector<HTMLElement>('a.active');
    if (active) {
      active.scrollIntoView({ block: 'nearest' });
    } else {
      el.scrollTop = this.lastNavScrollTop;
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
