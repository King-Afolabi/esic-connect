import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { AcademicApiService } from '../../academic/academic-api.service';
import { ClassGroupResponse } from '../../academic/academic.models';
import { PlanningApiService } from '../planning-api.service';
import { toPlanningError } from '../planning-errors';
import {
  PlanningVersionDetailResponse,
  PlanningVersionResponse,
  formatInstant,
} from '../planning.models';

type ClassesState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; classes: ClassGroupResponse[] };

type VersionsState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'empty' }
  | { kind: 'ready'; versions: PlanningVersionResponse[] };

type DetailState =
  | { kind: 'closed' }
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; detail: PlanningVersionDetailResponse };

/**
 * Consultation des versions publiées d'un planning de classe
 * (EF-PLAN-005/007 ; RG-032 : les versions ne sont jamais supprimées).
 * On choisit une classe, on liste ses versions (la plus récente en
 * premier) et on déplie le détail d'une version (ses créneaux).
 */
@Component({
  selector: 'app-planning-versions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatTableModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './planning-versions.html',
  styleUrl: './planning-versions.scss',
})
export class PlanningVersions {
  private readonly api = inject(PlanningApiService);
  private readonly academic = inject(AcademicApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly formatInstant = formatInstant;
  protected readonly versionColumns = ['versionNumber', 'status', 'entryCount', 'changeSummary', 'publishedAt', 'actions'];
  protected readonly entryColumns = ['slotKey', 'title', 'window', 'room'];

  protected readonly classesState = signal<ClassesState>({ kind: 'loading' });
  protected readonly versionsState = signal<VersionsState>({ kind: 'idle' });
  protected readonly detailState = signal<DetailState>({ kind: 'closed' });
  protected readonly openVersionId = signal<string | null>(null);

  protected readonly form = this.formBuilder.group({
    classGroupPublicId: this.formBuilder.control(''),
  });

  protected readonly classes = computed<ClassGroupResponse[]>(() => {
    const current = this.classesState();
    return current.kind === 'ready' ? current.classes : [];
  });
  protected readonly classesError = computed(() => {
    const current = this.classesState();
    return current.kind === 'error' ? current.message : null;
  });
  protected readonly versions = computed<PlanningVersionResponse[]>(() => {
    const current = this.versionsState();
    return current.kind === 'ready' ? current.versions : [];
  });
  protected readonly versionsErrorMessage = computed(() => {
    const current = this.versionsState();
    return current.kind === 'error' ? current.message : null;
  });
  protected readonly detailEntries = computed(() => {
    const current = this.detailState();
    return current.kind === 'ready' ? current.detail.entries : [];
  });
  protected readonly detailErrorMessage = computed(() => {
    const current = this.detailState();
    return current.kind === 'error' ? current.message : null;
  });

  constructor() {
    this.loadClasses();
    this.form.controls.classGroupPublicId.valueChanges.subscribe((value) => {
      this.detailState.set({ kind: 'closed' });
      this.openVersionId.set(null);
      if (value) {
        this.loadVersions(value);
      } else {
        this.versionsState.set({ kind: 'idle' });
      }
    });
  }

  protected loadClasses(): void {
    this.classesState.set({ kind: 'loading' });
    this.academic.listClassGroups({ status: 'ACTIVE', sort: 'code,asc', size: 100 }).subscribe({
      next: (page) => this.classesState.set({ kind: 'ready', classes: page.content }),
      error: (error: unknown) =>
        this.classesState.set({ kind: 'error', message: toPlanningError(error).message }),
    });
  }

  protected retryVersions(): void {
    const value = this.form.getRawValue().classGroupPublicId;
    if (value) {
      this.loadVersions(value);
    }
  }

  protected toggleDetail(version: PlanningVersionResponse): void {
    if (this.openVersionId() === version.publicId) {
      this.openVersionId.set(null);
      this.detailState.set({ kind: 'closed' });
      return;
    }
    this.openVersionId.set(version.publicId);
    this.detailState.set({ kind: 'loading' });
    this.api.getVersion(version.publicId).subscribe({
      next: (detail) => this.detailState.set({ kind: 'ready', detail }),
      error: (error: unknown) =>
        this.detailState.set({ kind: 'error', message: toPlanningError(error).message }),
    });
  }

  private loadVersions(classGroupPublicId: string): void {
    this.versionsState.set({ kind: 'loading' });
    this.api.listVersions(classGroupPublicId, { sort: 'versionNumber,desc', size: 50 }).subscribe({
      next: (page) =>
        this.versionsState.set(
          page.content.length === 0 ? { kind: 'empty' } : { kind: 'ready', versions: page.content },
        ),
      error: (error: unknown) => {
        const view = toPlanningError(error);
        if (view.forbidden) {
          this.versionsState.set({ kind: 'forbidden' });
          return;
        }
        if (view.notFound) {
          this.versionsState.set({ kind: 'empty' });
          return;
        }
        this.versionsState.set({ kind: 'error', message: view.message });
      },
    });
  }
}
