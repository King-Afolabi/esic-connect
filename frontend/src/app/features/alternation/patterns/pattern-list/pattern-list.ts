import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '../../../../core/auth/auth.service';
import { toAlternationError } from '../../alternation-errors';
import { frenchPaginatorIntl } from '../../alternation-paginator';
import { AlternationApiService } from '../../alternation-api.service';
import {
  PATTERN_SORT_FIELDS,
  PageResponse,
  PatternSortField,
  SortDirection,
  WORK_STUDY_PATTERN_STATUSES,
  WORK_STUDY_PATTERN_TYPES,
  WorkStudyPatternResponse,
  WorkStudyPatternStatus,
  WorkStudyPatternType,
  workStudyPatternStatusLabel,
  workStudyPatternTypeLabel,
} from '../../alternation.models';

/** Rôles autorisés à écrire un modèle (`AlternationWeb.PATTERN_WRITE_ROLES`). */
export const PATTERN_WRITE_ROLES = ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION'] as const;

type ListState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; page: PageResponse<WorkStudyPatternResponse> };

const DEFAULT_SORT_FIELD: PatternSortField = 'code';
const DEFAULT_SORT_DIRECTION: SortDirection = 'asc';
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

/**
 * Liste des modèles de rythme — `GET /api/v1/alternation/patterns`.
 *
 * Recherche `q` (code ou nom), filtres `status` / `type`, tri sur la
 * liste blanche `code|name|createdAt|updatedAt` et pagination bornée à
 * 100 : strictement ce que l'API expose. Le bouton « Nouveau modèle »
 * n'apparaît que pour les rôles d'écriture ; l'autorisation réelle reste
 * côté Spring Security (un `403` API est rendu « accès refusé »).
 */
@Component({
  selector: 'app-pattern-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    RouterLinkActive,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  providers: [{ provide: MatPaginatorIntl, useFactory: frenchPaginatorIntl }],
  templateUrl: './pattern-list.html',
  styleUrl: './pattern-list.scss',
})
export class PatternList {
  private readonly api = inject(AlternationApiService);
  private readonly auth = inject(AuthService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly statuses = WORK_STUDY_PATTERN_STATUSES;
  protected readonly types = WORK_STUDY_PATTERN_TYPES;
  protected readonly statusLabel = workStudyPatternStatusLabel;
  protected readonly typeLabel = workStudyPatternTypeLabel;
  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  protected readonly displayedColumns = [
    'code',
    'name',
    'type',
    'status',
    'updatedAt',
    'actions',
  ] as const;

  protected readonly canWrite = this.auth.hasAnyRole([...PATTERN_WRITE_ROLES]);

  protected readonly filters = this.formBuilder.group({
    q: this.formBuilder.control(''),
    status: this.formBuilder.control<WorkStudyPatternStatus | ''>(''),
    type: this.formBuilder.control<WorkStudyPatternType | ''>(''),
  });

  protected readonly state = signal<ListState>({ kind: 'loading' });
  protected readonly sortField = signal<PatternSortField>(DEFAULT_SORT_FIELD);
  protected readonly sortDirection = signal<SortDirection>(DEFAULT_SORT_DIRECTION);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);

  protected readonly rows = computed<WorkStudyPatternResponse[]>(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.page.content : [];
  });
  protected readonly totalElements = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.page.totalElements : 0;
  });
  protected readonly isEmpty = computed(() => {
    const current = this.state();
    return current.kind === 'ready' && current.page.content.length === 0;
  });
  protected readonly errorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'error' ? current.message : null;
  });

  constructor() {
    this.load();
  }

  protected applyFilters(): void {
    this.pageIndex.set(0);
    this.load();
  }

  protected resetFilters(): void {
    this.filters.reset({ q: '', status: '', type: '' });
    this.pageIndex.set(0);
    this.load();
  }

  protected onSortChange(sort: Sort): void {
    const field = PATTERN_SORT_FIELDS.includes(sort.active as PatternSortField)
      ? (sort.active as PatternSortField)
      : DEFAULT_SORT_FIELD;
    this.sortField.set(field);
    this.sortDirection.set(sort.direction === 'asc' ? 'asc' : 'desc');
    this.pageIndex.set(0);
    this.load();
  }

  protected onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    const raw = this.filters.getRawValue();
    this.api
      .listPatterns({
        q: raw.q.trim() || null,
        status: raw.status || null,
        type: raw.type || null,
        sort: `${this.sortField()},${this.sortDirection()}`,
        page: this.pageIndex(),
        size: this.pageSize(),
      })
      .subscribe({
        next: (page) => this.state.set({ kind: 'ready', page }),
        error: (error: unknown) => {
          const view = toAlternationError(error);
          this.state.set(
            view.forbidden ? { kind: 'forbidden' } : { kind: 'error', message: view.message },
          );
        },
      });
  }
}
