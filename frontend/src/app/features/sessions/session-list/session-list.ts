import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { RoleContextService } from '../../../core/auth/role-context.service';
import { frenchPaginatorIntl } from '../../alternation/alternation-paginator';
import { SessionsApiService } from '../sessions-api.service';
import { toSessionError } from '../session-errors';
import {
  CourseSessionResponse,
  PageResponse,
  SESSION_CREATE_ROLES,
  SESSION_SORT_FIELDS,
  SESSION_STATUSES,
  SessionSortField,
  SessionStatus,
  SortDirection,
  classCodes,
  formatInstantUtc,
  sessionStatusLabel,
  teacherName,
} from '../sessions.models';

type ListState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; page: PageResponse<CourseSessionResponse> };

const DEFAULT_SORT_FIELD: SessionSortField = 'startsAt';
const DEFAULT_SORT_DIRECTION: SortDirection = 'desc';
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

/**
 * Liste des séances — `GET /api/v1/sessions`.
 *
 * Filtre `status`, tri sur la liste blanche `startsAt|createdAt`,
 * pagination bornée à 100 : strictement ce que l'API expose. Un
 * `TEACHER` ne voit que ses séances, un `PEDAGOGICAL_MANAGER` que son
 * périmètre — décidé **côté serveur**. Le bouton « Nouvelle séance »
 * n'apparaît que si le **contexte de rôle actif**
 * ({@link RoleContextService.effectiveRoles}) contient un rôle de
 * création : sélectionner un contexte plus restreint le masque
 * immédiatement, sans jamais élargir le JWT. L'autorisation réelle reste
 * côté Spring Security (un `403` API est rendu « accès refusé »).
 */
@Component({
  selector: 'app-session-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  providers: [{ provide: MatPaginatorIntl, useFactory: frenchPaginatorIntl }],
  templateUrl: './session-list.html',
  styleUrl: './session-list.scss',
})
export class SessionList {
  private readonly api = inject(SessionsApiService);
  private readonly roleContext = inject(RoleContextService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly statuses = SESSION_STATUSES;
  protected readonly statusLabel = sessionStatusLabel;
  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  protected readonly formatInstantUtc = formatInstantUtc;
  protected readonly classCodes = classCodes;
  protected readonly teacherName = teacherName;
  protected readonly displayedColumns = [
    'startsAt',
    'teacher',
    'classes',
    'status',
    'actions',
  ] as const;

  protected readonly canCreate = computed(() =>
    this.roleContext
      .effectiveRoles()
      .some((role) => (SESSION_CREATE_ROLES as readonly string[]).includes(role)),
  );

  protected readonly filters = this.formBuilder.group({
    status: this.formBuilder.control<SessionStatus | ''>(''),
  });

  protected readonly state = signal<ListState>({ kind: 'loading' });
  protected readonly sortField = signal<SessionSortField>(DEFAULT_SORT_FIELD);
  protected readonly sortDirection = signal<SortDirection>(DEFAULT_SORT_DIRECTION);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);

  protected readonly rows = computed<CourseSessionResponse[]>(() => {
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
    this.filters.reset({ status: '' });
    this.pageIndex.set(0);
    this.load();
  }

  protected onSortChange(sort: Sort): void {
    const field = SESSION_SORT_FIELDS.includes(sort.active as SessionSortField)
      ? (sort.active as SessionSortField)
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
    this.api
      .listSessions({
        status: this.filters.getRawValue().status || null,
        sort: `${this.sortField()},${this.sortDirection()}`,
        page: this.pageIndex(),
        size: this.pageSize(),
      })
      .subscribe({
        next: (page) => this.state.set({ kind: 'ready', page }),
        error: (error: unknown) => {
          const view = toSessionError(error);
          this.state.set(
            view.forbidden ? { kind: 'forbidden' } : { kind: 'error', message: view.message },
          );
        },
      });
  }
}
