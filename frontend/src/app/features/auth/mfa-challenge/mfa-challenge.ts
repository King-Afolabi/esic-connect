import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router } from '@angular/router';

import { SkipLink } from '../../../core/a11y/skip-link';
import { AuthService } from '../../../core/auth/auth.service';
import { MfaEnrollment } from '../../../core/models/mfa';
import { PendingChallengeStore } from './pending-challenge.store';

/**
 * Seconde étape de connexion (EF-AUTH-008, EF-AUTH-009 ; AC-021).
 *
 * <p>Deux parcours derrière un même écran, selon le défi reçu :
 * <ul>
 *   <li><strong>VERIFY</strong> — un facteur existe : saisir le code à six
 *       chiffres, ou un code de récupération si l'appareil est perdu ;</li>
 *   <li><strong>ENROLL</strong> — un rôle privilégié impose le facteur et
 *       le compte n'en a pas : enrôler d'abord, la session s'ouvre
 *       ensuite sans redemander le mot de passe.</li>
 * </ul>
 *
 * <p>Le défi n'est pas passé dans l'URL : il transite par un service en
 * mémoire. Un identifiant de défi dans la barre d'adresse finirait dans
 * l'historique du navigateur et dans les journaux d'un proxy.
 */
@Component({
  selector: 'app-mfa-challenge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    SkipLink,
  ],
  templateUrl: './mfa-challenge.html',
  styleUrl: './mfa-challenge.scss',
})
export class MfaChallenge {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly store = inject(PendingChallengeStore);

  protected readonly pending = this.store.pending;
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly enrollment = signal<MfaEnrollment | null>(null);
  protected readonly recoveryCodes = signal<string[] | null>(null);
  protected readonly loadingEnrollment = signal(false);

  protected readonly mustEnroll = computed(() => this.pending()?.challenge.purpose === 'ENROLL');

  protected readonly form = this.formBuilder.group({
    // Six chiffres pour un TOTP, onze caractères pour un code de
    // récupération : la borne haute couvre les deux sans les distinguer,
    // afin que l'écran n'annonce pas quel type est attendu.
    code: this.formBuilder.control('', [
      Validators.required,
      Validators.minLength(6),
      Validators.maxLength(20),
    ]),
  });

  constructor() {
    if (this.pending() === null) {
      // Arrivée directe sur l'URL, ou rechargement : le défi n'existe plus
      // en mémoire, la connexion doit repartir du début.
      void this.router.navigate(['/login']);
      return;
    }
    if (this.mustEnroll()) {
      this.openEnrollment();
    }
  }

  protected submit(): void {
    const pending = this.pending();
    if (this.form.invalid || this.submitting() || !pending) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    const code = this.form.getRawValue().code.trim();

    if (this.mustEnroll()) {
      this.auth
        .confirmMfaEnrollment(code, pending.challenge.challengeId, pending.email)
        .subscribe({
          next: (result) => {
            this.submitting.set(false);
            // Les codes de récupération ne s'affichent qu'ici : la session
            // n'est ouverte qu'après leur prise en compte par la personne.
            this.recoveryCodes.set(result.recoveryCodes);
          },
          error: (error: unknown) => this.fail(error),
        });
      return;
    }

    this.auth.verifyMfa(pending.challenge.challengeId, code, pending.email).subscribe({
      next: () => {
        this.submitting.set(false);
        this.finish();
      },
      error: (error: unknown) => this.fail(error),
    });
  }

  /** Quitte l'écran une fois la session ouverte. */
  protected finish(): void {
    const pending = this.pending();
    this.store.clear();
    const target = pending?.redirect ?? '/dashboard';
    void this.router.navigateByUrl(target.startsWith('/') && !target.startsWith('//')
      ? target
      : '/dashboard');
  }

  protected cancel(): void {
    this.store.clear();
    void this.router.navigate(['/login']);
  }

  private openEnrollment(): void {
    const pending = this.pending();
    if (!pending) {
      return;
    }
    this.loadingEnrollment.set(true);
    this.auth.startMfaEnrollment(pending.challenge.challengeId).subscribe({
      next: (enrollment) => {
        this.loadingEnrollment.set(false);
        this.enrollment.set(enrollment);
      },
      error: () => {
        this.loadingEnrollment.set(false);
        this.errorMessage.set(
          "L'ajout du second facteur n'a pas pu démarrer. Reprenez la connexion.",
        );
      },
    });
  }

  private fail(error: unknown): void {
    this.submitting.set(false);
    const status = (error as { status?: number })?.status;
    if (status === 401) {
      this.errorMessage.set('Code incorrect ou expiré. Saisissez le code affiché actuellement.');
      return;
    }
    if (status === 429) {
      this.errorMessage.set('Trop de tentatives. Patientez quelques minutes.');
      return;
    }
    this.errorMessage.set('La vérification n’a pas abouti. Réessayez dans un instant.');
  }
}
