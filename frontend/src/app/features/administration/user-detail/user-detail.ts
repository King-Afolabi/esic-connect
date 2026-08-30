import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { normalizeHttpError } from '../../../core/models/api-error';
import { ROLES, Role, roleLabel } from '../../../core/models/role';
import { NotificationService } from '../../../core/notifications/notification.service';
import { AdministrationApiService } from '../administration-api.service';
import { toAdministrationError } from '../administration-errors';
import { ACTION_REASON_MAX_LENGTH, UserDetailResponse, accountStatusLabel } from '../administration.models';

type DetailState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'not-found' }
  | { kind: 'forbidden' }
  | { kind: 'ready'; user: UserDetailResponse };

/** Action de mutation en cours de confirmation. */
type PendingAction =
  | { kind: 'suspend' }
  | { kind: 'restore' }
  | { kind: 'archive' }
  | { kind: 'assign' }
  | { kind: 'revoke'; role: string };

/** Périmètre de `suspend` / `restore` (`UserAccountController` `LIFECYCLE_ROLES`). */
const LIFECYCLE_ROLES: readonly Role[] = ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION'];
/** Périmètre de `archive` / `roles` / `revoke` (`UserAccountController` `ADMIN_ROLES`). */
const ROLE_ADMIN_ROLES: readonly Role[] = ['ADMIN', 'SUPER_ADMIN'];

/**
 * Fiche d'un compte utilisateur, historique de ses rôles et **actions
 * d'administration** :
 *
 * - cycle de vie : suspension (`ACTIVE` → `SUSPENDED`), réactivation
 *   (`SUSPENDED` → `ACTIVE`), archivage (clôture les rôles actifs,
 *   terminal dans ce lot) ;
 * - rôles : attribution d'un rôle, retrait d'une affectation active
 *   (l'historique est conservé).
 *
 * La visibilité des actions suit `RoleContextService.effectiveRoles`
 * (contexte de rôle actif, toujours un sous-ensemble du JWT — il peut
 * restreindre l'affichage, jamais l'élargir). Elle masque aussi les
 * auto-actions lorsque le sujet du JWT correspond de façon fiable au
 * compte affiché. Rien de cela n'est une garantie de sécurité : chaque
 * mutation traite les réponses `USER_*` réelles du back-end
 * (`USER_INVALID_STATE`, `USER_SELF_ACTION_FORBIDDEN`,
 * `USER_SUPER_ADMIN_PROTECTED`, `USER_LAST_ACTIVE_ROLE`…), et un `403`
 * reste rendu « accès refusé ».
 *
 * Chaque mutation demande un motif obligatoire (≤ 500 caractères),
 * présente une confirmation en ligne, désactive ses contrôles pendant
 * l'appel, empêche la double soumission, puis recharge la fiche.
 */
