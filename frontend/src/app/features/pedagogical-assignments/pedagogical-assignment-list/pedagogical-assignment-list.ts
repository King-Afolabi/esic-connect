import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs';

import { AcademicApiService } from '../../academic/academic-api.service';
import { ProgramResponse } from '../../academic/academic.models';
import { AdministrationApiService } from '../../administration/administration-api.service';
import { UserSummaryResponse } from '../../administration/administration.models';
import { NormalizedError, normalizeHttpError } from '../../../core/models/api-error';
import { NotificationService } from '../../../core/notifications/notification.service';
import { PedagogicalAssignmentsApiService } from '../pedagogical-assignments-api.service';
import {
  PEDAGOGICAL_ASSIGNMENT_TYPES,
  PedagogicalAssignmentResponse,
  pedagogicalAssignmentStatusLabel,
  pedagogicalAssignmentTypeLabel,
} from '../pedagogical-assignments.models';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'forbidden' }
  | { kind: 'ready' };

const REASON_MAX_LENGTH = 500;

/**
 * Affectation du responsable pédagogique d'une formation
 * (`PedagogicalAssignmentController` — cahier RG-004/RG-010/RG-011).
 *
 * Réservé à `ADMIN` / `SUPER_ADMIN` (garde de route + `@PreAuthorize`
 * `AcademicWeb.ASSIGNMENT_ROLES`, qui couvre aussi la lecture) : ni
 * `SCHOOL_ADMINISTRATION`, ni le responsable pédagogique lui-même n'ont
 * accès à cet écran. L'affectation porte sur une **formation entière**
 * (pas une classe) — c'est le périmètre exact reconnu par le back-end
 * (`AcademicScopeGuard`).
 *
 * Le responsable est choisi par recherche en direct parmi les comptes
 * `PEDAGOGICAL_MANAGER` existants (`GET /api/v1/users?role=...`) : aucun
 * identifiant technique n'est saisi à la main.
 */
@Component({
  selector: 'app-pedagogical-assignment-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
    MatTableModule,
  ],
  templateUrl: './pedagogical-assignment-list.html',
  styleUrl: './pedagogical-assignment-list.scss',
})
export class PedagogicalAssignmentList {
  private readonly api = inject(PedagogicalAssignmentsApiService);
  private readonly academic = inject(AcademicApiService);
  private readonly administration = inject(AdministrationApiService);
  private readonly notifications = inject(NotificationService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly typeLabel = pedagogicalAssignmentTypeLabel;
  protected readonly statusLabel = pedagogicalAssignmentStatusLabel;
  protected readonly assignmentTypes = PEDAGOGICAL_ASSIGNMENT_TYPES;
  protected readonly columns = [
    'program',
    'manager',
    'type',
    'status',
    'validFrom',
    'validUntil',
    'actions',
  ] as const;

  protected readonly loadState = signal<LoadState>({ kind: 'loading' });
  protected readonly assignments = signal<PedagogicalAssignmentResponse[]>([]);
  protected readonly programs = signal<ProgramResponse[]>([]);
  /** Résolution `userPublicId → identité civile`, pour l'affichage. */
  protected readonly managerNames = signal<Map<string, string>>(new Map());

  protected readonly programFilter = signal<string>('');
  protected readonly statusFilter = signal<'ACTIVE' | 'CLOSED' | ''>('ACTIVE');

  protected readonly showCreateForm = signal(false);
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);

  protected readonly managerOptions = signal<UserSummaryResponse[]>([]);
  protected readonly managerSearchLoading = signal(false);

  protected readonly closingId = signal<string | null>(null);
  protected readonly closeSubmitting = signal(false);
  protected readonly closeError = signal<string | null>(null);

  protected readonly createForm = this.formBuilder.group({
    programPublicId: this.formBuilder.control('', [Validators.required]),
    managerSearch: this.formBuilder.control(''),
    userPublicId: this.formBuilder.control('', [Validators.required]),
    type: this.formBuilder.control<'PRIMARY_MANAGER' | 'DELEGATE'>('PRIMARY_MANAGER', [
      Validators.required,
    ]),
    validFrom: this.formBuilder.control('', [Validators.required]),
    validUntil: this.formBuilder.control(''),
    reason: this.formBuilder.control('', [Validators.maxLength(REASON_MAX_LENGTH)]),
  });

  protected readonly closeForm = this.formBuilder.group({
    reason: this.formBuilder.control('', [Validators.required, Validators.maxLength(REASON_MAX_LENGTH)]),
    effectiveDate: this.formBuilder.control(''),
  });

  protected readonly errorMessage = computed(() => {
    const state = this.loadState();
    return state.kind === 'error' ? state.message : '';
  });

  protected readonly programName = computed(() => {
    const byId = new Map(this.programs().map((p) => [p.publicId, p]));
    return (programPublicId: string) => byId.get(programPublicId)?.name ?? null;
  });

