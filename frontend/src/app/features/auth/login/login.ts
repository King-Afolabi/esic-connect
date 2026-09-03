import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';

import { SkipLink } from '../../../core/a11y/skip-link';
import { AuthService } from '../../../core/auth/auth.service';
import { isWebAuthnAvailable } from '../../../core/auth/webauthn';
import { normalizeHttpError } from '../../../core/models/api-error';
import { PendingChallengeStore } from '../mfa-challenge/pending-challenge.store';
import { Turnstile } from '../turnstile/turnstile';

const GENERIC_AUTH_FAILURE =
  'Adresse électronique ou mot de passe incorrect.';

@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    RouterLink,
    SkipLink,
    Turnstile,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly pendingChallenge = inject(PendingChallengeStore);

  /** Route d'origine à rejoindre après connexion (lié depuis `?redirect=`). */
  readonly redirect = input<string>();
  /** `expired` lorsque l'utilisateur a été renvoyé ici après un 401. */
  readonly reason = input<string>();

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  /** Jeton anti-robot, quand un fournisseur est configuré (EF-AUTH-011). */
  protected readonly captchaToken = signal<string | null>(null);
  /**
   * Les clés d'accès exigent un contexte sûr : HTTPS, ou `localhost` en
   * développement. Ailleurs, le bouton est masqué plutôt que d'échouer.
   */
  protected readonly passkeysSupported = isWebAuthnAvailable();

  protected readonly form = this.formBuilder.group({
    email: this.formBuilder.control('', [Validators.required, Validators.email]),
    // Le back-end n'impose que « non vide » sur ce champ (LoginRequest).
    password: this.formBuilder.control('', [Validators.required]),
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    const { email, password } = this.form.getRawValue();

    this.auth.login(email, password, this.captchaToken()).subscribe({
      next: (outcome) => {
        this.submitting.set(false);
        if (outcome.kind === 'challenge') {
          // Le mot de passe est bon, la session n'est pas ouverte pour
          // autant : un second facteur est exigé (RG-007, AC-021).
          this.pendingChallenge.start({
            challenge: outcome.challenge,
            email: outcome.email,
            redirect: this.safeTarget(),
          });
          void this.router.navigate(['/connexion/verification']);
          return;
        }
        void this.router.navigateByUrl(this.safeTarget());
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        // Message volontairement identique quel que soit le motif réel :
        // aucune énumération de comptes (docs/02 §27.2, §49).
        const normalized = normalizeHttpError(error);
        this.errorMessage.set(
          normalized.status === 401 || normalized.status === 400
            ? GENERIC_AUTH_FAILURE
            : normalized.message,
        );
      },
    });
  }

  /** Connexion sans mot de passe (EF-AUTH-007). */
  protected loginWithPasskey(): void {
    if (this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.auth.loginWithPasskey().subscribe({
      next: () => {
        this.submitting.set(false);
        void this.router.navigateByUrl(this.safeTarget());
      },
      error: () => {
        this.submitting.set(false);
        // Annulation par l'utilisateur, clé inconnue ou signature refusée :
        // un seul message, qui n'apprend rien sur l'existence du compte.
        this.errorMessage.set(
          "La connexion par clé d'accès n'a pas abouti. Utilisez votre mot de passe.",
        );
      },
    });
  }

  protected onCaptchaToken(token: string | null): void {
    this.captchaToken.set(token);
  }

  private safeTarget(): string {
    const target = this.redirect() ?? '/dashboard';
    return this.isSafeInternalPath(target) ? target : '/dashboard';
  }

  private isSafeInternalPath(path: string): boolean {
    return path.startsWith('/') && !path.startsWith('//');
  }
}
