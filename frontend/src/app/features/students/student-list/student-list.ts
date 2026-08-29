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
import { RouterLink } from '@angular/router';

import { normalizeHttpError } from '../../../core/models/api-error';
import { StudentsApiService } from '../students-api.service';
import {
  PageResponse,
  STUDENT_PROFILE_SORT_FIELDS,
  STUDENT_PROFILE_STATUSES,
  SortDirection,
  StudentProfileResponse,
  StudentProfileSortField,
  StudentProfileStatus,
  studentProfileStatusLabel,
} from '../students.models';

/** État de la consultation de la liste. */
type ListState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; page: PageResponse<StudentProfileResponse> };

const DEFAULT_SORT_FIELD: StudentProfileSortField = 'createdAt';
const DEFAULT_SORT_DIRECTION: SortDirection = 'desc';
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

/**
 * Liste des profils apprenants — `GET /api/v1/student-profiles`.
 *
 * Recherche, filtre, tri et pagination reflètent **exactement** ce que
 * l'API accepte : recherche `q` sur le seul numéro étudiant, filtre
 * `status`, tri sur `studentNumber` / `createdAt`, pagination bornée à
 * 100. Aucune capacité inventée.
 *
 * Le contrôle d'accès reste côté Spring Security : un `403` renvoyé par
 * l'API est rendu comme un état « accès refusé » explicite, même si le
 * `roleGuard` de la route a normalement déjà filtré l'utilisateur.
 */
@Component({
  selector: 'app-student-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
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
  templateUrl: './student-list.html',
  styleUrl: './student-list.scss',
})
export class StudentList {
  private readonly api = inject(StudentsApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly statuses = STUDENT_PROFILE_STATUSES;
  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  protected readonly statusLabel = studentProfileStatusLabel;
  protected readonly displayedColumns = [
    'studentNumber',
    'workStudy',
    'companyName',
    'status',
    'createdAt',
    'actions',
  ] as const;

  /** Filtres appliqués (numéro étudiant + statut). */
  protected readonly filters = this.formBuilder.group({
    q: this.formBuilder.control(''),
    status: this.formBuilder.control<StudentProfileStatus | ''>(''),
  });

  protected readonly state = signal<ListState>({ kind: 'loading' });

  protected readonly sortField = signal<StudentProfileSortField>(DEFAULT_SORT_FIELD);
  protected readonly sortDirection = signal<SortDirection>(DEFAULT_SORT_DIRECTION);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);

  protected readonly rows = computed<StudentProfileResponse[]>(() => {
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
    this.filters.reset({ q: '', status: '' });
    this.pageIndex.set(0);
    this.load();
  }

  protected onSortChange(sort: Sort): void {
    const field = STUDENT_PROFILE_SORT_FIELDS.includes(sort.active as StudentProfileSortField)
      ? (sort.active as StudentProfileSortField)
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
      .listProfiles({
        q: raw.q.trim() || null,
        status: raw.status || null,
        sort: `${this.sortField()},${this.sortDirection()}`,
        page: this.pageIndex(),
        size: this.pageSize(),
      })
      .subscribe({
        next: (page) => this.state.set({ kind: 'ready', page }),
        error: (error: unknown) => {
          const normalized = normalizeHttpError(error);
          if (normalized.status === 403) {
            this.state.set({ kind: 'forbidden' });
            return;
          }
          this.state.set({ kind: 'error', message: normalized.message });
        },
      });
  }
}

/** Libellés français du paginateur Material (docs/02 §38.7). */
export function frenchPaginatorIntl(): MatPaginatorIntl {
  const intl = new MatPaginatorIntl();
  intl.itemsPerPageLabel = 'Éléments par page';
  intl.nextPageLabel = 'Page suivante';
  intl.previousPageLabel = 'Page précédente';
  intl.firstPageLabel = 'Première page';
  intl.lastPageLabel = 'Dernière page';
  intl.getRangeLabel = (page: number, pageSize: number, length: number): string => {
    if (length === 0 || pageSize === 0) {
      return `0 sur ${length}`;
    }
    const start = page * pageSize + 1;
    const end = Math.min(start + pageSize - 1, length);
    return `${start} – ${end} sur ${length}`;
  };
  return intl;
}
