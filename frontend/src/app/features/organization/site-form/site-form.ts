import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { NotificationService } from '../../../core/notifications/notification.service';
import { OrganizationApiService } from '../organization-api.service';
import { toOrganizationError } from '../organization-errors';
import { CreateSiteRequest, UpdateSiteRequest } from '../organization.models';

type FormState =
  | { kind: 'ready' }
  | { kind: 'loading' }
  | { kind: 'not-found' }
  | { kind: 'forbidden' }
  | { kind: 'load-error'; message: string };

/** Motif fixe d'audit exigé par `PATCH /sites` : ici pas de motif, le champ n'existe pas. */

/**
 * Formulaire de **création** et de **modification** d'un site. Le mode
 * vient de `route.data.mode`. En édition, `code` est figé : `PATCH /sites`
 * ne le porte pas (`UpdateSiteRequest`).
 *
 * La validation qui fait foi est celle du back-end
 * (`SiteFieldValidator` → `ORG_INVALID_TIME_ZONE` / `ORG_INVALID_COUNTRY_CODE`
 * / `ORG_DUPLICATE_CODE`). Les erreurs serveur rattachables à un champ
 * sont posées sur le `FormControl` concerné ; les autres deviennent un
 * message global.
 */
@Component({
  selector: 'app-site-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './site-form.html',
  styleUrl: './site-form.scss',
})
export class SiteForm {
  private readonly api = inject(OrganizationApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly mode: 'create' | 'edit' =
    this.route.snapshot.data['mode'] === 'edit' ? 'edit' : 'create';
  private readonly publicId = this.route.snapshot.paramMap.get('publicId') ?? '';

  protected readonly state = signal<FormState>({
    kind: this.mode === 'edit' ? 'loading' : 'ready',
  });
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);

  protected readonly form = this.formBuilder.group({
    code: this.formBuilder.control('', [
      Validators.required,
      Validators.maxLength(50),
      Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$/),
    ]),
    name: this.formBuilder.control('', [Validators.required, Validators.maxLength(150)]),
    addressLine1: this.formBuilder.control('', [Validators.maxLength(255)]),
    addressLine2: this.formBuilder.control('', [Validators.maxLength(255)]),
    postalCode: this.formBuilder.control('', [Validators.maxLength(20)]),
    city: this.formBuilder.control('', [Validators.maxLength(100)]),
    countryCode: this.formBuilder.control('', [Validators.maxLength(2)]),
    timeZoneId: this.formBuilder.control('Europe/Paris', [
      Validators.required,
      Validators.maxLength(64),
    ]),
  });

  protected readonly loadErrorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'load-error' ? current.message : null;
  });

  constructor() {
    if (this.mode === 'edit') {
      this.loadForEdit();
    }
  }

  protected retryLoad(): void {
    this.loadForEdit();
  }

  protected submit(): void {
    this.submitError.set(null);
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const raw = this.form.getRawValue();
    const optional = (value: string): string | null => (value.trim() ? value.trim() : null);

    if (this.mode === 'edit') {
      const body: UpdateSiteRequest = {
        name: raw.name.trim(),
        addressLine1: optional(raw.addressLine1),
        addressLine2: optional(raw.addressLine2),
        postalCode: optional(raw.postalCode),
        city: optional(raw.city),
        countryCode: optional(raw.countryCode),
        timeZoneId: raw.timeZoneId.trim(),
      };
      this.api.updateSite(this.publicId, body).subscribe({
        next: (site) => this.onSaved(site.publicId, 'Site mis à jour.'),
        error: (error: unknown) => this.onSubmitError(error),
      });
      return;
    }

    const body: CreateSiteRequest = {
      code: raw.code.trim(),
      name: raw.name.trim(),
      addressLine1: optional(raw.addressLine1),
      addressLine2: optional(raw.addressLine2),
      postalCode: optional(raw.postalCode),
      city: optional(raw.city),
      countryCode: optional(raw.countryCode),
      timeZoneId: raw.timeZoneId.trim(),
    };
    this.api.createSite(body).subscribe({
      next: (site) => this.onSaved(site.publicId, 'Site créé.'),
      error: (error: unknown) => this.onSubmitError(error),
    });
  }

  private onSaved(publicId: string, message: string): void {
    this.submitting.set(false);
    this.notifications.info(message);
    void this.router.navigate(['/organization/sites', publicId]);
  }

  private onSubmitError(error: unknown): void {
    this.submitting.set(false);
    const view = toOrganizationError(error);
    if (view.field === 'code' && this.mode === 'create') {
      this.form.controls.code.setErrors({ server: view.message });
    } else if (view.field === 'timeZoneId') {
      this.form.controls.timeZoneId.setErrors({ server: view.message });
    } else if (view.field === 'countryCode') {
      this.form.controls.countryCode.setErrors({ server: view.message });
    }
    this.submitError.set(view.message);
  }

  private loadForEdit(): void {
    this.state.set({ kind: 'loading' });
    this.api.getSite(this.publicId).subscribe({
      next: (site) => {
        this.form.controls.code.setValue(site.code);
        this.form.controls.code.disable();
        this.form.controls.name.setValue(site.name);
        this.form.controls.addressLine1.setValue(site.addressLine1 ?? '');
        this.form.controls.addressLine2.setValue(site.addressLine2 ?? '');
        this.form.controls.postalCode.setValue(site.postalCode ?? '');
        this.form.controls.city.setValue(site.city ?? '');
        this.form.controls.countryCode.setValue(site.countryCode ?? '');
        this.form.controls.timeZoneId.setValue(site.timeZoneId);
        this.state.set({ kind: 'ready' });
      },
      error: (error: unknown) => {
        const view = toOrganizationError(error);
        if (view.notFound) {
          this.state.set({ kind: 'not-found' });
          return;
        }
        this.state.set(
          view.forbidden
            ? { kind: 'forbidden' }
            : { kind: 'load-error', message: view.message },
        );
      },
    });
  }
}
