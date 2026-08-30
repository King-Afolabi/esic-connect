import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { AttendanceApiService } from '../attendance-api.service';
import { toAttendanceError } from '../attendance-errors';
import {
  ATTENDANCE_MANAGE_ROLES,
  JustificationResponse,
  justificationCategoryLabel,
  justificationStatusLabel,
} from '../attendance.models';
import { attendanceStatusLabel, formatInstantUtc } from '../../sessions/sessions.models';

type State =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; rows: JustificationResponse[] };

/**
 * File des justificatifs à examiner (V10). L'examen (accepter / refuser
 * avec motif) est réservé à
 * `ADMIN`/`SUPER_ADMIN`/`SCHOOL_ADMINISTRATION`/`PEDAGOGICAL_MANAGER` ;
 * le périmètre est appliqué côté serveur. Confirmations en ligne. Aucun
 * stockage navigateur.
 */
@Component({
  selector: 'app-justification-queue',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
  ],
  templateUrl: './justification-queue.html',
  styleUrl: '../my-attendance/my-attendance-list.scss',
})
export class JustificationQueue {
  private readonly api = inject(AttendanceApiService);
  private readonly fb = inject(FormBuilder);
  private readonly roleContext = inject(RoleContextService);
  private readonly notifications = inject(NotificationService);

  protected readonly categoryLabel = justificationCategoryLabel;
  protected readonly justificationStatusLabel = justificationStatusLabel;
  protected readonly attendanceStatusLabel = attendanceStatusLabel;
  protected readonly formatInstantUtc = formatInstantUtc;
  protected readonly columns = ['student', 'session', 'category', 'status', 'actions'] as const;

  protected readonly state = signal<State>({ kind: 'loading' });
  protected readonly statusFilter = signal<string>('PENDING');

  protected readonly reviewId = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly reviewForm = this.fb.nonNullable.group({
    decision: ['ACCEPTED', Validators.required],
    decisionReason: ['', Validators.maxLength(500)],
  });

  /** Un contexte de rôle sans droit de gestion ferme le panneau d'examen. */
  protected readonly canReview = computed(() =>
    this.roleContext.effectiveRoles().some((r) => (ATTENDANCE_MANAGE_ROLES as readonly string[]).includes(r)),
  );

  protected readonly rows = computed(() => {
    const s = this.state();
    return s.kind === 'ready' ? s.rows : [];
  });
  protected readonly errorMessage = computed(() => {
    const s = this.state();
    return s.kind === 'error' ? s.message : null;
  });

  constructor() {
    this.load();
  }

  protected setFilter(status: string): void {
    this.statusFilter.set(status);
    this.load();
  }
  protected retry(): void {
    this.load();
  }

  protected startReview(row: JustificationResponse): void {
    if (!this.canReview()) {
      return;
    }
    this.reviewId.set(row.publicId);
    this.reviewForm.reset({ decision: 'ACCEPTED', decisionReason: '' });
    this.formError.set(null);
  }
  protected cancelReview(): void {
    this.reviewId.set(null);
    this.formError.set(null);
  }

  protected submitReview(): void {
    const id = this.reviewId();
    if (!id || this.reviewForm.invalid || this.submitting() || !this.canReview()) {
      this.reviewForm.markAllAsTouched();
      return;
    }
    const raw = this.reviewForm.getRawValue();
    if (raw.decision === 'REJECTED' && !raw.decisionReason.trim()) {
      this.formError.set('Un motif est obligatoire pour refuser un justificatif.');
      return;
    }
    this.submitting.set(true);
    this.formError.set(null);
    this.api
      .reviewJustification(id, {
        decision: raw.decision as 'ACCEPTED' | 'REJECTED',
        decisionReason: raw.decisionReason.trim() || null,
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.reviewId.set(null);
          this.notifications.info('Justificatif examiné.');
          this.load();
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.formError.set(toAttendanceError(error).message);
        },
      });
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    this.api.listJustificationsForReview(this.statusFilter() || null).subscribe({
      next: (rows) => this.state.set({ kind: 'ready', rows }),
      error: (error: unknown) => {
        const view = toAttendanceError(error);
        this.state.set(view.forbidden ? { kind: 'forbidden' } : { kind: 'error', message: view.message });
      },
    });
  }
}
