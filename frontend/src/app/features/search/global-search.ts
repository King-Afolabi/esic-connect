import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { GlobalSearchApiService } from './global-search-api.service';
import {
  GlobalSearchResponse,
  SEARCH_MIN_LENGTH,
  SearchHit,
  hitRoute,
  hitTypeLabel,
} from './global-search.models';

type State =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'forbidden' }
  | { kind: 'error' }
  | { kind: 'ready'; data: GlobalSearchResponse };

/**
 * Recherche globale (EF-USER-009 ; docs/02 §22.7).
 *
 * Le périmètre est appliqué **côté serveur** : cet écran n'a aucun
 * filtre de périmètre et n'en transmet aucun. Les mentions renvoyées par
 * l'API — périmètre restreint, adresse non recherchée — sont affichées
 * telles quelles plutôt que reformulées ici : c'est le serveur qui sait
 * ce qu'il a réellement fait.
 */
@Component({
  selector: 'app-global-search',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './global-search.html',
  styleUrl: './global-search.scss',
})
export class GlobalSearch {
  private readonly api = inject(GlobalSearchApiService);
  private readonly fb = inject(FormBuilder);

  protected readonly minLength = SEARCH_MIN_LENGTH;
  protected readonly hitTypeLabel = hitTypeLabel;
  protected readonly hitRoute = hitRoute;

  protected readonly form = this.fb.nonNullable.group({ query: '' });
  protected readonly state = signal<State>({ kind: 'idle' });

  protected submit(): void {
    const query = this.form.getRawValue().query.trim();
    if (query.length < this.minLength) {
      // Refusé côté client aussi, pour ne pas envoyer une requête dont on
      // sait déjà qu'elle ne cherchera rien. Le serveur refuse de toute
      // façon : ce contrôle est un confort, pas une protection.
      this.state.set({
        kind: 'ready',
        data: {
          query,
          truncated: false,
          results: [],
          notes: [`Saisissez au moins ${this.minLength} caractères.`],
        },
      });
      return;
    }
    this.state.set({ kind: 'loading' });
    this.api.search(query).subscribe({
      next: (data) => this.state.set({ kind: 'ready', data }),
      error: (error: unknown) => {
        const status = (error as { status?: number } | null)?.status;
        this.state.set({ kind: status === 403 ? 'forbidden' : 'error' });
      },
    });
  }

  protected reset(): void {
    this.form.reset({ query: '' });
    this.state.set({ kind: 'idle' });
  }

  protected trackHit(_index: number, hit: SearchHit): string {
    return `${hit.type}:${hit.publicId}`;
  }
}
