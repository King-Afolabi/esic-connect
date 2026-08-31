import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { frenchPaginatorIntl } from '../../../alternation/alternation-paginator';
import { RoleContextService } from '../../../../core/auth/role-context.service';
import { StudentImportApiService } from '../student-import-api.service';
import { toStudentImportError } from '../student-import-errors';
import {
  ConfirmationResultResponse,
  ISSUE_SEVERITIES,
  IssueSeverity,
  JobResponse,
  PLANNED_ACTIONS,
  PageResponse,
  PlannedAction,
  ROW_STATUSES,
  RowResponse,
  RowStatus,
  jobStatusLabel,
  plannedActionLabel,
  rowStatusLabel,
  severityLabel,
} from '../student-import.models';

const WRITE_ROLES = ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'] as const;
const PAGE_SIZE_OPTIONS = [20, 50, 100] as const;

type JobState =
  | { kind: 'loading' }
  | { kind: 'forbidden' }
  | { kind: 'not-found' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; job: JobResponse };

type RowsState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; page: PageResponse<RowResponse> };

type ConfirmState =
  | { kind: 'idle' }
  | { kind: 'panel' }
  | { kind: 'running' }
  | { kind: 'done'; result: ConfirmationResultResponse }
  | { kind: 'stale' }
  | { kind: 'error'; message: string };

/**
 * Revue d'une simulation d'import (rapport §13.3) : cartes de synthèse,
 * anomalies globales, table des lignes normalisées (pagination et filtres
 * serveur), puis confirmation ou annulation explicite avec récapitulatif
 * chiffré.
 *
 * Aucune capacité inventée. Un contexte de rôle sans droit d'écriture
 * ferme le panneau de confirmation et masque les actions ; une réponse
 * tardive est alors ignorée (aucune fausse confirmation). Un
 * `409 IMP_STALE_SIMULATION` recharge la synthèse et les lignes et bloque
 * la confirmation jusqu'à revue.
 */
@Component({
  selector: 'app-student-import-review',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  providers: [{ provide: MatPaginatorIntl, useFactory: frenchPaginatorIntl }],
  templateUrl: './student-import-review.html',
  styleUrl: './student-import-review.scss',
})
export class StudentImportReview {
  private readonly api = inject(StudentImportApiService);
  private readonly roleContext = inject(RoleContextService);
  private readonly publicId = inject(ActivatedRoute).snapshot.paramMap.get('publicId') ?? '';

  protected readonly rowStatuses = ROW_STATUSES;
  protected readonly severities = ISSUE_SEVERITIES;
  protected readonly plannedActions = PLANNED_ACTIONS;
  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  protected readonly plannedActionLabel = plannedActionLabel;
  protected readonly rowStatusLabel = rowStatusLabel;
  protected readonly severityLabel = severityLabel;
  protected readonly jobStatusLabel = jobStatusLabel;
  protected readonly displayedColumns = [
    'rowNumber',
    'name',
    'email',
    'classCode',
    'plannedAction',
    'rowStatus',
    'issues',
  ] as const;

  protected readonly canWrite = computed(() =>
    this.roleContext.effectiveRoles().some((r) => (WRITE_ROLES as readonly string[]).includes(r)),
  );

  protected readonly jobState = signal<JobState>({ kind: 'loading' });
  protected readonly rowsState = signal<RowsState>({ kind: 'loading' });
  protected readonly confirmState = signal<ConfirmState>({ kind: 'idle' });
  protected readonly expandedRow = signal<number | null>(null);

  protected readonly filters = inject(NonNullableFormBuilder).group({
    rowStatus: '' as RowStatus | '',
    severity: '' as IssueSeverity | '',
    action: '' as PlannedAction | '',
  });
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(50);

  protected readonly job = computed<JobResponse | null>(() => {
    const state = this.jobState();
    return state.kind === 'ready' ? state.job : null;
  });
  protected readonly rows = computed<RowResponse[]>(() => {
    const state = this.rowsState();
    return state.kind === 'ready' ? state.page.content : [];
  });
  protected readonly totalRows = computed(() => {
    const state = this.rowsState();
    return state.kind === 'ready' ? state.page.totalElements : 0;
  });
  protected readonly confirmable = computed(() => {
    const j = this.job();
    return !!j && j.status === 'SIMULATED' && j.confirmable && this.confirmState().kind !== 'stale';
  });

