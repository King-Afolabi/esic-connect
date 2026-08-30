import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '../../../../core/auth/auth.service';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { AlternationApiService } from '../../alternation-api.service';
import { toAlternationError } from '../../alternation-errors';
import {
  CanonicalPatternConfiguration,
  WorkStudyPatternResponse,
  formatIsoDate,
  readCanonicalConfiguration,
  workStudyPatternStatusLabel,
  workStudyPatternTypeLabel,
} from '../../alternation.models';
import { CyclePreview } from '../../shared/cycle-preview/cycle-preview';
import { PATTERN_WRITE_ROLES } from '../pattern-list/pattern-list';

type DetailState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'not-found' }
  | { kind: 'forbidden' }
  | { kind: 'ready'; pattern: WorkStudyPatternResponse };

/**
 * Fiche d'un modèle de rythme : faits, prévisualisation du cycle et
 * actions d'écriture (modifier, archiver, restaurer) — visibles
 * uniquement pour `ADMIN` / `SUPER_ADMIN` / `SCHOOL_ADMINISTRATION`.
 * L'archivage et la restauration demandent une confirmation ; l'archivage
 * exige un motif (piste d'audit, `ALT` `Archive.reason`).
 */
@Component({
  selector: 'app-pattern-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    RouterLinkActive,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    CyclePreview,
  ],
  templateUrl: './pattern-detail.html',
  styleUrl: './pattern-detail.scss',
})
export class PatternDetail {
  private readonly api = inject(AlternationApiService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly notifications = inject(NotificationService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  private readonly publicId = this.route.snapshot.paramMap.get('publicId') ?? '';

  protected readonly typeLabel = workStudyPatternTypeLabel;
  protected readonly statusLabel = workStudyPatternStatusLabel;
  protected readonly canWrite = this.auth.hasAnyRole([...PATTERN_WRITE_ROLES]);

  protected readonly state = signal<DetailState>({ kind: 'loading' });
  /** Panneau de confirmation ouvert : `archive`, `restore` ou `null`. */
  protected readonly pendingAction = signal<'archive' | 'restore' | null>(null);
  protected readonly submitting = signal(false);
  protected readonly actionError = signal<string | null>(null);

  protected readonly archiveForm = this.formBuilder.group({
    reason: this.formBuilder.control('', [Validators.required, Validators.maxLength(500)]),
  });

  protected readonly pattern = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.pattern : null;
  });
  protected readonly errorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'error' ? current.message : null;
  });
  protected readonly canonicalConfig = computed<CanonicalPatternConfiguration | null>(() => {
    const value = this.pattern();
    return value ? readCanonicalConfiguration(value.configuration) : null;
  });
  protected readonly rawConfigJson = computed(() => {
    const value = this.pattern();
    return value ? JSON.stringify(value.configuration, null, 2) : '';
  });
  protected readonly isArchived = computed(() => this.pattern()?.status === 'ARCHIVED');

  constructor() {
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  protected startArchive(): void {
    this.archiveForm.reset({ reason: '' });
    this.actionError.set(null);
    this.pendingAction.set('archive');
  }

  protected startRestore(): void {
    this.actionError.set(null);
    this.pendingAction.set('restore');
  }

  protected cancelAction(): void {
    this.pendingAction.set(null);
    this.actionError.set(null);
  }

  protected confirmArchive(): void {
    if (this.archiveForm.invalid || this.submitting()) {
      this.archiveForm.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.actionError.set(null);
    this.api
      .archivePattern(this.publicId, { reason: this.archiveForm.getRawValue().reason.trim() })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.pendingAction.set(null);
          this.notifications.info('Modèle de rythme archivé.');
          this.load();
        },
        error: (error: unknown) => this.onActionError(error),
      });
  }

  protected confirmRestore(): void {
    if (this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.actionError.set(null);
    this.api.restorePattern(this.publicId).subscribe({
      next: () => {
        this.submitting.set(false);
        this.pendingAction.set(null);
        this.notifications.info('Modèle de rythme restauré.');
        this.load();
      },
      error: (error: unknown) => this.onActionError(error),
    });
  }

  private onActionError(error: unknown): void {
    this.submitting.set(false);
    this.actionError.set(toAlternationError(error).message);
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    this.api.getPattern(this.publicId).subscribe({
      next: (pattern) => this.state.set({ kind: 'ready', pattern }),
      error: (error: unknown) => {
        const view = toAlternationError(error);
        if (view.notFound) {
          this.state.set({ kind: 'not-found' });
          return;
        }
        this.state.set(
          view.forbidden ? { kind: 'forbidden' } : { kind: 'error', message: view.message },
        );
      },
    });
  }

  protected readonly formatIsoDate = formatIsoDate;
}
