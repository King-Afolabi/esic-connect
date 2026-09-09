import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { AuthService } from '../../../core/auth/auth.service';
import { isWebAuthnAvailable } from '../../../core/auth/webauthn';
import { MfaEnrollment, MfaStatus, PasskeyCredential, TrustedDevice } from '../../../core/models/mfa';

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

  constructor() {
    this.refresh();
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
