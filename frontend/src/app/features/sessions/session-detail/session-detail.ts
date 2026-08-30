import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, interval } from 'rxjs';

import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { QrDisplay } from '../shared/qr-display/qr-display';
import { SessionsApiService } from '../sessions-api.service';
import { toSessionError } from '../session-errors';
import {
  AttendanceCorrectionEntry,
  AttendanceTokenResponse,
  CheckpointAttendance,
  CheckpointView,
  CourseSessionResponse,
  SESSION_MANAGE_ROLES,
  SESSION_READ_ROLES,
  SessionAttendanceResponse,
  attendanceSourceLabel,
  attendanceStatusLabel,
  checkpointStatusLabel,
  checkpointTypeLabel,
  classCodes,
  correctionActionLabel,
  formatInstantUtc,
  holdsAnySessionRole,
  sessionStatusLabel,
  teacherName,
} from '../sessions.models';

export { SESSION_MANAGE_ROLES } from '../sessions.models';

const ATTENDANCE_POLL_MS = 15_000;
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
 * Fiche d'une séance (V10) : faits, cycle de vie, gestion des points de
 * contrôle (création, ouverture / fermeture / annulation en confirmation
 * en ligne), QR d'émargement ciblant le point de contrôle ouvert
 * sélectionné, tableau des présences par point de contrôle avec présence
 * manuelle, correction, annulation logique et historique.
 *
 * Le jeton (`token` / `shortCode`) reste en mémoire du composant : jamais
 * journalisé, stocké, placé dans une URL ni affiché en texte. Le
 * renouvellement et le polling s'arrêtent à la destruction, à la
 * fermeture de la séance, à la perte du droit dans le contexte de rôle
 * actif. Spring Security reste l'autorité.
 */
