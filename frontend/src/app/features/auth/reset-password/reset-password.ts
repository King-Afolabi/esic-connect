import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';

import { SkipLink } from '../../../core/a11y/skip-link';
import { AuthService } from '../../../core/auth/auth.service';
import { normalizeHttpError } from '../../../core/models/api-error';

/** Doit rester aligné sur `app.security.password.min-length` côté serveur. */
const MIN_PASSWORD_LENGTH = 12;

/**
 * Vérifie que la confirmation correspond. Le contrôle vit au niveau du
 * groupe : une erreur portée par le seul champ de confirmation serait
 * effacée à chaque frappe dans le champ principal.
 */
function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const password = group.get('newPassword')?.value as string | undefined;
  const confirmation = group.get('confirmation')?.value as string | undefined;
  if (!password || !confirmation) {
    return null;
  }
  return password === confirmation ? null : { passwordsMismatch: true };
}

/**
 * Définition d'un nouveau mot de passe à partir d'un lien reçu par
 * courriel (EF-AUTH-005).
 *
 * <p>La politique complète est appliquée par le serveur : cet écran ne
 * duplique que la longueur minimale, pour éviter un aller-retour évident.
 * Les autres refus — mot de passe trop courant, contenant l'adresse —
 * sont affichés tels que le serveur les renvoie, dans `details`.
 */
@Component({
  selector: 'app-reset-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    SkipLink,
  ],
  templateUrl: './reset-password.html',
  styleUrl: './reset-password.scss',
})
export class ResetPassword {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Jeton lié depuis `?token=…`. Absent, l'écran ne propose rien. */
  readonly token = input<string>();

  protected readonly minLength = MIN_PASSWORD_LENGTH;
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly errorDetails = signal<readonly string[]>([]);
  protected readonly hasToken = computed(() => !!this.token()?.trim());

  protected readonly form = this.formBuilder.group(
    {
      newPassword: this.formBuilder.control('', [
        Validators.required,
        Validators.minLength(MIN_PASSWORD_LENGTH),
      ]),
      confirmation: this.formBuilder.control('', [Validators.required]),
    },
    { validators: passwordsMatch },
  );

  protected submit(): void {
    const token = this.token()?.trim();
    if (!token || this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.errorDetails.set([]);

    this.auth.resetPassword(token, this.form.getRawValue().newPassword).subscribe({
      next: () => {
        this.submitting.set(false);
        // Les sessions ayant été révoquées côté serveur, la seule suite
        // cohérente est de se reconnecter avec le nouveau mot de passe.
        void this.router.navigate(['/login'], { queryParams: { reason: 'password-changed' } });
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        const normalized = normalizeHttpError(error);
        this.errorMessage.set(normalized.message);
        this.errorDetails.set(normalized.details ?? []);
      },
    });
  }
}
