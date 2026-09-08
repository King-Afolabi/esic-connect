import { DatePipe, KeyValuePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { normalizeHttpError } from '../../../core/models/api-error';
import { AdministrationApiService } from '../administration-api.service';
import {
  DuplicateComparisonResponse,
  DuplicateGroup,
  accountStatusLabel,
  duplicateAssessmentLabel,
  duplicateDependencyLabel,
  duplicateFieldLabel,
} from '../administration.models';

type DuplicateListState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; groups: DuplicateGroup[] };

type ComparisonState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'error'; message: string; correlationId: string | null }
  | { kind: 'ready'; result: DuplicateComparisonResponse };

/**
 * Détection de doublons (EF-USER-005) — `GET /api/v1/users/duplicates` —
 * et **comparaison contrôlée en lecture seule** de deux d'entre eux
 * (ANO-USER-001) — `POST /api/v1/users/duplicates/compare`.
 *
 * <p>Le parcours de résolution s'arrête à la **simulation** : sélection
 * d'exactement deux comptes, comparaison côte à côte, verdict informatif
 * (« Fusion potentiellement sûre » / « Revue manuelle requise » / « Fusion
 * impossible »). **Aucune fusion n'est réalisée** — aucune route de
 * fusion n'existe côté serveur, et la suppression d'un doublon reste une
 * action humaine, exceptionnelle et doublement confirmée (docs/02 §9.5).
 */
@Component({
  selector: 'app-duplicate-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    KeyValuePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './duplicate-list.html',
  styleUrl: './duplicate-list.scss',
})
export class DuplicateList {
  private readonly api = inject(AdministrationApiService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly statusLabel = accountStatusLabel;
  protected readonly assessmentLabel = duplicateAssessmentLabel;
  protected readonly fieldLabel = duplicateFieldLabel;
  protected readonly dependencyLabel = duplicateDependencyLabel;

  protected readonly state = signal<DuplicateListState>({ kind: 'loading' });

  /** Identifiants des comptes cochés — au plus deux (parcours ANO-USER-001). */
  protected readonly selected = signal<readonly string[]>([]);
  protected readonly comparison = signal<ComparisonState>({ kind: 'idle' });

  /** Dernier déclencheur « Comparer » — pour restaurer le focus à la fermeture. */
  private lastCompareTrigger: HTMLElement | null = null;

  private focusCloseButton(): void {
    queueMicrotask(() => {
      const button = this.host.nativeElement.querySelector<HTMLButtonElement>(
        '.duplicates__close',
      );
      button?.focus();
    });
  }

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

  protected readonly selectedCount = computed(() => this.selected().length);
  protected readonly canCompare = computed(
    () => this.selected().length === 2 && this.comparison().kind !== 'loading',
  );
  protected readonly comparisonResult = computed(() => {
    const current = this.comparison();
    return current.kind === 'ready' ? current.result : null;
  });
  protected readonly comparisonError = computed(() => {
    const current = this.comparison();
    return current.kind === 'error' ? current : null;
  });
  protected readonly comparisonLoading = computed(() => this.comparison().kind === 'loading');

  constructor() {
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  protected isSelected(userId: string): boolean {
    return this.selected().includes(userId);
  }

  /**
   * Bascule la case d'un compte. La sélection est **plafonnée à deux** :
   * cocher un troisième n'a aucun effet (le gabarit désactive d'ailleurs
   * les cases non cochées à 2/2). Un même compte ne peut être coché
   * qu'une fois — la liste est un ensemble d'identifiants distincts.
   */
  protected toggle(userId: string): void {
    const current = this.selected();
    if (current.includes(userId)) {
      this.selected.set(current.filter((id) => id !== userId));
      return;
    }
    if (current.length >= 2) {
      return;
    }
    this.selected.set([...current, userId]);
  }

  protected onCheckboxChange(userId: string, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    if (checked !== this.isSelected(userId)) {
      this.toggle(userId);
    }
    // Rejette une coche au-delà de deux : remet la case dans son état réel.
    (event.target as HTMLInputElement).checked = this.isSelected(userId);
  }

  protected checkboxDisabled(userId: string): boolean {
    return this.selected().length >= 2 && !this.isSelected(userId);
  }

  protected clearSelection(): void {
    this.selected.set([]);
  }

  protected compare(trigger?: EventTarget | null): void {
    const [firstUserId, secondUserId] = this.selected();
    if (!firstUserId || !secondUserId) {
      return;
    }
    this.lastCompareTrigger =
      trigger instanceof HTMLElement ? trigger : (document.activeElement as HTMLElement | null);
    this.comparison.set({ kind: 'loading' });
    this.api.compareDuplicates({ firstUserId, secondUserId }).subscribe({
      next: (result) => {
        this.comparison.set({ kind: 'ready', result });
        this.focusCloseButton();
      },
      error: (error: unknown) => {
        const normalized = normalizeHttpError(error);
        this.comparison.set({
          kind: 'error',
          message: normalized.message,
          correlationId: normalized.correlationId,
        });
        this.focusCloseButton();
      },
    });
  }

  protected retryComparison(): void {
    this.compare(this.lastCompareTrigger);
  }

  protected closeComparison(): void {
    this.comparison.set({ kind: 'idle' });
    const trigger = this.lastCompareTrigger;
    this.lastCompareTrigger = null;
    queueMicrotask(() => trigger?.focus());
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    this.selected.set([]);
    this.comparison.set({ kind: 'idle' });
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
