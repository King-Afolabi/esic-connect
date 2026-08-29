import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { normalizeHttpError } from '../../../core/models/api-error';

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
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Route d'origine à rejoindre après connexion (lié depuis `?redirect=`). */
  readonly redirect = input<string>();
  /** `expired` lorsque l'utilisateur a été renvoyé ici après un 401. */
  readonly reason = input<string>();

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

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

    this.auth.login(email, password).subscribe({
      next: () => {
        this.submitting.set(false);
        const target = this.redirect() ?? '/dashboard';
        void this.router.navigateByUrl(this.isSafeInternalPath(target) ? target : '/dashboard');
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

  private isSafeInternalPath(path: string): boolean {
    return path.startsWith('/') && !path.startsWith('//');
  }
}
