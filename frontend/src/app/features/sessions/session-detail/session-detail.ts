import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, interval } from 'rxjs';

import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { QrDisplay } from '../shared/qr-display/qr-display';
import { SessionsApiService } from '../sessions-api.service';
import { toSessionError } from '../session-errors';
import {
  AttendanceTokenResponse,
  CourseSessionResponse,
  SESSION_MANAGE_ROLES,
  SESSION_READ_ROLES,
  SessionAttendanceResponse,
  attendanceSourceLabel,
  classCodes,
  formatInstantUtc,
  holdsAnySessionRole,
  sessionStatusLabel,
  teacherName,
} from '../sessions.models';

export { SESSION_MANAGE_ROLES } from '../sessions.models';

/** Rafraîchissement automatique modéré des présences pendant qu'une séance est ouverte. */
const ATTENDANCE_POLL_MS = 15_000;
/** Marge avant expiration pour renouveler le jeton d'émargement. */
const TOKEN_RENEW_MARGIN_MS = 3_000;

type DetailState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'not-found' }
  | { kind: 'forbidden' }
  | { kind: 'ready'; session: CourseSessionResponse };

type AttendanceState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; data: SessionAttendanceResponse };

/**
 * Fiche d'une séance : faits, cycle de vie (ouverture / fermeture avec
 * confirmation en ligne), affichage du QR d'émargement (jeton renouvelé
 * avant expiration) et suivi des présences.
 *
 * Le jeton (`token` / `shortCode`) reste **en mémoire du composant** : il
 * n'est jamais journalisé, ni stocké, ni placé dans une URL, ni affiché
 * en texte brut (le QR l'encode ; le code court est un code court, pas le
 * jeton). Le renouvellement et le rafraîchissement sont arrêtés à la
 * destruction, à la fermeture de la séance, à la perte d'autorisation et
 * au changement de contexte de rôle. Spring Security reste l'autorité.
 */
@Component({
  selector: 'app-session-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    QrDisplay,
  ],
  templateUrl: './session-detail.html',
  styleUrl: './session-detail.scss',
})
export class SessionDetail {
  private readonly api = inject(SessionsApiService);
  private readonly roleContext = inject(RoleContextService);
  private readonly route = inject(ActivatedRoute);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly publicId = this.route.snapshot.paramMap.get('publicId') ?? '';

  protected readonly statusLabel = sessionStatusLabel;
  protected readonly sourceLabel = attendanceSourceLabel;
  protected readonly formatInstantUtc = formatInstantUtc;
  protected readonly classCodes = classCodes;
  protected readonly teacherName = teacherName;
  protected readonly rosterColumns = ['student', 'number', 'recordedAt', 'source'] as const;

  protected readonly state = signal<DetailState>({ kind: 'loading' });
  protected readonly attendance = signal<AttendanceState>({ kind: 'idle' });
  protected readonly pendingAction = signal<'open' | 'close' | null>(null);
  protected readonly submitting = signal(false);
  protected readonly actionError = signal<string | null>(null);

  /** Jeton d'émargement courant (mémoire seule). */
  protected readonly attendanceToken = signal<AttendanceTokenResponse | null>(null);
  protected readonly tokenError = signal<string | null>(null);
  protected readonly tokenLoading = signal(false);

  private renewHandle: ReturnType<typeof setTimeout> | null = null;
  private pollSubscribed = false;

