import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Observable } from 'rxjs';

import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { formatInstantUtc } from '../../sessions/sessions.models';
import { ClaimsApiService } from '../claims-api.service';
import { toClaimError } from '../claim-errors';
import {
  CLAIM_AUDIENCES,
  CLAIM_DECISIONS,
  CLAIM_STAFF_ROLES,
  CLOSED_CLAIM_STATUSES,
  ClaimThread as ClaimThreadModel,
  claimAudienceLabel,
  claimCategoryLabel,
  claimEventLabel,
  claimRoleLabel,
  claimStatusLabel,
} from '../claims.models';

type ThreadState =
  | { kind: 'loading' }
  | { kind: 'not-found' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; thread: ClaimThreadModel };

/**
 * Fil d'une réclamation : conversation, transfert, décision, réouverture
 * (EF-CLAIM-002..004 ; docs/02 §20).
 *
 * <p>Messages et décisions sont présentés <strong>séparément</strong>,
 * comme en base : une décision n'est pas de la conversation, et les
 * mélanger rendrait l'historique d'un dossier transféré illisible
 * (RG-088).
 *
 * <p>Un fil clos n'accepte plus de message — il accepte une réouverture
 * motivée. L'écran propose donc l'une ou l'autre, jamais les deux.
 */
@Component({
  selector: 'app-claim-thread',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
  ],
  templateUrl: './claim-thread.html',
  styleUrl: '../claim-list/claim-list.scss',
})
export class ClaimThread {
  private readonly api = inject(ClaimsApiService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly roleContext = inject(RoleContextService);
  private readonly notifications = inject(NotificationService);

  private readonly publicId = this.route.snapshot.paramMap.get('publicId') ?? '';

  protected readonly audiences = CLAIM_AUDIENCES;
  protected readonly decisions = CLAIM_DECISIONS;
  protected readonly audienceLabel = claimAudienceLabel;
  protected readonly categoryLabel = claimCategoryLabel;
  protected readonly statusLabel = claimStatusLabel;
  protected readonly eventLabel = claimEventLabel;
  protected readonly roleLabel = claimRoleLabel;
  protected readonly formatInstantUtc = formatInstantUtc;

  protected readonly state = signal<ThreadState>({ kind: 'loading' });
  protected readonly busy = signal(false);
  protected readonly actionError = signal<string | null>(null);
  protected readonly openPanel = signal<'transfer' | 'decide' | 'reopen' | null>(null);

  protected readonly messageForm = this.fb.nonNullable.group({
    body: ['', [Validators.required, Validators.maxLength(5000)]],
  });
  protected readonly transferForm = this.fb.nonNullable.group({
    audience: ['PEDAGOGICAL_MANAGER', Validators.required],
    motive: ['', [Validators.required, Validators.maxLength(500)]],
  });
  protected readonly decideForm = this.fb.nonNullable.group({
    status: ['IN_PROGRESS', Validators.required],
    motive: ['', [Validators.required, Validators.maxLength(500)]],
  });
  protected readonly reopenForm = this.fb.nonNullable.group({
    motive: ['', [Validators.required, Validators.maxLength(500)]],
  });

  protected readonly thread = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.thread : null;
  });

  /** Un fil clos n'accepte plus de message : il accepte une réouverture. */
  protected readonly isClosed = computed(() => {
    const t = this.thread();
    return !!t && CLOSED_CLAIM_STATUSES.includes(t.claim.status);
  });

  /**
   * Ergonomie seulement : c'est le serveur qui décide, et il refuse un
   * transfert ou une décision demandés par l'auteur.
   */
  protected readonly isStaff = computed(() =>
    this.roleContext.effectiveRoles().some((role) => CLAIM_STAFF_ROLES.includes(role as never)),
  );

  constructor() {
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  protected toggle(panel: 'transfer' | 'decide' | 'reopen'): void {
    this.actionError.set(null);
    this.openPanel.update((current) => (current === panel ? null : panel));
  }

  protected sendMessage(): void {
    if (this.messageForm.invalid || this.busy()) {
      this.messageForm.markAllAsTouched();
      return;
    }
    const body = this.messageForm.getRawValue().body.trim();
    this.run(this.api.postMessage(this.publicId, body), 'Message ajouté au fil.', () =>
      this.messageForm.reset({ body: '' }),
    );
  }

  protected transfer(): void {
    if (this.transferForm.invalid || this.busy()) {
      this.transferForm.markAllAsTouched();
      return;
    }
    const raw = this.transferForm.getRawValue();
    this.run(
      this.api.transfer(this.publicId, raw.audience, raw.motive.trim()),
      'Réclamation transférée.',
    );
  }

  protected decide(): void {
    if (this.decideForm.invalid || this.busy()) {
      this.decideForm.markAllAsTouched();
      return;
    }
    const raw = this.decideForm.getRawValue();
    this.run(this.api.decide(this.publicId, raw.status, raw.motive.trim()), 'Décision enregistrée.');
  }

  protected reopen(): void {
    if (this.reopenForm.invalid || this.busy()) {
      this.reopenForm.markAllAsTouched();
      return;
    }
    const motive = this.reopenForm.getRawValue().motive.trim();
    this.run(this.api.reopen(this.publicId, motive), 'Réclamation rouverte.');
  }

  private run(request: Observable<ClaimThreadModel>, done: string, after?: () => void): void {
    this.busy.set(true);
    this.actionError.set(null);
    request.subscribe({
      next: (thread) => {
        this.busy.set(false);
        this.openPanel.set(null);
        after?.();
        this.state.set({ kind: 'ready', thread });
        this.notifications.info(done);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.actionError.set(toClaimError(error).message);
      },
    });
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    this.api.thread(this.publicId).subscribe({
      next: (thread) => this.state.set({ kind: 'ready', thread }),
      error: (error: unknown) => {
        const view = toClaimError(error);
        // Une réclamation d'autrui répond 404, jamais 403 : l'existence
        // même du fil est protégée. L'écran ne dit donc pas autre chose.
        this.state.set(
          view.notFound ? { kind: 'not-found' } : { kind: 'error', message: view.message },
        );
      },
    });
  }
}
