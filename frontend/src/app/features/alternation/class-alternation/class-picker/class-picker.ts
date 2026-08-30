import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { AcademicApiService } from '../../../academic/academic-api.service';
import {
  ClassGroupResponse,
  PageResponse as AcademicPageResponse,
} from '../../../academic/academic.models';
import { toAlternationError } from '../../alternation-errors';
import { frenchPaginatorIntl } from '../../alternation-paginator';

type PickerState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; page: AcademicPageResponse<ClassGroupResponse> };

const PAGE_SIZE_OPTIONS = [10, 20, 50] as const;

/**
 * Sélection d'une classe pour la gestion de son rythme d'alternance.
 *
 * S'appuie sur `GET /api/v1/class-groups` (module `academic`,
 * `AcademicWeb.READ_ROLES` — mêmes 4 rôles que l'alternance ; un
 * `PEDAGOGICAL_MANAGER` est filtré à son périmètre côté serveur). On ne
 * récupère jamais une liste globale pour la filtrer localement.
 */
@Component({
  selector: 'app-class-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    RouterLinkActive,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  providers: [{ provide: MatPaginatorIntl, useFactory: frenchPaginatorIntl }],
  templateUrl: './class-picker.html',
  styleUrl: './class-picker.scss',
})
export class ClassPicker {
  private readonly academic = inject(AcademicApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  protected readonly displayedColumns = ['code', 'name', 'status', 'actions'] as const;

  protected readonly filters = this.formBuilder.group({
    q: this.formBuilder.control(''),
  });

  protected readonly state = signal<PickerState>({ kind: 'loading' });
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);

  protected readonly rows = computed<ClassGroupResponse[]>(() => {
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
    this.filters.reset({ q: '' });
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
    this.academic
      .listClassGroups({
        q: this.filters.getRawValue().q.trim() || null,
        sort: 'code,asc',
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
