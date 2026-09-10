import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink, RouterLinkActive } from '@angular/router';

import { RoleContextService } from '../../../core/auth/role-context.service';
import { Role } from '../../../core/models/role';
import { normalizeHttpError } from '../../../core/models/api-error';
import { NotificationService } from '../../../core/notifications/notification.service';
import { AcademicApiService } from '../academic-api.service';
import {
  ACADEMIC_LIST_TABS,
  ACADEMIC_RESOURCES,
  AcademicChildSection,
  AcademicFact,
  AcademicParentLink,
  AcademicResourceConfig,
} from '../academic.config';
import {
  AcademicRecord,
  AcademicResourceSlug,
  academicStatusLabel,
} from '../academic.models';

const ARCHIVE_REASON_MAX = 500;

/**
 * Visibilité des actions d'écriture (modifier / archiver / restaurer),
 * reprise de `AcademicWeb.WRITE_ROLES` / `SCOPED_WRITE_ROLES` — voir
 * `academic-reference-list.ts` pour le détail par ressource.
 */
const WRITE_ROLES: Record<AcademicResourceSlug, readonly Role[]> = {
  'academic-years': ['ADMIN', 'SUPER_ADMIN'],
  programs: ['ADMIN', 'SUPER_ADMIN', 'PEDAGOGICAL_MANAGER'],
  'program-levels': ['ADMIN', 'SUPER_ADMIN', 'PEDAGOGICAL_MANAGER'],
  promotions: ['ADMIN', 'SUPER_ADMIN', 'PEDAGOGICAL_MANAGER'],
  'class-groups': ['ADMIN', 'SUPER_ADMIN', 'PEDAGOGICAL_MANAGER'],
};

type PendingAction = { kind: 'archive' } | { kind: 'restore' };

type DetailState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'not-found' }
  | { kind: 'forbidden' }
  | { kind: 'ready'; record: AcademicRecord };

type ChildState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; rows: AcademicRecord[] };

/**
 * Fiche d'une entité du référentiel académique et ses sous-listes
 * (enfants directs), pilotée par le `data.resource` de la route.
 *
 * - `loadOne` charge l'entité (`GET .../{publicId}`) ;
 * - chaque section « enfants » charge une sous-liste via un filtre
 *   **réellement exposé** (`GET /promotions?academicYear=…`,
 *   `GET /programs/{id}/levels`, `GET /class-groups?promotion=…`, etc.).
 *
 * Un `404` rend un état « introuvable » ; un `403` (hors périmètre
 * pédagogique, `ACAD_FORBIDDEN`) rend un état « accès refusé » — le
 * contrôle d'accès reste côté Spring Security.
 */
