import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';

import { NotificationService } from '../../../core/notifications/notification.service';
import { AttendanceApiService } from '../attendance-api.service';
import { toAttendanceError } from '../attendance-errors';
import {
  EarlyDeparture,
  earlyDepartureEffectLabel,
  earlyDepartureOpinionLabel,
  earlyDepartureStatusLabel,
} from '../attendance.models';
import { formatInstantUtc } from '../../sessions/sessions.models';

type PanelState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; rows: EarlyDeparture[] };

/**
 * Départs anticipés d'une séance, côté formateur et responsable
 * (EF-ATT-013 ; docs/02 §16.13).
 *
 * <p>Le cahier donne quatre gestes : accepter, refuser, recommander
 * favorablement, transmettre. Les deux derniers aboutissent au même état —
 * le dossier quitte le formateur — et se distinguent par l'avis joint.
 * L'écran l'expose tel quel : « transmettre » avec un avis facultatif.
 *
 * <p>Une fois transmis, le bouton de décision disparaît pour le
 * formateur : le serveur refuserait (403), et proposer une action vouée à
 * l'échec est une invitation à la confusion.
 */
@Component({
  selector: 'app-early-departure-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
  ],
  templateUrl: './early-departure-panel.html',
  styleUrl: './early-departure-panel.scss',
})
export class EarlyDeparturePanel {
  /** Séance dont on liste les dossiers. */
  readonly sessionId = input.required<string>();
  /**
   * `true` si l'utilisateur peut trancher un dossier déjà transmis.
   * Ergonomie seulement — le serveur reste l'autorité et renvoie `403`.
   */
  readonly canDecideForwarded = input<boolean>(false);

  private readonly api = inject(AttendanceApiService);
  private readonly fb = inject(FormBuilder);
  private readonly notifications = inject(NotificationService);

  protected readonly statusLabel = earlyDepartureStatusLabel;
  protected readonly effectLabel = earlyDepartureEffectLabel;
  protected readonly opinionLabel = earlyDepartureOpinionLabel;
  protected readonly formatInstantUtc = formatInstantUtc;

  protected readonly state = signal<PanelState>({ kind: 'loading' });
  protected readonly busy = signal(false);
  protected readonly actionError = signal<string | null>(null);
  /** Dossier dont le formulaire est déployé, et nature du geste. */
  protected readonly openForm = signal<{ id: string; mode: 'forward' | 'decide' } | null>(null);

  protected readonly forwardForm = this.fb.nonNullable.group({
    opinion: [''],
    comment: ['', Validators.maxLength(500)],
  });
  protected readonly decideForm = this.fb.nonNullable.group({
    accepted: [true],
    comment: ['', [Validators.required, Validators.maxLength(500)]],
  });

  protected readonly rows = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.rows : [];
  });

  constructor() {
    effect(() => {
      const id = this.sessionId();
      if (id) {
        this.load(id);
      }
    });
  }

  protected isOpen(row: EarlyDeparture): boolean {
    return row.status === 'REQUESTED' || row.status === 'FORWARDED';
  }

  /** Un dossier transmis n'est plus décidable que par un responsable. */
  protected canDecide(row: EarlyDeparture): boolean {
    if (!this.isOpen(row)) {
      return false;
    }
    return row.status === 'REQUESTED' || this.canDecideForwarded();
  }

  protected canForward(row: EarlyDeparture): boolean {
    return row.status === 'REQUESTED';
  }

  protected toggle(id: string, mode: 'forward' | 'decide'): void {
    const current = this.openForm();
    this.actionError.set(null);
    if (current && current.id === id && current.mode === mode) {
      this.openForm.set(null);
      return;
    }
    this.forwardForm.reset({ opinion: '', comment: '' });
    this.decideForm.reset({ accepted: true, comment: '' });
    this.openForm.set({ id, mode });
  }

  protected submitForward(id: string): void {
    if (this.busy()) {
      return;
    }
    const raw = this.forwardForm.getRawValue();
    this.run(
      this.api.forwardEarlyDeparture(id, {
        opinion: raw.opinion || null,
        comment: raw.comment.trim() || null,
      }),
      'Dossier transmis au responsable pédagogique.',
    );
  }

  protected submitDecision(id: string): void {
    if (this.busy() || this.decideForm.invalid) {
      this.decideForm.markAllAsTouched();
      return;
    }
    const raw = this.decideForm.getRawValue();
    this.run(
      this.api.decideEarlyDeparture(id, {
        accepted: raw.accepted,
        comment: raw.comment.trim(),
      }),
      raw.accepted ? 'Départ anticipé accepté.' : 'Départ anticipé refusé.',
    );
  }

  protected retry(): void {
    this.load(this.sessionId());
  }

  private run(request: import('rxjs').Observable<EarlyDeparture>, done: string): void {
    this.busy.set(true);
    this.actionError.set(null);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.openForm.set(null);
        this.notifications.info(done);
        this.load(this.sessionId());
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.actionError.set(toAttendanceError(error).message);
      },
    });
  }

  private load(sessionId: string): void {
    this.state.set({ kind: 'loading' });
    this.api.listSessionEarlyDepartures(sessionId).subscribe({
      next: (rows) => this.state.set({ kind: 'ready', rows }),
      error: (error: unknown) =>
        this.state.set({ kind: 'error', message: toAttendanceError(error).message }),
    });
  }
}
