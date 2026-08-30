import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';

import { SessionsApiService } from '../sessions/sessions-api.service';
import { toSessionError } from '../sessions/session-errors';
import { AttendanceRecordResponse, formatInstantUtc } from '../sessions/sessions.models';

/** Longueur défensive du champ code court (le serveur revalide). */
const SHORT_CODE_MAX_LENGTH = 32;

type CheckInState =
  | { kind: 'idle' }
  | { kind: 'submitting' }
  | { kind: 'success'; record: AttendanceRecordResponse }
  | { kind: 'error'; message: string };

/**
 * Écran d'émargement de l'apprenant — `POST /api/v1/attendance/validate`.
 *
 * Dans cette tranche, le parcours fiable est la **saisie du code court**
 * affiché par le formateur (le scan caméra sera ajouté ultérieurement).
 * Le serveur détermine l'apprenant à partir du seul JWT : aucun
 * identifiant d'apprenant ni d'inscription n'est transmis. Rien n'est
 * conservé (ni `localStorage`, ni paramètre d'URL) ; le formulaire reste
 * réutilisable après une erreur.
 */
@Component({
  selector: 'app-attendance-check-in',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './attendance-check-in.html',
  styleUrl: './attendance-check-in.scss',
})
export class AttendanceCheckIn {
  private readonly api = inject(SessionsApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly shortCodeMaxLength = SHORT_CODE_MAX_LENGTH;
  protected readonly formatInstantUtc = formatInstantUtc;

  protected readonly form = this.formBuilder.group({
    shortCode: this.formBuilder.control('', [
      Validators.required,
      Validators.maxLength(SHORT_CODE_MAX_LENGTH),
    ]),
  });

  protected readonly state = signal<CheckInState>({ kind: 'idle' });

  protected readonly submitting = computed(() => this.state().kind === 'submitting');
  protected readonly successRecord = computed(() => {
    const current = this.state();
    return current.kind === 'success' ? current.record : null;
  });
  protected readonly errorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'error' ? current.message : null;
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const shortCode = normalizeShortCode(this.form.getRawValue().shortCode);
    if (!shortCode) {
      this.form.controls.shortCode.setErrors({ required: true });
      this.form.markAllAsTouched();
      return;
    }

    this.state.set({ kind: 'submitting' });
    this.api.validateAttendance({ shortCode }).subscribe({
      next: (record) => {
        this.state.set({ kind: 'success', record });
        this.form.reset({ shortCode: '' });
      },
      error: (error: unknown) => {
        this.state.set({ kind: 'error', message: toSessionError(error).message });
      },
    });
  }

  protected reset(): void {
    this.state.set({ kind: 'idle' });
    this.form.reset({ shortCode: '' });
  }
}

/** Majuscules, sans espaces ni séparateurs — cohérent avec le back-end. */
function normalizeShortCode(value: string): string {
  return value
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '');
}
