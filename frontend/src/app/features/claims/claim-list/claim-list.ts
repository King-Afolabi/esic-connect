import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { formatInstantUtc } from '../../sessions/sessions.models';
import { ClaimsApiService } from '../claims-api.service';
import { toClaimError } from '../claim-errors';
import {
  CLAIM_AUDIENCES,
  CLAIM_CATEGORIES,
  CLAIM_STAFF_ROLES,
  ClaimSummary,
  claimAudienceLabel,
  claimCategoryLabel,
  claimStatusLabel,
} from '../claims.models';

type ListState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; rows: ClaimSummary[]; total: number };

/**
 * Réclamations : liste et dépôt (EF-CLAIM-001 ; docs/02 §20).
 *
 * <p>La même route sert l'apprenant et les intervenants — le serveur
 * décide de ce que chacun voit à partir de son rôle et de son périmètre.
 * Le front n'ajoute aucun filtre d'autorisation : il ne ferait
 * qu'imiter une décision déjà prise, et divergerait au premier
 * changement.
 *
 * <p>Le destinataire est un <strong>guichet</strong>, pas une personne :
 * c'est ce qui permet à une réclamation d'être transférée sans perdre son
 * histoire.
 */
@Component({
  selector: 'app-claim-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
  ],
  templateUrl: './claim-list.html',
  styleUrl: './claim-list.scss',
})
export class ClaimList {
  private readonly api = inject(ClaimsApiService);
  private readonly fb = inject(FormBuilder);
  private readonly roleContext = inject(RoleContextService);
  private readonly notifications = inject(NotificationService);

  protected readonly categories = CLAIM_CATEGORIES;
  protected readonly audiences = CLAIM_AUDIENCES;
  protected readonly categoryLabel = claimCategoryLabel;
  protected readonly audienceLabel = claimAudienceLabel;
  protected readonly statusLabel = claimStatusLabel;
  protected readonly formatInstantUtc = formatInstantUtc;
  protected readonly columns = ['subject', 'category', 'audience', 'status', 'updatedAt', 'open'] as const;

  protected readonly state = signal<ListState>({ kind: 'loading' });
  protected readonly page = signal(0);
  private readonly size = 20;
  protected readonly creating = signal(false);
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly filters = this.fb.nonNullable.group({ audience: [''] });
  protected readonly form = this.fb.nonNullable.group({
    category: ['ATTENDANCE', Validators.required],
    audience: ['PEDAGOGICAL_MANAGER', Validators.required],
    subject: ['', [Validators.required, Validators.maxLength(191)]],
    description: ['', [Validators.required, Validators.maxLength(5000)]],
    sessionPublicId: ['', Validators.maxLength(64)],
  });

  /**
   * Le dépôt est ouvert à tout compte authentifié. Un intervenant qui
   * dépose une réclamation le fait au même titre que n'importe qui —
   * le serveur choisit le guichet par défaut selon son rôle.
   */
  protected readonly isStaff = computed(() =>
    this.roleContext.effectiveRoles().some((role) => CLAIM_STAFF_ROLES.includes(role as never)),
  );

  protected readonly totalPages = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? Math.max(1, Math.ceil(current.total / this.size)) : 1;
  });

  constructor() {
    this.load();
  }

  protected toggleCreate(): void {
    this.creating.update((open) => !open);
    this.formError.set(null);
  }

  protected applyFilters(): void {
    this.page.set(0);
    this.load();
  }

  protected previous(): void {
    this.page.update((p) => Math.max(0, p - 1));
    this.load();
  }

  protected next(): void {
    this.page.update((p) => Math.min(this.totalPages() - 1, p + 1));
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    this.submitting.set(true);
    this.formError.set(null);
    this.api
      .create({
        category: raw.category,
        audience: raw.audience,
        subject: raw.subject.trim(),
        description: raw.description.trim(),
        sessionPublicId: raw.sessionPublicId.trim() || null,
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.creating.set(false);
          this.form.reset({
            category: 'ATTENDANCE',
            audience: 'PEDAGOGICAL_MANAGER',
            subject: '',
            description: '',
            sessionPublicId: '',
          });
          this.notifications.info('Réclamation ouverte.');
          this.load();
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.formError.set(toClaimError(error).message);
        },
      });
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    this.api
      .list({
        audience: this.filters.getRawValue().audience || null,
        page: this.page(),
        size: this.size,
      })
      .subscribe({
        next: (response) =>
          this.state.set({
            kind: 'ready',
            rows: response.content,
            total: response.totalElements,
          }),
        error: (error: unknown) =>
          this.state.set({ kind: 'error', message: toClaimError(error).message }),
      });
  }
}