  protected readonly session = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.session : null;
  });
  protected readonly errorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'error' ? current.message : null;
  });
  protected readonly isOpen = computed(() => this.session()?.status === 'OPEN');
  protected readonly isPlanned = computed(() => this.session()?.status === 'PLANNED');
  protected readonly isClosed = computed(() => this.session()?.status === 'CLOSED');

  /** Rôles de gestion pris dans le contexte actif (jamais un élargissement du JWT). */
  protected readonly canManage = computed(() =>
    holdsAnySessionRole(this.roleContext.effectiveRoles(), SESSION_MANAGE_ROLES),
  );
  /** Le contexte actif permet-il encore de lire la fiche ? Pilote le polling. */
  protected readonly canRead = computed(() =>
    holdsAnySessionRole(this.roleContext.effectiveRoles(), SESSION_READ_ROLES),
  );
  /** Le QR n'est proposé que si la séance est ouverte et l'appelant gestionnaire. */
  protected readonly canShowQr = computed(() => this.isOpen() && this.canManage());

  protected readonly attendanceRows = computed(() => {
    const current = this.attendance();
    return current.kind === 'ready' ? current.data.records : [];
  });
  protected readonly attendanceSummary = computed(() => {
    const current = this.attendance();
    return current.kind === 'ready'
      ? { present: current.data.presentCount, expected: current.data.expectedCount }
      : null;
  });
  protected readonly attendanceError = computed(() => {
    const current = this.attendance();
    return current.kind === 'error' ? current.message : null;
  });

  constructor() {
    this.load();

    // Arrête tout dès que la séance n'est plus ouverte ou que le contexte
    // de rôle retire le droit de gestion.
    effect(() => {
      if (!this.canShowQr()) {
        this.stopTokenRenewal();
        this.attendanceToken.set(null);
      }
    });

    this.destroyRef.onDestroy(() => this.stopTokenRenewal());
  }

  protected retry(): void {
    this.load();
  }

  protected startOpen(): void {
    this.actionError.set(null);
    this.pendingAction.set('open');
  }

  protected startClose(): void {
    this.actionError.set(null);
    this.pendingAction.set('close');
  }

  protected cancelAction(): void {
    this.pendingAction.set(null);
    this.actionError.set(null);
  }

  protected confirmOpen(): void {
    this.runLifecycle(() => this.api.openSession(this.publicId), 'Séance ouverte : l’émargement est disponible.');
  }

  protected confirmClose(): void {
    this.stopTokenRenewal();
    this.attendanceToken.set(null);
    this.runLifecycle(
      () => this.api.closeSession(this.publicId),
      'Séance clôturée : les jetons d’émargement sont invalidés.',
    );
  }

  /** Émet ou renouvelle le jeton d'émargement. */
  protected refreshToken(): void {
    if (!this.canShowQr()) {
      return;
    }
    this.tokenLoading.set(true);
    this.tokenError.set(null);
    this.api.issueAttendanceToken(this.publicId).subscribe({
      next: (token) => {
        this.tokenLoading.set(false);
        // Réponse d'une émission déjà en vol : ignorée si, entre-temps,
        // la séance a été fermée ou le contexte de rôle a retiré le droit
        // de gestion. On ne programme alors aucun renouvellement.
        if (!this.canShowQr() || token.sessionPublicId !== this.publicId) {
          return;
        }
        this.attendanceToken.set(token);
        this.scheduleRenewal(token.ttlSeconds);
      },
      error: (error: unknown) => {
        this.tokenLoading.set(false);
        this.stopTokenRenewal();
        this.attendanceToken.set(null);
        if (!this.canShowQr()) {
          return;
        }
        this.tokenError.set(toSessionError(error).message);
      },
    });
  }

  protected refreshAttendance(): void {
    this.attendance.set({ kind: 'loading' });
    this.api.getSessionAttendance(this.publicId).subscribe({
      next: (data) => this.attendance.set({ kind: 'ready', data }),
      error: (error: unknown) =>
        this.attendance.set({ kind: 'error', message: toSessionError(error).message }),
    });
  }

  // ------------------------------------------------------------------

  private runLifecycle(call: () => Observable<void>, successMessage: string): void {
    if (this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.actionError.set(null);
    call().subscribe({
      next: () => {
        this.submitting.set(false);
        this.pendingAction.set(null);
        this.notifications.info(successMessage);
        this.load();
        this.refreshAttendance();
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.actionError.set(toSessionError(error).message);
      },
    });
  }

  private scheduleRenewal(ttlSeconds: number): void {
    this.stopTokenRenewal();
    const delay = Math.max(TOKEN_RENEW_MARGIN_MS, ttlSeconds * 1000 - TOKEN_RENEW_MARGIN_MS);
    this.renewHandle = setTimeout(() => {
      this.renewHandle = null;
      if (this.canShowQr()) {
        this.refreshToken();
      }
    }, delay);
  }

  private stopTokenRenewal(): void {
    if (this.renewHandle !== null) {
      clearTimeout(this.renewHandle);
      this.renewHandle = null;
    }
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    this.api.getSession(this.publicId).subscribe({
      next: (session) => {
        this.state.set({ kind: 'ready', session });
        if (this.attendance().kind === 'idle') {
          this.refreshAttendance();
        }
        this.maybeStartPolling();
      },
      error: (error: unknown) => {
        const view = toSessionError(error);
        if (view.notFound) {
          this.state.set({ kind: 'not-found' });
          return;
        }
        this.state.set(
          view.forbidden ? { kind: 'forbidden' } : { kind: 'error', message: view.message },
        );
      },
    });
  }

  /**
   * Rafraîchissement automatique modéré des présences — uniquement tant
   * que le composant vit ; l'appel est ignoré si la séance n'est pas
   * ouverte ou si le contexte de rôle actif ne permet plus la lecture de
   * la page. Nettoyé par {@link takeUntilDestroyed}.
   */
  private maybeStartPolling(): void {
    if (this.pollSubscribed) {
      return;
    }
    this.pollSubscribed = true;
    interval(ATTENDANCE_POLL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.canRead() && this.isOpen() && this.attendance().kind !== 'loading') {
          this.api.getSessionAttendance(this.publicId).subscribe({
            next: (data) => this.attendance.set({ kind: 'ready', data }),
            error: () => {
              /* un échec transitoire de polling n'écrase pas l'affichage courant */
            },
          });
        }
      });
  }
}