  constructor() {
    this.loadJob();
    this.loadRows();
  }

  protected retryJob(): void {
    this.loadJob();
  }

  protected retryRows(): void {
    this.loadRows();
  }

  protected applyFilters(): void {
    this.pageIndex.set(0);
    this.loadRows();
  }

  protected resetFilters(): void {
    this.filters.reset({ rowStatus: '', severity: '', action: '' });
    this.pageIndex.set(0);
    this.loadRows();
  }

  protected onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadRows();
  }

  protected toggleRow(rowNumber: number): void {
    this.expandedRow.set(this.expandedRow() === rowNumber ? null : rowNumber);
  }

  /** Prédicat `matRowDef` : afficher la ligne de détail des anomalies. */
  protected readonly isExpanded = (_index: number, row: RowResponse): boolean =>
    this.expandedRow() === row.rowNumber && row.issues.length > 0;

  protected openConfirm(): void {
    if (this.confirmable() && this.canWrite()) {
      this.confirmState.set({ kind: 'panel' });
    }
  }

  protected closeConfirm(): void {
    this.confirmState.set({ kind: 'idle' });
  }

  protected confirm(): void {
    if (this.confirmState().kind !== 'panel' || !this.confirmable() || !this.canWrite()) {
      return;
    }
    this.confirmState.set({ kind: 'running' });
    this.api.confirm(this.publicId).subscribe({
      next: (result) => {
        if (!this.canWrite()) {
          return; // droit perdu pendant l'appel : réponse ignorée
        }
        this.confirmState.set({ kind: 'done', result });
        this.loadJob();
      },
      error: (error: unknown) => {
        if (!this.canWrite()) {
          return;
        }
        const view = toStudentImportError(error);
        if (view.stale) {
          this.confirmState.set({ kind: 'stale' });
          this.loadJob();
          this.loadRows();
          return;
        }
        this.confirmState.set({
          kind: 'error',
          message: view.expired
            ? 'Cette simulation a expiré. Relancez un import.'
            : view.forbidden
              ? "Vous n'êtes pas autorisé à confirmer cet import."
              : view.message,
        });
      },
    });
  }

  protected cancel(): void {
    if (!this.canWrite() || this.job()?.status !== 'SIMULATED') {
      return;
    }
    this.confirmState.set({ kind: 'running' });
    this.api.cancel(this.publicId).subscribe({
      next: () => {
        if (!this.canWrite()) {
          return;
        }
        this.confirmState.set({ kind: 'idle' });
        this.loadJob();
      },
      error: (error: unknown) => {
        if (!this.canWrite()) {
          return;
        }
        this.confirmState.set({ kind: 'error', message: toStudentImportError(error).message });
      },
    });
  }

  private loadJob(): void {
    this.jobState.set({ kind: 'loading' });
    this.api.getJob(this.publicId).subscribe({
      next: (job) => this.jobState.set({ kind: 'ready', job }),
      error: (error: unknown) => {
        const view = toStudentImportError(error);
        if (view.forbidden) {
          this.jobState.set({ kind: 'forbidden' });
        } else if (view.notFound) {
          this.jobState.set({ kind: 'not-found' });
        } else {
          this.jobState.set({ kind: 'error', message: view.message });
        }
      },
    });
  }

  private loadRows(): void {
    this.rowsState.set({ kind: 'loading' });
    const raw = this.filters.getRawValue();
    this.api
      .listRows(this.publicId, {
        rowStatus: raw.rowStatus || null,
        severity: raw.severity || null,
        action: raw.action || null,
        sort: 'rowNumber,asc',
        page: this.pageIndex(),
        size: this.pageSize(),
      })
      .subscribe({
        next: (page) => this.rowsState.set({ kind: 'ready', page }),
        error: (error: unknown) =>
          this.rowsState.set({ kind: 'error', message: toStudentImportError(error).message }),
      });
  }
}
