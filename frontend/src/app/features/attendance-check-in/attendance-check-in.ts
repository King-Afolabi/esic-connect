import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';

import { RoleContextService } from '../../core/auth/role-context.service';
import { SessionsApiService } from '../sessions/sessions-api.service';
import { toSessionError } from '../sessions/session-errors';
import { AttendanceRecordResponse, formatInstantUtc } from '../sessions/sessions.models';

/** Longueur défensive du champ code court (le serveur revalide). */
const SHORT_CODE_MAX_LENGTH = 32;

/** Idem pour le jeton d'affiche de salle. */
const ROOM_REFERENCE_MAX_LENGTH = 128;

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
 *
 * Saisie et soumission ne sont possibles que dans un **contexte de rôle
 * `STUDENT` effectif**. Quitter ce contexte efface immédiatement le code,
 * le récépissé et les erreurs métier, et bloque toute requête (y compris
 * une réponse arrivée tardivement). Revenir au contexte `STUDENT` rend le
 * formulaire à nouveau utilisable, sans rechargement.
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
    MatCheckboxModule,
    MatIconModule,
  ],
  templateUrl: './attendance-check-in.html',
  styleUrl: './attendance-check-in.scss',
})
export class AttendanceCheckIn {
  private readonly api = inject(SessionsApiService);
  private readonly roleContext = inject(RoleContextService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly shortCodeMaxLength = SHORT_CODE_MAX_LENGTH;
  protected readonly roomReferenceMaxLength = ROOM_REFERENCE_MAX_LENGTH;
  protected readonly formatInstantUtc = formatInstantUtc;

  protected readonly form = this.formBuilder.group({
    shortCode: this.formBuilder.control('', [
      Validators.required,
      Validators.maxLength(SHORT_CODE_MAX_LENGTH),
    ]),
    /**
     * Suivi à distance déclaré (EF-ENR-004 ; docs/02 §15.3). Sur une
     * séance présentielle, le serveur exige une autorisation active.
     */
    remote: this.formBuilder.control(false),
  });

  /**
   * QR fixe de salle (EF-ATT-010) — parcours distinct : le jeton vient de
   * l'affiche, pas du formateur, et n'est accepté que depuis le réseau de
   * l'établissement (EF-ATT-008).
   */
  protected readonly roomForm = this.formBuilder.group({
    roomReference: this.formBuilder.control('', [
      Validators.required,
      Validators.maxLength(ROOM_REFERENCE_MAX_LENGTH),
    ]),
  });

  protected readonly state = signal<CheckInState>({ kind: 'idle' });

  /** L'émargement n'est possible qu'en contexte de rôle `STUDENT` effectif. */
  protected readonly canCheckIn = computed(() => this.roleContext.effectiveRoles().includes('STUDENT'));

  protected readonly submitting = computed(() => this.state().kind === 'submitting');
  protected readonly successRecord = computed(() => {
    const current = this.state();
    return current.kind === 'success' ? current.record : null;
  });
  protected readonly errorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'error' ? current.message : null;
  });

  constructor() {
    // Sortie du contexte STUDENT : on efface code, récépissé et erreurs.
    effect(() => {
      if (!this.canCheckIn()) {
        this.state.set({ kind: 'idle' });
        this.form.reset({ shortCode: '', remote: false });
        this.roomForm.reset({ roomReference: '' });
      }
    });
  }

  protected submit(): void {
    if (!this.canCheckIn()) {
      return;
    }
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
    this.api
      .validateAttendance({ shortCode, remote: this.form.getRawValue().remote || null })
      .subscribe({
        next: (record) => {
          // Réponse tardive après une sortie du contexte STUDENT : ignorée.
          if (!this.canCheckIn()) {
            return;
          }
          this.state.set({ kind: 'success', record });
          this.form.reset({ shortCode: '', remote: false });
        },
        error: (error: unknown) => {
          if (!this.canCheckIn()) {
            return;
          }
          this.state.set({ kind: 'error', message: toSessionError(error).message });
        },
      });
  }

  /** Émargement par le QR fixe affiché dans la salle (EF-ATT-010). */
  protected submitRoomQr(): void {
    if (!this.canCheckIn()) {
      return;
    }
    if (this.roomForm.invalid || this.submitting()) {
      this.roomForm.markAllAsTouched();
      return;
    }
    const roomReference = this.roomForm.getRawValue().roomReference.trim();
    if (!roomReference) {
      this.roomForm.controls.roomReference.setErrors({ required: true });
      this.roomForm.markAllAsTouched();
      return;
    }

    this.state.set({ kind: 'submitting' });
    this.api.validateRoomQr({ roomReference }).subscribe({
      next: (record) => {
        if (!this.canCheckIn()) {
          return;
        }
        this.state.set({ kind: 'success', record });
        this.roomForm.reset({ roomReference: '' });
      },
      error: (error: unknown) => {
        if (!this.canCheckIn()) {
          return;
        }
        this.state.set({ kind: 'error', message: toSessionError(error).message });
      },
    });
  }

  protected reset(): void {
    this.state.set({ kind: 'idle' });
    this.form.reset({ shortCode: '', remote: false });
    this.roomForm.reset({ roomReference: '' });
  }
}

/** Majuscules, sans espaces ni séparateurs — cohérent avec le back-end. */
function normalizeShortCode(value: string): string {
  return value
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '');
}
