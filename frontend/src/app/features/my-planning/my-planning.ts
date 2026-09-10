import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { normalizeHttpError } from '../../core/models/api-error';
import { MyPlanningApiService } from './my-planning-api.service';
import { MyPlanningSession, sessionStatusLabel, teacherDisplayName } from './my-planning.models';
import { formatInstantUtc } from '../sessions/sessions.models';

type ViewState =
  | { kind: 'loading' }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; role: 'TEACHER' | 'STUDENT' | 'NONE'; sessions: MyPlanningSession[] };

/**
 * « Mon planning » (CDC §5.6/§5.7) — un formateur ou un apprenant consulte
 * ses propres séances dans l'application, sans passer par un abonnement
 * de calendrier externe. Le serveur (`GET /api/v1/me/planning`) résout
 * seul le périmètre à partir du rôle effectif du JWT.
 */
@Component({
  selector: 'app-my-planning',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './my-planning.html',
  styleUrl: './my-planning.scss',
})
export class MyPlanning {
  private readonly api = inject(MyPlanningApiService);
  private readonly fb = inject(FormBuilder);

  protected readonly formatInstantUtc = formatInstantUtc;
  protected readonly sessionStatusLabel = sessionStatusLabel;
  protected readonly teacherDisplayName = teacherDisplayName;

  protected readonly state = signal<ViewState>({ kind: 'loading' });

  protected readonly filters = this.fb.nonNullable.group({
    from: [''],
    to: [''],
  });

  protected readonly sessions = computed(() => {
    const s = this.state();
    return s.kind === 'ready' ? s.sessions : [];
  });
  protected readonly isTeacher = computed(() => {
    const s = this.state();
    return s.kind === 'ready' && s.role === 'TEACHER';
  });
  protected readonly errorMessage = computed(() => {
    const s = this.state();
    return s.kind === 'error' ? s.message : null;
  });

  constructor() {
    this.load();
  }

  protected applyFilters(): void {
    this.load();
  }

  protected resetFilters(): void {
    this.filters.reset({ from: '', to: '' });
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    const raw = this.filters.getRawValue();
    this.api.get(isoStart(raw.from), isoEnd(raw.to)).subscribe({
      next: (result) => this.state.set({ kind: 'ready', role: result.role, sessions: result.sessions }),
      error: (error: unknown) => {
        const view = normalizeHttpError(error);
        this.state.set(view.status === 403 ? { kind: 'forbidden' } : { kind: 'error', message: view.message });
      },
    });
  }
}

/** Convertit une date `YYYY-MM-DD` du filtre en instant ISO (début / fin de jour UTC). */
function isoStart(value: string): string | null {
  if (!value) {
    return null;
  }
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

function isoEnd(value: string): string | null {
  if (!value) {
    return null;
  }
  const date = new Date(`${value}T23:59:59Z`);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}
