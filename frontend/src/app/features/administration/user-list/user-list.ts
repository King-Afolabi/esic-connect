import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { RoleContextService } from '../../../core/auth/role-context.service';
import { normalizeHttpError } from '../../../core/models/api-error';
import { ROLES, Role, roleLabel } from '../../../core/models/role';
import {
  ListQueryReader,
  writeListQueryParams,
} from '../../../core/navigation/list-query-params';
import { NotificationService } from '../../../core/notifications/notification.service';
import { AdministrationApiService } from '../administration-api.service';
import { toAdministrationError } from '../administration-errors';
import {
  ACCOUNT_STATUSES,
  ACTION_REASON_MAX_LENGTH,
  AccountStatus,
  BULK_ACTIONS,
  BulkAction,
  BulkResult,
  PageResponse,
  SortDirection,
  USER_SORT_FIELDS,
  UserRoleFilter,
  UserSortField,
  UserSummaryResponse,
  accountStatusLabel,
  bulkActionLabel,
  bulkOutcomeLabel,
} from '../administration.models';

/** Périmètre des opérations de masse (`UserAccountController` `LIFECYCLE_ROLES`) — identique côté serveur. */
const BULK_ROLES: readonly Role[] = ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION'];
/** Périmètre de la détection de doublons (`UserAccountController` `ADMIN_ROLES`) — plus restreint. */
const DUPLICATE_VIEW_ROLES: readonly Role[] = ['ADMIN', 'SUPER_ADMIN'];

/** État de la consultation de la liste. */
type ListState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready'; page: PageResponse<UserSummaryResponse> };

const DEFAULT_SORT_FIELD: UserSortField = 'createdAt';
const DEFAULT_SORT_DIRECTION: SortDirection = 'desc';
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

/**
 * Liste des comptes utilisateurs — `GET /api/v1/users`.
 *
 * Recherche, filtres, tri et pagination reflètent **exactement** ce que
 * l'API accepte : recherche `q` (sous-chaîne email / prénom / nom),
 * filtre `status` (`AccountStatus`), filtre `role` (affectation active,
 * `RoleCode`), tri sur `createdAt` / `lastLoginAt` / `email` / `lastName`,
 * pagination bornée à 100. Aucune capacité inventée. Lecture seule :
 * aucune action de cycle de vie ni de gestion de rôle n'est déclenchée.
 *
 * Le contrôle d'accès reste côté Spring Security : un `403` renvoyé par
 * l'API est rendu comme un état « accès refusé » explicite, même si le
 * `roleGuard` de la route a normalement déjà filtré l'utilisateur.
 */
@Component({
  selector: 'app-user-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatCheckboxModule,
  ],
  providers: [{ provide: MatPaginatorIntl, useFactory: frenchPaginatorIntl }],
  templateUrl: './user-list.html',
  styleUrl: './user-list.scss',
})
export class UserList {
  private readonly api = inject(AdministrationApiService);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly roleContext = inject(RoleContextService);
  private readonly notifications = inject(NotificationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly statuses = ACCOUNT_STATUSES;
  protected readonly roleOptions = ROLES;
  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  protected readonly statusLabel = accountStatusLabel;
  protected readonly roleLabel = roleLabel;

  /** Libellés des rôles actifs, séparés par une virgule ; `—` si aucun. */
  protected rolesLabel(codes: readonly string[]): string {
    return codes.length ? codes.map(roleLabel).join(', ') : '—';
  }
  /** La colonne de sélection n'apparaît que pour un rôle habilité aux opérations de masse. */
  protected readonly displayedColumns = computed(() =>
    this.canBulk()
      ? (['select', 'email', 'lastName', 'roles', 'status', 'createdAt', 'lastLoginAt', 'actions'] as const)
      : (['email', 'lastName', 'roles', 'status', 'createdAt', 'lastLoginAt', 'actions'] as const),
  );

  /** Filtres appliqués (recherche + statut + rôle). */
  /**
   * Création d'un compte (EF-USER-001, EF-TEA-001).
   *
   * <p>Pas de champ mot de passe, et il ne faut jamais en ajouter : la
   * personne le choisit via son lien d'invitation. Le domaine de
   * l'adresse est libre — c'est ainsi qu'on crée un formateur externe,
   * le domaine n'étant jamais un critère de confiance (docs/02 §12.1).
   */
  protected readonly createForm = this.formBuilder.group({
    email: this.formBuilder.control('', [Validators.required, Validators.email]),
    firstName: this.formBuilder.control('', [Validators.required, Validators.maxLength(120)]),
    lastName: this.formBuilder.control('', [Validators.required, Validators.maxLength(120)]),
    role: this.formBuilder.control('STUDENT', [Validators.required]),
  });

  protected readonly creating = signal(false);
  protected readonly createError = signal<string | null>(null);
  protected readonly createInfo = signal<string | null>(null);

  protected readonly filters = this.formBuilder.group({
    q: this.formBuilder.control(''),
    status: this.formBuilder.control<AccountStatus | ''>(''),
    role: this.formBuilder.control<UserRoleFilter | ''>(''),
  });

  protected readonly state = signal<ListState>({ kind: 'loading' });

  protected readonly sortField = signal<UserSortField>(DEFAULT_SORT_FIELD);
  protected readonly sortDirection = signal<SortDirection>(DEFAULT_SORT_DIRECTION);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);