  constructor() {
    this.loadPrograms();

    // Recherche en direct d'un compte PEDAGOGICAL_MANAGER — débattue pour
    // ne pas interroger le serveur à chaque frappe.
    this.createForm.controls.managerSearch.valueChanges
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => {
          const trimmed = q.trim();
          if (trimmed.length < 2) {
            return [];
          }
          this.managerSearchLoading.set(true);
          return this.administration.listUsers({
            q: trimmed,
            role: 'PEDAGOGICAL_MANAGER',
            size: 10,
          });
        }),
      )
      .subscribe({
        next: (page) => {
          this.managerSearchLoading.set(false);
          this.managerOptions.set(page.content);
        },
        error: () => this.managerSearchLoading.set(false),
      });

    // Le filtre pilote la liste.
    effect(() => {
      this.programFilter();
      this.statusFilter();
      this.load();
    });
  }

  protected retry(): void {
    this.load();
  }

  private load(): void {
    this.loadState.set({ kind: 'loading' });
    this.api
      .list({
        program: this.programFilter() || null,
        status: this.statusFilter() || null,
        sort: 'validFrom,desc',
        size: 100,
      })
      .subscribe({
        next: (page) => {
          this.assignments.set(page.content);
          this.loadState.set({ kind: 'ready' });
          this.resolveManagerNames(page.content);
        },
        error: (error: unknown) => {
          const view = this.toView(error);
          this.loadState.set(
            view.status === 403 ? { kind: 'forbidden' } : { kind: 'error', message: view.message },
          );
        },
      });
  }

  private loadPrograms(): void {
    this.academic.listPrograms({ status: 'ACTIVE', size: 200, sort: 'name,asc' }).subscribe({
      next: (page) => this.programs.set(page.content),
      error: () => this.programs.set([]),
    });
  }

  /** Résout, pour l'affichage, le nom des responsables sans requête par ligne. */
  private resolveManagerNames(rows: PedagogicalAssignmentResponse[]): void {
    const known = this.managerNames();
    const missing = Array.from(new Set(rows.map((r) => r.userPublicId))).filter((id) => !known.has(id));
    if (missing.length === 0) {
      return;
    }
    missing.forEach((userPublicId) => {
      this.administration.getUser(userPublicId).subscribe({
        next: (detail) => {
          const next = new Map(this.managerNames());
          next.set(userPublicId, `${detail.firstName} ${detail.lastName}`.trim() || detail.email);
          this.managerNames.set(next);
        },
        error: () => {
          const next = new Map(this.managerNames());
          next.set(userPublicId, '—');
          this.managerNames.set(next);
        },
      });
    });
  }

  protected managerName(userPublicId: string): string {
    return this.managerNames().get(userPublicId) ?? '…';
  }

  protected setProgramFilter(value: string): void {
    this.programFilter.set(value);
  }

  protected setStatusFilter(value: 'ACTIVE' | 'CLOSED' | ''): void {
    this.statusFilter.set(value);
  }

  protected toggleCreateForm(): void {
    this.showCreateForm.set(!this.showCreateForm());
    if (this.showCreateForm()) {
      this.submitError.set(null);
      this.createForm.reset({
        programPublicId: '',
        managerSearch: '',
        userPublicId: '',
        type: 'PRIMARY_MANAGER',
        validFrom: new Date().toISOString().slice(0, 10),
        validUntil: '',
        reason: '',
      });
      this.managerOptions.set([]);
    }
  }

  protected selectManager(user: UserSummaryResponse): void {
    this.createForm.controls.userPublicId.setValue(user.publicId);
    this.createForm.controls.managerSearch.setValue(`${user.firstName} ${user.lastName} — ${user.email}`, {
      emitEvent: false,
    });
    this.managerOptions.set([]);
  }

  protected submitCreate(): void {
    this.submitError.set(null);
    if (this.createForm.invalid || this.submitting()) {
      this.createForm.markAllAsTouched();
      return;
    }
    const raw = this.createForm.getRawValue();
    this.submitting.set(true);
    this.api
      .create({
        programPublicId: raw.programPublicId,
        userPublicId: raw.userPublicId,
        type: raw.type,
        validFrom: raw.validFrom,
        validUntil: raw.validUntil || null,
        reason: raw.reason.trim() || null,
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.showCreateForm.set(false);
          this.notifications.info('Responsable pédagogique affecté.');
          this.load();
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.submitError.set(this.toView(error).message);
        },
      });
  }

  protected startClose(publicId: string): void {
    this.closingId.set(publicId);
    this.closeError.set(null);
    this.closeForm.reset({ reason: '', effectiveDate: '' });
  }

  protected cancelClose(): void {
    this.closingId.set(null);
    this.closeError.set(null);
  }

  protected confirmClose(): void {
    const publicId = this.closingId();
    if (!publicId || this.closeForm.invalid || this.closeSubmitting()) {
      this.closeForm.markAllAsTouched();
      return;
    }
    const raw = this.closeForm.getRawValue();
    this.closeSubmitting.set(true);
    this.api.close(publicId, { reason: raw.reason.trim(), effectiveDate: raw.effectiveDate || null }).subscribe({
      next: () => {
        this.closeSubmitting.set(false);
        this.closingId.set(null);
        this.notifications.info('Affectation clôturée.');
        this.load();
      },
      error: (error: unknown) => {
        this.closeSubmitting.set(false);
        this.closeError.set(this.toView(error).message);
      },
    });
  }

  private toView(error: unknown): NormalizedError {
    return normalizeHttpError(error);
  }
}
