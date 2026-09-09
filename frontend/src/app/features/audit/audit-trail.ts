import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';

import { NotificationService } from '../../core/notifications/notification.service';
import { triggerCsvDownload } from '../attendance/attendance-api.service';
import { AuditApiService } from './audit-api.service';
import {
  AUDIT_EXPORT_FORMATS,
  AuditExportFormat,
  AuditFacets,
  AuditPage,
  AuditQuery,
} from './audit.models';

type State =
  | { kind: 'loading' }
  | { kind: 'forbidden' }
  | { kind: 'error' }
  | { kind: 'ready'; data: AuditPage };

/**
 * Consultation et export de la piste d'audit (EF-AUD-002 ;
 * docs/02 §23.4).
 *
 * L'écran ne propose **aucune action d'écriture** : la piste d'audit se
 * lit, se filtre et s'exporte, elle ne se corrige pas. Les listes de
 * filtres sont peuplées par les valeurs réellement présentes en base
 * (`/facets`) plutôt que par une énumération recopiée ici, qui se
 * périmerait au premier événement ajouté.
 */
@Component({
  selector: 'app-audit-trail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './audit-trail.html',
  styleUrl: './audit-trail.scss',
})
export class AuditTrail {
  private readonly api = inject(AuditApiService);
  private readonly fb = inject(FormBuilder);
  private readonly notifications = inject(NotificationService);

  protected readonly exportFormats = AUDIT_EXPORT_FORMATS;
  protected readonly state = signal<State>({ kind: 'loading' });
  protected readonly facets = signal<AuditFacets>({
    actions: [],
    categories: [],
    resourceTypes: [],
    results: [],
  });
  protected readonly exporting = signal(false);
  protected readonly page = signal(0);
  protected readonly size = 20;

  protected readonly filters = this.fb.nonNullable.group({
    from: '',
    to: '',
    action: '',
    category: '',
    resourceType: '',
    result: '',
    correlation: '',
  });

  constructor() {
    this.api.facets().subscribe({
      next: (facets) => this.facets.set(facets),
      // Sans facettes, l'écran reste utilisable : les filtres deviennent
      // simplement vides. Inutile d'alerter pour cela.
      error: () => undefined,
    });
    this.load();
  }

  protected apply(): void {
    this.page.set(0);
    this.load();
  }

  protected reset(): void {
    this.filters.reset({
      from: '',
      to: '',
      action: '',
      category: '',
      resourceType: '',
      result: '',
      correlation: '',
    });
    this.apply();
  }

  protected nextPage(): void {
    const s = this.state();
    if (s.kind === 'ready' && this.page() + 1 < s.data.totalPages) {
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

  protected exportAs(format: AuditExportFormat): void {
    if (this.exporting()) {
      return;
    }
    this.exporting.set(true);
    this.api.export(this.query(), format).subscribe({
      next: (response) => {
        this.exporting.set(false);
        triggerCsvDownload(response, `audit.${format}`);
      },
      error: () => {
        this.exporting.set(false);
        this.notifications.error("L'export de la piste d'audit a échoué.");
      },
    });
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    this.api.list(this.query(), this.page(), this.size).subscribe({
      next: (data) => this.state.set({ kind: 'ready', data }),
      error: (error: unknown) => {
        const status = (error as { status?: number } | null)?.status;
        this.state.set({ kind: status === 403 ? 'forbidden' : 'error' });
      },
    });
  }

  private query(): AuditQuery {
    const raw = this.filters.getRawValue();
    return {
      from: isoStart(raw.from),
      to: isoEnd(raw.to),
      action: raw.action || null,
      category: raw.category || null,
      resourceType: raw.resourceType || null,
      result: raw.result || null,
      actor: null,
      resource: null,
      correlation: raw.correlation.trim() || null,
    };
  }
}

/** `aaaa-mm-jj` → début de journée en UTC. */
function isoStart(day: string): string | null {
  return day ? new Date(`${day}T00:00:00Z`).toISOString() : null;
}

/**
 * `aaaa-mm-jj` → **fin** de journée en UTC.
 *
 * Sans cela, filtrer « jusqu'au 5 septembre » exclurait tout ce qui
 * s'est passé ce jour-là après minuit — c'est-à-dire presque tout.
 */
function isoEnd(day: string): string | null {
  return day ? new Date(`${day}T23:59:59.999Z`).toISOString() : null;
}