@Component({
  selector: 'app-academic-reference-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    RouterLinkActive,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './academic-reference-detail.html',
  styleUrl: './academic-reference-detail.scss',
})
export class AcademicReferenceDetail {
  private readonly api = inject(AcademicApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly roleContext = inject(RoleContextService);
  private readonly notifications = inject(NotificationService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  private readonly publicId = this.route.snapshot.paramMap.get('publicId') ?? '';
  protected readonly resource = this.route.snapshot.data['resource'] as AcademicResourceSlug;
  protected readonly config: AcademicResourceConfig = ACADEMIC_RESOURCES[this.resource];
  protected readonly tabs = ACADEMIC_LIST_TABS;
  protected readonly statusLabel = academicStatusLabel;
  protected readonly childColumns = ['code', 'name', 'status', 'actions'] as const;
  protected readonly reasonMaxLength = ARCHIVE_REASON_MAX;

  protected readonly canWrite = computed(() =>
    this.roleContext.effectiveRoles().some((r) => WRITE_ROLES[this.resource].includes(r)),
  );
  /** « Ajouter un niveau » n'apparaît que sur la fiche d'une formation. */
  protected readonly canCreateLevel = computed(
    () =>
      this.resource === 'programs' &&
      this.roleContext.effectiveRoles().some((r) => WRITE_ROLES['program-levels'].includes(r)),
  );

  protected readonly pendingAction = signal<PendingAction | null>(null);
  protected readonly actionSubmitting = signal(false);
  protected readonly actionError = signal<string | null>(null);
  protected readonly reasonForm = this.formBuilder.group({
    reason: this.formBuilder.control('', [
      Validators.required,
      Validators.maxLength(ARCHIVE_REASON_MAX),
    ]),
  });

  protected readonly state = signal<DetailState>({ kind: 'loading' });
  /** État de chaque section « enfants », aligné sur `config.children` par index. */
  protected readonly childStates = signal<ChildState[]>(
    this.config.children.map(() => ({ kind: 'loading' as const })),
  );

  protected readonly record = computed(() => {
    const current = this.state();
    return current.kind === 'ready' ? current.record : null;
  });
  protected readonly errorMessage = computed(() => {
    const current = this.state();
    return current.kind === 'error' ? current.message : null;
  });
  protected readonly facts = computed<readonly AcademicFact[]>(() => {
    const value = this.record();
    return value ? this.config.facts(value) : [];
  });
  protected readonly parentLinks = computed<readonly AcademicParentLink[]>(() => {
    const value = this.record();
    return value && this.config.parentLinks ? this.config.parentLinks(value) : [];
  });

  constructor() {
    this.loadRecord();
  }

  protected retryRecord(): void {
    this.loadRecord();
  }

  protected readonly isArchived = computed(() => this.record()?.status === 'ARCHIVED');

  protected startAction(kind: 'archive' | 'restore'): void {
    this.reasonForm.reset({ reason: '' });
    this.actionError.set(null);
    this.pendingAction.set({ kind });
  }

  protected cancelAction(): void {
    this.pendingAction.set(null);
    this.actionError.set(null);
  }

  protected confirmAction(): void {
    const action = this.pendingAction();
    if (!action || this.actionSubmitting()) {
      return;
    }
    if (action.kind === 'archive') {
      if (this.reasonForm.invalid) {
        this.reasonForm.markAllAsTouched();
        return;
      }
      if (!this.config.archive) {
        return;
      }
      this.actionSubmitting.set(true);
      this.actionError.set(null);
      this.config
        .archive(this.api, this.publicId, this.reasonForm.getRawValue().reason.trim())
        .subscribe({
          next: () => this.onActionDone('Élément archivé.'),
          error: (error: unknown) => this.onActionError(error),
        });
      return;
    }
    if (!this.config.restore) {
      return;
    }
    this.actionSubmitting.set(true);
    this.actionError.set(null);
    this.config.restore(this.api, this.publicId).subscribe({
      next: () => this.onActionDone('Élément restauré.'),
      error: (error: unknown) => this.onActionError(error),
    });
  }

  private onActionDone(message: string): void {
    this.actionSubmitting.set(false);
    this.pendingAction.set(null);
    this.notifications.info(message);
    this.loadRecord();
  }

  private onActionError(error: unknown): void {
    this.actionSubmitting.set(false);
    this.actionError.set(normalizeHttpError(error).message);
  }

  protected childState(index: number): ChildState {
    return this.childStates()[index] ?? { kind: 'loading' };
  }

  protected childRows(index: number): AcademicRecord[] {
    const current = this.childState(index);
    return current.kind === 'ready' ? current.rows : [];
  }

  protected childErrorMessage(index: number): string | null {
    const current = this.childState(index);
    return current.kind === 'error' ? current.message : null;
  }

  protected retryChild(index: number): void {
    this.loadChild(index, this.config.children[index]);
  }

  private loadRecord(): void {
    this.state.set({ kind: 'loading' });
    this.config.loadOne(this.api, this.publicId).subscribe({
      next: (record) => {
        this.state.set({ kind: 'ready', record });
        this.config.children.forEach((child, index) => this.loadChild(index, child));
      },
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

  private loadChild(index: number, child: AcademicChildSection): void {
    this.setChildState(index, { kind: 'loading' });
    child.load(this.api, this.publicId).subscribe({
      next: (page) => this.setChildState(index, { kind: 'ready', rows: page.content }),
      error: (error: unknown) =>
        this.setChildState(index, { kind: 'error', message: normalizeHttpError(error).message }),
    });
  }

  private setChildState(index: number, next: ChildState): void {
    this.childStates.update((states) => {
      const copy = [...states];
      copy[index] = next;
      return copy;
    });
  }
}
