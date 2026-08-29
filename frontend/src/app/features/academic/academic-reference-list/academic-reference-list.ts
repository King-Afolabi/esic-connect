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
import { ActivatedRoute, RouterLink, RouterLinkActive } from '@angular/router';

import { normalizeHttpError } from '../../../core/models/api-error';
import { AcademicApiService } from '../academic-api.service';
import { ACADEMIC_LIST_TABS, ACADEMIC_RESOURCES, AcademicResourceConfig } from '../academic.config';
import {
  ACADEMIC_STATUSES,
  AcademicListQuery,
  AcademicRecord,
  AcademicResourceSlug,
  AcademicStatus,
  PageResponse,
  SortDirection,
  academicStatusLabel,
} from '../academic.models';

type ListState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; page: PageResponse<AcademicRecord> };

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

/**
 * Consultation d'une liste du référentiel académique — pilotée par le
 * `data.resource` de la route (`academic-years`, `programs`,
 * `promotions`, `class-groups`).
 *
 * Recherche (`q` = code ou nom), filtre `status`, tri (liste blanche du
 * service, sinon repli sur le tri par défaut) et pagination (bornée à
 * 100) reflètent **exactement** ce que l'API accepte. Aucune capacité
 * inventée, lecture seule.
 *
 * Un `403` renvoyé par l'API est rendu comme un état « accès refusé »
 * explicite : le contrôle d'accès reste côté Spring Security, même si le
 * `roleGuard` de la route a déjà filtré l'utilisateur.
 */
@Component({
  selector: 'app-academic-reference-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
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
  templateUrl: './academic-reference-list.html',
  styleUrl: './academic-reference-list.scss',
})
export class AcademicReferenceList {
  private readonly api = inject(AcademicApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly route = inject(ActivatedRoute);

  protected readonly resource = this.route.snapshot.data['resource'] as AcademicResourceSlug;
  protected readonly config: AcademicResourceConfig = ACADEMIC_RESOURCES[this.resource];
  protected readonly tabs = ACADEMIC_LIST_TABS;
  protected readonly statuses = ACADEMIC_STATUSES;
  protected readonly statusLabel = academicStatusLabel;
  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  protected readonly displayedColumns = [
    ...this.config.columns.map((column) => column.key),
    'actions',
  ];

  private readonly defaultSort = parseSort(this.config.defaultSort);

  protected readonly filters = this.formBuilder.group({
    q: this.formBuilder.control(''),
    status: this.formBuilder.control<AcademicStatus | ''>(''),
  });

  protected readonly state = signal<ListState>({ kind: 'loading' });
  protected readonly sortField = signal<string>(this.defaultSort.field);
  protected readonly sortDirection = signal<SortDirection>(this.defaultSort.direction);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);

  protected readonly rows = computed<AcademicRecord[]>(() => {
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
    const field = this.config.sortFields.includes(sort.active) ? sort.active : this.defaultSort.field;
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
    const loader = this.config.loadList;
    if (!loader) {
      this.state.set({ kind: 'error', message: normalizeHttpError(null).message });
      return;
    }
    this.state.set({ kind: 'loading' });
    const raw = this.filters.getRawValue();
    const query: AcademicListQuery = {
      q: raw.q.trim() || null,
      status: raw.status || null,
      sort: `${this.sortField()},${this.sortDirection()}`,
      page: this.pageIndex(),
      size: this.pageSize(),
    };
    loader(this.api, query).subscribe({
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

function parseSort(value: string): { field: string; direction: SortDirection } {
  const [field, direction] = value.split(',', 2);
  return { field: field.trim(), direction: direction?.trim() === 'desc' ? 'desc' : 'asc' };
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
