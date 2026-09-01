import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { interval, startWith } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { RoleContextService } from '../../core/auth/role-context.service';
import { roleLabel } from '../../core/models/role';
import { NAV_ITEMS, visibleNavItems } from '../../core/navigation/navigation';
import { DashboardApiService } from './dashboard-api.service';
import { DashboardResponse, shortInstant } from './dashboard.models';

type DashboardState =
  | { kind: 'loading' }
  | { kind: 'error' }
  | { kind: 'forbidden' }
  | { kind: 'ready'; data: DashboardResponse };

@Component({
  selector: 'app-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    MatCardModule,
    MatIconModule,
    MatListModule,
    MatButtonModule,
    MatProgressBarModule,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly auth = inject(AuthService);
  private readonly roleContext = inject(RoleContextService);
  private readonly dashboardApi = inject(DashboardApiService);

  protected readonly roleLabel = roleLabel;
  protected readonly shortInstant = shortInstant;

  protected readonly session = this.auth.session;
  protected readonly roles = this.auth.roles;

  // --- Tableau de bord par rôle (bloc G1-F) -----------------------
  protected readonly dashState = signal<DashboardState>({ kind: 'loading' });
  protected readonly dash = computed(() => {
    const s = this.dashState();
    return s.kind === 'ready' ? s.data : null;
  });
  /** Le contexte de rôle du front est ergonomique ; le serveur seul décide. */
  protected readonly canLinkSessions = computed(() =>
    this.roleContext
      .effectiveRoles()
      .some((r) =>
        ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER', 'TEACHER'].includes(r),
      ),
  );

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

  /**
   * Contexte transmis au serveur : uniquement quand un choix existe (compte
   * multi-rôles). Un compte mono-rôle n'a qu'un tableau de bord possible —
   * inutile de le préciser.
   */
  private readonly requestedContext = computed(() =>
    this.roleContext.hasChoice() ? this.roleContext.active() : null,
  );

  constructor() {
    // Recharge le tableau de bord au démarrage et à chaque changement de
    // contexte de rôle actif (EF-AUTH-003) : le serveur renvoie la carte du
    // rôle demandé, après l'avoir vérifié contre le JWT (403 si non détenu).
    effect(() => {
      const context = this.requestedContext();
      this.loadDashboard(context);
    });
  }

  protected loadDashboard(context = this.requestedContext()): void {
    this.dashState.set({ kind: 'loading' });
    this.dashboardApi.getDashboard(context).subscribe({
      next: (data) => this.dashState.set({ kind: 'ready', data }),
      error: (error: unknown) => {
        const status =
          typeof error === 'object' && error !== null && 'status' in error
            ? (error as { status: number }).status
            : 0;
        this.dashState.set({ kind: status === 403 ? 'forbidden' : 'error' });
      },
    });
  }

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
