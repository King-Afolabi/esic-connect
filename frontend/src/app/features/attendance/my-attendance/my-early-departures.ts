import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { NotificationService } from '../../../core/notifications/notification.service';
import { AttendanceApiService } from '../attendance-api.service';
import { toAttendanceError } from '../attendance-errors';
import {
  EarlyDeparture,
  earlyDepartureEffectLabel,
  earlyDepartureOpinionLabel,
  earlyDepartureStatusLabel,
} from '../attendance.models';
import { formatInstantUtc } from '../../sessions/sessions.models';

type ListState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; rows: EarlyDeparture[] };

/**
 * Départ anticipé, côté apprenant (EF-ATT-013 ; docs/02 §16.13).
 *
 * <p>L'apprenant <strong>signale</strong> — il n'autorise rien. L'écran le
 * dit explicitement : tant que personne n'a tranché, l'effet affiché est
 * « à confirmer ». Laisser croire qu'un signalement excuse la fin de
 * journée serait trompeur, et la conséquence tomberait sur son relevé
 * d'assiduité.
 */
@Component({
  selector: 'app-my-early-departures',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './my-early-departures.html',
  styleUrl: './my-attendance-list.scss',
})
export class MyEarlyDepartures {
  private readonly api = inject(AttendanceApiService);
  private readonly fb = inject(FormBuilder);
  private readonly notifications = inject(NotificationService);

  protected readonly statusLabel = earlyDepartureStatusLabel;
  protected readonly effectLabel = earlyDepartureEffectLabel;
  protected readonly opinionLabel = earlyDepartureOpinionLabel;
  protected readonly formatInstantUtc = formatInstantUtc;
  protected readonly columns = ['session', 'departureAt', 'status', 'effect', 'decision'] as const;

  protected readonly state = signal<ListState>({ kind: 'loading' });
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    sessionPublicId: ['', [Validators.required, Validators.maxLength(64)]],
    departureAt: ['', Validators.required],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });

  protected readonly hasRows = computed(() => {
    const current = this.state();
    return current.kind === 'ready' && current.rows.length > 0;
  });

  constructor() {
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  protected declare(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    this.submitting.set(true);
    this.formError.set(null);
    this.api
      .declareEarlyDeparture({
        sessionPublicId: raw.sessionPublicId.trim(),
        // `datetime-local` est une heure locale sans fuseau ; le serveur
        // attend un instant. La conversion est faite ici, une fois.
        departureAt: new Date(raw.departureAt).toISOString(),
        reason: raw.reason.trim(),
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.form.reset({ sessionPublicId: '', departureAt: '', reason: '' });
          this.notifications.info(
            'Départ signalé. Il reste à confirmer par votre formateur ou votre responsable.',
          );
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
    this.api.listMyEarlyDepartures().subscribe({
      next: (rows) => this.state.set({ kind: 'ready', rows }),
      error: (error: unknown) => {
        const view = toAttendanceError(error);
        this.state.set(
          view.forbidden ? { kind: 'forbidden' } : { kind: 'error', message: view.message },
        );
      },
    });
  }
}
