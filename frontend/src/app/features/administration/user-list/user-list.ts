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
import { ROLES, roleLabel } from '../../../core/models/role';
import { AdministrationApiService } from '../administration-api.service';
import {
  ACCOUNT_STATUSES,
  AccountStatus,
  PageResponse,
  SortDirection,
  USER_SORT_FIELDS,
  UserRoleFilter,
  UserSortField,
  UserSummaryResponse,
  accountStatusLabel,
} from '../administration.models';

/** État de la consultation de la liste. */
type ListState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; page: PageResponse<UserSummaryResponse> };

const DEFAULT_SORT_FIELD: UserSortField = 'createdAt';
const DEFAULT_SORT_DIRECTION: SortDirection = 'desc';
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

/**
 * Liste des comptes utilisateurs — `GET /api/v1/users`.
 *
 * Recherche, filtres, tri et pagination reflètent **exactement** ce que
 * l'API accepte : recherche `q` (sous-chaîne email / prénom / nom),
 * filtre `status` (`AccountStatus`), filtre `role` (affectation active,
 * `RoleCode`), tri sur `createdAt` / `lastLoginAt` / `email` / `lastName`,
 * pagination bornée à 100. Aucune capacité inventée. Lecture seule :
 * aucune action de cycle de vie ni de gestion de rôle n'est déclenchée.
 *
 * Le contrôle d'accès reste côté Spring Security : un `403` renvoyé par
 * l'API est rendu comme un état « accès refusé » explicite, même si le
 * `roleGuard` de la route a normalement déjà filtré l'utilisateur.
 */
@Component({
  selector: 'app-user-list',
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
  templateUrl: './user-list.html',
  styleUrl: './user-list.scss',
})
export class UserList {
  private readonly api = inject(AdministrationApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly statuses = ACCOUNT_STATUSES;
  protected readonly roleOptions = ROLES;
  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  protected readonly statusLabel = accountStatusLabel;
  protected readonly roleLabel = roleLabel;

  /** Libellés des rôles actifs, séparés par une virgule ; `—` si aucun. */
  protected rolesLabel(codes: readonly string[]): string {
    return codes.length ? codes.map(roleLabel).join(', ') : '—';
  }
  protected readonly displayedColumns = [
    'email',
    'lastName',
    'roles',
    'status',
    'createdAt',
    'lastLoginAt',
    'actions',
  ] as const;

  /** Filtres appliqués (recherche + statut + rôle). */
  protected readonly filters = this.formBuilder.group({
    q: this.formBuilder.control(''),
    status: this.formBuilder.control<AccountStatus | ''>(''),
    role: this.formBuilder.control<UserRoleFilter | ''>(''),
  });

  protected readonly state = signal<ListState>({ kind: 'loading' });

  protected readonly sortField = signal<UserSortField>(DEFAULT_SORT_FIELD);
  protected readonly sortDirection = signal<SortDirection>(DEFAULT_SORT_DIRECTION);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);

  protected readonly rows = computed<UserSummaryResponse[]>(() => {
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
    this.filters.reset({ q: '', status: '', role: '' });
    this.pageIndex.set(0);
    this.load();
  }

  protected onSortChange(sort: Sort): void {
    const field = USER_SORT_FIELDS.includes(sort.active as UserSortField)
      ? (sort.active as UserSortField)
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
      .listUsers({
        q: raw.q.trim() || null,
        status: raw.status || null,
        role: raw.role || null,
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
