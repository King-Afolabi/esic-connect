import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { AttendanceApiService } from '../attendance-api.service';
import { toAttendanceError } from '../attendance-errors';
import { SummaryResponse, percent } from '../attendance.models';

type State =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; summary: SummaryResponse };

/**
 * Cartes de synthèse d'assiduité (V10). Périmètre appliqué côté serveur ;
 * un `403` est rendu « accès refusé ». Aucun stockage navigateur, aucune
 * donnée sensible en URL de navigation.
 */
@Component({
  selector: 'app-attendance-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './attendance-summary.html',
  styleUrl: '../my-attendance/my-attendance-list.scss',
})
export class AttendanceSummary {
  private readonly api = inject(AttendanceApiService);
  private readonly fb = inject(FormBuilder);

  protected readonly percent = percent;
  protected readonly state = signal<State>({ kind: 'loading' });

  protected readonly filters = this.fb.nonNullable.group({
    from: [''],
    to: [''],
    classGroup: [''],
  });

  protected readonly summary = computed(() => {
    const s = this.state();
    return s.kind === 'ready' ? s.summary : null;
  });
  protected readonly errorMessage = computed(() => {
    const s = this.state();
    return s.kind === 'error' ? s.message : null;
  });

  constructor() {
    this.load();
  }

  protected apply(): void {
    this.load();
  }
  protected reset(): void {
    this.filters.reset({ from: '', to: '', classGroup: '' });
    this.load();
  }
  protected retry(): void {
    this.load();
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    const raw = this.filters.getRawValue();
    this.api
      .summary({
        from: isoStart(raw.from),
        to: isoStart(raw.to),
        classGroup: raw.classGroup.trim() || null,
      })
      .subscribe({
        next: (summary) => this.state.set({ kind: 'ready', summary }),
        error: (error: unknown) => {
          const view = toAttendanceError(error);
          this.state.set(view.forbidden ? { kind: 'forbidden' } : { kind: 'error', message: view.message });
        },
      });
  }
}

function isoStart(value: string): string | null {
  if (!value) {
    return null;
  }
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}
