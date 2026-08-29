import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { normalizeHttpError } from '../../../core/models/api-error';
import { AcademicApiService } from '../academic-api.service';
import {
  ACADEMIC_LIST_TABS,
  ACADEMIC_RESOURCES,
  AcademicChildSection,
  AcademicFact,
  AcademicParentLink,
  AcademicResourceConfig,
} from '../academic.config';
import {
  AcademicRecord,
  AcademicResourceSlug,
  academicStatusLabel,
} from '../academic.models';

type DetailState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'not-found' }
  | { kind: 'forbidden' }
  | { kind: 'ready'; record: AcademicRecord };

type ChildState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; rows: AcademicRecord[] };

/**
 * Fiche d'une entité du référentiel académique et ses sous-listes
 * (enfants directs), pilotée par le `data.resource` de la route.
 *
 * - `loadOne` charge l'entité (`GET .../{publicId}`) ;
 * - chaque section « enfants » charge une sous-liste via un filtre
 *   **réellement exposé** (`GET /promotions?academicYear=…`,
 *   `GET /programs/{id}/levels`, `GET /class-groups?promotion=…`, etc.).
 *
 * Un `404` rend un état « introuvable » ; un `403` (hors périmètre
 * pédagogique, `ACAD_FORBIDDEN`) rend un état « accès refusé » — le
 * contrôle d'accès reste côté Spring Security.
 */
@Component({
  selector: 'app-academic-reference-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './academic-reference-detail.html',
  styleUrl: './academic-reference-detail.scss',
})
export class AcademicReferenceDetail {
  private readonly api = inject(AcademicApiService);
  private readonly route = inject(ActivatedRoute);

  private readonly publicId = this.route.snapshot.paramMap.get('publicId') ?? '';
  protected readonly resource = this.route.snapshot.data['resource'] as AcademicResourceSlug;
  protected readonly config: AcademicResourceConfig = ACADEMIC_RESOURCES[this.resource];
  protected readonly tabs = ACADEMIC_LIST_TABS;
  protected readonly statusLabel = academicStatusLabel;
  protected readonly childColumns = ['code', 'name', 'status', 'actions'] as const;

  protected readonly state = signal<DetailState>({ kind: 'loading' });
  /** État de chaque section « enfants », aligné sur `config.children` par index. */
  protected readonly childStates = signal<ChildState[]>(
    this.config.children.map(() => ({ kind: 'loading' as const })),
  );

  protected readonly record = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.record : null;
  });
  protected readonly errorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'error' ? current.message : null;
  });
  protected readonly facts = computed<readonly AcademicFact[]>(() => {
    const value = this.record();
    return value ? this.config.facts(value) : [];
  });
  protected readonly parentLinks = computed<readonly AcademicParentLink[]>(() => {
    const value = this.record();
    return value && this.config.parentLinks ? this.config.parentLinks(value) : [];
  });

  constructor() {
    this.loadRecord();
  }

  protected retryRecord(): void {
    this.loadRecord();
  }

  protected childState(index: number): ChildState {
    return this.childStates()[index] ?? { kind: 'loading' };
  }

  protected childRows(index: number): AcademicRecord[] {
    const current = this.childState(index);
    return current.kind === 'ready' ? current.rows : [];
  }

  protected childErrorMessage(index: number): string | null {
    const current = this.childState(index);
    return current.kind === 'error' ? current.message : null;
  }

  protected retryChild(index: number): void {
    this.loadChild(index, this.config.children[index]);
  }

  private loadRecord(): void {
    this.state.set({ kind: 'loading' });
    this.config.loadOne(this.api, this.publicId).subscribe({
      next: (record) => {
        this.state.set({ kind: 'ready', record });
        this.config.children.forEach((child, index) => this.loadChild(index, child));
      },
      error: (error: unknown) => {
        const normalized = normalizeHttpError(error);
        if (normalized.status === 404) {
          this.state.set({ kind: 'not-found' });
          return;
        }
        if (normalized.status === 403) {
          this.state.set({ kind: 'forbidden' });
          return;
        }
        this.state.set({ kind: 'error', message: normalized.message });
      },
    });
  }

  private loadChild(index: number, child: AcademicChildSection): void {
    this.setChildState(index, { kind: 'loading' });
    child.load(this.api, this.publicId).subscribe({
      next: (page) => this.setChildState(index, { kind: 'ready', rows: page.content }),
      error: (error: unknown) =>
        this.setChildState(index, { kind: 'error', message: normalizeHttpError(error).message }),
    });
  }

  private setChildState(index: number, next: ChildState): void {
    this.childStates.update((states) => {
      const copy = [...states];
      copy[index] = next;
      return copy;
    });
  }
}
