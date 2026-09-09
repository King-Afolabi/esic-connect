import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { AuthService } from '../../../core/auth/auth.service';
import { isWebAuthnAvailable } from '../../../core/auth/webauthn';
import { normalizeHttpError } from '../../../core/models/api-error';
import { MfaEnrollment, MfaStatus, PasskeyCredential, TrustedDevice } from '../../../core/models/mfa';

/**
 * Longueur minimale du mot de passe, alignée sur `PasswordPolicy`
 * côté serveur (`app.security.password.min-length`, défaut 12). Le
 * serveur reste l'autorité : ce contrôle ne fait qu'éviter un aller-retour
 * évident.
 */
const PASSWORD_MIN_LENGTH = 12;

/** Le nouveau mot de passe et sa confirmation doivent coïncider. */
function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const next = group.get('newPassword')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return next && confirm && next !== confirm ? { passwordMismatch: true } : null;
}

/**
 * Sécurité du compte : second facteur, clés d'accès, appareils reconnus
 * (EF-AUTH-006, EF-AUTH-008, EF-AUTH-009, EF-AUTH-013).
 *
 * <p>L'écran n'accorde aucun droit : il rend visible ce que le serveur
 * autorise. Un administrateur y voit que son second facteur est
 * obligatoire et que le bouton de retrait n'existe pas — le serveur le
 * refuserait de toute façon (RG-007).
 */
@Component({
  selector: 'app-account-security',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './account-security.html',
  styleUrl: './account-security.scss',
})
export class AccountSecurity {
  private readonly auth = inject(AuthService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly status = signal<MfaStatus | null>(null);
  protected readonly passkeys = signal<PasskeyCredential[]>([]);
  protected readonly devices = signal<TrustedDevice[]>([]);
  protected readonly enrollment = signal<MfaEnrollment | null>(null);
  protected readonly recoveryCodes = signal<string[] | null>(null);
  protected readonly busy = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly infoMessage = signal<string | null>(null);

  /**
   * Les clés d'accès exigent un contexte sûr : HTTPS, ou `localhost` en
   * développement. Hors de là, la section est masquée plutôt que de
   * proposer un bouton qui échouerait.
   */
  protected readonly passkeysSupported = isWebAuthnAvailable();

  protected readonly enrollForm = this.formBuilder.group({
    code: this.formBuilder.control('', [Validators.required, Validators.minLength(6)]),
  });

  protected readonly passkeyForm = this.formBuilder.group({
    label: this.formBuilder.control('', [Validators.maxLength(120)]),
  });

  /**
   * Changement de mot de passe (EF-AUTH, docs/02 §17.1). Disponible pour
   * **tout** rôle : le serveur agit sur le seul sujet du jeton. Trois
   * champs (actuel / nouveau / confirmation), confirmation vérifiée ici,
   * politique et mot de passe actuel vérifiés côté serveur.
   */
  protected readonly passwordForm = this.formBuilder.group(
    {
      currentPassword: this.formBuilder.control('', [Validators.required]),
      newPassword: this.formBuilder.control('', [
        Validators.required,
        Validators.minLength(PASSWORD_MIN_LENGTH),
      ]),
      confirmPassword: this.formBuilder.control('', [Validators.required]),
    },
    { validators: passwordsMatch },
  );

  protected readonly passwordBusy = signal(false);
  protected readonly passwordError = signal<string | null>(null);
  protected readonly passwordMinLength = PASSWORD_MIN_LENGTH;

  constructor() {
    this.refresh();
  }

  protected changePassword(): void {
    if (this.passwordForm.invalid || this.passwordBusy()) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.passwordBusy.set(true);
    this.passwordError.set(null);
    this.auth.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        // Le serveur a fermé toutes les sessions : on renvoie vers la
        // connexion avec le bandeau « mot de passe modifié ».
        this.auth.completePasswordChange();
      },
      error: (error: unknown) => {
        this.passwordBusy.set(false);
        const normalized = normalizeHttpError(error);
        const detail = normalized.details.length > 0 ? ` ${normalized.details.join(' ')}` : '';
        this.passwordError.set(
          normalized.status === 0
            ? 'Le service est momentanément indisponible. Réessayez.'
            : `${normalized.message}${detail}`,
        );
      },
    });
  }

  protected refresh(): void {
    this.auth.mfaStatus().subscribe({
      next: (status) => this.status.set(status),
      error: () => this.errorMessage.set("L'état du second facteur n'a pas pu être chargé."),
    });
    this.auth.trustedDevices().subscribe({
      next: (devices) => this.devices.set(devices),
      error: () => undefined,
    });
    if (this.passkeysSupported) {
      this.auth.passkeys().subscribe({
        next: (passkeys) => this.passkeys.set(passkeys),
        error: () => undefined,
      });
    }
  }

  protected startEnrollment(): void {
    this.reset();
    this.busy.set(true);
    this.auth.startMfaEnrollment().subscribe({
      next: (enrollment) => {
        this.busy.set(false);
        this.enrollment.set(enrollment);
      },
      error: () => this.fail("L'ajout du second facteur n'a pas pu démarrer."),
    });
  }

  protected confirmEnrollment(): void {
    if (this.enrollForm.invalid || this.busy()) {
      this.enrollForm.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.errorMessage.set(null);
    this.auth
      .confirmMfaEnrollment(this.enrollForm.getRawValue().code.trim(), undefined, '')
      .subscribe({
        next: (result) => {
          this.busy.set(false);
          this.enrollment.set(null);
          this.enrollForm.reset();
          this.recoveryCodes.set(result.recoveryCodes);
          this.refresh();
        },
        error: () => this.fail('Code incorrect ou expiré. Saisissez le code affiché actuellement.'),
      });
  }

  protected registerPasskey(): void {
    this.reset();
    this.busy.set(true);
    const label = this.passkeyForm.getRawValue().label.trim() || "Clé d'accès";
    this.auth.registerPasskey(label).subscribe({
      next: () => {
        this.busy.set(false);
        this.passkeyForm.reset();
        this.infoMessage.set("La clé d'accès est enregistrée sur cet appareil.");
        this.refresh();
      },
      error: () =>
        this.fail(
          "La clé d'accès n'a pas pu être enregistrée. L'opération a peut-être été annulée.",
        ),
    });
  }

  protected revokePasskey(id: string): void {
    this.busy.set(true);
    this.auth.revokePasskey(id).subscribe({
      next: () => {
        this.busy.set(false);
        this.infoMessage.set("La clé d'accès a été révoquée.");
        this.refresh();
      },
      error: () => this.fail("La clé d'accès n'a pas pu être révoquée."),
    });
  }

  protected revokeDevice(id: string): void {
    this.busy.set(true);
    this.auth.revokeTrustedDevice(id).subscribe({
      next: () => {
        this.busy.set(false);
        this.infoMessage.set(
          "L'appareil n'est plus reconnu : une vérification complète sera redemandée.",
        );
        this.refresh();
      },
      error: () => this.fail("L'appareil n'a pas pu être révoqué."),
    });
  }

  private reset(): void {
    this.errorMessage.set(null);
    this.infoMessage.set(null);
    this.recoveryCodes.set(null);
  }

  private fail(message: string): void {
    this.busy.set(false);
    this.errorMessage.set(message);
  }
}
