import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { SkipLink } from '../../../core/a11y/skip-link';
import { AuthService } from '../../../core/auth/auth.service';

/**
 * Demande de lien de réinitialisation (EF-AUTH-005).
 *
 * <p>L'écran affiche **toujours** la même confirmation après envoi, que
 * l'adresse soit connue ou non : le serveur répond déjà de façon neutre
 * (docs/02 §17.8), et il serait absurde que l'interface reconstitue
 * l'information qu'il prend soin de ne pas donner.
 *
 * <p>Seul le refus pour excès de tentatives (`429`) est distingué, car il
 * appelle une action concrète de l'utilisateur — attendre — et n'apprend
 * rien sur l'existence du compte : le compteur existe pour toute adresse.
 */
@Component({
  selector: 'app-forgot-password',
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
  templateUrl: './forgot-password.html',
  styleUrl: './forgot-password.scss',
})
export class ForgotPassword {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);

  protected readonly submitting = signal(false);
  protected readonly submitted = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly form = this.formBuilder.group({
    email: this.formBuilder.control('', [Validators.required, Validators.email]),
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    this.auth.requestPasswordReset(this.form.getRawValue().email).subscribe({
      next: () => {
        this.submitting.set(false);
        this.submitted.set(true);
      },
      error: (error: { status?: number }) => {
        this.submitting.set(false);
        if (error?.status === 429) {
          this.errorMessage.set(
            'Trop de demandes pour cette adresse. Patientez quelques minutes avant de réessayer.',
          );
          return;
        }
        // Toute autre erreur est traitée comme un incident technique : on
        // ne laisse jamais entendre que l'adresse serait en cause.
        this.errorMessage.set(
          "L'envoi n'a pas pu aboutir. Réessayez dans un instant.",
        );
      },
    });
  }
}
