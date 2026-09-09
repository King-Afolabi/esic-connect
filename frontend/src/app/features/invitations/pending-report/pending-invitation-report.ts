import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { NotificationService } from '../../../core/notifications/notification.service';
import { triggerCsvDownload } from '../../attendance/attendance-api.service';
import { InvitationsApiService } from '../invitations-api.service';
import {
  PENDING_INVITATION_EXPORT_FORMATS,
  PendingInvitationExportFormat,
  PendingInvitationRow,
} from '../invitations.models';

type State =
  | { kind: 'loading' }
  | { kind: 'forbidden' }
  | { kind: 'error' }
  | { kind: 'ready'; rows: PendingInvitationRow[] };

/**
 * Rapport des invitations non activées (EF-REP-010 ; docs/02 §22.3).
 *
 * L'adresse électronique n'apparaît que sous forme masquée : ce rapport
 * sert à relancer, pas à produire un annuaire exportable. La correction
 * d'une adresse se fait depuis l'écran de suivi des invitations.
 */
@Component({
  selector: 'app-pending-invitation-report',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, MatButtonModule, MatIconModule, MatProgressBarModule],
  templateUrl: './pending-invitation-report.html',
  styleUrl: './pending-invitation-report.scss',
})
export class PendingInvitationReport {
  private readonly api = inject(InvitationsApiService);
  private readonly notifications = inject(NotificationService);

  protected readonly exportFormats = PENDING_INVITATION_EXPORT_FORMATS;
  protected readonly state = signal<State>({ kind: 'loading' });
  protected readonly exporting = signal(false);

  protected readonly expiredCount = computed(() => {
    const s = this.state();
    return s.kind === 'ready' ? s.rows.filter((r) => r.expired).length : 0;
  });

  constructor() {
    this.load();
  }

  protected exportAs(format: PendingInvitationExportFormat): void {
    if (this.exporting()) {
      return;
    }
    this.exporting.set(true);
    this.api.exportPendingInvitations(format).subscribe({
      next: (response) => {
        this.exporting.set(false);
        triggerCsvDownload(response, `invitations-non-activees.${format}`);
      },
      error: () => {
        this.exporting.set(false);
        this.notifications.error("L'export du rapport a échoué.");
      },
    });
  }

  protected reload(): void {
    this.load();
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    this.api.listPendingInvitations().subscribe({
      next: (rows) => this.state.set({ kind: 'ready', rows }),
      error: (error: unknown) => {
        const status = (error as { status?: number } | null)?.status;
        this.state.set({ kind: status === 403 ? 'forbidden' : 'error' });
      },
    });
  }
}
