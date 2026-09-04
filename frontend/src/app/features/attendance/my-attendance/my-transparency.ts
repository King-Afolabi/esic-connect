import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { AttendanceApiService } from '../attendance-api.service';
import { toAttendanceError } from '../attendance-errors';
import {
  TransparencyEntry,
  actorRoleLabel,
  attendanceChannelLabel,
  transparencyEventLabel,
} from '../attendance.models';
import { attendanceStatusLabel, formatInstantUtc } from '../../sessions/sessions.models';

type JournalState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; entries: TransparencyEntry[]; total: number };

/**
 * Journal de transparence de l'apprenant (EF-ATT-014 ; docs/02 §5.7).
 *
 * <p>Répond à une seule question : qu'est-il arrivé à mes présences, quand,
 * par quel canal, à quel titre et pourquoi. Le serveur bâtit le journal
 * depuis le seul JWT — aucun identifiant n'est transmis, et les auteurs y
 * sont désignés par leur <strong>fonction</strong>, jamais par leur nom.
 */
@Component({
  selector: 'app-my-transparency',
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
  templateUrl: './my-transparency.html',
  styleUrl: './my-attendance-list.scss',
})
export class MyTransparency {
  private readonly api = inject(AttendanceApiService);
  private readonly fb = inject(FormBuilder);

  protected readonly eventLabel = transparencyEventLabel;
  protected readonly actorLabel = actorRoleLabel;
  protected readonly channelLabel = attendanceChannelLabel;
  protected readonly statusLabel = attendanceStatusLabel;
  protected readonly formatInstantUtc = formatInstantUtc;
  protected readonly columns = ['occurredAt', 'event', 'actor', 'session', 'change', 'reason'] as const;

  protected readonly state = signal<JournalState>({ kind: 'loading' });
  protected readonly page = signal(0);
  private readonly size = 25;

  protected readonly filters = this.fb.nonNullable.group({ from: [''], to: [''] });

  protected readonly totalPages = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? Math.max(1, Math.ceil(current.total / this.size)) : 1;
  });

  constructor() {
    effect(() => {
      const page = this.page();
      this.load(page);
    });
  }

  protected applyFilters(): void {
    this.page.set(0);
    this.load(0);
  }

  protected resetFilters(): void {
    this.filters.reset({ from: '', to: '' });
    this.applyFilters();
  }

  protected previous(): void {
    this.page.update((p) => Math.max(0, p - 1));
  }

  protected next(): void {
    this.page.update((p) => Math.min(this.totalPages() - 1, p + 1));
  }

  protected retry(): void {
    this.load(this.page());
  }

  /** Une ligne « avant → après » lisible, ou rien quand il n'y a pas de changement de valeur. */
  protected change(entry: TransparencyEntry): string | null {
    if (!entry.previousStatus && !entry.newStatus) {
      return null;
    }
    const before = entry.previousStatus ? this.statusLabel(entry.previousStatus) : '—';
    const after = entry.newStatus ? this.statusLabel(entry.newStatus) : '—';
    const late =
      entry.newLateMinutes !== null && entry.newLateMinutes !== undefined
        ? ` (${entry.newLateMinutes} min de retard)`
        : '';
    return `${before} → ${after}${late}`;
  }

  private load(page: number): void {
    const { from, to } = this.filters.getRawValue();
    this.state.set({ kind: 'loading' });
    this.api
      .transparencyJournal({
        from: from ? new Date(from).toISOString() : null,
        to: to ? new Date(to).toISOString() : null,
        page,
        size: this.size,
      })
      .subscribe({
        next: (response) =>
          this.state.set({
            kind: 'ready',
            entries: response.content,
            total: response.totalElements,
          }),
        error: (error: unknown) => {
          const view = toAttendanceError(error);
          this.state.set(
            view.forbidden ? { kind: 'forbidden' } : { kind: 'error', message: view.message },
          );
        },
      });
  }
}
