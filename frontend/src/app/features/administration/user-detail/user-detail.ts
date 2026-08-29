import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { normalizeHttpError } from '../../../core/models/api-error';
import { roleLabel } from '../../../core/models/role';
import { AdministrationApiService } from '../administration-api.service';
import { UserDetailResponse, accountStatusLabel } from '../administration.models';

type DetailState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'not-found' }
  | { kind: 'forbidden' }
  | { kind: 'ready'; user: UserDetailResponse };

/**
 * Fiche d'un compte utilisateur et historique de ses rôles.
 *
 * - `GET /api/v1/users/{publicId}` : le compte + l'historique complet des
 *   affectations de rôle (actives et clôturées, docs/02 §9.7).
 *
 * Lecture seule : aucune action de suspension, réactivation, archivage,
 * attribution ou retrait de rôle n'est déclenchée (ces routes existent
 * côté back-end mais ne sont pas consommées par cette tranche).
 *
 * Un `404` (identifiant inconnu ou mal formé) rend un état « introuvable » ;
 * un `403` rend un état « accès refusé » — le contrôle d'accès reste
 * décidé par Spring Security.
 */
@Component({
  selector: 'app-user-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    RouterLink,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './user-detail.html',
  styleUrl: './user-detail.scss',
})
export class UserDetail {
  private readonly api = inject(AdministrationApiService);
  private readonly route = inject(ActivatedRoute);

  private readonly publicId = this.route.snapshot.paramMap.get('publicId') ?? '';

  protected readonly statusLabel = accountStatusLabel;
  protected readonly roleLabel = roleLabel;
  protected readonly roleColumns = ['role', 'active', 'validFrom', 'validUntil'] as const;

  protected readonly state = signal<DetailState>({ kind: 'loading' });

  protected readonly user = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.user : null;
  });
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
    this.api.getUser(this.publicId).subscribe({
      next: (user) => this.state.set({ kind: 'ready', user }),
      error: (error: unknown) => {
        const normalized = normalizeHttpError(error);
        if (normalized.status === 404) {
          this.state.set({ kind: 'not-found' });
          return;
        }
        if (normalized.status === 403) {
          this.state.set({ kind: 'forbidden' });
          return;
        }
        this.state.set({ kind: 'error', message: normalized.message });
      },
    });
  }
}