  protected readonly rows = computed<UserSummaryResponse[]>(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.page.content : [];
  });
  protected readonly totalElements = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.page.totalElements : 0;
  });
  protected readonly isEmpty = computed(() => {
    const current = this.state();
    return current.kind === 'ready' && current.page.content.length === 0;
  });
  protected readonly errorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'error' ? current.message : null;
  });

  constructor() {
    // Lot G : restaure recherche / filtres / tri / pagination depuis l'URL.
    const params = new ListQueryReader(this.route);
    this.filters.patchValue({
      q: params.str('q'),
      status: params.oneOf('status', [...ACCOUNT_STATUSES, ''] as const, ''),
      role: params.oneOf('role', [...ROLES, ''] as const, ''),
    });
    this.sortField.set(params.oneOf('sort', USER_SORT_FIELDS, DEFAULT_SORT_FIELD));
    this.sortDirection.set(params.direction('dir', DEFAULT_SORT_DIRECTION));
    this.pageIndex.set(params.int('page', 0));
    this.pageSize.set(params.int('size', 20));
    this.load();
  }

  /** Crée le compte et déclenche l'invitation (le serveur s'en charge). */
  protected submitCreate(): void {
    if (this.createForm.invalid || this.creating()) {
      this.createForm.markAllAsTouched();
      return;
    }
    this.creating.set(true);
    this.createError.set(null);
    this.createInfo.set(null);
    const value = this.createForm.getRawValue();
    this.api
      .createUser({
        email: value.email.trim(),
        firstName: value.firstName.trim(),
        lastName: value.lastName.trim(),
        role: value.role,
      })
      .subscribe({
        next: () => {
          this.creating.set(false);
          this.createForm.reset({ role: 'STUDENT' });
          this.createInfo.set(
            "Le compte est créé en attente d'activation ; l'invitation vient de partir.",
          );
          this.load();
        },
        error: (error: unknown) => {
          this.creating.set(false);
          const normalized = normalizeHttpError(error);
          this.createError.set(
            normalized.code === 'USER_EMAIL_ALREADY_USED'
              ? 'Un compte existe déjà pour cette adresse électronique.'
              : normalized.message,
          );
        },
      });
  }

  protected applyFilters(): void {
    this.pageIndex.set(0);
    this.resetBulkOutcome();
    this.load();
  }

  protected resetFilters(): void {
    this.filters.reset({ q: '', status: '', role: '' });
    this.pageIndex.set(0);
    this.resetBulkOutcome();
    this.load();
  }

  protected onSortChange(sort: Sort): void {
    const field = USER_SORT_FIELDS.includes(sort.active as UserSortField)
      ? (sort.active as UserSortField)
      : DEFAULT_SORT_FIELD;
    this.sortField.set(field);
    this.sortDirection.set(sort.direction === 'asc' ? 'asc' : 'desc');
    this.pageIndex.set(0);
    this.resetBulkOutcome();
    this.load();
  }

  protected onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.resetBulkOutcome();
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  /** Lot G : reflète l'état courant dans l'URL (défauts non écrits). */
  private syncUrl(q: string, status: string, role: string): void {
    writeListQueryParams(this.router, this.route, {
      q,
      status,
      role,
      sort: this.sortField() === DEFAULT_SORT_FIELD ? null : this.sortField(),
      dir: this.sortDirection() === DEFAULT_SORT_DIRECTION ? null : this.sortDirection(),
      page: this.pageIndex(),
      size: this.pageSize() === 20 ? null : this.pageSize(),
    });
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    const raw = this.filters.getRawValue();
    this.syncUrl(raw.q.trim(), raw.status, raw.role);
    this.api
      .listUsers({
        q: raw.q.trim() || null,
        status: raw.status || null,
        role: raw.role || null,
        sort: `${this.sortField()},${this.sortDirection()}`,
        page: this.pageIndex(),
        size: this.pageSize(),
      })
      .subscribe({
        next: (page) => this.state.set({ kind: 'ready', page }),
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

  // -------------------------------------------------------------------
  // Opérations de masse (EF-USER-004) — sélection multiple sur la PAGE
  // affichée, aperçu obligatoire avant toute écriture (RG-034 : « une
  // opération groupée exige une confirmation explicite »).
  // -------------------------------------------------------------------

  protected readonly bulkActions = BULK_ACTIONS;
  protected readonly bulkActionLabel = bulkActionLabel;
  protected readonly bulkOutcomeLabel = bulkOutcomeLabel;
  protected readonly bulkReasonMaxLength = ACTION_REASON_MAX_LENGTH;

  /** Même périmètre que le back-end (`LIFECYCLE_ROLES`) — masquage ergonomique seulement. */
  protected readonly canBulk = computed(() =>
    BULK_ROLES.some((role) => this.roleContext.effectiveRoles().includes(role)),
  );
  /** Détection de doublons : périmètre plus restreint (`ADMIN_ROLES`) que les opérations de masse. */
  protected readonly canViewDuplicates = computed(() =>
    DUPLICATE_VIEW_ROLES.some((role) => this.roleContext.effectiveRoles().includes(role)),
  );

  private readonly selectedIds = signal<ReadonlySet<string>>(new Set());
  protected readonly selectedCount = computed(() => this.selectedIds().size);

  protected readonly bulkForm = this.formBuilder.group({
    action: this.formBuilder.control<BulkAction>('SUSPEND', [Validators.required]),
    reason: this.formBuilder.control('', [
      Validators.required,
      Validators.maxLength(ACTION_REASON_MAX_LENGTH),
    ]),
  });

  /** Résultat du dernier aperçu (`applied: false`) — `null` tant qu'aucun n'a été demandé. */
  protected readonly bulkPreview = signal<BulkResult | null>(null);
  protected readonly bulkSubmitting = signal(false);
  protected readonly bulkError = signal<string | null>(null);
  /** Vrai une fois l'exécution réelle terminée (`applied: true`) : affiche le résultat final. */
  protected readonly bulkApplied = signal(false);

  protected isRowSelected(publicId: string): boolean {
    return this.selectedIds().has(publicId);
  }

  protected toggleRow(publicId: string): void {
    const next = new Set(this.selectedIds());
    if (next.has(publicId)) {
      next.delete(publicId);
    } else {
      next.add(publicId);
    }
    this.selectedIds.set(next);
    this.resetBulkOutcome();
  }

  /** Vrai si toutes les lignes de la page courante sont sélectionnées (et qu'il y en a). */
  protected readonly allOnPageSelected = computed(() => {
    const rows = this.rows();
    return rows.length > 0 && rows.every((row) => this.selectedIds().has(row.publicId));
  });
  protected readonly someOnPageSelected = computed(() => {
    const rows = this.rows();
    return rows.some((row) => this.selectedIds().has(row.publicId)) && !this.allOnPageSelected();
  });

  protected toggleAllOnPage(): void {
    const rows = this.rows();
    const next = new Set(this.selectedIds());
    if (this.allOnPageSelected()) {
      for (const row of rows) {
        next.delete(row.publicId);
      }
    } else {
      for (const row of rows) {
        next.add(row.publicId);
      }
    }
    this.selectedIds.set(next);
    this.resetBulkOutcome();
  }

  protected clearSelection(): void {
    this.selectedIds.set(new Set());
    this.resetBulkOutcome();
  }

  /** Un aperçu affiché ne reste valable que tant que la sélection ou le motif ne changent pas. */
  private resetBulkOutcome(): void {
    this.bulkPreview.set(null);
    this.bulkApplied.set(false);
    this.bulkError.set(null);
  }

  /** Étape 1 : calcule éligibles / ignorés / refusés — AUCUNE écriture (RG-034). */
  protected previewBulk(): void {
    if (this.bulkForm.invalid || this.selectedIds().size === 0 || this.bulkSubmitting()) {
      this.bulkForm.markAllAsTouched();
      return;
    }
    this.runBulk(false);
  }

  /** Étape 2 : exécute réellement l'action précédemment prévisualisée. */
  protected confirmBulk(): void {
    if (this.bulkSubmitting()) {
      return;
    }
    this.runBulk(true);
  }

  protected cancelBulkPreview(): void {
    this.resetBulkOutcome();
  }

  private runBulk(confirm: boolean): void {
    this.bulkSubmitting.set(true);
    this.bulkError.set(null);
    const value = this.bulkForm.getRawValue();
    this.api
      .bulkUsers({
        action: value.action,
        userIds: Array.from(this.selectedIds()),
        reason: value.reason.trim(),
        confirm,
      })
      .subscribe({
        next: (result) => {
          this.bulkSubmitting.set(false);
          this.bulkPreview.set(result);
          this.bulkApplied.set(result.applied);
          if (result.applied) {
            this.selectedIds.set(new Set());
            this.notifications.info(
              `Opération « ${bulkActionLabel(result.action)} » appliquée : ` +
                `${result.eligible} traité(s), ${result.ignored} ignoré(s), ${result.rejected} refusé(s).`,
            );
            this.load();
          }
        },
        error: (error: unknown) => {
          this.bulkSubmitting.set(false);
          const view = toAdministrationError(error);
          this.bulkError.set(view.message);
        },
      });
  }
}

/** Libellés français du paginateur Material (docs/02 §38.7). */
export function frenchPaginatorIntl(): MatPaginatorIntl {
  const intl = new MatPaginatorIntl();
  intl.itemsPerPageLabel = 'Éléments par page';
  intl.nextPageLabel = 'Page suivante';
  intl.previousPageLabel = 'Page précédente';
  intl.firstPageLabel = 'Première page';
  intl.lastPageLabel = 'Dernière page';
  intl.getRangeLabel = (page: number, pageSize: number, length: number): string => {
    if (length === 0 || pageSize === 0) {
      return `0 sur ${length}`;
    }
    const start = page * pageSize + 1;
    const end = Math.min(start + pageSize - 1, length);
    return `${start} – ${end} sur ${length}`;
  };
  return intl;
}
