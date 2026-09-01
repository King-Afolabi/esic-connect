import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { NotificationService } from '../../../core/notifications/notification.service';
import { frenchPaginatorIntl } from '../../organization/organization-paginator';
import { PlanningApiService } from '../planning-api.service';
import { toPlanningError } from '../planning-errors';
import {
  PageResponse,
  PlanningJobResponse,
  PlanningRowResponse,
  formatInstant,
  planningActionLabel,
  planningJobStatusLabel,
} from '../planning.models';

type JobState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'not-found' }
  | { kind: 'forbidden' }
  | { kind: 'ready'; job: PlanningJobResponse };

type RowsState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; page: PageResponse<PlanningRowResponse> };

const PAGE_SIZE_OPTIONS = [20, 50, 100] as const;

/**
 * Revue d'une simulation d'import de planning : synthèse du job, tableau
 * des lignes normalisées avec leurs anomalies, puis **publication**
 * (confirmation en ligne) ou **annulation**. La publication est refusée
 * côté serveur tant qu'une ligne est bloquante (`409 PLAN_BLOCKING_ISSUES`).
 */
@Component({
  selector: 'app-planning-import-review',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    MatCardModule,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  providers: [{ provide: MatPaginatorIntl, useFactory: frenchPaginatorIntl }],
  templateUrl: './planning-import-review.html',
  styleUrl: './planning-import-review.scss',
})
export class PlanningImportReview {
  private readonly api = inject(PlanningApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  private readonly jobId = this.route.snapshot.paramMap.get('jobId') ?? '';

  protected readonly statusLabel = planningJobStatusLabel;
  protected readonly actionLabel = planningActionLabel;
  protected readonly formatInstant = formatInstant;
  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  protected readonly rowColumns = ['rowNumber', 'slotKey', 'title', 'window', 'action', 'status'];

  protected readonly jobState = signal<JobState>({ kind: 'loading' });
  protected readonly rowsState = signal<RowsState>({ kind: 'loading' });
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(50);

  /** `true` quand le panneau de confirmation de publication est ouvert. */
  protected readonly confirmingPublish = signal(false);
  protected readonly submitting = signal(false);
  protected readonly actionError = signal<string | null>(null);

  protected readonly job = computed(() => {
    const current = this.jobState();
    return current.kind === 'ready' ? current.job : null;
  });
  protected readonly jobErrorMessage = computed(() => {
    const current = this.jobState();
    return current.kind === 'error' ? current.message : null;
  });
  protected readonly rows = computed<PlanningRowResponse[]>(() => {
    const current = this.rowsState();
    return current.kind === 'ready' ? current.page.content : [];
  });
  protected readonly rowsTotal = computed(() => {
    const current = this.rowsState();
    return current.kind === 'ready' ? current.page.totalElements : 0;
  });
  protected readonly rowsErrorMessage = computed(() => {
    const current = this.rowsState();
    return current.kind === 'error' ? current.message : null;
  });

  protected readonly isPublished = computed(() => this.job()?.status === 'PUBLISHED');
  protected readonly canPublish = computed(() => {
    const job = this.job();
    return job !== null && job.status === 'SIMULATED' && job.confirmable;
  });
  protected readonly canCancel = computed(() => this.job()?.status === 'SIMULATED');

  constructor() {
    this.loadJob();
  }

  protected retry(): void {
    this.loadJob();
  }

  protected onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadRows();
  }

  protected startPublish(): void {
    this.actionError.set(null);
    this.confirmingPublish.set(true);
  }

  protected cancelPublish(): void {
    this.confirmingPublish.set(false);
  }

  protected confirmPublish(): void {
    if (this.submitting()) {
      return;
    }
    this.run(this.api.publish(this.jobId), (message) => {
      this.confirmingPublish.set(false);
      this.notifications.info(message);
      this.loadJob();
    });
  }

  protected cancelJob(): void {
    if (this.submitting()) {
      return;
    }
    this.run(this.api.cancel(this.jobId), () => {
      this.notifications.info('Import annulé.');
      void this.router.navigate(['/planning/import']);
    });
  }

  private run(call: Observable<unknown>, onSuccess: (message: string) => void): void {
    this.submitting.set(true);
    this.actionError.set(null);
    call.subscribe({
      next: (result) => {
        this.submitting.set(false);
        const alreadyPublished =
          typeof result === 'object' && result !== null && 'alreadyPublished' in result
            ? Boolean((result as { alreadyPublished: unknown }).alreadyPublished)
            : false;
        onSuccess(alreadyPublished ? 'Ce planning était déjà publié.' : 'Planning publié.');
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.actionError.set(toPlanningError(error).message);
      },
    });
  }

  private loadJob(): void {
    this.jobState.set({ kind: 'loading' });
    this.confirmingPublish.set(false);
    this.actionError.set(null);
    this.api.getJob(this.jobId).subscribe({
      next: (job) => {
        this.jobState.set({ kind: 'ready', job });
        this.loadRows();
      },
      error: (error: unknown) => {
        const view = toPlanningError(error);
        if (view.notFound) {
          this.jobState.set({ kind: 'not-found' });
          return;
        }
        if (view.forbidden) {
          this.jobState.set({ kind: 'forbidden' });
          return;
        }
        this.jobState.set({ kind: 'error', message: view.message });
      },
    });
  }

  protected loadRows(): void {
    this.rowsState.set({ kind: 'loading' });
    this.api
      .listRows(this.jobId, { sort: 'rowNumber,asc', page: this.pageIndex(), size: this.pageSize() })
      .subscribe({
        next: (page) => this.rowsState.set({ kind: 'ready', page }),
        error: (error: unknown) =>
          this.rowsState.set({ kind: 'error', message: toPlanningError(error).message }),
      });
  }
}
