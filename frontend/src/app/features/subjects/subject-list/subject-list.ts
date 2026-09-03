import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';

import { normalizeHttpError } from '../../../core/models/api-error';
import { SubjectsApiService } from '../subjects-api.service';
import { SubjectResponse } from '../subjects.models';

/**
 * Référentiel des matières (EF-ACA-006 ; docs/02 §6.4).
 *
 * <p>L'écran ne propose <strong>aucun champ formateur</strong>, et il ne
 * doit jamais en proposer : le cahier réserve l'affectation à la séance,
 * à une période ou à une association classe–matière–période. Un champ ici
 * contredirait la règle et casserait les remplacements.
 */
@Component({
  selector: 'app-subject-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './subject-list.html',
  styleUrl: './subject-list.scss',
})
export class SubjectList {
  private readonly api = inject(SubjectsApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly columns = ['code', 'name', 'hourlyVolume', 'programs', 'status', 'actions'];
  protected readonly subjects = signal<SubjectResponse[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly showArchived = signal(false);

  protected readonly form = this.formBuilder.group({
    code: this.formBuilder.control('', [Validators.required, Validators.maxLength(40)]),
    name: this.formBuilder.control('', [Validators.required, Validators.maxLength(200)]),
    hourlyVolume: this.formBuilder.control<number | null>(null),
  });

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.api
      .list({ status: this.showArchived() ? undefined : 'ACTIVE', size: 100, sort: 'code' })
      .subscribe({
        next: (page) => {
          this.loading.set(false);
          this.subjects.set(page.content);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(normalizeHttpError(error).message);
        },
      });
  }

  protected toggleArchived(): void {
    this.showArchived.set(!this.showArchived());
    this.reload();
  }

  protected submit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    const value = this.form.getRawValue();
    this.api
      .create({
        code: value.code.trim(),
        name: value.name.trim(),
        hourlyVolume: value.hourlyVolume,
      })
      .subscribe({
        next: () => {
          this.form.reset();
          this.reload();
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(this.explain(error));
        },
      });
  }

  protected archive(subject: SubjectResponse): void {
    this.loading.set(true);
    this.api.archive(subject.id, 'Retirée du catalogue').subscribe({
      next: () => this.reload(),
      error: (error: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(this.explain(error));
      },
    });
  }

  protected restore(subject: SubjectResponse): void {
    this.loading.set(true);
    this.api.restore(subject.id).subscribe({
      next: () => this.reload(),
      error: (error: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(this.explain(error));
      },
    });
  }

  /** Codes des formations rattachées, pour l'affichage tabulaire. */
  protected programCodes(subject: SubjectResponse): string {
    return subject.programs.map((program) => program.code).join(', ');
  }

  private explain(error: unknown): string {
    const normalized = normalizeHttpError(error);
    if (normalized.code === 'ACAD_DUPLICATE_CODE') {
      return 'Ce code de matière est déjà utilisé.';
    }
    if (normalized.code === 'ACAD_ENTITY_ARCHIVED') {
      return 'Cette matière est archivée : restaurez-la avant de la modifier.';
    }
    return normalized.message;
  }
}
