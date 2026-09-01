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

import { RoleContextService } from '../../../core/auth/role-context.service';
import { Role } from '../../../core/models/role';
import { frenchPaginatorIntl } from '../organization-paginator';
import { OrganizationApiService } from '../organization-api.service';
import { toOrganizationError } from '../organization-errors';
import {
  ORGANIZATION_STATUSES,
  OrganizationListQuery,
  OrganizationStatus,
  PageResponse,
  SiteResponse,
  SortDirection,
  formatIsoDate,
  organizationStatusLabel,
} from '../organization.models';

type ListState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; page: PageResponse<SiteResponse> };

/** `SiteController.WRITE_ROLES` — visibilité du bouton de création uniquement. */
const SITE_WRITE_ROLES: readonly Role[] = ['ADMIN', 'SUPER_ADMIN'];
/** Liste blanche de tri du back-end (`SiteService.SORTABLE`). */
const SORT_FIELDS = ['code', 'name', 'createdAt', 'updatedAt'] as const;
const DEFAULT_SORT = { field: 'code', direction: 'asc' as SortDirection };
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

/**
 * Consultation de la liste des **sites** (`GET /api/v1/sites`). Recherche
 * (`q` = code ou nom), filtre `status`, tri (liste blanche du service,
 * sinon repli sur le tri par défaut) et pagination (bornée à 100)
 * reflètent **exactement** ce que l'API accepte.
 *
 * Le bouton « Nouveau site » n'est rendu que pour un contexte de rôle
 * `ADMIN` / `SUPER_ADMIN` : masquage ergonomique, Spring Security reste
 * l'autorité (un `403` de l'API de création serait rendu « accès refusé »
 * par le formulaire cible).
 */
@Component({
  selector: 'app-site-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
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
  templateUrl: './site-list.html',
  styleUrl: './site-list.scss',
})
export class SiteList {
  private readonly api = inject(OrganizationApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly roleContext = inject(RoleContextService);

  protected readonly statuses = ORGANIZATION_STATUSES;
  protected readonly statusLabel = organizationStatusLabel;
  protected readonly formatDate = formatIsoDate;
  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  protected readonly sortFields = SORT_FIELDS;
  protected readonly displayedColumns = ['code', 'name', 'city', 'timeZoneId', 'status', 'actions'];

  protected readonly canCreate = computed(() =>
    this.roleContext.effectiveRoles().some((r) => SITE_WRITE_ROLES.includes(r)),
  );

  protected readonly filters = this.formBuilder.group({
    q: this.formBuilder.control(''),
    status: this.formBuilder.control<OrganizationStatus | ''>(''),
  });

  protected readonly state = signal<ListState>({ kind: 'loading' });
  protected readonly sortField = signal<string>(DEFAULT_SORT.field);
  protected readonly sortDirection = signal<SortDirection>(DEFAULT_SORT.direction);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);

  protected readonly rows = computed<SiteResponse[]>(() => {
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
    const field = (SORT_FIELDS as readonly string[]).includes(sort.active)
      ? sort.active
      : DEFAULT_SORT.field;
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
    const query: OrganizationListQuery = {
      q: raw.q.trim() || null,
      status: raw.status || null,
      sort: `${this.sortField()},${this.sortDirection()}`,
      page: this.pageIndex(),
      size: this.pageSize(),
    };
    this.api.listSites(query).subscribe({
      next: (page) => this.state.set({ kind: 'ready', page }),
      error: (error: unknown) => {
        const view = toOrganizationError(error);
        if (view.forbidden) {
          this.state.set({ kind: 'forbidden' });
          return;
        }
        this.state.set({ kind: 'error', message: view.message });
      },
    });
  }
}
