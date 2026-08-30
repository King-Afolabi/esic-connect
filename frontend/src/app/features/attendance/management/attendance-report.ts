import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { NotificationService } from '../../../core/notifications/notification.service';
import { AttendanceApiService, triggerCsvDownload } from '../attendance-api.service';
import { toAttendanceError } from '../attendance-errors';
import {
  ClassReportRow,
  PageResponse,
  ReportKind,
  ReportQuery,
  SessionReportRow,
  StudentReportRow,
  percent,
} from '../attendance.models';
import { formatInstantUtc } from '../../sessions/sessions.models';

type Row = SessionReportRow | ClassReportRow | StudentReportRow;
type State =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; rows: Row[]; total: number };

/**
 * Rapport d'assiduité tabulaire (V10). `data.kind` (route) sélectionne
 * le rapport : `sessions` | `classes` | `students`. Export CSV en `blob`
 * remis par un téléchargement programmatique — jamais de secret ni de
 * filtre dans l'URL de navigation Angular. Aucun stockage navigateur.
 */
@Component({
  selector: 'app-attendance-report',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './attendance-report.html',
  styleUrl: '../my-attendance/my-attendance-list.scss',
})
export class AttendanceReport {
  private readonly api = inject(AttendanceApiService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly notifications = inject(NotificationService);

  protected readonly kind = (this.route.snapshot.data['kind'] as ReportKind) ?? 'sessions';
  protected readonly percent = percent;
  protected readonly formatInstantUtc = formatInstantUtc;

  protected readonly state = signal<State>({ kind: 'loading' });
  protected readonly page = signal(0);
  protected readonly exporting = signal(false);
  private readonly size = 20;

  protected readonly filters = this.fb.nonNullable.group({
    from: [''],
    to: [''],
    classGroup: [''],
    studentProfile: [''],
  });

  protected readonly title = computed(
    () =>
      ({ sessions: 'Rapport par séance', classes: 'Rapport par classe', students: 'Rapport par apprenant' })[
        this.kind
      ],
  );
  protected readonly columns = computed(() =>
    this.kind === 'sessions'
      ? (['label', 'when', 'expected', 'present', 'late', 'absent', 'rate'] as const)
      : this.kind === 'classes'
        ? (['label', 'count', 'expected', 'present', 'absent', 'excused', 'unknown', 'rate'] as const)
        : (['label', 'classCode', 'expected', 'present', 'absent', 'excused', 'unknown', 'rate'] as const),
  );

  protected readonly rows = computed(() => {
    const s = this.state();
    return s.kind === 'ready' ? s.rows : [];
  });
  protected readonly total = computed(() => {
    const s = this.state();
    return s.kind === 'ready' ? s.total : 0;
  });
  protected readonly errorMessage = computed(() => {
    const s = this.state();
    return s.kind === 'error' ? s.message : null;
  });

  constructor() {
    this.load();
  }

  protected apply(): void {
    this.page.set(0);
    this.load();
  }
  protected reset(): void {
    this.filters.reset({ from: '', to: '', classGroup: '', studentProfile: '' });
    this.page.set(0);
    this.load();
  }
  protected retry(): void {
    this.load();
  }
  protected nextPage(): void {
    if ((this.page() + 1) * this.size < this.total()) {
      this.page.update((p) => p + 1);
      this.load();
    }
  }
  protected previousPage(): void {
    if (this.page() > 0) {
      this.page.update((p) => p - 1);
      this.load();
    }
  }

  protected exportCsv(): void {
    if (this.exporting()) {
      return;
    }
    this.exporting.set(true);
    this.api.exportReport(this.kind, this.query(false)).subscribe({
      next: (response) => {
        this.exporting.set(false);
        triggerCsvDownload(response, `assiduite-${this.kind}.csv`);
      },
      error: (error: unknown) => {
        this.exporting.set(false);
        this.notifications.error(toAttendanceError(error).message);
      },
    });
  }

  // ------------------------------------------------------------------

  private query(paged: boolean): ReportQuery {
    const raw = this.filters.getRawValue();
    return {
      from: isoStart(raw.from),
      to: isoStart(raw.to),
      classGroup: raw.classGroup.trim() || null,
      studentProfile: this.kind === 'students' ? raw.studentProfile.trim() || null : null,
      ...(paged ? { page: this.page(), size: this.size } : {}),
    };
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    const q = this.query(true);
    const call: Observable<PageResponse<Row>> = (
      this.kind === 'sessions'
        ? this.api.sessionsReport(q)
        : this.kind === 'classes'
          ? this.api.classesReport(q)
          : this.api.studentsReport(q)
    ) as unknown as Observable<PageResponse<Row>>;
    call.subscribe({
      next: (result: PageResponse<Row>) =>
        this.state.set({ kind: 'ready', rows: result.content, total: result.totalElements }),
      error: (error: unknown) => {
        const view = toAttendanceError(error);
        this.state.set(view.forbidden ? { kind: 'forbidden' } : { kind: 'error', message: view.message });
      },
    });
  }
}

function isoStart(value: string): string | null {
  if (!value) {
    return null;
  }
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}