@Component({
  selector: 'app-user-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    RouterLink,
    ReactiveFormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
  ],
  templateUrl: './user-detail.html',
  styleUrl: './user-detail.scss',
})
export class UserDetail {
  private readonly api = inject(AdministrationApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly roleContext = inject(RoleContextService);
  private readonly notifications = inject(NotificationService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  private readonly publicId = this.route.snapshot.paramMap.get('publicId') ?? '';

  protected readonly statusLabel = accountStatusLabel;
  protected readonly roleLabel = roleLabel;
  protected readonly reasonMaxLength = ACTION_REASON_MAX_LENGTH;

  protected readonly state = signal<DetailState>({ kind: 'loading' });

  /** Action dont le panneau de confirmation est ouvert, ou `null`. */
  protected readonly pending = signal<PendingAction | null>(null);
  protected readonly submitting = signal(false);
  /** Message d'erreur global d'une mutation. */
  protected readonly actionError = signal<string | null>(null);
  /** Message d'erreur rattaché au champ « rôle » (`USER_ROLE_UNKNOWN`). */
  protected readonly roleFieldError = signal<string | null>(null);

  protected readonly reasonForm = this.formBuilder.group({
    reason: this.formBuilder.control('', [
      Validators.required,
      Validators.maxLength(ACTION_REASON_MAX_LENGTH),
    ]),
  });

  protected readonly assignForm = this.formBuilder.group({
    role: this.formBuilder.control<Role | ''>('', [Validators.required]),
    reason: this.formBuilder.control('', [
      Validators.required,
      Validators.maxLength(ACTION_REASON_MAX_LENGTH),
    ]),
  });

  protected readonly user = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.user : null;
  });
  protected readonly errorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'error' ? current.message : null;
  });

  /** Rôles effectifs pour l'affichage (contexte actif ou tous les rôles JWT). */
  private readonly effectiveRoles = this.roleContext.effectiveRoles;

  protected readonly canLifecycle = computed(() => holdsAny(this.effectiveRoles(), LIFECYCLE_ROLES));
  protected readonly canManageRoles = computed(() =>
    holdsAny(this.effectiveRoles(), ROLE_ADMIN_ROLES),
  );
  private readonly isSuperAdminContext = computed(() => this.effectiveRoles().includes('SUPER_ADMIN'));

  /** Vrai si la fiche affichée est, de façon fiable, le compte de l'appelant. */
  protected readonly isSelf = computed(() => {
    const subject = this.auth.session()?.subject ?? null;
    const current = this.user();
    return subject !== null && current !== null && subject === current.publicId;
  });

  protected readonly isArchived = computed(() => this.user()?.status === 'ARCHIVED');

  protected readonly showSuspend = computed(
    () => this.canLifecycle() && !this.isSelf() && this.user()?.status === 'ACTIVE',
  );
  protected readonly showRestore = computed(
    () => this.canLifecycle() && !this.isSelf() && this.user()?.status === 'SUSPENDED',
  );
  protected readonly showArchive = computed(
    () => this.canManageRoles() && !this.isSelf() && this.user() !== null && !this.isArchived(),
  );
  /** Le retrait de rôle et le formulaire d'attribution partagent ce garde. */
  protected readonly canManageTargetRoles = computed(
    () => this.canManageRoles() && this.user() !== null && !this.isArchived(),
  );

  /** Rôles déjà actifs sur la cible (exclus de la sélection d'attribution). */
  private readonly activeRoleCodes = computed<string[]>(() => {
    const current = this.user();
    return current ? current.roleAssignments.filter((a) => a.active).map((a) => a.role) : [];
  });

  /** Options d'attribution : rôles non actifs, `SUPER_ADMIN` réservé à un contexte `SUPER_ADMIN`. */
  protected readonly assignableRoleOptions = computed<Role[]>(() => {
    const active = new Set(this.activeRoleCodes());
    const superAllowed = this.isSuperAdminContext();
    return ROLES.filter((r) => !active.has(r) && (r !== 'SUPER_ADMIN' || superAllowed));
  });

  protected readonly roleColumns = computed<string[]>(() =>
    this.canManageTargetRoles() && !this.isSelf()
      ? ['role', 'active', 'validFrom', 'validUntil', 'actions']
      : ['role', 'active', 'validFrom', 'validUntil'],
  );

  protected readonly confirmTitle = computed(() => {
    const action = this.pending();
    switch (action?.kind) {
      case 'suspend':
        return 'Suspendre le compte';
      case 'restore':
        return 'Réactiver le compte';
      case 'archive':
        return 'Archiver le compte';
      case 'assign':
        return 'Attribuer un rôle';
      case 'revoke':
        return `Retirer le rôle « ${roleLabel(action.role)} »`;
      default:
        return '';
    }
  });

  protected readonly confirmDescription = computed(() => {
    const action = this.pending();
    switch (action?.kind) {
      case 'suspend':
        return 'Le compte ne pourra plus se connecter jusqu’à sa réactivation.';
      case 'restore':
        return 'Le compte pourra de nouveau se connecter.';
      case 'archive':
        return 'Le compte est archivé et son historique conservé. Cette opération est irréversible dans ce lot.';
      case 'assign':
        return 'Le rôle choisi devient actif immédiatement. Le back-end reste l’autorité (rôle déjà actif, compte protégé…).';
      case 'revoke':
        return 'L’affectation est clôturée ; son historique est conservé et n’est pas supprimé.';
      default:
        return '';
    }
  });

  protected readonly confirmCta = computed(() => {
    const action = this.pending();
    switch (action?.kind) {
      case 'suspend':
        return 'Suspendre';
      case 'restore':
        return 'Réactiver';
      case 'archive':
        return 'Archiver';
      case 'assign':
        return 'Attribuer';
      case 'revoke':
        return 'Retirer';
      default:
        return '';
    }
  });

  constructor() {
    this.load();
    // Un changement de contexte de rôle (ou de compte cible) doit fermer
    // un panneau devenu indisponible : on ne laisse jamais un formulaire
    // sensible ouvert pour une action que l'interface ne propose plus.
    effect(() => {
      const action = this.pending();
      if (!action) {
        return;
      }
      const stillOffered =
        (action.kind === 'suspend' && this.showSuspend()) ||
        (action.kind === 'restore' && this.showRestore()) ||
        (action.kind === 'archive' && this.showArchive()) ||
        (action.kind === 'assign' && this.canManageTargetRoles()) ||
        (action.kind === 'revoke' && this.canManageTargetRoles() && !this.isSelf());
      if (!stillOffered) {
        this.pending.set(null);
        this.clearActionErrors();
      }
    });
  }

  protected retry(): void {
    this.load();
  }

  protected canRevoke(role: string): boolean {
    return (
      this.canManageTargetRoles() &&
      !this.isSelf() &&
      (role !== 'SUPER_ADMIN' || this.isSuperAdminContext())
    );
  }

  protected startAction(kind: 'suspend' | 'restore' | 'archive'): void {
    this.reasonForm.reset({ reason: '' });
    this.clearActionErrors();
    this.pending.set({ kind });
  }

  protected startAssign(): void {
    this.assignForm.reset({ role: '', reason: '' });
    this.clearActionErrors();
    this.pending.set({ kind: 'assign' });
  }

  protected startRevoke(role: string): void {
    this.reasonForm.reset({ reason: '' });
    this.clearActionErrors();
    this.pending.set({ kind: 'revoke', role });
  }

  protected cancelAction(): void {
    this.pending.set(null);
    this.clearActionErrors();
  }

  protected confirm(): void {
    const action = this.pending();
    const current = this.user();
    if (!action || !current || this.submitting()) {
      return;
    }

    if (action.kind === 'assign') {
      if (this.assignForm.invalid) {
        this.assignForm.markAllAsTouched();
        return;
      }
      const raw = this.assignForm.getRawValue();
      this.run(
        this.api.assignRole(current.publicId, {
          role: raw.role as Role,
          reason: raw.reason.trim(),
        }),
        'Rôle attribué.',
      );
      return;
    }

    if (this.reasonForm.invalid) {
      this.reasonForm.markAllAsTouched();
      return;
    }
    const reason = this.reasonForm.getRawValue().reason.trim();
    switch (action.kind) {
      case 'suspend':
        this.run(this.api.suspendUser(current.publicId, { reason }), 'Compte suspendu.');
        break;
      case 'restore':
        this.run(this.api.restoreUser(current.publicId, { reason }), 'Compte réactivé.');
        break;
      case 'archive':
        this.run(this.api.archiveUser(current.publicId, { reason }), 'Compte archivé.');
        break;
      case 'revoke':
        this.run(this.api.revokeRole(current.publicId, action.role, { reason }), 'Rôle retiré.');
        break;
    }
  }

  private run(call: Observable<void>, successMessage: string): void {
    this.submitting.set(true);
    this.clearActionErrors();
    call.subscribe({
      next: () => {
        this.submitting.set(false);
        this.pending.set(null);
        this.notifications.info(successMessage);
        this.load();
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        const view = toAdministrationError(error);
        if (view.field === 'role') {
          this.roleFieldError.set(view.message);
        } else {
          this.actionError.set(view.message);
        }
      },
    });
  }

  private clearActionErrors(): void {
    this.actionError.set(null);
    this.roleFieldError.set(null);
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    this.pending.set(null);
    this.clearActionErrors();
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

function holdsAny(held: readonly string[], required: readonly string[]): boolean {
  return required.some((role) => held.includes(role));
}
