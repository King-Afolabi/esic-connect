import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { RoleContextService } from '../../../core/auth/role-context.service';
import { AttendanceApiService } from '../attendance-api.service';
import { toAttendanceError } from '../attendance-errors';
import { ATTENDANCE_MANAGE_ROLES, SummaryResponse, percent } from '../attendance.models';

type State =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; summary: SummaryResponse };

/**
 * Cartes de synthèse d'assiduité (V10). Périmètre appliqué côté serveur ;
 * un `403` est rendu « accès refusé ». Aucun stockage navigateur, aucune
 * donnée sensible en URL de navigation.
 */
@Component({
  selector: 'app-attendance-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './attendance-summary.html',
  styleUrl: '../my-attendance/my-attendance-list.scss',
})
export class AttendanceSummary {
  private readonly api = inject(AttendanceApiService);
  private readonly fb = inject(FormBuilder);
  private readonly roleContext = inject(RoleContextService);

  protected readonly percent = percent;
  protected readonly state = signal<State>({ kind: 'loading' });

  /**
   * §5 — jeton monotone : chaque `load()` (et chaque perte du droit
   * actif) l'incrémente ; une réponse HTTP dont le jeton n'est plus le
   * courant est ignorée. Empêche une réponse tardive de réafficher des
   * données après une bascule de contexte.
   */
  private readonly loadToken = signal(0);

  /** Droit d'accès aux rapports dans le **contexte de rôle actif**. */
  protected readonly canView = computed(() =>
    this.roleContext.effectiveRoles().some((r) => (ATTENDANCE_MANAGE_ROLES as readonly string[]).includes(r)),
  );

  protected readonly filters = this.fb.nonNullable.group({
    from: [''],
    to: [''],
    classGroup: [''],
  });

  protected readonly summary = computed(() => {
    const s = this.state();
    return s.kind === 'ready' ? s.summary : null;
  });
  protected readonly errorMessage = computed(() => {
    const s = this.state();
    return s.kind === 'error' ? s.message : null;
  });

  constructor() {
    // §5 — pilote le chargement sur le contexte de rôle actif :
    //  - contexte autorisé (à l'init ou retrouvé) → rechargement propre ;
    //  - bascule vers un contexte sans droit → invalide la requête en
    //    cours (jeton), efface la synthèse chargée, n'émet plus rien.
    effect(() => {
      const allowed = this.canView();
      // `untracked` : l'effet ne dépend QUE de `canView()` (donc du
      // contexte de rôle), jamais des signaux lus dans `load()` — sinon
      // `apply()` / `reset()` relanceraient l'effet en boucle.
      untracked(() => {
        if (allowed) {
          this.load();
        } else {
          this.loadToken.update((n) => n + 1);
          this.state.set({ kind: 'forbidden' });
        }
      });
    });
  }

  protected apply(): void {
    this.load();
  }
  protected reset(): void {
    this.filters.reset({ from: '', to: '', classGroup: '' });
    this.load();
  }
  protected retry(): void {
    this.load();
  }

  private load(): void {
    this.loadToken.update((n) => n + 1);
    const token = this.loadToken();
    if (!this.canView()) {
      this.state.set({ kind: 'forbidden' });
      return;
    }
    this.state.set({ kind: 'loading' });
    const raw = this.filters.getRawValue();
    this.api
      .summary({
        from: isoStart(raw.from),
        to: isoStart(raw.to),
        classGroup: raw.classGroup.trim() || null,
      })
      .subscribe({
        next: (summary) => {
          // §5 — réponse obsolète (nouveau load ou droit perdu depuis) : ignorée.
          if (token !== this.loadToken() || !this.canView()) {
            return;
          }
          this.state.set({ kind: 'ready', summary });
        },
        error: (error: unknown) => {
          if (token !== this.loadToken() || !this.canView()) {
            return;
          }
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
