import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { normalizeHttpError } from '../../../core/models/api-error';
import { AdministrationApiService } from '../administration-api.service';
import { DuplicateGroup, accountStatusLabel } from '../administration.models';

type DuplicateListState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; groups: DuplicateGroup[] };

/**
 * Détection de doublons (EF-USER-005) — `GET /api/v1/users/duplicates`.
 *
 * <p>Lecture seule : le service **signale** des groupes de comptes
 * probablement dupliqués (même nom normalisé, ou même téléphone), il ne
 * propose **aucune fusion** — la suppression d'un doublon reste une
 * action humaine, exceptionnelle et doublement confirmée (docs/02 §9.5).
 * Chaque candidat renvoie vers sa fiche complète pour la décision.
 */
@Component({
  selector: 'app-duplicate-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule],
  templateUrl: './duplicate-list.html',
  styleUrl: './duplicate-list.scss',
})
export class DuplicateList {
  private readonly api = inject(AdministrationApiService);

  protected readonly statusLabel = accountStatusLabel;
  protected readonly state = signal<DuplicateListState>({ kind: 'loading' });

  protected readonly groups = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.groups : [];
  });
  protected readonly isEmpty = computed(
    () => this.state().kind === 'ready' && this.groups().length === 0,
  );
  protected readonly errorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'error' ? current.message : null;
  });

  constructor() {
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    this.api.listDuplicates().subscribe({
      next: (groups) => this.state.set({ kind: 'ready', groups }),
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