@Component({
  selector: 'app-session-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
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
  private readonly fb = inject(FormBuilder);

  private readonly publicId = this.route.snapshot.paramMap.get('publicId') ?? '';

  protected readonly statusLabel = sessionStatusLabel;
  protected readonly sourceLabel = attendanceSourceLabel;
  protected readonly attendanceStatusLabel = attendanceStatusLabel;
  protected readonly checkpointTypeLabel = checkpointTypeLabel;
  protected readonly checkpointStatusLabel = checkpointStatusLabel;
  protected readonly correctionActionLabel = correctionActionLabel;
  protected readonly formatInstantUtc = formatInstantUtc;
  protected readonly classCodes = classCodes;
  protected readonly teacherName = teacherName;
  protected readonly rosterColumns = ['student', 'number', 'status', 'recordedAt', 'actions'] as const;
  protected readonly checkpointColumns = ['label', 'type', 'status', 'required', 'actions'] as const;

  protected readonly state = signal<DetailState>({ kind: 'loading' });
  protected readonly attendance = signal<AttendanceState>({ kind: 'idle' });
  protected readonly pendingAction = signal<'open' | 'close' | null>(null);
  protected readonly submitting = signal(false);
  protected readonly actionError = signal<string | null>(null);

  protected readonly attendanceToken = signal<AttendanceTokenResponse | null>(null);
  protected readonly tokenError = signal<string | null>(null);
  protected readonly tokenLoading = signal(false);

  /** Point de contrôle sélectionné pour le QR et la saisie manuelle. */
  protected readonly selectedCheckpointId = signal<string | null>(null);

  /** Confirmations / formulaires en ligne. */
  protected readonly checkpointCancelId = signal<string | null>(null);
  protected readonly showCheckpointForm = signal(false);
  protected readonly showManualForm = signal(false);
  protected readonly correctRowId = signal<string | null>(null);
  protected readonly cancelRowId = signal<string | null>(null);
  protected readonly historyRowId = signal<string | null>(null);
  protected readonly historyEntries = signal<AttendanceCorrectionEntry[]>([]);
  protected readonly rowBusy = signal(false);
  protected readonly rowError = signal<string | null>(null);

  protected readonly checkpointForm = this.fb.nonNullable.group({
    label: ['', [Validators.required, Validators.maxLength(120)]],
    type: ['CUSTOM' as 'START' | 'END' | 'CUSTOM', Validators.required],
    required: [true],
  });
  protected readonly cancelForm = this.fb.nonNullable.group({
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });
  protected readonly manualForm = this.fb.nonNullable.group({
    enrollmentPublicId: ['', [Validators.required]],
    status: ['ABSENT' as 'PRESENT' | 'LATE' | 'ABSENT', Validators.required],
    lateMinutes: [null as number | null],
    comment: ['', [Validators.required, Validators.maxLength(500)]],
  });
  protected readonly correctForm = this.fb.nonNullable.group({
    status: ['' as '' | 'PRESENT' | 'LATE' | 'ABSENT'],
    lateMinutes: [null as number | null],
    comment: [''],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });

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

  protected readonly canManage = computed(() =>
    holdsAnySessionRole(this.roleContext.effectiveRoles(), SESSION_MANAGE_ROLES),
  );
  protected readonly canRead = computed(() =>
    holdsAnySessionRole(this.roleContext.effectiveRoles(), SESSION_READ_ROLES),
  );

  protected readonly checkpoints = computed<CheckpointView[]>(() => this.session()?.checkpoints ?? []);
  protected readonly openCheckpoints = computed(() =>
    this.checkpoints().filter((cp) => cp.status === 'OPEN'),
  );
  protected readonly selectedCheckpoint = computed(() => {
    const id = this.selectedCheckpointId();
    return this.openCheckpoints().find((cp) => cp.publicId === id) ?? this.openCheckpoints()[0] ?? null;
  });
  protected readonly canShowQr = computed(
    () => this.isOpen() && this.canManage() && !!this.selectedCheckpoint(),
  );

  protected readonly attendanceBlocks = computed<CheckpointAttendance[]>(() => {
    const current = this.attendance();
    return current.kind === 'ready' ? current.data.checkpoints : [];
  });
  protected readonly attendanceError = computed(() => {
    const current = this.attendance();
    return current.kind === 'error' ? current.message : null;
  });

  constructor() {
    this.load();

    effect(() => {
      if (!this.canShowQr()) {
        this.stopTokenRenewal();
        this.attendanceToken.set(null);
      }
    });
    // Ferme tout formulaire sensible dès que le contexte de rôle retire
    // le droit de gestion.
    effect(() => {
      if (!this.canManage()) {
        this.showCheckpointForm.set(false);
        this.showManualForm.set(false);
        this.checkpointCancelId.set(null);
        this.correctRowId.set(null);
        this.pendingAction.set(null);
      }
    });

    this.destroyRef.onDestroy(() => this.stopTokenRenewal());
  }

  protected retry(): void {
    this.load();
  }

  // --- Cycle de vie de la séance ---------------------------------

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
    this.runLifecycle(() => this.api.openSession(this.publicId), 'Séance ouverte.');
  }
  protected confirmClose(): void {
    this.stopTokenRenewal();
    this.attendanceToken.set(null);
    this.runLifecycle(
      () => this.api.closeSession(this.publicId),
      'Séance clôturée : les jetons d’émargement sont invalidés.',
    );
  }

  // --- Points de contrôle --------------------------------------

  protected toggleCheckpointForm(): void {
    this.showCheckpointForm.update((v) => !v);
    this.rowError.set(null);
    if (!this.showCheckpointForm()) {
      this.checkpointForm.reset({ label: '', type: 'CUSTOM', required: true });
    }
  }

  protected submitCheckpoint(): void {
    if (this.checkpointForm.invalid || this.rowBusy() || !this.canManage()) {
      this.checkpointForm.markAllAsTouched();
      return;
    }
    this.rowBusy.set(true);
    this.rowError.set(null);
    const raw = this.checkpointForm.getRawValue();
    this.api
      .createCheckpoint(this.publicId, {
        label: raw.label.trim(),
        type: raw.type,
        required: raw.required,
      })
      .subscribe({
        next: () => {
          this.rowBusy.set(false);
          this.showCheckpointForm.set(false);
          this.checkpointForm.reset({ label: '', type: 'CUSTOM', required: true });
          this.notifications.info('Point de contrôle créé.');
          this.load();
        },
        error: (error: unknown) => {
          this.rowBusy.set(false);
          this.rowError.set(toSessionError(error).message);
        },
      });
  }

  protected openCheckpoint(cp: CheckpointView): void {
    this.checkpointAction(() => this.api.openCheckpoint(this.publicId, cp.publicId), 'Point de contrôle ouvert.');
  }
  protected closeCheckpoint(cp: CheckpointView): void {
    this.checkpointAction(
      () => this.api.closeCheckpoint(this.publicId, cp.publicId),
      'Point de contrôle fermé.',
    );
  }
  protected startCancelCheckpoint(cp: CheckpointView): void {
    this.checkpointCancelId.set(cp.publicId);
    this.cancelForm.reset({ reason: '' });
    this.rowError.set(null);
  }
  protected confirmCancelCheckpoint(): void {
    const id = this.checkpointCancelId();
    if (!id || this.cancelForm.invalid || this.rowBusy()) {
      this.cancelForm.markAllAsTouched();
      return;
    }
    this.checkpointAction(
      () => this.api.cancelCheckpoint(this.publicId, id, { reason: this.cancelForm.getRawValue().reason.trim() }),
      'Point de contrôle annulé.',
      () => this.checkpointCancelId.set(null),
    );
  }
  protected abortCancelCheckpoint(): void {
    this.checkpointCancelId.set(null);
    this.rowError.set(null);
  }

  private checkpointAction(call: () => Observable<void>, message: string, after?: () => void): void {
    if (this.rowBusy() || !this.canManage()) {
      return;
    }
    this.rowBusy.set(true);
    this.rowError.set(null);
    call().subscribe({
      next: () => {
        this.rowBusy.set(false);
        after?.();
        this.notifications.info(message);
        this.load();
        this.refreshAttendance();
      },
      error: (error: unknown) => {
        this.rowBusy.set(false);
        this.rowError.set(toSessionError(error).message);
      },
    });
  }

  // --- QR ----------------------------------------------------------

  protected refreshToken(): void {
    const checkpoint = this.selectedCheckpoint();
    if (!this.canShowQr() || !checkpoint) {
      return;
    }
    this.tokenLoading.set(true);
    this.tokenError.set(null);
    this.api.issueCheckpointToken(this.publicId, checkpoint.publicId).subscribe({
      next: (token) => {
        this.tokenLoading.set(false);
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

  protected selectCheckpoint(id: string): void {
    this.selectedCheckpointId.set(id);
    this.stopTokenRenewal();
    this.attendanceToken.set(null);
  }

  protected refreshAttendance(): void {
    this.attendance.set({ kind: 'loading' });
    this.api.getSessionAttendance(this.publicId).subscribe({
      next: (data) => this.attendance.set({ kind: 'ready', data }),
      error: (error: unknown) =>
        this.attendance.set({ kind: 'error', message: toSessionError(error).message }),
    });
  }

  // --- Présence manuelle / correction / annulation / historique -

  protected toggleManualForm(): void {
    this.showManualForm.update((v) => !v);
    this.rowError.set(null);
    if (!this.showManualForm()) {
      this.manualForm.reset({ enrollmentPublicId: '', status: 'ABSENT', lateMinutes: null, comment: '' });
    }
  }

  protected submitManual(): void {
    const checkpoint = this.selectedCheckpoint() ?? this.checkpoints()[0] ?? null;
    if (this.manualForm.invalid || this.rowBusy() || !this.canManage() || !checkpoint) {
      this.manualForm.markAllAsTouched();
      return;
    }
    this.rowBusy.set(true);
    this.rowError.set(null);
    const raw = this.manualForm.getRawValue();
    this.api
      .recordManual(this.publicId, {
        enrollmentPublicId: raw.enrollmentPublicId.trim(),
        checkpointPublicId: checkpoint.publicId,
        status: raw.status,
        lateMinutes: raw.status === 'LATE' ? raw.lateMinutes : null,
        comment: raw.comment.trim(),
      })
      .subscribe({
        next: () => {
          this.rowBusy.set(false);
          this.showManualForm.set(false);
          this.manualForm.reset({ enrollmentPublicId: '', status: 'ABSENT', lateMinutes: null, comment: '' });
          this.notifications.info('Présence enregistrée.');
          this.refreshAttendance();
        },
        error: (error: unknown) => {
          this.rowBusy.set(false);
          this.rowError.set(toSessionError(error).message);
        },
      });
  }

  protected startCorrect(attendancePublicId: string): void {
    this.correctRowId.set(attendancePublicId);
    this.historyRowId.set(null);
    this.correctForm.reset({ status: '', lateMinutes: null, comment: '', reason: '' });
    this.rowError.set(null);
  }
  protected abortCorrect(): void {
    this.correctRowId.set(null);
    this.rowError.set(null);
  }
  protected submitCorrect(): void {
    const id = this.correctRowId();
    if (!id || this.correctForm.invalid || this.rowBusy()) {
      this.correctForm.markAllAsTouched();
      return;
    }
    this.rowBusy.set(true);
    this.rowError.set(null);
    const raw = this.correctForm.getRawValue();
    this.api
      .correctAttendance(this.publicId, id, {
        status: raw.status || null,
        lateMinutes: raw.lateMinutes ?? null,
        comment: raw.comment ? raw.comment.trim() : null,
        reason: raw.reason.trim(),
      })
      .subscribe({
        next: () => this.afterRowMutation('Présence corrigée.', () => this.correctRowId.set(null)),
        error: (error: unknown) => {
          this.rowBusy.set(false);
          this.rowError.set(toSessionError(error).message);
        },
      });
  }

  protected startCancelRow(attendancePublicId: string): void {
    this.cancelRowId.set(attendancePublicId);
    this.correctRowId.set(null);
    this.historyRowId.set(null);
    this.cancelForm.reset({ reason: '' });
    this.rowError.set(null);
  }
  protected abortCancelRow(): void {
    this.cancelRowId.set(null);
    this.rowError.set(null);
  }
  protected confirmCancelRow(): void {
    const id = this.cancelRowId();
    if (!id || this.cancelForm.invalid || this.rowBusy() || !this.canManage()) {
      this.cancelForm.markAllAsTouched();
      return;
    }
    this.rowBusy.set(true);
    this.rowError.set(null);
    this.api
      .cancelAttendance(this.publicId, id, { reason: this.cancelForm.getRawValue().reason.trim() })
      .subscribe({
        next: () => this.afterRowMutation('Présence annulée.', () => this.cancelRowId.set(null)),
        error: (error: unknown) => {
          this.rowBusy.set(false);
          this.rowError.set(toSessionError(error).message);
        },
      });
  }

  protected toggleHistory(attendancePublicId: string): void {
    if (this.historyRowId() === attendancePublicId) {
      this.historyRowId.set(null);
      return;
    }
    this.historyRowId.set(attendancePublicId);
    this.historyEntries.set([]);
    this.api.attendanceHistory(this.publicId, attendancePublicId).subscribe({
      next: (entries) => this.historyEntries.set(entries),
      error: (error: unknown) => {
        this.historyRowId.set(null);
        this.rowError.set(toSessionError(error).message);
      },
    });
  }

  private afterRowMutation(message: string, after?: () => void): void {
    this.rowBusy.set(false);
    after?.();
    this.notifications.info(message);
    this.refreshAttendance();
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
        const open = (session.checkpoints ?? []).filter((cp) => cp.status === 'OPEN');
        if (open.length && !open.some((cp) => cp.publicId === this.selectedCheckpointId())) {
          this.selectedCheckpointId.set(open[0].publicId);
        }
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
