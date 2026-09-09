import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { NotificationService } from '../../../core/notifications/notification.service';
import { OutboxApiService, OutboxMessage, OutboxSummary } from './outbox-api.service';

type Filter = 'DEAD' | 'FAILED' | 'PENDING' | 'ALL';

type State =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; items: readonly OutboxMessage[]; totalElements: number };

const PAGE_SIZE = 20;

/**
 * File d'échec des effets de bord (EF-OPS-005 ; docs/02 §25.2, §34.2).
 *
 * <p>L'écran ouvre sur les messages <strong>abandonnés</strong> : ce sont
 * les seuls qui appellent une décision humaine. Les autres se résorbent
 * d'eux-mêmes, et les mettre au premier plan noierait ce qui compte.
 *
 * <p>Le contenu d'un message n'est jamais affiché — l'API ne le renvoie
 * pas. L'exploitant voit ce qui a échoué et pourquoi ; lire les données
 * transportées relève des écrans métier, avec leurs propres contrôles.
 */
@Component({
  selector: 'app-outbox-console',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatButtonToggleModule, MatIconModule, MatProgressBarModule],
  templateUrl: './outbox-console.html',
  styleUrl: './outbox-console.scss',
})
export class OutboxConsole {
  private readonly api = inject(OutboxApiService);
  private readonly toasts = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<State>({ kind: 'loading' });
  protected readonly summary = signal<OutboxSummary | null>(null);
  protected readonly filter = signal<Filter>('DEAD');
  protected readonly replaying = signal<string | null>(null);

  constructor() {
    this.reload();
  }

  protected changeFilter(next: Filter): void {
    this.filter.set(next);
    this.reload();
  }

  protected reload(): void {
    this.state.set({ kind: 'loading' });
    const status = this.filter() === 'ALL' ? null : this.filter();
    this.api
      .list({ status, page: 0, size: PAGE_SIZE })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) =>
          this.state.set({
            kind: 'ready',
            items: page.content,
            totalElements: page.totalElements,
          }),
        error: () =>
          this.state.set({
            kind: 'error',
            message: 'La file des effets de bord n’a pas pu être chargée.',
          }),
      });
    this.api
      .summary()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (s) => this.summary.set(s), error: () => this.summary.set(null) });
  }

  /**
   * Rejoue un effet de bord abandonné. Le serveur le remet en file et le
   * diffuseur le reprend : le résultat est donc visible au rechargement
   * qui suit, sans traitement particulier ici.
   */
  protected replay(message: OutboxMessage): void {
    this.replaying.set(message.publicId);
    this.api
      .replay(message.publicId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.replaying.set(null);
          this.toasts.info('Effet de bord remis en file.');
          this.reload();
        },
        error: () => {
          this.replaying.set(null);
          this.toasts.error('Ce message n’a pas pu être rejoué.');
          this.reload();
        },
      });
  }

  protected trackMessage = (_: number, message: OutboxMessage): string => message.publicId;
}
