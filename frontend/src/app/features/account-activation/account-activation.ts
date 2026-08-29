import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, take } from 'rxjs';

import { normalizeHttpError } from '../../core/models/api-error';
import { AccountActivationApiService } from './account-activation-api.service';
import {
  ActivationView,
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
} from './account-activation.models';

const NETWORK_MESSAGE =
  'Impossible de joindre le serveur. Vérifiez votre connexion, puis réessayez.';
const GENERIC_MESSAGE =
  "Une erreur est survenue pendant l'activation. Veuillez réessayer dans un instant.";
const PASSWORD_REJECTED_MESSAGE = `Le mot de passe doit contenir entre ${PASSWORD_MIN_LENGTH} et ${PASSWORD_MAX_LENGTH} caractères.`;

/**
 * Parcours public d'activation de compte, atteint via le lien
 * `/activation?token=<jeton>` généré par le back-end.
 *
 * Sécurité du jeton :
 * <ul>
 *   <li>lu une seule fois depuis la query string puis retiré de la barre
 *       d'adresse (`Location.replaceState`, sans rechargement) ;</li>
 *   <li>conservé uniquement en mémoire du composant, jamais journalisé,
 *       affiché, mis en storage, ni ajouté à une autre URL ;</li>
 *   <li>transmis seulement au paramètre / champ attendu par l'API
 *       ({@link AccountActivationApiService}), jamais comme jeton porteur.</li>
 * </ul>
 *
 * L'activation réussie ne crée aucune session : le back-end répond
 * `204` sans identifiants. L'utilisateur est invité à se connecter.
 */
@Component({
  selector: 'app-account-activation',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './account-activation.html',
  styleUrl: './account-activation.scss',
})
export class AccountActivation {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly api = inject(AccountActivationApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  /** Jeton d'invitation — mémoire du composant uniquement. */
  private capturedToken: string | null = null;

  protected readonly view = signal<ActivationView>({ kind: 'validating' });
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);
  protected readonly showPassword = signal(false);

  protected readonly minLength = PASSWORD_MIN_LENGTH;
  protected readonly maxLength = PASSWORD_MAX_LENGTH;

  protected readonly form = this.formBuilder.group({
    password: this.formBuilder.control('', [
      Validators.required,
      Validators.minLength(PASSWORD_MIN_LENGTH),
      Validators.maxLength(PASSWORD_MAX_LENGTH),
    ]),
  });

  constructor() {
    const rawToken = this.route.snapshot.queryParamMap.get('token')?.trim() ?? '';
    this.capturedToken = rawToken.length > 0 ? rawToken : null;

    // Retire le jeton de la barre d'adresse dès que le routeur a fixé
    // l'URL (`NavigationEnd`), via l'History API (`Location.replaceState`) :
    // ni rechargement, ni entrée d'historique, ni nouvelle navigation.
    // Fait que le jeton soit exploitable ou non.
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        take(1),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.stripTokenFromUrl());

    inject(DestroyRef).onDestroy(() => {
      this.capturedToken = null;
      this.form.reset();
    });

    if (this.capturedToken === null) {
      this.view.set({ kind: 'invalid-link' });
      return;
    }
    this.runValidation();
  }

  protected retryValidation(): void {
    if (this.capturedToken === null) {
      this.view.set({ kind: 'invalid-link' });
      return;
    }
    this.runValidation();
  }

  protected togglePassword(): void {
    this.showPassword.update((visible) => !visible);
  }

  protected submit(): void {
    if (this.capturedToken === null) {
      this.view.set({ kind: 'invalid-link' });
      return;
    }
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.submitError.set(null);
    const { password } = this.form.getRawValue();

    this.api.activate({ token: this.capturedToken, password }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.form.reset();
        this.view.set({ kind: 'success' });
      },
      error: (error: unknown) => this.handleActivationError(error),
    });
  }

  private runValidation(): void {
    this.view.set({ kind: 'validating' });
    // capturedToken est non nul ici (garanti par les appelants).
    this.api.validate(this.capturedToken as string).subscribe({
      next: (response) => this.view.set(response.valid ? { kind: 'form' } : { kind: 'invalid-link' }),
      error: () => this.view.set({ kind: 'validation-error' }),
    });
  }

  private handleActivationError(error: unknown): void {
    this.submitting.set(false);
    const normalized = normalizeHttpError(error);

    if (normalized.status === 400 && normalized.code === 'INVITATION_INVALID') {
      // Jeton devenu inutilisable entre la validation et l'envoi
      // (expiré, révoqué, déjà consommé) : état terminal.
      this.form.reset();
      this.submitError.set(null);
      this.view.set({ kind: 'invalid-link' });
      return;
    }

    if (normalized.status === 400 && normalized.code === 'VALIDATION_ERROR') {
      this.submitError.set(PASSWORD_REJECTED_MESSAGE);
      return;
    }

    // Réseau indisponible ou 5xx : récupérable, le formulaire reste
    // affiché et l'utilisateur peut renvoyer.
    this.submitError.set(normalized.status === 0 ? NETWORK_MESSAGE : GENERIC_MESSAGE);
  }

  private stripTokenFromUrl(): void {
    const currentPath = this.location.path();
    const queryStart = currentPath.indexOf('?');
    if (queryStart === -1) {
      return;
    }
    this.location.replaceState(currentPath.slice(0, queryStart));
  }
}
